# Super PI Agent Plugin Development

Development documentation for external plugin authors: project setup, contracts, packaging and signing, install and update, and every extension surface. Official plugins and the template are in [SuperPI-Official-Plugins](https://github.com/Sadami3066/SuperPI-Official-Plugins) and [SuperPI-Plugins](https://github.com/Sadami3066/SuperPI-Plugins); the host repository keeps only the contract and on-device regression scripts. Product capabilities: [USER_GUIDE.md](USER_GUIDE.md); builds and modules: [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).

PI plugins are **Kotlin code plugins**: implement `SuperPIPlugin` from `plugin-api`, package as `.ddp`, and the host loads them inside the app process. Plugins install only from `.ddp`; nothing is compiled into the app.

**Security premise**: plugins share the app process and UID with no sandbox, and installing means trusting — plugin code is equivalent to app code and can read the app's private data, reflect into host internals and crash the app. Install only plugins you trust; use MCP for third-party tool sources.

## Quick start

`plugin-api` is the only contract artifact (Android library, package `io.github.whywood.superpi.plugin.api`): the current release is `2.0.0`, whose transitive dependency is `design 1.0.0` (take the latest release in `SuperPI-Maven` as authoritative); `PLUGIN_API_LEVEL = 7`, `BASELINE_API_LEVEL = 7`, `MIN_SUPPORTED_API_LEVEL = 7`, `HOST_UI_API_LEVEL = 3`.

`plugin-api 2.0.0` / `design 1.0.0` start the SuperPI contract line at API level 7: the Kotlin package, plugin interface and Maven group are renamed, plugin ids use `io.github.whywood.superpi.plugin.*`, and host UI API level is 3. Plugins must be rebuilt and declare `apiLevel: 7`; old 1.x plugins are rejected. Later 2.x releases stay binary-compatible within level 7. The compatibility promise is in `plugin-api/COMPATIBILITY.md`: within one `apiLevel` only binary-compatible additions, and deleting, renaming, changing signatures or changing semantics requires a new `apiLevel`; the host rejects plugins with an `apiLevel` above its own, and level 5 has not been accepted since 1.0.4.

Start a plugin project from `template/` in [SuperPI-Plugins](https://github.com/Sadami3066/SuperPI-Plugins): a standalone Gradle Android project with an example tool, an example skill, one prompt section and the full `.ddp` packaging/signing/verification implementation. Copy or rename it as a starting point. Official plugin implementations are in [SuperPI-Official-Plugins](https://github.com/Sadami3066/SuperPI-Official-Plugins).

Prerequisites:

- JDK 17+ and the Android SDK; locate the SDK with the `ANDROID_HOME`/`ANDROID_SDK_ROOT` environment variables or `sdk.dir` in the project's `local.properties`.
- Plugins depend on the public Maven repository `https://sadami3066.github.io/SuperPI-Maven` (group `io.github.sadami3066.superpi`, currently `plugin-api 2.0.0` / `design 1.0.0`, take the latest release as authoritative); no local mavenLocal publish is needed.

`plugin-api` is the contract, and `design` is the UI design system shared with the app (plugin UI uses it directly, and `plugin-api` brings it in through `api()`). Publishing a new version:

```bash
./scripts/gradlew-locked :design:publishAllPublicationsToPagesRepository :plugin-api:publishAllPublicationsToPagesRepository
# artifacts land in app/build/maven-repo/io/github/sadami3066/superpi/
```

Sync the artifacts into the matching directories of the `SuperPI-Maven` repository and push; Pages updates automatically. When the SDK version goes up, update `compileOnly` in each module's `build.gradle.kts` and the template version too.

Adding a plugin:

```bash
# 1. Copy the template and rename it (run from the plugin repository root)
cp -R template my-plugin && cd my-plugin
#    Change rootProject.name in settings.gradle.kts (it decides the .ddp file name)
#    Change namespace/applicationId/versionName in build.gradle.kts
#    Change id/version/entryClass/name/summary/provides in src/main/assets/plugin.json
#    Rename the package and entry class under src/main/kotlin

# 2. Package + sign + self-check
./gradlew assemblePlugin
./gradlew signPlugin
./gradlew verifyPlugin

# 3. Artifacts
#    build/plugin/<rootProject.name>.unsigned.ddp   unsigned (for troubleshooting)
#    build/plugin/<rootProject.name>.ddp            installable signed package
```

With the template unchanged, `rootProject.name` is `plugin-template` and the artifacts are `template/build/plugin/plugin-template.ddp` (unsigned: `plugin-template.unsigned.ddp`); renaming changes the file names too. The module builds only the `debug` variant, `release` runs no minify, and the `.ddp` is repackaged from the debug APK. When the template is one module of a multi-module project (for example `:template`), run tasks per module such as `:template:verifyPlugin`, and artifacts land in `<module>/build/plugin/<module>.ddp`.

Installing: transfer the `.ddp` to the phone, then open it with PI from a file manager, share the file to PI, or pick it with "Import plugin" on the Plugins page. The confirmation page shows name/version/size/whole-package sha256/publisher name and `keyId`, the tools/skills/pages/prompt override (when `prompt` is declared) it will provide, and the capability/permission/component declarations; the first import asks you to trust the publisher, and only after confirming does the host write the TOFU trust store, unpack, install and enable immediately (when `defaultEnabled` is true). Later upgrades must be signed with the same publisher key.

Device regression is driven by scripts at the host repository root (with the device connected): `scripts/plugin-bundles-e2e.sh` regresses install and activation for the five official plugin bundles; `scripts/plugin-e2e.sh` regresses install, run, disable and upgrade with the `sample` test fixture from the plugin repository. The scripts install the debug build with `adb install`, push the `.ddp` into the app's external private directory, need no root, and run on normal devices or emulators.

## Project and dependencies

A plugin is an ordinary Android application project; the artifact only needs `classes*.dex` and `assets/**` (see "`.ddp` package format"), and the APK's manifest, resources and signature do not take part.

| Item | Value |
|---|---|
| AGP | 9.4.1 |
| Kotlin (same version as the Compose compiler plugin) | 2.4.21 |
| Compose BOM | 2026.03.00 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization-json | 1.12.0 |
| compileSdk / targetSdk | 36 |
| minSdk | 29 (a plugin dex minApi cannot be higher than the host) |
| JVM target | 17 |

Dependency rules:

- Libraries the host already provides can only be `compileOnly`: `io.github.sadami3066.superpi:plugin-api` (version from the latest `SuperPI-Maven` release; `api()` carries the matching `design:1.0.0`, and plugin UI uses its Dd components directly), Compose (`ui`/`foundation`/`material3` + BOM), `kotlinx-coroutines-core`, `kotlinx-serialization-json`.
- The host release applies R8 keep to plugin runtime dependencies (`plugin-api`, `design`, Kotlin, coroutines, Compose, plus `androidx.lifecycle`/`androidx.activity`/`androidx.savedstate` commonly used by plugin UI), so these libraries never lose classes in the minified build. Keep preserves ABI names only and is not a version compatibility promise: plugins still need a rebuild for each host SDK version, and host libraries outside the allowlist are not guaranteed to work.
- **Do not** use `implementation` for these libraries: the host is parent-first, `plugin-api` and Compose exist only in the host, and duplicates packed into the plugin trigger class conflicts.
- A plugin may bring its own third-party libraries, but they must be packed into `classes*.dex` and must avoid packages and class names the host already occupies.
- Expose only `plugin-api`; do not reflect into app internals (upgrades will break it).
- Native libraries: `lib/<abi>/**` in the `.ddp` enters the signature digest, but the host does not add it to the classloader's library search path, so `System.loadLibrary` does not work.
- No `res/`, no `R`: copy, icons and skill files all go through the manifest and assets.

## plugin.json manifest

The manifest always lives at `assets/plugin.json` inside the package and is parsed and validated by the host. Fields:

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | string | Yes | Lowercase reverse domain, at least two segments, regex `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$` (e.g. `com.example.myplugin`); the host uses it as the install directory and unique identity |
| `version` | string | Yes | `major.minor.patch`, optionally with a `-dev`/`+build` suffix; the host records the installed version and decides upgrades |
| `apiLevel` | int | No (default 6) | Contract level the plugin requires; must be within `MIN_SUPPORTED_API_LEVEL..PLUGIN_API_LEVEL` (currently 6..6), otherwise loading is refused. Omitted means `BASELINE_API_LEVEL` (6); level 5 plugins have been rejected since host 1.0.4 |
| `minHostVersion` | string | No | Minimum host version. The host version comes from `BuildConfig.VERSION_NAME`; comparison looks only at `major.minor.patch` and ignores suffixes. Plugins using only capabilities that existed in 1.0.x can write `"1.0.4"`; plugins using 1.1.0 capabilities (including design 0.7.0 components) write `"1.0.6"`; plugins using design 0.8.0 components write `"1.0.7"`; plugins using design 1.0.0 components or plugin-api 2.0.0 capabilities write `"1.0.8"`; plugins using tool result renderers or other 1.3.0 capabilities write `"1.1.0"`; plugins using `host.images()`/`DdRemoteImage` write `"1.4.0"` |
| `entryClass` | string | Yes | Fully qualified name of the class implementing `SuperPIPlugin`; it must have a no-arg constructor |
| `name` | LocalizedText | Yes | Plugin name, with at least `default` |
| `summary` | LocalizedText | Yes | One-line description, with at least `default` |
| `description` | LocalizedText | No | Detail text |
| `homepage` | string | No | Must be an `http(s)://` link |
| `changelog` | string | No | Release notes |
| `icon` | string | No | Only `asset:<path inside package>` is supported, e.g. `asset:icon.png`; the list downsamples to display size and does not tint; a missing or broken icon falls back to a placeholder |
| `ui` | object | No | `{ "apiLevel": 2, "kotlin"?: "2.4", "compose"?: "1.10.5" }`; part of Compose slot compatibility, see "UI extensions" |
| `publisher` | object | No | `{ "name": ..., "keyId": ... }`. `name` is the display name (e.g. `milous`), shown directly in the import confirmation and installed info; when missing the UI falls back to the first 12 characters of the signing `keyId`. `keyId` is descriptive only: publisher identity and trust always come from the `keyId` in the signature file `META-INF/plugin-signature.json` |
| `provides` | object | No | Pre-install check of contributions, see below; must match the actual contributions |
| `capabilities` | string[] | Yes | Host services and capabilities the plugin needs (38 items), see below; missing or unknown entries refuse loading |
| `permissions` | string[] | No | System permissions the plugin will request, values in "Permission declarations"; must be declared in pairs with `capabilities` |
| `components` | object | No | System component abilities proxied by the host, see "Component declarations"; every enabled item requires the matching capability |
| `defaultEnabled` | bool | No (default true) | Whether the plugin is enabled by default after the first install |

The manifest does not support fields such as `source`/`installable`/`sizeLabel`/`tintIcon`: the source is always treated as an external `.ddp`, the size is shown by the host from the package, and icons always use asset images. Unknown fields are ignored, and a wrong scalar type degrades to the default before validation reports the error.

`provides` is used for the pre-install capability check and the confirmation page:

| Key | Contents |
|---|---|
| `tools` | Tool name list, lowercase `snake_case`, e.g. `template_echo`; must not conflict with host reserved tool names |
| `skills` | Skill id list, e.g. `template-skill` |
| `uiSlots` | Registered slot ids, e.g. `tool.card.content:template_echo`, `chat.message:card`; plugin pages do not use slots, see `pages` |
| `pages` | Plugin page id list |
| `themes` | Theme id list; one-to-one with `id` in `assets/themes.json`, see "Theme packs" |

`capabilities` declares the host services and capabilities the plugin actually uses; the install confirmation and plugin details show them, and the host injects only the declared services. **An undeclared service accessor throws `PluginServiceUnavailable` at runtime** (it does not return null). 1.3.0 has 38 items in four groups; accessors marked "experimental" need `@OptIn(PluginExperimentalApi::class)`.

**Base capabilities (since 1.0)**

| wire name | Accessor | What it does | When unavailable |
|---|---|---|---|
| `linux` | `host.linux()` | Linux environment command execution and streaming output | `available=false`; throws `IllegalStateException` while the Root tier is preparing or after preparation fails |
| `screen` | `host.screen()` | Screen observation, screenshots, tap/swipe/type and virtual screens | capability fields marked unavailable, calls throw |
| `notifications` | `host.notifications()` | Post/update/cancel notifications; notification listening and actions | with `enabled=false` notifications are silently ignored; listener methods return empty/false while listening is unauthorized |
| `secrets` | `host.secrets()` | Encrypted key-value storage isolated by plugin id | throws `PluginServiceUnavailable` when the host does not provide it |
| `network` | `host.network()` | HTTP requests, downloads and `get`/`post` extensions | network errors throw; response body capped at 2 MB, downloads at 512 MB |
| `files` | `host.files()` | Plugin data directory and workspace file access | relative paths root at `dataDir`; absolute paths change with the tier |
| `sessions` | `host.sessions()` | Session list/history/prompt/stop/create/rename/delete/archive | with no active session `active=null` and `prompt` returns false |
| `jobs` | `host.jobs()` | Start, read and kill background tasks | `start` returns null when there is no session |
| `timers` | `host.timers()` | One-shot and cron reminders | invalid parameters return null |
| `goals` | `host.goals()` | Goal create, edit, pause/resume/complete/clear | invalid state returns null |
| `miniapp` | `host.miniapp()` | Mini app scan, load, create, edit, run, delete | throws `PluginServiceUnavailable` when the host does not provide it |
| `mcp` | `host.mcp()` | MCP server probing, tool list and calls | returns an error result when the server does not exist |
| `ui` | `host.ui()` | Open URL/share/pages, interactive pickers, Snackbar, theme | throws `PluginServiceUnavailable("ui.xxx")` when not wired |
| `app` | `host.app()` | Read-only app version, language, device info; app foreground/background events | always available |
| `bus` | `host.bus()` | Lightweight event bus between plugins | throws `PluginServiceUnavailable` when the host does not provide it |
| `logs` | `host.logs()` | Write to the host log | always available |
| `chat` | `host.chat()` | Deliver messages/cards/attachments to a session | unknown sessions throw; a failed delivery returns false |
| `prompt` | no accessor | Enables `PromptOverridePlugin` prompt override (experimental) | `promptOverride()` is not called when undeclared |

**Devices and system (new in 1.1.0)**

| wire name | Accessor | What it does | When unavailable |
|---|---|---|---|
| `settings` | `host.settings()` | Plugin-level key-value/JSON settings persistence and change stream | always available (host storage) |
| `device` | `host.device()` | Device info, battery/network/screen state streams, vibration, jump to system settings pages | `observe` emits a default snapshot when state is unavailable; jumps return false when they fail |
| `clipboard` | `host.clipboard()` | Read and write clipboard text and URIs | returns null or ignores writes when background access is restricted |
| `permissions` | `host.permissions()` | Query/request runtime permissions, query special grant state and jump to system settings | `request` returns whether each permission was granted; `granted=false` while a special grant is off |
| `packages` | `host.packages()` | App list/details, install/uninstall APKs, launch, open app info, read icons | returns null for a missing package; silent install needs the Root/ADB tier, otherwise it returns waiting for user confirmation |
| `privilege` | `host.privilege()` | Current tier and Root/ADB state, run commands at a given tier, silent install/uninstall, wireless debugging pairing | `exec` returns non-zero/error when the tier is insufficient, and pairing interfaces return the failure reason |
| `intents` | `host.intents()` | Start any Intent, broadcast, service, resolve and query | returns false/null when nothing matches; background Activity starts are limited by the system |
| `components` | `host.components()` (experimental) | Open the host-proxied dedicated plugin window, register dynamic broadcast receivers | not injected when components is undeclared; failed windows/receivers return false |
| `background` | `host.background()` | WorkManager/Alarm background tasks, boot callbacks, foreground services, battery optimization | `schedule` returning false means the system refused; task firing is affected by Doze |
| `location` | `host.location()` | Last location, single fix, location update stream, geocoding | returns null when `available=false` or permission is not granted |
| `contacts` | `host.contacts()` | Query, search, create, update, delete contacts | throws `SecurityException` when permission is not granted; `permissionGranted=false` |
| `sms` | `host.sms()` | SMS thread/message read, send, mark read, delete | operations return false or throw when `canRead`/`canSend` is false |
| `calendar` | `host.calendar()` | Calendar and event read/write | throws `SecurityException` when permission is not granted; `permissionGranted=false` |
| `media` | `host.media()` | Save to Downloads/gallery, scan, open, share, preview, image read, URI import | returns null/false for unreadable paths; request `manage-storage` separately for all-files access |
| `speech` | `host.speech()` | TTS read-aloud and speech recognition | returns false/error results when `speakAvailable`/`recognizeAvailable` is false |
| `web` | `host.web()` | Host web search and body fetch | returns an empty list/null when `available=false` |
| `credentials` | `host.credentials()` (experimental) | Read host provider and MCP credential information | returns an empty list/null when the host is not configured; read-only, no writes |
| `accessibility` | `host.accessibility()` (experimental) | Accessibility event stream and global actions | with `enabled=false` the stream is empty and actions return false |
| `share` | no accessor | Receive system shares (with `components.share`) | `shareReceived` is not dispatched when the component is undeclared |
| `deeplink` | no accessor | Receive deep link paths (with `components.deeplinks`) | `deepLinkReceived` is not dispatched when the path does not match or is undeclared |

`capabilities` is an **availability boundary, not a security sandbox**: plugins still share the process and UID with the host and can bypass it through `host.context` or reflection; it exists so plugins declare intent and users see which capabilities will be used before installing, and cannot stop malicious code. Declare at least one; an empty array is legal but the plugin can barely work.

`tools`/`skills`/`pages` ids all match `^[a-z][A-Za-z0-9_-]*$` and **cannot contain dots** (a skill id is `template-guide`, not `template.guide`); the slot id rules for `uiSlots` come later.

### Permission declarations (`permissions`)

`permissions` lists the system permissions a plugin will request, with values from the `PluginPermission` constants (all strings). Install-time validation checks that permissions and capabilities agree: any permission needs the matching capability, otherwise `PERMISSION_WITHOUT_CAPABILITY`; unknown or duplicate values report `INVALID_PERMISSION`. The install confirmation page shows the permission list.

**Runtime permissions** (`permissions().request(...)` shows the system grant dialog):

| wire name | Permission | Purpose | Capability needed |
|---|---|---|---|
| `location.coarse` | `ACCESS_COARSE_LOCATION` | Approximate location | `location` |
| `location.fine` | `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` | Precise location | `location` |
| `contacts.read` | `READ_CONTACTS` | Read contacts | `contacts` |
| `contacts.write` | `READ_CONTACTS` + `WRITE_CONTACTS` | Modify contacts | `contacts` |
| `sms.read` | `READ_SMS` | Read SMS | `sms` |
| `sms.send` | `SEND_SMS` | Send SMS | `sms` |
| `calendar.read` | `READ_CALENDAR` | Read calendars | `calendar` |
| `calendar.write` | `READ_CALENDAR` + `WRITE_CALENDAR` | Modify calendars | `calendar` |
| `camera` | `CAMERA` | Take photos (image picker) | `ui` |
| `microphone` | `RECORD_AUDIO` | Speech recognition | `ui` |
| `notifications.post` | `POST_NOTIFICATIONS` | Post notifications | `notifications` |

**Special grants** (no runtime dialog; the user enables them in system settings; query state with `permissions().special(...)` and jump with `openSettings(...)`):

| wire name | Setting | Purpose | Capability needed |
|---|---|---|---|
| `notifications.listen` | Notification access | Read/manage notifications (notification listening) | `notifications` |
| `accessibility` | Accessibility service | Read accessibility events, perform global actions | `accessibility` |
| `overlay` | Display over other apps | Show floating content | `permissions` |
| `usage-stats` | Usage access | Read app usage statistics | `permissions` |
| `battery-optimization` | Ignore battery optimizations | Keep background tasks unrestricted by the system | `permissions` |
| `install-packages` | Install unknown apps | Install/update APKs | `packages` |
| `manage-storage` | All files access | Read and write shared storage directly | `media` |

### Component declarations (`components`)

`components` declares the system component abilities that need a static host proxy. Plugins cannot register Activity/Service/Receiver themselves; the host relays through fixed proxy components. Every enabled item must have the matching capability, otherwise `COMPONENT_WITHOUT_CAPABILITY`; an invalid structure or boolean type also reports `COMPONENT_WITHOUT_CAPABILITY`.

| Field | Type | Purpose | Capability needed |
|---|---|---|---|
| `background` | bool | WorkManager/Alarm background tasks and re-registration on boot | `background` |
| `foregroundService` | bool | Host foreground service proxy, showing ongoing progress | `background` |
| `notificationListener` | bool | Notification listening: the `notificationReceived` callback and `recent`/`received`/`removed` queries | `notifications` (plus `permissions: ["notifications.listen"]`) |
| `accessibility` | bool | Accessibility proxy: `accessibility()` events and global actions | `accessibility` (plus `permissions: ["accessibility"]`) |
| `share` | bool | Receive system shares, firing `shareReceived` | `share` |
| `deeplinks` | string[] | Deep link paths to receive, e.g. `["sync", "sync/start"]`; matches "equal path or starts with `declared/`", max 128 characters, regex `^[a-z0-9][a-z0-9/._-]*$`, duplicates or invalid values report `INVALID_DEEPLINK` | `deeplink` |

Example:

```json
"capabilities": ["background", "share", "deeplink", "notifications"],
"permissions": ["notifications.listen"],
"components": {
  "foregroundService": true,
  "share": true,
  "deeplinks": ["sync"],
  "notificationListener": true
}
```

`LocalizedText` format:

```json
"name": { "default": "Plugin Template", "zh": "插件模板", "en": "Plugin Template" }
```

- `default` is the required fallback; `zh`/`en` are language tags, chosen by the app language, falling back to `default` when missing.
- A plain string shorthand (`"name": "Plugin Template"`) is also allowed and equals `default` only.
- `host.text(source, *args)` supports `String.format` placeholders (`%s`/`%d`), passing only strings/numbers (the signature keeps `vararg Any`; new code should not pass other types).
- `LocalizedText` and `IconRef` are read-only interfaces: build text with `localizedText(default, zh?, en?)` and icons with `assetIcon("asset:<path>")`; the level 5 `Literal`/`Resource` constructors were removed in v6.
- External plugins have no app resources; `host.text()` handles resolution and formatting.

### Theme packs (`assets/themes.json`)

Plugins can contribute fixed light or dark themes; the user switches them under Settings → Theme and they apply globally. Built-in themes are PI (the default, following the system light/dark setting), Classic Blue, Santorini and Oceanic. A theme pack only reads JSON from the install directory and never runs plugin code; when the plugin is disabled or the file is missing, the selection falls back to the default PI theme. `provides.themes` in `plugin.json` declares the theme ids and `assets/themes.json` defines them; the two must match exactly.

```json
{
  "schema": 1,
  "themes": [
    {
      "id": "aurora",
      "name": { "default": "Aurora", "zh": "极光", "en": "Aurora" },
      "dark": false,
      "base": "santorini",
      "tokens": {
        "paper": "#F7F8F8",
        "accent": "#7C3AED",
        "ink": "#1E3A47"
      }
    }
  ]
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `schema` | int | Yes | Fixed at `1` |
| `themes` | array | Yes | 1–24 themes, ids must not repeat |
| `id` | string | Yes | Theme id, regex `^[a-z][a-z0-9_-]*$`, max 64 characters; matches `provides.themes` |
| `name` | object | Yes | `default` is required, `zh`/`en` optional; chosen by app language, falling back to `default`; each entry max 64 characters |
| `dark` | bool | Yes | Theme light/dark; decides the color marker, status bar icons and the plugin's `ui.isDark` |
| `base` | string | Yes | `pi`, `classic`, `santorini` or `oceanic`; supplies defaults for tokens not overridden |
| `tokens` | object | Yes | Color overrides; keys must be in the allowlist below and values must be `#RRGGBB` or `#AARRGGBB` |

`base` is only a starting point: `pi` is the default PI palette, `classic` (Classic Blue) and `santorini` are light, and `oceanic` is dark; `dark` may differ from the `base` light/dark, and the final light/dark comes from `dark`. Tokens you do not write come from `base`.

Available tokens:

```
paper surface raised ink inkSecondary inkTertiary accent accentPressed onAccent
accentSoft danger success warning hairline wash composer scrim
codeBackground inlineCodeBackground codeKeyword codeString codeComment
codeNumber codeFunction codeType
```

Contrast is validated on the final palette:

| Pair | Minimum contrast |
|---|---|
| `ink` / `paper` | 4.5:1 |
| `ink` / `surface` | 4.5:1 |
| `inkSecondary` / `paper` | 4.5:1 |
| `onAccent` / `accent` | 3:1 |

The theme file is read and validated at install time; a file over 64 KiB, an invalid schema or field, an unknown token, a bad color format, insufficient contrast, duplicate ids, more than 24 themes, or `provides.themes` disagreeing with the file all reject the install with `THEMES_INVALID` and give the specific reason on the confirmation page or in the toast. A theme file that passes validation is unpacked into the install directory with `assets/**`, and the host only reads that file at startup, without running the plugin.

Validation error codes (run by the host at install time, `PluginValidation`):

| Error code | Meaning |
|---|---|
| `INVALID_ID` | The id is not in lowercase reverse-domain form |
| `INVALID_VERSION` | The version is not `major.minor.patch[suffix]` |
| `INVALID_MIN_HOST_VERSION` | minHostVersion cannot be parsed |
| `HOST_VERSION_UNSUPPORTED` | The host version is below minHostVersion |
| `API_LEVEL_UNSUPPORTED` | apiLevel is not within `MIN_SUPPORTED_API_LEVEL..host level` (level 5 is rejected here since 1.0.4) |
| `UI_API_LEVEL_UNSUPPORTED` | `ui.apiLevel` does not match the host `HOST_UI_API_LEVEL` (2) (**warning**: only Compose bindings are disabled, the plugin still loads) |
| `ENTRY_CLASS_MISSING` / `INVALID_ENTRY_CLASS` | Missing entry class or invalid class name |
| `MISSING_TEXT` / `INVALID_TEXT` | name/summary lacks `default`, or uses resource text |
| `INVALID_ICON` / `INVALID_URL` | Icon path or homepage is invalid |
| `INVALID_PROVIDES` | A provides entry is invalid or duplicated |
| `MISSING_CAPABILITIES` | The manifest has no `capabilities` field |
| `INVALID_CAPABILITY` | capabilities contains an unknown service name |
| `INVALID_UI_SLOT` | A slot id in uiSlots is unknown or lacks an instance suffix |
| `INVALID_SKILL` | A skill file returned by the plugin is invalid |
| `INVALID_PERMISSION` | `permissions` contains unknown or duplicate values |
| `PERMISSION_WITHOUT_CAPABILITY` | A declared permission has no matching capability |
| `COMPONENT_WITHOUT_CAPABILITY` | The `components` structure or field types are invalid |
| `COMPONENT_WITHOUT_CAPABILITY` | An enabled component has no matching capability |
| `INVALID_DEEPLINK` | A deep link path is malformed or duplicated |

## `.ddp` package format

A `.ddp` is just a zip (the extension does not take part in detection):

```
plugin.ddp
├── classes.dex                      # required, at least one; several load in order classes.dex, classes2.dex, ...
├── classes2.dex
├── assets/
│   ├── plugin.json                  # required, and only at this path
│   ├── icon.png                     # optional, referenced by the manifest icon
│   └── skills/...                   # optional, skill markdown and attached files
├── lib/<abi>/*.so                   # optional; enters the signature digest, not loaded today
└── META-INF/plugin-signature.json   # required for install
```

Constraints:

- Only four kinds of entries: dex/assets/lib/signature; directory entries are ignored.
- Entry names must use `/`, must not be absolute, and must not contain `..`, `\`, `:`, NUL; duplicate names are rejected.
- `assets/plugin.json` can only be at that one path.
- Whole package ≤ 100 MB, unpacked total ≤ 256 MB; `classes*.dex` indices must not repeat.
- dex comes from D8/R8 (AGP's `assembleDebug` already does this), and `minSdk = 29` guarantees the dex minApi is not higher than the host.
- The host loads `classes*.dex` with an in-memory ClassLoader; at install it copies the whole package to `files/plugins/<id>/<version>/bundle/` and unpacks `assets` into the same version directory.
- Plugin runtime data always lives in `files/plugins/<id>/data` (mapped to `host.dataDir`) and survives upgrades; `host.assetsDir` is the read-only assets unpacked for the current version.
- Version directories are kept per version: after an upgrade the previous version directory serves as a rollback point until the next successful upgrade or an uninstall.

## Build, sign and update

### Packaging and signing tasks

The template project's `build.gradle.kts` ships four packaging tasks; multi-module projects (such as the official plugin repository) factor this into a shared root `gradle/plugin-bundle.gradle.kts`.

| Task | Purpose |
|---|---|
| `generateSigningKey` | Generates `keys/plugin-signing.pem` and `.pub`; fails if they already exist and never overwrites |
| `assemblePlugin` | Repackages `build/outputs/apk/debug/<module>-debug.apk` into `build/plugin/<module>.unsigned.ddp`, keeping only `classes*.dex`, `assets/**`, `lib/**`, with fixed entry timestamps for reproducibility |
| `signPlugin` | Reads the Ed25519 key, signs the unsigned package and writes `META-INF/plugin-signature.json`; prints `publisher keyId`, whole-package sha256 and entry digests |
| `verifyPlugin` | Re-verifies the signature by the host rules and prints `Signature OK` |

Keys default to `keys/plugin-signing.pem`/`.pub` in the project root, generated by `generateSigningKey` (private key permissions narrowed to `600`, ignored by `.gitignore`); `signPlugin` only reads keys and fails with a hint on how to generate them when missing. A different prefix (without extension) can also be given through a Gradle property:

```bash
./gradlew -PpluginSigningKey=/secure/keys/my-plugin signPlugin
```

- The private key **never goes into version control and is never shared**; losing it means you can no longer publish updates under the same name.
- Changing the key means changing publisher identity, and installed users cannot upgrade with the new key.
- Repository keys under `keys/` are only for local development and signing official plugins.

### Signing algorithm (ddp-1)

Installing requires `META-INF/plugin-signature.json`, algorithm Ed25519 (the host prefers the platform Provider and falls back to BouncyCastle on older platforms).

**canonical entry digest**: sort every non-directory entry in the zip by the **UTF-8 byte order of the entry name**, excluding every entry that starts with `META-INF/`; each entry is one line:

```
<sha256-hex-lowercase><SP><uncompressed-size-decimal><SP><entry-name>\n
```

sha256 is computed over the **uncompressed contents** of the entry; lines are concatenated directly and the last line ends with `\n`; the whole UTF-8 byte sequence is the message to sign. The host recomputes it with the same algorithm when verifying.

**The signature file** has exactly 4 fields (unknown fields are ignored):

```json
{
  "algorithm": "Ed25519",
  "keyId": "ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213",
  "publicKey": "<base64(std) of X.509 SubjectPublicKeyInfo DER>",
  "signature": "<base64(std) of the 64-byte Ed25519 signature over the canonical entry digest bytes>"
}
```

**keyId must be derived from the public key**: `keyId = "ed25519:" + lowercase hex(SHA-256(SPKI DER))`. The host requires the `keyId` in the signature to match the recomputed value; that value is also the publisher fingerprint and the TOFU trust store key.

### Install, trust and update

There is exactly one install entry: pick a local `.ddp` through "Import plugin" on the Plugins page. The host first reads the package and verifies the signature (rejecting outright on failure), and the confirmation page shows the manifest and publisher information; after the user confirms, it writes the TOFU trust store, unpacks into the version directory, records the install and loads and enables the plugin immediately.

- The first install is the only trust decision point: the confirmation page shows the publisher name/`keyId`, whole-package sha256, size and the capabilities it will provide, and states "the plugin has all app permissions". After confirmation `keyId → publicKey` goes into the TOFU trust store.
- Before every load the `bundleSha256` in the install record re-checks the package, and the canonical digest is recomputed to verify the signature; a mismatch rejects the plugin and puts the error text on its row.
- **Updates must use the same key**: a new version with the same `id` must be signed with the same `keyId`, otherwise it is rejected as a publisher mismatch.
- Update flow: pick the new package → verify sha256 + signature (same key) → stage into the new version directory; activate immediately when no session is busy, and when sessions are busy the phase stays `PENDING_RESTART` and activation happens automatically once all sessions finish (it also continues after a process restart); activation runs the prepare → verify → commit → swap sequence, and the verify stage calls `onUpgrade(fromVersion)` and pre-builds contributions; a prepare/verify failure discards the staged version and deletes the broken version directory while the old version stays usable; a commit write failure keeps the staged version for a retry on the next start.
- The install record keeps `previousVersion` as a rollback point. Recovery rules: an interrupted activation → the next start skips automatic activation and reports "last activation interrupted"; two consecutive failed starts on the same version → disable automatically (`enabled=false`), and the user enabling it manually grants permission again.

### Marketplace previews

The marketplace entry's `plugin.previews` field configures detail page previews; the `plugin.json` manifest itself does not contain it:

- A list of portrait 9:20 image URLs, 1080×2400 recommended; only `https://` is accepted, the same URL is deduplicated, at most 6 images.
- The marketplace detail page shows them as 144×320dp cards with a 9:20 zero-crop fit; tapping opens full screen with left/right swiping; without `previews` there is no preview block.

### Marketplace access labels

The marketplace entry's `plugin.requiredAccess` field declares the system access tier the plugin needs to run; the `plugin.json` manifest itself does not contain it:

- Values `normal` (default) / `adb` / `root`, matching the Normal, ADB and Root tiers.
- Label according to what the plugin really needs: mark a higher tier only when it truly needs system capabilities under ADB or Root (for example a tool that only runs with root), and never inflate it.
- The client shows a colored label for the current tier (Normal green / ADB yellow / Root red); when the current tier is below the requirement, the marketplace entry and the installed plugin row warn that some features may be unavailable; a missing field or a non-enum value is treated as `normal` for compatibility with older indexes.

## Lifecycle and phases

The host keeps a phase per plugin (visible on the Plugins page and in the info dialog):

| Phase | Meaning |
|---|---|
| `INSTALLED` | Installed but not loaded (for example waiting for the next start) |
| `ACTIVE` | Enabled and running normally, all contributions in effect |
| `DEGRADED` | Enabled but `availability()` reports unavailable: skills are not enabled and the reason is visible; tools/prompts/UI/hooks remain at the plugin's discretion |
| `FAILED` | Load/enable/activation failed, reason in the error text |
| `DISABLED` | The user turned the plugin off ("user choice ≠ load success") |
| `PENDING_RESTART` | A new version is staged and activates after a restart |

Only loaded, running plugins (`ACTIVE`/`DEGRADED`) aggregate contributions; when `availability()` is unavailable, skills are disabled and the reason shown, **but the plugin's tools/prompts/UI/hooks are not cleared automatically** — unavailable capabilities should return empty from `contributions()`/`ui()` based on availability.

`SuperPIPlugin` lifecycle methods (`availability()` must be implemented; the rest have default empty implementations; callback exceptions are isolated by the host and never take the app down):

| Method | When it is called and what it must do |
|---|---|
| `availability(host)` | Called on every refresh; returning `Unavailable(reason)` puts the phase in `DEGRADED`, shows the reason and disables skills |
| `onEnable(host)` / `onDisable(host)` | The user enables or disables the plugin; `onEnable` is also called for enabled plugins at startup, and disabling calls `onDisable` first and then cancels the plugin scope |
| `onUpgrade(host, fromVersion)` | Called in the verify stage of new-version activation; `fromVersion` is the version currently in use |
| `skills(host)` | Provides skills; called on every refresh |
| `promptSections(host)` | Appends system prompt sections |
| `contributions(host)` | Provides global tools + hooks |
| `contributionsFor(host, session)` | Materialized once per session: returns the tools + hooks in effect for that session (hooks override the plugin's global hooks by `pluginId`); `session` is a live handle (nullable) used only to read session snapshots; defaults to delegating to `contributions` |
| `ui(host)` | Slot/page/composer action contributions |
| `uninstall(host)` | Before the user deletes the plugin, clean up the plugin's own external side effects (the host then deletes the install directory and skill overrides) |
| `close()` | Called on disable/uninstall/update/process exit (the host cancels the plugin scope first and waits up to 5s); do not call back asynchronously after `close` |

Plugins implementing `PromptOverridePlugin` (with the `prompt` capability declared in the manifest) also get `promptOverride(host)`: the host calls it once when materializing contributions, see "Prompt override".

Plugin sign-in, CLI installation, connection state and so on are all managed by the plugin itself: state goes into `host.dataDir`/`host.secrets()` and UI is drawn inside plugin pages; the host no longer provides install/connect/settings/status/action/detail row callbacks, and there is no model-facing plugin connection tool. After an action in a page changes capabilities, call `host.requestContributionsRefresh()`; the host re-materializes contributions and rebuilds sessions at a safe point (busy sessions take effect at the end of the current round), so tools/skills/prompts/pages update right after.

`host` is the plugin's only entry point (`PluginHost`): `manifest`, `context` (the app context injected by the host), `dataDir`, `assetsDir`, `scope`, `text`, 35 host service accessors (see "Host services") and `requestContributionsRefresh()`. Manifest types such as `manifest` are host-constructed read-only interfaces; plugins must not construct them.

### Per-plugin scope

- Each plugin has its own `SupervisorJob` and `CoroutineScope`, that is `host.scope`. Background coroutines in a plugin should hang off this scope (`host.scope.launch { ... }`).
- Disable, uninstall, update and process exit all follow the same path: cancel the scope → `close()` (5s timeout) → release the install handle → remove registrations/clear contributions → sync skills; an exception in any step is recorded and the remaining cleanup still runs.
- `close()` is not guaranteed to be called when the process is force-killed; state that must survive the process should be written to `host.dataDir` promptly.
- Re-enabling creates a new scope and a new `PluginHost`, and the plugin object is loaded again. **Do not cache host or scope in plugin instance fields**; always use the parameter passed to each callback.
- Coroutines must handle cancellation (`CancellationException` propagates upward, never swallowed); check `isActive` in long loops or at stage boundaries.
- Once a session run starts, the host freezes the hook set (see "Events and hooks"); a plugin disabled during that round may still receive the remaining events, so callbacks must tolerate changed state; put resource cleanup in `close()`/`onDisable`.

### One materialization

The host calls contribution methods at most once per plugin per refresh, aggregating the results into an immutable snapshot; tool assembly, prompts, skill sync, UI slots, conflict diagnostics and the Plugins page all consume the same snapshot and never call the plugin again on consumption paths.

- Every refresh calls `availability()` and `skills()`; when enabled and installed it also calls `contributions()`, `ui()` and `promptSections()`, and `promptOverride()` when the `prompt` capability is declared.
- `contributionsFor()` is called once per session (one session binding from establishment to close), and that session's tools and session hooks both come from that result; it is materialized again only after the session is rebuilt.
- Do not do heavy IO or depend on changing external state in these methods: put slow work on `host.scope` asynchronously (for example start polling in `onEnable` and rely on scope cancellation in `onDisable`/`close`).

### Failure recovery

A load/activation/enable failure puts the phase in `FAILED`, and an unavailable `availability()` puts it in `DEGRADED`. The row error text on the Plugins page shows the reason; the "⋯" menu at the end of the row offers recovery entries by state:

| Entry | Behavior |
|---|---|
| "Retry activation" | Clears the interrupted-activation marker and start counter, then activates again: a loaded plugin gets services injected again and `onEnable` called, an unloaded or auto-disabled plugin is loaded and enabled again; with a staged version the whole activation sequence reruns. On success it refreshes and clears the error; on failure it keeps the state and shows the new error text |
| "Roll back to <previousVersion>" | Appears when the record has a previous version: switches the installed version back, clears the staged record and deletes the failed version directory |
| Enable switch | Turning it off and on runs `onEnable` again, for manually recovering an auto-disabled plugin |

- Two consecutive failed starts on the same version disable the plugin automatically (phase `DISABLED`), and enabling it manually grants permission again and clears the start counter.
- A slot render crash only disables the matching Compose binding and falls back to the host view, without changing the plugin phase; rendering retries automatically after a plugin version upgrade. Only load failures go through retry/rollback/re-enable above.

## Tools

```kotlin
override fun contributions(host: PluginHost) = contributions(
    tools = listOf(
        pluginTool(
            name = "template_echo",
            label = "Template Echo",
            description = "Echo text back, streaming one partial update.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("text") { put("type", "string") } }
                put("required", buildJsonArray { add(JsonPrimitive("text")) })
            },
            promptSnippet = "template_echo echoes text",
            promptGuidelines = listOf("Use it only in development demos."),
        ) { call ->
            val text = call.args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            call.partial(textResult("received ${text.length} chars"))
            textResult("echo: $text")
        },
    ),
)
```

| Field | Description |
|---|---|
| `name` | Global tool name, lowercase `snake_case`; must not conflict with host reserved names (see below), and when names collide across plugins the first registration wins |
| `label` | Tool card title |
| `description` | Tool description for the model; state what it does, when to use it and its boundaries |
| `parameters` | JSON Schema (`{"type":"object","properties":{...},"required":[...]}`), passed to the SDK as is |
| `promptSnippet` | One-line summary in the system prompt (optional) |
| `promptGuidelines` | Additional tool usage rules for the model (optional) |
| `exposure` | `PluginToolExposure.DIRECT` (default; the tool is resident and directly callable) or `DEFERRED` (the tool stays out of the resident list and the model first finds it with `tool_search`, then runs it with `tool_execute`) |
| `execute` | `suspend (PluginToolCall) -> PluginToolResult` |

#### Tool exposure

Tools are `DIRECT` by default: name, description and parameters appear in the model's resident tool list. When you have many tools, or tools used only occasionally, declare `PluginToolExposure.DEFERRED`:

```kotlin
pluginTool(
    name = "calendar_sync",
    label = "Calendar sync",
    description = "Sync calendar events in the given time range",
    parameters = syncSchema,
    exposure = PluginToolExposure.DEFERRED,
) { call -> ... }
```

`DEFERRED` tools are not resident in the system prompt and only appear in the host's `tool_search` catalog. The model calls `tool_search(query)` to get the tool name and parameter docs, then executes with `tool_execute(name, arguments)`; the host never loads the tool into the session. Whether to fold is up to the plugin; a plugin can also offer a switch in its pages and make it take effect on new sessions through `requestContributionsRefresh()` (the current run's contribution set is frozen when the run starts).

Host reserved tool names (conflicts are rejected at install time): the engine defaults `read`/`write`/`edit`/`bash`; app built-ins `ask_user_question`/`present`/`system_shell`/`get_goal`/`create_goal`/`update_goal`/`job_*`/`timer_*`/`miniapp_*`/`web_search`/`web_fetch`/`calendar`/`screen_*`/`virtual_screen`/`todo_write`; host on-demand tools `tool_search`/`tool_execute` and the SDK's `list_mcp_resources`/`list_mcp_resource_templates`/`read_mcp_resource`; plus every `mcp__` prefix.

`PluginToolCall`:

- `callId`: the call id, for logs and correlation only.
- `args: JsonObject`: arguments passed by the model.
- `sessionId`: the current session id resolved by the host in `execute`; an empty string when there is no active session.
- `signal: PluginRunSignal?`: cancellation signal; long tasks call `signal?.throwIfCancelled()` at stage boundaries, or register cleanup with `signal?.onCancel { ... }`; do not swallow cancellation exceptions.
- `partial(result)`: streaming intermediate result, shown when a live tool card exists; the return value of `execute` is the final result.

`PluginToolResult`:

| Constructor | Purpose |
|---|---|
| `textResult(text, details?, isError?)` | Plain text result; `details` is host-side structured metadata (for tool cards and logs, not model context), where `superpi.fileRefs` is a host reserved key that plugins must not read or write |
| `richResult(blocks, details?, isError?)` | Multi-block result: `textBlock(text)`, `imageBlock(data, mimeType)` (base64 goes into model image content), `fileRefBlock(path, mimeType?)` (content keeps one `file: <path> (<mime>)` text line for the model to read, and the host also writes `{path, mimeType}` into the reserved details key `superpi.fileRefs`; tool cards and history render a clickable file row that survives reopening the session) |
| Result fields | `PluginToolResult` is a read-only interface (`text`/`blocks`/`details`/`isError`), constructed with the two factories above; the old `PluginToolResult.Text`/`Rich` constructors were removed |

Fields you write into `details` other than `superpi.fileRefs` are passed through to the tool card and the custom message card as is; `superpi.fileRefs` is generated by the host from `fileRefBlock(...)` in `richResult(...)`, and plugins must not overwrite it.

`isError = true` marks a failed tool result (the model can retry or explain it); an exception thrown from `execute` (other than cancellation) also becomes an error result, so do not use exceptions to convey business errors.

#### Three layers of tool results: raw / guidance / card (2.0.0, plugin tools only)

Cards belong to plugin tools only: a `PluginTool` result may carry a card, and the host renders it in the answer area by `superpi.card`. Plugins that run a CLI through bash (such as Sun Yat-sen University, Feishu, WeCom) produce no cards and do not parse bash commands; their results stay CLI text that the model reads and answers from, and the host does no card handling for bash results at all.

The tool writes three pieces of data into the result details, and `open fun Cards.deliver(callId, result)` (plugin-api 2.0.0, host 2.0.0+) assembles them into model-visible text and display text:

| details key | Contents | Who uses it |
|---|---|---|
| `superpi.raw` | The full upstream output (verbatim; CLI results drop ANSI, `[exit: N]` and host hints, with no summarization or truncation) | `Cards.deliver` appends it to the "full return" section of the model text |
| `superpi.guidance` | Short guidance for the model: next step, failure reason, constraints that must be asked about, output file paths, pagination/login hints; do not restate facts already in raw such as names, prices or IDs | `Cards.deliver` appends it to the "usage instructions" section |
| `superpi.card` | `{ "customType": "...", "title": "...", "payload": {...} }` | `Cards.deliver` generates the "call present to show this card" instruction and writes `superpi.display` for the tool card to display |

- Plugins do not assemble card ids or the `present` instruction themselves, and must not put it into the card payload; the instruction in the model text is generated uniformly by `Cards.deliver` (the `pluginTool` factory calls it automatically, while plugins that implement `PluginTool` themselves must call it explicitly once; repeated calls are idempotent).
- The text of `textResult` contains only the plugin's own body (for example a list of mailbox facts); `raw`/`guidance` are no longer assembled by the host.
- For error results (`isError = true`), `Cards.deliver` drops the card and the `present` instruction, keeping only the body, the full return and the usage instructions.
- The card payload must be complete (including raw dirty strings, IDs, coordinates, image URLs, state, pagination tokens); trimming happens only in the default preview/expand of the rendering layer.
- Fetch image URLs with `host.images().load(url, maxEdgePx)` (the manifest must declare the `images` capability), which returns `RemoteImage.bitmap`; render it with `DdRemoteImage` from design 1.0.0, and use `DdImageViewer(bitmap)` for full screen. Images only go into host memory and the private cache; do not write them to shared storage or the plugin data directory.
- `plugin-api 2.0.0` + `design 1.0.0`: plugins using `host.images()` write `minHostVersion: "1.4.0"`; `plugin-api 2.0.0` is still additive within level 6.

Tools enter the session with enabled plugins by default; when you need session information, use the live handle from `contributionsFor`:

```kotlin
override fun contributionsFor(host: PluginHost, session: PluginSessionInfo?) = contributions(
    tools = listOf(
        pluginTool(
            name = "session_info",
            label = "Session Info",
            description = "Return the active session id and working directory.",
            parameters = buildJsonObject { put("type", "object") },
        ) { _ ->
            val snapshot = session?.snapshot()
            if (snapshot == null) {
                textResult("No session is bound.", isError = true)
            } else {
                textResult("session=${snapshot.sessionId} cwd=${snapshot.cwd}")
            }
        },
    ),
)
```

- `session` in `contributionsFor` is a **live session handle**: do not cache or copy its fields, and do not assume an active session exists when it is called.
- Inside a tool's `execute`, call `session?.snapshot()` for the latest `PluginSessionSnapshot(sessionId, file, cwd)`; it returns `null` when there is no active session.
- `session.supportsChatDelivery` says whether the session supports delivering custom cards (mini app one-off agents and similar scenarios do not).

## Skills and prompt sections

`pluginSkill(id, markdown, files)`:

- `id`: lowercase `[a-z][a-z0-9_-]*`; when global skill ids collide the first registration wins.
- `markdown`: the full SKILL.md content, which must have YAML frontmatter (`name`, `description`); the body is written for the model.
- `files`: attached files, built with `pluginSkillFile(path, content?, assetPath?)` — `path` is a relative path inside the skill directory, and `content` (inline string) and `assetPath` (`assets/<path>` inside the package) are mutually exclusive; `assetPath` is read as text on the host side.
- When the plugin can contribute, the host materializes skills into the agent skill directory and cleans them up on disable/uninstall using an ownership marker; do not write files yourself in `skills()`.

`promptSections(host)` returns a list of `promptSection(id, body, priority)` appended to the system prompt; sorted by `priority` ascending (smaller first), **ids must be unique within one plugin**.

### Prompt override (`PromptOverridePlugin`, experimental)

The experimental capability added in v6: after declaring the `prompt` capability in the manifest and implementing `PromptOverridePlugin`, a plugin can override the preamble and rules of the agent system prompt, not just append sections. The API is marked `@PluginExperimentalApi`, needs an explicit opt-in, and may change while experimental.

```kotlin
import io.github.whywood.superpi.plugin.api.promptOverride as promptOverrideOf

class MyPlugin : SuperPIPlugin, PromptOverridePlugin {
    override fun promptOverride(host: PluginHost): PromptOverride = promptOverrideOf(
        preamble = "You are a sync assistant.",
        rules = "- Confirm the target directory before syncing.",
        sections = listOf(promptSection("sync", "Report the file count after syncing completes.")),
        priority = 10,
    )
}
```

- The manifest must declare `capabilities: ["prompt", ...]`; otherwise the host does not call `promptOverride()`.
- Merge rules: among several plugins, `preamble`/`rules` come from the highest `priority` (conflicts are written to the host log); `sections` are concatenated by `priority` ascending and join the sections from `promptSections()` in the plugin section of the system prompt.
- Budget: `preamble`, `rules` and `sections` of a single plugin total 32 KiB, and all plugins together 64 KiB; a plugin that exceeds it has its whole contribution skipped and logged.
- Override applies only to the agent's preamble and rules; the workspace/environment sections generated by the host are not overridable and are always kept.
- Callback exceptions are isolated by the host, only logged, and never interrupt the session.
- Division of labor with `promptSections()`: `promptSections()` only appends sections and works for any plugin; prompt override needs the `prompt` capability and replaces the agent's default identity and rules.

## Events and hooks

Every `PluginHooks` callback is a `suspend` function with a default empty implementation, so implement only what you need. Hooks are returned from `contributions(host)` (global) or `contributionsFor(host, session)` (session level), and both feed the same dispatch pipeline. Observation events and decision types are host-implemented read-only interfaces; plugins must not implement or construct them.

**Session-level hooks**: the `Contributions` returned by `contributionsFor(host, session)` are the tools + hooks in effect for that session, where hooks override the global hooks of the same plugin by `pluginId`. The default implementation delegates to `contributions(host)`, so plugins that only return hooks from `contributions()` behave the same (still one set per plugin); when you only add session tools in `contributionsFor`, return `contributions(tools = ...)` (with `hooks` left at the default `PluginHooks.None`, the plugin's global hooks are used). When the session contribution returns the default `PluginHooks.None` (no hooks provided explicitly), the plugin's global hooks are used, so writing `contributionsFor` does not silently disable hooks. Each session is materialized once: one session binding calls `contributionsFor` once from establishment to close, and tools and session hooks share that result; a capability change (enable/disable, or a refresh after a page action) triggers a session rebuild at a safe point, and only then is it materialized again. Materialization for different sessions does not block each other and may run concurrently, so plugin implementations must not rely on shared mutable state across sessions.

**Run-level freeze**: when a run starts (`RunStartedEvent`), the host freezes the hook set in effect, and all observation events and transforms/gates in that run go through it: enabling or disabling plugins, refreshing contributions or adding plugins after the freeze has no effect on this run — a frozen plugin still receives the run's remaining events even if disabled; newly enabled plugins take effect from the next run. After the run ends (`RunFinishedEvent`; a failed run also arrives as `RunFinishedEvent(error)`) and the events of the run are dispatched, hooks unfreeze and the next run freezes the latest plugin snapshot.

Note: a plugin disabled during the freeze may still receive the run's remaining events, so callbacks must tolerate the plugin having been closed or state having changed; do not rely on callback timing for resource cleanup (put cleanup in `close()`/`onDisable`). The premise is one active run per session at a time.

A mini app one-off agent reuses the same prompt/input/tool hooks and session binding, but its session events do not go through the host session observation pipeline, so the 21 session observation events and run freezing cover only the main session run path; there are 6 more system events below.

### Observation events (notify only, cannot change the flow)

Dispatched serially in plugin registration order, with a 5s timeout per handler (`PluginHooks.OBSERVE_TIMEOUT_MS`); timeouts and exceptions are logged and dispatch continues without affecting the session. Session events are dispatched one by one in arrival order, and structured events take precedence over streaming deltas (the oldest deltas are dropped when overloaded).

| Callback (`PluginHooks` method) | Event type | Available fields | When |
|---|---|---|---|
| `sessionStarted(event: SessionStartedEvent)` | `SessionStartedEvent` | `sessionId`, `title?`, `workspace?` | Session created |
| `sessionClosed(event: SessionClosedEvent)` | `SessionClosedEvent` | `sessionId` | Session closed |
| `runStarted(event: RunStartedEvent)` | `RunStartedEvent` | `sessionId`, `runId` | An agent run starts |
| `runFinished(event: RunFinishedEvent)` | `RunFinishedEvent` | `sessionId`, `runId`, `error?` (`succeeded`) | Run finished |
| `turnStarted(event: TurnStartedEvent)` | `TurnStartedEvent` | `sessionId`, `turnId` | A model turn starts |
| `turnFinished(event: TurnFinishedEvent)` | `TurnFinishedEvent` | `sessionId`, `turnId`, `failed` | A model turn ends |
| `messageStarted(event: MessageStartedEvent)` | `MessageStartedEvent` | `sessionId`, `messageId`, `role` (`USER/ASSISTANT/TOOL/SYSTEM`) | A message starts |
| `messageDelta(event: MessageDeltaEvent)` | `MessageDeltaEvent` | `sessionId`, `messageId`, `delta` | Streaming delta |
| `messageFinished(event: MessageFinishedEvent)` | `MessageFinishedEvent` | `sessionId`, `messageId`, `text?` | A message completes |
| `toolExecutionUpdate(event: ToolExecutionUpdateEvent)` | `ToolExecutionUpdateEvent` | `sessionId`, `callId`, `toolName`, `phase` (`STARTED/PARTIAL/FINISHED/FAILED`), `partial?` (`PluginToolResult`) | Tool execution state |
| `compactionStarted(event: CompactionStartedEvent)` | `CompactionStartedEvent` | `sessionId` | Context compaction starts |
| `compactionFinished(event: CompactionFinishedEvent)` | `CompactionFinishedEvent` | `sessionId`, `tokensBefore`, `tokensAfter` | Compaction finishes |
| `compactionFailed(event: CompactionFailedEvent)` | `CompactionFailedEvent` | `sessionId`, `error` | Compaction failed |
| `modelChanged(event: ModelChangedEvent)` | `ModelChangedEvent` | `sessionId`, `model` (`provider/model`) | Model switched |
| `thinkingLevelChanged(event: ThinkingLevelChangedEvent)` | `ThinkingLevelChangedEvent` | `sessionId`, `level` | Thinking level switched |
| `retryScheduled(event: RetryScheduledEvent)` | `RetryScheduledEvent` | `sessionId`, `attempt`, `delayMs`, `reason?` | Retry scheduled |
| `retrySucceeded(event: RetrySucceededEvent)` | `RetrySucceededEvent` | `sessionId`, `attempt` | Retry succeeded |
| `retryExhausted(event: RetryExhaustedEvent)` | `RetryExhaustedEvent` | `sessionId`, `attempts`, `error` | Retries exhausted |
| `mcpStatusChanged(event: McpStatusChangedEvent)` | `McpStatusChangedEvent` | `server`, `status` (`OK/AUTH_REQUIRED/FAILED/UNKNOWN`) | MCP status changed |
| `mcpToolsChanged(event: McpToolsChangedEvent)` | `McpToolsChangedEvent` | `server`, `tools` (`List<String>`) | MCP tools changed |
| `busEvent(event: BusEventReceived)` | `BusEventReceived` | `event` (`PluginBusEvent`: `topic`/`publisher`/`payload`/`publishedAt`) | Plugin bus event |

21 session observation callbacks in total.

### System events (no session context)

The 6 observation events below are dispatched by the host when system-side events arrive; they are not bound to a session and do not go through run freezing. The target is every plugin that returned global hooks from `contributions()` and meets the gate. The per-handler timeout is 5s as well; timeouts and exceptions are logged and dispatch continues.

| Callback | Event type | Fields | Trigger and gate |
|---|---|---|---|
| `notificationReceived(event: NotificationReceivedEvent)` | `NotificationReceivedEvent` | `notification` (`PluginNotificationRecord`), `removed` | The notification listener receives or removes a notification; `notifications` + `components.notificationListener` |
| `notificationAction(event: NotificationActionEvent)` | `NotificationActionEvent` | `notificationId`, `actionId`, `payload` | The user taps an action button on a notification the plugin itself posted; only the `notifications` capability is needed |
| `backgroundTask(event: BackgroundTaskEvent)` | `BackgroundTaskEvent` | `taskId`, `kind`, `firedAtMs` | A background task registered by the plugin fires or replays at boot; `background` + `components.background` or `components.foregroundService` |
| `shareReceived(event: ShareReceivedEvent)` | `ShareReceivedEvent` | `text?`, `streams` (`SharedStream`: `path`/`mimeType`/`name`), `title?` | The user shares content to PI; `share` + `components.share` |
| `deepLinkReceived(event: DeepLinkReceivedEvent)` | `DeepLinkReceivedEvent` | `path`, `arguments` | A received deep link path matches a `components.deeplinks` declaration; `deeplink` |
| `appLifecycle(event: AppLifecycleEvent)` | `AppLifecycleEvent` | `foreground` | The app enters the foreground or background; `app` |

- System events go only to global hooks (the `PluginHooks` returned by `contributions()`); session-level hooks never receive them.
- When a gate is not met the event is neither dispatched nor reported: notification listening, accessibility, share, deep links and background tasks all additionally require the user to grant the matching access in the system or to agree to the component declaration at install time.

### Transforms and gates (can change the flow)

Executed serially in plugin registration order with a 2s timeout per handler (`PluginHooks.TRANSFORM_TIMEOUT_MS`); timeouts and exceptions follow the failure policy of each stage:

| Method | Input → output | Failure policy |
|---|---|---|
| `promptTransform(request: PromptRequest): String` | Session id + prompt → new prompt, chained | fail-open: keep the previous value |
| `inputTransform(request: InputRequest): InputDecision` | Input text + image paths → `inputContinue(text, imagePaths)` continues (chained), `inputHandled()` short-circuits and drops this input | fail-open: keep the current input |
| `beforeToolCall(request: ToolCallRequest): ToolCallDecision` | Tool call (including MCP/dynamic tools) → `toolContinue()` / `toolBlock(reason)` / `toolRewrite(args)` (later plugins continue from the rewritten args) | **fail-closed**: timeout/exception blocks the call with a reason |
| `context(request: PluginContextRequest): String?` | Returns retrieval/memory text to inject into the system prompt; several plugins concatenate in registration order | fail-open: skipped on timeout/exception; truncated at 8 KiB per plugin and 32 KiB in total |
| `afterToolExecution(request: ToolExecutionResult): ToolResultDecision` | Tool result → `resultKeep()` / `resultReplace(result)` (chained replacement) | keep: retain the previous result |

Notes:

- These callbacks sit on the session critical path and must return fast; move slow work to `host.scope` asynchronously, and do not do network or heavy IO inside them.
- Do not block a transform waiting for the user; gates that need user confirmation should only make a synchronous decision.
- The `imagePaths` returned by `inputTransform` really take effect: the images at those paths are read and replace or join this input (unreadable paths are ignored), and an empty list means no images.
- `PluginHooks.None` can be used as a placeholder; event order = load/registration order in the currently effective set (plugin install registration order).
- `CancellationException` caused by user cancellation propagates upward; do not swallow it.

## UI extensions

The host fixes the main shell, navigation and chat skeleton; plugins can register **plugin pages**, attach Compose bindings to two slots, or contribute composer actions; they cannot replace a whole screen or register Activity/Service in the manifest. Plugin pages are the plugin's only self-drawn surface: they render inside the current navigation stack by default, and when a dedicated window is needed use the host-proxied plugin window (`components().startActivity`).

### Slot catalog

| slotId | Scenario |
|---|---|
| `tool.card.content:<toolName>` | Content area of the tool call card for the named tool |
| `chat.message:<customType>` | Content area for a custom message type |

`slotBinding(slotId, priority, content)`: only one binding renders per slot — the first renderable one by descending `priority` (same priority: plugin registration order), and the rest are overridden; when no binding is renderable the host falls back to the default view.

`SlotIds` provides helpers:

```kotlin
SlotIds.toolCard("template_echo")            // "tool.card.content:template_echo"
SlotIds.chatMessage("card")                  // "chat.message:card"
SlotIds.matches(slotId)                      // validates a full slot id
```

### Tool result renderers (1.3.0)

Slots are "registered by exact tool name", which suits a plugin's own tools. To render **MCP tools** or a batch of tools with different names with a plugin card, use tool result renderers:

```kotlin
class TravelPlugin : SuperPIPlugin {
    override fun ui(host: PluginHost): PluginUiContributions =
        object : PluginUiContributions, ToolResultRendererContributor {
            override val bindings = emptyList<SlotBinding>()

            override val toolResultRenderers = listOf(
                toolResultRenderer(
                    id = "amap-route",
                    match = toolResultMatch(
                        source = ToolResultSource.MCP,
                        server = "amap",
                        toolPrefix = "route_",
                    ),
                ) { state ->
                    val data = state.structuredContent as? JsonObject
                    DdGroupCard {
                        DdText("Route", style = DdType.heading)
                        DdText(data?.get("summary")?.jsonPrimitive?.content ?: state.result?.text.orEmpty())
                    }
                },
            )
        }
}
```

Match rules:

- `source`: `ToolResultSource.PLUGIN` / `MCP` / `BUILTIN`; empty means any;
- `server`: the MCP server name, which must match the name the user gave in MCP settings;
- `tool`: exact tool name match; `toolPrefix`: tool name prefix; both can be used together;
- only one renderer per tool: higher `priority` first, then the more specific match, then plugin registration order;
- when a plugin is disabled, the UI is incompatible or no renderer matches, the default tool card is used automatically; do not `require` inside a renderer.

`ToolResultState` provides:

| Field | Description |
|---|---|
| `callId` / `toolName` | The call id and the inner tool name; MCP tools called through `tool_execute` are restored to the real MCP tool name automatically |
| `source` / `server` | Tool source and MCP server name |
| `arguments` | Tool arguments; the `tool_execute` wrapper restores the inner arguments |
| `result` | The same `PluginToolResult` as the tool card slot (text/images/files/errors) |
| `structuredContent` | The `structuredContent` returned by the tool; MCP results are persisted by the host (256 KiB limit) and remain available for rendering after reopening the session; null when absent |

- MCP tools are called through `tool_search` + `tool_execute` by default, and renderers do not need to care about that wrapper.
- Structured content is for UI only and does not enter the model context; do not stuff images or large JSON into `details` expecting the host to render them — prefer `structuredContent`.
- Renderer content is subject to the same plugin UI compatibility constraints (`ui.apiLevel` / `kotlin` / `compose`) and retries automatically after a version upgrade; Compose exceptions have no crash isolation, just like slots, so test them yourself.
- Tool result renderers need `plugin-api 2.0.0+` and host `1.1.0+`; plugins using only older capabilities are unaffected.

Pages and composer actions are contributions, not slots: `PluginUiContributions.pages` (built with `pluginPage(id, title, icon?, content)`) declares plugin pages with inline page content, and `PluginUiContributions.composerActions` (built with `composerAction(id, label, icon?)`) declares composer actions. `PluginUiContributions` is a read-only interface, `pages` / `composerActions` have default empty implementations, and plugins implement only `bindings` (plus the members they need). In the plugin page list, tapping a row that has `pages` enters the first page; composer action chips appear in the chat composer and open that plugin's page when tapped (the action id opens the matching page when it equals a page id, otherwise the first page).

### Pages and arguments

`pluginPage(id, title, icon?) { ... }` declares a page. Since 1.1.0 pages can be opened with arguments and return results:

```kotlin
host.ui().openPageWithArguments(
    pageId = "detail",
    arguments = buildJsonObject { put("id", "42") },
    newTask = false,
)
```

- With `newTask = true` the page opens in a dedicated host-proxied window (`PluginHostActivity`), which suits scanning, photography and long flows; `false` opens it inside the current navigation stack.
- Inside the page, read `PluginPageContext(pageId, arguments)` through `host.ui().pageContext()` or Compose's `LocalPluginPageContext.current`; it returns null when called outside a plugin page.
- Close a page with `host.ui().closePage(result)`, where `result` is optional JSON; closing the dedicated window returns to the host UI.
- You cannot open another plugin's page: `openPage(...)`/`openPageWithArguments(...)` only accept page ids declared by the calling plugin.

Since 1.2.0 a page can also open as a sheet stacked on the chat, for cases like "tap a chat card to see details" that should not leave the current session:

```kotlin
host.ui().openPageSheet(
    pageId = "detail",
    arguments = buildJsonObject { put("threadId", threadId) },
)
```

- The host carries the same `PluginPage.content()` in a bottom sheet and does not push a navigation page; swiping down, tapping the scrim, pressing back or the plugin calling `closePage()` all close the sheet and return to the chat.
- Inside the page, check the current presentation with `LocalPluginPageContext.current?.presentation == PluginPagePresentation.SHEET`; `pageContext().presentation` defaults to `PAGE`, and ordinary pages and dedicated windows are unaffected.
- Only one plugin page sheet is allowed at a time; opening a `DdSheet` inside it (for example a compose form or a picker) is allowed, and sheets close starting from the topmost one.
- When you need a full page, still use `openPage(...)`/`openPageWithArguments(...)`; a common pattern in plugin cards is "tap the content to open a sheet, open the full page from the footer".
- `isDark` follows the host's current theme (built-in or plugin theme), not just the system dark mode.

### Interaction and pickers

`UiService` provides host-style system interactions; plugins should not show their own Material dialogs. These methods need the page in the foreground, and calling them from the background or from tool callbacks may return null/false:

| Method | Returns | Description |
|---|---|---|
| `confirm(uiConfirmRequest(title, message?, confirmLabel?, cancelLabel?, destructive))` | `Boolean` | Confirmation dialog; `destructive` uses the danger color |
| `prompt(uiPromptRequest(title, message?, initial, placeholder?, multiline))` | `String?` | Text input; cancel returns null |
| `choose(uiChooseRequest(title, listOf(uiChoice(id, label, description?)), message?))` | `String?` | Single-choice list, returns the selected `id` |
| `pickDate(uiDatePickRequest(initialMs?, minMs?, maxMs?))` | `Long?` | Date picker |
| `pickTime(uiTimePickRequest(initialHour?, initialMinute?, is24Hour))` | `Int?` | Time picker, returns `hour * 60 + minute` |
| `pickFiles(uiFilePickRequest(mimeTypes, multiple, maxCount))` | `List<PluginPickedFile>` | System file picker; cancel returns an empty list |
| `pickImage(uiImagePickRequest(source = UiImageSource.GALLERY/CAMERA, frontCamera))` | `PluginPickedFile?` | Gallery or camera; cancel returns null |
| `scanQr()` | `String?` | Scan a code and return the text |
| `pickDirectory()` | `String?` | Pick a directory |
| `snackbar(message, actionLabel?, action?)` | — | Bottom light message, with one optional action |
| `shareFiles(paths, mimeType?)` | — | Share the plugin's own files (through the host FileProvider) |
| `isDark` | `Boolean` | Whether the current theme is dark |
| `openUrl` / `share(shareRequest(...))` / `toast(message)` | — | Open a link, share text, Toast (light feedback only) |

`PluginPickedFile` fields: `path` (a local path the host can read), `uri`, `name`, `sizeBytes`, `mimeType`.

```kotlin
val picked = host.ui().pickImage(uiImagePickRequest(source = UiImageSource.CAMERA))
val note = host.ui().prompt(uiPromptRequest(title = "Note", placeholder = "Optional")) ?: return
host.ui().snackbar("Imported", actionLabel = "View") { host.ui().openPage("home") }
```

- The host requests runtime permissions for camera/gallery/voice uniformly; plugins only declare `camera`/`microphone` in the manifest `permissions` (capability `ui`).
- `shareFiles` only accepts paths the plugin itself can read; to share another app's files, copy them into `host.dataDir` first.

### Compose rendering

`SlotContent` is a read-only interface built with the `composeSlot(render)` factory; `SlotState` depends on the slot family:

| Slot family | State | Fields |
|---|---|---|
| Tool card | `ToolCardSlotState` | callId/toolName/args/result (structured arguments and result) |
| Custom message | `CustomMessageSlotState` | messageId/customType/payload |
| Stateless | `NoSlotState` | — |

Plugin page content does not go through `SlotState`: the `content` of `pluginPage(...)` is a plain Composable (`@Composable () -> Unit`) that captures host or page state directly in its closure.

Compatibility checks (failing any one disables only that plugin's UI, never the plugin itself):

- Manifest `ui.apiLevel <= HOST_UI_API_LEVEL` (a missing `ui` block passes at the host level).
- If the manifest sets `ui.kotlin`, it must match the host Kotlin **major.minor version** (for example `2.4`).
- If the manifest sets `ui.compose`, it must match the host Compose runtime version **exactly** (not the Compose BOM version); the host also disables UI when it cannot detect its own Compose version. When unsure about the host version, leave both fields out and write only `ui.apiLevel`.
- When UI is disabled `pagesOf` returns empty and slots do not render, while the plugin's tools/skills/hooks still apply.

```kotlin
override fun ui(host: PluginHost) = object : PluginUiContributions {
    override val bindings = listOf(
        slotBinding(
            slotId = SlotIds.toolCard("template_echo"),
            content = composeSlot { state -> MyToolCard(state) },
        ),
    )

    override val pages = listOf(
        pluginPage(id = "home", title = localizedText("Home", "首页"), icon = assetIcon("asset:icon.png")) {
            MyPage(host)
        },
    )
}
```

- Slot and page content render on the Compose main thread; do not do blocking IO inside a composable — load data with `remember` + `LaunchedEffect` and handle cancellation.
- Slots and pages have no process-level isolation: an exception thrown by Compose content crashes the app (same as tool/callback exceptions), so plugin authors must verify it themselves. An incompatible binding only records the reason and falls back to the host view, leaving the plugin's other capabilities intact.
- External plugins cannot reference app resources; icons use `assetIcon("asset:<path inside package>")`, and page titles may omit the icon.

### Plugin UI uses only `:design`

Plugin UI uses the app's design system `io.github.whywood.superpi.design` directly (`plugin-api` already carries `design 0.7.0` through `api()`, so plugin projects do not declare the coordinate again). Components are identical to the app's and inventing styles is forbidden; new components are in the second and third blocks of the table below:

| Component | Purpose |
|---|---|
| `DdGroupCard` | White rounded group card (20dp radius, 16dp inner padding, hairline between rows) |
| `DdListRow` | List row inside a card (title/subtitle/trailing value/icon/clickable/trailing) |
| `DdSectionLabel` | Section label |
| `DdPrimaryButton` | Primary button (52dp tall, accent fill, at most one per screen) |
| `DdTextAction` | Text button (accent/inkSecondary for secondary actions) |
| `DdTextField` / `DdTextArea` | Text fields (52dp tall, accent border when focused) |
| `DdSwitch` | Switch (52x32, accent track) |
| `DdChip` | Tag/filter |
| `DdSheet` + `DdSheetHeader` | Bottom sheet and header |
| `DdDialog` | Centered confirmation dialog |
| `DdMenu` / `DdMenuItem` | Dropdown menu |
| `DdEmptyState` / `DdLoadingBlock` / `DdSpinner` | Empty and loading states |
| `DdHairline` | Divider |
| `DdType` | Type scale: `pageTitle`/`heading`/`body`/`bodySmall`/`label`/`button` |
| `superPiColors()` | Color tokens: `paper`/`surface`/`raised`/`ink`/`inkSecondary`/`inkTertiary`/`accent`/`accentSoft`/`danger`/`success`/`warning`/`hairline`/`wash` |

`design 0.7.0` adds:

| Component | Purpose |
|---|---|
| `DdMarkdown(text, live, onFileClick, onLinkClick, onImage)` | Markdown rendering (headings/lists/tables/code/images/footnotes); `live` for streaming text, `onImage` handles image taps |
| `DdHtmlText(html, onLinkClick?, imageLabel?, style, color, linkColor)` | Restricted HTML text |
| `DdWebView(url, state = DdWebViewState())` / `DdWebView(DdWebContent.Url/Html, ...)` | Embedded web page; `state` provides `canGoBack`/`goBack`/`reload`/`evaluateJavascript`/`dispose` |
| `DdImage(path / bytes / bitmap, contentDescription, contentScale, shape, maxLongEdge, failure)` | Local image loading with placeholder and failure states |
| `DdImageViewer(path / bytes / bitmap, onDismiss)` | Full-screen image viewer (Dialog) |
| `DdPullToRefresh(isRefreshing, onRefresh) { ... }` | Pull-to-refresh container |
| `DdSnackbarHostState` + `DdSnackbarHost(state)` | In-page light messages; `showSnackbar(...)` returns a result |
| `DdFileRow(name, subtitle?, icon, onClick?, enabled, trailing)` | File/attachment row |
| `DdDatePicker(selected: LocalDate, onSelect, onDismiss, title, previousMonthLabel, nextMonthLabel)` | Date picker sheet |
| `DdTimePicker(selected: LocalTime, onSelect, onDismiss, title, doneLabel)` | Time picker sheet |

`design 0.8.0` adds (needs an explicit dependency and `minHostVersion: "1.0.7"`, see below):

| Component | Purpose |
|---|---|
| `DdSheet(onDismiss, fullBleed?, containerColor?, content)` | Rewritten bottom sheet: drags down only; scrim taps and back play the exit animation first; max height is the window height minus the status bar; long content scrolls itself (`verticalScroll`/`LazyColumn`), so stop writing `fillMaxHeight(0.94f)` |
| `DdSwipeRow(key, actions, openKey, onOpenChange, enabled, content)` + `DdSwipeAction(icon, label, tint, background, onClick)` | List row swipe actions: two anchors with snapping, only one row open at a time, haptics past the threshold; the row content provides its own opaque background |
| `DdHtmlBlocks(html, loader, onLinkClick, onImageClick, imageLabel, ...)` + `DdHtmlImageLoader` | HTML body with images: split by `<img>`, text blocks keep `DdHtmlText` formatting support; the loader returns `ImageBitmap` and shows a placeholder on failure |
| `DdInfoTooltip(text, contentDescription?, icon?)` | Info icon at the end of a row with a tap/long-press hint, replacing permanent helper text |
| `LocalDdMotionEnabled` | System "reduce motion" switch; custom animations skip when it is false |

```kotlin
compileOnly("io.github.sadami3066.superpi:design:0.8.0")
```

```kotlin
DdHtmlBlocks(
    html = detail.htmlBody.orEmpty(),
    loader = myImageLoader,
    imageLabel = host.t("含图片", "Image"),
    onImageClick = { source -> /* open the viewer */ },
)
```

`design 1.0.0` adds (needs an explicit dependency and `minHostVersion: "1.3.0"`):

| Component / API | Purpose |
|---|---|
| `DdExpandToggle(expanded, collapsedLabel, expandedLabel, onToggle)` | Expand/collapse for long card lists: one line of text plus a rotating chevron, 44dp target; copy comes from the caller |

```kotlin
if (items.size > PreviewRows) {
    DdExpandToggle(
        expanded = expanded,
        collapsedLabel = host.t("展开全部 ${items.size} 条", "Show all ${items.size}"),
        expandedLabel = host.t("收起", "Show less"),
        onToggle = { expanded = !expanded },
    )
}
```

`design 1.0.0` adds (needs an explicit dependency and `minHostVersion: "1.0.8"`, see below):

| Component / API | Purpose |
|---|---|
| `DdStatusRow(label, value, tone, busy)` | Status summary row: label on the left, value on the right, `DdStatusTone` controls the dot/spinner color; on refresh keep the old value and mark only that row busy |
| `DdNoticeBar(text, tone, actionLabel?, onAction?)` | Inline page notice: `DdNoticeTone.INFO/SUCCESS/WARNING/DANGER`, good for errors with retry, sign-in prompts and action results |
| `DdProgressBar(progress: Float?)` | Download/install/sync progress; `null` is indeterminate, pass 0..1 for determinate progress; respects reduce motion |
| `SuperPITheme(colors, fontStyle)` | Wraps a page in a custom `SuperPIColors` (the host already sets it, so plugins usually do not call it) |
| `DdTheme.lightPi()` / `DdTheme.darkPi()` | The default PI light and dark palettes, usable as a theme pack `base` or a starting point for custom colors; `DdTheme.lightClassicBlue()` remains for the Classic Blue palette |
| `DdStatusTone` / `DdNoticeTone` | The status/notice tones above |

```kotlin
DdGroupCard {
    DdStatusRow("Runtime", if (linuxAvailable) "Ready" else "Unavailable", if (linuxAvailable) DdStatusTone.SUCCESS else DdStatusTone.DANGER)
    DdStatusRow("Sign-in status", if (connected) "Connected" else "Not connected", if (connected) DdStatusTone.SUCCESS else DdStatusTone.NEUTRAL, busy = checking)
}
if (error != null) {
    DdNoticeBar(error, DdNoticeTone.DANGER, actionLabel = "Retry") { retry() }
}
DdProgressBar(progress = if (downloading) percent else null)
```

Import components such as `io.github.whywood.superpi.design.DdMarkdown` directly (or import the whole package); no extra dependency declaration is needed.

```kotlin
DdPullToRefresh(isRefreshing = refreshing, onRefresh = { refreshing = true }) {
    DdMarkdown(
        text = state.markdown,
        onLinkClick = { host.ui().openUrl(it) },
        onImage = { image -> viewer = image.source },
    )
}
if (viewer != null) DdImageViewer(path = viewer, onDismiss = { viewer = null })
```

Date/time components use `java.time` (`LocalDate`/`LocalTime`); plugins using design 0.7.0 components write `minHostVersion: "1.0.6"`, using design 0.8.0 components writes `"1.0.7"` and declares `design:0.8.0` explicitly in the build script; using design 1.0.0 components writes `"1.0.8"` and declares `design:0.9.0`; using design 1.0.0 components (`DdExpandToggle`) writes `"1.3.0"` and declares `design:0.10.0`; using design 1.0.0 components (`DdRemoteImage`) writes `"1.4.0"` and declares `design:0.11.0`; using tool result renderers writes `"1.1.0"` and depends on `plugin-api 2.0.0`.

Conventions:

- Do not hardcode colors or type sizes; use only `DdType.*` and `superPiColors()`; do not use Material3's `Card`/`Button`/`OutlinedButton`/`TextButton`/`Switch`/`OutlinedTextField` directly — they look inconsistent with the app.
- The page is its own scroll container: `Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp))`; do not add a title bar (the host already has a 64dp top bar), and add 24dp where bottom spacing is needed.
- State changes must be stable: do not clear known values into loading on init; keep old values while refreshing and mark only the affected area busy, to avoid page flicker.
- Show errors inline near where they happen (`DdType.bodySmall` + `danger` color) with the last part of the CLI/API output; do not show only an exit code and do not use a Toast.

```kotlin
@Composable
private fun StatusCard(host: PluginHost, status: Status) {
    val colors = superPiColors()
    DdGroupCard {
        DdListRow(title = "Runtime", value = status.runtime)
        DdListRow(title = "Sign-in status", value = if (status.authorized) "Connected" else "Not connected")
        if (status.cliVersion != null) {
            DdListRow(title = "CLI", value = status.cliVersion)
        }
    }
    if (status.error != null) {
        Text(status.error, color = colors.danger, style = DdType.bodySmall)
    }
}
```

### Plugin UI conventions

All official plugins converge on this skeleton in the same upgrade, and third-party plugins can follow it too:

- Page skeleton: 20dp horizontal, 12dp top, **24dp bottom**, 16dp between groups; the host already has a 64dp top bar, so do not repeat the title inside the page.
- Card inner padding: `DdGroupCard` only draws the card background and does not add horizontal padding for custom content. When placing `DdStatusRow`, `DdText`, `Row` or other custom content directly, wrap it in `Column(Modifier.fillMaxWidth().padding(horizontal = DdSpacing.Card))` (18dp) first so text never touches the card edges; `DdListRow` already carries 18dp through `LocalDdRowHorizontalPadding`, so do not wrap it in horizontal padding again; `DdNoticeBar` has its own padding. The page's outermost 20dp plus 18dp inside the card is normal double padding; do not remove it.
- Open with status: build a summary from `DdStatusRow` (runtime, component/CLI, account, sign-in status) with human-readable values ("Ready / Update available / Not connected"), never version numbers, paths or booleans; keep old values on refresh and mark only that row busy.
- Action hierarchy: at most one `DdPrimaryButton` per screen; secondary actions use text buttons or menus; irreversible actions such as sign-out and delete go through `host.ui().confirm(destructive = true)`.
- Feedback: show errors nearby with `DdNoticeBar` (danger color) plus "Retry", and retry must rerun the action that just failed, not just refresh the page; success/undo can use `snackbar` or a result card, and never report a path with a Toast alone.
- Progress: operations that take more than a second or two — install, download, sync — get a `DdProgressBar` plus stage text; offer cancel where possible, and stop in a retryable state after cancel.
- Subflows: sign-in, scanning, verification codes and WebView enter and leave as subpages with a transition, and system back leaves the subpage first; validate forms in place, handle the keyboard, and allow showing/hiding passwords.
- Motion: durations and curves come from `DdMotion` and respect `LocalDdMotionEnabled`; never cause layout jumps, and never hide state changes behind animation.
- Copy: Chinese and English in pairs, with no development terms such as CLI, paths, proot or version numbers; drop implementation details that need explaining, and use `InfoTooltip` for the genuinely necessary ones.
- Icons: use the consistent linear `DdIcons`, never emoji; add no one-off controls, and when `DdGroupCard`/`DdListRow`/`DdTextAction` fit, do not draw your own.

### Autonomous capabilities

Plugin tools are the agent's autonomous path and pages are the optional path for people; never build a capability only in the UI:

- When the user explicitly asks for an action, the agent should be able to call a tool to do it rather than send the user to tap a button on a plugin page; for example a mailbox provides `mail_draft` + `mail_send`, and sending does not need another tap.
- Tool execution does not need confirmation each time by default; do not add a "wait for the user to tap" gate inside a tool, or scheduled runs, background tasks and goal rounds get stuck. For more sensitive actions, write the consequences into the description/prompt so the model judges before calling, or let the user ask for it explicitly in the conversation.
- A UI upgrade must not delete or hide tools, and must not turn an existing capability into something only the UI can do; for every page change check "can the agent still do the same thing".
- Irreversible actions still need a trace: prefer the "create the object first (draft/preview), then execute" tool pairing, so the user can see in the chat history or a card what is about to happen.

### Custom message cards

Inside a tool execution, use `host.chat().sendMessage(...)` to write a message into the current session, optionally showing only a card, interrupting, or starting a new round:

```kotlin
val result = host.chat().sendMessage(
    sessionId = call.sessionId,
    text = "Sync finished, 12 files",
    options = chatMessageOptions(
        triggerTurn = false,
        display = chatDisplay(
            customType = "my.card",
            payload = buildJsonObject { put("title", "Sync finished") },
            text = "Card summary",
        ),
    ),
)
```

- `sessionId` comes from `PluginToolCall.sessionId` (resolved by the host during execute) or `session.snapshot()?.sessionId`.
- `options.triggerTurn = true` (default): when the session is idle, start a new round with `text` as the user message; when busy, interrupt by `options.deliverAs` (`STEER`, default) or queue (`FOLLOW_UP`), returning a result with `kind = ChatSendKind.QUEUED`.
- When `options.display` is non-null, write the card message first (equivalent to `postCustomMessage`), then trigger the turn; `triggerTurn = false` only writes the card.
- Returns `ChatSendResult` with `kind` of `ChatSendKind.STARTED`/`QUEUED`/`DISPLAYED`; an unknown session throws `IllegalArgumentException`.
- The card is written as a `custom_message` entry into the pi JSONL, can be restored after reopening the session, and renders through the `chat.message:<customType>` slot; `postCustomMessage` remains for writing only a card.
- A card delivered while a tool call is running renders inline as a node of that turn in occurrence order (not stacked at the end of the answer); a card delivered while the session is idle still appears as a standalone message at a turn boundary, and the order after reopening the session matches the live order.

## Host services

`PluginHost` exposes 35 service accessors (17 in 1.0, 18 added in 1.1.0), of which `components()` / `credentials()` / `accessibility()` are experimental APIs that need an explicit opt-in:

```kotlin
@OptIn(PluginExperimentalApi::class)
val components = host.components()
```

When the host does not provide a service it **throws `PluginServiceUnavailable`** (it does not return null), so plugins should `runCatching` and degrade. When a service is wired but currently unavailable (for example Linux not installed, ADB not authorized, permission denied), it usually says so through `available`/`enabled`/`permissionGranted` fields, by returning null/false, or with an explicit exception.

```kotlin
val linux = runCatching { host.linux() }.getOrNull()
if (linux == null || !linux.available) {
    return textResult("Linux environment unavailable", isError = true)
}
```

| Accessor | Main capabilities | When unavailable or failing |
|---|---|---|
| `linux()` | `available`, `exec(command, cwd, timeoutMs)`, `stream(command)` → `LinuxResult`/`LinuxChunk` | `available=false` when the runtime is not installed or enabled; `exec` records timeouts and output truncation; stderr merges into stdout (`LinuxResult.stderr` is always empty, so check `exitCode` and the end of the output for failures) |
| `screen()` | `status`, `startVirtual/stopVirtual`, `observe`, `screenshot`, `apps`, `launch`, `tap/tapNode/swipe/scroll/type/key`, `waitFor` | without permission the capabilities are marked unavailable and calls throw; `status` is a synchronous snapshot, and the first read starts a host-side background refresh (no stop interface) |
| `notifications()` | `enabled`, `notify(...)`, `notifyChecked(...)`, `updateProgress`, `cancel`; notification listening `listenerGranted`/`recent`/`received`/`removed`/`dismiss` (see "Notifications and notification listening") | with notification permission off, `enabled=false`: `notify` is silently ignored and `notifyChecked` returns null; tapping a notification opens the first page declared in the plugin manifest |
| `secrets()` | `get/put/remove/clear`, encrypted storage namespaced by plugin id | throws `PluginServiceUnavailable` when the host does not provide it |
| `network()` | `request(method,url,headers,body,timeoutMs)`, `download(url,targetPath,expectedSha256,onProgress)`; `get`/`post` extensions | network errors throw; response bodies cap at 2 MB and downloads at 512 MB |
| `files()` | Plugin data/workspace files: `rootDir`, `workspaceDir`, `list/readText/writeText/readBytes/writeBytes/delete/exists/resolve` | relative paths resolve against the plugin `dataDir`; absolute paths resolve per the current tier (the Root tier is the native file system, see "Execution identity and environment") |
| `sessions()` | `active`, `observe()` (including busy changes), `list()`, `prompt(text, sessionId?)`, `history(sessionId, limit)`, `stop(sessionId)`, `create(title?)`, `rename(sessionId, title)`, `delete(sessionId)`, `open(sessionId)`, `archive(sessionId, archived)` | with no active session `active=null` and `prompt` returns false; invalid ids return null/false |
| `jobs()` | `observe()`, `start(command,label,cwd,sessionId)`, `read(jobId)`, `kill(jobId)` | with no session or no host support, `start` returns null; there is no `list()`, so read once with `observe().first()` |
| `timers()` | `schedule(TimerRequest)` (built with `timerRequest(...)` + `timerOnce`/`timerCron`), `list()`, `cancel(id)` | invalid parameters or no host support return null or throw |
| `goals()` | `observe()`, `get/create/edit/pause/resume/complete/clear` | returns null for invalid goal state |
| `miniapp()` | Mini apps: `scan/load/create/edit/delete/run` | throws `PluginServiceUnavailable` when the host does not provide it |
| `mcp()` | `servers()`, `probe(name)`, `tools(server)`, `call(server,tool,arguments)`, `callStructured(server,tool,arguments)` | returns an error result or throws when the server does not exist; `callStructured` gives text blocks and the structured result together |
| `ui()` | Open URLs/sharing/Toast, pages and arguments, interactive pickers, Snackbar, `isDark` (see "UI extensions") | throws `PluginServiceUnavailable("ui.xxx")` for an individual unwired item; `openPage` only accepts this plugin's page ids, not `superpi://` deep links |
| `app()` | `info: AppInfo` (versionName/versionCode/pluginApiLevel/uiApiLevel/language/androidSdkInt/deviceModel/installationId) | always available |
| `bus()` | Plugin bus: `publish(event)` (built with `pluginBusEvent(...)`), `subscribe(topic?)` → `Flow<PluginBusEvent>` | throws `PluginServiceUnavailable` when the host does not provide it |
| `logs()` | `log(level, message, error?)` + `debug/info/warn/error` | always available |
| `chat()` | `sendMessage(...)`: write cards, start a new round, interrupt/queue; `sendMessageWithAttachments(..., attachments = listOf(chatAttachment(path, mimeType, name)))`; `postCustomMessage(...)` writes only a card | unknown sessions throw; a failed delivery returns false |

