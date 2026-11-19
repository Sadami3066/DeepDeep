# Super PI Agent User Guide

Applies to the current version. The interface and capabilities will keep changing; what the app actually shows is authoritative.

## Install and first launch

- Requires Android 10 or later.
- Download the package from [GitHub Releases](https://github.com/Sadami3066/SuperPI/releases). The first launch runs a 6-screen onboarding: welcome, account, models, notifications and permissions, choosing a work environment, system access.
- You can skip onboarding or leave it midway, then fill in each item later in Settings.
- The app has no account system and needs no sign-up; model keys stay on this device.

## Updates

- Check the current version and for a new one under Settings → About → Software update.
- PI checks for updates when you open or return to the app, at most once a day; a failed check stays quiet, and you can check again manually on the update page.
- When a new version exists, the "Software update" row shows "x.y.z available" in the accent color, and a small dot appears next to the gear on the drawer account row.
- The update page shows the release notes and package size of the new version; tap "Download and install" and follow the on-screen prompt.
- The first time you install an update, the system asks you to allow PI to install apps; after you allow it, PI continues automatically, and later updates no longer ask for this permission.
- Updates do not delete local data: sessions, settings and credentials are kept.
- Without a network, tap "Open in browser" on the update page to download the package manually from the release page.

## Models and chat

- Supports 42 providers, with API key or browser OAuth sign-in.
- Custom providers are supported too: enter the endpoint and key, fetch the model list, and configure context size, max output, image input and thinking level for each model. The endpoint is usually `https://`; a gateway on this machine or the LAN may use `http://`, but plain HTTP is not encrypted, so do not use a public address.
- Models and thinking levels are per session; switching sessions does not affect other conversations.
- "Chat detail level" in chat settings offers detailed or concise: in concise mode tools and thinking collapse to a one-line summary, and tapping still shows the details.
- Supports Markdown, code blocks, tables, images, files, voice input and read-aloud.
- Up to 20 attachments, 1 GB per file; the camera screen can recognize several QR codes at once.
- The ring at the top right of the input area is context usage; tap it to see input, output, cache and cost.
- Long sessions can be compacted with `/compact`; `/goal` manages goals; `/skill:<name>` forces a skill to load.

## Bots

- Every bot in the drawer is an independent assistant identity: its own name, avatar, one-line summary and instructions, plus its own skills, plugins, connectors, tools and file directory.
- Sessions under each bot are independent; switching bots switches the whole context.
- The default bot is PI, uses the app icon, and cannot be renamed or deleted; all its capabilities are on by default and can be turned off one by one.
- New bot: in the Bot section of the drawer tap "New", then name it, pick an avatar and write a summary. A new bot starts with no capabilities enabled; turn them on in "Bot settings" after creating it.
- Bot settings:
  - Capabilities: per-item switches for skills, plugins and connectors;
  - Tools: screen tools and mini app tools, off by default; screen tools need the ADB or Root system access tier;
  - Instructions: the bot's behavior description, applied to new sessions after saving.
- The skills, plugins and connectors pages only install, sign in, update and delete; they do not decide which bot uses what.
- Memory and remote connection are under "Show more" in the drawer.

## System access

System access has three tiers, switched under Settings → System access; the tier decides which identity `bash`, file and system commands run as.

| Tier | What it can do | Notes |
|---|---|---|
| Normal | `bash` runs in the Linux environment inside the app; files, network, calendar and so on work as usual | This environment is not a security sandbox |
| Wireless debugging | `bash` still runs inside the app; system commands run as the wireless debugging identity and can operate the screen and system settings | Needs system "Wireless debugging" pairing; after a reboot, turn wireless debugging on again and connect |
| Root | `bash` and file operations run as root and can read and write system files directly | Authorized by the system root manager; the first Root setup takes a while, and you can retry on the System access page if it fails |

You no longer need to enable a separate shell: ordinary commands run through `bash` in the current tier, and system commands automatically use the matching identity for the same tier.

When browsing, previewing or sharing system files in the Root tier, the app first copies the file to the app cache, opens it there, and clears it when it expires, so no copy is kept long term.

Wireless debugging pairing, first time:

1. Turn on "Wireless debugging" in developer options.
2. On the System access page tap "Pair wireless debugging".
3. On the system "Pair device with pairing code" page, note the 6-digit code.
4. Pull down the notification, expand it and enter the code.

After pairing succeeds the app starts the daemon automatically. Wireless debugging turns off after a Wi-Fi change or a reboot; turn it on again and tap "Connect" on the System access page — no new pairing needed.

## Screen control

Screen control is off by default; turn it on in that bot's Settings → Tools. You also need the wireless debugging or Root tier in System access; when neither is available the switch warns that it is not in effect and the agent cannot operate the screen.

- By default the agent works in a **background virtual screen**, without taking over the main screen you are using.
- To operate the main screen it must explicitly pass `target=main`.
- The virtual screen closes after 15 minutes without agent activity, to save power.
- Protected pages such as payment and password screens cannot be captured; the agent hands control back to you.
- The floating window shows a thumbnail of the virtual screen; long-press PI to expand or collapse the preview.

## System integration

- **System assistant**: set PI as the default assistant, then long-press the power button or use the assistant gesture to give a task from any app.
- **Floating window**: shows run progress, pending questions and deliverables; tap it for the run panel.
- **Share receiver**: share images, files or text from other apps and they are inserted into the current session or prefill the input box.
- **Notifications**: background tasks, scheduled tasks and plugin messages are delivered as notifications; tap one to return to its session.

## Linux environment

- In the default tier Alpine runs in proot and needs no root; the Root tier does not use proot, and `bash` runs directly in the system environment.
- Initialize it once in the "Linux environment" section of Settings → System access; after that `bash` is ready.
- Common commands, Node, Python, database clients, office suites and compiler toolchains can all be installed on demand.
- Each bot has its own file directory, where working files go by default; file tools must use absolute paths.

## Automation

- **Background tasks**: long jobs such as downloads, builds and polling can run in the background, with incremental output visible at any time and a stop action.
- **Scheduled tasks**: five-field cron, bound to the current session; results are delivered back to that conversation. A missed run is delivered once the next time you open the app; delivery is not guaranteed when the service is killed by the system.
- **Goals**: create with `/goal <text>` and they keep running automatically; they live only in the current process and are cleared on restart.
- **Watch for me**: have the agent run a background task that polls a page or API and performs the next step when the condition holds, for example "watch the court and book a slot when one frees up".

## Extensions

### Plugins

- A plugin is a signed `.ddp` package, installed from Plugins → Import plugin.
- The confirmation page shows name, version, size, sha256, publisher, capability list and the tools, skills and pages it will provide.
- The first install asks you to trust the publisher; later updates must use the same key.
- Plugins share the app process and permissions, with **no sandbox**; install only trusted sources.
- The app ships no plugins; uninstalling removes plugin files, sign-in information and skills.

### Skills

- A skill is a zip with `SKILL.md`; once installed it can be enabled globally or in a single bot.
- Type `/` to see available skills; `/skill:<name>` forces one to load.

### MCP

- Supports Streamable HTTP servers and OAuth.
- Tools load on demand through `tool_search`, so all schemas do not go into the context at once.
- If a connection fails, check the address, authorization and network first.

### Marketplace

- Plugins, skills and remote MCP servers are available from the [PI marketplace](https://github.com/Sadami3066/SuperPI-Market). The index is signed and artifacts are checked against sha256.

Plugin entries list the access they need (Normal / ADB / Root); when the current system access tier is below the requirement, plugin details warn that some features may be unavailable.

The ADB tier of system access has two connection modes: wireless debugging (pairing code) and local ADB. When the system has no wireless debugging entry (for example some Huawei/HarmonyOS devices), enable local ADB from a computer first: turn on USB debugging on the phone and connect it with a cable, run `adb tcpip 5555` on the computer, then go to Settings → System access → ADB → Local ADB and tap "Connect and start"; allow the debugging authorization in the phone dialog on the first connection. After a phone reboot, run the computer command once more.

## Mini apps

Mini apps are HTML/CSS/JS pages the agent writes on the spot for a request:

- Pages read and write their own SQLite data through the bridge, isolated per mini app.
- Pages can run one-off agents.
- Pages can send notifications, set timed reminders, pick files and share, and use the camera, voice, calendar and location; these request system permissions the first time they are used.
- Once built, "Add to home screen" puts an icon there that opens it directly.
- The top bar of the viewer toggles full screen, and the system back key leaves it; "⋯" at top right edits, deletes or adds to the home screen.

Good for timetables, expense tracking, anniversaries, drawing lots and small utilities.

## Remote connection

Connect to DSH on a computer through `dsh-mobile`:

1. Install and enable `dsh-mobile` on the computer, and turn on LAN or remote access.
2. On the phone open Drawer → Remote connection and scan the code or paste the pairing link.
3. The first connection pins the computer certificate; a certificate change asks you to pair again.
4. When the LAN is unreachable it tries the remote entry automatically; one computer at a time.

The connection page is DSH's mobile UI on the computer; sessions, messages and bots match the computer.

## Data and privacy

- There is no PI account and no relay server; conversation content goes only to the model provider you configure.
- Sessions are stored on this device; pinning, archiving and renaming are local markers only.
- API keys are stored encrypted; plugin sign-in information lives in the plugin's own data directory.
- Update checks and marketplace index access go to GitHub.
- The full user agreement and disclaimers are under Settings → Privacy and user agreement; the project is distributed as open source under Apache-2.0.
- Uninstalling the app deletes local sessions, settings and credentials; to keep them, export a backup with "Export session" in the chat top bar first.
- After a signing certificate change you cannot install over the old app; you must uninstall first, which loses data.

## FAQ

**Wireless debugging turned itself off?**
The system turns wireless debugging off after a Wi-Fi change or a reboot. Turn it on again and tap "Connect" on the System access page.

**Screenshot failed?**
Protected pages such as payment and password screens cannot be captured; some pages need Android 11+ or accessibility/ADB permission.

**A plugin will not install?**
First check that the `.ddp` is complete and validly signed, and that the publisher matches the installed version; an update that changes keys is rejected.

**Command not found?**
Initialize the Linux environment first, then install with the package manager; Node and Python are not all preinstalled.

**A scheduled task did not fire?**
Check that the app process is still alive; delivery is not guaranteed when the service is killed by the system, and a missed run is delivered once the next time you open the app.

**Model error?**
Check the key, balance, network and provider status; you can retry in the session, and a failure does not re-run tools automatically.

**Why do file tools require absolute paths?**
A bot's file directory is only a default location, not an access boundary; absolute paths avoid ambiguity and are easy to reuse from Linux and plugins.

## Limits

- In the Normal and Wireless debugging tiers the Linux environment is a proot user space, not a security sandbox.
- Plugins share the app process and permissions; there is no sandbox.
- There is no second approval step, only the Normal / Wireless debugging (ADB) / Root tiers.
- Goal continuation lives only in the process.
- Protected pages cannot be captured or automated.
- Distribution is local for now, with no app store listing.
