# Release Builds

## Signing material

| File | Purpose | In git |
|---|---|---|
| `app/release.keystore` | Release signing certificate (RSA 4096, alias `deepdeep`) | No |
| `keystore.properties` | Certificate path and passwords | No |

The build script reads `keystore.properties` automatically; fields:

```properties
storeFile=release.keystore
storePassword=...
keyAlias=deepdeep
keyPassword=...
```

`storeFile` resolves relative to the `app/` module directory. Without `keystore.properties`, release falls back to the debug signature, only so that environments without a certificate can still compile; do not distribute such builds.

## Build

```bash
./scripts/gradlew-locked :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. Release enables R8 code shrinking and resource shrinking; the rules are in `app/proguard-rules.pro`.

## Versioning and release flow

The version numbers in `app/build.gradle.kts` are edited by hand before a release:

- `versionCode`: incremented on every release, computed as `MAJOR*10000 + MINOR*100 + PATCH` (for example `1.2.3` → `10203`).
- `versionName`: `X.Y.Z`, matching the tag.

Version numbers appear only in: `app/build.gradle.kts`, the top section of `CHANGELOG.md`, `release-notes/vX.Y.Z.md`, and git tags. Product docs such as README and USER_GUIDE do not carry versions.

1. Set `versionCode` and `versionName`, then commit after `./scripts/check.sh --full` passes.
2. Tag `vX.Y.Z`, matching `versionName`.
3. Create a GitHub release with a single asset, named `PI-X.Y.Z.apk`.
4. Write the notes by hand as user-facing copy: the app's update page shows them verbatim. Do not generate a commit list with `--generate-notes`, and do not write notes like "see the attachments below" that only make sense on GitHub.
5. Do not mark the release as prerelease or draft: the app only reads the latest stable release.

How the app reads `releases/latest` and validates the asset and digest is described in [In-app updates (release contract)](#in-app-updates-release-contract).

## R8 keep fallback

Release R8 shrinks and renames classes and members; such problems appear only in minified builds, never in debug builds. Pitfalls already hit: the BouncyCastle fallback being obfuscated and breaking plugin signature verification; host ABI being shrunk and causing `NoClassDefFoundError` on plugin pages; the wireless debugging shell daemon entry being shrunk and breaking ADB mode startup.

Two kinds of symbols must keep their original names:

- **Plugin ABI**: plugins reuse the host's Kotlin, coroutines, Compose, `plugin-api` and `design` directly at runtime. The keep rules live in `plugin-api/consumer-rules.pro` (shipped with the SDK AAR, so any minified build that depends on it applies them) and `app/proguard-rules.pro` keeps the same copy as a fallback. The `androidx.lifecycle` / `androidx.activity` / `androidx.savedstate` libraries that plugin UIs commonly use are also kept.
- **Reflection and dynamic-loading entries**: the `ShellDaemon` started by `app_process`, the virtual screen helper, BouncyCastle, MLKit barcode and so on.
- **Room / WorkManager**: WorkManager creates its own Room database implementation through reflection, and `app/proguard-rules.pro` keeps the no-arg constructor with `-keep class * extends androidx.room.RoomDatabase { <init>(); }`; removing it makes release builds crash at startup (which cost a rework in 1.0.6).

Change constraints: when you add a reflection entry, add an `api()` dependency to `plugin-api`, or let plugin UI use a new host library, add the matching keep rules in the same change; otherwise the release build succeeds and crashes only when that path runs. Keep rules preserve ABI names only and promise no version compatibility between host and plugin: plugins still need a rebuild for each SDK version.

## In-app updates (release contract)

The app reads updates only from GitHub Releases: it reads `releases/latest`, compares the `vX.Y.Z` tag to decide whether a version is newer, uses the release body verbatim as the update notes, and uses the download URL and the sha256 digest GitHub computes for the `.apk` asset to verify the download. The asset named `PI-X.Y.Z.apk` is preferred; when it is missing, the first `.apk` asset in the release is used instead.

For release steps and the versioning and update-notes contract, see [Versioning and release flow](#versioning-and-release-flow). GitHub computes the sha256 digest after the asset is uploaded, and the app verifies the downloaded package against it.

## Pre-release checks

1. `./scripts/check.sh --full` passes.
2. Install the **minified release APK** and smoke test: for each of the four official plugins, go through install → enable → open the plugin page, trigger one tool and one UI slot; verify the open and share-import entry points for `.ddp` once each.
3. Passing on a debug build does not count, and unit tests alone are not enough: R8 problems surface only in the minified release build.
4. Before releasing, run an in-app update acceptance with the previous version on a real device: the old version detects the new one → downloads → the system installs it. After releasing, upgrade once through in-app update as well.

## Re-signing migration

The first time you switch from the debug certificate to `release.keystore` (or change certificates later), Android does not allow an overlay install, so the old app must be uninstalled first. With `android:allowBackup="false"`, uninstalling deletes local sessions, settings and credentials; to keep them, first use "Export session" in the chat top bar to export each session you need as a JSONL file.

Before installing, confirm the package name on the device matches `applicationId`; later versions signed with the same certificate install over the old app directly.
