# syntax=docker/dockerfile:1
# Imagem multi-arquitetura: as bases eclipse-temurin:21-* publicam linux/amd64 e
# linux/arm64/v8, então a mesma Dockerfile serve para a Blitz.cloud (amd64) e para
# a instância Oracle Ampere A1 (arm64). Nenhuma dependência nativa específica de
# arquitetura é usada (JVM pura: Spring Boot, Flyway, PostgreSQL JDBC, Thymeleaf).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# curl apenas para o HEALTHCHECK (o endpoint público /login responde 200 sem auth).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Usuário sem privilégios (menor privilégio). O diretório ./data guarda o upload
# de imagens de produtos e, no modo H2, o banco em arquivo; precisa ser gravável
# e é montado como volume persistente em produção.
RUN groupadd --system app && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/data \
    && chown -R app:app /app
USER app

COPY --from=build --chown=app:app /app/target/pratoja-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Flags JVM para memória limitada (Render Free 512 MB / instância compartilhada Oracle A1):
#  - MaxRAMPercentage=75 : heap máx ~75% do limite do container (~384 MB em 512 MB),
#    deixando o resto para metaspace, stack de threads e off-heap (evita OOM killer
#    sem starvar o heap). A JVM é ciente de cgroup: respeita o limite de memória do
#    container em vez de enxergar a RAM total do host.
#  - InitialRAMPercentage=50 : heap inicial menor no boot (menos pico de alocação
#    durante o cold start).
#  - UseSerialGC : em heap pequeno (~384 MB) e CPU limitada, o Serial GC tem menor
#    pegada de memória nativa (sem threads de GC paralelas/concorrentes do G1) e
#    pausas curtas suficientes para o tráfego de demo. G1 só compensa em heaps maiores.
#  - ExitOnOutOfMemoryError : em OOM o processo sai imediatamente e a plataforma
#    (Render/Oracle) reinicia o contêiner, em vez de servir erros de um processo zombie.
#  - TieredStopAtLevel=1 foi AVALIADO E DESCARTADO: acelera o boot (só compilador C1),
#    mas limita o JIT a otimizações rasas para sempre — degrada latência e CPU
#    no estado estacionário, o oposto do objetivo. Comportamento do app é inalterado.
# JAVA_OPTS é expansível em runtime (docker run -e JAVA_OPTS=...), sobrescrevendo o padrão.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Health real: HTTP no endpoint público /login (permitAll, 200 sem autenticação).
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/login >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
