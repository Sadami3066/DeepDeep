# Super PI Agent Developer Guide

For developers who will change the app, the runtime or the build flow. Plugin authors should start with [Plugin Development](PLUGIN_DEV.md).

## Environment

- JDK 17; builds pin Java 17 (`jvmToolchain` 17).
- Android SDK Platform 36, build-tools 36.0.0 (about 5.6 GB).
- At least 8 GB of memory and 10 GB of free disk; a first build with a cold cache takes about 15–30 minutes (including JitPack compiling the SDK and downloading dependencies).
- The repository ships a Gradle wrapper; day-to-day builds go through `scripts/build.sh`, which sits on `scripts/gradlew-locked`. Do not run `./gradlew` directly, to avoid OOM from concurrent local builds. Memory settings live only in `gradle.properties`, and `gradlew-locked` ignores command-line overrides so daemons do not multiply.
- Default Android SDK locations: macOS `~/Library/Android/sdk`, Linux usually `~/Android/Sdk`. Use `ANDROID_HOME` or `ANDROID_SDK_ROOT` to override.
- Windows is not supported natively: the scripts need bash and flock, so build on WSL2, Linux or macOS.

First-time setup:

```bash
./scripts/dev-setup.sh
```

It checks `java` and `javac` (JDK 17, with a compiler), locates the Android SDK from environment variables, then an existing `local.properties`, then common paths, installs Platform 36 and build-tools 36.0.0 with `sdkmanager` when needed, keeps an existing `local.properties`, and then builds the debug APK; it does not touch signing material.

The Agent SDK comes from JitPack: `com.github.Sadami3066.DeepDeep-KotlinSDK:pi-coding-agent-android`, with the version in `gradle/libs.versions.toml` under `pi`. The first build may wait for JitPack to compile the SDK (a few minutes, and it can time out); once the dependency is cached, builds can run offline. In an offline environment, run one complete build while online first.

## Build and verify

```bash
./scripts/build.sh quick        # day-to-day compile
./scripts/build.sh test         # targeted unit tests
./scripts/build.sh check        # full verification (same as check.sh --full)
./scripts/build.sh release      # full verification, then build the release APK
./scripts/build.sh stop         # reclaim Gradle/Kotlin daemon memory
./scripts/check.sh              # compile + unit tests + lint + module boundaries + APK
./scripts/check.sh --full       # before delivery: adds the plugin SDK ABI baseline
./scripts/check.sh --device     # also installs over the app on a device and launches it
./scripts/plugin-e2e.sh         # plugin end to end (device or emulator, no root)
./scripts/plugin-bundles-e2e.sh # regression over all official plugin bundles (build the plugin repo first)
./scripts/api-compat.sh         # check the public ABI of plugin-api and design (--update rebuilds the baseline)
```

Gradle and Kotlin daemon memory is pinned in `gradle.properties` (Gradle 3g / Kotlin 2g / workers 2); `gradlew-locked` serializes builds with flock and drops `-Dorg.gradle.jvmargs` and `-Pkotlin.daemon.jvmargs` overrides. After a long idle period, reclaim memory with `build.sh stop`.

Docs-only changes do not need a build.

## Modules

| Module | Responsibility |
|---|---|
| `app/` | Compose UI, chat, files, settings, plugin pages, mini app container and page assembly |
| `runtime/` | Agent runtime: sessions, tools, memory, tasks, timers, plugin hooks and prompt contributions |
| `runtime-api/` | Pure JVM contracts, decoupling the runtime from the host |
| `shell/` | proot and ADB/Root system access backends |
| `design/` | Theme, icons, shared components and design tokens |
| `plugin-api/` | Plugin contracts and the publishable SDK |

## Runtime

- Sessions use pi JSONL as the single authority and live in `files/agent/sessions/`; pinning, archiving and renaming are local metadata only.
- UI state is a projection of the session event stream; do not store a second "current message" state.
- Tools and files are both provided by the current system access environment (`HostEnvironmentProvider`): in the Normal and Wireless debugging tiers `bash` runs inside the app sandbox, and the Root tier enters a native chroot. `adb_shell` / `root_shell` are no longer registered; system commands in the ADB and Root tiers all run through the resident `system_shell` (the tier is resolved at execution time, and a tier without a system shell reports an error). Background tasks and plugin Linux streams prefer `HostEnvironment.bashStreaming(cwd)`: the Normal and Wireless debugging tiers reuse proot streaming output, the Root tier pushes lines from the su output callback of the chroot executor, and environments without support fall back to a single output after completion.
- The system access tier (Normal / Wireless debugging / Root) is maintained by `HostAccessController` and only degrades, never upgrades itself; the runtime reads `HostEnvironment` instead of deciding root on its own.
- Sessions do not cache environment snapshots: `PiSessionEngine` uses `DynamicShellExecutor` / `DynamicFileSystem` to resolve the current environment at every execution, so switching tiers after a session is created updates tools and prompts immediately.
- Goals and background tasks live only in process memory and are cleared on restart.
- In the Normal and Wireless debugging tiers the Linux environment is a proot user space, not a security sandbox; the Root tier uses the system root environment directly.
- Scheduled tasks are bound to a session with five-field cron; execution is not guaranteed when delivery fails or the process is killed.

