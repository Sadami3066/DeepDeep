<div align="center">

<img src="docs/media/mascot.webp" width="96" alt="PI" />

# Super PI Agent

**A personal agent for Android**

<sub>Press and hold the power button, say what you need, and it gets to work.</sub>

[简体中文](README.cn.md) · English

<a href="https://github.com/Sadami3066/SuperPI-App/releases"><img src="https://img.shields.io/badge/Download-Latest-111111?style=for-the-badge&logo=github&logoColor=white" alt="Download latest release" /></a>

<img src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 10+" />
<img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
<img src="https://img.shields.io/badge/Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
<img src="https://img.shields.io/github/v/release/Sadami3066/SuperPI-App?style=flat-square&color=111111" alt="Latest release" />
<img src="https://img.shields.io/badge/License-Apache--2.0-blue?style=flat-square" alt="Apache-2.0" />

</div>

[Overview](#overview) · [Capabilities](#capabilities) · [Quick Start](#quick-start) · [Installation & Configuration](#installation--configuration) · [Boundaries](#boundaries) · [Development](#development) · [Docs](#docs--feedback) · [Contributing](#contributing) · [License](#license)

## Overview

Super PI Agent is a personal agent that runs on an Android phone. Hold the power button, ask in voice or text, and it does the work on the device: managing files, controlling other apps, running Linux commands, and connecting to external services.

It needs no server and no always-on computer. Model requests go only to the provider you configure; conversations, files, credentials, and plugin data stay on the phone.

Super PI Agent ships a complete agent harness: tools, skills, long-term memory, MCP connectors, and a plugin system. A lightweight Linux environment covers most desktop office and coding tasks. Plugins connect external services such as Feishu, WeCom, campus systems, email, maps, ride hailing, hotels, and rail.

The project is open source and auditable. Apart from the model provider and the MCP servers or plugins you configure, it does not upload data.

Installers are available from [GitHub Releases](https://github.com/Sadami3066/SuperPI-App/releases).

## Capabilities

- **Natural interaction**: wake by holding the power button; voice input and text-to-speech in the overlay. Messages, attachments, images, and files share one thread.
- **Phone control**: screen tools read the UI, tap, type, and scroll. A virtual display lets the agent operate other apps in the background.
- **On-device execution**: a bundled Alpine/proot user space runs shell, Node, Python, and common toolchains. Root access runs in a native root environment.
- **Watch for me**: scheduled and background jobs keep checking a condition and act when it matches.
- **Miniapps**: build small HTML/CSS/JS apps and open them from a home-screen shortcut.
- **Plugin ecosystem**: install signed `.ddp` plugins to add tools, skills, pages, and themes. The market offers plugins, skills, and remote MCP servers.
- **Bring your own model**: multiple providers with API-key or OAuth sign-in; model and thinking level are chosen per session.
- **Local first**: no account system and no backend of our own; data stays on the device by default.

## Quick Start

1. **Download the installer.** Get the latest build from [GitHub Releases](https://github.com/Sadami3066/SuperPI-App/releases).
2. **Install and open.** Android 10 or newer is required. The first launch walks through setup; you can follow it or skip and finish later in Settings.
3. **Connect a model.** Open Settings → Providers, choose a provider, and enter an API key or sign in with OAuth.
4. **Initialize Linux.** In Settings → System access → Linux environment, finish the Alpine setup.
5. **Choose system access.** Normal access is the default. To control other apps, enable wireless debugging or root in Settings → System access; with wireless debugging, enter the pairing code from the system notification.

## Installation & Configuration

### Installation

| Item | Details |
|---|---|
| System | Android 10 or newer |
| Installer | APK from [GitHub Releases](https://github.com/Sadami3066/SuperPI-App/releases) |
| Account | No account system; model keys stay on the device |
| Updates | Install over the same signature. Changing signatures requires uninstalling, which deletes local sessions and credentials |
| Network | Conversations go only to the configured model provider; the market and update checks use GitHub and may need a proxy on some networks |

### Configuration

| Item | Where | Notes |
|---|---|---|
| Model providers | Settings → Providers | Multiple providers; API key or browser OAuth |
| System access | Settings → System access | Normal, wireless ADB, or root |
| Linux | Settings → System access → Linux environment | Initialize Alpine; install shell, Node, Python, and toolchains |
| Android integration | Settings → Android setup | Overlay, system assistant, notifications, and share targets |
| Remote connection | Drawer → Remote | Pair a computer through [`dsh-mobile`](https://github.com/saya-ch/dsh-mobile) |
| Plugins | Drawer → Plugins | Import signed `.ddp` files; inspect capabilities and runtime state |
| Memory | Automatic | Distilled when a session ends; searchable and injectable into the current session |

## Boundaries

| Item | Details |
|---|---|
| **Linux isolation** | On normal and wireless-debugging levels, Alpine runs in a proot user space and is not a security sandbox. Root level uses the system root environment directly |
| **Plugin permissions** | Plugins run in the app process with the same UID; there is no sandbox |
| **Approval** | Only the three system-access levels are chosen; commands are not confirmed one by one |
| **Where it runs** | Tasks run on the device and stop when the app stops. Goal continuation lives only in process memory |
| **Protected screens** | Payment and password screens cannot be captured; the agent hands control back to you |
| **Distribution** | Distributed through GitHub Releases, not an app store. The source is public |

## Development

<details>
<summary><b>Modules, build, and verification</b></summary>

See [Architecture](docs/ARCHITECTURE.md) for modules and dependency direction.

Requirements:

- JDK 17. The build targets Java 17 (`jvmToolchain` 17).
- Android SDK Platform 36 and build-tools 36.0.0 (about 5.6 GB).
- At least 8 GB RAM and 10 GB free disk; a cold first build takes roughly 15–30 minutes.
- Android SDK locations: macOS `~/Library/Android/sdk`, commonly `~/Android/Sdk` on Linux. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` if needed.
- Windows is not supported natively: the scripts need bash and flock. Use WSL2, Linux, or macOS.

```bash
./scripts/dev-setup.sh            # first run: check JDK and Android SDK, install Platform 36/build-tools if needed, build debug APK
./scripts/build.sh quick          # day-to-day compile
./scripts/build.sh test           # targeted unit tests
./scripts/build.sh check          # full verification (same as check.sh --full)
./scripts/build.sh release        # full verification, then release APK
./scripts/check.sh                # compile + unit tests + lint + module boundaries + APK
./scripts/check.sh --full         # before delivery: also plugin SDK ABI baseline
./scripts/api-compat.sh           # verify plugin SDK public ABI (--update rewrites the baseline)
```

See the [Developer Guide](docs/DEVELOPER_GUIDE.md) for the complete workflow.

</details>

## Docs & Feedback

- [User Guide](docs/USER_GUIDE.md): installation, system access, Linux, plugins, miniapps, remote connections, and troubleshooting.
- [Developer Guide](docs/DEVELOPER_GUIDE.md): environment, modules, build, tests, and conventions.
- [Plugin Development](docs/PLUGIN_DEV.md): plugin contracts, manifest, signing, lifecycle, and UI extensions.
- [Architecture](docs/ARCHITECTURE.md): modules, runtime, plugins, and update model.
- [Product Principles](docs/PRODUCT.md): product and design constraints.
- [Release](docs/RELEASE.md): signing, packaging, and distribution.
- [Changelog](CHANGELOG.md): changes by version.

## Contributing

Issues and pull requests are welcome. Read the [Contributing Guide](CONTRIBUTING.md) first. Plugin authors can start with [Plugin Development](docs/PLUGIN_DEV.md) and [Super PI Agent official plugins](https://github.com/Sadami3066/SuperPI-Official-Plugins). Do not report security issues in public; contact the maintainer directly.

## License

Our own code is licensed under [Apache-2.0](LICENSE). Third-party components, fonts, and icons are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

---

<sub>Built on [pi](https://pi.dev) · [SuperPI-KotlinSDK](https://github.com/Sadami3066/SuperPI-KotlinSDK) · Alpine Linux · Jetpack Compose</sub>
