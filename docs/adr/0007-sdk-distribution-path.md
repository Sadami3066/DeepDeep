# 0007 SDK 分发路径：GitHub Release m2.zip → Maven Central

- 状态：已采纳 · 2026-10-30 · 任务 M1-8（第一步 M0-1/M0-2 已完成；第二步 M4-1 deferred）

## 背景

DeepDeep 依赖 pi2kotlin 发布的 `dev.pi:*` 构件（`:runtime`/`:shell` 使用）。分发现状是**两步走的第一步**，已经落地：

- `gradle/libs.versions.toml:13`：`pi = "0.2.4"`。
- 根 `build.gradle.kts:23-101`：`syncPiSdk` 任务从 `https://github.com/Sadami3066/pi2kotlin/releases/download/v0.2.4/pi2kotlin-0.2.4-m2.zip` 下载，校验 `gradle/pi-sdk.sha256`（`bc1e9fed229129e085079815ea4f0b5e9e1aeb9d72f2a72d8979fd2b865f0684`），解包到 `build/pi-sdk-m2`，并挂在所有子项目 `preBuild` 上（`:103-107`）。当前离线调试走 `-PpiSdkLocalZip=<zip>`（`:94-98`、`AGENTS.md:29`）。
- `settings.gradle.kts:12-23`：依赖解析只允许两个额外仓库——`mavenLocal`（content filter 只放行 `dev.pi`）与 `build/pi-sdk-m2`；其余走 `google()`/`mavenCentral()`。
- 实测：`build/pi-sdk-cache/pi2kotlin-0.2.4-m2.zip` 的 sha256 与 `gradle/pi-sdk.sha256` 一致；解出的 `build/pi-sdk-m2/dev/pi/*` 含 12 个模块 POM、4 个 AAR。
- M0 之前的问题（GL-004）：SDK 只在本机 `~/.m2`（由兄弟目录 `../pi2kotlin` 的 `publishToMavenLocal` 产生），新机器/CI 只 clone 本仓库必然构建失败；这是 M0 阶段 CI 无法复活的唯一硬阻塞（M0-1/M0-2 已解除）。

必须现在决定的原因：① CI 已在 M1-7 复活，`verify.sh` 会触发 `syncPiSdk`（`.github/workflows/ci.yml:41`），分发路径必须可从公网获取；② 第二步（M4-1）会改 `libs.versions.toml` 与 `settings.gradle.kts`，属“跨仓联动 + 供应链”变更，不提前记录就会有人顺手改坐标；③ 对外宣称“开源”时，第三方必须能构建（否则是“名义开源、实际不可构建”）。

## 决定

**SDK 分发两步走，第一步已执行、第二步等 v0.3：**

1. **过渡期（现在）**：pi2kotlin `v0.2.4` 由 GitHub Release 提供 m2 zip（含 4 个 AAR，由 HEAD `6cff93e` 重建，M0-1 验收）；DeepDeep 用 `syncPiSdk` 下载 → sha256 校验 → 解包成本地 Maven 仓库 → `preBuild` 依赖。离线/调试用 `-PpiSdkLocalZip`。
2. **v0.3（M4-1，human-approve）**：pi2kotlin 把 group 从 `dev.pi` 改为 `io.github.sadami3066`，发布到 Maven Central；DeepDeep 同步改 `libs.versions.toml` 坐标、删除 `mavenLocal`/`piSdkLocal` 仓库与 `syncPiSdk`（或仅保留离线路经）；验收 `mvn dependency:get` 可解析 `0.3.0`。
3. 两步之间**不新增**私有仓库、兄弟目录 `includeBuild`、或把 SDK 源码复制进本仓库。

为什么不能直接上 Central：Maven Central 的 groupId 需要证明命名空间所有权（自有域名校验，或 `io.github.<username>` 走 GitHub 校验）。`dev.pi` 对应域名 `pi.dev`，作者无法提供该域名的所有权证明；改用 `io.github.sadami3066` 才符合 Central 规则。**换的是 group 坐标，包名与模块名不变**（`dev.pi:pi-coding-agent-android` → `io.github.sadami3066:pi-coding-agent-android`）。

## 被否决的方案

- **维持 mavenLocal + 兄弟目录 `publishToMavenLocal`**：新机器/CI clone 必然失败（GL-004 的实证），开源后第三方不可构建；已由 M0-1/M0-2 替代。
- **直接以 `dev.pi` 发 Maven Central**：无法证明 groupId 命名空间所有权，Central 不接受；改 group 是唯一合规路径。
- **长期只用 GitHub Release m2.zip**：可复现性依赖 Release 资产（可被删除/覆盖）、外部使用者要自己解包成本地仓库、没有 Central 的不可变坐标与传递依赖解析；只适合过渡。
- **JitPack 打 tag**：第三方按需构建，产物非上游签名、依赖其缓存与 GitHub 可达性，坐标不可变性与审计弱于 Central；不选。
- **`includeBuild("../pi2kotlin")` 复合构建**（GL-004 建议里的写法）：仍需兄弟目录，不能独立 clone；KMP android variant 的 dependencySubstitution 未实测，风险高于收益。
- **把 SDK 源码复制进本仓库**：破坏版本独立性、许可证归属混乱、每次升级变成本仓大 diff。

## 后果

- 代价：过渡期构建依赖 GitHub Release 的可用性与网络；`syncPiSdk` 有 zip 缓存（`build/pi-sdk-cache/`）与 sha256 固定，但 Release 若被删除，新机器/CI 会失败直到第二步完成。第二步是跨仓联动：pi2kotlin 发版（签名/账号/release 流程）+ DeepDeep 改 catalog/settings，缺一不可。
- 过渡期 `mavenLocal` 仍被 content filter 放行 `dev.pi`：本地 `~/.m2` 里若有同名同版本构件会被优先命中（正是 M0 前的坑）。改动 SDK 版本时先清缓存或核实来源。
- **被永久驳回的评审建议**：用 `includeBuild` 兄弟目录作为“可复现构建”的主答案；把 `dev.pi` 直接上传公开仓/私有 Nexus 当最终方案；“README 改对版本号即可”的临时修法（M0-2 已用发布物替代，README 已同步 0.2.4）。
- 在完成第二步之前，对外只能声明“仓库可构建需要从本仓配置的 Release 获取 SDK”，不能宣称第三方可独立构建（与 ADR-0006 的“名义开源、实际不可构建”风险对应）。

## 推翻条件

1. **M4-1 完成且 Central `io.github.sadami3066:...:0.3.0` 可解析** → 第一步退役；`syncPiSdk` 移除或仅作离线兜底，本 ADR 标记为“被取代”。
2. **pi2kotlin 并入 DeepDeep 成为 `includeBuild`/源码模块**（上游不再独立发布）→ 重估分发形态。
3. **GitHub Release 不再可长期依赖**（仓库迁移、Release 被删、需要离线/审计更强的来源）→ 提前执行第二步，或增加镜像与本地缓存策略。
4. **Maven Central 规则或可用性变化**（例如必须强制 GPG/新发布门户、或需要私有分发）→ 重估第二步；私有 Nexus 需自建与维护成本，另开 ADR。
