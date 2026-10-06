# Java 21 application module

This module reuses the maintained app-java17 source/test/resource tree and the
same Jakarta dependencies, but compiles to Java 21 bytecode and packages a
separate movie-query-poc-java21-1.0.0.jar. It is tested and executed on real JDK/JRE 21.

With JAVA_HOME set to JDK 21, bootstrap SAP via scripts/bootstrap-sap.sh, then:
`mvn -Pjava21 -pl app-java21 -am verify -Pintegration`.

Alternatively use `bash scripts/verify-matrix.sh` with Docker/OrbStack. Start
all variants with `docker compose --profile variants up -d --build --wait` and
open http://localhost:8081. See ../docs/java-versions.md.
