# Third-Party Notices — Super PI Agent

Super PI Agent's own code is Copyright 2026 The Super PI Agent Authors, licensed under the Apache License 2.0 (see `LICENSE`).
Third-party components included in this repository and in the APK belong to their respective rights holders, are distributed under their own licenses, and are **not covered by Apache-2.0**.

- Full license texts in the repository: `app/src/main/assets/licenses/`, `design/src/main/assets/licenses/`, `shell/src/main/assets/licenses/`
- Path inside the APK: `assets/licenses/` (the directories above are merged at build time)
- Contact and GPL/LGPL source requests (written offer): <https://github.com/Sadami3066/DeepDeep/issues>
- This file is packaged into the APK as `assets/licenses/THIRD_PARTY_NOTICES.md`

## 0. Distribution Obligations at a Glance

Not legal advice; the full text of each license governs. The summary below covers whether a component can be used, whether it can stay closed source, and what redistribution requires:

| Component | License | Redistribution / closed-source use | Main obligations |
|---|---|---|---|
| PRoot, PRoot loader | GPL-2.0-or-later | May be aggregated and distributed with the app as standalone executables; PRoot's own code cannot be closed source | Include the full license text; provide the complete corresponding source (including the build script and talloc) or a valid written offer; state any modifications |
| The 8 GPL-2.0-only packages in the Alpine rootfs (busybox, apk-tools, etc.) | GPL-2.0-only | The rootfs is aggregated and distributed as a data package; the code of these packages cannot be closed source | Include the full license text; provide the corresponding source (aports v3.24.2 and upstream tarballs) |
| talloc 2.4.2 | LGPL-3.0-or-later | May be linked and distributed with proprietary programs (static linking requires providing the materials needed to relink) | Include the full license text; provide the library source; do not restrict user replacement/relinking |
| pi2kotlin SDK | MIT | Closed-source use and redistribution permitted | Keep the copyright notice and the full license text |
| AndroidX / Compose / Kotlin / kotlinx.coroutines / Okio / WebKit | Apache-2.0 | Closed-source use and redistribution permitted | Keep a copy of the license; keep any upstream NOTICE file |
| libadb-android 3.1.1 / AdbLib | Apache-2.0 / BSD-3-Clause | Closed-source use and redistribution permitted | Keep the copyright notices and full license texts; this project uses it under Apache-2.0 |
| spake2-android 2.2.1 | LGPL-3.0 | May be linked and distributed with the app; requires providing the library source and relinking conditions | Include the full license text; provide the source; do not restrict user replacement/relinking |
| Bouncy Castle 1.86 (bcprov/bcpkix/bcutil) | Bouncy Castle License (MIT-style) | Closed-source use and redistribution permitted | Keep the copyright notice and the full license text |
| hiddenapibypass 6.1 | Apache-2.0 | Closed-source use and redistribution permitted | Keep a copy of the license |
| OkHttp 5.4.0 | Apache-2.0 | Closed-source use and redistribution permitted | Keep a copy of the license |
| DataStore repackaged proto-lite | BSD-3-Clause | Closed-source use and redistribution permitted | Keep the copyright notice and the full license text |
| Lora / Manrope / Open Runde / Fredoka | OFL-1.1 | May be embedded and distributed with the app | Keep the copyright and the full license text; the fonts may not be sold separately |
| Tabler Icons / LobeHub lobe-icons | MIT | May be redistributed | Keep the copyright notice and the full license text |
| Radius icon, App Store public icons, Feishu plugin icon | Non-open-source assets / brand terms | For identifying the corresponding service or vendor only; replace it or obtain a license before large-scale distribution or commercial use | Trademark attribution; must not imply authorization, partnership, or affiliation |

## 1. Linux Environment Components Distributed with the APK

### 1.1 PRoot — `lib/arm64-v8a/libproot.so`

