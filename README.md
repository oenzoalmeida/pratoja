# PratoJá

Sistema web responsivo de delivery para restaurante, desenvolvido para demonstração acadêmica. O cliente escolhe produtos, personaliza o prato, monta a sacola, finaliza o pedido e acompanha o status. A equipe administrativa gerencia cardápio, pedidos e relatórios.

## Demonstração

**Aplicação:** https://pratoja.onrender.com

## Credencial demo

| Perfil | E-mail | Senha |
|---|---|---|
| Cliente | `cliente@pratoja.com.br` | `Cliente@123` |

O acesso administrativo é provisionado internamente e não possui credencial pública.

## Sobre / Objetivo

Demonstrar um fluxo completo de delivery com autenticação, controle de acesso, carrinho, checkout, pedidos e painel administrativo em uma aplicação web única.

## Funcionalidades principais

- Home, cardápio com busca e categorias e detalhes do produto.
- Monte seu prato com grupos de opções e adicionais.
- Carrinho com quantidades, observações, subtotal e taxa de entrega.
- Cadastro, login, recuperação de senha simulada e perfil.
- Endereços de entrega, checkout para entrega ou retirada e pagamentos simulados por PIX, cartão ou dinheiro.
- Acompanhamento do pedido, histórico, repetir pedido e avaliação.
- Painel ADMIN com dashboard, pedidos, alteração de status, produtos, categorias e relatórios.
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
- **Administrador (ADMIN):** gerencia cardápio, pedidos e relatórios. Credencial não pública.

## Arquitetura / Estrutura

```text
src/main/java/br/com/pratoja/pratoja/
├── config/       Security, seeding e inicialização
├── domain/       Entidades JPA e tipos de domínio
├── repository/   Repositórios Spring Data
├── service/      Regras de negócio (conta, pedidos, tempo real)
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

## Testes

Testes de fluxo com MockMvc (catálogo, cadastro/login, checkout, painel admin) executados via Maven Wrapper e GitHub Actions:

```bash
./mvnw test
```

## Deploy

- **Render** (blueprint em `render.yaml`): serviço Docker com health check em `/`, variável `PRATOJA_DEMO_MODE=true` e `PRATOJA_ADMIN_PASSWORD` definido no painel.
- **Banco:** PostgreSQL gerenciado (configuração em `application-postgres.properties`).
- **CI:** GitHub Actions executa `mvnw test` em cada push.

## Limitações conhecidas

- Pagamento e confirmação são **simulados**; nenhuma cobrança real é realizada.
- A recuperação de senha não envia e-mail: em modo demonstração, o link é exibido na tela.
- Sem rate limiting nas rotas de autenticação.
- O e-mail da conta não é editável após o cadastro.

## Avisos específicos

- **Nenhuma cobrança real é realizada** — pagamento inteiramente simulado para fins de demonstração. Não informe dados reais de cartão.

## Autor

Enzo Almeida
