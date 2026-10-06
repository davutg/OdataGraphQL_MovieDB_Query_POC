#!/bin/sh
set -eu
cd "$(dirname "$0")"
rm -rf target/contracts
mkdir -p target/contracts
find src/main/java src/test/java -name '*.java' > target/sources.txt
"${JAVAC:-javac}" --release 8 -Xlint:all -Werror -d target/contracts @target/sources.txt
"${JAVA:-java}" -cp target/contracts dev.poc.core.CoreContractTest
"${JAVA:-java}" -cp target/contracts dev.poc.core.ExtendedContractTest
