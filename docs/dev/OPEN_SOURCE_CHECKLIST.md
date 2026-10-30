# 开源就绪清单（M4）

> 当前状态：**暂不公开**。本清单把「公开/发版前必须完成的事」固化下来，避免依赖记忆。
> 每一项都可在仓库内验证；完成一项勾一项，未完成项不得对外称「开源」。

## A. 许可证与合规

- [x] 根 `LICENSE`（Apache-2.0）与官方文本逐字一致
- [x] `NOTICE` 与 `THIRD_PARTY_NOTICES.md`（自有代码 / 第三方分开表述，不写「整 APK 是 Apache-2.0」）
- [x] 随 APK 的许可全文落 `app/`、`design/`、`shell/` 三个模块的 `assets/licenses/`（自检 0 MISSING；2026-10-30 复核 notice 引用的 14 个文本在三个模块中均存在）
- [x] PRoot（GPL-2.0-or-later）源码 commit、构建脚本、talloc（LGPL-3.0-or-later）与 written offer 已写入 notice
- [x] Alpine rootfs 官方 sha256 + aports tag + 逐包许可表
- [ ] 公开分发前替换 3 个非开源 App Store 图标、Radius 图形与来源未记录的 `app/src/main/res/drawable-nodpi/ic_plugin_feishu.png`，或取得授权（M4-4）
- [ ] mascot 参考图授权确认（当前来源未明，不随代码许可）

## B. 仓库卫生

- [x] `.gitattributes`、`.gitignore`（assets 大图/rs.html/.notes 不入库）、`.mailmap`
- [x] `git gc`（.git 51M → 28M；当前 pack 27.29 MiB，日常提交会再产生少量 loose object）
- [x] tracked 文件中无密钥/令牌（`password`/`api[_-]?key` 字面命中 20 个文件，均为提示文案、资源名与测试占位 `sk-test`；真实令牌模式 0 命中）。个人邮箱只出现在 `.mailmap` 与 `docs/dev/refactor/HYGIENE.md` 的身份说明（见下一项）
- [ ] 私有仓转公开前：确认 commit 历史里没有不想公开的内容（作者邮箱可用 `git mailmap` 控制显示）
- [ ] `src/main` 通配 import = 0（lint 崩溃风险面）

## C. 可复现构建

- [x] 新机器只需本仓库即可构建（`syncPiSdk` 从 Release 拉 SDK + sha256；2026-10-30 实测 Release 资产 sha256 = `gradle/pi-sdk.sha256`）
- [x] wrapper 有 `distributionSha256Sum`
- [x] 一条门禁命令 `./scripts/verify.sh`（编译 + 单测 + lint + 模块/包边界）
- [x] CI 在 PR 上跑 `verify`（`.github/workflows/ci.yml`）
- [ ] master 分支保护要求该检查（GitHub 仓库设置项，仓库内不可验证；公开前在设置中确认）
- [ ] 构建环境文档与 CI 一致（JDK17 / SDK36 / build-tools 36.0.0）

## D. 文档

- [x] 用户向：`README.md`、`docs/user/GUIDE.md`
- [x] 开发者向：`docs/dev/DEVELOPMENT.md`、`docs/dev/ARCHITECTURE.md`、`docs/dev/ACCEPTANCE.md`
- [x] 决策记录：`docs/dev/adr/`（7 篇）
- [x] agent 向：`AGENTS.md`、`docs/dev/refactor/PLAYBOOK.md`
- [x] 贡献：`CONTRIBUTING.md`、`CODE_OF_CONDUCT.md`、`SECURITY.md`、issue/PR 模板
- [ ] README 截图待补（需真机；目录 `docs/img/` 建好后脱敏入库）
- [ ] `CHANGELOG.md`（从 tag 区间提交生成，不手写流水账）

## E. 社区与协作

- [x] issue 模板（bug/feature）
- [x] PR 模板 + squash/回滚约定（`docs/dev/adr/0004-trunk-based-one-task-one-revert.md`、`docs/dev/refactor/PLAYBOOK.md`）
- [ ] 公开前启用：Issue 标签体系同步到 GitHub（`P0-P3`、`mod:*`、`type:*`、`effort:*`）
- [ ] 公开前决定：是否启用 CODEOWNERS 强制评审（出现第二个能批的人再开）
- [ ] 公开前决定：GitHub Actions 分钟数预算（私有仓 2000 分钟/月，公开仓免费）

## F. 发布工程

- [x] 版本号策略（tag → versionCode/versionName）与发布流程文档（`docs/dev/RELEASING.md`）
- [ ] `app/build.gradle.kts` 消费 tag 推导的 `-PversionName` / `-PversionCode`（当前 `defaultConfig` 硬编码 `0.2.0-dev` / `1`）
- [ ] 正式 keystore（一次性，丢失不可恢复）+ CI Secrets
- [ ] release workflow（当前为 workflow_dispatch 准备稿，未启用 tag 触发）
- [ ] Release Notes 模板与回滚演练
- [ ] Maven Central 收口（pi2kotlin v0.3，ADR-0007）

## G. 公开当天

1. 跑 `./scripts/verify.sh` + 随机 3 条 `docs/dev/ACCEPTANCE.md` 真机验收
2. 重跑密钥/邮箱自检：`git grep -nE "(ghp_|sk-|api[_-]?key|password)" -- '*.kt' '*.md' '*.yml' '*.json'` 与 `git grep -nE "@(qq|gmail)\.com"`；字面命中需人工确认是文案/测试占位（2026-10-30 抽查：20 个文件命中 `password`/`api_key` 文案，无真实密钥；邮箱仅 `.mailmap` 与 HYGIENE.md）
3. 仓库由 private → public（**不可撤回**：fork/缓存/爬虫会保留）并同步打开 Discussions（可选）
4. 打第一个公开 tag 并发 Release（带已签名 APK + 源码包）
5. 在 README 声明「当前为个人项目，接口与数据格式可能变化」
