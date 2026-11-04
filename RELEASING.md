# Releasing to Maven Central

This repository is **not** published by CI on every push. A release is a manual step. The `release` profile is off by default, so `mvn test` and `mvn package` do not sign artifacts and do not need a GPG key.

Published coordinates (version `0.2.0-SNAPSHOT` until a non-snapshot release is cut):

| Module | groupId | artifactId | Published |
|--------|---------|------------|-----------|
| parent | `io.github.chensirj` | `java-opt-chain` | yes (POM) |
| annotations | `io.github.chensirj` | `opt-chain-annotations` | yes |
| Maven plugin | `io.github.chensirj` | `opt-chain-maven-plugin` | yes |
| demo | `io.github.chensirj` | `opt-chain-demo` | **no** |
| IDEA plugin | — | Gradle project `opt-chain-idea` | **no** (install the zip from `./gradlew buildPlugin`) |

`opt-chain-demo` sets `maven.deploy.skip` and `skipPublishing` so it is left out of both `maven-deploy-plugin` and the Central Portal bundle. Java packages stay `io.github.chensirj.optchain` and are independent of the Maven groupId.

## Namespace

Verify **`io.github.chensirj`** on the Central Portal with the GitHub account **ChensirJ**.

Central treats `io.github.<user>` as a namespace you can prove by signing in with that GitHub user (the account name is matched case-insensitively; the portal user is `ChensirJ`). No custom domain is required.

This project does **not** publish under `io.ch`. A groupId starting with `io.ch` follows reverse-DNS rules and would require control of the domain `ch.io`. That option is not used.

## One-time setup

1. Create an account at [central.sonatype.com](https://central.sonatype.com/) and sign in with GitHub as **ChensirJ**.
2. Open **Namespaces** and verify `io.github.chensirj`. Wait until the namespace shows as verified before deploying.
3. Generate a user token in the portal (Account → **Generate User Token**). The token has a username and a password; those are not your GitHub password.
4. Create a GPG key and publish the public key so Central can verify signatures (for example `gpg --keyserver hkps://keys.openpgp.org --send-keys <KEYID>`).

`~/.m2/settings.xml` (server id must be `central`, matching `publishingServerId`):

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>gpg</id>
      <properties>
        <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>gpg</activeProfile>
  </activeProfiles>
</settings>
```

The GPG plugin reads `gpg.passphrase` from that active profile (or from `-Dgpg.passphrase=...`). Do not commit the token or the passphrase.

## Build the release bits without signing

From a clean tree, on JDK 17:

```bash
mvn -B -P release -Dgpg.skip package
```

`-Dgpg.skip` skips `maven-gpg-plugin`. The command still builds the main jars plus `-sources.jar` and `-javadoc.jar` for the modules that produce code. It does not upload anything. A normal `mvn package` without `-P release` does not run the source, javadoc, or GPG plugins.

## Deploy

Drop `-SNAPSHOT` from the version in every POM (parent and modules) and commit that change on the release commit. Snapshot versions are not accepted as a Central release.

```bash
mvn -B -P release deploy
```

`autoPublish` defaults to `false`, so the portal keeps the deployment in a pending state for you to inspect and publish in the UI. To publish in the same command:

```bash
mvn -B -P release deploy -DautoPublish=true
```

The Central plugin's server id is `central`. It does not use the old OSSRH staging URLs.

## GitHub Actions

[`.github/workflows/release.yml`](.github/workflows/release.yml) runs only on **workflow_dispatch**. It does not run on push or pull request. Repository secrets:

| Secret | Purpose |
|--------|---------|
| `CENTRAL_TOKEN_USERNAME` | Portal user-token username |
| `CENTRAL_TOKEN_PASSWORD` | Portal user-token password |
| `GPG_PRIVATE_KEY` | Armored private key (`gpg --armor --export-secret-keys`) |
| `GPG_PASSPHRASE` | Passphrase for that key |

The workflow calls `mvn -B -P release deploy`. Publishing still follows `autoPublish` (false unless you change the POM or the workflow). Cut a non-snapshot version before dispatching it.
