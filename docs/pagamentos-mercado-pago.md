# Pagamentos online — Mercado Pago (Pix por loja)

Cada loja conecta a **própria conta do Mercado Pago** (OAuth) e recebe o Pix direto nela.
A plataforma nunca toca no dinheiro do pedido. Enquanto a loja não conecta, o checkout segue
em **modo demonstração** (confirmação simulada) e o dinheiro na entrega continua igual.

## Fluxo do cliente (loja conectada)

1. Checkout com PIX ou CARTÃO cria o pedido com pagamento `PENDING` + referência única (UUID).
2. Pix: a tela `/loja/{slug}/pedidos/{id}/pagamento` cria a cobrança na API do MP
   (idempotente pela referência, expiração de 2h) e mostra QR dinâmico + copia-e-cola.
3. Cartão: a mesma tela tokeniza o cartão NO NAVEGADOR via MercadoPago.js (public key da loja)
   e envia apenas o token ao servidor — dados de cartão nunca passam pelo PratoJá.
4. A confirmação chega pelo **webhook** `/webhooks/mercadopago` (ou pela resposta da criação,
   no cartão) e o pedido avança RECEBIDO → CONFIRMADO automaticamente (SSE).
5. O botão "Já paguei, verificar" e o **reconciliador agendado** (15min) re-sincronizam
   pela API — cobrem webhook perdido e falhas de entrega.

## Defesas implementadas (webhook → cozinha)

- Assinatura `x-signature` HMAC-SHA256 validada (janela de 5 min) — sem segredo configurado o endpoint falha fechado (503).
- O POST nunca é confiado: o estado é **re-consultado na API** com o token da loja dona do pagamento.
- Conferência de `external_reference` == referência do pedido, **valor** == total, **moeda** == BRL e
  **collector_id** == conta conectada da loja antes de confirmar.
- Idempotência por chave única `(provider, event_key)` (id de evento do MP). O evento só é marcado
  APÓS o processamento: falha na API do MP gera 500 e o provedor reenvia (nada se perde).
  Entregas simultâneas convergem porque as mutações de estado são idempotentes.
- Estorno/cancelamento/chargeback/expiração cancelam pagamento e pedido (se ainda não entregue);
  eventos fora de ordem são tolerados.
- Tokens OAuth das lojas criptografados com AES-256-GCM (`PRATOJA_PAYMENTS_CRYPTO_KEY`); falha fechada sem a chave.
- Política de modo: conexão em modo TEST **só aceita credencial de teste do MP (`TEST-...`)** e PRODUCTION
  só `APP_USR-...` — verificado na conexão E a cada uso; impossível cobrar de verdade sob modo de testes.
- Só o status `approved` (validado) confirma pagamento e avança o pedido para a cozinha.

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

- E2E com credenciais sandbox reais do MP e entrega real de webhook depende da ativação acima;
  os testes automatizados cobrem o fluxo completo contra um servidor stub da API (assinatura, idempotência,
  divergência de valor/collector, estorno, expiração, reconciliação, cartão aprovado/recusado,
  isolamento por loja). A tokenização do cartão no navegador (MercadoPago.js) só pode ser
  exercida de ponta a ponta com uma public key de teste real.
- Parcelamento real (tabela de parcelas/issuer) hoje assume 1x; o fluxo de issuer/installments
  completo é refinado na homologação com credenciais reais.
- Conciliação/relatório de pagamentos no admin: etapa futura (hoje o status fica no pedido e no histórico).
