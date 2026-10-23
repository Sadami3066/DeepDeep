# Status

## Done: P10.1 follow-up polish

- Runtime page: 3 bundles only (环境基础 / 日常办公 / 开发编程), no individual tools; roomier title/subtitle spacing and one-line package summary; self-install hint
- Personalization: 8000-character instruction limit removed; "语气" renamed to "提示词"
- Attachments: max 15 (was 5)
- Chat settings: haptics / keyboard / streaming / reasoning / process expansion moved to a dedicated page
- Office bundle: poppler-utils, qpdf, openpyxl, xlsxwriter, Pillow, tesseract + pip python-docx/python-pptx/pdfplumber; verified imports and pdftotext

## Done: P10 personalization, notifications, runtime tools

- Settings: profile → personalization (nickname, interaction style, prompt presets, user instruction); billing/time-focus/sharing removed; privacy page; font picker; notification permission row
- Prompt assembly: `<system>` (identity/environment/hidden interaction rules) + `<instruction>` (user preference) injected through SDK hook; old `deepdeep_environment` removed; XML escaping
- Notifications: foreground service shows session title/activity, multi-session count, stop action; background completion notification opens the session; download progress channel
- Runtime: 3 developer-tool bundles (environment basics / office / development), background `apk update/add` with line streaming, optional pip post-install for office, notification progress, installed-state refresh
- Provider icons: 42 providers (15 color, 26 mono, Radius fallback), list/detail wiring
- Fonts: Instrument Sans / Geist / IBM Plex Sans / Newsreader / Lora bundled; Chinese falls back to system; font picker persists and rebuilds
- Design docs: `docs/SCHEDULING.md`, `docs/SKILLS.md`
- Unit tests for prompt assembly and package catalog; existing tests green

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

- UI v3 drawer: workspaces laid out directly as an expandable tree (Home is the default workspace row), multiple workspaces can stay expanded, chevron-only disclosure, no section labels and no selection accent; top-bar new-chat button removed; sessions can only be created from the workspace row + (or workspace long-press menu)
- workspace pin (long-press menu, persisted in AppPreferences, pinned-first order, small pin icon); session pin only within its workspace, shown as a small pin icon instead of relative time; per-workspace + creates a session in that workspace
- drawer loads all non-archived sessions across workspaces (`allSessions`) and groups them by cwd; workspace rows get folder icon + 15sp medium name, session rows get message icon + smaller secondary text and indentation; expand/collapse via LazyColumn `animateItem()`
- legacy UI replaced with self-written controls: DdDialog (no Material AlertDialog, IME-aware), DdSearchField (session/archived/provider search), DdTextField (API key + rename), DdSpinner (all loaders); DdSegmentedControl no longer shows square press ripple
- settings runtime-environment page fixed (removed nested wash card and misaligned size line; DdSwitch/DdSpinner/DdDialog standard rows)
- Provider detail shows masked saved key placeholder (`sk-*****`) instead of "已保存，输入新 Key 可替换"

- UI v4 performance: IME bottom inset read moved to layout phase (janky frames 15.57% -> 7.64%, slow-UI frames 66 -> 35); drawer scrim alpha moved to draw phase; 16ms stream delta batching (streaming same-prompt 20s: janky 29.95% -> 23.76%, slow-UI 339 -> 238); stability config now includes AttachmentRef + File, full compiler report has no unstable ChatScreen params
- UI v4 visual: original icon library kept (attempted new Lucide asset set reverted); drawer hierarchy uses existing Folder/Message icons; mascot welcome WebP (480px, ~56KB) in new-chat empty state (light/dark)
- UI v4 markdown: CommonMark code spans (multi-backtick, space trim, newline normalization); workspace file refs render as links (no code chip); path-like inline code renders monospace/no background; other inline code uses subtle neutral codeBackground; new tests + docs

## Checks

- `verifyModuleBoundaries` pass
- `:runtime:testDebugUnitTest`, `:app:testDebugUnitTest`, `:shell:testDebugUnitTest` pass
- `lintDebug` 0 errors
- `assembleDebug` / `assembleRelease` pass
- Device smoke: expandable workspace tree + per-workspace session creation, workspace pin ordering persistence across restart, session pin icon, custom dialog above keyboard, runtime page layout, masked Provider key, unified search fields, segmented control without ripple
- Perf smoke (gfxinfo): drawer/IME/scroll/streaming scenarios captured before/after in `artifacts/validation/perf/`
- Markdown/visual smoke: light/dark empty state with mascot, new icons in drawer/settings, path table rendering with clickable workspace refs and no code chips

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
- `artifacts/validation/ui-round3/`: drawer workspace tree, workspace pin order, session pin icon, dialog above keyboard, runtime settings fixed, provider masked key, provider search, session search, archived search
- `artifacts/validation/ui-round4/`: new icon drawer/settings, mascot empty state light/dark, markdown path rendering (links vs monospace vs codeBackground)
- `artifacts/validation/perf/`: performance baseline/after notes and raw gfxinfo dumps
- `artifacts/validation/ui-round5/`: original icon library restored in drawer/settings, simple-prompt natural Markdown answer, `files/home` absolute path rendered clickable and preview opened
- `artifacts/validation/round7/`: 3 bundle runtime layout, chat settings page, prompt rename/no limit, office bundle install (`office-libs-ok`, `pdftotext` 25.12)
- `artifacts/validation/round6/`: personalization (nickname/mode/tone/preset), settings removals/privacy/fonts, 42 provider icons, runtime package install (network + Python, notifications, installed state, 72MB rootfs), prompt `<system>`/`<instruction>` evidence, foreground/completion notifications and session open

Device: Redmi 22101316UCP / Android 13 / arm64-v8a.

## Pending

- root: `ShellExecutor` extension point; implementation deferred until root testing is authorized
- scheduled tasks: implementation per `docs/SCHEDULING.md` (service tick + catch-up)
- skills: implementation per `docs/SKILLS.md` (global/workspace CRUD, enable/disable)
- proactive cron scheduling tied to interaction style
