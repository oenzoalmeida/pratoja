#!/usr/bin/env bash
# Backup consistente do PratoJá: banco PostgreSQL + volume de uploads.
# Gera um diretório datado com:
#   - db.dump        (pg_dump -Fc, formato custom, restaurável com pg_restore)
#   - uploads.tar.gz (conteúdo do volume de imagens de produtos)
#   - MANIFEST.txt   (contagens por tabela + checksums para verificação)
#
# Uso (na VM, a partir da pasta deploy/):
#   ./backup.sh [diretorio_destino]        # padrão: ./backups
#
# Não imprime segredos. Lê as credenciais do mesmo deploy/.env usado pelo compose.
set -euo pipefail

cd "$(dirname "$0")"
[ -f .env ] && set -a && . ./.env && set +a

DB_NAME="${DB_NAME:-pratoja}"
DB_USER="${DB_USER:-pratoja}"
DEST_ROOT="${1:-./backups}"
STAMP="$(date -u +%Y%m%d-%H%M%SZ)"
OUT="${DEST_ROOT}/pratoja-${STAMP}"
mkdir -p "$OUT"

COMPOSE="docker compose"
$COMPOSE version >/dev/null 2>&1 || COMPOSE="docker-compose"

echo "[1/4] pg_dump (formato custom, consistente)..."
$COMPOSE exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner --no-privileges \
  > "${OUT}/db.dump"

echo "[2/4] Arquivando o volume de uploads..."
# Usa um container efêmero para ler o volume montado pelo serviço app.
docker run --rm \
  -v pratoja_uploads:/src:ro \
  -v "$(cd "$OUT" && pwd)":/out \
  alpine:3 sh -c "cd /src && tar czf /out/uploads.tar.gz . 2>/dev/null || (mkdir -p /src && tar czf /out/uploads.tar.gz -C /src .)"

echo "[3/4] Coletando contagens por tabela (verificação de integridade)..."
{
  echo "# PratoJá backup ${STAMP}"
  echo "# Contagem de linhas por tabela (public):"
  $COMPOSE exec -T db psql -U "$DB_USER" -d "$DB_NAME" -At -c "
    SELECT format('%s=%s', relname, n_live_tup)
    FROM pg_stat_user_tables ORDER BY relname;" 2>/dev/null || true
  echo "# flyway_schema_history (versões aplicadas):"
  $COMPOSE exec -T db psql -U "$DB_USER" -d "$DB_NAME" -At -c "
    SELECT format('%s:%s:%s', installed_rank, version, success)
    FROM flyway_schema_history ORDER BY installed_rank;" 2>/dev/null || true
} > "${OUT}/MANIFEST.txt"

echo "[4/4] Checksums..."
( cd "$OUT" && sha256sum db.dump uploads.tar.gz > SHA256SUMS )

echo "OK: backup em ${OUT}"
echo "Verifique com: ./verify-backup.sh ${OUT}"
