# PratoJá

Sistema web responsivo de delivery para restaurante, desenvolvido para demonstração acadêmica. O cliente escolhe produtos, personaliza o prato, monta a sacola, finaliza o pedido e acompanha o status. A equipe administrativa gerencia cardápio, pedidos e relatórios.

## Objetivo

Demonstrar um fluxo completo de delivery com autenticação, controle de acesso, carrinho, checkout, pedidos e painel administrativo em uma aplicação web única.

## Funcionalidades

- Home, cardápio com busca e categorias e detalhes do produto.
- Monte seu prato com grupos de opções e adicionais.
- Carrinho com quantidades, observações, subtotal e taxa de entrega.
- Cadastro, login, recuperação de senha simulada e perfil.
- Endereços de entrega, checkout para entrega ou retirada e pagamentos simulados por PIX, cartão ou dinheiro.
- Acompanhamento do pedido, histórico, repetir pedido e avaliação.
- Painel ADMIN com dashboard, pedidos, alteração de status, produtos, categorias e relatórios.
- Proteção de rotas, CSRF, validação de dados e isolamento dos pedidos por usuário.

## Tecnologias

- Java 21 e Spring Boot 4.1.1.
- Spring MVC, Thymeleaf, Spring Security, Spring Data JPA e Bean Validation.
- Flyway, H2 para demonstração local e PostgreSQL preparado para produção.
- HTML, CSS e JavaScript sem framework de frontend.
- Maven Wrapper para execução reproduzível.

## Arquitetura resumida

O projeto segue uma organização por responsabilidade:

- `domain`: entidades e tipos do domínio.
- `repository`: interfaces de persistência JPA.
- `service`: regras de negócio de conta, carrinho e pedidos.
- `web`: controllers MVC e tratamento de erros.
- `config`: segurança, dados demo e configurações web.
- `templates`: páginas Thymeleaf do cliente e do administrador.
- `static`: CSS, JavaScript e imagens locais.
- `db/migration`: migrations Flyway versionadas.

## Banco de dados

Por padrão, a aplicação usa H2 em arquivo em `./data/pratoja` e executa as migrations Flyway automaticamente. Para produção, use o perfil `postgres` e configure `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`. Nenhuma cobrança ou envio de e-mail real é realizado.

## Como executar

No Windows:

```powershell
cd C:\Users\enzoa\Downloads\pratoja
.\mvnw.cmd spring-boot:run
```

Acesse `http://localhost:8080`. Se a porta estiver ocupada:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Para validar o projeto:

```powershell
.\mvnw.cmd clean package
```

## Credenciais demo

| Perfil | E-mail | Senha |
| --- | --- | --- |
| Cliente | `cliente@pratoja.com.br` | `Cliente@123` |
| Administrador | `admin@pratoja.com.br` | `Admin@123` |

Os dados são destinados exclusivamente ao ambiente local de demonstração.

## Fluxo recomendado para apresentação

### Cliente

1. Abra a home e o cardápio.
2. Acesse um produto ou `Monte seu prato` e adicione à sacola.
3. Entre com o cliente demo, confira o checkout e crie o pedido.
4. Abra o acompanhamento e mostre a atualização do status.

### Administrador

1. Abra `/admin/login` e entre com o administrador demo.
2. Apresente o dashboard, o cardápio e os pedidos.
3. Abra o pedido criado, altere seu status e volte ao acompanhamento do cliente.
4. Finalize mostrando relatórios e disponibilidade dos produtos.

## Estrutura principal

```text
src/main/java/br/com/pratoja/pratoja/
├── cart/         # carrinho em sessão
├── config/       # segurança, configuração e dados demo
├── domain/       # entidades e enums
├── repository/   # persistência
├── service/      # regras de negócio
└── web/          # controllers e tratamento de erros

src/main/resources/
├── db/migration/ # schema versionado
├── static/       # CSS, JS e imagens
└── templates/    # páginas Thymeleaf
```
