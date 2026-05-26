# ── Stage 1: Build the JAR ──────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now copy source and build
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Runtime image (smaller, just the JRE + JAR) ────────
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/url-shortener-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xmx400m -Xms200m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]