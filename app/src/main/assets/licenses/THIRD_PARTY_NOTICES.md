# Third-Party Notices — deepdeep

deepdeep 自有代码 Copyright 2026 The deepdeep Authors，采用 Apache License 2.0（见 `LICENSE`）。
本仓库与 APK 中包含的第三方组件归各权利人所有，按各自的许可证分发，**不在 Apache-2.0 范围内**。

- 仓库内许可全文：`app/src/main/assets/licenses/`、`design/src/main/assets/licenses/`、`shell/src/main/assets/licenses/`
- APK 内路径：`assets/licenses/`（构建时合并以上目录）
- 联系与 GPL/LGPL 源码索取（written offer 受理）：<https://github.com/Sadami3066/DeepDeep/issues>
- 本文件随 APK 打包为 `assets/licenses/THIRD_PARTY_NOTICES.md`

## 0. 分发义务摘要

不构成法律意见；以各许可证全文为准。判断某个组件能否使用、能否闭源、再分发要做什么，以下为概要：

| 组件 | 许可证 | 再分发/闭源使用 | 主要义务 |
|---|---|---|---|
| PRoot、PRoot loader | GPL-2.0-or-later | 可作为独立可执行文件随 App 聚合分发；PRoot 自身代码不能闭源 | 附许可全文；提供完整对应源码（含构建脚本与 talloc）或有效书面要约；修改须声明 |
| Alpine rootfs 中 8 个 GPL-2.0-only 包（busybox、apk-tools 等） | GPL-2.0-only | rootfs 以数据包形式聚合分发；这些包自身代码不能闭源 | 附许可全文；提供对应源码（aports v3.24.2 及上游 tarball） |
| talloc 2.4.2 | LGPL-3.0-or-later | 可与专有程序链接分发（静态链接时须提供重新链接所需材料） | 附许可全文；提供库源码；不限制用户替换/重链接 |
| pi2kotlin SDK | MIT | 可闭源使用与再分发 | 保留版权声明与许可全文 |
| AndroidX / Compose / Kotlin / kotlinx.coroutines / Okio / WebKit | Apache-2.0 | 可闭源使用与再分发 | 保留许可副本；上游如有 NOTICE 文件须一并保留 |
| libadb-android 3.1.1 / AdbLib | Apache-2.0 / BSD-3-Clause | 可闭源使用与再分发 | 保留版权声明与许可全文；本项目按 Apache-2.0 使用 |
| spake2-android 2.2.1 | LGPL-3.0 | 可与 App 链接分发；需提供库源码与重新链接条件 | 附许可全文；提供源码；不限制用户替换/重链接 |
| Bouncy Castle 1.86（bcprov/bcpkix/bcutil） | Bouncy Castle License（MIT 式） | 可闭源使用与再分发 | 保留版权声明与许可全文 |
| hiddenapibypass 6.1 | Apache-2.0 | 可闭源使用与再分发 | 保留许可副本 |
| OkHttp 5.4.0 | Apache-2.0 | 可闭源使用与再分发 | 保留许可副本 |
| DataStore 重打包 proto-lite | BSD-3-Clause | 可闭源使用与再分发 | 保留版权声明与许可全文 |
| Lora / Manrope | OFL-1.1 | 可嵌入 App 分发 | 保留版权与许可全文；字体不得单独出售 |
| Tabler Icons / LobeHub lobe-icons | MIT | 可再分发 | 保留版权声明与许可全文 |
| Radius 图标、App Store 公开图标、飞书插件图标 | 非开源素材 / 品牌条款 | 仅可作相应服务或厂商的识别性使用；大规模分发或商用前应替换或取得授权 | 商标归属声明；不得暗示授权、合作或隶属关系 |
| mascot 插画 | 无（AI 生成；参考图权利未明） | 不在本仓库源码许可范围内，亦不适用 Apache-2.0 | 需项目所有者另行决定授权与分发范围 |

## 1. 随 APK 分发的 Linux 环境组件

