# Release signing

## Why the key was rotated

`app/norwin.keystore.jks` was committed to this repository, which is public, and its
passwords were in plain text in `app/build.gradle.kts`. Anyone who cloned the repo could
sign an APK that Android would accept as a legitimate update to the app. That key must be
treated as compromised and permanently retired.

It was also used to sign *debug* builds, which is what forced it into the repo. Debug now
uses the local SDK debug key instead, so the release key has no reason to be here.

Rotating the signing key means existing installs cannot update in place and must reinstall.
That cost is already being paid by the `applicationId` change needed to leave `com.example.*`,
so doing both at once costs one reinstall rather than two.

## 1. Generate a new key

Run this yourself - the password must never be pasted into a chat, an issue, or a commit.
Keep the file **outside** the repository.

```bash
keytool -genkeypair -v -keystore ../norwin-release.jks -alias norwin-release -keyalg RSA -keysize 4096 -validity 10000
```

Back the file up somewhere durable. If you lose it you cannot ship updates to an installed
app, and on Play you cannot recover it without key upgrade.

## 2. Sign locally

```bash
cp keystore.properties.example keystore.properties
```

Fill in the real values. `keystore.properties`, `*.jks` and `*.keystore` are gitignored.

## 3. Sign on CI

Base64-encode the keystore:

```bash
base64 -w0 ../norwin-release.jks > keystore.b64
```

Add four repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | contents of `keystore.b64` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `norwin-release` |
| `KEY_PASSWORD` | the key password |

Then delete `keystore.b64`.

`release.yml` fails loudly if `KEYSTORE_BASE64` is missing rather than publishing an unsigned
APK, so **add these secrets before merging this branch to main** or the next release will fail.

## 4. Purge the old key

The old keystore is no longer tracked, but it remains in git history and has already been
public. Rotation is what actually protects you; history rewriting is optional cleanup:

```bash
git filter-repo --path app/norwin.keystore.jks --invert-paths
```

This rewrites history and requires a force push plus re-clones by every collaborator. The old
key is dead either way once the new one ships.
