# Building contract-executor build 1518

Build 1518 (commit `ca2b28bb`, June 2024) is the executor shipped with node release
`mainnet-4.2.532.0`. It no longer builds from source as published, for three reasons:

| Problem | Fix used here |
|---|---|
| `sc-api-internal` 2.2 (smart-contract-api), `general` 1.3 and `executor_client` 1.4 (thrift-api-client) were never published to the `mvn-repo` branches the poms point at (they stop at 2.1, 1.2 and 1.2, September 2019) | build and `mvn install` them from their repositories first |
| thrift-api-client's default branch has a dependabot bump to libthrift 0.14, whose module name breaks `executor_client`'s `module-info` | use thrift-api-client at `59d1e9af`, the last commit before it |
| `org.eclipse.jdt.core` and the Eclipse platform bundles declare open version ranges, which now resolve to jars built for Java 17 | pin them (`dependencyManagement` in the root poms) to the exact versions bundled in build 1518, matched class by class |
| `node_client` 1.4 and `general` 1.2 come only from the `mvn-repo` branch of CREDITSCOM/thrift-api-client | a copy in `third-party/mvn-repo`, which `third-party/maven-settings.xml` mirrors every repository except Maven Central to (see `third-party/README.md`) |

Two build-environment details also matter:

- `maven-assembly-plugin` is pinned to 2.2-beta-5. That is the version Maven 3.6.1 selects by default, and newer versions merge the duplicate `module-info.class` of the `sc-api-support` jar differently.
- The build uses JDK 11.0.3, as build 1518 did. Later 11.0.x `javac` versions generate different string-concatenation code in a few `toString()` methods.

## Steps

```sh
# in this repository, with its full git history
export VENDORED_MAVEN_REPO=$PWD/third-party/mvn-repo
MVN="mvn -s $PWD/third-party/maven-settings.xml"
git clone -b build/reproducible-1518 https://github.com/EnzinoBB/smart-contract-api.git   # 677c567f
git clone -b build/reproducible-1518 https://github.com/EnzinoBB/thrift-api-client.git
(cd thrift-api-client && $MVN -DskipTests install)
(cd smart-contract-api && $MVN -DskipTests install)
$MVN -DskipTests package
```

The jars are `contract-executor/target/contract-executor-v0.8.4-b<N>.jar` and
`sc-api-support/target/sc-api-support-v0.8.4-b<N>.jar`.

## Result

`.github/workflows/build.yml` runs these steps, then compares every class with the jars of
the release. 14,057 of 14,058 executor classes and 41 of 42 `sc-api-support` classes are
byte-identical. Each `module-info.class` differs only in the JDK version string it records:
`11.0.3-ojdkbuild` in the release, against `11.0.3` from the Zulu JDK used here.

## Commit count

The node checks the executor's total commit count against `executor_commit_min` and
`executor_commit_max`, and the release's config.ini pins both to 1518. Commits added on top of
build 1518 raise the count, so such a build needs a higher `executor_commit_max`.