### 1.1 PRoot — `lib/arm64-v8a/libproot.so`

- 许可证：GNU General Public License v2 or later（GPL-2.0-or-later）
- 版权行：`Copyright (C) 2015 STMicroelectronics`（二进制内自述）；上游 COPYING 另列 PRoot/CARE 贡献者名单
- 本 APK 内文件 sha256：`27c35e02cee0dd6fea9d81566741dc7d5064f2d158208e4b8a1395131c6fda34`
- 本仓库内路径：`shell/src/main/jniLibs/arm64-v8a/libproot.so`（APK：`lib/arm64-v8a/libproot.so`）
- 完整对应源码：
  - PRoot（OpenMinis Android fork）：<https://github.com/OpenMinis/proot/tree/1b444ee1cb2f4896c08166e9a12c905af5416f1c>
  - 构建脚本：<https://github.com/OpenMinis/OpenMinis/blob/b4c0661d5631ebab4d1a2e6f3fd4c805d4030a6c/deps/build_proot.sh>
    （sha256 `0c42a65d6ebe1be2573880673178ae1b19abadddbe61ea52b9227afc41e7f116`，490 行；Android NDK r28c 28.2.13676358、`ANDROID_API=26`、`aarch64-linux-android`；脚本注明不同 NDK/clang 版本产物非字节级可复现）
  - talloc 2.4.2（静态链接进 `libproot.so`）：<https://download.samba.org/pub/talloc/talloc-2.4.2.tar.gz>
    （sha256 `85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6`）
- talloc 许可证：LGPL-3.0-or-later（`talloc.c`/`talloc.h` 头声明 "version 3 ... or any later version"）
- talloc 版权行：`Copyright (C) Andrew Tridgell 2004`、`Copyright (C) Stefan Metzmacher 2006`
- 修改声明：本仓库不包含对 PRoot 或 talloc 源码的补丁；分发的二进制来自上述上游源码与构建脚本（该脚本注明产物非字节级可复现，因此以 sha256 固定分发文件，并以源码 + 脚本满足完整对应源码义务）。
- 许可全文：`licenses/GPL-2.0.txt`（官方 GNU 文本）、`licenses/LGPL-3.0.txt`；上游 PRoot COPYING 另存 `shell/src/main/assets/licenses/PRoot-GPL-2.0.txt`
  （与 OpenMinis/proot 的 COPYING 字节一致，sha256 `0ffa6454acbe7e410ac6f0007df83b9b3161fe55484c1f4b87487c0f2d228a6d`）

### 1.2 PRoot loader — `lib/arm64-v8a/libproot-loader.so`

- 许可证：GPL-2.0-or-later（PRoot 组件）
- 版权行：`Copyright (C) 2015 STMicroelectronics`（PRoot）；loader 实现来自 Termux PRoot 项目
- 本 APK 内文件 sha256：`44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04`
- 本仓库内路径：`shell/src/main/jniLibs/arm64-v8a/libproot-loader.so`（APK：`lib/arm64-v8a/libproot-loader.so`）
- 来源：Termux PRoot 的 loader 源码 <https://github.com/termux/proot/tree/master/src/loader>
- 二进制来源（与之一致）：OpenMinis 仓库 vendored 副本
  <https://github.com/OpenMinis/OpenMinis/blob/b4c0661d5631ebab4d1a2e6f3fd4c805d4030a6c/src/android/app/src/main/jniLibs/arm64-v8a/libproot-loader.so>
  构建脚本第 375 行以该 sha256 固定此文件（脚本 sha256 见 1.1）。
- 许可全文：`licenses/GPL-2.0.txt`

### 1.3 Alpine Linux minirootfs 3.24.2（aarch64）

