# Plan

1. P0 — make process visible and remove silent send: runId, ready state, immediate title/list, auto-follow, singleTask (done)
2. P1 — Claude chat layout + DSH process/tool/working timeline (done)
3. P2 — drawer history: no icons, long-press rename/pin/archive/delete, archived page (done)
4. P3 — Claude-style settings, 42-provider directory + API Key auth, active provider/model (done)
5. P4 — remove old UI, new runtime/event/session metadata ownership, tests/docs (done)
6. P5 — motion system, drawer/session switch smoothness, process timeline redesign, palette and press states (done)
7. P6 — markdown upgrade (nested lists, images, table scroll, highlighting), message action menu/quote, conversation switch cache (done)
8. P7 — text selection actions (text toolbar, draggable handles, outside dismiss), file attachments (thumbnails, markdown preview, share), clickable file references in assistant messages (done)
9. P8 — drawer v3: expandable workspace tree with per-workspace sessions, workspace pin, session pin icon, no section labels/selection accent; legacy UI cleanup (DdDialog/DdSearchField/DdTextField/DdSpinner, runtime env row fix, masked saved API key) (done)
10. P9 — performance (deferred IME/drawer reads, stream delta batching, stability report), mascot empty state (new icon set experiment reverted to original icon library), modern markdown inline-code/path rendering with home/workspace absolute path links (done)
11. P10 — personalization + `<system>`/`<instruction>` prompt assembly, notification center (session/tool/download progress), runtime developer-package bundles, 42 provider icons, 5 Latin fonts, remove billing/focus/sharing, privacy page, scheduling/skills design docs (done)
12. P10.1 — runtime 3 bundles (基础/办公/开发) + roomier layout, no individual tools; remove instruction limit; attachments 15; tone → prompt; chat settings page (done)
13. P10.2 — remove placeholder settings rows; Newsreader default + new font sample; always-on chat options; process auto-collapse at final-text start (done)
14. P11 — skills repository with zip install + global/project enable, workspace settings + AGENTS.md editor, app tools (`ask_user_question` UI, schedule CRUD + local store only) (done)
15. Next — scheduled executor + task page + hidden schedule workspace per `docs/SCHEDULING.md`; workspace-level session migration/archive; full-text search; root ShellExecutor when authorized
16. Later — phone capabilities via root shell/scripts; model-created schedules; custom tools only when shell cannot express it
