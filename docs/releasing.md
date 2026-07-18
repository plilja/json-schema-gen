# Releasing

Gjuton publishes to [Maven Central](https://central.sonatype.com) via the
[Central Portal](https://central.sonatype.com). Releases are cut from a git tag:
pushing a `v*` tag triggers `.github/workflows/release.yml`, which builds, signs,
and uploads the artifacts. The upload is **not** published automatically — it
waits in a *pending* state on the Central Portal for a human to click **Publish**
(the plugin is configured with `autoPublish=false`). Maven Central is
**immutable**: once a version is published it can never be changed or removed, so
the manual publish step is a deliberate gate.

Versioning is manual. Between releases the POM version is `X.Y.Z-SNAPSHOT`
(Central rejects `-SNAPSHOT`); a release drops the suffix, tags, then bumps to the
next `-SNAPSHOT`.

## Prerequisites (one-time setup)

These must be done once before the first release. They are not part of the repo.

### 1. Central Portal account and namespace

1. Sign in at <https://central.sonatype.com> with the GitHub account that owns the
   `gjuton` org.
2. Register the namespace `io.github.gjuton` and complete the verification
   challenge the Portal issues (it confirms you control `github.com/gjuton`).
3. Under your account, generate a **user token** (Account → Generate User Token).
   It yields a username and password — these are the `CENTRAL_USERNAME` /
   `CENTRAL_PASSWORD` secrets below, not your login.

### 2. GPG signing key

Central requires every artifact to be GPG-signed and the public key to be
discoverable on a keyserver.

```bash
# Generate a key (RSA 4096, no expiry or a long one). Choose a passphrase and
# keep it secret — it becomes the GPG_PASSPHRASE secret.
gpg --full-generate-key

# Find the key id (the long hex after "sec   rsa4096/").
gpg --list-secret-keys --keyid-format=long

# Publish the public key so Central can verify signatures.
gpg --keyserver keys.openpgp.org      --send-keys <KEY_ID>
gpg --keyserver keyserver.ubuntu.com  --send-keys <KEY_ID>

# Export the private key (armored) for the GitHub secret.
gpg --armor --export-secret-keys <KEY_ID> > gjuton-signing-key.asc
```

### 3. GitHub Actions secrets

Add these to the repo (Settings → Secrets and variables → Actions), or to the
`gjuton` org. They are consumed by `release.yml`:

| Secret             | Value                                              |
| ------------------ | -------------------------------------------------- |
| `CENTRAL_USERNAME` | user-token username from the Central Portal        |
| `CENTRAL_PASSWORD` | user-token password from the Central Portal        |
| `GPG_PRIVATE_KEY`  | full contents of `gjuton-signing-key.asc`          |
| `GPG_PASSPHRASE`   | the passphrase chosen when generating the key      |

Delete the exported `gjuton-signing-key.asc` afterwards.

## Cutting a release

Assume the release is `0.0.2` and the POM currently reads `0.0.2-SNAPSHOT`.

1. **Update the changelog.** In `CHANGELOG.md`, rename `## [Unreleased]` to
   `## [0.0.2] - <today's date>` and add a fresh empty `## [Unreleased]` above it.
   Confirm the entries describe what a user gets by upgrading.
2. **Drop the snapshot suffix.** Set the version to the release version in both
   POMs:
   ```bash
   mvn versions:set -DnewVersion=0.0.2 -DgenerateBackupPoms=false
   ```
3. **Verify the build** locally: `mvn clean verify`.
4. **Commit** the version and changelog: `git commit -am "chore: release 0.0.2"`.
5. **Tag and push.** The tag name must be the version with a `v` prefix:
   ```bash
   git tag v0.0.2
   git push origin master v0.0.2
   ```
   The tag push triggers `release.yml`, which builds, signs, and uploads to the
   Central Portal.
6. **Publish on the Portal.** Sign in at <https://central.sonatype.com>, open the
   pending deployment, inspect the staged artifacts, and click **Publish**. It
   syncs to Maven Central within a few minutes.
7. **Bump to the next snapshot** so development continues off a snapshot version:
   ```bash
   mvn versions:set -DnewVersion=0.0.3-SNAPSHOT -DgenerateBackupPoms=false
   git commit -am "chore: bump to 0.0.3-SNAPSHOT"
   git push origin master
   ```
8. **Write the GitHub Release.** Create a release for tag `v0.0.2` and paste the
   `0.0.2` section of the changelog as the body.

## Breaking changes

While pre-1.0, breaking changes are allowed in any release. When one lands, its
changelog entry must state what broke and the migration steps. The exact format
for this will be settled at the first breaking change.