- 本仓库内路径：`shell/src/main/assets/alpine-minirootfs-3.24.2-aarch64.rootfs`（APK：`assets/alpine-minirootfs-3.24.2-aarch64.rootfs`）
- 文件 sha256：`9bf70a7f18ea44094cbb5f70c58f9af129c8214745743db0e68e5502cc2ce773`，4,028,030 字节，与官方发布完全一致（未修改）
- 官方发布与校验值：<https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/>（`alpine-minirootfs-3.24.2-aarch64.tar.gz.sha256`）
- 版权行：各 Alpine 包版权归其上游作者；逐包构建配方与源码引用见 aports 标签 v3.24.2
- 包与许可（来源：rootfs 内 `/lib/apk/db/installed`，共 16 包）：

| 许可证 | 包 |
|---|---|
| GPL-2.0-only | alpine-baselayout 3.7.2-r1、alpine-baselayout-data 3.7.2-r1、apk-tools 3.0.8-r0、libapk 3.0.8-r0、busybox 1.37.0-r31、busybox-binsh 1.37.0-r31、scanelf 1.3.9-r1、ssl_client 1.37.0-r31 |
| MIT | musl 1.2.6-r2、alpine-keys 2.6-r0、alpine-release 3.24.2-r0 |
| MIT AND BSD-2-Clause AND GPL-2.0-or-later | musl-utils 1.2.6-r2 |
| Apache-2.0 | libcrypto3 3.5.8-r0、libssl3 3.5.8-r0 |
| MPL-2.0 AND MIT | ca-certificates-bundle 20260909-r0 |
| Zlib | zlib 1.3.2-r0 |

- 对应源码：Alpine aports 标签 v3.24.2 <https://github.com/alpinelinux/aports/tree/v3.24.2>，以及各 APKBUILD 引用的上游 tarball。
- 许可全文：`licenses/GPL-2.0.txt`、`licenses/Apache-2.0.txt`、`licenses/MPL-2.0.txt`、`licenses/Zlib.txt`、`licenses/BSD-2-Clause.txt`
- 说明：该 rootfs 不含 `/usr/share/licenses` 目录（tar 内容实测；`/usr/share` 存在但无 `licenses` 子目录）。Alpine 运行包默认不附带许可文件，因此许可文本由本 App 在 `assets/licenses/` 中提供，GPL 组件的完整对应源码按第 1.4 节的书面要约提供。

### 1.4 书面要约（GPL / LGPL 对应源码）

自本 APK 首次分发之日起至少三年内，任何第三方均可通过 <https://github.com/Sadami3066/DeepDeep/issues> 免费索取第 1.1–1.3 节所列 GPL/LGPL 组件的完整对应源码：OpenMinis/proot 源码快照、构建脚本 `deps/build_proot.sh`、Termux PRoot loader 源码、talloc 2.4.2 源码 tarball、Alpine aports v3.24.2 及各包上游源码；如经实体介质提供，仅收取介质与传输成本。

## 2. 编译进 APK 的代码库

### 2.1 pi2kotlin SDK 0.4.0

- 构件：`dev.pi:pi-coding-agent-android:0.4.0`、`dev.pi:pi-ai-android:0.4.0`、`dev.pi:pi-agent-android:0.4.0`、`dev.pi:pi-mcp-android:0.4.0`
- 版权行：`Copyright (c) 2026 pi2kotlin contributors`、`Copyright (c) 2025 Mario Zechner and pi contributors`
- 许可证：MIT
- 来源：<https://github.com/Sadami3066/pi2kotlin> 提交 `d03325b82de8e1c0544758dd4f9d567bc1aeabfa`（`LICENSE`）
- 本仓库内声明：`gradle/libs.versions.toml`（`pi = "0.4.0"`）与根 `build.gradle.kts`（`syncPiSdk`，从 pi2kotlin Release 下载并校验 sha256）
- 许可全文：`licenses/pi2kotlin-MIT.txt`
- 说明：上游 AAR/POM 未携带 LICENSE/NOTICE 元数据；本条目构成随二进制分发所需的版权与许可声明。

### 2.2 AndroidX / Jetpack Compose / Kotlin / kotlinx.coroutines / Okio / WebKit