## Plugins

- The contract lives in `plugin-api/` and is published to a public Maven repository.
- A plugin is a signed `.ddp` loaded by the host inside the app process; there is no sandbox.
- When you add or change a contract, update the ABI baseline and run `./scripts/check.sh --full`.
- The full manifest, signing, lifecycle, tools, skills, hooks, UI extensions and troubleshooting are in [PLUGIN_DEV.md](PLUGIN_DEV.md).

## Design system

- Use the components, colors, type sizes and spacing from `design/`; do not build a second set inside a screen.
- User-facing copy lives in `app/src/main/res/values*/strings_*.xml`, Chinese by default with `values-en` kept in sync; do not concatenate copy in code.
- Check `design/icons/` and the asset notes before adding an icon, to avoid drawing a duplicate.
- After a UI change, look at it on a real device at least once: margins, alignment, hierarchy, whitespace and emphasis.

## Testing

- Add tests for new behavior; do not delete tests or weaken assertions to make them pass.
- Unit tests run per module; `check.sh` runs the full set.
- Run `plugin-e2e.sh` at least once for plugin-related changes, and `plugin-bundles-e2e.sh` as well when official plugin bundles are involved.
- Confirm with the user first before anything that may delete real data, an irreversible migration, a signing change or a change to key product tradeoffs.

**Devices and emulators**

- An x86_64 AVD covers unit tests, compilation, install and launch, plugin e2e and most UI checks.
- The following require an arm64 device: the Linux environment (the bundled rootfs is aarch64 only), screen control / virtual screens and Root system access.
- Plugin e2e needs no root: the script installs the debug build with `adb install` and pushes the `.ddp` fixtures into the app's external private directory (`/sdcard/Android/data/<pkg>/files/`), so it runs on normal devices and emulators; only the app's Root system access feature needs root.
- Do not run an emulator and a full build at the same time: Gradle/Kotlin daemons plus an emulator easily exhaust memory and OOM the system; shut the emulator down before `check.sh --full` (`adb -s <serial> emu kill`).

**Device scripts**

- `adb` is required. With several devices online, choose one with `ANDROID_SERIAL`; the scripts prefer it, then the only online device, and fail when several are online and none is specified.
- Plugin e2e needs a plugin repository: by default the scripts look for `plugins` / `DeepDeep-Official-Plugins` next to or one level above the repository (`plugin-e2e.sh` expects DeepDeep-Plugins, `plugin-bundles-e2e.sh` the official plugin repository); set `PLUGINS_DIR` when they live elsewhere. `plugin-e2e.sh` also needs the `sample` test fixture from the plugin repository; without it the script suggests pointing `E2E_DDP` at a signed package.
- `check.sh --device` and both e2e scripts install the debug build over the app: a build with the same package name but a different signature on the device (for example a release APK) makes the install fail, since debug and release signatures are not interchangeable; uninstall first (which clears app data) or use another device or emulator. A failed e2e exits with a non-zero code.
- `api-compat.sh` needs `unzip`, `jar` and `javap`; the JDK provides the last two, and the script self-checks and reports anything missing.

## Release

Signing material and the re-signing flow are in [RELEASE.md](RELEASE.md).

Key points:

- `app/release.keystore` and `keystore.properties` are not committed.
- After a signing change Android does not allow an overlay install, so uninstall first; `allowBackup=false`, and uninstalling loses local sessions and credentials.
- Before a public distribution, confirm `applicationId` matches and export old sessions for backup.

## Code conventions

- One change does one thing, in one commit; title it `type: summary` so a single `git revert` undoes it.
- When refactoring, delete the old implementation: keep no compatibility layer, deprecated entry point or dead code.
- Code files carry no comments; put necessary information in the commit, docs or test names.
- Do not change the module dependency graph, `settings.gradle.kts`, signing configuration or `.gitignore` on your own.
- Decide whether a new third-party dependency is necessary first; when adding one, record its purpose, license and size.
- Public docs live under `docs/`; module architecture is documented in [ARCHITECTURE.md](ARCHITECTURE.md).

## Documentation

- [User Guide](USER_GUIDE.md)
- [Plugin Development](PLUGIN_DEV.md)
- [Release](RELEASE.md)
- [Changelog](../CHANGELOG.md)
- Internal design records are kept separately from the public repository and are not required to build or contribute to the app.
