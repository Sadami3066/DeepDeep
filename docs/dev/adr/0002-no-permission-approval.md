# 0002 不做权限/审批系统

- 状态：已采纳 · 2026-10-30 · 任务 M1-8

## 背景

DeepDeep 是一个跑在本人手机上的个人 agent：模型可以执行 `bash`（Alpine/proot）、读写工作区文件、调用 MCP 与插件工具、通过无障碍服务操作界面、读写系统日历。它没有任何“多用户/托管/远程执行”形态（`README.md §能做什么`、`docs/user/GUIDE.md`）。

现状是**执行不经过 App 内审批**，这是明确设计而不是遗漏：

- 系统提示词直接写死：「不需要请求审批；直接执行命令。不要编造工具结果。」（`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:12`）。
- 与执行权限相关的总开关是 Linux 运行环境：`LinuxSettingsStore` 只有 `assistant_enabled` 与 `shared_storage_enabled` 两个布尔项（`app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:20-24`）；关闭 `assistant_enabled` 时 `shellExecutorFor` 返回 `null`，`bash` 工具不存在（`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:281-287`）。
- 入口在设置页「运行环境」开关（`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt` 的 `LinuxEnvironmentSection(enabled = linuxEnabled, …)`）。
- 能力边界写在文档里：`README.md` 边界（“不做权限/审批系统；只有 Linux/root 总开关”）、`docs/user/GUIDE.md §已知边界`。

必须现在决定：① 公开/开源后，评审会按“agent 执行危险操作应有确认”反复提（2026-10-30 复核已把“无权限审批”列为对项目定位的理解偏差，`docs/dev/review/10-30/REVIEW_OF_REVIEW.md §4.6`）；② 审批一旦半途加上，会改动全部工具调用协议与 UI，属于产品级改动，不能由某个 agent 顺手加；③ 信任模型必须先写清，否则“guest 里的 bash 能干什么”“proot 算不算沙箱”会被当成安全缺陷来修。

## 决定

**不实现工具级审批、策略引擎、per-tool allowlist 或执行前二次确认；与执行权限相关的人工闸门只有 Linux 运行环境总开关（`assistant_enabled`）与共享目录开关（`shared_storage_enabled`）；插件启停属于能力配置，不是审批。信任边界 = 持有这台设备的人。**

落地口径：

1. Android 系统权限（通知、录音、日历、无障碍、所有文件访问等）继续按平台机制申请/拒绝，不在 App 内重复造一层“审批”。
2. 不新增「本次允许/总是允许/拒绝」类 UI；不记录审批日志（没有审批，也就没有日志）。
3. 适用边界固定：**个人设备、单一用户、自己发起任务**。不接受“替他人执行”“远程下发命令”“无人值守处理不可信外部输入”作为使用方式。
4. `proot` 不是安全沙箱（`README.md §边界`）：它是用户空间 Linux，与 App 的 Android 沙箱是两回事；App 私有目录与 DataStore 凭据由 Android 沙箱保护，不靠 proot。任何“把 proot 加固成沙箱”的诉求都不是本 ADR 的缺陷。
5. 工坊页面 Agent 的能力白名单 + `ScopedFileSystem` 是**能力限制**（页面只能调用列出的工具、只能写本次 run 的临时目录），不是审批机制；两者不要混为一谈。

## 被否决的方案

- **每个写操作/命令弹确认**：一轮对话几十次工具调用，确认疲劳会让用户无脑点“允许”，安全收益接近 0，交互成本真实且不可逆地拖慢所有任务。
- **工具级 allowlist / 策略引擎（沙箱配置、路径规则、域名规则）**：规则会随插件与 MCP 增长而漂移，维护成本高于收益；个体设备上攻防双方是同一人，配置层只增加摩擦。
- **先加“危险命令”关键词检测/拦截**：`bash` 是图灵完备的，字符串匹配漏报率高、误报直接卡住正常任务；不承诺这种假安全。
- **把 proot 当安全边界并据此设计审批**：前提错误——proot 不隔离 App 数据，审批也拦不住模型在 proot 内执行的任意命令。
- **引入 root 开关**：`docs/archive/ROOT.md` 的状态是“研究，不实现”；产品不能假设设备已 root，且 root 的敏感能力与“无审批”组合风险过高。root 属于独立的产品决策，不是本 ADR 的延伸。
- **面向多人/企业场景加审批层**：当前没有多用户、没有账号体系、没有远程下发入口；为不存在的场景写机制是过度设计。

## 后果

- 代价：模型误操作（`rm -rf` 工作区、写坏文件、把凭据内容发到网络）没有任何 App 内拦截；恢复只能靠用户自己的备份、会话 JSONL 的 `.corrupt` 备份与 git。工作区数据不在 Android 备份内（`allowBackup=false`，见 ADR-0001），重装/换机即丢。
- 安全责任转移到使用场景：处理不可信内容（外部网页、邮件、第三方 prompt、别人发来的文件）时，用户必须自己判断是否让 Agent 执行；这是使用边界，不是产品缺陷。
- **被永久驳回的评审建议**：把“无权限审批”列为安全缺陷并要求补审批弹窗/策略引擎的条目；要求“每个工具调用二次确认”的通用最佳实践；把 proot 当沙箱、要求做隔离加固的条目（`proot` 的定位见 `README.md §边界`）。这些建议再出现时直接引用本篇驳回，不再重复讨论。
- 对新增能力的约束：任何新工具默认继承“无审批”；若新能力会突破“个人设备、单一用户”边界（例如接收外部消息自动执行），必须新开 ADR 并先解决信任模型，而不是悄悄加一个确认弹窗。

## 推翻条件

出现以下任一情况，本 ADR 失效，必须重新设计审批/授权模型（而不是局部打补丁）：

1. **多用户或共享设备**：App 需要区分“机主”和“其他人”的权限。
2. **接受不可信外部输入并自动执行**：例如定时轮询外部消息、他人可下发的任务、开放网络入口驱动工具。
3. **无人值守/远程执行形态**：把本 App 当作远程执行入口（包括通过 GUI 无障碍能力操作他人设备）。
4. **引入 root 或系统级自动化**（`docs/archive/ROOT.md` 的 P1–P4）：必须同时设计确认、审计与降级路径，推翻“只有总开关”。
5. **企业/他人设备分发**，或合规要求可审计的操作日志：审批从“交互问题”变成“合规问题”。

## 修订记录

- 2026-10-30 · M5-9 校准：`shellExecutorFor` 行号随 AppGraph 改动修正；未改决定。
