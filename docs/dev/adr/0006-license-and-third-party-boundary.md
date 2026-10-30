# 0006 许可与第三方边界

- 状态：已采纳 · 2026-10-30 · 任务 M1-8（落地 M1-9；公开边界与品牌替换归 M4-3/M4-4）

## 背景

仓库按「随时可开源」维护（`CONTRIBUTING.md:3`），许可证与第三方边界必须现在定，因为**已发布/已分发版本上的许可不可撤销**：仓库一旦公开、APK 一旦发给他人，再改许可证只能对之后的新版本生效（`docs/dev/refactor/README.md §5 已定决策` 已定“Apache-2.0，仅覆盖自有代码；第三方按各自许可”）。

现状（M1-9 交付，文件在仓库根）：

- `LICENSE` = Apache-2.0 全文；`NOTICE` 声明“自有代码与文档 Apache-2.0，第三方组件按各自许可，不在 Apache-2.0 范围内”；`THIRD_PARTY_NOTICES.md` 是第三方总表。
- 随 APK 分发的第三方组件（可取证）：
  - **PRoot**：`shell/src/main/jniLibs/arm64-v8a/libproot.so`（GPL-2.0-or-later）、`libproot-loader.so`（GPL-2.0-or-later），构建时静态链接 talloc 2.4.2（LGPL-3.0-or-later）。许可全文 `shell/src/main/assets/licenses/PRoot-GPL-2.0.txt`，GPL/LGPL 文本在 `app/src/main/assets/licenses/`；完整对应源码、构建脚本、talloc 源码与书面要约见 `THIRD_PARTY_NOTICES.md §1.1-1.4`。
  - **Alpine minirootfs 3.24.2**：`shell/src/main/assets/alpine-minirootfs-3.24.2-aarch64.rootfs`（sha256 `9bf70a…`，与官方一致、未修改），16 个软件包含 GPL-2.0-only/MIT/Apache-2.0/MPL-2.0/Zlib/BSD 等；逐包许可表与 aports 源码指引见 `THIRD_PARTY_NOTICES.md §1.3`。
  - **pi2kotlin SDK**（MIT）、AndroidX/Compose/Kotlin/kotlinx/Okio/WebKit（Apache-2.0）、DataStore proto-lite（BSD-3-Clause）、三款字体（OFL-1.1）、Tabler/LobeHub 图标（MIT）见 `THIRD_PARTY_NOTICES.md §2-4`。
  - **非开源/商标素材**：Radius 图标（Earendil 品牌条款）、飞书/企业微信/微信图标（Apple App Store 公开图标，非开源）、`ic_plugin_feishu.png`（来源未记录）；42 个 provider 图标里 41 个 LobeHub MIT、10 个 vendor 图标中 6 个 Tabler MIT + 小米 Wikimedia PD。
  - **mascot**：`app/src/main/res/drawable-nodpi/mascot_welcome.webp`（AI 生成，参考图权利未明），不在 Apache-2.0 范围（`THIRD_PARTY_NOTICES.md §5`）。
- 代码事实（聚合论证的事实基础）：`:app` 只 import `:shell` 的 4 个类型（`LinuxEnvironment`/`LinuxInstallState`/`LinuxResult`/`LinuxShellExecutor`，共 17 处 import）；GPL 二进制不在 JVM 类路径，调用方式是 `ProcessBuilder` fork/exec（`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:184`、`:263`）；两个 `.so` 是独立可执行 ELF，rootfs 是数据 tarball。

必须现在决定的原因：① 许可证选型只有在“未公开、未分发”时是自由的（公开/发 APK/合外部 PR 后不可撤回）；② GPL 义务是**持续义务**，“聚合”论证需要靠履行义务支撑，缺许可文本或完整对应源码就退化成“主张聚合但义务没做全”；③ 品牌素材如果被默认当作代码资产，公开分发会直接踩商标/非开源素材；④ 对外表述一旦说成“整个 APK 是 Apache-2.0”，再纠正成本很高。

## 决定

**自有代码与文档采用 Apache-2.0；随包第三方组件分别适用各自许可证；PRoot/Alpine 以独立可执行文件与数据包形式聚合分发，逐项履行其义务；品牌素材与 mascot 不随代码许可；对外表述使用固定口径。**

落地口径：

1. **许可范围**：`LICENSE` 只覆盖本仓库自有代码与文档。第三方按 `THIRD_PARTY_NOTICES.md` 与 `app|design|shell/src/main/assets/licenses/` 的文本执行；“开源”不等于“整个 APK 一个许可证”。
2. **聚合而非派生**：PRoot/loader 是独立可执行程序，经 `fork/exec` 调用；rootfs 是数据包。GPL 义务只落在这些组件上，自有代码不受传染。
3. **GPL/LGPL 义务的履行清单**（缺一项即退化为不合规）：许可全文；完整对应源码（PRoot 源 commit + OpenMinis 的 `deps/build_proot.sh` + talloc 2.4.2 tarball + loader 源码）；Alpine aports v3.24.2 与逐包来源；修改声明（当前未修改）；书面要约（≥3 年，含联系方式）。
4. **品牌边界**：provider/vendor/插件图标只作识别性使用，不表示授权、合作或隶属；不授予任何商标许可。非开源素材（Radius、App Store 图标、来源未记录的飞书插件图标）在公开分发/商用前必须替换或取得授权（`THIRD_PARTY_NOTICES.md §4.6` 的替换预案）。mascot 同理：可随 APK 分发，但不纳入 Apache-2.0、不得单独再许可。
5. **对外固定口径**（`NOTICE:12`，不得简化成“整个 APK 是 Apache-2.0”）：

   > 本仓库自有代码采用 Apache-2.0。随 APK 分发的第三方组件（PRoot、Alpine、字体、图标、pi2kotlin SDK 等）分别适用各自许可证，见 THIRD_PARTY_NOTICES.md；品牌名称与图形归各权利人，本项目不授予任何商标许可。

