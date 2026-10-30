# Changelog

## HEAD (since m2)

- refactor: route conversation writes through a single ConversationWriteTarget [M3-4]
- refactor: extract route dispatch into pure functions and fix the package boundary rule [M3-1]
- refactor: give the Skills screen a state holder and surface its errors [M3-11]
- refactor: move streaming delta batching out of ChatViewModel [M3-5]
- refactor: give the MCP screen a state holder and inline retryable errors [M3-12]
- feat: add page scaffold/group/empty/loading components and reuse them in two screens [M3-16]
- refactor: move sheets, dialogs and drawer into OverlayHost/ShellDrawerHost [M3-2]
- docs: forbid wildcard imports and document the shared-fake convention [M3-hygiene]
- refactor: move auto-retry state machine out of ChatViewModel [M3-6]
- refactor: give the Plugins screens a state holder and lifecycle-aware collection [M3-13]
- refactor: give the Schedule screens a state holder and drop wildcard imports [M3-14]
- refactor: extract deep-link/share intake and slash-command dispatch from the shell [M3-3]
- build: serialize every Gradle invocation behind one lock after an OOM crash [infra]
- docs: prepare open-source community files and a manual release workflow [M4-prep]
- chore: replace wildcard imports in AppRoutes with explicit ones [M3-h2]
- refactor: give the Workshop screen a state holder and surface scan/delete errors [M3-15]
- refactor: extract conversation cache and session draft store from ChatViewModel [M3-7]
- docs: add generated changelog and link community/release docs from README [M4-prep]
- refactor: move the session open pipeline into SessionOpener [M3-8]
- refactor: split session list, workspace, model and usage out of ChatViewModel [M3-9]
- docs: describe the runtime-api module and the solved boundary debt [M3-17]
- refactor: collapse the chat double model and shrink ChatScreen's parameter surface [M3-10]
- docs: refresh baseline and hotspot sizes after M3 completion [M3-17]

生成命令：`./scripts/changelog.sh <from-tag> <to-tag>`（不手写流水账）
