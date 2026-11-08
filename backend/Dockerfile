# The API as one container: built from source with the Maven wrapper, run on a JRE. Tests run in CI, not here.
# Sized for a 512 MB instance (Render's free plan): heap at most 55% of memory, the serial collector, small stacks.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp -q dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -q package -DskipTests && cp target/paynudge-api-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 paynudge
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER paynudge
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