Extra models for sessions and chat: `sessions().history(...)` returns `PluginMessage(id, role, text, timestampMs, toolName?, images)`; chat attachments are built with `chatAttachment(path, mimeType?, name?)` and passed to `sendMessageWithAttachments(...)`.

Minimal usage of each service:

```kotlin
host.linux().exec("ls -la", cwd = host.dataDir.absolutePath)
host.screen().status.virtual.available
host.notifications().notify(pluginNotification(id = "sync", title = "Sync", body = "Done"))
host.secrets().put("token", value)
host.network().get("https://example.com/api")
host.files().writeText("state.json", "{}")
host.sessions().prompt("Continue", sessionId)
host.jobs().start("sleep 5", "demo", cwd = null)
host.timers().schedule(timerRequest("Reminder", "Drink water", timerOnce(System.currentTimeMillis() + 60_000)))
host.goals().get(sessionId)
host.miniapp().scan()
host.mcp().servers()
host.ui().toast("Copied")
host.app().info.versionName
host.bus().publish(pluginBusEvent(topic = "demo.sync", payload = buildJsonObject { }))
host.logs().info("plugin ready")
host.chat().sendMessage(call.sessionId, "Sync finished", chatMessageOptions(triggerTurn = false, display = chatDisplay(customType = "my.card", payload = buildJsonObject { }, text = "Card summary")))

host.settings().put("last-sync", "2026-11-13T10:00:00Z")
host.device().state.batteryPercent
host.clipboard().setText("Copied")
```

