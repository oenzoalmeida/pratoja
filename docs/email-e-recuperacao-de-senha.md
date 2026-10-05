# E-mails transacionais e recuperação de senha

Detalhes operacionais do subsistema de e-mail (padrão outbox) e do rate limiting das rotas de recuperação de senha. Para visão geral do projeto, veja o [README](../README.md).

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

### Rate limit da recuperação de senha

`POST /recuperar-senha` e `POST /redefinir-senha` têm **rate limit in-memory** (janela fixa simples, por chave) que protege contra enumeração de contas, força bruta de tokens e flood de outbox. Implementação: `config/RecoveryRateLimiter` + `config/ClientIpResolver`.

Comportamento ao atingir o limite:

- **A resposta é sempre 200 com a MESMA mensagem genérica** ("Se o e-mail estiver cadastrado, ...") — idêntica byte a byte à resposta de e-mail inexistente. Nada na resposta revela que o limite agiu (responder 429 criaria um oráculo de enumeração). Em `/recuperar-senha`, a tentativa limitada **não gera token e não enfileira e-mail**; em `/redefinir-senha`, **não valida o token** (responde "Link inválido ou expirado.", igual a um token errado). Cada bloqueio registra log `WARN` no servidor.
- **Vale sempre**, inclusive com `PRATOJA_MAIL_ENABLED=false` (o endpoint existe sempre; com o mail desligado o custo restante é a geração de token).
- Duas dimensões independentes: **por IP** (buckets separados para os dois endpoints) e **por e-mail** (normalizado, case-insensitive), este último imune a spoofing de IP e responsável por limitar os enqueues por conta quando o mail estiver ligado.
- O estado é **em memória da instância**: restart/redeploy zera os contadores e réplicas não compartilham estado. Proteção contra abuso/enumeração, não defesa de infraestrutura.
- **IP do cliente:** atrás do proxy do Render (1 hop confiável) é usada a entrada **mais à direita** de `X-Forwarded-For` (a anexada pelo proxy); entradas à esquerda vêm do cliente e são ignoradas por serem spoofáveis. Sem o header (acesso direto/local), usa-se `request.getRemoteAddr()`. Por isso o limite **não** usa `server.forward-headers-strategy=framework` — o `ForwardedHeaderFilter` adotaria a primeira entrada, que o atacante controla.

| Property (override por ambiente) | Default | Descrição |
|---|---|---|
| `pratoja.recovery.rate-limit.max-attempts-per-ip` (`PRATOJA_RATE_LIMIT_IP_MAX`) | `5` | Máximo de tentativas por IP por janela em cada um dos endpoints de recuperação/redefinição |
| `pratoja.recovery.rate-limit.window-minutes` (`PRATOJA_RATE_LIMIT_IP_WINDOW_MINUTES`) | `10` | Janela fixa (minutos) dos buckets por IP |
| `pratoja.recovery.rate-limit.max-per-email` (`PRATOJA_RATE_LIMIT_EMAIL_MAX`) | `3` | Máximo de tentativas de recuperação por e-mail por janela (limita tokens gerados/enqueued por conta) |
| `pratoja.recovery.rate-limit.email-window-minutes` (`PRATOJA_RATE_LIMIT_EMAIL_WINDOW_MINUTES`) | `60` | Janela fixa (minutos) do bucket por e-mail |

### Instruções Brevo

1. No painel Brevo: **SMTP & API → Senders & IP**; crie/garanta um sender para o `MAIL_FROM` (ex.: `notificacoes@pratoja.app`).
2. Em **SMTP & API → SMTP**, gere a **SMTP key** e use: `SMTP_HOST=smtp-relay.brevo.com`, `SMTP_PORT=587`, `SMTP_USER=<login Brevo>`, `SMTP_PASSWORD=<smtp key>`.
3. Defina `PRATOJA_MAIL_ENABLED=true`, `MAIL_FROM` e `MAIL_BASE_URL` (URL pública do deploy).
4. O monitor de falhas é o próprio outbox: linhas `FAILED` têm `last_error` e `next_attempt_at`; `DEAD` indica entrega não concluída após 5 tentativas.
