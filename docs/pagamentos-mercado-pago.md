# Pagamentos online — Mercado Pago (Pix por loja)

Cada loja conecta a **própria conta do Mercado Pago** (OAuth) e recebe o Pix direto nela.
A plataforma nunca toca no dinheiro do pedido. Enquanto a loja não conecta, o checkout segue
em **modo demonstração** (confirmação simulada) e o dinheiro na entrega continua igual.

## Fluxo do cliente (loja conectada)

1. Checkout com PIX cria o pedido com pagamento `PENDING` + referência única (UUID).
2. A tela `/loja/{slug}/pedidos/{id}/pagamento` cria a cobrança na API do MP
   (idempotente pela referência) e mostra QR dinâmico + copia-e-cola.
3. A confirmação chega pelo **webhook** `/webhooks/mercadopago` e o pedido avança
   RECEBIDO → CONFIRMADO automaticamente (SSE atualiza painel e cliente).
4. O botão "Já paguei, verificar" faz a mesma verificação pela API (fallback sem webhook).

## Defesas implementadas (webhook → cozinha)

- Assinatura `x-signature` HMAC-SHA256 validada (janela de 5 min) — sem segredo configurado o endpoint falha fechado (503).
- O POST nunca é confiado: o estado é **re-consultado na API** com o token da loja dona do pagamento.
- Conferência de `external_reference` == referência do pedido, **valor** == total e **moeda** == BRL antes de confirmar.
- Idempotência por chave única `(provider, event_key)` (id de evento do MP) — reenvio não reprocessa.
- Estorno/cancelamento/chargeback cancelam pagamento e pedido (se ainda não entregue); webhooks fora de ordem são tolerados.
- Tokens OAuth das lojas criptografados com AES-256-GCM (`PRATOJA_PAYMENTS_CRYPTO_KEY`); falha fechada sem a chave.

## Variáveis de ambiente (servidor)

| Variável | Uso |
| --- | --- |
| `MP_CLIENT_ID` / `MP_CLIENT_SECRET` | Aplicação (integrador) do PratoJá no Mercado Pago — usada no OAuth das lojas |
| `MP_WEBHOOK_SECRET` | Segredo da assinatura dos webhooks (configurado no painel do MP) |
| `PRATOJA_PAYMENTS_CRYPTO_KEY` | AES-256 base64 (32 bytes) p/ tokens das lojas — gerar com `openssl rand -base64 32` |
| `MP_API_BASE_URL` / `MP_AUTH_BASE_URL` | Overrides (padrão produção; usados em testes com stub) |
| `MP_DEFAULT_MODE` | `TEST` (padrão) ou `PRODUCTION` — gravado na conexão de cada loja |
| `PUBLIC_BASE_URL` | Já existente: base da URL de callback OAuth e do `notification_url` |

## Ativação real (pendências do provedor — fora do código)

1. Criar a aplicação do PratoJá no painel de desenvolvedores do Mercado Pago (conta do integrador).
2. Cadastrar a URL de callback: `{PUBLIC_BASE_URL}/admin/pagamentos/mercado-pago/callback`.
3. Configurar o webhook `{PUBLIC_BASE_URL}/webhooks/mercadopago` (eventos de pagamento) e guardar o segredo em `MP_WEBHOOK_SECRET`.
4. Definir `MP_CLIENT_ID`/`MP_CLIENT_SECRET`/`PRATOJA_PAYMENTS_CRYPTO_KEY` no ambiente e recriar o container.
5. Homologar com **contas de teste** do MP (vendedor + comprador) antes de virar `MP_DEFAULT_MODE=PRODUCTION`.

## Limites desta etapa (documentados)

- **Cartão online não implementado** — no checkout o cartão segue simulado; Pix e dinheiro são os caminhos reais.
- E2E com credenciais sandbox reais do MP e entrega real de webhook depende da ativação acima;
  os testes automatizados cobrem o fluxo completo contra um servidor stub da API (assinatura, idempotência,
  divergência de valor, estorno, isolamento por loja).
- Conciliação/relatório de pagamentos no admin: etapa futura (hoje o status fica no pedido e no histórico).