- License: GNU General Public License v2 or later (GPL-2.0-or-later)
- Copyright line: `Copyright (C) 2015 STMicroelectronics` (self-described in the binary); the upstream COPYING additionally lists the PRoot/CARE contributors
- File sha256 inside this APK: `27c35e02cee0dd6fea9d81566741dc7d5064f2d158208e4b8a1395131c6fda34`
- Path in this repository: `shell/src/main/jniLibs/arm64-v8a/libproot.so` (APK: `lib/arm64-v8a/libproot.so`)
- Complete corresponding source:
  - PRoot (OpenMinis Android fork): <https://github.com/OpenMinis/proot/tree/1b444ee1cb2f4896c08166e9a12c905af5416f1c>
  - Build script: <https://github.com/OpenMinis/OpenMinis/blob/b4c0661d5631ebab4d1a2e6f3fd4c805d4030a6c/deps/build_proot.sh>
    (sha256 `0c42a65d6ebe1be2573880673178ae1b19abadddbe61ea52b9227afc41e7f116`, 490 lines; Android NDK r28c 28.2.13676358, `ANDROID_API=26`, `aarch64-linux-android`; the script notes that output built with a different NDK/clang version is not byte-level reproducible)
  - talloc 2.4.2 (statically linked into `libproot.so`): <https://download.samba.org/pub/talloc/talloc-2.4.2.tar.gz>
    (sha256 `85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6`)
- talloc license: LGPL-3.0-or-later (the `talloc.c`/`talloc.h` headers state "version 3 ... or any later version")
- talloc copyright lines: `Copyright (C) Andrew Tridgell 2004`, `Copyright (C) Stefan Metzmacher 2006`
- Modification statement: this repository contains no patches to the PRoot or talloc sources; the distributed binaries come from the upstream sources and build script above (the script notes that the output is not byte-level reproducible, so the distributed files are pinned by sha256 and the sources plus script satisfy the complete corresponding source obligation).
- Full license texts: `licenses/GPL-2.0.txt` (official GNU text), `licenses/LGPL-3.0.txt`; the upstream PRoot COPYING is also stored as `shell/src/main/assets/licenses/PRoot-GPL-2.0.txt`
  (byte-identical to the COPYING in OpenMinis/proot, sha256 `0ffa6454acbe7e410ac6f0007df83b9b3161fe55484c1f4b87487c0f2d228a6d`)

### 1.2 PRoot loader — `lib/arm64-v8a/libproot-loader.so`

- License: GPL-2.0-or-later (PRoot component)
- Copyright line: `Copyright (C) 2015 STMicroelectronics` (PRoot); the loader implementation comes from the Termux PRoot project
- File sha256 inside this APK: `44ef39c1e1a18c09f6e4c4b5d6f8bba82d30596598bd155ec162d05c5122ff04`
- Path in this repository: `shell/src/main/jniLibs/arm64-v8a/libproot-loader.so` (APK: `lib/arm64-v8a/libproot-loader.so`)
- Source: Termux PRoot loader source <https://github.com/termux/proot/tree/master/src/loader>
- Binary source (identical to it): the vendored copy in the OpenMinis repository
  <https://github.com/OpenMinis/OpenMinis/blob/b4c0661d5631ebab4d1a2e6f3fd4c805d4030a6c/src/android/app/src/main/jniLibs/arm64-v8a/libproot-loader.so>
  Line 375 of the build script pins this file by that sha256 (script sha256 see 1.1).
- Full license text: `licenses/GPL-2.0.txt`

### 1.3 Alpine Linux minirootfs 3.24.2 (aarch64)

- Path in this repository: `shell/src/main/assets/alpine-minirootfs-3.24.2-aarch64.rootfs` (APK: `assets/alpine-minirootfs-3.24.2-aarch64.rootfs`)
- File sha256: `9bf70a7f18ea44094cbb5f70c58f9af129c8214745743db0e68e5502cc2ce773`, 4,028,030 bytes, identical to the official release (unmodified)
- Official release and checksum: <https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/> (`alpine-minirootfs-3.24.2-aarch64.tar.gz.sha256`)
- Copyright line: each Alpine package is copyright its upstream authors; per-package build recipes and source references are in aports tag v3.24.2
- Packages and licenses (source: `/lib/apk/db/installed` in the rootfs; 16 packages in total):

| License | Packages |
|---|---|
| GPL-2.0-only | alpine-baselayout 3.7.2-r1, alpine-baselayout-data 3.7.2-r1, apk-tools 3.0.8-r0, libapk 3.0.8-r0, busybox 1.37.0-r31, busybox-binsh 1.37.0-r31, scanelf 1.3.9-r1, ssl_client 1.37.0-r31 |
| MIT | musl 1.2.6-r2, alpine-keys 2.6-r0, alpine-release 3.24.2-r0 |
| MIT AND BSD-2-Clause AND GPL-2.0-or-later | musl-utils 1.2.6-r2 |
| Apache-2.0 | libcrypto3 3.5.8-r0, libssl3 3.5.8-r0 |
| MPL-2.0 AND MIT | ca-certificates-bundle 20260909-r0 |
| Zlib | zlib 1.3.2-r0 |

