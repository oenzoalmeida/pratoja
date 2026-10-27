# Preservar dados do PratoJá na migração Blitz (H2) → Oracle (PostgreSQL)

> **Contexto honesto.** Na Blitz.cloud o PratoJá roda em **modo demonstração**
> (`pratoja.demo-mode=true`) com banco **H2 em arquivo** (`./data/pratoja`). O
> catálogo, a loja e o admin são recriados deterministicamente a cada boot pelo
> `DataSeeder` + migrações Flyway; o que é realmente "gerado por visitantes"
> (contas de clientes criadas na demo, pedidos, avaliações) vive apenas no arquivo
> H2 do container. **Não presuma** que o banco da demo é um banco de produção
> estável — a própria demo avisa que os dados podem ser redefinidos.

Há dois caminhos, dependendo do que você quer preservar.

## Caminho A — Recriar o estado inicial (recomendado p/ demo)

Nada a exportar: ao subir a stack no Oracle com PostgreSQL vazio, o Flyway aplica
`db/migration` + `db/migration-postgres` e o `DataSeeder` recria loja, catálogo e
contas administrativas automaticamente. É o estado "limpo" da demonstração.

- Dados preservados: **estrutura + catálogo + admin** (idênticos à origem).
- Dados NÃO preservados: contas/pedidos gerados por visitantes na Blitz.

## Caminho B — Migrar também os dados gerados (H2 → PostgreSQL)

Use quando quiser levar as contas/pedidos atuais do arquivo H2 da Blitz.

1. **Obtenha o arquivo H2 da Blitz** (`data/pratoja.mv.db`). Isso exige acesso ao
   volume/arquivo do container na Blitz. Se não houver acesso ao arquivo, o
   Caminho B não é possível e usa-se o Caminho A.

2. **Abra o H2 em modo leitura e gere um dump SQL** (localmente, com o mesmo
   schema das migrações já aplicado):

   ```bash
   # Requer o JAR do H2 (mesma versão do projeto). O app usa MODE=PostgreSQL.
   java -cp h2-*.jar org.h2.tools.Script \
     -url "jdbc:h2:file:./pratoja;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE" \
     -user sa -script h2-dump.sql
   ```

3. **Importe no PostgreSQL de destino com uma estratégia de dados-apenas.** O
   schema no destino já vem das migrações Flyway; portanto importe **apenas as
   linhas** (INSERTs), evitando recriar tabelas/constraints. Ferramentas como
   `pgloader` fazem a conversão de tipos H2→PostgreSQL de forma mais segura que um
   replay de SQL cru:

   ```bash
   # Exemplo com pgloader apontando para o arquivo H2 (ajuste caminhos/credenciais).
   pgloader ./pratoja.mv.db postgresql://pratoja:SENHA@localhost/pratoja
   ```

4. **Reative as sequences/identidades** após importar linhas com IDs explícitos
   (senão novos inserts colidem):

   ```sql
   SELECT setval(pg_get_serial_sequence(t.relname, a.attname),
                  (SELECT COALESCE(MAX(id),1) FROM only t.relname::regclass))
   FROM ...;  -- veja o script de reset no final deste guia
   ```

5. **Preserve os uploads**: copie o conteúdo do diretório de uploads da Blitz para
   o volume `pratoja_uploads` (ver `restore.sh`, passo de uploads).

## Validação obrigatória (ambos os caminhos)

Antes de considerar os dados preservados, **compare contagens e integridade** no
destino contra a origem, em ambiente isolado:

```bash
# No destino (ou no Postgres efêmero de teste):
psql -U pratoja -d pratoja -c "
  SELECT relname, n_live_tup FROM pg_stat_user_tables ORDER BY relname;"
# Confira flyway_schema_history: todas as versões V1..V9 com success=true.
```

- As contagens por tabela batem com a origem?
- `flyway_schema_history` tem as 9 migrações com `success=true`?
- Login do admin e do cliente demo funcionam?
- Imagens de produtos (uploads) aparecem no cardápio?

Só declare os dados preservados quando essas respostas forem **sim** e o teste de
restauração (`verify-backup.sh`) tiver passado.

> **Nunca** copie o arquivo H2, dumps ou uploads reais para o repositório Git nem
> para logs. Mantenha-os apenas na VM / em storage controlado.
