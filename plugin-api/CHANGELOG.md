# Plugin API changelog

## 1.4.0 / API level 6

Additive release, `PLUGIN_API_LEVEL` stays 6: interfaces only grow, published plugins need no rebuild; plugins that use the new capabilities need `minHostVersion >= 1.4.3`.

- Card delivery moves to the plugin side: new `Cards` object (`KEY`/`RAW_KEY`/`GUIDANCE_KEY`/`DISPLAY_KEY`, `descriptor(...)`, `instruction(callId, title)` and `deliver(callId, result)`). The `PluginTool` factory automatically assembles `superpi.card`, `superpi.raw` and `superpi.guidance` from the result details into model-visible text (full return, usage instructions, the `present` call instruction) and writes the display text into `superpi.display`; the host no longer appends card ids or any card copy.
- `Cards.deliver(callId, result)`: the plugin writes the card descriptor, full return and usage instructions into the result details, and the SDK generates the model text (including the `present` instruction) and the display text (`superpi.display`); the `PluginTool` factory calls it automatically, repeated calls are idempotent, and `richResult(blocks)` is supported too.
- Cards serve plugin tools only: plugins that run a CLI through bash produce no cards and keep text results; the host does no card handling for bash results.
- Compatibility: `design` stays 0.11.0; everything added is additive, so existing plugin binaries are unaffected.

## 1.3.0 / API level 6

Additive release, `PLUGIN_API_LEVEL` stays 6: interfaces only grow, published plugins need no rebuild; plugins that use the new capabilities need `minHostVersion >= 1.1.0`.

- Tool result renderers: new `ToolResultSource` (value class: `PLUGIN`/`MCP`/`BUILTIN`), `ToolResultMatch` + `toolResultMatch(...)`, `ToolResultRenderer` + `toolResultRenderer(...)`, `ToolResultState`, and the optional `ToolResultRendererContributor`.
- `PluginUiContributions` is unchanged; a plugin contributes renderers by also implementing `ToolResultRendererContributor` on the object returned from `ui()`, and old plugin binaries are unaffected.
- Plugins can provide Compose rendering for any tool result, including MCP tools the agent calls through `tool_execute`, no longer limited to their own tool names; `ToolResultState` provides the arguments, `result`, `structuredContent` and the tool source.
- Match dimensions: `source`, the MCP `server`, `tool`, `toolPrefix`; priority, match specificity and plugin registration order decide the single renderer; disabled or incompatible plugins fall back to the default tool card automatically.
- `McpService.callStructured(...)`: new `McpStructuredCallResult`, so plugins calling MCP themselves also get `blocks` and `structuredContent`; without that capability the plugin falls back to `call(...)` with structured set to null.
- Structured results: the host persists MCP `structuredContent` into the tool details (256 KiB limit), and the `tool_execute` wrapper for MCP results restores the inner tool identity automatically; renderers can still rely on text and arguments alone when there is no structured result.
- Compatibility: `design` stays 0.9.0; MCP tool result rendering needs host 1.1.0+.

## 1.2.0 / API level 6

Additive release, `PLUGIN_API_LEVEL` stays 6: interfaces only grow, published plugins keep running on host 1.0.8+ without a rebuild; plugins that use the new capabilities need `minHostVersion >= 1.0.8`.

- manifest: `Provides` gains `themes: List<String>` (empty list by default, declaring the themes a plugin can provide); `Provides.isEmpty` counts themes too.
- Pages: `UiService.openPageSheet(pageId, arguments)` (the default implementation falls back to `openPageWithArguments`); new `PluginPagePresentation` (`PAGE`/`SHEET`, including `fromWire`) and `PluginPageContext.presentation` (default `PAGE`).
- Compatibility: `design` needs 0.9.0+ (`SuperPITheme(colors, fontStyle)` overload, `DdTheme.lightSuperPI()`, `DdStatusRow`/`DdNoticeBar`/`DdProgressBar`); every new API has a host implementation or an explicit unavailable degradation.

## 1.1.0 / API level 6

Additive release, `PLUGIN_API_LEVEL` stays 6: interfaces only grow, published plugins keep running on host 1.0.6+ without a rebuild; plugins that use the new capabilities need `minHostVersion >= 1.0.6`.

