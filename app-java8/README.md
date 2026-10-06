# Java 8 application adapter

Build from the repository root with `bash scripts/bootstrap-sap-java8.sh`, then
`mvn -Pjava8 -pl app-java8 -am verify -Pintegration` using JDK 8 or a newer JDK
supporting --release 8. Actual Java 8 runtime verification is performed by
`bash scripts/verify-matrix.sh`.

This module uses Spring Boot 2.7 / javax and SAP 1.1.1. Its API paths, GraphQL
schema, visual query builder, seed and safeguards match the Java 17 application.
The modern stack never enters this adapter's dependency graph. Resources are
shared with app-java17; framework configuration and Java adapter sources are local.

Run with `docker compose --profile variants up -d --build --wait` and open
http://localhost:8082. See ../docs/java-versions.md for the verified matrix.