- 构件与版本（Maven 坐标即固定版本，构件不可变）：
  - AndroidX：`androidx.core:core-ktx:1.17.0`、`androidx.activity:activity-compose:1.12.4`、`androidx.lifecycle:*:2.10.0`、`androidx.datastore:datastore-preferences:1.2.0`、`androidx.webkit:webkit:1.18.0`
  - Jetpack Compose：BOM `2026.03.00`（`androidx.compose.*`）
  - Kotlin stdlib：`2.4.21`
  - kotlinx.coroutines：`org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0`、`kotlinx-coroutines-android:1.11.0`
  - Okio：`com.squareup.okio:okio:3.18.2`
- 版权行：
  - AndroidX / Compose / androidx.webkit：`Copyright (C) The Android Open Source Project`
  - Kotlin：`Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors`（上游各文件年份可能不同）
  - kotlinx.coroutines：`Copyright JetBrains s.r.o.`
  - Okio：`Copyright (C) Square, Inc.`（各文件年份不同）
- 许可证：Apache-2.0
- 来源（固定版本/标签）：
  - <https://maven.google.com>（`androidx.*`、`androidx.compose.*` 各版本坐标；AndroidX 源码见 <https://cs.android.com/androidx/platform/frameworks/support>）
  - <https://github.com/JetBrains/kotlin> 标签 `v2.4.21`
  - <https://github.com/Kotlin/kotlinx.coroutines> 标签 `1.11.0`
  - <https://github.com/square/okio> 标签 `parent-3.18.2`
  - 注：kotlin-stdlib 与 androidx 部分构件的 Apache 文本随构件进入 APK（`META-INF/**/LICENSE.txt`）；kotlinx.coroutines 与 Okio 构件未携带文本，由本条目补充提供。
- 本仓库内声明：`gradle/libs.versions.toml`
- 许可全文：`licenses/Apache-2.0.txt`

### 2.3 DataStore 重打包 proto-lite

- 构件：`androidx.datastore:datastore-preferences-external-protobuf:1.2.0`（上游描述 "Repackaged proto-lite dependency for use by datastore preferences"）
- 版权行：`Copyright 2008 Google Inc.`（上游 protobuf/proto-lite 实现；androidx 重打包构件未另行声明版权行）
- 许可证：BSD-3-Clause（该构件 POM 声明）
- 来源：AndroidX DataStore 1.2.0（<https://developer.android.com/jetpack/androidx/releases/datastore#1.2.0>），源码 <https://cs.android.com/androidx/platform/frameworks/support>；上游 protobuf <https://github.com/protocolbuffers/protobuf>
- 许可文本位置：APK 内 `META-INF/androidx/datastore/datastore-preferences-external-protobuf/LICENSE.txt`（BSD-3-Clause 文本随 APK 保留）；补充全文 `licenses/BSD-3-Clause.txt`；版本声明 `gradle/libs.versions.toml`（`datastore = "1.2.0"`）

### 2.4 内嵌无线调试（ADB）依赖

- `libadb-android:3.1.1`（JitPack；上游 `GPL-3.0-or-later OR Apache-2.0` 双许可，本项目按 Apache-2.0 使用）
  - 版权：`Copyright 2013 Cameron Gutman`（AdbLib 部分，BSD-3-Clause）、`Copyright 2020-2022 Muntashir Al-Islam`（libadb 部分）
  - 来源：<https://github.com/MuntashirAkon/libadb-android> 标签 `3.1.1`，<https://jitpack.io/#MuntashirAkon/libadb-android/3.1.1>
  - 用途：无线调试 TLS 连接、配对与 `shell:` 流；不修改代码
  - 许可全文：`licenses/Apache-2.0.txt`、`licenses/BSD-3-Clause.txt`
