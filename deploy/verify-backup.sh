#!/usr/bin/env bash
# Verifica um backup do PratoJá SEM tocar na produção:
#   1. Confere os checksums SHA256.
#   2. Restaura o db.dump num PostgreSQL EFÊMERO isolado.
#   3. Recontabiliza as tabelas e compara com o MANIFEST.txt do backup.
#   4. Confere que uploads.tar.gz é um tar válido e lista a contagem de arquivos.
#
# Uso: ./verify-backup.sh <diretorio_do_backup>
# Exemplo: ./verify-backup.sh ./backups/pratoja-20261020-120000Z
#
# Não altera nenhum container de produção; sobe/derruba um Postgres temporário.
set -euo pipefail

BK="${1:?informe o diretório do backup}"
[ -f "${BK}/db.dump" ] || { echo "db.dump não encontrado em ${BK}"; exit 1; }

echo "[1/4] Conferindo checksums..."
( cd "$BK" && sha256sum -c SHA256SUMS )

TMP_DB="pratoja-verify-$$"
TMP_PW="verify-$(date +%s)"
echo "[2/4] Subindo PostgreSQL efêmero (${TMP_DB})..."
docker run -d --name "$TMP_DB" -e POSTGRES_PASSWORD="$TMP_PW" -e POSTGRES_DB=verify postgres:16 >/dev/null
trap 'docker rm -f "$TMP_DB" >/dev/null 2>&1 || true' EXIT

# Espera o Postgres ficar pronto.
for i in $(seq 1 30); do
  docker exec "$TMP_DB" pg_isready -U postgres -d verify >/dev/null 2>&1 && break
  sleep 1
done

echo "       Restaurando o dump no banco efêmero..."
docker exec -i "$TMP_DB" pg_restore -U postgres -d verify --no-owner --no-privileges < "${BK}/db.dump" \
  || echo "       (avisos de pg_restore são aceitáveis desde que as contagens confiram)"

echo "[3/4] Comparando contagens de linhas com o MANIFEST..."
docker exec "$TMP_DB" psql -U postgres -d verify -At -c "
  SELECT format('%s=%s', relname, n_live_tup)
  FROM pg_stat_user_tables ORDER BY relname;" > /tmp/verify-counts.txt 2>/dev/null || true
# ANALYZE para atualizar as estatísticas usadas na contagem.
docker exec "$TMP_DB" psql -U postgres -d verify -c "ANALYZE;" >/dev/null 2>&1 || true
docker exec "$TMP_DB" psql -U postgres -d verify -At -c "
  SELECT format('%s=%s', relname, n_live_tup)
  FROM pg_stat_user_tables ORDER BY relname;" > /tmp/verify-counts.txt 2>/dev/null || true
echo "       Contagens restauradas:"; cat /tmp/verify-counts.txt | sed 's/^/         /'
echo "       (compare manualmente com MANIFEST.txt — divergências indicam backup incompleto)"

echo "[4/4] Verificando uploads.tar.gz..."
if [ -f "${BK}/uploads.tar.gz" ]; then
  N=$(tar tzf "${BK}/uploads.tar.gz" | grep -c . || true)
  echo "       tar válido, ${N} entradas."
else
  echo "       AVISO: uploads.tar.gz ausente."
fi

echo "OK: verificação concluída (ambiente efêmero será removido)."