### Notifications and notification listening

Build notifications with `pluginNotification` / `pluginNotificationWithActions`; `notifyChecked` returns null when notification permission is off:

```kotlin
val handle = host.notifications().notifyChecked(
    pluginNotificationWithActions(
        id = "sync",
        title = "Syncing",
        body = "Processed 3/12",
        progress = 25,
        ongoing = true,
        actions = listOf(notificationAction("pause", "Pause"), notificationAction("cancel", "Cancel")),
        payload = buildJsonObject { put("task", "sync") },
    ),
)
host.notifications().updateProgress("sync", 50, "Processed 6/12")
host.notifications().cancel("sync")
```

- `notifyChecked` returns `PluginNotificationHandle(id, key)`; it returns null when `enabled=false`, while `notify` is silently ignored.
- Action buttons are handled by the host notification service; a plugin that declares the `notifications` capability receives `notificationId`/`actionId`/`payload` in the `notificationAction` hook, and does not need the notification listener component.
- Tapping the notification body opens the plugin's first page in `provides.pages` / `ui()`.

Notification listening needs `permissions: ["notifications.listen"]` + `components.notificationListener`, granted by the user under the system "Notification access" setting:

| Member | Description |
|---|---|
| `listenerGranted` | Whether notification access has been granted |
| `recent(limit = 50)` | Recent notification records |
| `received()` | `Flow<PluginNotificationRecord>` of new or removed records |
| `removed()` | `Flow<String>` of removed notification keys |
| `dismiss(key)` | Requests removal of a notification (where the system allows it) |

