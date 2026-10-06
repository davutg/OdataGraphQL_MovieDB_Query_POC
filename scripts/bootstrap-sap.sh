#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -f vendor/sap/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
