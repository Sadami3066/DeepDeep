# 0005 真机证据做行为验收，CI 只做机器闸门

- 状态：已采纳 · 2026-10-30 · 任务 M1-8（相关 M1-5、M1-7）

## 背景

两道验证机制在同一个月落地，容易被误读成“二选一”，实际是分工：

**机器闸门（M1-7 已落地）**
- `.github/workflows/ci.yml`：push `master` 与所有 PR 触发，跑 `./scripts/verify.sh`（`:3-6`、`:40-41`），失败上传测试/lint 报告（`:43-52`）；master 分支保护已随 M1-7 启用。
- `scripts/verify.sh:6` 最后一行 `exec "$(dirname "$0")/gradlew-locked" check :app:assembleDebug`——全局串行锁与默认 `--max-workers=1` 在 `scripts/gradlew-locked:5-15`；`check` = 编译 + 单测（基线见 `docs/dev/refactor/BASELINE.md`、0 skip）+ lint（0 error）+ 模块边界 + 包边界。
- 它**不跑** `androidTest`、不启动模拟器、无真机。

**真机证据链（M1-5 已落地）**
- `docs/dev/ACCEPTANCE.md`：**191 条**稳定 ID（`ACC-<域>-<序号>`），每条固定三段「前置 → 操作 → 可见判据（看到什么算过、看到什么算失败）」；统计对照表列出 178 active / 12 obsolete / 1 blocked，且与 `docs/archive/device-testing-rounds.md`（已归档）的 191 条旧流水号逐条对应；`docs/dev/DEVICE_TESTING.md` 只保留设备与证据采集操作。PR、提交、证据目录只引用 ID，不复制正文。
- `docs/dev/evidence/README.md`：目录 `docs/dev/evidence/<YYYY-MM-DD>-<ID>/`，每轮 5–10 张、长边 720px、单张 <500KB，截图命名 `NN-<slug>.png`；每轮必须有 `verdict.md`（勾选通过/失败/待定 + 一行现象），失败证据保留不删。
- `scripts/collect-evidence.sh`：`collect-evidence.sh <ACC-ID> [备注]` 校验 ID 在 `docs/dev/ACCEPTANCE.md` 内，截当前屏、压到 720p、按日期-ID 落盘并维护 `verdict.md`；无设备/无 adb 立即报错退出。
- PR 模板 G3 已固化：真机覆盖安装、验收条目逐条打勾、截图在 `docs/dev/evidence/`、回滚演练（`.github/pull_request_template.md:34-39`）。
- 历史证据在 `artifacts/validation/`（round6–43，约 240MB），`.gitignore:10` 忽略该目录——原始大图只在本机，**可复核的证据必须走 `docs/dev/evidence/`**（这也是 M1-5 存在的理由）。

为什么必须现在决定：① 评审把“无 CI / 无自动化 / Compose UI 测试为零”列为问题（GL-001/GL-003/SC-014），若不写清分工，agent 会去补脆弱 UI 自动化或把 CI 当行为验收；② 复核指出“验证文化”被当作亮点又在排期时不算成本（`docs/dev/review/10-30/REVIEW_OF_REVIEW.md §4.6`），真机验收必须被当成正式机制，而不是临时手测；③ 这个 App 的行为大量依赖 MIUI、无障碍、PRoot、OAuth、WebView、通知、系统日历（GUI 插件需用户在系统设置手动开启无障碍，`docs/user/GUIDE.md §接入`），模拟器无法覆盖这些平台差异。

## 决定

**CI 做机器闸门，真机证据做行为验收，两者固定分工、互不替代。**

落地口径：

1. **CI / `verify.sh` 只证明机器可判定的东西**：编译、单测数与 0 skip、lint 0 error、模块边界、新增测试“回退修复后必红”。CI 红 = 阻断合并；CI 绿 ≠ 行为正确。
2. **行为验收 = 真机证据链**：条目 ID（`ACC-*`）→ 真机覆盖安装（不卸载）→ 逐条打勾 → `scripts/collect-evidence.sh` 采集 5–10 张 720p 截图 → `verdict.md` 勾选通过/失败/待定并写一行现象 → 证据目录随 PR 提交；PR 只引用 ID，不复制正文。数据/持久化改动必须额外给“旧数据 → 新版本”的真机证据（`CONTRIBUTING.md §硬规则`）。
3. **不为回归而写 Compose UI 自动化**：页面层测试优先补纯函数/解析/状态机（JVM 单测）；`@Preview`/组件语义测试按 SC-014 排第二批，不作为行为门禁。
4. **证据必须可复核**：截图缺条目 ID/日期、只存在 `artifacts/validation/`（被忽略）或口头描述，一律不算验收证据。
5. **CI 不做 UX/性能门禁**：性能结论要 `gfxinfo` 前后对比（`CONTRIBUTING.md §硬规则`），但不把 macrobenchmark/截图对比塞进 CI（见 §后果）。