- `spake2-android:2.2.1`（JitPack；LGPL-3.0；`libspake2.so`）
  - 版权：`Copyright 2021 Muntashir Al-Islam`；底层 Ed25519/ref10 与 BoringSSL SPAKE2 分别按其上游许可
  - 来源：<https://github.com/MuntashirAkon/spake2-java> 标签 `2.2.1`（`spake2-c` 子模块 <https://github.com/MuntashirAkon/spake2-c>）
  - 用途：系统无线调试配对码的 SPAKE2 密钥交换
  - 许可全文：`licenses/LGPL-3.0.txt`；完整对应源码见第 1.4 节书面要约
- `org.bouncycastle:bcprov-jdk15to18:1.86`、`bcpkix-jdk15to18:1.86`、`bcutil-jdk15to18:1.86`（Maven Central；Bouncy Castle License）
  - 版权：`Copyright (c) 2000-2023 The Legion of the Bouncy Castle Inc.`
  - 来源：<https://www.bouncycastle.org/>、<https://repo1.maven.org/maven2/org/bouncycastle/>
  - 用途：自签 ADB X.509 证书、配对 AES-GCM/HKDF 与 ADB 公钥编码
  - 许可全文：`licenses/Bouncy-Castle.txt`
- `org.lsposed.hiddenapibypass:hiddenapibypass:6.1`（Maven Central；Apache-2.0）
  - 版权：LSPosed 项目贡献者
  - 来源：<https://github.com/LSPosed/AndroidHiddenApiBypass>
  - 用途：配对时读取平台 Conscrypt 的 TLS keying material（Android 隐藏 API 豁免）
  - 许可全文：`licenses/Apache-2.0.txt`
- `com.squareup.okhttp3:okhttp:5.4.0`（Maven Central；Apache-2.0）
  - 版权：`Copyright 2019 Square, Inc.`
  - 来源：<https://github.com/square/okhttp> 标签 `parent-5.4.0`
  - 用途：访问本机 daemon 的 HTTP 回环接口
  - 许可全文：`licenses/Apache-2.0.txt`

### 2.5 仅构建/测试期使用、不随 APK 分发

Android Gradle Plugin 9.4.1、Kotlin 编译器、JUnit 4.13.2、commons-compress 1.28.0、androidx.test.runner 1.7.0、androidx.test.ext:junit 1.3.0、Android SDK/build-tools。它们在构建机或测试 APK 中使用，不属于本 APK 的分发内容，不在本声明范围。

## 3. 字体

字体随 APK 分发于 `res/font/`，源文件在 `design/src/main/res/font/`，许可文本在 `design/src/main/assets/licenses/`。

### 3.1 Lora — `design/src/main/res/font/lora_variable.ttf`

- 版权行：`Copyright 2011 The Lora Project Authors (https://github.com/cyrealtype/Lora-Cyrillic), with Reserved Font Name "Lora".`
- 许可证：SIL Open Font License 1.1（`licenses/lora-OFL.txt`，取自 Google Fonts `ofl/lora/OFL.txt`）
- 来源：Google Fonts `ofl/lora` <https://github.com/google/fonts/tree/main/ofl/lora>；上游作者仓库 <https://github.com/cyrealtype/Lora-Cyrillic>
- 文件 sha256：`822a6621ccbe8d97d20ac88c1c41f5615c9c2c202eaa75f272cd452aac6475a7`，与 Google Fonts `Lora[wght].ttf` 字节一致，**未修改**。

### 3.2 Manrope — `design/src/main/res/font/manrope_variable.ttf`

- 版权行：`Copyright 2018 The Manrope Project Authors (https://github.com/googlefonts/manrope)`（随附 OFL 文本；字体 name 表写 2019，上游两处年份不一致，均未修改）
- 许可证：SIL Open Font License 1.1（`licenses/manrope-OFL.txt`，与 Google Fonts `ofl/manrope/OFL.txt` 字节一致）
- 来源：Google Fonts `ofl/manrope` <https://github.com/google/fonts/tree/main/ofl/manrope>
- 文件 sha256：`3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6`，与 Google Fonts `Manrope[wght].ttf` 字节一致，**未修改**。