`PluginNotificationRecord` fields: `key`, `packageName`, `appLabel`, `title`, `text`, `bigText`, `subText`, `channelId`, `category`, `postedAtMs`, `ongoing`, `clearable`, `progress`, `actions`. Notification content touches other apps' privacy: request only the scope you really need, and do not persist notification content unrelated to this plugin.

### Execution identity and environment

The actual execution identity of `host.linux()` and `host.files()` is decided by the tier the user picked on the System access page (Normal / Wireless debugging (ADB) / Root); plugins cannot choose or raise it:

| Tier | `linux()` | `files()` |
|---|---|---|
| Normal (LOCAL) | PRoot Alpine inside the app sandbox, same UID as the app | The plugin `dataDir` and workspace; absolute paths are still constrained by the Android sandbox |
| Wireless debugging (ADB) | Same as above; system commands go through the host `system_shell` over ADB | Same as above |
| Root | Real root chroot (uid 0); Android paths are native (`/data`, `/system`, `/sdcard`) | Native file system, absolute paths apply directly |

- Code must run in all three tiers: do not escalate by yourself and do not assume the current tier is root; treat a non-zero exit code and failed file reads/writes as ordinary failures with a readable reason.
- The Root tier may be "preparing" or "preparation failed" at startup, in which case `linux()`/`files()` throw `IllegalStateException` (the message contains `Root Linux is being prepared` / `Root Linux preparation failed`); tell the user to retry later instead of crashing.
- The manifest no longer declares `requiresPrivilege`; when you need system capabilities, declare only the matching `capabilities` (such as `screen`) and the host injects the service under the tier the user picked.
- Plugins cannot escalate: `privilege().exec(...)` only runs within the tier the user currently selected (`AUTO` picks the currently available tier, and explicitly asking for `ROOT`/`ADB` when the tier is insufficient returns a failure); do not assume any tier is always available.
- Environment descriptions in prompts are generated by the host per tier; plugin `promptSections` must not hardcode assumptions such as "root acquired".

