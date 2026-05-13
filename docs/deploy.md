# Release Guide

This document is for project maintainers publishing the `pandoc4j` Maven reactor to Maven Central.

## Current Coordinates

- Group: `org.tinycircl`
- Parent: `pandoc4j-parent`
- Artifacts: `pandoc4j`, `pandoc4j-binary`
- Current release line: `0.3.x`

## Prerequisites

- Java 21 and Maven installed
- Sonatype Central Portal account with the `org.tinycircl` namespace approved
- A Central user token configured in `~/.m2/settings.xml`
- A working GPG key available locally
- Public key uploaded to a supported key server

Example `settings.xml` server entry:

```xml
<server>
  <id>central</id>
  <username><!-- Sonatype user token username --></username>
  <password><!-- Sonatype user token password --></password>
</server>
```

## Before Releasing

1. Update the root `pom.xml` and module parent versions to the target release version, for example `0.3.0`.
2. Update user-facing version references in `README.md`.
3. Add or update release notes in `CHANGELOG.md`.
4. Confirm the bundled `pandoc4j-binary` manifest points to the intended Pandoc release and SHA-256 digests.
5. Run a release-style verification build from the repository root:

```bash
mvn clean -P release -Dgpg.skip=true verify
```

```powershell
mvn clean -P release "-Dgpg.skip=true" verify
```

## Publishing

Use a system terminal for the final deploy command.

Important:
- On Windows, Cursor's integrated terminal may not surface the GPG pinentry dialog reliably.
- If the deploy appears to hang during `maven-gpg-plugin`, run the command from `cmd.exe`, PowerShell, or another system terminal where the GPG passphrase popup can appear normally.

Release command:

```bash
mvn -P release -Dgpg.keyname=<KEY_ID> deploy
```

PowerShell:

```powershell
mvn -P release "-Dgpg.keyname=<KEY_ID>" deploy
```

Windows fallback:

```bash
cmd /c "mvn -P release -Dgpg.keyname=<KEY_ID> deploy"
```

Expected successful flow:

1. Maven builds the parent POM plus the `pandoc4j` and `pandoc4j-binary` jars, sources jars, and javadoc jars
2. GPG signs all release artifacts
3. `central-publishing-maven-plugin` uploads the bundle
4. Sonatype validates the deployment and returns a deployment id

## Final Publish Step

The current project configuration uses manual publishing after validation.

After `mvn deploy` succeeds:

1. Open [Central Deployments](https://central.sonatype.com/publishing/deployments)
2. Locate the validated deployment
3. Click `Publish`
4. Wait for Central sync to complete

## After Publishing

1. Verify the new `pandoc4j` and `pandoc4j-binary` versions appear on Maven Central
2. Verify the dependency snippets on `mvnrepository.com` or Central
3. Create and push a Git tag such as `v0.3.0`
4. If needed, update any release announcement or project homepage references

## Notes

- `spring-boot-configuration-processor` is configured as a compile-time annotation processor and is not meant to be published as a normal dependency
- Since `0.2.0`, the AST API uses Jackson 3 types from `tools.jackson.*`
- Since `0.2.0`, the public Java package is `org.tinycircl.pandoc4j`
- `pandoc4j-binary` downloads official Pandoc release archives at runtime; keep its manifest checksums reviewable in source control
