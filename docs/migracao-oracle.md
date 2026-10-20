# Migração do PratoJá: Blitz.cloud → Oracle Cloud Always Free

> **Status: PREPARADO (aguardando acesso à tenancy Oracle).** Este documento e os
> artefatos em `deploy/` deixam a migração pronta para execução. **Nada foi
> publicado na Oracle ainda** e a Blitz.cloud permanece intacta como ambiente
> atual até a validação da nova infraestrutura.

## 1. Origem e destino

| | |
|---|---|
| **Hospedagem atual** | Blitz.cloud (container Docker, amd64), dorme por inatividade |
| **Destino** | Oracle Cloud Infrastructure — instância **Ampere A1 Flex (ARM64)**, Always Free |
| **Banco atual (demo)** | H2 em arquivo (`./data/pratoja`), modo demonstração |
| **Banco destino** | PostgreSQL 16 em container, volume persistente |
| **Proxy/TLS** | Caddy 2 (Let's Encrypt automático, gratuito) |

## 2. Compatibilidade ARM64 (verificada)

- Java 21, Spring Boot, Flyway, PostgreSQL JDBC, Thymeleaf — **100% JVM, sem
  dependências nativas por arquitetura** (verificado no `pom.xml`: sem
  netty-native/rocksdb/sqlite/snappy).
- Bases `eclipse-temurin:21-jdk` e `:21-jre` publicam `linux/arm64/v8`.
- A `Dockerfile` é multi-arch: o CI publica `linux/amd64,linux/arm64` no GHCR, de
  modo que o **mesmo** `:latest` roda na Blitz (amd64) e na Oracle (arm64).
- **Nenhuma funcionalidade é removida** para a migração.

## 3. Elegibilidade Always Free e proteção financeira

> **Confirme na documentação oficial da Oracle, na sua região/tenancy, ANTES de
> provisionar.** Os limites abaixo são o alvo; eu não tenho acesso à sua conta
> para confirmá-los por você.

Recursos a usar, todos dentro do **Always Free**:

- **Compute Ampere A1 Flex**: até 4 OCPUs e 24 GB de RAM somados, sempre grátis.
  Uma configuração conservadora (ex.: 2 OCPU / 12 GB) hospeda PratoJá + PenteFino
  + PostgreSQL com folga.
- **Boot volume** dentro da franquia Always Free de Block Volume (até 200 GB
  somados). **Não** crie volumes/backaups automáticos além da franquia.
- **IP público efêmero/reservado** dentro da franquia Always Free.
- **Imagem Ubuntu 22.04/24.04 (ARM)** oficial e gratuita — **não** usar imagens de
  marketplace pagas.

**Regras de proteção (obrigatórias):**

- Não fazer upgrade para **Pay As You Go**; manter a conta **Always Free**.
- Não habilitar Load Balancer gerenciado, monitoramento/log pagos, snapshots
  automáticos ou backups gerenciados pagos.
- Não configurar autoscaling que crie instâncias faturáveis.
- Ativar **Budgets/alertas de custo** (gratuitos) — lembrando que **alerta NÃO é
  bloqueio de cobrança**.
- Se a elegibilidade de qualquer recurso for incerta, **não** provisioná-lo.

Riscos conhecidos e documentados:

- A capacidade de criar instâncias **A1 Always Free pode faltar** em algumas
  regiões (erro "out of host capacity"); pode ser necessário tentar outra AD ou
  horário. Isso não gera custo, mas atrasa o provisionamento.
- Instâncias Always Free **podem ser recuperadas/reiniciadas** pela Oracle; contas
  Always Free **ociosas** podem ser marcadas para recuperação. Por isso há
  `restart: unless-stopped`, volumes persistentes e o procedimento de recuperação
  (seção 7). **Não** se presume disponibilidade absoluta.

## 4. Provisionamento (a executar quando houver acesso)

1. Criar a instância **A1 Flex (ARM)** com Ubuntu, chave SSH própria.
2. **Security List / NSG**: liberar apenas **22 (SSH, idealmente restrito ao seu
   IP)**, **80** e **443**. Nada mais. PostgreSQL **nunca** exposto.
3. No host: instalar Docker + plugin Compose; habilitar `ufw` permitindo apenas
   22/80/443; `systemctl enable docker`.
4. Clonar o repositório em `~/pratoja`, criar `deploy/.env` (a partir de
   `.env.example`) com `chmod 600`.
5. Apontar o DNS (`A`/`AAAA`) do domínio para o IP público.
6. `cd deploy && docker compose up -d`. O Caddy emite o TLS automaticamente.

### Stack combinada (PratoJá + PenteFino na mesma VM)

Para hospedar ambos atrás de um único Caddy, use este `docker-compose.yml`
(coloque num diretório próprio na VM, ex.: `~/stack`). Cada app mantém **banco,
credenciais e volumes isolados**:

```yaml
name: oci-stack
services:
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    networks: [web]

  pratoja-db:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: pratoja
      POSTGRES_USER: pratoja
      POSTGRES_PASSWORD: ${PRATOJA_DB_PASSWORD:?}
    volumes: [pratoja_db:/var/lib/postgresql/data]
    networks: [pratoja_net]
  pratoja:
    image: ghcr.io/oenzoalmeida/pratoja:latest
    restart: unless-stopped
    depends_on: [pratoja-db]
    environment:
      SPRING_PROFILES_ACTIVE: postgres
      SERVER_FORWARD_HEADERS_STRATEGY: framework
      SERVER_SERVLET_SESSION_COOKIE_SECURE: "true"
      DATABASE_URL: jdbc:postgresql://pratoja-db:5432/pratoja
      DATABASE_USERNAME: pratoja
      DATABASE_PASSWORD: ${PRATOJA_DB_PASSWORD:?}
      PRATOJA_ADMIN_PASSWORD: ${PRATOJA_ADMIN_PASSWORD:?}
      PRATOJA_DEMO_MODE: "false"
      PUBLIC_BASE_URL: https://pratoja.seudominio.com
      PRATOJA_UPLOAD_DIR: /app/data/uploads
    volumes: [pratoja_uploads:/app/data/uploads]
    networks: [pratoja_net, web]

  pentefino-db:
    image: postgres:16
    restart: unless-stopped
    environment:
      POSTGRES_DB: pentefino
      POSTGRES_USER: pentefino
      POSTGRES_PASSWORD: ${PENTEFINO_DB_PASSWORD:?}
    volumes: [pentefino_db:/var/lib/postgresql/data]
    networks: [pentefino_net]
  pentefino:
    image: ghcr.io/oenzoalmeida/pentefino:latest
    restart: unless-stopped
    depends_on: [pentefino-db]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://pentefino-db:5432/pentefino
      DATABASE_USERNAME: pentefino
      DATABASE_PASSWORD: ${PENTEFINO_DB_PASSWORD:?}
      PUBLIC_URL: https://pentefino.seudominio.com
      CORS_ALLOWED_ORIGINS: https://pentefino.seudominio.com
      STORAGE_PROVIDER: filesystem
      STORAGE_ROOT: /data/documents
      BOOTSTRAP_USERS_ENABLED: "false"
      GEMINI_API_KEY: ${GEMINI_API_KEY:-}
    volumes: [pentefino_docs:/data/documents]
    networks: [pentefino_net, web]

networks:
  web: {}
  pratoja_net: { internal: true }
  pentefino_net: { internal: true }
volumes:
  caddy_data: {}
  caddy_config: {}
  pratoja_db: {}
  pratoja_uploads: {}
  pentefino_db: {}
  pentefino_docs: {}
```

`Caddyfile` combinado:

```
pratoja.seudominio.com {
    encode zstd gzip
    reverse_proxy pratoja:8080 { header_up X-Forwarded-Proto https }
}
pentefino.seudominio.com {
    encode zstd gzip
    reverse_proxy pentefino:8080 { header_up X-Forwarded-Proto https }
}
```

> **Isolamento:** cada app só enxerga o próprio Postgres (redes `internal`
> separadas). Nenhum banco é publicado; só o Caddy fica na rede `web`.

## 5. CI/CD (`.github/workflows/build-image.yml`)

`push` na `main` → **testes** → build **multi-arch** no GHCR → deploy:

- `redeploy-blitz`: mantém a Blitz atualizada (pulado sem `BLITZ_API_KEY`).
- `deploy-oci`: via SSH, faz `pull` do digest testado, `compose up -d`, espera o
  **health** do container e, em falha, **reverte** para a imagem anterior
  (rollback). Pulado enquanto não existirem os segredos `OCI_SSH_*`.

**Segredos necessários (Settings → Secrets and variables → Actions), escopo mínimo:**

- `OCI_SSH_HOST`, `OCI_SSH_USER`, `OCI_SSH_KEY` (chave privada **dedicada ao
  deploy**, não a sua chave pessoal). Nenhum segredo vai para o código ou logs.

## 6. Backup, restauração e cutover (preservação de dados)

Scripts em `deploy/`:

- `backup.sh` — `pg_dump -Fc` + tar dos uploads + `MANIFEST.txt` (contagens) +
  `SHA256SUMS`.
- `verify-backup.sh` — restaura o dump num **Postgres efêmero isolado**, compara
  contagens e valida o tar de uploads. **Não toca a produção.**
- `restore.sh` — restaura para a stack (usar só no cutover/recuperação).
- `export-h2-to-postgres.md` — como preservar dados gerados na demo H2 da Blitz.

**Ordem de cutover (requer sua autorização explícita — não executo sozinho):**

1. Congelar escrita na Blitz (modo manutenção / janela de baixa atividade).
2. `backup.sh` na origem **ou** export H2 (conforme `export-h2-to-postgres.md`).
3. `verify-backup.sh` — confirmar integridade.
4. Restaurar no destino e `verify` novamente (contagens batem).
5. Validar funcionalmente o destino (seção 9 do briefing).
6. Só então apontar o DNS para a Oracle.
7. Manter a Blitz **intacta** por um período de observação antes de desativar.

## 7. Recuperação de desastre (VM recuperada/perdida)

- Dados vivem em **volumes nomeados** + backups verificados fora da VM.
- Para reconstruir: nova instância A1 → Docker/Compose → `compose up -d` →
  `restore.sh <ultimo_backup>`.
- Agende `backup.sh` por `cron` na VM (ex.: diário), guardando as cópias fora da
  VM (download manual ou bucket próprio — **sem** serviço pago).

## 8. Segurança (resumo)

- PostgreSQL só na rede interna do compose; **sem** porta publicada.
- SSH idealmente restrito ao seu IP na Security List + `ufw`.
- HTTPS obrigatório (Caddy), HSTS e cabeçalhos de segurança.
- App roda como **usuário não-root** no container.
- Segredos só em `deploy/.env` (600) e em GitHub Secrets; nunca no Git/logs.
- Cookie de sessão `Secure` + `SameSite` atrás do proxy; CSRF já ativo no app.

## 9. Limitações do plano gratuito (documentadas)

- A1 Always Free pode faltar capacidade em certas regiões no momento da criação.
- Oracle pode reiniciar/recuperar instâncias Always Free; contas ociosas podem ser
  recuperadas. Mitigação: persistência em volume + backups + procedimento de
  recuperação. **Não** há garantia de disponibilidade absoluta — o objetivo é
  **eliminar a suspensão por inatividade** da Blitz, não prometer 100% de uptime.
- Cold start de JVM no primeiro boot/redeploy (segundos), não por inatividade.
