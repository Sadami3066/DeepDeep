# 0008 可选 root 能力与 root_shell

- 状态：已采纳 · 2026-10-30 · 相关任务 M7-6

## 背景

ADR-0002 明确写「不实现工具级审批」「引入 root 属于独立的产品决策」，并把「引入 root 或系统级自动化」列为推翻条件 4。2026-10-30 用户在本机（Redmi 22101316UCP / Android 13 / APatch 11224，`su -c id` 返回 uid 0）明确要求给 App 增加 root 能力，并选择「总开关 + root_shell 工具、不逐次确认」的形态。触发推翻条件 4，必须重新定义信任模型，而不是在旧决定上打补丁。

## 决定

root 是**默认关闭的可选增强层**，不改变「没有 root 也能用」的产品定位：

1. 设置 → 运行环境新增 root 总开关（`LinuxSettingsStore.root_enabled`，默认 false）。开启不代表已授权；首次实际执行 `su` 时由 root 管理器（Magisk/APatch/KernelSU）弹系统授权，App 不重复造审批 UI。
2. 授权结果不持久化为安全凭据：每次调用以 `su` 实际结果为准；设置页的状态（未检测/已授权/被拒绝/超时/不可用）只用于展示与重试。
3. 只注册一个工具 `root_shell`（`runtime/RootShellTool.kt`，实现在 `shell/RootShell.kt`）：单条命令、默认 15s、上限 120s、stdout+stderr 合计 64 KiB 截断、返回 exitCode；超时用内层 `timeout -s KILL` 终止命令本身，而不是只杀 su 客户端。没有 root 文件读写、短信库、pm/settings 专用工具。
4. 只有「App 前台 + 用户直接发起的轮次（USER/RETRY）」可用：后台任务唤醒、定时提醒、目标续跑等自动轮次返回 `root_user_turn_required`；`AppVisibility.foreground` 为 false 时返回 `root_foreground_required`；创意工坊页面 Agent 的 `WORKSHOP_AGENT_TOOL_NAMES` 白名单不含 `root_shell`；后台任务、定时任务、无人值守路径不获得 root。
5. 命令与退出码写入运行日志，不记录输出正文（可能含隐私）；不自动重试、不循环弹授权。
6. 支持的 root 方案只依赖「`su` 可执行 + 用户授权」这一契约，不绑定具体管理器包名，不读 `ro.boot.*` 判断 root（本机已验证属性可被模块伪装）。
7. 非 root 设备或关闭开关时：`root_shell` 不注入，UI 显示「本机没有可用的 su」，不报错、不降级其他功能。

## 落地口径

- `shell/RootShell.kt`：`probe()` / `execute()`，进程、超时、输出上限都在 shell 模块内，可注入 runner 单测。
- `runtime/RootShellTool.kt`：`CodingTool`，错误码 `root_disabled` / `root_foreground_required` / `root_unavailable` / `root_denied` / `root_timeout`。
- `AppGraph.kt`：root 开关 flow → `host.reconfigure()`；工具在开关打开时注入，关闭后下一轮 reconfigure 移除。
- `ui/settings/RuntimeSettingsScreen.kt`：开关、状态、检测并授权、风险说明。

## 被否决的方案

- **每次 root 命令弹确认**：一轮任务几十次工具调用，确认疲劳使安全收益接近 0，和 ADR-0002 的结论冲突；用户明确选择不做。
- **只做固定模板的只读白名单（id/getprop/df…）**：无法覆盖用户真实需要（改系统设置、装模块、处理系统文件），会退化成演示功能。
- **静默 root / 后台自动 root**：违反「用户可见、前台执行」，且后台无人看守时误操作代价最高。
- **Shizuku 作为替代**：本机未安装，且仍需每次重启激活；root 是同一信任模型下的更直接路径，不再另造一套 shell 级授权。
- **用 root 替换 proot 运行环境**：proot 是用户空间 Linux，替换会改变文件系统与守护进程语义，属于另一个产品决策，不在本 ADR 内。
- **开放 root 文件/短信/pm 等专用工具**：本轮只交付 `root_shell` 一条通用能力，专用工具按需再评估并新开 ADR。

## 后果

- 需接受：模型可以在前台会话中执行任意 root 命令；误操作可破坏系统分区、应用数据或其他 App 数据，恢复只能靠用户自己的备份/刷机。root 管理器本身、SELinux（本机 `u:r:magisk:s0`）与 ROM 兼容性会限制部分命令。
- ADR-0002 中「引入 root」的部分由本篇取代；「不做工具级审批、信任边界是机主本人」的结论继续适用于 root 工具。
- 对本机 open-source 定位：root 默认关闭，README 仍写「基本功能不需要 root」；不假设用户设备已 root。
- 被永久驳回的评审建议：要求给 root 工具逐次确认弹窗、要求把 root 做成「只读演示」、要求把 root 能力默认开启。

## 推翻条件

出现任一情况必须重新设计，而不是局部调参：

1. 多用户/共享设备，或需要区分「机主」和「其他人」。
2. 接受不可信外部输入并自动执行 root（外部消息、网页、他人下发的任务）。
3. 无人值守/远程执行形态（含后台定时 root、远程入口）。
4. 需要把 root 工具开放给创意工坊页面或第三方页面。
5. 目标从「自用开发机」转为面向普通用户分发，且无法保证用户理解 root 风险。

## 修订记录

- 2026-10-30 · 初始版本；触发 ADR-0002 推翻条件 4，root 相关以本篇为准。