- Corresponding source: Alpine aports tag v3.24.2 <https://github.com/alpinelinux/aports/tree/v3.24.2>, plus the upstream tarballs referenced by each APKBUILD.
- Full license texts: `licenses/GPL-2.0.txt`, `licenses/Apache-2.0.txt`, `licenses/MPL-2.0.txt`, `licenses/Zlib.txt`, `licenses/BSD-2-Clause.txt`
- Note: this rootfs has no `/usr/share/licenses` directory (verified against the tar contents; `/usr/share` exists but has no `licenses` subdirectory). Alpine runtime packages do not ship license files by default, so the app provides the license texts in `assets/licenses/`, and the complete corresponding source for the GPL components is available under the written offer in section 1.4.

### 1.4 Written Offer (GPL / LGPL Corresponding Source)

For at least three years from the date this APK was first distributed, any third party may request the complete corresponding source for the GPL/LGPL components listed in sections 1.1–1.3 at no charge through <https://github.com/Sadami3066/DeepDeep/issues>: the OpenMinis/proot source snapshot, the `deps/build_proot.sh` build script, the Termux PRoot loader source, the talloc 2.4.2 source tarball, Alpine aports v3.24.2, and the upstream source of each package; if provided on physical media, only the cost of the media and shipping is charged.

## 2. Code Libraries Compiled into the APK

### 2.1 pi2kotlin SDK 0.6.3 (DeepDeep-KotlinSDK)

- Artifacts: `com.github.Sadami3066.DeepDeep-KotlinSDK:pi-coding-agent-android:v0.6.3`, `pi-ai-android`, `pi-agent-android`, `pi-mcp-android` (JitPack build, version tag `v0.6.3`)
- Copyright lines: `Copyright (c) 2026 pi2kotlin contributors`, `Copyright (c) 2025 Mario Zechner and pi contributors`
- License: MIT
- Source: <https://github.com/Sadami3066/DeepDeep-KotlinSDK> tag `v0.6.3`, commit `df388d6d737ee079303e4e4acb5776d9ee62746f` (`LICENSE`)
- Declared in this repository: `gradle/libs.versions.toml` (`pi = "v0.6.3"`; dependency coordinates in `[libraries]` in the same file)
- Full license text: `licenses/pi2kotlin-MIT.txt`
- Note: the upstream AAR/POM carries no LICENSE/NOTICE metadata; this entry provides the copyright and license notices required when distributing the binary.

### 2.2 AndroidX / CameraX / Jetpack Compose / Kotlin / kotlinx.coroutines / Okio / WebKit

- Artifacts and versions (the Maven coordinates pin the version; artifacts are immutable):
  - AndroidX: `androidx.core:core-ktx:1.17.0`, `androidx.activity:activity-compose:1.12.4`, `androidx.lifecycle:*:2.10.0`, `androidx.datastore:datastore-preferences:1.2.0`, `androidx.webkit:webkit:1.18.0`
  - CameraX: `androidx.camera:camera-camera2:1.6.2`, `androidx.camera:camera-lifecycle:1.6.2`, `androidx.camera:camera-compose:1.6.2`
  - Jetpack Compose: BOM `2026.03.00` (`androidx.compose.*`)
  - Kotlin stdlib: `2.4.21`
  - kotlinx.coroutines: `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0`, `kotlinx-coroutines-android:1.11.0`
  - Okio: `com.squareup.okio:okio:3.18.2`
- Copyright lines:
  - AndroidX / Compose / androidx.webkit: `Copyright (C) The Android Open Source Project`
  - Kotlin: `Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors` (years vary per upstream file)
  - kotlinx.coroutines: `Copyright JetBrains s.r.o.`
  - Okio: `Copyright (C) Square, Inc.` (years vary per file)
