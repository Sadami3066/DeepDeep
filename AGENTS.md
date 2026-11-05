# DeepDeep · 项目总览

Android 个人 agent App（Kotlin + Compose）。模块：[:app](app/AGENTS.md)（UI/装配）、[:runtime](runtime/AGENTS.md)（agent 运行时）、[:runtime-api](runtime-api/AGENTS.md)（纯 JVM 契约）、[:shell](shell/AGENTS.md)（proot/root）、[:design](design/AGENTS.md)（主题与组件）、[:plugin-api](plugin-api/AGENTS.md)（插件契约与可发布 SDK）、[:plugin-sample](plugin-sample/AGENTS.md)（示例外部插件，不进 APK）。

## 索引

目录级速查（文件 → 职责）写在各级 `AGENTS.md` 里：开始任务先读本文件，再读目标目录树的 `AGENTS.md`；新增目录按同样格式补一份。

- 产品能力与边界：[docs/GUIDE.md](docs/GUIDE.md)；设计取舍：[docs/DECISIONS.md](docs/DECISIONS.md)；插件作者文档：[docs/PLUGIN_DEV.md](docs/PLUGIN_DEV.md)；变更记录：[CHANGELOG.md](CHANGELOG.md)
- 构建脚本：`scripts/`（`check.sh` 完整验证、`gradlew-locked` 全局串行锁、`dev-setup.sh` 首次环境）
- `assets/` 是设计源素材与第三方图标，**不是** App 运行时资源（运行时资源在 `app/src/main/assets` 与各模块 `res/`）
- `reference/`、`.agents/` 是本机参考源码与 git worktree，不进版本库，别在里面改

## 命令

```bash
./scripts/check.sh --full      # 交付前完整验证：编译 + 单测 + lint + 模块/包边界 + APK + ABI 基线 + 插件样例 + 插件模板
./scripts/check.sh --device    # 再加覆盖安装到手机并启动
./scripts/plugin-e2e.sh        # 真机插件端到端（安装→启用→工具→卡片→停用→卸载）
./scripts/plugin-api-abi.sh    # 单独校验插件 SDK ABI 基线（--update 更新）
./scripts/gradlew-locked ...   # 其它 Gradle 任务；不要直接 ./gradlew（本机并发会 OOM）
```

首次环境：`./scripts/dev-setup.sh`。基线：单测 2381（app 1761 / runtime 510 / shell 50 / plugin-api 60）、0 失败 0 skip、lint 0 error。

## 关键事实

- 会话以 pi JSONL 为唯一权威（`files/agent/sessions/`），内容只认它；置顶/归档/重命名只是本地元数据，界面状态都是它的事件投影。
- 用户可见文案全部在 `app/src/main/res/values*/strings_*.xml`，默认中文、`values-en` 同步；代码里不拼文案。
- 目标与后台任务只在进程内存，重启清空；Linux 是 proot 用户空间，不是安全沙箱。
- 本地分发，release 变体用 debug 证书签名（换签 = 卸载重装 = 丢数据，见 `DECISIONS`）。

## 规则

- **发布前没有用户**：不做旧版本兼容与数据迁移——旧数据、旧文件格式、旧协议、旧入口一律直接淘汰，不为「以后可能」保留兼容层、占位符、废弃 API 或死代码；重构时旧实现直接删除，新实现只保留当前唯一形态。
- 每次代码更新同步更新所在目录的 `AGENTS.md`：文件增删、职责变化都要改；新增目录按同样格式补一份。索引只写总体性、前瞻性信息，保持精简。
- 一次改动只做一件事，一个提交，标题 `type: 摘要`；要能 `git revert` 一行撤回。
- 新行为加测试；不删测试、不改断言凑绿。
- 开发中按改动选择编译、相关测试或真机检查；完整代码交付或集成结束时统一跑一次 `check.sh`，仅改文档不构建。
- 代码文件不写注释；用户可见文案走 `res/values*/strings_*.xml`。
- 第三方依赖按需判断，不预设倾向：有合适的成熟库就用，自己实现更简单就不引入；新增依赖在 [docs/DECISIONS.md](docs/DECISIONS.md) 记录用途、许可与体积。不改模块依赖图、`settings.gradle.kts`、签名配置、`.gitignore`。
- 设计取舍见 [docs/DECISIONS.md](docs/DECISIONS.md)，按它来，不要「顺手修正」。
- 改 UI：直接改对应 Screen 和 `design/` 组件，编译过 + 手机上截一张图就算验收，不要求写测试。
- 可能删除真实数据、不可逆迁移、换签名、发布或改变关键产品取舍时先确认；已授权需求内的常规持久化与权限接线直接完成。
- 需要流程文档时去 git 历史找（`git log -- docs`），不要新建。
- 盲从比做错更糟：明知有更好的解法，先摆出来让用户选，不要闷头照指示做。
- 用户说「修 X」要的是「X 不再发生」，不是「执行他说的修法」；一眼能看出更简单的根因改法，先说出来。

## 保持审美反省

- 改界面先看边距、对齐、层级和视觉重心，不把“能显示”当验收。
- 不为了填满空间堆控件；留白、克制和清楚的主次优先。
- 完成后以陌生用户视角再看一遍，觉得不美就继续改。

## Team 模式

- 并行时按目录/文件分工，**同一个文件同一时间只有一个 agent 写**。
- 同一时间只让一个 agent 跑 `check.sh`（Gradle 全局锁会排队，不必并发）。
- 开工先检查分支和工作区，按需同步远端；别人刚改过的文件先读再动。
