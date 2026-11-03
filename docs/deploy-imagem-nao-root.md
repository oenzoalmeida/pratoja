# Deploy da imagem não-root — pré-condição de volume

A imagem passa a rodar como usuário `app` (**UID/GID fixos 10001**, ver Dockerfile).
Antes do primeiro deploy desta imagem, o volume de uploads precisa pertencer a esse UID.

## Estado medido em produção (03/nov/2026, leitura sem alteração)

- `/srv/pratoja/docker-compose.yml`: serviço app monta `pratoja-uploads:/app/data/uploads`
- Volume externo real: `pratoja-uploads`
- `/var/lib/docker/volumes/pratoja-uploads/_data`: `755 uid 0 gid 0` (root), igual por dentro
- Volume **vazio** na data da leitura; app atual roda como root

## Pré-condição (executar uma vez, antes do primeiro up da nova imagem)

```sh
docker run --rm -v pratoja-uploads:/data alpine chown -R 10001:10001 /data
```

Sem esse ajuste o app não-root **não consegue gravar** em `/app/data/uploads`
(uploads de imagens de produtos falham). O volume estar vazio hoje não elimina
o passo: o diretório raiz do volume já pertence a root, e uploads podem existir
na data do deploy.

## Rollback

A imagem anterior roda como root, que lê e grava independente do dono do volume.
Reverter para a imagem antiga não exige reverter o chown.

## Verificação pós-deploy

```sh
docker exec <container> id            # esperado: uid=10001(app) gid=10001(app)
docker exec <container> touch /app/data/uploads/.rw-test && rm ...
curl -fsS https://18.220.70.13.sslip.io/login
```