## 4. 图标与品牌图形

图形仅用于识别对应的服务、厂商或功能，**不表示任何授权、合作或隶属关系**；品牌名称与图形归各权利人所有。

### 4.1 Tabler Icons — `design/src/main/res/drawable/*.xml`（51 个 VectorDrawable）

- 许可证：MIT
- 版权行：`Copyright (c) 2020-2026 Paweł Kuna`
- 来源：快照 v3.50.0，提交 `ed177a085b652fe04ec4f37ced867c94621e7840`（<https://github.com/tabler/tabler-icons/tree/ed177a085b652fe04ec4f37ced867c94621e7840>）；SVG 转为 Android VectorDrawable，图形几何未改
- 许可全文：`licenses/tabler-MIT.txt`（与上游 `LICENSE` 字节一致）

### 4.2 LobeHub lobe-icons — `app/src/main/res/drawable-nodpi/provider_*.png` 与 `app/src/main/res/drawable-night-nodpi/provider_*.png`（42 个 provider 图标中的 41 个）

- 许可证：MIT（图标实现）；品牌图形归各品牌权利人
- 版权行：`Copyright (c) 2023 LobeHub`
- 来源：提交 `680f1d7f95b5ae6a494fe43539da8a992f17b53d`（<https://github.com/lobehub/lobe-icons/tree/680f1d7f95b5ae6a494fe43539da8a992f17b53d>）；41 个 provider 复用 30 份不同图形（42 个 provider 中仅 Radius 来自其他来源）
- 许可全文：`licenses/lobehub-MIT.txt`（与上游 `LICENSE` 字节一致）

### 4.3 Radius — `app/src/main/res/drawable-nodpi/provider_radius.png`

- 许可证：Earendil 品牌指引的第三方服务识别条款，**非开源许可**
- 版权行/权利人：Earendil（Radius 品牌）
- 来源：Earendil `elanor` 图标源，提交 `f9e904c08d2e2274714f35b9c848ba7bab19e2b3`
  <https://github.com/earendil-works/elanor/blob/f9e904c08d2e2274714f35b9c848ba7bab19e2b3/packages/logos/sources/radius/mark.svg>
- 处理方式：保留官方图形、色彩 token 与 1/4 标志宽度的留白，图形未改。

### 4.4 厂商图标 — `app/src/main/res/drawable-nodpi/vendor_*.png`（10 个）