### Settings storage

`host.settings()` is key-value storage isolated by plugin id, good for small state that must survive across processes; keep secrets in `secrets()`:

| Method | Description |
|---|---|
| `get(key)` / `put(key, value)` / `remove(key)` / `clear()` | String key-values |
| `keys()` / `snapshot()` | All keys / a snapshot of all key-values |
| `getJson(key)` / `putJson(key, value)` | JSON object read and write |
| `observe(key? = null)` | `Flow<Map<String, String>>` of settings changes; an empty `key` observes everything |

```kotlin
host.settings().putJson("state", buildJsonObject { put("lastSync", now) })
val json = host.settings().getJson("state")
host.settings().observe("state").collect { state -> render(state) }
```

Always available; do not store large objects, and do not put user credentials in it (use `secrets()`).

### Devices and clipboard

`host.device()`:

| Member | Description |
|---|---|
| `info: DeviceInfo` | Vendor/brand/model/device codename/Android version/SDK/ABI/emulator flag/language/time zone/screen size and density/refresh rate/memory and storage totals/installation id |
| `state: DeviceState` | Battery/charging/power saving/network type and connectivity/screen on/lock state/language/time zone/uptime/free memory and storage/current execution tier |
| `observe(): Flow<DeviceState>` | State change stream |
| `vibrate(durationMs = 200)` | Vibrate once |
| `openSettings(section: DeviceSettings)` | Jump to a system settings page, returns whether the jump is possible |

