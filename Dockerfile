FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY vendor/sap/ /build/sap/
RUN mvn -q -f sap/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
COPY pom.xml /build/app/pom.xml
COPY query-core /build/app/query-core
COPY app-java17 /build/app/app-java17
WORKDIR /build/app
RUN mvn -q verify
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/app/app-java17/target/movie-query-poc-1.0.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
