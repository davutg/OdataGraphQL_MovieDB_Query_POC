# SAP source for the Java 8 adapter

- Upstream: https://github.com/SAP/olingo-jpa-processor-v4
- Tag: 1.1.1; source archive root commit prefix: 7c52e19.
- Archive: https://api.github.com/repos/SAP/olingo-jpa-processor-v4/zipball/1.1.1
- License: Apache License 2.0; see LICENSE.txt.
- The jpa source tree is vendored without changes. It builds separately from
  vendor/sap (2.5.0) and installs different artifact versions.
- Java 8 uses javax.persistence, Olingo 4.9 and SAP 1.1.1. Java 17/21 use
  Jakarta Persistence, Olingo 5 and SAP 2.5.0. Neither adapter puts those
  framework types in query-core.
