# SAP source provenance

Upstream: https://github.com/SAP/olingo-jpa-processor-v4
Version tag: 2.5.0
Commit: 53976fd (GitHub archive prefix)
Source archive: https://api.github.com/repos/SAP/olingo-jpa-processor-v4/zipball/2.5.0
License: Apache License 2.0 (see adjacent LICENSE file)

The `jpa` source tree is included to bootstrap unpublished Maven artifacts without requiring credentials. The application uses odata-jpa-processor and odata-jpa-processor-cb. Upstream source is not modified by application code. The criteria-builder enhancement is included but not activated via an EntityManagerFactory wrapper; this POC has no collection expands requiring its window-function support. SAP JPA Processor is what the user's “SAP SQL preprocessor” request is interpreted to mean. The optional index policy is this application's JDBC preprocessor; it is not a SAP product feature.
