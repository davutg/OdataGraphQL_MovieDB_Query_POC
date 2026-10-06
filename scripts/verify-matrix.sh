#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --wait db
db_id=$(docker compose ps -q db)
network=$(docker inspect "$db_id" --format '{{range $name,$value := .NetworkSettings.Networks}}{{$name}}{{end}}')
mkdir -p "$HOME/.m2"
run_maven() {
  local version=$1
  shift
  docker run --rm --network "$network" \
    -e 'DB_URL=jdbc:postgresql://db:5432/movies?prepareThreshold=0' \
    -v "$PWD:/project" -v "$HOME/.m2:/root/.m2" -w /project \
    "maven:3.9.9-eclipse-temurin-$version" mvn -B -ntp "$@"
}
run_maven 8 -f vendor/sap-java8/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
run_maven 17 -f vendor/sap/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
# Run sequentially: the reactor modules share query-core's target directory.
run_maven 8 -Pjava8 -pl app-java8 -am clean verify -Pintegration
run_maven 17 -pl app-java17 -am clean verify -Pintegration
run_maven 21 -Pjava21 -pl app-java21 -am clean verify -Pintegration
