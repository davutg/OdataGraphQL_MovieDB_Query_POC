$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
mvn -f vendor/sap/jpa/pom.xml -pl odata-jpa-processor,odata-jpa-processor-cb -am install -Dmaven.test.skip=true -Dduplicate-finder.skip=true
if ($LASTEXITCODE -ne 0) { throw 'SAP dependency build failed' }