| 图标 | 来源 | 许可/权利 |
|---|---|---|
| dingtalk、github、notion、slack、telegram、discord | Tabler Icons brand 图标，提交 `ed177a085b652fe04ec4f37ced867c94621e7840`（v3.50.0） | MIT，`Copyright (c) 2020-2026 Paweł Kuna`；`licenses/tabler-MIT.txt` |
| xiaomi | Wikimedia Commons [File:Xiaomi_logo.svg](https://commons.wikimedia.org/wiki/File:Xiaomi_logo.svg)（源自小米官方媒体素材包） | Public domain（简单几何/文字组合，低于版权独创性门槛）；"Xiaomi"/"mi" 为小米公司商标 |
| feishu | Apple App Store 公开应用图标（iTunes Search API `artworkUrl512`，中国区，2026-10-29）：<https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/45/f4/d8/45f4d8ef-fc19-8e6e-19eb-4e1e4ee1aabd/AppIcon-0-0-1x_U007epad-0-0-0-1-0-0-85-220.png/512x512bb.jpg> | **非开源素材**；图形与商标归 Beijing Feishu Technology Co., Ltd.（字节跳动） |
| wecom | 同上（企业微信）：<https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/3e/ba/6b/3eba6be1-a7fd-00c5-7f0d-2415c8258ef5/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/512x512bb.jpg> | **非开源素材**；图形与商标归 Tencent Technology (Shenzhen) Company Limited |
| wechat | 同上（微信）：<https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/84/6b/3a/846b3ab8-9dea-e3e5-861e-f6ecaa0b3387/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-0-0-85-220.png/512x512bb.jpg> | **非开源素材**；图形与商标归 Tencent Technology (Shenzhen) Company Limited |

### 4.5 飞书插件图标 — `app/src/main/res/drawable-nodpi/ic_plugin_feishu.png`

- 内容：飞书品牌图形（96×96）
- 版权行/权利人：飞书（Feishu）品牌，商标归北京字节跳动科技有限公司
- 来源：**非开源素材**；本仓库未保留该文件的原始下载记录（该文件于提交 `41b6885` 引入）
- 处置：公开分发前应改为与 4.4 一致的可追溯来源，或替换为已授权素材。

### 4.6 非开源素材替换预案

公开分发或商用前，以下非开源素材应替换为已授权素材，或从发行版移除：
`provider_radius.png`（Radius 品牌条款）、`vendor_feishu.png`、`vendor_wecom.png`、`vendor_wechat.png`（App Store 公开图标）、`ic_plugin_feishu.png`（来源未记录）。
替换时同步更新本文件对应条目与素材清单。

## 5. 应用内插画

### mascot — `app/src/main/res/drawable-nodpi/mascot_welcome.webp`

- 文件 sha256：`7dfe20ee6520c4aaf2eea1e45606bb3718313ba021925bceba9748b80b4d0de8`（56,846 字节）
- 来源：AI 生成（Codex 内置 `image_gen`，2026-10-21），由项目所有者提供的角色参考图衍生；生成记录与提示词保存在开发侧素材库
- 权利状态：**不适用 Apache-2.0，不在本仓库源码许可范围内**。参考图由用户提供，其作者与许可未随图提供；原始参考图不随 APK 或本仓库分发。对外发放该插画（例如以 CC 等许可单独发布）需项目所有者另行决定。

## 6. 聚合与商业使用说明（对公司/厂商口径）

> App 自有代码 Apache-2.0。Linux 环境组件（PRoot、Alpine）以独立可执行文件与数据包形式聚合分发，分别适用 GPL-2.0/MIT/Apache-2.0 等许可，源码与构建脚本可从 NOTICE 所列地址获取。若贵司政策不接受 GPL：`:shell` 是独立模块，可移除 Linux 功能后闭源使用其余部分，我们也可提供不含 GPL 组件的企业构建。

## 7. 许可文本来源与校验

以下文件均为对官方发布地址的直接下载（未改写条款）；BSD-2-Clause/BSD-3-Clause 的 SPDX 文本含模板变量，已按变量 `original=` 值展开，并将行尾由 CRLF 归一化为 LF，其余字节不变。`licenses/Bouncy-Castle.txt` 为上游 Bouncy Castle 许可文本的逐字转录（无修改），官方地址为 <https://www.bouncycastle.org/licence.html>。

| 文件 | 官方下载地址 | sha256（仓库内文件） |
|---|---|---|
| `licenses/Apache-2.0.txt` | <https://www.apache.org/licenses/LICENSE-2.0.txt> | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| `licenses/GPL-2.0.txt` | <https://www.gnu.org/licenses/gpl-2.0.txt> | `edaef632cbb643e4e7a221717a6c441a4c1a7c918e6e4d56debc3d8739b233f6` |
| `licenses/LGPL-3.0.txt` | <https://www.gnu.org/licenses/lgpl-3.0.txt> | `e3a994d82e644b03a792a930f574002658412f62407f5fee083f2555c5f23118` |
| `licenses/MPL-2.0.txt` | <https://www.mozilla.org/media/MPL/2.0/index.815ca599c9df.txt> | `fab3dd6bdab226f1c08630b1dd917e11fcb4ec5e1e020e2c16f83a0a13863e85` |
| `licenses/Zlib.txt` | <https://spdx.org/licenses/Zlib.txt> | `e21f5c0f66a0103c75f120a8864ef885d2f5922862d8ce168709451a2964972b` |
| `licenses/BSD-2-Clause.txt` | <https://spdx.org/licenses/BSD-2-Clause.txt>（下载原文 sha256 `ffcd6a8c421ee58d9f85b115ee0642805be3b497d2023565739622f044dc11e2`） | `0b86240ad7fea85ddf41452089afc233637d0879dc833cf425d73dac9fd4dee0` |
| `licenses/BSD-3-Clause.txt` | <https://spdx.org/licenses/BSD-3-Clause.txt>（下载原文 sha256 `0fe4dd6931c4c2fc418940de41074fa3c506cad24bfd891743ea7f2fcfe631ef`） | `7d4b8b9f72b9a4795b33e71ab9fcddd8df245b2a609aa82fd455270e103b70a8` |
| `licenses/Bouncy-Castle.txt` | <https://www.bouncycastle.org/licence.html> | c61027784b36d538c8ca196869d6a3e3f3937e3cd0e5869c38c1c10bdbb6723d |
| `licenses/pi2kotlin-MIT.txt` | <https://raw.githubusercontent.com/Sadami3066/pi2kotlin/d03325b82de8e1c0544758dd4f9d567bc1aeabfa/LICENSE> | `8a9a64220cdc0d7201389aeb34ffd627e73d160abf26ccfdc415928594ecbd53` |
| `licenses/lobehub-MIT.txt` | <https://raw.githubusercontent.com/lobehub/lobe-icons/680f1d7f95b5ae6a494fe43539da8a992f17b53d/LICENSE> | `add9d7531d1b21646317a8958e38fc727506fa39d24bdecb44154d943c82753a` |
| `licenses/lora-OFL.txt` | <https://raw.githubusercontent.com/google/fonts/main/ofl/lora/OFL.txt>（与 cyrealtype/Lora-Cyrillic 的 OFL.txt 字节一致） | `1d9a970809ac804b582a6ce7f0ebc4e7fefcbfd7ff6299cad35ee656a21be716` |
| `licenses/manrope-OFL.txt` | <https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/OFL.txt> | `58172e0c0fac2cda8a37b348164bb55e44b0e69051e557e92b1d3f6910141f7b` |
| `licenses/tabler-MIT.txt` | <https://raw.githubusercontent.com/tabler/tabler-icons/ed177a085b652fe04ec4f37ced867c94621e7840/LICENSE> | `b740a1d46122672da62833e97f7e7c8a13fa85cbc7445b584b297cc00dde93db` |
| `licenses/PRoot-GPL-2.0.txt` | <https://raw.githubusercontent.com/OpenMinis/proot/1b444ee1cb2f4896c08166e9a12c905af5416f1c/COPYING> | `0ffa6454acbe7e410ac6f0007df83b9b3161fe55484c1f4b87487c0f2d228a6d` |

注：`licenses/PRoot-GPL-2.0.txt` 是上游 PRoot 的 COPYING（GPL-2.0 全文 + PRoot/CARE 版权名单），不是 GNU 官方排版版本；GPL 官方文本另见 `licenses/GPL-2.0.txt`，两者内容均为 GPL-2.0。

## 8. 变更记录

- 2026-10-30：初版。覆盖 APK 内 PRoot/loader/Alpine、pi2kotlin SDK、AndroidX/Compose/Kotlin/kotlinx/Okio/WebKit、DataStore proto-lite、两款字体、Tabler/LobeHub/Radius 与厂商图标、mascot 插画；补齐 GPL/LGPL/MIT/OFL/Apache/MPL/Zlib/BSD 许可全文。
- 2026-10-31：新增内嵌无线调试依赖（libadb-android、spake2-android、Bouncy Castle、hiddenapibypass、OkHttp）；移除 Shizuku 依赖；补充 Bouncy Castle 许可全文与 JitPack/Maven 来源。
- 2026-11-01：pi2kotlin SDK 升级至 0.3.0，构件与来源提交同步更新。
- 2026-11-01：pi2kotlin SDK 升级至 0.4.0，构件与来源提交同步更新。