`DeviceSettings`: `APP` / `NOTIFICATIONS` / `ACCESSIBILITY` / `NOTIFICATION_LISTENER` / `OVERLAY` / `USAGE_STATS` / `BATTERY_OPTIMIZATION` / `INSTALL_PACKAGES` / `STORAGE` / `DEVELOPER` / `WIRELESS_DEBUGGING` / `LOCATION`.

`host.clipboard()`: `text()` / `setText(text)` / `uri()` / `setUri(uri)` / `has()` / `clear()`. Background clipboard access is restricted on Android 10+, so reads return null or are ignored by the system; do not rely on the clipboard for cross-app transfer.

```kotlin
host.device().observe().collect { state -> if (state.batteryPercent < 20) warnLowBattery() }
host.device().vibrate(80)
host.clipboard().setText("Copied")
```

### Permissions and special grants

`host.permissions()`:

| Method | Description |
|---|---|
| `granted(permission)` | Queries the grant state of a `PluginPermission` constant |
| `suspend request(vararg permissions)` | Requests runtime permissions, returns "permission → granted" |
| `special(access)` | Queries a special grant: `PluginAccessState(granted, available, reason)` |
| `openSettings(access)` | Jumps to the matching system settings page, returns whether the jump is possible |

`PluginSpecialAccess`: `NOTIFICATION_LISTENER` / `ACCESSIBILITY` / `OVERLAY` / `USAGE_STATS` / `BATTERY_OPTIMIZATION` / `INSTALL_PACKAGES` / `MANAGE_STORAGE`. The manifest declarations for runtime permissions and special grants are in "Permission declarations".

```kotlin
if (!host.permissions().granted(PluginPermission.CAMERA)) {
    host.permissions().request(PluginPermission.CAMERA)
}
val overlay = host.permissions().special(PluginSpecialAccess.OVERLAY)
if (!overlay.granted) host.permissions().openSettings(PluginSpecialAccess.OVERLAY)
```

Special grants have no permission dialog, so you can only guide the user to system settings; do not repeatedly jump to settings at startup.

### App management

`host.packages()`:

| Method | Description |
|---|---|
| `suspend list(includeSystem = false)` | Installed app list |
| `suspend get(packageName)` | Single app info, null when not found |
| `suspend install(apkPath, silent = false)` | Install an APK; `silent` needs the Root/ADB tier |
| `suspend uninstall(packageName, keepData = false)` | Uninstall an app |
| `launch(packageName)` / `openAppInfo(packageName)` | Launch an app / open its app info page |
| `suspend icon(packageName, sizePx = 256)` | Read an app icon as PNG bytes |

`PluginPackageInfo`: package name/name/version/version code/system app flag/enabled flag/install and update times; `PackageInstallResult`: `accepted`, `pendingUserAction`, `error`.

```kotlin
val apps = host.packages().list()
val result = host.packages().install(apkPath)
if (!result.accepted) host.ui().snackbar(result.error ?: "Install failed")
```

`silent = true` only works in the Root/ADB tier; the Normal tier goes through the system installer and waits for the user to confirm.

### Root / ADB execution (privilege)

`host.privilege()` provides system-level execution within the three tiers and never goes beyond the tier the user currently selected:

| Member | Description |
|---|---|
| `access: HostAccessLevel` | The currently effective tier: `LOCAL` / `ADB` / `ROOT` |
| `root: RootState` / `adb: AdbState` | Whether root is granted, whether ADB is paired/connected, and the error reason |
| `suspend probeRoot()` | Actively probes the Root state |
| `suspend exec(command, level = AUTO, timeoutMs = 30_000)` | Runs a command, returns `exitCode`/`stdout`/`stderr`/`timedOut`/`truncated` |
| `suspend install(apkPath, silent = true)` / `uninstall(packageName)` | Silent install/uninstall (needs the matching tier) |
| `openAccessSettings()` / `openWirelessDebugging()` | Jump to System access / wireless debugging settings |
| `suspend startAdbPairing()` / `submitAdbPairingCode(code)` / `connectAdb()` | Wireless debugging pairing and connection |

`PrivilegeLevel`: `AUTO` / `ROOT` / `ADB`. `AUTO` picks the currently available tier; explicitly asking for a higher tier than currently available returns a failure and never escalates automatically.

```kotlin
val root = host.privilege().root
if (root.granted) {
    val result = host.privilege().exec("id", level = PrivilegeLevel.ROOT)
    host.logs().info("exit=${result.exitCode} out=${result.stdout.trim()}")
}
```