- License: Apache-2.0
- Sources (pinned versions/tags):
  - <https://maven.google.com> (version coordinates for `androidx.*` and `androidx.compose.*`; AndroidX source at <https://cs.android.com/androidx/platform/frameworks/support>)
  - <https://github.com/JetBrains/kotlin> tag `v2.4.21`
  - <https://github.com/Kotlin/kotlinx.coroutines> tag `1.11.0`
  - <https://github.com/square/okio> tag `parent-3.18.2`
  - Note: kotlin-stdlib and some androidx artifacts carry the Apache text into the APK (`META-INF/**/LICENSE.txt`); the kotlinx.coroutines and Okio artifacts carry no text, so this entry supplies it.
- Declared in this repository: `gradle/libs.versions.toml`
- Full license text: `licenses/Apache-2.0.txt`

### 2.3 DataStore repackaged proto-lite

- Artifact: `androidx.datastore:datastore-preferences-external-protobuf:1.2.0` (upstream description "Repackaged proto-lite dependency for use by datastore preferences")
- Copyright line: `Copyright 2008 Google Inc.` (upstream protobuf/proto-lite implementation; the androidx repackaged artifact declares no separate copyright line)
- License: BSD-3-Clause (as declared in the artifact POM)
- Source: AndroidX DataStore 1.2.0 (<https://developer.android.com/jetpack/androidx/releases/datastore#1.2.0>), source <https://cs.android.com/androidx/platform/frameworks/support>; upstream protobuf <https://github.com/protocolbuffers/protobuf>
- License text location: `META-INF/androidx/datastore/datastore-preferences-external-protobuf/LICENSE.txt` inside the APK (the BSD-3-Clause text is retained in the APK); supplementary full text `licenses/BSD-3-Clause.txt`; version declaration `gradle/libs.versions.toml` (`datastore = "1.2.0"`)

### 2.4 Embedded Wireless Debugging (ADB) Dependencies

- `libadb-android:3.1.1` (JitPack; the upstream is dual-licensed `GPL-3.0-or-later OR Apache-2.0`, and this project uses it under Apache-2.0)
  - Copyright: `Copyright 2013 Cameron Gutman` (AdbLib part, BSD-3-Clause), `Copyright 2020-2022 Muntashir Al-Islam` (libadb part)
  - Source: <https://github.com/MuntashirAkon/libadb-android> tag `3.1.1`, <https://jitpack.io/#MuntashirAkon/libadb-android/3.1.1>
  - Use: TLS connection, pairing, and `shell:` streams for wireless debugging; no code changes
  - Full license texts: `licenses/Apache-2.0.txt`, `licenses/BSD-3-Clause.txt`
- `spake2-android:2.2.1` (JitPack; LGPL-3.0; `libspake2.so`)
  - Copyright: `Copyright 2021 Muntashir Al-Islam`; the underlying Ed25519/ref10 and BoringSSL SPAKE2 follow their respective upstream licenses
  - Source: <https://github.com/MuntashirAkon/spake2-java> tag `2.2.1` (`spake2-c` submodule <https://github.com/MuntashirAkon/spake2-c>)
  - Use: SPAKE2 key exchange for the system wireless debugging pairing code
  - Full license text: `licenses/LGPL-3.0.txt`; complete corresponding source under the written offer in section 1.4
- `org.bouncycastle:bcprov-jdk15to18:1.86`, `bcpkix-jdk15to18:1.86`, `bcutil-jdk15to18:1.86` (Maven Central; Bouncy Castle License)
  - Copyright: `Copyright (c) 2000-2023 The Legion of the Bouncy Castle Inc.`
  - Source: <https://www.bouncycastle.org/>, <https://repo1.maven.org/maven2/org/bouncycastle/>
  - Use: self-signed ADB X.509 certificates, AES-GCM/HKDF pairing, and ADB public key encoding
  - Full license text: `licenses/Bouncy-Castle.txt`
- `org.lsposed.hiddenapibypass:hiddenapibypass:6.1` (Maven Central; Apache-2.0)
  - Copyright: LSPosed project contributors
  - Source: <https://github.com/LSPosed/AndroidHiddenApiBypass>
  - Use: reading the platform Conscrypt TLS keying material during pairing (Android hidden API exemption)
  - Full license text: `licenses/Apache-2.0.txt`
- `com.squareup.okhttp3:okhttp:5.4.0` (Maven Central; Apache-2.0)
  - Copyright: `Copyright 2019 Square, Inc.`
  - Source: <https://github.com/square/okhttp> tag `parent-5.4.0`
  - Use: HTTP loopback access to the on-device daemon
  - Full license text: `licenses/Apache-2.0.txt`

### 2.5 ML Kit Barcode Scanning (Not Open Source)

- Artifact: `com.google.mlkit:barcode-scanning:17.3.0` (bundled-model variant; the code and model are distributed with the APK)
- Copyright and license: Google ML Kit Terms of Service, not open source; Apache-2.0 does not apply, and the ML Kit code and model in the APK are used under Google's terms.
- Source: <https://developers.google.com/ml-kit/terms>, <https://maven.google.com> (`com.google.mlkit:barcode-scanning:17.3.0`)
- Use: on-device barcode recognition for camera scanning; runs offline and does not depend on Google Play services
- Declared in this repository: `gradle/libs.versions.toml` (`mlkit-barcode = "17.3.0"`)

### 2.6 Build and Test Only, Not Distributed with the APK

Android Gradle Plugin 9.4.1, the Kotlin compiler, JUnit 4.13.2, commons-compress 1.28.0, androidx.test.runner 1.7.0, androidx.test.ext:junit 1.3.0, and the Android SDK/build-tools. They are used on build machines or in test APKs, are not part of what this APK distributes, and are outside the scope of this notice.

## 3. Fonts

The fonts ship with the APK under `res/font/`; source files are in `design/src/main/res/font/` and license texts in `design/src/main/assets/licenses/`.

### 3.1 Lora — `design/src/main/res/font/lora_variable.ttf`

- Copyright line: `Copyright 2011 The Lora Project Authors (https://github.com/cyrealtype/Lora-Cyrillic), with Reserved Font Name "Lora".`
- License: SIL Open Font License 1.1 (`licenses/lora-OFL.txt`, taken from Google Fonts `ofl/lora/OFL.txt`)
- Source: Google Fonts `ofl/lora` <https://github.com/google/fonts/tree/main/ofl/lora>; upstream author repository <https://github.com/cyrealtype/Lora-Cyrillic>
- File sha256: `822a6621ccbe8d97d20ac88c1c41f5615c9c2c202eaa75f272cd452aac6475a7`, byte-identical to Google Fonts `Lora[wght].ttf`, **unmodified**.

### 3.2 Manrope — `design/src/main/res/font/manrope_variable.ttf`

- Copyright line: `Copyright 2018 The Manrope Project Authors (https://github.com/googlefonts/manrope)` (with the accompanying OFL text; the font name table says 2019, the two upstream years disagree, and neither was changed)
- License: SIL Open Font License 1.1 (`licenses/manrope-OFL.txt`, byte-identical to Google Fonts `ofl/manrope/OFL.txt`)
- Source: Google Fonts `ofl/manrope` <https://github.com/google/fonts/tree/main/ofl/manrope>
- File sha256: `3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6`, byte-identical to Google Fonts `Manrope[wght].ttf`, **unmodified**.

### 3.3 Open Runde — `design/src/main/res/font/open_runde_regular.otf`, `open_runde_medium.otf`, `open_runde_semibold.otf`

- Copyright line: the font name table says `Copyright 2023 Laurids Kern`; the accompanying OFL text begins with `Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)` (Open Runde derives from Inter, and the OFL text is kept verbatim from upstream, not rewritten)
- License: SIL Open Font License 1.1 (`licenses/open-runde-OFL.txt`, taken from `LICENSE.txt` in the upstream repository)
- Source: <https://github.com/lauridskern/open-runde>, commit `3e7ed7f3cdfa5523766db7e430066472615fc935`, path `src/desktop/`; upstream is based on Inter <https://github.com/rsms/inter>
- File sha256:
  - `open_runde_regular.otf`: `8398c8adfc2b41a21cd1c0e00dbbd81c2ce3877cc5c9843bebd3e0b7b43cfbcc` (312,956 bytes)
  - `open_runde_medium.otf`: `163b5e80e397da746081f94ee0a2314d17d256f2ee4a06998f48ce372b56f056` (323,716 bytes)
  - `open_runde_semibold.otf`: `39d6c706cfbb9cad2a7ea2bfdbe12c4384185d8625a21208a39cbdfe1ea1790c` (315,896 bytes)
  All are byte-identical to the upstream files at that commit, **unmodified**.

### 3.4 Fredoka — `design/src/main/res/font/fredoka_variable.ttf`

- Copyright line: `Copyright 2016 The Fredoka Project Authors (https://github.com/hafontia/Fredoka-One)`
- License: SIL Open Font License 1.1 (`licenses/fredoka-OFL.txt`, taken from Google Fonts `ofl/fredoka/OFL.txt`)
- Source: Google Fonts `ofl/fredoka` <https://github.com/google/fonts/tree/main/ofl/fredoka>
- Font type: variable font, axes `wght` 300–700 and `wdth` 75–125
- File sha256: `2ba02e68b152868aef9ba28e24b3648c7d457fe6f25c761f2c2c53fb61a73fc8`, 159,184 bytes, byte-identical to Google Fonts `Fredoka[wdth,wght].ttf`, **unmodified**.

## 4. Icons and Brand Graphics

The graphics are used only to identify the corresponding service, vendor, or feature and **do not imply any authorization, partnership, or affiliation**; brand names and graphics belong to their respective owners.

### 4.1 Tabler Icons — `design/src/main/res/drawable/*.xml` (51 VectorDrawables)

- License: MIT
- Copyright line: `Copyright (c) 2020-2026 Paweł Kuna`
- Source: snapshot v3.50.0, commit `ed177a085b652fe04ec4f37ced867c94621e7840` (<https://github.com/tabler/tabler-icons/tree/ed177a085b652fe04ec4f37ced867c94621e7840>); SVG converted to Android VectorDrawable, geometry unchanged
- Full license text: `licenses/tabler-MIT.txt` (byte-identical to the upstream `LICENSE`)

### 4.2 LobeHub lobe-icons — `app/src/main/res/drawable-nodpi/provider_*.png` and `app/src/main/res/drawable-night-nodpi/provider_*.png` (41 of the 42 provider icons)

- License: MIT (icon implementation); brand graphics belong to their respective brand owners
- Copyright line: `Copyright (c) 2023 LobeHub`
- Source: commit `680f1d7f95b5ae6a494fe43539da8a992f17b53d` (<https://github.com/lobehub/lobe-icons/tree/680f1d7f95b5ae6a494fe43539da8a992f17b53d>); the 41 providers reuse 30 distinct graphics (of the 42 providers, only Radius comes from another source)
- Full license text: `licenses/lobehub-MIT.txt` (byte-identical to the upstream `LICENSE`)

### 4.3 Radius — `app/src/main/res/drawable-nodpi/provider_radius.png`

- License: the third-party service identification terms in the Earendil brand guidelines, **not an open-source license**
- Copyright line / owner: Earendil (the Radius brand)
- Source: the Earendil `elanor` icon source, commit `f9e904c08d2e2274714f35b9c848ba7bab19e2b3`
  <https://github.com/earendil-works/elanor/blob/f9e904c08d2e2274714f35b9c848ba7bab19e2b3/packages/logos/sources/radius/mark.svg>
- Handling: the official graphic, color tokens, and clear space of one quarter of the logo width are retained; the graphic is unchanged.

### 4.4 Vendor Icons — `app/src/main/res/drawable-nodpi/vendor_*.png` (10)

| Icon | Source | License / rights |
|---|---|---|
| dingtalk, github, notion, slack, telegram, discord | Tabler Icons brand icons, commit `ed177a085b652fe04ec4f37ced867c94621e7840` (v3.50.0) | MIT, `Copyright (c) 2020-2026 Paweł Kuna`; `licenses/tabler-MIT.txt` |
| xiaomi | Wikimedia Commons [File:Xiaomi_logo.svg](https://commons.wikimedia.org/wiki/File:Xiaomi_logo.svg) (from Xiaomi's official media kit) | Public domain (simple geometric/text combination, below the threshold of copyright originality); "Xiaomi"/"mi" are trademarks of Xiaomi Corporation |
| feishu | Apple App Store public app icon (iTunes Search API `artworkUrl512`, China storefront, 2026-10-29): <https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/45/f4/d8/45f4d8ef-fc19-8e6e-19eb-4e1e4ee1aabd/AppIcon-0-0-1x_U007epad-0-0-0-1-0-0-85-220.png/512x512bb.jpg> | **Non-open-source asset**; the graphic and trademark belong to Beijing Feishu Technology Co., Ltd. (ByteDance) |
| wecom | same as above (WeCom): <https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/3e/ba/6b/3eba6be1-a7fd-00c5-7f0d-2415c8258ef5/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/512x512bb.jpg> | **Non-open-source asset**; the graphic and trademark belong to Tencent Technology (Shenzhen) Company Limited |
| wechat | same as above (WeChat): <https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/84/6b/3a/846b3ab8-9dea-e3e5-861e-f6ecaa0b3387/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-0-0-85-220.png/512x512bb.jpg> | **Non-open-source asset**; the graphic and trademark belong to Tencent Technology (Shenzhen) Company Limited |

### 4.5 Feishu Plugin Icon — `app/src/main/res/drawable-nodpi/ic_plugin_feishu.png`

- Content: Feishu brand graphic (96×96)
- Copyright line / owner: the Feishu brand; the trademark belongs to Beijing ByteDance Technology Co., Ltd.
- Source: **non-open-source asset**; this repository kept no record of the original download (the file was introduced in commit `41b6885`)
- Handling: before public distribution it should be replaced with a traceable source consistent with 4.4, or with licensed artwork.

### 4.6 Plan for Replacing Non-Open-Source Assets

Before public distribution or commercial use, the following non-open-source assets should be replaced with licensed assets or removed from the release:
`provider_radius.png` (Radius brand terms), `vendor_feishu.png`, `vendor_wecom.png`, `vendor_wechat.png` (App Store public icons), `ic_plugin_feishu.png` (source not recorded).
When replacing them, update the corresponding entries and the asset list in this file.

## 5. Aggregation and Commercial Use (for Companies and Vendors)

> The app's own code is Apache-2.0. The Linux environment components (PRoot, Alpine) are aggregated and distributed as standalone executables and data packages under GPL-2.0/MIT/Apache-2.0 and other licenses respectively, and their source and build scripts are available from the addresses listed in this notice. If your policy does not accept GPL: `:shell` is a separate module, so you can remove the Linux features and use the rest in closed source; we can also provide an enterprise build without GPL components.

## 6. License Text Sources and Verification

Every file below is a direct download from the official release address (terms not rewritten); the SPDX texts for BSD-2-Clause/BSD-3-Clause contain a template variable, which has been expanded with the variable's `original=` value and the line endings normalized from CRLF to LF, with all other bytes unchanged. `licenses/Bouncy-Castle.txt` is a verbatim transcription of the upstream Bouncy Castle license text (unmodified); the official address is <https://www.bouncycastle.org/licence.html>.

| File | Official download address | sha256 (file in repository) |
|---|---|---|
| `licenses/Apache-2.0.txt` | <https://www.apache.org/licenses/LICENSE-2.0.txt> | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| `licenses/GPL-2.0.txt` | <https://www.gnu.org/licenses/gpl-2.0.txt> | `edaef632cbb643e4e7a221717a6c441a4c1a7c918e6e4d56debc3d8739b233f6` |
| `licenses/LGPL-3.0.txt` | <https://www.gnu.org/licenses/lgpl-3.0.txt> | `e3a994d82e644b03a792a930f574002658412f62407f5fee083f2555c5f23118` |
| `licenses/MPL-2.0.txt` | <https://www.mozilla.org/media/MPL/2.0/index.815ca599c9df.txt> | `fab3dd6bdab226f1c08630b1dd917e11fcb4ec5e1e020e2c16f83a0a13863e85` |
| `licenses/Zlib.txt` | <https://spdx.org/licenses/Zlib.txt> | `e21f5c0f66a0103c75f120a8864ef885d2f5922862d8ce168709451a2964972b` |
| `licenses/BSD-2-Clause.txt` | <https://spdx.org/licenses/BSD-2-Clause.txt> (downloaded file sha256 `ffcd6a8c421ee58d9f85b115ee0642805be3b497d2023565739622f044dc11e2`) | `0b86240ad7fea85ddf41452089afc233637d0879dc833cf425d73dac9fd4dee0` |
| `licenses/BSD-3-Clause.txt` | <https://spdx.org/licenses/BSD-3-Clause.txt> (downloaded file sha256 `0fe4dd6931c4c2fc418940de41074fa3c506cad24bfd891743ea7f2fcfe631ef`) | `7d4b8b9f72b9a4795b33e71ab9fcddd8df245b2a609aa82fd455270e103b70a8` |
| `licenses/Bouncy-Castle.txt` | <https://www.bouncycastle.org/licence.html> | c61027784b36d538c8ca196869d6a3e3f3937e3cd0e5869c38c1c10bdbb6723d |
| `licenses/pi2kotlin-MIT.txt` | <https://raw.githubusercontent.com/Sadami3066/DeepDeep-KotlinSDK/94f300abef96516204358a8fe549f9d774c373df/LICENSE> | `8a9a64220cdc0d7201389aeb34ffd627e73d160abf26ccfdc415928594ecbd53` |
| `licenses/lobehub-MIT.txt` | <https://raw.githubusercontent.com/lobehub/lobe-icons/680f1d7f95b5ae6a494fe43539da8a992f17b53d/LICENSE> | `add9d7531d1b21646317a8958e38fc727506fa39d24bdecb44154d943c82753a` |
| `licenses/lora-OFL.txt` | <https://raw.githubusercontent.com/google/fonts/main/ofl/lora/OFL.txt> (byte-identical to the OFL.txt in cyrealtype/Lora-Cyrillic) | `1d9a970809ac804b582a6ce7f0ebc4e7fefcbfd7ff6299cad35ee656a21be716` |
| `licenses/manrope-OFL.txt` | <https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/OFL.txt> | `58172e0c0fac2cda8a37b348164bb55e44b0e69051e557e92b1d3f6910141f7b` |
| `licenses/open-runde-OFL.txt` | <https://raw.githubusercontent.com/lauridskern/open-runde/3e7ed7f3cdfa5523766db7e430066472615fc935/LICENSE.txt> | `262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a` |
| `licenses/fredoka-OFL.txt` | <https://raw.githubusercontent.com/google/fonts/main/ofl/fredoka/OFL.txt> | `5c9e7eee5c6b25f4b05b8d53b2e470ea4962f9ced742d044a98f7d95d1375bab` |
| `licenses/tabler-MIT.txt` | <https://raw.githubusercontent.com/tabler/tabler-icons/ed177a085b652fe04ec4f37ced867c94621e7840/LICENSE> | `b740a1d46122672da62833e97f7e7c8a13fa85cbc7445b584b297cc00dde93db` |
| `licenses/PRoot-GPL-2.0.txt` | <https://raw.githubusercontent.com/OpenMinis/proot/1b444ee1cb2f4896c08166e9a12c905af5416f1c/COPYING> | `0ffa6454acbe7e410ac6f0007df83b9b3161fe55484c1f4b87487c0f2d228a6d` |

Note: `licenses/PRoot-GPL-2.0.txt` is the upstream PRoot COPYING (the full GPL-2.0 text plus the PRoot/CARE copyright list), not the official GNU typeset version; the official GPL text is in `licenses/GPL-2.0.txt`, and both contain GPL-2.0.

## 7. Change Log

- 2026-10-30: First version. Covered PRoot/loader/Alpine in the APK, pi2kotlin SDK, AndroidX/Compose/Kotlin/kotlinx/Okio/WebKit, DataStore proto-lite, two fonts, Tabler/LobeHub/Radius and vendor icons; added the full GPL/LGPL/MIT/OFL/Apache/MPL/Zlib/BSD license texts.
- 2026-10-31: Added the embedded wireless debugging dependencies (libadb-android, spake2-android, Bouncy Castle, hiddenapibypass, OkHttp); removed the Shizuku dependency; added the full Bouncy Castle license text and JitPack/Maven sources.
- 2026-11-01: Upgraded pi2kotlin SDK to 0.3.0, updating artifacts and source commits.
- 2026-11-01: Upgraded pi2kotlin SDK to 0.4.0, updating artifacts and source commits.
- 2026-11-05: Added two OFL-1.1 fonts, Open Runde (Regular/Medium/Semibold, static OTF) and Fredoka (`wght`/`wdth` variable TTF), with copyright lines, upstream sources and commits, file sha256 values, and full license texts; made Open Runde the default font.
- 2026-11-09: Upgraded pi2kotlin SDK to 0.6.2 (JitPack coordinate `com.github.Sadami3066.DeepDeep-KotlinSDK`), updating artifacts, source commits, and version declarations.
- 2026-11-11: Upgraded pi2kotlin SDK to 0.6.3 (tag `v0.6.3`, commit `df388d6d737ee079303e4e4acb5776d9ee62746f`), updating artifact coordinates and version declarations.
