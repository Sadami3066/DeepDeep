# Architecture

Super PI Agent is a Kotlin/Compose Android application built from a small set of Gradle modules. The runtime is an Android port of the pi agent runtime; the plugin SDK lets external Kotlin plugins extend tools, skills, pages, and themes.

## Modules

| Module | Responsibility |
|---|---|
| `app/` | Compose UI, single Activity, dependency graph, chat, files, settings, plugin pages, miniapp container |
| `runtime/` | Agent runtime: sessions, tools, memory, jobs, timers, providers, plugin hooks and prompt contributions |
| `runtime-api/` | Pure JVM contracts between the runtime and the host |
| `shell/` | proot and ADB/root system-access backends |
| `design/` | Theme, typography, spacing, icons, and shared Compose components; also published as the plugin UI SDK |
| `plugin-api/` | Plugin contracts and the published plugin SDK |

Dependency direction: `app` → `runtime` → `runtime-api`, `app` → `shell`, and `app` → `plugin-api` → `design`; plugins also compile against `design`.

## Runtime

- The pi JSONL session file is the authoritative record of a conversation. UI state, titles, pins, and archives are projections of session events; they do not store a second copy of messages.
- `HostEnvironmentProvider` resolves the active environment (normal, wireless debugging, or root) at execution time. Shell and file operations go through `HostEnvironment`; the runtime does not cache a capability snapshot at session creation.
- Tool execution emits start, finish and failure events. The UI maps these events into the process panel and the final answer.
- Jobs and timers live in process memory and are bound to a session. They are cleared on restart unless persisted by their owning feature.
- Model providers are configured per install; sessions choose a model and thinking level independently.

## UI

- One Activity hosts the Compose tree. Navigation destinations and screen state live under `app/ui`; screens do not create their own dependency graphs.
- All user-visible copy comes from `res/values*/strings_*.xml` with an English mirror in `values-en`. Code does not build sentences from fragments.
- Plugin pages are Compose screens provided by plugins, rendered inside the host window with the host's theme and window chrome.
- Miniapps are HTML/CSS/JS pages in a WebView with a versioned bridge. The bridge is a public contract; its JavaScript object name and message schema do not change casually.

## Plugins

- A plugin is a signed `.ddp` archive loaded in the app process. It declares tools, skills, prompt sections, pages, hooks, and optional themes in `plugin.json`.
- `plugin-api` is a binary-compatible SDK: within an API level, changes are additive. Removing or changing a public signature requires a new API level.
- `design` is the shared UI SDK. Plugins consume the same components and theme values as the host; public component signatures are cross-repository API.
- Tool results have three layers: raw upstream data, guidance for the model, and optional card payloads. Cards are delivered by plugin tools through the plugin API; the host renders them in the answer area and does not invent card content.
- Command-line plugins expose plain text. They do not parse host state, inject present instructions, or render cards.
- Hooks are limited to observation and notification unless the contract explicitly allows transformation.
- Theme packs are data: `assets/themes.json` is parsed by the host, and plugin code is never executed to resolve a theme.

## Themes

`ThemeController` resolves the selected theme into a `SuperPIColors` palette and provides it to the whole app and to plugin pages:
- Built-in themes: PI (default, light/dark), Classic Blue, Santorini, Oceanic.
- PI is the product palette: black and white structure, semantic colors for danger/success/warning, restrained syntax colors in code.
- Third-party theme packs declare a base (`pi`, `classic`, `santorini`, `oceanic`) and token overrides.
- A missing plugin theme falls back to PI; it does not leave the app unstyled.

## Updates

- The app reads the latest GitHub release, compares the tag version with the installed version, and shows the release body as update notes.
- The release APK is `PI-<version>.apk`. The updater uses the GitHub asset digest when present and verifies the archive package name and version code before installing.
- If the exact asset name is unavailable, the updater falls back to the first APK asset, so a release can change asset naming without breaking older clients.
- Releases are cut from a tag that matches `versionName`; the release workflow builds, signs, and publishes the APK.

## Testing

- Pure logic has JVM unit tests in each module. Compose screens are verified by building, installing, and inspecting on a device.
- `check.sh --full` runs compilation, unit tests, lint, module boundary checks, APK assembly, and the plugin SDK ABI baseline.
- Device tests cover tool contracts, images, calendar, network policy, plugin end-to-end flows, and binary plugin compatibility where a real device or emulator is required.
