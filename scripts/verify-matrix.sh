#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --wait db
db_id=$(docker compose ps -q db)
network=$(docker inspect "$db_id" --format '{{range $name,$value := .NetworkSettings.Networks}}{{$name}}{{end}}')
mkdir -p "$HOME/.m2" "$PWD/work/matrix"
run_maven() {
  local version=$1
  shift
  docker run --rm --network "$network" \
    -e 'DB_URL=jdbc:postgresql://db:5432/movies?prepareThreshold=0' \
    -v "$PWD:/source:ro" -v "$HOME/.m2:/root/.m2" \
    -v "$PWD/work/matrix:/reports" -e "MATRIX_JAVA=$version" -w /build \
    "maven:3.9.9-eclipse-temurin-$version" bash -c '
      set -uo pipefail
      # Isolate compiler output from host IDE auto-builds and other JVM targets.
      tar -C /source --exclude=.git --exclude=target --exclude=work -cf - . | tar -C /build -xf - || exit $?
      mvn -B -ntp "$@"
      result=$?
      for module in app-java8 app-java17 app-java21; do
        for kind in surefire-reports failsafe-reports; do
          if test -d "$module/target/$kind"; then
            mkdir -p "/reports/java$MATRIX_JAVA/$module"
            cp -R "$module/target/$kind" "/reports/java$MATRIX_JAVA/$module/"
          fi
        done
      done
      exit "$result"
    ' bash "$@"
}
run_maven 8 -f vendor/sap-java8/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
run_maven 17 -f vendor/sap/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
# Run sequentially against the same PostgreSQL seed and shared Maven cache.
run_maven 8 -Pjava8 -pl app-java8 -am clean verify -Pintegration
run_maven 17 -pl app-java17 -am clean verify -Pintegration
run_maven 21 -Pjava21 -pl app-java21 -am clean verify -Pintegration
