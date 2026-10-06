#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B -ntp -f vendor/sap-java8/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
