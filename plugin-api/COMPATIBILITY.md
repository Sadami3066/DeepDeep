# Plugin API compatibility contract

This file defines the public API promise for `plugin-api` and `design`. Read it before changing public types or signatures in either module; the tool gate is `./scripts/api-compat.sh`.

## 1. Surface categories

| Category | Meaning | Examples | Compatibility promise |
|---|---|---|---|
| Plugin Surface | APIs plugins implement, call or construct | `SuperPIPlugin`, `PluginHooks`, factory functions, request DTOs | Frozen; within the same `PLUGIN_API_LEVEL` only binary-compatible additions |
| Host Surface | APIs the host implements and plugins consume read-only | Service interfaces, event interfaces, result DTOs | Properties and methods may be added, never removed or changed |
| Host Only | Host internals | manifest parsing and validation, registry, install records | Not in the SDK, not covered by the promise |

`apiLevel` is the contract level: **the same apiLevel = binary compatible**; plugins must keep running without a rebuild.

## 2. Allowed changes (no level bump needed)

- New optional interfaces (for example a new `PromptOverridePlugin`); plugins that do not implement them keep the old path;
- Adding methods or properties **with default implementations** to interfaces that plugins implement, such as `SuperPIPlugin`, `PluginHooks`, `PluginUiContributions`;
- Adding service accessors or methods to `PluginHost` (plugins are the caller);
- Adding read-only properties/methods to Host Surface interfaces, and fields to result data classes (plugins do not construct them);
- Adding optional manifest fields (for example `provides.themes`) and `PluginCapability` constants;
- Adding methods/properties with default implementations to service interfaces (for example `UiService.openPageSheet`, `PluginPageContext.presentation`);
- Adding factory functions, value class constants and `@PluginExperimentalApi` experimental APIs.

The default interface methods of `plugin-api` rely on Kotlin's JVM default output (the current toolchain defaults to `jvm-default=enable`, and artifacts keep `DefaultImpls` as well); do not turn that behavior off.

## 3. Incompatible changes (apiLevel bump required)

- Deleting or renaming any public class, interface, method, property, constructor, enum/value class constant;
- Changing public method signatures, parameter default semantics, return types or exception contracts;
- Adding abstract (non-default) members to interfaces **plugins implement**;
- Changing public data class constructor parameters (including new fields) or the `copy` signature; types plugins construct directly must go through factories;
- Extending a sealed hierarchy with a new subtype, or adding values to result enums (an old plugin's exhaustive `when` throws `NoWhenBranchMatchedException`); result states should use value class plus constants;
- Changing runtime semantics: hook timeouts/order, behavior when `availability` is unavailable, error code meanings, defaults.

A breaking change requires all of: bumping `PLUGIN_API_LEVEL`, updating the `MIN_SUPPORTED_API_LEVEL` policy, updating the `plugin-api/compat/level-*.api` baseline, and writing `plugin-api/CHANGELOG.md` plus the host release notes.

## 4. Experimental API

Mark new capabilities that are uncertain or not promised as stable with `@PluginExperimentalApi` (`@RequiresOptIn`). Plugins must opt in explicitly; renaming or reshaping is allowed while experimental. Graduation means only "drop the annotation and add it to the ABI baseline", never a signature change.

## 5. Deprecation

- Mark with `@Deprecated(level = WARNING)` first and keep it for at least one host release cycle;
- Removal is allowed only at the next `PLUGIN_API_LEVEL`;
- Before removal, document the migration in the compatibility matrix and the release notes.

## 6. Tools and process

- `./scripts/api-compat.sh`: dumps the public ABI of `plugin-api` and `design` and compares it with the `plugin-api/compat/`, `design/compat/` baselines; CI fails on removals or signature changes, added lines are allowed;
- `--update` is only for a level bump or the first baseline;
- `check.sh --full` includes this check; `release.yml` runs `check.sh --full` before signed packaging, so release builds are gated too;
- Old plugin binary regression: `scripts/plugin-binary-compat.sh` (device side, run on demand, not part of `check.sh`) takes `.ddp` packages published in the marketplace through install/enable/disable/upgrade/uninstall on a device;
- Compatibility matrix:

| apiLevel | SDK (plugin-api / design) | Host version | Notes |
|---|---|---|---|
| 5 | 0.8.1 / 0.6.1 | ≤1.0.3 | Historical contract, rejected from 1.0.4 |
| 6 | 1.0.x / 0.6.1 | 1.0.4+ | Factory construction, event interfaces, prompt override experimental capability |
| 6 | 1.1.x / 0.7.x | 1.0.6+ | Additive expansion: devices/permissions/package management/privileged access/Intents/dedicated windows/background tasks/clipboard/location/contacts/SMS/calendar/media/speech/web/host credentials/accessibility/settings storage, page arguments and interactive pickers, notification listening and actions, system event hooks, new design components; plugins can use the `permissions`/`components` manifest fields |
| 6 | 1.2.x / 0.9.x | 1.0.8+ | Theming and plugin page sheets: manifest `provides.themes`, `UiService.openPageSheet`, `PluginPageContext.presentation`/`PluginPagePresentation`; design theme color overload, `DdTheme.lightSuperPI()` and status/notice/progress components |
| 6 | 1.3.x / 0.9.x–0.11.x | 1.1.0+ | MCP/plugin tool result renderers: `ToolResultRendererContributor`, `ToolResultRenderer`/`ToolResultState`; the host persists structuredContent and supports `tool_execute` wrapper restoration |
| 6 | 1.4.x / 0.11.x | 1.4.3+ | Plugin-side card delivery: `Cards`, `pluginCard`, `pluginResult`; tool result copy and the `present` instruction are generated by the plugin SDK; the `images` service (since 1.3.1) needs host 1.4.0+ |
