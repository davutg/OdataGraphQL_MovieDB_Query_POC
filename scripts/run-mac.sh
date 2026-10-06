#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Prefer a real Java 17 installation over the machine's default Java.
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null) || JAVA_HOME=""
  if [[ -z "$JAVA_HOME" ]]; then
    for candidate in "$HOME"/.sdkman/candidates/java/17*; do
      if [[ -x "$candidate/bin/java" ]]; then JAVA_HOME="$candidate"; break; fi
    done
  fi
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Set JAVA_HOME to a JDK 17 installation." >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
java -version
command -v mvn >/dev/null
docker info >/dev/null
docker compose stop app
docker compose up -d --wait db
bash scripts/verify-java17.sh -B -ntp
exec java -jar app-java17/target/movie-query-poc-1.0.0.jar
