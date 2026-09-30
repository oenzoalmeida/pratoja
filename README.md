# PratoJá

PratoJá é uma **plataforma de cardápio digital e delivery** para restaurantes. O restaurante cadastra a própria identidade (nome, slogan, contato, horários, cores, taxa de entrega) pelo painel administrativo, sem tocar em código — e recebe pedidos com personalização de pratos, checkout completo, acompanhamento em tempo real e relatórios.

## Demonstração

**Aplicação:** https://pratoja.onrender.com

## Credencial demo

> **Nota:** as credenciais abaixo são públicas para fins de demonstração; os dados da conta demo podem ser alterados por outros visitantes.

| Perfil | E-mail | Senha |
|---|---|---|
| Cliente | `cliente@pratoja.com.br` | `Cliente@123` |

O acesso administrativo é provisionado internamente e não possui credencial pública.

## Como um restaurante configura a própria identidade

1. Faça login no painel administrativo (`/admin`).
2. Abra **Configurações da loja** no menu lateral.
3. Preencha nome, slogan, descrição, telefone, WhatsApp, e-mail, endereço completo, horário de funcionamento, textos da página inicial (título, subtítulo e tempo de entrega), taxa de entrega e cores da marca (`#RRGGBB`).
4. Salve: o site inteiro passa a exibir o nome e a identidade do restaurante — títulos das páginas, cabeçalho, rodapé, home e taxa de entrega do checkout — sem nenhum deploy ou alteração de código.

## Funcionalidades principais

- Home, cardápio com busca e categorias e detalhes do produto.
- Monte seu prato com grupos de opções e adicionais.
- Carrinho com quantidades, observações, subtotal e taxa de entrega.
- Cadastro, login, recuperação de senha simulada e perfil.
- Endereços de entrega, checkout para entrega ou retirada e pagamentos simulados por PIX, cartão ou dinheiro.
- Acompanhamento do pedido, histórico, repetir pedido e avaliação.
- Painel ADMIN com dashboard, pedidos, alteração de status, produtos, categorias, relatórios e configurações da loja.
- Painel da plataforma (`/platform`) para criar e gerenciar lojas e administradores de loja.
- Exclusão de conta pela interface (dados pessoais apagados; histórico anonimizado).
- Termos de Uso e Política de Privacidade integrados à aplicação.

## Tecnologias

- Java 21 e Spring Boot.
- Spring MVC, Thymeleaf, Spring Security, Spring Data JPA e Bean Validation.
- Flyway, H2 para demonstração local e PostgreSQL para produção.
- HTML, CSS e JavaScript sem framework de frontend.
- Maven Wrapper para execução reproduzível.

## Perfis de acesso

- **Cliente (CUSTOMER):** monta pedidos, acompanha o status, avalia e gerencia o próprio perfil; pode excluir a própria conta.
- **Administrador da loja (STORE_ADMIN):** gerencia cardápio, pedidos, relatórios e as configurações da própria loja. Credencial definida por `PRATOJA_ADMIN_PASSWORD`.
- **Administrador da plataforma (PLATFORM_ADMIN):** operador da plataforma. Painel `/platform`: lista lojas (nome, slug, status, nº de produtos/pedidos/admins, taxa, criação), cria loja (slug gerado do nome e editável, com validação de unicidade), ativa/desativa loja (dados preservados; loja inativa fica indisponível em `/loja/{slug}`), edita dados básicos e gerencia STORE_ADMINs por loja (criação com senha inicial; desativação nunca deleta). É credencial da PLATAFORMA (não de restaurante): criado no boot apenas se `PRATOJA_PLATFORM_ADMIN_PASSWORD` estiver definida (e-mail fixo do seed: `platform@pratoja.com.br`). Não vê dados transacionais além de métricas agregadas.

## Multitenancy (Fases 1-5)

- Cada entidade de operação (`categories`, `products`, `option_groups`, `orders`) possui `store_id`; `users.store_id` vincula o STORE_ADMIN à sua loja (NULL para clientes).
- Toda consulta de backend é escopada por `store_id` (`findByIdAndStore_Id`, etc.); um STORE_ADMIN que tenta acessar objeto de outra loja recebe **404**.
- Migração **V6** criou a tabela `stores` (migrando a antiga `store_settings` singleton, depois removida) com backfill da loja legada `restaurante` (id=1). **V7** (específico por banco) trocou a UNIQUE global de `categories.name` por `UNIQUE(store_id, name)`.
- As páginas públicas vivem sob **`/loja/{slug}`** (Fase 4): home, cardápio, produto, monte-seu-prato, sacola, checkout, acompanhamento, repetir e avaliar resolvem a loja pelo slug (loja inexistente ou inativa → 404). A sacola em sessão é **por loja**. Rotas antigas sem slug redirecionam **301** para a loja única ativa; com 2+ lojas ativas, `/` lista as lojas ativas.

## Arquitetura / Estrutura

```text
src/main/java/br/com/pratoja/pratoja/
├── config/       Security, seeding e inicialização
├── domain/       Entidades JPA e tipos de domínio (inclui StoreSettings)
├── repository/   Repositórios Spring Data
├── service/      Regras de negócio (conta, pedidos, loja, tempo real)
└── web/          Controllers MVC (loja, perfil, admin, carrinho, jurídico)
src/main/resources/
├── db/migration/ Migrações Flyway
├── templates/    Páginas Thymeleaf
└── static/       CSS, JS e imagens
```

