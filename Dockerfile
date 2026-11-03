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

# JVM ciente de container: respeita os limites de memória do cgroup do contêiner
# em vez de enxergar a RAM total do host — a instância AWS divide a máquina com
# PostgreSQL e Caddy. 70/40 deixa margem para metaspace, threads e off-heap.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40"

# Health real: HTTP no endpoint público /login (permitAll, 200 sem autenticação).
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/login >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
