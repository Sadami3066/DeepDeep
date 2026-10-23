# Status

## Done: P0-P5 polish

- Claude-style chat: right user bubbles, fixed thinking row, quiet collapsed process
- process redesign: expanded while running, details collapsed, auto collapse at finish; intermediate text/tools in order; final answer separate
- history turns grouped; single process per user turn
- conversation switch: persistent drawer, short centered spinner, no horizontal page push
- drawer: light paper background, white menus, no selected accent, anchored context menu, rounded press state
- menu text centered, compact width, press follows row; session rows have rounded press state
- full-width rounded press highlights on settings rows / provider rows
- process text rendered with the same Markdown pipeline as final answers
- lighter default hairlines
- settings: compact grouped cards, no verbose copy, anchored theme menu, Toast placeholders, nested runtime page
- palette: light background + pure white contrast + teal-blue accent; lighter hairlines
- motion system: DdMotion tokens, DdTypingDots, typed navigation, AnimatedScreenHost
- press states: rounded gray wash on menus/list rows/provider rows/session rows
- old Assistant*/LinuxSettingsScreen removed
- markdown: task lists, ordered start, fenced language/copy, quote bar
- DdMenu unified topbar/session/theme menus with rounded press states; DdPressable fast press-in
- whale-blue accent + danger red; icon-left menus; API key input margin; process row padding
- settings rows/provider/theme/license migrated to DdPressable; tap gets bounded ripple, long press gets wash
- menu icons left-aligned; danger delete red; whale-blue palette
- API key field margin and process row padding
- markdown upgrade: nested lists/tasks, block images (async loader + cache), horizontally scrolling tables, syntax highlighting, recursive blockquotes, setext headings
- markdown inline elements: escapes/hard breaks, autolinks (URL/email), reference links, inline HTML subset (b/i/code/kbd/sup/sub/br/a/span color), math fallback (inline + display block), footnote refs + end list
- image tap opens a full-screen lightbox (scrim, fit-center, fade, back/tap to dismiss)
- message actions: text-label selection toolbar (copy/select all/quote/read aloud), draggable handles, outside-tap clears selection/menu, composer quote preview, quoted context in user bubbles
- attachments: system file picker (multi-select), Claude-style composer preview cards (image thumbnail / text snippet / file card + circular remove), workspace copy for agent access, attachments rendered as separate equal-size square cards outside the user bubble, in a horizontal row (cropped image thumbs, extension-badge file tiles), full-screen preview (image/text/markdown/unpreviewable) with FileProvider share
- file references in assistant messages: attachments/workspace/absolute/file:// paths and markdown links are clickable and resolve to previews
- Claude Android app reverse-engineered for selection + attachment UX (Compose TextContextMenu provider, text-label toolbar, native handles, image/preview sheets)
- conversation switch cache: finished turns cached per conversation, reopening a visited conversation renders instantly while the SDK session opens in background
- performance: Compose stability config for runtime models + ChatUiState, draw-phase press wash, memoized markdown spans, image fade read in graphics layer

- i18n: values-en full English translations (app/chat/drawer/settings + status/error strings) + in-app language switcher (system/zh/en, persisted, attachBaseContext locale, no appcompat); runtime activity is a typed RuntimeActivity localized in ChatViewModel, session default titles localized
- search: independent drawer search (inline, title filter, empty hint, back/ESC close) and archived-screen search; drawer placeholders 技能/插件/定时任务 show toast
- projects: workspace renamed to home with session/data migration; create/select projects (files/projects/<id>) from drawer; sessions bound to cwd; project picker dialog
- multi-session concurrency: per-session runtime pool, background runs keep streaming while switching/new chat; busySessionIds; per-session cache restores instantly
- DeepSeek thinking levels now include MAX (off/low/high/max from model catalog)

- UI v2: Claude/ChatGPT-style DdSwitch (52×32, thumb shadow, spring) + DdSegmentedControl (theme), 5 persisted interaction switches (haptics/send-hides-keyboard/stream-follow/show-reasoning/process-expanded), settings wiring
- dedicated session search screen (drawer search icon, title-only match, workspace grouping, relative time, empty/loading states); drawer inline search removed
- Workspace rename (Project→Workspace end-to-end: UI/API/type/files/DataStore/directories/session cwd/docs) with idempotent migration; drawer DSH-style Workspaces section, create/rename/delete (Home fixed), running-session spinner, relative time
- IME: animated bottom inset (imeAnimationTarget + tween) + per-frame list anchoring, removed delay hacks; keyboard-up drawer open stays smooth

## Checks

- `verifyModuleBoundaries` pass
- `:runtime:testDebugUnitTest`, `:app:testDebugUnitTest`, `:shell:testDebugUnitTest` pass
- `lintDebug` 0 errors
- `assembleDebug` / `assembleRelease` pass
- Device smoke: responsive drawer, smooth session switch, collapsed process, anchored menu, text selection actions (labels/handles/tap-outside), attachment picker/thumbnail/preview/share, clickable file references

## Evidence

`artifacts/validation/polish/` plus latest:

- `process-running-v2.png`, `process-final-v2.png`
- `menu-anchored-v2.png`
- `settings-pressed-v2.png`
- `history-grouped.png`
- `topbar-menu.png`, `session-menu-new.png`, `final-settings-press.png`
- `artifacts/validation/markdown/`: markdown tour, nested quote/code, table scroll, dark theme, message menu, quote composer, copy toast, cached switch, inline elements, footnote list, image lightbox
- `artifacts/validation/attachments/`: selection toolbar, selection quote, file picker, composer chips, attachment bubble, image/text preview, share sheet, image thumbnail, markdown preview, clickable file references
- `artifacts/validation/claude-re/`: Claude Android selection toolbar/overflow/handle-drag/tap-outside screenshots

Device: Redmi 22101316UCP / Android 13 / arm64-v8a.

## Pending

- root: `ShellExecutor` extension point; implementation deferred until root testing is authorized
- scheduled prompts / event triggers
- skills/AGENTS.md provisioning, memory conventions