- New capabilities (manifest `capabilities`, 20 new wire entries): `settings`, `device`, `clipboard`, `permissions`, `packages`, `privilege`, `intents`, `components`, `background`, `location`, `contacts`, `sms`, `calendar`, `media`, `speech`, `web`, `credentials`, `accessibility`, `share`, `deeplink`.
- New `PluginHost` accessors: `settings()/device()/clipboard()/permissions()/packages()/privilege()/intents()/components()/background()/location()/contacts()/sms()/calendar()/media()/speech()/web()/credentials()/accessibility()`; `components()`/`credentials()`/`accessibility()` are marked `@PluginExperimentalApi`.
- Devices and system: device information and state streams, clipboard, runtime permissions and special grants, app list/install/uninstall/launch, Root/ADB state and privileged execution, arbitrary Intents and dynamic broadcast receivers, dedicated windows (`PluginHostActivity`) and host foreground services, WorkManager/AlarmManager background tasks and boot callbacks.
- Data and content: plugin-level settings storage, location and geocoding, contact read and write, SMS read, write and send, system calendar read and write, media save/share/preview/image read, speech recognition and read-aloud, host web search/fetch, host provider and MCP credential read (experimental).
- UI: `UiService` gains `openPageWithArguments`/`closePage`/`pageContext`/`confirm`/`prompt`/`choose`/`pickDate`/`pickTime`/`pickFiles`/`pickImage`/`scanQr`/`pickDirectory`/`snackbar`/`shareFiles`/`isDark`; new `LocalPluginPageContext` and `PluginPageContext`; new `PluginPickedFile` and interaction request models.
- Notifications: `NotificationService` gains `notifyChecked`, `listenerGranted`, `recent`, `received`, `removed`, `dismiss`; `PluginNotification` supports `actions`/`payload` (`pluginNotificationWithActions(...)`), and actions call back through the `notificationAction(...)` hook.
- Sessions and chat: `SessionService` gains `history`/`stop`/`create`/`rename`/`delete`/`open`/`archive`; `ChatService` gains `sendMessageWithAttachments(...)`.
- Event hooks: new `notificationReceived`/`notificationAction`/`backgroundTask`/`shareReceived`/`deepLinkReceived`/`appLifecycle` (the last two, plus share/background, are delivered only with the matching capability).
- manifest: new optional `permissions` (`PluginPermission` constants, declaring the runtime permissions a plugin will request) and `components` (background, foreground service, notification listener, accessibility, share receiver, deep link paths); the host validates at install time that permissions and capabilities agree.
- Compatibility: `design` needs 0.7.0+ (new Markdown/WebView/image/HTML/refresh/date-time/Snackbar/file row components); every new API has a host implementation or an explicit unavailable degradation.

## 1.0.1 / API level 6

Patch release, contract and apiLevel unchanged.

- Fixed the execution recursion in the `PluginTool` factory (`PluginToolImpl.execute` calling itself, which overflowed the stack).
- Release discipline: published coordinates are immutable from this version on, and later fixes only bump the patch version; machines that resolved the overwritten `1.0.0` AAR should upgrade to `1.0.1` or run `--refresh-dependencies`.

## 1.0.0 / API level 6

Breaking upgrade, with no level 5 compatibility layer.

- Contract levels: `PLUGIN_API_LEVEL = 6`, `BASELINE_API_LEVEL = 6`, `MIN_SUPPORTED_API_LEVEL = 6`, `HOST_UI_API_LEVEL = 2`; the host rejects level 5 plugins and asks for an update.
- Construction: DTOs constructed by plugins became interfaces plus top-level factories (`pluginTool`, `pluginSkill`, `promptSection`, `pluginPage`, `slotBinding`, `contributions`, `textResult`, `localizedText`, `timerRequest` and so on); public data class constructors are gone.
- Events and hooks: `PluginEvent` became a non-sealed marker interface, and the 21 observation events became host-implemented read-only interfaces; decision types became interfaces plus factories (`inputContinue`, `toolBlock`, `resultReplace` and so on).
- Enum convergence: `PluginCapability` became a value class; `MessageRole`, `ToolPhase`, `JobStatus`, `GoalPhase`, `McpServerStatus`, `ChatSendKind` and other result states became value class plus constants.
- Text and icons: removed `LocalizedText.Resource` / `IconRef.Resource`, keeping only the `localizedText()` / `assetIcon()` factories.
- New capability: manifest capability `prompt`; `PromptOverridePlugin` / `PromptOverride` (preamble/rules/sections/priority, experimental API).
- Host Surface: `PluginHost.text(source, vararg args: Any)` (formatting arguments accept only strings and numbers); manifest types became read-only interfaces.
- Engineering: moved the manifest codec / validation / version to the host; enabled explicit API; added `scripts/api-compat.sh` (an add-only ABI gate for plugin-api and design) and the `COMPATIBILITY.md` / `compat/` baseline.

## 0.8.1 / API level 5

- Republished the AAR with complete AndroidX consumer keep rules; depends on design 0.6.1.
