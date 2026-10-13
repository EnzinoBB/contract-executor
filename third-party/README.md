# Vendored Maven artifacts

`mvn-repo/` holds the only artifacts the build used to download from the `mvn-repo` branches of
the CREDITSCOM repositories declared in the poms (`raw.github.com/creditscom/{sc-api,thrift-api-client,contract-executor}/mvn-repo/`).
They are copied byte for byte, with their `.sha1` and `.md5` files, from the `mvn-repo` branch of
CREDITSCOM/thrift-api-client (commit `8ebdd22`, September 2019):

| Artifact | Used by |
|---|---|
| `com.credits.client:general:1.2` (jar, pom) | `executor_client` 1.4, `contract-executor` |
| `com.credits.client:node_client:1.4` (jar, pom) | `executor_client` 1.4, `contract-executor` |

Their parent `com.credits.client:api-client:none` is installed by the thrift-api-client build, and
the sc-api and contract-executor branches provide nothing the build uses.

`maven-settings.xml` mirrors every repository except Maven Central to this directory; the
workflow passes it to every `mvn` call.