Check `exitCode`/`stderr` for command failures instead of string-matching the output; ADB pairing needs the user to turn on wireless debugging in the system and confirm the pairing code.

### Intents and dynamic broadcasts

`host.intents()`:

| Method | Description |
|---|---|
| `startActivity(intent)` / `sendBroadcast(intent)` | Start an Activity / send a broadcast |
| `suspend startActivityForResult(intent, timeoutMs = 120_000)` | Waits for a result, returns `PluginActivityResult(resultCode, data, cancelled, timedOut)` |
| `startService(intent)` / `stopService(intent)` | Start/stop a service (subject to system background limits) |
| `canResolve(intent)` / `suspend resolve(intent)` / `suspend queryActivities(intent)` | Resolve the target `ResolvedIntent(packageName, className, label, exported)` |

Dynamic broadcasts use `host.components().registerReceiver(filter)` (experimental API):

```kotlin
@OptIn(PluginExperimentalApi::class)
val handle = host.components().registerReceiver(IntentFilter(Intent.ACTION_BATTERY_CHANGED))
host.scope.launch { handle.events().collect { intent -> onBattery(intent) } }
// onDisable()/close(): handle.close()
```

- `PluginReceiverHandle`: `id`, `events(): Flow<Intent>`, `close()`; the plugin must `close()` before it is disabled or exits.
- Android 10+ restricts starting Activities/Services from the background; call them from a page or a foreground flow, and failures return false.
- Resolve and start only Intents that are visible to the user and relevant to the plugin's function; do not scan unrelated apps.

### Dedicated windows and foreground services (experimental)

Plugins cannot register Activity/Service themselves; when they need a dedicated window or a long-running service, use the host proxy:

```kotlin
@OptIn(PluginExperimentalApi::class)
host.components().startActivity(
    pluginActivityOptions(pageId = "scanner", arguments = buildJsonObject { }, newTask = true),
)
```

- `pluginActivityOptions(pageId, arguments, newTask)` lets the host open this plugin's page in `PluginHostActivity`; the page is still the `PluginPage` declared in `ui()`, and `pageContext()` can read the passed arguments.
- Foreground services: `host.background().startForeground(foregroundServiceOptions(id, title, text, ongoing, progress))`, `updateForeground(...)`, `stopForeground(id)`; needs `components.foregroundService: true` + the `background` capability.
- Proxy components serve plugin pages and foreground tasks only: you cannot customize the Activity layout, replace the host theme or register manifest components.

### Background tasks and boot callbacks

`host.background()`:

| Method | Description |
|---|---|
| `suspend schedule(id, kind, triggerAtMs?, intervalMs?, requiresNetwork = false, requiresCharging = false)` | Registers a task, returns whether it succeeded |
| `suspend cancel(id)` / `suspend tasks()` | Cancel / list tasks |
| `startForeground` / `updateForeground` / `stopForeground` | Foreground services |
| `batteryOptimizationIgnored` / `requestIgnoreBatteryOptimizations()` / `openBatteryOptimizationSettings()` | Battery optimization state and guidance |

`PluginBackgroundKind`:

| Value | Meaning |
|---|---|
| `ONCE` | One-shot trigger (trigger time or delay) |
| `PERIODIC` | Periodic task (WorkManager, system minimum interval about 15 minutes) |
| `EXACT` | Exact timing (AlarmManager, subject to system power saving and the exact alarm permission) |
| `BOOT` | Runs once after boot, also used to re-register periodic tasks after a restart |

```kotlin
host.background().schedule(
    id = "digest",
    kind = PluginBackgroundKind.PERIODIC,
    intervalMs = 6 * 60 * 60 * 1000,
    requiresNetwork = true,
)
```

When a task fires it dispatches the `backgroundTask` hook (`taskId`/`kind`/`firedAtMs`), requiring the `background` capability and `components.background: true`. Doze and power saving delay tasks; for reliable execution guide the user to ignore battery optimizations.

### Location

`host.location()`: `available`, `permissionGranted`, `lastKnown(): PluginLocation?`, `current(timeoutMs = 10_000): PluginLocation?`, `updates(minIntervalMs = 1_000, minDistanceM = 0f): Flow<PluginLocation>`, `geocode(query, limit = 5): List<PluginGeocode>`, `reverseGeocode(location): PluginGeocode?`.

`PluginLocation`: latitude/longitude/accuracy/source/time, with optional altitude/speed/bearing; `PluginGeocode`: lat/lon/name/country/administrative area/city. Needs the `location` capability + `location.coarse`/`location.fine` in `permissions`; unauthorized calls return null or an empty list.

```kotlin
val here = host.location().current() ?: return textResult("Location unavailable", isError = true)
val place = host.location().reverseGeocode(here)
```

### Contacts

`host.contacts()`: `permissionGranted`, `suspend list(limit = 0)`, `suspend search(query, limit = 20)`, `suspend get(id)`, `suspend create(draft)`, `suspend update(id, draft)`, `suspend delete(id)`; drafts are built with `pluginContactDraft(displayName, phones, emails, organization)`.

`PluginContact`: id/display name/phones/emails/organization/note/starred; `PluginContactField`: value/label/type. Needs the `contacts` capability + `contacts.read`/`contacts.write` in `permissions`; unauthorized calls throw `SecurityException`.

```kotlin
val contacts = host.contacts().search("Ann")
val created = host.contacts().create(pluginContactDraft(displayName = "Ann Lee", phones = listOf("13800000000")))
```

### SMS

`host.sms()`: `available`, `canRead`, `canSend`, `suspend threads(limit = 20)`, `suspend messages(threadId?, address?, limit = 50)`, `suspend send(address, text, subscriptionId?)`, `suspend markRead(threadId)`, `suspend delete(threadId)`.

`PluginSmsThread`: thread id/number/contact name/summary/time/unread count; `PluginSmsMessage`: id/thread id/number/body/time/received/read. Needs the `sms` capability + `sms.read`/`sms.send` in `permissions`.

- Sending is sensitive: confirm the content and number in the page first, and on failure return false with the reason (the default SMS app or carrier restrictions may block sending).
- Do not bulk-read or delete the user's messages; handle only the threads the user explicitly names.

```kotlin
val thread = host.sms().threads(limit = 5).firstOrNull() ?: return
host.sms().messages(threadId = thread.id, limit = 20)
if (host.sms().canSend) host.sms().send(thread.address, "Got it, I will reply later")
```

### Calendar

`host.calendar()`: `permissionGranted`, `suspend calendars()`, `suspend events(fromMs, toMs, calendarIds = emptyList(), limit = 200)`, `suspend get(eventId)`, `suspend create(draft)`, `suspend update(eventId, draft)`, `suspend delete(eventId)`; drafts are built with `pluginCalendarEventDraft(title, startMs, endMs, allDay = false, description?, location?, calendarId?, timeZone?, reminders = emptyList())`.

`PluginCalendar`: id/name/account/display name/color/visible/default; `PluginCalendarEvent`: id/calendar id/title/start and end/all-day/description/location/time zone/reminders. Needs the `calendar` capability + `calendar.read`/`calendar.write` in `permissions`.

```kotlin
val draft = pluginCalendarEventDraft(
    title = "Weekly sync",
    startMs = start,
    endMs = start + 3_600_000,
    reminders = listOf(10),
)
host.calendar().create(draft)
```

### Media

`host.media()`:

| Method | Description |
|---|---|
| `saveToDownloads(fileName, mimeType, bytes)` | Writes bytes into Downloads, returns the path |
| `saveFileToDownloads(path, fileName?, mimeType?)` | Copies an existing file into Downloads |
| `saveImageToGallery(fileName, mimeType, bytes)` | Writes an image into the system gallery |
| `scanFile(path)` | Triggers a media scan |
| `open(path)` / `share(path, mimeType?)` / `preview(path)` | Open/share/preview a file |
| `readImage(path, maxSizePx = 2_048)` / `thumbnail(path, maxSizePx = 512)` | Image decoding (with a size limit) |
| `importUri(uri, fileName?)` | Imports an external URI into the plugin directory, returns the local path |

Needs the `media` capability; whether extra grants are needed depends on the host implementation and the system version, and declaring `manage-storage` in `permissions` is required to access shared storage directly (the user enables "All files access" in system settings).

```kotlin
val saved = host.media().saveToDownloads("report.csv", "text/csv", bytes)
host.media().share(saved!!, "text/csv")
```

### Speech

`host.speech()`: `speakAvailable`, `recognizeAvailable`, `suspend speak(text)`, `stopSpeaking()`, `speaking(): Flow<Boolean>`, `suspend recognize(timeoutMs = 30_000): PluginSpeechResult(text, error)`, `openVoiceSettings()`.

Needs the `speech` capability; speech recognition also needs `microphone` in `permissions` (capability `ui`). With no TTS engine or recognition service, the matching `available=false` and calls return false or an error result.

```kotlin
if (host.speech().speakAvailable) host.speech().speak("Sync finished")
val heard = host.speech().recognize()
```

### Web

`host.web()`: `available`, `suspend search(query, limit = 8): List<PluginWebResult(title, url, snippet?)>`, `suspend fetch(url, maxChars = 20_000): PluginWebPage(url, title?, text, truncated)`.

Needs the `web` capability; search and fetch use the host's web service and parsing, and failures return an empty list or null. When you already use `network()` to request things yourself, prefer your own implementation (controllable timeouts and parsing).

```kotlin
val results = host.web().search("Android notification listener")
val page = results.firstOrNull()?.let { host.web().fetch(it.url) }
```

### Host credentials (experimental)

`host.credentials()` (`@PluginExperimentalApi`) reads credentials already configured in the host, for plugins to reuse with the user's knowledge:

| Method | Description |
|---|---|
| `providers(): List<PluginProviderCredential>` / `provider(id)` | Provider configuration: id/name/baseUrl/apiKey/authKind |
| `mcpTokens(): List<PluginMcpToken>` / `mcpToken(server)` | MCP tokens: server/url/accessToken/refreshToken/tokenType/expiresAtMs |
| `hostValues(): Map<String, String>` | Generic host credential key-values |

**Sensitivity**: these are the user's real credentials. Read them only while performing a user request, never persist them, never log them and never send them to third parties; the install confirmation page and the plugin capability list show this capability so users know the plugin reads host credentials.

```kotlin
@OptIn(PluginExperimentalApi::class)
val provider = host.credentials().providers().firstOrNull()
val mcp = host.credentials().mcpTokens().firstOrNull()
```

### Accessibility (experimental)

`host.accessibility()` (`@PluginExperimentalApi`):

| Member | Description |
|---|---|
| `enabled` | Whether the accessibility service is on |
| `events(): Flow<PluginAccessibilityEvent>` | Accessibility event stream: eventType/packageName/className/text/contentDescription/timeMs |
| `performGlobalAction(action)` | Global actions: `BACK`/`HOME`/`RECENTS`/`NOTIFICATIONS`/`QUICK_SETTINGS`/`POWER_DIALOG` |

Needs the `accessibility` capability + `permissions: ["accessibility"]` + `components.accessibility: true`, and the user must enable PI's service in the system accessibility settings. Read only the events the feature needs, and do not collect or send other apps' text.

```kotlin
@OptIn(PluginExperimentalApi::class)
if (host.accessibility().enabled) {
    host.accessibility().events().collect { event -> onAccessibilityEvent(event) }
    host.accessibility().performGlobalAction(PluginGlobalAction.BACK)
}
```

**Bus**: `host.bus().publish(pluginBusEvent(topic, payload))` broadcasts to all plugins (the host overrides `publisher` with the plugin id); `subscribe("topic")` returns a `Flow`, and the `PluginHooks.busEvent(...)` observation callback receives it too. Use events only for cross-plugin coordination and lightweight notifications, never for large payloads or synchronous RPC.

## Pages and state

Plugin pages are the plugin's only interaction surface, and where the plugin keeps its own state:

- Pages are declared by `PluginUiContributions.pages`, and the `content` of `pluginPage(...)` is the page content (the host renders it directly, without slots); tapping a whole row in the list enters the first page, and pages switch with `host.ui().openPage(...)` / `openPageWithArguments(...)` (only the plugin's own pages can be opened).
- For a dedicated window or a long flow, use `openPageWithArguments(..., newTask = true)` or `components().startActivity(pluginActivityOptions(...))`: the host renders the same page inside `PluginHostActivity`, the page reads arguments from `pageContext()` / `LocalPluginPageContext`, and `closePage(result)` returns a JSON result when closing.
- Plugin data goes into `host.dataDir` and `host.settings()` (relative paths in `host.files()` root at `dataDir`), and secrets go into `host.secrets()`; do not cache state that must survive the process in a page.
- After an action in a page that affects tools or skills (install, sign-in, switches), call `host.requestContributionsRefresh()`: the host materializes again and triggers a session rebuild, and contributions apply at a safe point. For example, after installing a CLI in a page and returning to the tool list, the next session sees the new tools.
- Pages can call every declared host service; prefer `host.ui()` confirm/input/pickers (see "Interaction and pickers") over rolling your own Material dialogs, which look inconsistent with the app.
- `PluginPage.content` does not receive a `SlotState`; when you need the active session call `host.sessions().active`, and when you need page arguments read `pageContext()`.
- `host.requestContributionsRefresh()` is asynchronous and does not wait for completion; when the UI must confirm the result, use the plugin's own state marker and do not assume materialization has happened when it returns.
- After leaving a page the host cancels its coroutines such as `LaunchedEffect`; long tasks go on `host.scope`, never relying on the page lifecycle to stay alive.
- `network().get`/`post` and `PluginAvailability.isReady` are extension functions; with explicit imports you must import `get`/`post`/`isReady` separately (or import the whole `io.github.whywood.superpi.plugin.api.*` package).

## Compatibility and migration

### 1.2.x → 1.3.0 (purely additive)

`plugin-api 2.0.0` does not raise the apiLevel: existing plugins need no code change or rebuild. To render cards on MCP/plugin tool results: raise the dependency to `plugin-api 2.0.0` and write `minHostVersion: "1.1.0"` in the manifest; then also implement `ToolResultRendererContributor` on the object returned from `ui()`, matching with `toolResultMatch(source/server/tool/toolPrefix)` and constructing with `toolResultRenderer`. When the agent calls an MCP tool through `tool_execute`, the host restores the inner tool identity automatically, so renderers match on the real MCP server/tool.

`plugin-api 2.0.0` / `design 1.0.0` do not raise the apiLevel either: new `PluginHost.images()` (returning `RemoteImage`) and `DdRemoteImage`; add `images` to the `plugin.json` capabilities and a plugin can load remote images in cards. Both are additive only, and old plugins need no rebuild.

### 1.1.x → 1.2.0 (purely additive)

`plugin-api 2.0.0` does not raise the apiLevel: existing plugins need no code change or rebuild. To use the new capabilities (theme packs, `openPageSheet`, page presentation mode, design 1.0.0 components): raise the dependencies to `plugin-api 2.0.0`/`design 1.0.0` and write `minHostVersion: "1.0.8"`; theme packs declare ids in `provides.themes` and add `assets/themes.json`.

### 1.0.x → 1.1.0 (purely additive)

`plugin-api 1.1.0` does not raise the apiLevel: published 1.0.x plugins keep running on host 1.0.4+ without code changes or a rebuild. Migration is needed only to use the new 1.1.0 capabilities (which need host 1.0.6+):

1. Raise the dependency to `1.1.0` (with `design 0.7.0`, carried through `plugin-api`'s `api()`, so plugin projects still declare only `plugin-api`).
2. Raise `minHostVersion` to `"1.0.6"`; plugins that only run on older hosts and do not call the new 1.1.0 APIs can keep their previous threshold (such as `"1.0.4"`).
3. Declare the new capabilities in `capabilities`; runtime permissions go in `permissions` and system components in `components`, declared in pairs with the matching capability (see "Permission declarations" and "Component declarations").
4. When using experimental APIs (`components()` / `credentials()` / `accessibility()`, `PromptOverridePlugin`), add `@OptIn(PluginExperimentalApi::class)`.
5. Wire page arguments, interactive pickers, notification actions and system events per the new sections; import design 0.7.0 components directly from `io.github.whywood.superpi.design`.
6. Bump the plugin version, sign again and update the marketplace entry; the runtime threshold is enforced by `minHostVersion` and the host's actual capabilities.

### Migrating from API level 5 to level 6

`plugin-api 1.0.0` is a breaking upgrade: since host 1.0.4 only `apiLevel = 6` is accepted, and level 5 plugins are rejected at install with an update prompt. Migrate the code using the mapping below, then rebuild and raise the manifest (`apiLevel: 6`, `minHostVersion: "1.0.4"`, `ui.apiLevel: 2`); sign again with the same publisher key so installed users can upgrade.

| Level 5 (`0.8.1`) form | Level 6 (`1.0.0`) form |
|---|---|
| `Contributions(...)` | `contributions(...)` |
| `PluginTool(...)` | `pluginTool(...)` (the constructor is no longer public) |
| `PluginToolResult.Text(...)` / `.Rich(...)` | `textResult(...)` / `richResult(...)` |
| `ToolBlock.Text` / `.Image` / `.FileRef` | `textBlock(...)` / `imageBlock(...)` / `fileRefBlock(...)` |
| `PluginSkill(...)` / `PluginSkillFile(...)` | `pluginSkill(...)` / `pluginSkillFile(...)` |
| `PromptSection(...)` | `promptSection(...)` |
| `LocalizedText.Literal(...)` (or `literal(...)`) | `localizedText(...)` |
| `IconRef.Asset(...)` | `assetIcon(...)` |
| `SlotBinding(...)` / `SlotContent.Compose { }` | `slotBinding(...)` / `composeSlot { }` |
| `PluginPage(...)` / `ComposerAction(...)` | `pluginPage(...)` / `composerAction(...)` |
| `PluginNotification(...)` | `pluginNotification(...)` |
| `TimerRequest(...)` + `TimerTrigger.Once/Cron` | `timerRequest(...)` + `timerOnce(...)` / `timerCron(...)` |
| `PluginBusEvent(...)` | `pluginBusEvent(...)` |
| `ChatMessageOptions(...)` / `ChatDisplay(...)` | `chatMessageOptions(...)` / `chatDisplay(...)` |
| Constructing `PluginAvailability` directly | `available()` / `unavailable(reason)` |
| `PluginEvent.X` (sealed subclass) | Top-level interface `XEvent` (e.g. `SessionStartedEvent`) |
| `InputDecision.Continue/Handled` | `inputContinue(...)` / `inputHandled()` |
| `ToolCallDecision.Continue/Block/Rewrite` | `toolContinue()` / `toolBlock(reason)` / `toolRewrite(args)` |
| `ToolResultDecision.Keep/Replace` | `resultKeep()` / `resultReplace(result)` |
| `PluginCapability` enum members | Value class constants (wire names unchanged, `plugin.json` needs no change) |
| `MessageRole` / `ToolPhase` and other enums | Value class constants (`USER` / `STARTED` and other names unchanged) |
| Objects implementing `PluginUiContributions` | Interface unchanged; build `bindings` / `pages` with the factories above |
| `host.text(source, *args)` | Unchanged (`vararg Any` kept, pass only strings/numbers) |

Manifest and project:

- `apiLevel: 6`, `minHostVersion: "1.0.4"`, `ui.apiLevel: 2`; `entryClass` and the entry class's no-arg constructor are unchanged.
- `capabilities` may add `prompt` (prompt override); all other wire names are unchanged.
- Dependencies become `compileOnly("io.github.sadami3066.superpi:plugin-api:<version>")` (`<version>` from the latest `SuperPI-Maven` release), rebuilt against the host Kotlin/Compose versions; any change to `.ddp` contents requires a new signature.
- Manifest parsing, validation and version comparison moved to the host (host repository `app/plugin/host/external/`), and the SDK no longer has `PluginManifestCodec` / `PluginValidation` / `PluginVersion`; `PluginManifest` / `Provides` / `UiManifest` / `PublisherInfo` are host-constructed read-only interfaces that plugins do not construct.
- The compatibility promise applies from 1.0.0: within level 6 only binary-compatible additions; deleting or changing public APIs requires a new `apiLevel` (see `plugin-api/COMPATIBILITY.md`).

## ABI and versions

- Currently `plugin-api 1.1.0` / API level 6: `PLUGIN_API_VERSION = "1.1.0"`, `PLUGIN_API_LEVEL = 7`, `BASELINE_API_LEVEL = 7`, `MIN_SUPPORTED_API_LEVEL = 7`. At load the host requires `plugin.apiLevel` to be within `MIN_SUPPORTED_API_LEVEL..PLUGIN_API_LEVEL` (currently 6..6); an omitted `apiLevel` is treated as `BASELINE_API_LEVEL` (6). Since 1.0.4 the host rejects level 5 plugins and asks for a rebuild with the new SDK.
- Compatibility matrix:

| apiLevel | plugin-api / design | Host | Notes |
|---|---|---|---|
| 5 | 0.8.1 / 0.6.1 | ≤1.0.3 | Historical contract, rejected from 1.0.4 |
| 6 | 1.0.x / 0.6.1 | 1.0.4+ | Factory construction, event interfaces, prompt override experimental capability |
| 6 | 1.1.x / 0.7.x | 1.0.6+ | Additive: 38 capabilities, 18 new service accessors, permissions/components, notification listening and system events, page arguments and interactive pickers, design 0.7.0 components |

- **Compatibility contract**: within one `apiLevel` only binary-compatible additions (new optional interfaces, methods with default implementations, value class constants, factory functions, optional manifest fields); deleting, renaming, changing signatures or changing runtime semantics requires a new `apiLevel`. Full rules in `plugin-api/COMPATIBILITY.md`.
- `minHostVersion` compares only `major.minor.patch` and ignores suffixes (`1.0.4-dev` counts as `1.0.4`), so a development build is not blocked by its own threshold; the host version comes from the app's `BuildConfig.VERSION_NAME` and is currently 1.0.6+; thresholds for older capabilities (such as `1.0.4`) stay valid.
- Gate: `./scripts/api-compat.sh` exports the public ABI of `plugin-api` and `design` and compares it with the `plugin-api/compat/`, `design/compat/` baselines, failing on removals or signature changes; `--update` is only for a level bump or the first baseline. A breaking change requires bumping `apiLevel`, updating the baseline, and writing `plugin-api/CHANGELOG.md` plus the host release notes.
- Experimental APIs are marked `@PluginExperimentalApi` (`@RequiresOptIn`) and plugins must opt in explicitly; adjustments are allowed while experimental, and graduation only drops the annotation without changing signatures. Current experimental capabilities: `PromptOverridePlugin`, `components()`, `credentials()`, `accessibility()`.
- `design` is protected by the ABI gate together with `plugin-api`; plugins using design 0.7.0 components write `minHostVersion` according to the host version (1.0.6+).
- Plugins must depend on host libraries with `compileOnly`; the host being parent-first guarantees `plugin-api` and Compose exist only in the host.
- Compose makes no cross-version binary ABI promise: plugins must be rebuilt against the host Kotlin/Compose versions; a version mismatch only disables that plugin's UI, never rejects the plugin.

## Security model

- **Same process, same UID, no sandbox**: plugin code has the same permissions as app code and can read and write the app's private data, access system capabilities the host has been granted, reflect into host internals, start threads and kill the process. There is no trustworthy privilege narrowing within a process; signatures, `capabilities`, `permissions` and `components` are declarations, display and gating only, not a sandbox.
- **Installing means trusting**: the first-install confirmation shows the publisher, capabilities, permissions and component declarations, and states explicitly that "the plugin has all app permissions"; signatures only prove whether the package was tampered with and whether it is the same publisher, not whether the author is trustworthy.
- **Permissions and special grants**: the host requests runtime permissions uniformly; special grants such as notification access, accessibility, overlay, usage access, install unknown apps and all-files access must be enabled by the user in system settings, and a plugin can only guide the jump, never gain them silently.
- **Credentials (experimental)**: `credentials()` can read the host's real provider and MCP credentials; read them only while performing a user request, and never persist, log or send them. The capability page and the install confirmation show this capability.
- **Intended sources**: trusted first-party or personal plugin packages; use MCP for third-party tool sources, not `.ddp`.
- **Integrity**: the install record pins the whole-package sha256 and re-checks it before every load; the canonical digest verifies the signature against tampering; updates must use the same `keyId`; a failure keeps the last usable version.
- **What the host does not expose**: host internal encryption keys, the ADB pairing private key, pi SDK internal types. Plugins get the `PluginHost` contract and `host.context`; `host.context` is still the app's Context, and the risk of calling Android APIs directly is the plugin's to bear.
- **Native networking**: use `host.network()` for HTTP; unconventional protocols (IMAP/SMTP, WebSocket and so on) may connect directly through the JVM standard library, but must declare `network` in the manifest, keep TLS certificate validation and respect the system cleartext HTTP policy; never block the main thread in a page or tool.
- **Plugin author obligations**: do not read user data unrelated to this plugin; do not write credentials to logs; make actions that delete or overwrite user data reversible or confirm them explicitly in the page; do not end the process with `System.exit`/`Runtime.halt`.
- **Crashes**: tool/callback exceptions are isolated by the host into error/failure records; but a plugin that crashes the process on the main thread, in a Compose slot or during tool execution still takes the app down (there is no process-level isolation).

## Debugging and common errors

Build/package/sign (the template project is `template/` in SuperPI-Plugins, artifacts in `template/build/plugin/plugin-template.ddp`):

```bash
cd template && ./gradlew assemblePlugin signPlugin verifyPlugin

# Official plugin repository (SuperPI-Official-Plugins root)
./scripts/check.sh        # build, sign and verify all five official plugins
```

Troubleshooting paths:

- **Pre-install self-check** (`Signature OK` from `verifyPlugin` only proves the signature matches the digest and **does not mean it can be installed**; check each item before importing):
  - `id` matches the reverse-domain regex (at least two segments, e.g. `com.example.myplugin`);
  - `version` is `major.minor.patch[suffix]`; `apiLevel` is 6 (≤ the host `PLUGIN_API_LEVEL`); `minHostVersion` is not higher than the host version (write `1.0.6` for 1.1.0 capabilities, keep `1.0.4` when using only older ones);
  - `permissions`/`components` match actual use and pair with `capabilities` (validated at install);
  - the `entryClass` class name is valid, the class is in one of the root `classes*.dex` files and has a no-arg constructor;
  - `provides` matches the actual contributions (tools/skills/uiSlots/pages), and tool names do not conflict with host reserved names;
  - `provides.themes` matches the ids in `assets/themes.json` exactly, and theme fields/colors/contrast/size pass validation;
  - slot ids pass `SlotIds.matches` (`tool.card.content:<tool>`, `chat.message:<customType>`), and plugin page ids pass `pages` validation;
  - `assets/plugin.json` is at the right path in the package and there is at least one root `classes*.dex`.
- **Install rejected**: the confirmation page or a toast gives the reason; common ones are manifest validation codes (`INVALID_ID`/`INVALID_VERSION`/`INVALID_PROVIDES` and so on), signature errors (tampered package, public key not matching keyId, missing signature file), publisher mismatch (key changed on upgrade), tool name conflict (`provides.tools` conflicting with the host or an installed plugin), version conflict (not newer than the installed version), theme pack errors (`THEMES_INVALID`: schema/token/color/contrast/size/id mismatch).
- **Install rejected (permissions/components)**: `INVALID_PERMISSION` for unknown or duplicate permission values; `PERMISSION_WITHOUT_CAPABILITY` when a permission has no matching capability; `COMPONENT_WITHOUT_CAPABILITY` when `components` field types or structure are invalid; `COMPONENT_WITHOUT_CAPABILITY` when a component is enabled but its capability is missing; `INVALID_DEEPLINK` for malformed or duplicate deep link paths.
- **Calling a service throws `PluginServiceUnavailable`**: the manifest `capabilities` does not declare that service accessor; capabilities take effect at install time, cannot be added while running, and need a reinstall after adding.
- **Permissions or special grants not granted**: request runtime permissions with `permissions().request(...)`; special grants such as notification access, accessibility and overlay can only be guided with `permissions().openSettings(...)` for the user to enable in system settings, and only then does `special(...).granted` become true.
- **No notification listening data**: needs `permissions: ["notifications.listen"]`, `components.notificationListener: true` and the `notifications` capability, granted under the system "Notification access"; with `listenerGranted=false`, `recent`/`received` are empty.
- **Share/deep link/background task not firing**: check the capability, the `components` switch and `deeplinks` path matching (equal, or starting with `declared/`); system events go only to global hooks.
- **Background tasks delayed or not running**: WorkManager periodic tasks are affected by Doze and power saving (minimum about 15 minutes); for reliable execution request a battery optimization exemption and guide the user.
- **Experimental API compile error**: `components()`/`credentials()`/`accessibility()` and `PromptOverridePlugin` need `@OptIn(PluginExperimentalApi::class)`.
- **Page arguments unavailable**: `pageContext()`/`LocalPluginPageContext` are only valid while a plugin page renders, so read them inside the page content.
- **Compile errors with old APIs**: `Unresolved reference` on old symbols such as `PluginTool(` / `PluginToolResult.Text` / `PluginEvent.SessionStarted` / `literal(` / `IconRef.Asset` → replace factories and interfaces item by item per "Migrating from API level 5 to level 6".
- **Level 5 plugin rejected**: since 1.0.4 the host accepts only `apiLevel = 6`; old packages must be rebuilt with `plugin-api 1.0.0`, raise `apiLevel` and `minHostVersion`, and be signed again.
- **Load failure**: `ClassNotFoundException` → wrong `entryClass` or minified away; `NoSuchMethodException` → missing no-arg constructor; `NoClassDefFoundError` → host version mismatch or host dependencies packed into the bundle.
- **Behavior differs**: `host.logs()` goes to the host log (logcat tag `SuperPIPlugin/<pluginId>`); exceptions thrown by plugins are isolated by the host and shown in the plugin row error.
- **Tools/skills unchanged after refresh**: after changing state in a page, call `host.requestContributionsRefresh()`; a busy session is rebuilt only after the current round ends, and the current round does not change midway.
- **File rows not showing**: `fileRefBlock(...)` uses the path exactly as the plugin gives it, absolute; file rows are restored by the host from the reserved details key `superpi.fileRefs`, so do not write that key yourself; if `afterToolExecution` swaps the result with `resultReplace(...)` and drops the details, the file rows disappear with it.
- **Hooks not effective / events still arriving after disable**: hooks apply whether returned from `contributions()` or `contributionsFor()`; when only `contributionsFor` is written and it returns the default `PluginHooks.None`, the global hooks are used; session contributions are materialized once per session, and plugin enable/disable or refresh rebuilds the session at a safe point (after the current round) before materializing again; the current run dispatches by the frozen set, so a disabled or uninstalled plugin still receives the run's remaining events, which is expected.
- **Skills not appearing**: the plugin must be enabled and `availability()` must be `Available` (skills are not enabled when unavailable); SKILL.md must have frontmatter, `PluginSkill.id` must match `name`, and `files` paths must be relative and safe.
- **Slots/pages not showing**: the slot id must pass `SlotIds.matches`; `provides.uiSlots` must match the actual bindings (at runtime `ui()` is authoritative); the page must appear in `PluginUiContributions.pages`; check the manifest `ui` fields (`ui.apiLevel`/`ui.kotlin`/`ui.compose`) against the host versions; when incompatible the whole UI block is disabled, falling back to host default views or an unavailable notice, and `host.logs()` records the reason.
- **Update failed**: confirm the signature `keyId` matches the first install and the whole-package sha256 matches the record; a changed key can only be installed as a new publisher.

## Limits (explicitly out of scope for now)

- No third-party plugin store, review or sandbox: same-process plugins have no isolation, so trust comes only from trusted sources + install confirmation + signature/TOFU.
- Plugins cannot register Activity/Service/Receiver/permissions in the manifest: platform components come from the host static manifest; for a dedicated window use the `PluginHostActivity` proxy, for foreground services `PluginHostService`, for broadcasts a dynamic receiver, and for notification listening/accessibility/share/deep links the host proxy components (see "Component declarations").
- Plugins cannot use `res/` resource ids; copy, icons and skills all go through the manifest and assets.
- No declarative UI nodes are provided; UI extensions are free Compose only (the two slot families for tool cards/custom messages, full `PluginPage` content, composer actions).
- Native libraries are not loaded (`lib/**` only enters the digest and unpacking).
- No process-level isolation is promised for plugin crashes; `close()` and unfinished callbacks are not guaranteed to run when the system force-kills the process.
- Plugins cannot escalate privileges or change the execution tier the user selected; Root/ADB are available only within the tier the user has authorized.
- System limits still apply: background Activity starts on Android 10+, Doze/power saving delays for background tasks, the exact alarm permission, and system authorization and default app policies for notifications/SMS/phone.
- Known contract limits: `notifications().notify` has no receipt (use `notifyChecked` when you need one); `requestContributionsRefresh()` returns no handle; background refresh of `screen().status` cannot be stopped by a plugin; `jobs()` has no `list()`. Use the contract as it is; the interface is deliberately not expanded.

## Reference

- Contract source: `plugin-api/src/main/kotlin/io/github/whywood/superpi/plugin/api/` in the host repository; compatibility contract and changelog: `plugin-api/COMPATIBILITY.md`, `plugin-api/CHANGELOG.md`; ABI gate: `./scripts/api-compat.sh`
- Design system: `design/` in the host repository (design 0.7.0 components and usage, carried to plugins through `plugin-api`)
- Plugin projects: [SuperPI-Official-Plugins](https://github.com/Sadami3066/SuperPI-Official-Plugins) (official implementations) and [SuperPI-Plugins](https://github.com/Sadami3066/SuperPI-Plugins) (template and development material)
- Product capabilities: [USER_GUIDE.md](USER_GUIDE.md); builds and modules: [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)
