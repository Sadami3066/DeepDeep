# Status

## Done: M0 + M1 + M2 basic

- 4-module scaffold: `:app`, `:runtime`, `:shell`, `:design`
- DeepSeek via pi2kotlin built-in provider; key encrypted with Android Keystore
- streaming chat, stop, session history, new/open conversation
- Alpine/proot installed from bundled rootfs; pi `bash` tool runs through `LinuxShellExecutor`
- file write/read through bash verified
- foreground service + notification; survives app background
- release/R8 build verified
- `verifyModuleBoundaries`, unit tests, lint (0 errors) pass

## Device evidence

`artifacts/validation/m1/`:

- `home.png`
- `bash-uname.png`
- `bash-file.png`
- `stop.png`
- `release-chat.png`
- `foreground-service.png`

Device: Redmi 22101316UCP / Android 13 / arm64-v8a.

## Pending

- root: design is `ShellExecutor`; implementation deferred until authorized testing
- scheduled prompts / event triggers
- skills/AGENTS.md provisioning, memory conventions
- notifications/calendar/GUI/voice (M5)
