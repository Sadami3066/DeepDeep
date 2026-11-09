<div align="center">

<img src="docs/media/mascot.webp" width="88" alt="DeepDeep" />

# DeepDeep

**A personal agent for Android**

<sub>Conversation, device control, and a Linux environment in one app.</sub>

[简体中文](README.md) · [English](README.en.md)

<a href="https://github.com/Sadami3066/DeepDeep/releases"><img src="https://img.shields.io/badge/Download-Latest-2EA043?style=for-the-badge&logo=github&logoColor=white" alt="Download latest release" /></a>

<img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 10+" />
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
<img src="https://img.shields.io/badge/1.0.0-6B7280?style=flat-square" alt="1.0.0" />
<img src="https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square" alt="Apache-2.0" />

<br />
<br />

<img src="docs/media/hero-en.webp" width="100%" alt="DeepDeep: your personal agent, running on your phone" />

</div>

[Overview](#overview) · [Preview](#preview) · [UI](#ui) · [Getting Started](#getting-started) · [Installation & Configuration](#installation--configuration) · [Features](#features) · [Development](#development) · [Docs](#docs--feedback) · [Contributing](#contributing) · [License](#license)

## Overview

DeepDeep is a personal agent that runs on Android. It combines an agent runtime, Android system APIs, and an Alpine Linux environment in one application. It can work with local files, search the web, operate apps, run shell commands, and execute scheduled background tasks. The app is written in Kotlin and Jetpack Compose; most features do not require root.

It is intended for users who need to finish real tasks on a phone: handling files, looking up information, operating other apps, running scripts, or carrying a Linux environment in their pocket.

Version 1.0.0 is distributed through [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases). The source code is not public yet.

## Preview

<table>
<tr>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/bfeaac5e-da31-4790-a9e3-3f7e9a6b2018

<b>Agent runtime</b><br />
<sub>Checks availability and starts a background task.</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/32019883-ca2d-40f6-b18f-6c941131cc64

<b>Android integration</b><br />
<sub>Controls another app from the floating window.</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/9deb4845-514d-4c42-a197-0fa80f2cd9f4

<b>Mini apps</b><br />
<sub>Generates a check-in app on the spot.</sub>
</td>
</tr>
<tr>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/d387b523-4283-4269-9302-63f135e3541c

<b>Universal scanner</b><br />
<sub>Reads a QR code and routes it to the right app.</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/11c0d18c-192d-4e15-98bf-c6fb95498fae

<b>Remote connection</b><br />
<sub>Connects to DSH on your computer.</sub>
</td>
<td width="33%" valign="top" align="center">

https://github.com/user-attachments/assets/99a8fa59-0e0b-404d-a56f-f00015c5647d

<b>Voice overlay</b><br />
<sub>Asks from the floating bar on the home screen.</sub>
</td>
</tr>
</table>

## UI

<p align="center"><img src="docs/media/shots/ui-showcase.png" width="100%" alt="DeepDeep UI: home, drawer, providers, settings, and personalization" /></p>

<sub>Home, drawer, model providers, settings, and personalization.</sub>

## Getting Started

1. **Download the APK.** Get the latest `DeepDeep-1.0.0.apk` from [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases).
2. **Install and open.** Android 10 or later is required. The first launch runs a six-screen setup; it can be skipped and completed later in Settings.
3. **Connect a model.** Open **Settings → Providers**, choose a provider, and enter an API key or sign in with OAuth. Model and reasoning settings are selected per conversation.
4. **Initialize Linux.** Open **Settings → Linux Settings** and initialize Alpine. After that, `bash` is available in conversations.
5. **Choose permissions.** The default tier is Standard. To operate other apps, enable Wireless debugging or Root under **Settings → Permissions**.

To install with adb:

```bash
adb install -r DeepDeep-1.0.0.apk
```

## Installation & Configuration

### Install

| Item | Description |
|---|---|
| System | Android 10 or later |
| Package | APK from [GitHub Releases](https://github.com/Sadami3066/DeepDeep/releases) |
| Account | No account system; model keys are stored only on the device |
| Upgrade | Install over the same signing key. Changing keys requires uninstalling, which removes local sessions and credentials |
| Network | Conversations go only to the configured model provider; the market and update checks access GitHub |

The first launch runs a six-screen setup covering welcome, account, model, notifications and overlay, Linux, and permissions. Every step can be skipped and completed later in Settings.

### Configuration

| Item | Location | Description |
|---|---|---|
| Model providers | Settings → Providers | 42 providers; API key or browser OAuth |
| Permissions | Settings → Permissions | Standard / Wireless debugging / Root; screen control is off by default |
| Linux | Settings → Linux Settings | Initialize Alpine; install commands, Node, Python, and build toolchains |
| Android integration | Settings → Android Settings | Overlay, system assistant, notifications, and share intake |
| Remote connection | Drawer → Remote connection | Pair a computer through [`dsh-mobile`](https://github.com/saya-ch/dsh-mobile) |
| Plugins | Drawer → Plugins | Import signed `.ddp` packages; inspect capabilities and status |
| Memory | Automatic | Consolidated at session end; searchable and injectable into the current conversation |

## Features

| Capability | Description |
|---|---|
| **Agent runtime** | Tools, MCP, skills, background jobs, cron timers, and memory run in the same conversation; MCP tools are loaded on demand. |
| **Android integration** | Standard / Wireless debugging / Root permissions; virtual display, overlay, system assistant, files, sharing, and notifications. |
| **Extensions and automation** | Mini apps, universal scanner, plugins, market, remote connection, background jobs, and five-field cron. |
| **Models and chat** | 42 providers; Markdown, attachments, voice input, read-aloud, context usage, and `/compact`. |

### Linux environment

Alpine runs through proot and does not require root. After initialization, `bash` is available directly; Node, Python, database clients, and Android build toolchains can be installed as needed. Working files live in the app workspace by default, and file tools use absolute paths.

## Design

DeepDeep puts three capabilities in one app: an agent runtime, Android system APIs, and a Linux environment. The agent loop, tool dispatch, session storage, and plugin host are implemented in Kotlin; the app does not embed a Node runtime. Plugins that need Node install it in their own Linux environment. Linux starts on demand through proot and does not require root.

Plugins share the app process and permissions. Installing a plugin is equivalent to trusting its code. The app does not provide a separate plugin sandbox; third-party tool sources should use MCP where possible.

## Boundaries

| Item | Description |
|---|---|
| **Linux isolation** | Alpine runs in a proot user space, not a security sandbox |
| **Plugin permissions** | Plugins share the app process and UID; there is no sandbox |
| **Approval** | Three permission tiers (Standard, Wireless debugging, Root); commands are not confirmed individually |
| **Execution** | Tasks run on the device; closing the app stops them, and goal continuation is process-local |
| **Protected pages** | Payment and password pages cannot be captured; the agent returns control to the user |
| **Distribution** | 1.0.0 is distributed through GitHub Releases and is not available in an app store; the source is not public yet |

## Development

<details>
<summary><b>Modules, build, and verification</b></summary>

| Module | Responsibility |
|---|---|
| `app/` | Compose UI, chat, files, settings, plugin pages, mini app container, and screen assembly |
| `runtime/` | Agent runtime: sessions, tools, memory, jobs, timers, and plugin loading |
| `runtime-api/` | Pure JVM contracts between runtime and host |
| `shell/` | proot and ADB/Root privileged channels |
| `design/` | Theme, icons, shared components, and design tokens |
| `plugin-api/` | Plugin contracts and publishable SDK |

Requires JDK 17 or newer, Android SDK Platform 36, and build-tools 36.0.0.

```bash
./scripts/dev-setup.sh        # First run: check tools, write local.properties, fetch SDK, build APK
./scripts/check.sh            # Daily: compile + unit tests + lint + module boundaries + APK
./scripts/check.sh --full     # Before release: add the plugin SDK ABI baseline
./scripts/check.sh --device   # Also install over the device and launch
```

The `dev.pi` SDK is downloaded from the pi2kotlin `v0.4.0` release and verified by sha256; it does not depend on a local `~/.m2`. All Gradle calls go through `./scripts/gradlew-locked`; concurrent local builds may run out of memory.

See the [Developer Guide](docs/DEVELOPER_GUIDE.md) and [Plugin Development](docs/PLUGIN_DEV.md) for details.

</details>

## Docs & Feedback

- [User Guide](docs/USER_GUIDE.md): installation, permissions, Linux, plugins, mini apps, remote connection, and FAQ.
- [Developer Guide](docs/DEVELOPER_GUIDE.md): environment, modules, build, tests, and conventions.
- [Plugin Development](docs/PLUGIN_DEV.md): plugin contracts, manifest, signing, lifecycle, and UI extensions.
- [Release Notes](docs/RELEASE.md): signing, packaging, and distribution.
- [Changelog](CHANGELOG.md): features and fixes by version.

## Contributing

Issues and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR. Plugin authors can start with [Plugin Development](docs/PLUGIN_DEV.md) and [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins). Please report security issues privately instead of opening a public issue.

## License

Original code is licensed under [Apache-2.0](LICENSE). Third-party components, fonts, and icons are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<sub>Built on [pi](https://pi.dev) · [pi2kotlin](https://github.com/Sadami3066/DeepDeep-KotlinSDK) · Alpine Linux · Jetpack Compose</sub>
