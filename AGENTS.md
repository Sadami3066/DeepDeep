# AGENTS.md — DeepDeep 仓库工作规则

本文件的命令与数字优先于 README 与 docs。文档里的数字会漂，**一律以命令输出为准**；发现不符，先改文档再改代码。

## 0. 先读

- 文档总索引：`docs/README.md`
- 路线图：`docs/dev/refactor/README.md`
- 可执行任务卡：`docs/dev/refactor/tasks.tsv`（只放活跃卡；完成的在 `tasks-archive-*.tsv`。开工前抄 `scope`/`accept`/`verify`）
- 执行协议、任务简报与 PR 模板：`docs/dev/refactor/PLAYBOOK.md`
- 基线数字：`docs/dev/refactor/BASELINE.md`

## 1. 环境与命令

JDK 17+、Android SDK Platform 36 + build-tools 36.0.0。首次：

```bash
./scripts/dev-setup.sh     # 校验 JDK/SDK、写 local.properties、拉 SDK、出 debug APK
```

日常：

```bash
./scripts/gate.sh 3        # 默认档：文档 + 编译 + 单测 + lint + 边界 + APK（约 75s）
./scripts/gate.sh 4        # 上真机：3 + 覆盖安装
./scripts/gate.sh ci full  # 需要时才在 GitHub 上跑，不阻塞本地
```

- `dev.pi` SDK 由 `syncPiSdk` 从 pi2kotlin `v0.2.4` Release 下载并校验 sha256，不依赖本机 `~/.m2`。
- 离线或调试 SDK 时：`./scripts/gradlew-locked ... -PpiSdkLocalZip=/path/to/pi2kotlin-0.2.4-m2.zip`。
- **所有 Gradle 调用必须走 `./scripts/gradlew-locked`**（`gate.sh` 已封装，`verify.sh` 是 `gate.sh 3` 的别名）。它用 `flock` 全局串行并默认 `--max-workers=1`：本机内存有限，并发构建已在 2026-10-30 造成整机 OOM 死机。**禁止直接 `./gradlew`**。

## 1.5 验证档位（L0–L4，手动选；CI 手动）

| 档 | 命令 | 跑什么 | 本机实测 |
|---|---|---|---|
| L0 文档 | `./scripts/gate.sh 0` | 断链检查 | ~1s |
| L1 编译 | `./scripts/gate.sh 1` | L0 + 五模块编译 | ~5s |
| L2 单测 | `./scripts/gate.sh 2` | L0 + 单测 | ~5s |
| L3 全量（默认） | `./scripts/gate.sh 3` | L0 + check（单测/lint/边界）+ APK | ~75s |
| L4 真机 | `./scripts/gate.sh 4` | L3 + 覆盖安装到真机 | L3 + ~10s |
| CI | `./scripts/gate.sh ci fast` 或 `ci full` | GitHub 上手动跑，不等它 | 另算 |

口诀：**只改文档 L0；改代码默认 L3；要上真机 L4；发版/改门禁才用 CI。** 流程强度（任务卡 / G2 / 证据）按档位裁剪，见 `docs/dev/refactor/PLAYBOOK.md §0`。

## 2. 基线（不得下降）

| 指标 | 基线 |
|---|---|
| 单测 | 1165（app 756 / runtime 377 / shell 32），0 失败、0 skip |
| Lint | 0 error；156 warning |
| 编译警告 | 13 条 Kotlin `w:` |

数字以 `docs/dev/refactor/BASELINE.md` 的复算命令为准。**测试数下降、出现 skip、删断言 = 失败**。

## 3. 硬边界

- 模块依赖只允许 `:app → {:runtime, :shell, :design}`、`:runtime → :runtime-api`；其余模块之间不得互相依赖（`verifyModuleBoundaries` 强制）。
- 不擅自修改：`settings.gradle.kts`、根 `build.gradle.kts`、`gradle/libs.versions.toml`、签名配置、`.gitignore`。需要改就在任务卡里写明并等 Lead 批准。
- 不碰：`artifacts/`、`*.ttf`、`*.rootfs`、`*.so`、任何 4MB 以上文件、`docs/dev/review/` 的原始数据。
- 只改任务卡「允许修改的文件」里的文件；需要额外文件先停下要授权。
- 代码文件里不留注释（用户明确要求）；用户可见文案走 `res/values*/strings_*.xml`。
- 不新增第三方依赖；不重排、不格式化无关文件；不做「顺手优化」。

## 4. 热点文件（不要往里加东西；改它必须单独任务卡 + 新增测试）

`ChatViewModel.kt`(799) ｜ `DeepDeepApp.kt`(644) ｜ `AgentRuntimeHost.kt`(1119，已有 6 场景直接测试) ｜ `PluginSetupSession.kt`(319)

## 5. 交付物（按档位取舍）

L0/L1：变更文件清单。
L2：清单 + 单测输出 + 回滚命令。
L3：清单 + gate 输出 + 回滚命令（新增行为加测试红绿证据）。
L4：全部五项都交。

1. 变更文件清单（`git diff --name-only <base>..HEAD`）
2. `./scripts/verify.sh` 的原始输出
3. 新增/修改行为的测试，并说明「把修复回退后哪条测试会红」（贴红的那次输出）
4. 回滚命令
5. 涉 UI/行为：验收条目 ID + 截图路径（`docs/dev/evidence/<date>-<id>/`）

## 6. 提交与 PR

- 标题：`type: 祈使句摘要 [任务ID]`，`type ∈ feat|fix|refactor|perf|docs|test|chore|build|revert`
- 一个任务一个 squash 提交；body 三行必填（`验收:`、`回滚:`、`不在范围:`）——L2–L4 必填，L0/L1 可只写标题
- 提交标题必须带 backlog ID（任务卡 ID 或 `RT-xxx`），否则无法机器对账；L0/L1 无卡时用 `[L0]`/`[L1]`
- 回滚必须是 `git revert <sha>` 一行能完成；数据影响要在 PR 里写明

## 7. 禁止动作

照做会让项目变糟的 15 条（换 release keystore、损坏文件跳过写入、事件改单消费者 Channel、消息内嵌 LazyColumn、卸载删证据目录、加全局异常处理器……）见 `docs/dev/refactor/PLAYBOOK.md §6`。开工前对照一遍。

## 8. 何时停下来问

只有三类：① 外部副作用（发 Release、切 public、改 GitHub 设置）；② 产品取舍；③ 无法自证的阻塞。一次最多 3 条，其余自己查（可 websearch 查版本/配置，以实测为准）并继续。

## 9. 真机验收

条目在 `docs/dev/ACCEPTANCE.md`（稳定 ID，如 `ACC-CHAT-01`）；PR 只引用 ID，不复制正文。截图用 `scripts/collect-evidence.sh <ACC-ID>` 落盘，原始大图留本地不入库。
