# AGENTS.md

DeepDeep：Android 个人 agent App（Kotlin + Compose）。模块：`:app`（UI/装配）、`:runtime`（agent 运行时）、`:runtime-api`（纯 JVM 契约）、`:shell`（proot/root）、`:design`（主题与组件）。

## 命令

```bash
./scripts/check.sh             # 编译 + 单测 + lint + 模块边界 + APK（默认，约 75s）
./scripts/check.sh --device    # 再加覆盖安装到手机并启动
./scripts/gradlew-locked ...   # 其它 Gradle 任务；不要直接 ./gradlew（本机并发会 OOM）
```

首次环境：`./scripts/dev-setup.sh`。基线：单测 1223、0 失败 0 skip、lint 0 error。

## 规则

- 一次改动只做一件事，一个提交，标题 `type: 摘要`；要能 `git revert` 一行撤回。
- 新行为加测试；不删测试、不改断言凑绿。
- 代码文件不写注释；用户可见文案走 `res/values*/strings_*.xml`。
- 不加第三方依赖；不改模块依赖图、`settings.gradle.kts`、签名配置、`.gitignore`。
- 设计取舍见 [docs/DECISIONS.md](docs/DECISIONS.md)，按它来，不要「顺手修正」。
- 改 UI：直接改对应 Screen 和 `design/` 组件，编译过 + 手机上截一张图就算验收，不要求写测试。
- 改持久化 / 数据迁移 / 权限 / 签名前先停手问我，这类不可逆。
- 需要流程文档时去 git 历史找（`git log -- docs`），不要新建。

## Team 模式

- 并行时按目录/文件分工，**同一个文件同一时间只有一个 agent 写**。
- 同一时间只让一个 agent 跑 `check.sh`（Gradle 全局锁会排队，不必并发）。
- 开工前先 `git pull --rebase`；别人刚改过的文件先读再动。
- 结束时自己跑一次 `check.sh` 并贴结果，别把红着的构建留给下一个。