## 何时重新评估（不是永远不做）

- **出现第二名常驻维护者 / 外部贡献者**：先保证证据可复核（截图进仓库 + ID 对应），再评估是否引入 device farm、截图对比或把 `androidTest` 纳入 CI。
- **验收条目增长到单轮无法逐条走完**（例如 191 条映射完成后仍持续膨胀），或同一类回归连续两次被真机发现而单测没覆盖 → 引入自动化，优先“截图对比/轨迹回放”，而不是先写脆弱的 Compose 断言。
- **CI 能拿到可负担的托管真机/模拟器**，且目标行为可在其上复现 → 可把部分 `androidTest` 纳入 CI，但仍不能替代真机平台验收。
- **纯 Compose 页面**（不依赖 MIUI/PRoot/OAuth/无障碍）在模拟器可稳定复现 → 这部分可自动化。

## 被否决的方案

- **用 Compose UI 自动化替代真机证据**：页面直连 Store、壳层 644 行、UI 层零测试基座（GL-003/SC-014），先写出来的 UI 测试测的是实现细节；MIUI 预测返回/无障碍/PRoot/OAuth/WebView/通知无法覆盖；维护成本真实。
- **只做 CI 不做真机证据**：CI 只跑 `check + assembleDebug`，证明不了行为和平台边界；“无 CI”条目（GL-001）的修复不等于“有回归网”。
- **只做真机证据不做 CI**：M1-7 已复活 CI；没有机器闸门时测试基线、lint、边界、回滚演练只能靠人读，成本更高（`PLAYBOOK.md §4 验证闭环`）。
- **把 `artifacts/validation/` 原始大图入库**：体积与隐私问题；只入压缩证据（`docs/dev/evidence/README.md`：每轮 5–10 张、长边 720px、单张 <500KB、必须有 `verdict.md`），原始大图留本地。
- **用“截图看起来没问题”当证据**：无法对应条目、无法复核；必须绑定稳定 ID + 日期路径（`AGENTS.md §1` 的 L4 与 §8）。
- **CI 里跑全量 `androidTest`**：需要设备/模拟器、时长不可控、平台差异导致假红；`androidTest` 目前只有 2 个设备测试（`docs/dev/DEVELOPMENT.md §测试`），按需手动跑。

## 后果

- 代价：每次行为改动都有真机人工成本；回归覆盖依赖作者/agent 按条目执行，重复劳动不能被自动化消除。跨设备/系统版本的行为只能在当前真机（Redmi / Android 13）上验证，未覆盖设备在 PR 里要如实写“未验证”。
- 证据质量依赖纪律：`docs/dev/evidence/` 必须进仓库且可读，`verdict.md` 的判定与一行现象不能省；截图命名/目录规范一旦松动，第二个人无法替你验收。
- **被永久驳回的评审建议**：SC-014 的“立即给页面补 Compose UI 测试并作为回归网”（降为第二批，先补纯函数/解析/状态机测试）；GL-003 的“Compose UI 测试覆盖导航栈与聊天流式渲染”作为门禁；把“没有 CI”直接等同于“没有验证”（CI 已有，职责已定）；把 macrobenchmark/截图对比当 CI 门禁。
- 已接受的缺口：无 Compose UI 自动化、无视觉回归、无多设备矩阵；这些在触发“重新评估”条件前不补。

## 推翻条件

1. **第二名常驻维护者出现**，且验收量超出人工能力 → 必须重估：先保证证据链可复核，再引入截图对比/CI device tests/device farm。
2. **Compose UI 测试基座建立**（共享 fake 与 `AppDependencies` 注入已具备，见 M2-1/M2-3；仍需至少 3 个页面可稳定渲染的 Compose 测试）→ 可把部分页面测试纳入 CI，但仍不作为唯一行为验收。
3. **平台行为可在模拟器可靠复现**（例如纯 Compose 页面、无系统服务依赖）→ 该部分可自动化，真机只保留平台能力验收。
4. **CI 稳定性/成本结构变化**（托管真机免费或自建设备池）→ 可扩大 CI 的验收范围，但仍保留“真机证据做行为验收”的分工。

## 修订记录

- 2026-10-30 · M5-9 校准：基线 1024→1165；`verify.sh` 改为现行 `gradlew-locked` 描述；旧流水号归档路径与壳层行数更新；未改决定。