## Segurança e privacidade

- Senhas com hash bcrypt (custo 12); senha administrativa definida por variável de ambiente (`PRATOJA_ADMIN_PASSWORD`) e sincronizada a cada inicialização.
- Sessão com cookie HttpOnly/SameSite e proteção CSRF em todos os formulários.
- Controle de acesso por papel e isolamento de pedidos/endereços por usuário (checagem de titularidade).
- Consultas parametrizadas via JPA; upload de imagens restrito a administradores e a formatos de imagem.
- Sem analytics, rastreamento ou cookies além do de sessão; Termos de Uso e Política de Privacidade integrados.
- Nenhuma credencial de produção versionada.

## Executando localmente

```bash
./mvnw spring-boot:run        # Linux/Mac
.\mvnw.cmd spring-boot:run    # Windows
```

Acesse `http://localhost:8080`. Por padrão o projeto usa H2 em memória; para PostgreSQL, ative o perfil `postgres` com `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`.

## E-mails transacionais

E-mails reais (hoje: **somente recuperação de senha**) usam o padrão **outbox**: a gravação acontece na mesma transação do evento de negócio (migration `V9__mail_outbox`) e um scheduler (`fixedDelay` ~30s) envia com retry exponencial (~1min, 5min, 15min), marcando **DEAD** após 5 tentativas. Templates em modo texto ficam em `src/main/resources/templates/email/`.

### Variáveis de ambiente

| Variável | Default | Descrição |
|---|---|---|
| `PRATOJA_MAIL_ENABLED` | `false` | Liga/desliga o subsistema. Com `false`, **nenhum e-mail é enviado** e a recuperação mantém o comportamento atual (link demo na tela apenas em demo-mode). Com `true`, o link de reset **nunca** aparece em tela — sai somente por e-mail. |
| `SMTP_HOST` | *(vazio)* | Host SMTP. Ex. Brevo: `smtp-relay.brevo.com` |
| `SMTP_PORT` | `587` | Porta SMTP (Brevo usa `587` com STARTTLS) |
| `SMTP_USER` | *(vazio)* | Usuário SMTP (no Brevo, o e-mail de login do Brevo) |
| `SMTP_PASSWORD` | *(vazio)* | Senha SMTP (no Brevo, a **SMTP key** gerada no painel — nunca a senha da conta) |
| `MAIL_FROM` | `notificacoes@pratoja.app` | Remetente (`From:`) dos e-mails |
| `MAIL_BASE_URL` | `http://localhost:8080` | URL base usada nos links dos e-mails (ex.: `https://pratoja.onrender.com`) |
| `MAIL_POLL_INTERVAL_MS` | `30000` | Intervalo do scheduler do outbox |

**Nenhuma credencial SMTP é versionada** — tudo entra por variáveis de ambiente (`spring.mail.*` lê `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`).

### Instruções Brevo

1. No painel Brevo: **SMTP & API → Senders & IP**; crie/garanta um sender para o `MAIL_FROM` (ex.: `notificacoes@pratoja.app`).
2. Em **SMTP & API → SMTP**, gere a **SMTP key** e use: `SMTP_HOST=smtp-relay.brevo.com`, `SMTP_PORT=587`, `SMTP_USER=<login Brevo>`, `SMTP_PASSWORD=<smtp key>`.
3. Defina `PRATOJA_MAIL_ENABLED=true`, `MAIL_FROM` e `MAIL_BASE_URL` (URL pública do deploy).
4. O monitor de falhas é o próprio outbox: linhas `FAILED` têm `last_error` e `next_attempt_at`; `DEAD` indica entrega não concluída após 5 tentativas.

## Testes

Testes de fluxo com MockMvc (catálogo, cadastro/login, checkout, painel admin, configurações da loja) executados via Maven Wrapper e GitHub Actions:

```bash
./mvnw test
```

## Deploy

- **Render** (blueprint em `render.yaml`): serviço Docker com health check em `/`, `PRATOJA_DEMO_MODE=false`, `SPRING_PROFILES_ACTIVE=postgres` e segredos (`PRATOJA_ADMIN_PASSWORD`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`) definidos no painel — nunca versionados.
- **Banco:** PostgreSQL persistente (Supabase, plano free, região us-west-1/Oregon). O profile `postgres` lê a conexão de `DATABASE_URL` (JDBC, com `?sslmode=require`); o schema é criado pelo **Flyway** e os dados demo pelo `DataSeeder`.
- **Produção exige** `PRATOJA_ADMIN_PASSWORD`: sem ela o boot falha (não existe senha padrão).
- **CI:** GitHub Actions executa `mvnw test` em cada push.

## Limitações conhecidas

- Pagamento e confirmação são **simulados**; nenhuma cobrança real é realizada.
- Recuperação de senha: com `PRATOJA_MAIL_ENABLED=false` (default), não há envio de e-mail — em modo demonstração o link é exibido na tela; com `true`, o envio é real via Brevo/SMTP.
- Sem rate limiting nas rotas de autenticação.
- O e-mail da conta não é editável após o cadastro.
- A plataforma é multitenant (Fases 1-5 concluídas: isolamento por loja no backend, páginas públicas por slug e painel `/platform` para criar e gerenciar lojas pela UI); fases seguintes podem alterar fluxos existentes.

## Avisos específicos

- **Nenhuma cobrança real é realizada** — pagamento inteiramente simulado para fins de demonstração. Não informe dados reais de cartão.

## Autor

Enzo Almeida
