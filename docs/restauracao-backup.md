# Restauração e verificação de backup do PostgreSQL (PratoJá na AWS)

> Documento de procedimento. Os comandos abaixo NÃO foram executados em ambiente real ao escrever este texto. Teste primeiro com um dump de teste.

## Formato do backup

O `backup.sh` do servidor gera dumps com `pg_dump -Fc` (formato custom do PostgreSQL). Esse formato **não** pode ser restaurado com `psql` (por exemplo `cat dump | psql`): isso falha. Use `pg_restore`.

Para conferir o formato de um arquivo: `pg_restore -l ARQUIVO.dump | head` (lista o conteúdo; só inspeciona, não prova que a restauração completa funciona).

## Regra principal

Nunca restaure por cima do banco em produção como forma de "testar". Verifique o dump num contêiner novo e isolado. Uma restauração em produção só deve ser feita com decisão explícita do dono, com backup recente e o app parado.

## Verificar um dump em um banco isolado (descartável)

Condições:

- Contêiner novo `postgres:17`, com nome único (não use o nome `db` nem nada que já exista).
- Sem volume de produção, sem rede de produção, sem publicar portas.
- Senha efêmera gerada na hora, não reaproveitada de `db.env`.
- Usar uma cópia do dump (nunca o arquivo original) e nunca colocar um dump real no GitHub ou em artefatos públicos.

Passos (executar no servidor ou em uma máquina com Docker e acesso ao arquivo):

```bash
set -euo pipefail
DUMP=/caminho/da/COPIA-do-dump.dump
NAME="pratoja-verify-$(date +%s)"
PW="$(openssl rand -hex 16)"

docker run -d --name "$NAME" -e POSTGRES_PASSWORD="$PW" -e POSTGRES_USER=pratoja -e POSTGRES_DB=pratoja postgres:17

# aguardar o banco ficar pronto
until docker exec "$NAME" pg_isready -U pratoja -d pratoja; do sleep 1; done

# restaurar (falha ao primeiro erro; sem donos nem privilégios do original)
docker exec -i "$NAME" pg_restore --exit-on-error --no-owner --no-privileges -U pratoja -d pratoja < "$DUMP"

# validar: tabelas e contagens esperadas
docker exec "$NAME" psql -U pratoja -d pratoja -c '\dt'
docker exec "$NAME" psql -U pratoja -d pratoja -c 'select count(*) from flyway_schema_history;'
# comparar as contagens de tabelas principais com o que se espera do banco de origem

# limpeza: remove apenas o contêiner criado neste teste
docker rm -f "$NAME"
```

Resultado esperado: `pg_restore` termina sem erro, as tabelas aparecem e as contagens batem com o esperado. Se algo falhar, o backup não é confiável: investigue antes de depender dele.

## Observações

- O serviço de produção usa o serviço `db` (`postgres:17`) no `docker compose` do servidor, com banco e usuário `pratoja`. Para qualquer comando dentro dele, use o nome real do serviço/contêiner definido no compose do servidor.
- `--clean`/`--if-exists` apagam objetos existentes. Não use em produção sem decisão explícita e backup validado.
- Listar o dump com `pg_restore -l` não prova que a restauração completa funciona; só o passo de restauração isolado prova.
