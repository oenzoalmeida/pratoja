#!/usr/bin/env bash
# Restaura um backup do PratoJá para a stack de produção.
# ATENÇÃO: sobrescreve os dados atuais do banco e do volume de uploads.
# Use somente no corte (cutover) ou em recuperação de desastre, com a stack parada
# ou em janela controlada. Peça confirmação explícita antes de rodar em produção.
#
# Uso: ./restore.sh <diretorio_do_backup>
set -euo pipefail

cd "$(dirname "$0")"
BK="${1:?informe o diretório do backup}"
[ -f .env ] && set -a && . ./.env && set +a
DB_NAME="${DB_NAME:-pratoja}"
DB_USER="${DB_USER:-pratoja}"

COMPOSE="docker compose"
$COMPOSE version >/dev/null 2>&1 || COMPOSE="docker-compose"

echo "ATENÇÃO: isto substitui os dados atuais do PratoJá."
read -r -p "Digite 'RESTAURAR' para continuar: " ans
[ "$ans" = "RESTAURAR" ] || { echo "Abortado."; exit 1; }

echo "[1/4] Conferindo checksums..."
( cd "$BK" && sha256sum -c SHA256SUMS )

echo "[2/4] Parando o app (evita escrita concorrente)..."
$COMPOSE stop app

echo "[3/4] Recriando o banco e restaurando o dump..."
$COMPOSE exec -T db psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS ${DB_NAME};"
$COMPOSE exec -T db psql -U "$DB_USER" -d postgres -c "CREATE DATABASE ${DB_NAME};"
$COMPOSE exec -T db pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges < "${BK}/db.dump"

echo "[4/4] Restaurando o volume de uploads..."
docker run --rm \
  -v pratoja_uploads:/dst \
  -v "$(cd "$BK" && pwd)":/bk:ro \
  alpine:3 sh -c "rm -rf /dst/* && tar xzf /bk/uploads.tar.gz -C /dst"

echo "Subindo o app..."
$COMPOSE up -d app
echo "OK: restauração concluída. Valide login, pedidos e imagens antes do cutover."