6. **对公司/厂商口径**（`THIRD_PARTY_NOTICES.md §6`）：若政策不接受 GPL，可移除 `:shell` 模块，提供不含 GPL 组件的构建。

### GPL 聚合解释的性质（必须写清，不留“保证”错觉）

- 依据是 GPLv2 §2 末段允许把程序与非派生程序置于同一分发媒介，以及 FSF 对“经 fork/exec/管道/命令行通信的程序属独立作品”的通行立场；Android 生态长期有 proot/busybox 以可执行文件/数据包随宽松许可 App 分发的实例。
- 这是**通行解释，不是判例保证**：没有权威判例界定“一个 APK 是否构成单一作品”。若被主张整包为一个作品，后果是自有代码须按 GPLv3 发布（PRoot 是 GPL-2.0-or-later，整包可转 GPLv3），属于商业/交付决策风险，**不是**“必然侵权赔偿”。
- 聚合论证靠义务履行支撑：许可文本、完整对应源码、书面要约都做了，才叫“聚合”；只挂一个上游链接不算。

## 被否决的方案

- **整包 GPLv3**：GPLv3 §6 对 User Product 的 Installation Information 要求与 Android 签名/更新机制冲突（难以满足），且直接放弃闭源/厂商采用；不选。
- **AGPL-3.0**：本 App 没有面向用户的网络服务，网络条款几乎不触发，只增加解释成本与采用阻力；不选。
- **MIT（或默认无 LICENSE）**：MIT 无明示专利授权与商标排除条款，厂商法务采用阻力更大；public 而无 LICENSE = 默认“保留所有权利”的假开源，后补只对新版本生效；不选。
- **把 PRoot/Alpine 视为自有代码的一部分并统一换成宽松许可**：无权改上游许可；也不能靠“打包在一起”改变 GPL 义务。
- **把 mascot / 第三方图标纳入 Apache-2.0**：权利来源不明或非开源素材，授权超出权利范围；商标性使用不等于许可。
- **只保留上游链接、不提供构建脚本/talloc 源码**：不构成 GPL 的“完整对应源码”，这正是 M1-9 修掉的旧缺陷（旧版 `shell/src/main/assets/THIRD_PARTY_NOTICES.txt` 只列了 proot 仓库与 talloc 地址，没有构建脚本与逐包许可/源码指引）。
- **对外说“整个 APK 是 Apache-2.0”以降低沟通成本**：错误表述，且与 NOTICE 冲突；一律使用固定口径。

## 后果

- 代价：每次分发/发版都要维护 `LICENSE`/`NOTICE`/`THIRD_PARTY_NOTICES.md` 与应用内许可页；新增第三方组件、字体、图标必须同步登记来源与许可；GPL 的源码/书面要约是持续义务（不是一次性动作）。
- 品牌素材有明确的替换欠账：Radius 图标、3 个 App Store 图标、来源未记录的飞书插件图标在公开分发前必须处理（M4-4）；mascot 的来源/授权由项目所有者另行决定。
- 公开仓不能包含未授权素材：App Store 原图、mascot 参考图等只能在本地保留，仓库只入派生后的 PNG 与来源记录（`assets/` 光栅母版不入库，见 `.gitignore:16-19`）。
- **被永久驳回的评审建议**：把根 LICENSE 改成 GPL/MIT 以“简化”；对外宣传“APK 整体 Apache-2.0”；忽略 GPL 义务、只写一个上游链接；把 mascot 或厂商图标当作可随代码自由再许可的资产；以“私有仓暂时不用管许可”为由推迟 LICENSE/NOTICE（公开/分发时点不可逆）。
- 应用商店/商业分发若要求排除 GPL：走“无 `:shell` 的企业构建”，不改许可证——这是本 ADR 允许的分支，不是推翻。

## 推翻条件

1. **上游 PRoot/Alpine 被替换为 Apache/MIT 实现，或不再随包分发任何 GPL 组件** → 聚合论证与 GPL 义务消失，重估许可结构（自有代码仍可保持 Apache-2.0）。
2. **出现有约束力的判例或正式法务意见认定 APK 为单一作品** → 要么整包按 GPLv3 发布，要么移除 GPL 组件；本 ADR 必须新开一篇取代。
3. **商业分发明确要求排除 GPL** → 提供无 `:shell` 构建；不推翻 Apache-2.0 决定，但需要单独记录产品形态。
4. **品牌素材来源与授权补齐**（设计授权、替换为开源素材、mascot 权利明确）→ 更新品牌边界与 `THIRD_PARTY_NOTICES.md`；M4-4 执行。
5. **决定只发源码不发 APK** → 随包分发义务范围缩小，但 LICENSE/NOTICE 与“可构建性”要求仍需要（见 ADR-0007）。

## 修订记录

- 2026-10-30 · M5-9 校准：`:app` 引用 `:shell` 的 import 处数随 M2/M3 重构更新为 17；构建脚本标明上游仓库；未改决定。
