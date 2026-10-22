# Status

## Done: P0-P4 polish

- Claude-style chat layout + working state + DSH process timeline
- real auto-follow: live tool/reasoning rows stay visible; jump-to-bottom chip
- runId filtering, immediate session title/list refresh, cold-start ready state
- singleTask + notification intent flags (one MainActivity)
- drawer history without icons; long-press rename/pin/archive/delete; archived page
- Claude-style settings with placeholders; real Provider/运行环境/颜色模式/关于
- 42-provider directory + write-only API Key auth + active provider/model selection
- thinking level moved to chat composer; settings no longer controls it
- old Assistant*/LinuxSettingsScreen removed; provider/model/session metadata owned by new code

## Checks

- `verifyModuleBoundaries` pass
- `:runtime:testDebugUnitTest`, `:app:testDebugUnitTest`, `:shell:testDebugUnitTest` pass
- `lintDebug` 0 errors (10 version/resource warnings)
- `assembleDebug` / `assembleRelease` pass
- Release/R8 signed with debug key, installed and chatted on device

## Device evidence

`artifacts/validation/polish/`:

- `new-long-2/6/12/22.png` — long tool task: reasoning/tool/working visible during run
- `new-stop.png` — stop marker + failed tool + process summary
- `drawer-immediate.png` — new session title appears in drawer immediately
- `new-actions.png` — long-press session actions
- `new-settings2.png` — Claude-style settings
- `provider-list2.png` — 42-provider directory
- `final-home.png`, `final-empty.png`, `release-final.png`
- `single-activity.txt`, `fgs-dumpsys.txt`

Device: Redmi 22101316UCP / Android 13 / arm64-v8a.

## Pending

- root: `ShellExecutor` extension point; implementation deferred until root testing is authorized
- scheduled prompts / event triggers
- skills/AGENTS.md provisioning, memory conventions
- notifications/calendar/GUI/voice (later)
