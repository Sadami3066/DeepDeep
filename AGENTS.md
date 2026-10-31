# AGENTS.md

DeepDeep：Android 个人 agent App（Kotlin + Compose）。模块：`:app`（UI/装配）、`:runtime`（agent 运行时）、`:runtime-api`（纯 JVM 契约）、`:shell`（proot/root）、`:design`（主题与组件）。

## 命令

```bash
./scripts/check.sh             # 完整验证：编译 + 单测 + lint + 模块边界 + APK
./scripts/check.sh --device    # 再加覆盖安装到手机并启动
./scripts/gradlew-locked ...   # 其它 Gradle 任务；不要直接 ./gradlew（本机并发会 OOM）
```

首次环境：`./scripts/dev-setup.sh`。基线：单测 1378、0 失败 0 skip、lint 0 error。

## 规则

- 一次改动只做一件事，一个提交，标题 `type: 摘要`；要能 `git revert` 一行撤回。
- 新行为加测试；不删测试、不改断言凑绿。
- 开发中按改动选择编译、相关测试或真机检查；完整代码交付或集成结束时统一跑一次 `check.sh`，仅改文档不构建。
- 代码文件不写注释；用户可见文案走 `res/values*/strings_*.xml`。
- 不加第三方依赖；不改模块依赖图、`settings.gradle.kts`、签名配置、`.gitignore`。
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
