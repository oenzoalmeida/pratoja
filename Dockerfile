FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/pratoja-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Flags para o plano Free do Render (512 MB RAM, ~0.1 CPU):
#  - MaxRAMPercentage=75 : heap máx ~384 MB de 512 MB, deixando ~128 MB para metaspace,
#    stack de threads e off-heap (evita OOM killer sem starvar o heap).
#  - InitialRAMPercentage=50 : heap inicial menor no boot (menos pico de alocação
#    durante o cold start).
#  - UseSerialGC : em heap pequeno (~384 MB) e CPU limitada, o Serial GC tem menor
#    pegada de memória nativa (sem threads de GC paralelas/concorrentes do G1) e
#    pausas curtas suficientes para o tráfego de demo. G1 só compensa em heaps maiores.
#  - ExitOnOutOfMemoryError : em OOM o processo sai imediatamente e o Render reinicia
#    o contêiner, em vez de servir erros de um processo zombie.
#  - TieredStopAtLevel=1 foi AVALIADO E DESCARTADO: acelera o boot (só compilador C1),
#    mas limita o JIT a otimizações rasas para sempre — degrada latência e CPU
#    no estado estacionário, o oposto do objetivo. Comportamento do app é inalterado.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:InitialRAMPercentage=50", "-XX:+UseSerialGC", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
