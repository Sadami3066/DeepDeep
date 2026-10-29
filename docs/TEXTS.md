# 描述性文本清单（模型可见 / 影响模型行为）

> 快照：2026-10-30，DeepDeep 工作区最终状态（master `e3395b1` + 本轮全部改动；SDK 固定 pi2kotlin 0.2.4）。
> 行号在生成时逐条 grep 核对；源码改动后请按「标识」重新定位，不要信旧行号。
> 范围：会进入模型上下文的提示词、工具说明、技能正文、运行期注入消息，以及影响模型行为的功能描述文本（插件 promptBody、MCP 服务器说明等）。
> **纯 UI 按钮 / 标题 / 空态 / 状态文案在 `app/src/main/res/values*/strings_*.xml`，不在本清单条目内**（见附录 A）。
> 位置写法：DeepDeep 仓库相对路径；SDK 文件写 `pi2kotlin/<路径>`。workshop 数据工具已合并为 `workshop_edit`；workshop 技能已迁到内部插件「创意工坊」；内部插件设置（工坊技能开关、最大回合等）已落地。
> 规模：164 条清单条目，按 §1–§9 分 9 类；附录 A 另列 20 个 UI 资源文件（不计入条目）
> 条目统计不含附录 A 的 UI 资源文件清单。

## 1. 系统提示（DeepDeep runtime）

每轮由 `PiSessionEngine` 重建；`SYSTEM.md` 提供 preamble，其余段由 `buildSessionSystemPromptSections` 拼接后经 SDK 的 `extraSystemPromptSections` 注入。

### 1.1 身份 preamble（SYSTEM.md）

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:8` | DEEP_DEEP_SYSTEM_PROMPT | 系统提示·preamble | 你是 DeepDeep（深潜），milous 的私人 AI 助手…对外只有一个身份 | 落盘为 `files/agent/SYSTEM.md`；改常量不会覆盖已存在文件，需删文件或做迁移 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:6` | SYSTEM.md | 系统提示·文件名 | SYSTEM.md：preamble 覆盖文件（files/agent/SYSTEM.md） | SDK 侧读取见 §7.1；改名需同步 ResourceLoader 与 TrustManager 白名单 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:17` | ensureSystemPromptFile | 系统提示·生成 | 仅当 SYSTEM.md 不存在时写入默认正文 | 已存在文件永不覆盖；文案升级需删文件或加迁移 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:27` | 固定规则·concise | 系统提示·规则 | Be concise in your responses | 所有会话生效；与 SDK 同名规则去重 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:28` | 固定规则·路径 | 系统提示·规则 | Show file paths clearly when working with files | 同上 |

### 1.2 个人化 / 环境 / 交互风格段（Personalization.kt，每轮重建）

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:17` | buildPersonalizationSections | 系统提示·段组装 | 生成 environment（+可选 instruction）两段 | 用户设置来源：Personalization(nickname/interaction/instruction) |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:26` | <environment> | 系统提示·环境段 | 日期/模型/工作区/技能目录/称呼/交互风格 | 每轮时间与模型变化都会重写；注意不要塞长文案 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:29` | 环境·日期行 | 系统提示·环境 | 当前日期：YYYY-MM-DD（UTC+08:00） | 格式变化会影响模型日期解析 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:32` | 环境·技能目录 | 系统提示·环境 | Linux：/skills 全局生效、/skill-repo 仓库…自动导入仓库 | 与 §5 技能章节、docs/SKILLS.md 保持一致 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:39` | 环境·交互风格行 | 系统提示·环境 | 当前交互风格：安静/默认/主动 | 样式名来自 interactionLabel；改动需同步设置页选项 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:44` | <instruction> | 系统提示·长期偏好 | 以下为用户长期偏好…若冲突以系统指令为准 | 仅非空时注入；用户可在设置中编辑 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:56` | buildSessionSystemPromptSections | 系统提示·总装 | 个人化段 + shared_storage + official_plugins + extra | 新增段在此登记，注意顺序与 extra 覆盖关系 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:70` | <official_plugins> | 系统提示·插件段 | 包裹插件 promptBody，XML 再转义 | 内容来自 §4.2 各插件 promptBody |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:76` | <shared_storage> | 系统提示·共享存储 | Android 共享存储挂载说明（读写 /sdcard 等） | 仅在设置开启共享存储时出现 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:88` | interactionLabel | 系统提示·风格名 | 安静 / 默认 / 主动 | 英文环境回退中文？当前固定中文 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:95` | 风格指令·QUIET | 系统提示·交互风格 | 少提问少打断…一次最多一个问题 | 改风格语义会影响所有会话行为 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:98` | 风格指令·BALANCED | 系统提示·交互风格 | 必要时提问，一次最多一个问题… | 默认风格 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:101` | 风格指令·PROACTIVE | 系统提示·交互风格 | 更主动澄清关键歧义…给出下一步 | 与提醒/目标轮文案无直接耦合 |

### 1.3 装配与规则段（PiSessionEngine.kt）

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:52` | createSession/ensureSystemPromptFile | 系统提示·装配 | 会话创建前确保 SYSTEM.md 存在 | 每次 createSession 都会检查 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:74` | currentSections | 系统提示·装配 | 闭包每轮重建全部段 | hooks 与 initial 两处调用 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:98` | buildToolRuleSection 注入 | 系统提示·规则段 | 把工具 promptGuidelines 汇总为 <rules> | 改工具 guidelines 即改此段（见 §2/§3） |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:99` | initialSystemPromptSections | 系统提示·首轮快照 | 首轮用创建时快照，之后 hooks 每轮刷新 | 首轮快照与后续刷新不一致时会出现补丁 system 消息 |

## 2. runtime 工具说明（App 层）

位置指向各工具的 `description` 行。`parameters` 的 JSON Schema 同样进入模型上下文（字段级 description），表中在修改提示里给出位置与 guidelines 条数。

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginTools.kt:34` | `plugin_setup` | 工具说明 | Install and run the setup flow of an official DeepDeep pl… | 参数 `pluginSetupParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginTools.kt:18；guidelines 3 条；插件准备流程入口；仅前台可用 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:79` | `ask_user_question` | 工具说明 | Ask the user one to five structured questions and wait fo… | 参数 `askUserQuestionParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:20；guidelines 3 条；questions 结构见 docs/TOOLS.md |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:93` | `get_goal` | 工具说明 | Read the goal for the current conversation, including its… | 参数 `getGoalParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:25；guidelines 2 条；目标 JSON 见 docs/TOOLS.md |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:117` | `create_goal` | 工具说明 | Create a goal for the current conversation. The goal is a… | 参数 `createGoalParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:30；guidelines 3 条；仅用户直接回合可创建 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:152` | `update_goal` | 工具说明 | Update the goal for the current conversation. The revisio… | 参数 `updateGoalParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:45；guidelines 5 条；revision/action 语义与 GoalService 耦合 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:78` | `job_start` | 工具说明 | Start a shell command as a background job and return its … | 参数 `jobStartParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:15；guidelines 2 条；前台 bash 与后台 job 的分工见 docs/TOOLS.md |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:108` | `job_output` | 工具说明 | Read output produced by a background job since the previo… | 参数 `jobOutputParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:30；guidelines 3 条 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:139` | `job_list` | 工具说明 | List background jobs started in the current conversation … | 参数 `jobListParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:49；guidelines 1 条 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:158` | `job_kill` | 工具说明 | Request cancellation of a running background job. The job… | 参数 `jobKillParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:54；guidelines 1 条；完成/中断文案见 §8 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:131` | `schedule_create` | 工具说明 | Create a reminder bound to the current conversation. When… | 参数 `createParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:31；guidelines 3 条；提醒注入正文见 §8 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:168` | `schedule_list` | 工具说明 | List the reminders bound to the current conversation with… | 参数 `listParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:106；guidelines 1 条 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:192` | `schedule_update` | 工具说明 | Update a reminder bound to the current conversation. Only… | 参数 `updateParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:66；guidelines 1 条 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:226` | `schedule_delete` | 工具说明 | Delete a reminder bound to the current conversation by id. | 参数 `deleteParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:111；guidelines 1 条 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:99` | `present` | 工具说明 | Deliver one to four finished works to the user: files, im… | 参数 `presentParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:47；guidelines 5 条；items 与聊天气泡卡片解析耦合 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:62` | `workshop_create` | 工具说明 | Create a Workshop mini app draft: a self-contained HTML/C… | 参数 `workshopCreateParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:19；guidelines 6 条；工坊页面规范见 §5 workshop 技能 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:99` | `workshop_load` | 工具说明 | Load a Workshop draft folder from the build folder as a m… | 参数 `workshopLoadParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:38；guidelines 3 条 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:134` | `workshop_list` | 工具说明 | List loaded Workshop mini apps and the current drafts wit… | 参数 `workshopListParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:49；guidelines 2 条 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:231` | `workshop_edit` | 工具说明 | Read or edit one JSON value in a Workshop app's persisted… | 参数 `workshopEditParameters` @ runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:190；guidelines 7 条；单工具承载 get/set/delete/keys，勿再拆回多个数据工具 |

## 3. app 插件工具说明

仅在对应插件已安装且启用时注入；`parameters` 字段级描述同样模型可见。

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:67` | `web_search` | 工具说明 | Search the web for current information and return ranked … | 参数 `searchParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:26；guidelines 4 条；外部内容只作资料的约束在 guidelines |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:90` | `web_fetch` | 工具说明 | Fetch one web page and return its main content as Markdow… | 参数 `fetchParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:47；guidelines 3 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:140` | `arrange_calendar` | 工具说明 | Read, create, update and delete calendar events through t… | 参数 `calendarParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:40；guidelines 5 条；日期相对解析规则在 calendarGuidelines |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:248` | `gui_observe` | 工具说明 | Read a trimmed snapshot of the current screen through the… | 参数 `observeParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:40；guidelines 6 条；安全红线同见 §4.2 deepdeep-gui 技能 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:279` | `gui_find` | 工具说明 | Find nodes on the current screen by text, desc, resourceI… | 参数 `findParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:65；guidelines 1 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:316` | `gui_tap` | 工具说明 | Tap or long-press a node on the current screen by ref, te… | 参数 `tapParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:93；guidelines 1 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:340` | `gui_set_text` | 工具说明 | Focus an editable field on the current screen, replace it… | 参数 `setTextParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:133；guidelines 2 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:375` | `gui_scroll` | 工具说明 | Scroll the current screen or a scrollable node in the giv… | 参数 `scrollParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:152；guidelines 1 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:406` | `gui_global` | 工具说明 | Perform a global system action: back, home, recents or th… | 参数 `globalParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:183；guidelines 1 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:432` | `gui_wait` | 工具说明 | Wait until the current screen shows a package or text, or… | 参数 `waitParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:200；guidelines 2 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:485` | `gui_screenshot` | 工具说明 | Capture the current screen as a PNG file and return its h… | 参数 `screenshotParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:227；guidelines 2 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:94` | `mi_cloud_account` | 工具说明 | Read the signed-in Xiaomi Cloud account profile or storag… | 参数 `accountParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:934；guidelines 2 条；只读 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:122` | `mi_cloud_notes` | 工具说明 | List, read, create, update or delete notes in Xiaomi Clou… | 参数 `notesParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:941；guidelines 5 条；写前确认；功能开关见 micloud 设置 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:208` | `mi_cloud_todo` | 工具说明 | List, create, update or delete to-do items in Xiaomi Clou… | 参数 `todoParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:959；guidelines 4 条 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:285` | `mi_cloud_sms` | 工具说明 | List recent SMS threads (optionally with call records) fr… | 参数 `smsParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:975；guidelines 3 条；只读 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:324` | `mi_cloud_contacts` | 工具说明 | List or search contacts in Xiaomi Cloud. Read-only. | 参数 `contactsParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:982；guidelines 3 条；只读 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:357` | `mi_cloud_gallery` | 工具说明 | List Xiaomi Cloud gallery photos/videos or albums. Read-o… | 参数 `galleryParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:991；guidelines 4 条；只读 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:408` | `mi_cloud_recordings` | 工具说明 | List or download call recordings stored in Xiaomi Cloud. … | 参数 `recordingsParameters` @ app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:999；guidelines 4 条 |

## 4. 插件贡献文案（summary / promptBody / 技能）

插件文案由 `PluginManager` 与本插件自身提供；promptBody 进入系统提示 `<official_plugins>` 段，技能 markdown 写入仓库与生效目录。

### 4.1 插件框架

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:61` | OfficialPlugin.promptBody | 插件文案·接口 | 插件可选覆盖；返回 null 则不注入 | Ready/未就绪两分支各自措辞；改后重开会话生效 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:122` | PluginSkill | 插件文案·技能声明 | skillId + markdown：插件技能原文 | 技能随安装写入 files/skills 与 files/agent/skills |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:353` | PluginManager.promptBody | 插件文案·收集 | 拼接已启用插件的 promptBody | 与 status() 刷新时机绑定（进插件页/回前台/轮末） |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:77` | PluginSkillStore.ensureRepoSkill | 插件文案·技能落盘 | 仓库副本写入 files/skills/<id> | 禁用插件只移除生效副本 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:91` | PluginSkillStore.writeSkill | 插件文案·技能落盘 | 写入 SKILL.md 到指定目录 | 校验空内容；改格式注意 .deepdeep-plugin 标记 |

### 4.2 各插件

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `app/src/main/res/values/strings_plugin_feishu.xml:2` | plugin_feishu_name / summary | 插件文案·名称摘要 | 飞书 · 消息、日历、云文档与邮箱 | 同步 values-en/strings_plugin_feishu.xml |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:294` | FeishuPlugin.promptBody | 插件文案·系统提示 | 已连接时：lark-cli、--as user、写前确认、导出兜底 | Ready/未连接两分支；missing scopes 动态拼接 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:409` | deepdeep-feishu 技能 | 插件文案·技能 | 飞书 CLI 用法速查：消息/日历/云文档/邮箱… | Kotlin 常量，改后需重装插件或重启 App 同步到 files/skills |
| `app/src/main/res/values/strings_plugin_wecom.xml:2` | plugin_wecom_name / summary | 插件文案·名称摘要 | 企业微信 · 消息、邮件、文档、日程与待办 | 同步 values-en |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:198` | WecomPlugin.promptBody | 插件文案·系统提示 | 已连接时：wecom-cli 只读优先、写前确认、禁止 auth init | Ready/未连接两分支 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:306` | deepdeep-wecom 技能 | 插件文案·技能 | 企业微信 CLI 用法速查… | Kotlin 常量，重装/重启同步 |
| `app/src/main/res/values/strings_web_search.xml:2` | plugin_web_search_name / summary | 插件文案·名称摘要 | 网络搜索 · 联网搜索与网页抓取 | 同步 values-en；本插件无技能 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:61` | WebSearchPlugin.promptBody | 插件文案·系统提示 | 已可用：web_search/web_fetch 参数范围与来源引用 | Key 未配置分支提示去提供商设置 |
| `app/src/main/res/values/strings_plugin_gui.xml:2` | plugin_gui_name / summary | 插件文案·名称摘要 | 界面操作 · 查看、操作并截图当前界面 | 同步 values-en |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:133` | deepdeep-gui 技能 | 插件文案·技能 | 无障碍观察/操作：ref、选择器优先级、安全红线… | 无 promptBody，提示全部由工具 guidelines + 本技能承担 |
| `app/src/main/res/values/strings_plugin_arrange.xml:2` | plugin_arrange_name / summary | 插件文案·名称摘要 | 安排 · 创建、读取、修改、删除系统日历事件与提醒 | 同步 values-en |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:79` | ArrangePlugin.promptBody | 插件文案·系统提示 | 安排插件已可用：arrange_calendar 动作与权限说明 | 常量 PROMPT_BODY，与 promptBody 同文件 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:97` | deepdeep-arrange 技能 | 插件文案·技能 | 系统日历事件与提醒的用法… | Kotlin 常量，重装/重启同步 |
| `app/src/main/res/values/strings_plugin_micloud.xml:2` | plugin_micloud_name / summary | 插件文案·名称摘要 | 小米云服务 · 笔记/待办/相册/短信/联系人/录音 | 同步 values-en |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:171` | MiCloudPlugin.promptBody | 插件文案·系统提示 | 已登录：已开启功能列表、写前确认、401 重新登录 | 动态拼接已开启开关；未登录分支 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:281` | deepdeep-micloud 技能 | 插件文案·技能 | 小米云数据读写与边界… | Kotlin 常量，重装/重启同步 |
| `app/src/main/res/values/strings_plugins.xml:58` | plugins_internal_workshop_name / summary | 插件文案·内部插件 | 创意工坊（UI 名称/摘要，模型不可见） | 内部插件 workshop 的 UI 文案；技能正文见 §5 |

## 5. 技能正文与运行期位置

技能列表（name+description+location）由 SDK 注入系统提示的 `<available_skills>`；技能正文只在 `/skill:<name>` 或模型按需 read 时进入上下文。内置 workshop 技能由内部插件「创意工坊」（`plugin/internal/WorkshopPlugin.kt`）贡献：SKILL.md 作为技能正文，references/examples 作为技能附件，经 PluginSkillStore 写入仓库/生效目录；`BuiltinSkills` 已移除。

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `app/src/main/assets/skills/workshop/SKILL.md:3` | workshop · description | 技能·frontmatter | 在「创意工坊」创建/修改 HTML/CSS/JS 轻应用… | description 决定模型何时选技能，控制 60 字内；会随 App 更新刷新 |
| `app/src/main/assets/skills/workshop/SKILL.md:6` | workshop · 正文 | 技能·正文 | 创意工坊把 Agent 写的一个网页变成手机轻应用… | 正文 172 行，含文件夹格式/数据层/页面 Agent；改后需重启 App 刷新 assets 副本 |
| `app/src/main/assets/skills/workshop/references/api-reference.md:1` | api-reference.md | 技能·参考 | workshop bridge/数据层/页面 Agent API 参考 | 模型按需读取；路径相对 SKILL.md 目录 |
| `app/src/main/assets/skills/workshop/references/design-checklist.md:1` | design-checklist.md | 技能·参考 | 移动端布局/视觉/可访问性检查清单 | 同上 |
| `app/src/main/assets/skills/workshop/references/tutorial.md:1` | tutorial.md | 技能·参考 | 多步骤 Agent 工作台示例教程 | 同上 |
| `app/src/main/assets/skills/workshop/examples/01-hello.html:1` | examples/*.html | 技能·示例 | 4 个示例页面（hello/工作台/流水线/日报） | 示例仅在模型读取时进入上下文；不参与 <available_skills> |
| `app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:116` | skillBinds | 技能·目录挂载 | files/agent/skills→/skills；files/skills→/skill-repo | Linux 内可见可写路径；改挂载需同步系统提示 §1.2 与 docs/SKILLS.md |
| `app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:162` | SkillRepository | 技能·仓库 | 仓库/生效/项目三级目录的启停与自动入库 | 列表与导入校验见同文件 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/internal/WorkshopPlugin.kt:46` | WorkshopPlugin.skills | 技能·内置插件化 | 读取 assets/skills/workshop：SKILL.md 作正文，其余文件作技能附件 | skill_enabled 开关控制是否注入；legacyOwned=true 走旧目录认领/迁移 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/internal/WorkshopPlugin.kt:68` | readWorkshopSkillFiles | 技能·assets 读取 | 递归读取 assets/skills/workshop 全部文件（含 references/examples） | 与 collectWorkshopAssetFiles 同文件；改 assets 结构需同步这里 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/internal/WorkshopPlugin.kt:75` | collectWorkshopAssetFiles | 技能·附件收集 | 遍历 assets：SKILL.md 排首位、其余按路径排序 | 返回 PluginSkillFile(path, content)；示例 html 也作为技能附件 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/plugin/internal/WorkshopPlugin.kt:62` | SKILL_ID=workshop | 技能·标识 | 技能名 workshop（/skill:workshop） | 改 id 会破坏用户习惯与 WorkShop 截图脚本 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:24` | SkillCatalog.expand | 技能·/skill: 加载 | 读取技能文件正文并渲染 <skill> 块 | 未知技能返回 null → UI 提示；正文来自磁盘最新内容 |
| `app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:32` | /skill: 解析 | 技能·命令 | 解析 /skill:<name> 及参数 | 命令可出现在句中；代码块内不触发（同文件） |
| `app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:525` | expandAndSend / expandInlineAndSend | 技能·命令执行 | 展开技能块后作为本轮 prompt 发送 | 多技能提及按出现顺序拼块 |
| `docs/SKILLS.md:5` | 技能作用域说明 | 技能·文档 | 仓库/全局/项目三级目录与 SDK 读取规则 | 与代码同步维护（文档非模型可见） |

## 6. MCP

服务器说明（mcp.json 的 description 或服务器自身 instructions）会进入系统提示；MCP 工具名/描述来自外部服务器，只在 tool_search 或加载后进入上下文。

### 6.1 mcp.json 说明字段与系统提示注入

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `pi2kotlin/pi-mcp/src/commonMain/kotlin/dev/pi/mcp/config/McpConfigParser.kt:134` | mcpServers[].description | MCP·配置字段 | 服务器条目可选 description（用户自填，可中文） | 解析后进入 McpServerStatus.description |
| `pi2kotlin/pi-mcp/src/commonMain/kotlin/dev/pi/mcp/config/McpServerConfig.kt:36` | McpServerConfig.description | MCP·模型字段 | 服务器描述字段（用户/配置提供） | 只读；写回见 DeepDeep McpConfigStore |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:200` | McpConfigStore · description 写回 | MCP·配置持久化 | 编辑保存时把 description 写回 files/agent/mcp.json | 页面改说明文案走 UI 字符串与编辑框 hint |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpManager.kt:506` | McpServerStatus.description | MCP·状态说明 | 优先用户 description，否则服务器 instructions | 连接后才拿得到 instructions |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpSection.kt:11` | <mcp_servers> 段正文 | MCP·系统提示 | …connected but their tools are not declared upfront. Use … | 仅在 deferred/codemode 服务器存在时注入 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpSection.kt:23` | descriptionOrInstructions | MCP·系统提示 | description → instructions → lastError 依次回退 | 无任何说明时显示 MCP server |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpSection.kt:27` | firstLine/200 截断 | MCP·系统提示 | 只取首行并截断 200 字 | 服务器说明过长时的唯一截断点 |

### 6.2 MCP 页面文案（strings_mcp.xml，UI 文案）

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `app/src/main/res/values/strings_mcp.xml:7` | mcp_import_template | MCP·UI 文案 | {"mcpServers":{"name":{"type":"streamablehttp",…}}} | 导入框占位示例；同步 values-en/strings_mcp.xml |
| `app/src/main/res/values/strings_mcp.xml:8` | mcp_import_error | MCP·UI 文案 | 导入失败，请检查 JSON 格式和必填字段 | 同步 values-en |
| `app/src/main/res/values/strings_mcp.xml:12` | mcp_status_*（12-17 行） | MCP·UI 文案 | 已连接·N 个工具/需要登录/连接失败/未测试/已停用/Android 不支持 | 状态文案整组同步 values-en |
| `app/src/main/res/values/strings_mcp.xml:26` | mcp_description_label / hint | MCP·UI 文案 | 说明 / 可选 | 用户填写的 description 即 §6.1 的模型可见说明 |
| `app/src/main/res/values/strings_mcp.xml:31` | mcp_stdio_hint | MCP·UI 文案 | 本地 STDIO 服务器在 Android 上不可用… | STDIO 保留但标记不支持 |
| `app/src/main/res/values/strings_mcp.xml:46` | mcp_oauth_waiting | MCP·UI 文案 | 在浏览器中完成授权，完成后会自动回到 DeepDeep | OAuth 等待提示 |
| `app/src/main/res/values/strings_mcp.xml:48` | mcp_oauth_manual_hint | MCP·UI 文案 | 没有收到回调？粘贴浏览器地址栏里的完整链接。 | 手动回调兜底 |
| `app/src/main/res/values/strings_mcp.xml:65` | mcp_search_placeholder | MCP·UI 文案 | 搜索服务器 | 搜索栏占位文案；同步 values-en |

### 6.3 tool_search 与 MCP 工具语义（SDK）

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpToolSearch.kt:22` | tool_search（名称） | SDK·MCP 工具 | 延迟工具检索入口；搜索后才在下一轮可用 | 仅检索未加载工具；已加载工具不再出现 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpToolSearch.kt:27` | TOOL_SEARCH_DESCRIPTION | SDK·MCP 工具说明 | # Tool discovery…BM25 检索延迟工具元数据，为下一轮暴露命中工具 | 描述进模型上下文；改语义需同步 McpSection 段正文 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpToolSearch.kt:197` | tool_search.promptSnippet | SDK·MCP 工具 | Search deferred tools and load matches for the next model… | guidelines 为 null，提示只靠 description/snippet |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpToolSearch.kt:124` | McpToolSearch 语义 | SDK·MCP 工具 | BM25 + 停用词/CJK 切分；命中结果本轮加载、下一轮可调 | 阈值/上限常量同文件（同目录） |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpToolAdapter.kt:53` | MCP 工具 description | SDK·MCP 工具（外部） | 服务器给的 description，空则用 fallback，超长截断 | 第三方文案不在我们控制内；fallback 是我们的 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpToolAdapter.kt:119` | mcp__<server>__<tool> | SDK·MCP 命名 | 工具名规则（非字母数字转下划线） | 改规则会破坏历史会话工具名 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpResourceTools.kt:32` | list_mcp_resources | SDK·MCP 工具说明 | Lists resources provided by MCP servers… | 服务器声明 resources 时自动提供 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpResourceTools.kt:37` | list_mcp_resource_templates | SDK·MCP 工具说明 | Lists resource templates… | 同上 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/mcp/McpResourceTools.kt:42` | read_mcp_resource | SDK·MCP 工具说明 | Read a specific resource from an MCP server… | 同上 |

## 7. SDK 侧（pi2kotlin 0.2.4）

DeepDeep 固定引用 pi2kotlin；下列文案由 SDK 注入，改动需改 pi2kotlin 并重新发布，或由 DeepDeep 覆写/传参。

### 7.1 基础系统提示

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:32` | DEFAULT_SYSTEM_PROMPT_PREAMBLE | SDK·preamble | You are an expert coding assistant operating inside pi… | DeepDeep 用 SYSTEM.md 覆盖（preamble 非空时不生成 tools/rules 段） |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:36` | TOOLS_TRAILER | SDK·tools 段 | In addition to the tools above, you may have access to ot… | 仅在无 preamble 覆盖时出现 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:41` | RULE_CONCISE | SDK·规则 | Be concise in your responses | 与 DeepDeep §1.1 同名规则去重 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:42` | RULE_FILE_PATHS | SDK·规则 | Show file paths clearly when working with files | 同上 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:44` | buildSystemPrompt | SDK·系统提示总装 | 按 preamble/tools/rules/docs/cwd/project_context/skills/cu… | 段名白名单校验：^[a-z][a-z0-9_-]*$ |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:78` | <cwd> | SDK·环境 | 当前工作区路径（模型用它拼绝对路径） | DeepDeep 的多工作区切换会改变它 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:138` | <project_instructions> | SDK·项目指令 | Project-specific instructions and guidelines:… | AGENTS.md/CLAUDE.md 内容逐个包裹 path |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:147` | buildRules | SDK·规则段 | 工具 guidelines + 文件操作建议 + 两条固定规则 | 规则去重；工具描述见 §7.2 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/SystemPrompt.kt:179` | buildDocsSection | SDK·文档段 | Pi documentation…（未使用） | DeepDeep 传 docsPaths=emptyList，故不出现 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/ResourceLoader.kt:50` | CONTEXT_FILE_NAMES | SDK·项目指令 | AGENTS.override.md/AGENTS.md/CLAUDE.md | 从工作区向上发现；DeepDeep 走 projectTrusted=true |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/ResourceLoader.kt:86` | loadSystemPromptOverride | SDK·SYSTEM.md | 读取 <cwd>/.pi/SYSTEM.md 或 files/agent/SYSTEM.md | 与 DeepDeep §1.1 联动 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/ResourceLoader.kt:96` | loadAppendSystemPrompt | SDK·APPEND_SYSTEM.md | 追加系统提示（用户可自放） | DeepDeep 未写入该文件，用户手工放才生效 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/AgentSdk.kt:242` | buildFreshSections | SDK·preamble 接线 | 把 SYSTEM.md 作为 preamble，extra 段随 initialSystemPromptSecti… | DeepDeep 的 extraSystemPromptSections=buildToolRuleSection |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/settings/TrustManager.kt:22` | TRUST_FILES | SDK·信任白名单 | SYSTEM.md、APPEND_SYSTEM.md 等参与项目信任判定 | 改文件名需同步 |

### 7.2 原生工具说明（description 直接进入每轮工具声明）

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/ReadTool.kt:48` | `read` | 工具说明 | Read the contents of a file. Supports text files and imag… | 参数 `readParameters` @ pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/ReadTool.kt:27；guidelines 1 条；支持图片附件；truncate 规则与参数同文件 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/WriteTool.kt:29` | `write` | 工具说明 | Write content to a file. Creates the file if it doesn't e… | 参数 `writeParameters` @ pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/WriteTool.kt:12；guidelines 1 条 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/EditTool.kt:51` | `edit` | 工具说明 | Edit a single file using exact text replacement. Every ed… | 参数 `editParameters` @ pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/EditTool.kt:20；guidelines 4 条 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/BashTool.kt:48` | `bash` | 工具说明 | Execute a bash command in the current working directory. … | 参数 `bashParameters` @ pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/BashTool.kt:31；guidelines 1 条；前台阻塞；后台任务用 DeepDeep job_*（§2） |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/GrepTool.kt:65` | `grep` | 工具说明 | Search file contents for a pattern. Returns matching line… | 参数 `toolsGrepParameters` @ pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/GrepTool.kt:24；guidelines 0 条 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/FindTool.kt:38` | `find` | 工具说明 | Search for files by glob pattern. Returns matching file p… | 参数 `findParameters` @ pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/FindTool.kt:17；guidelines 0 条 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/LsTool.kt:29` | `ls` | 工具说明 | List directory contents. Returns entries sorted alphabeti… | 参数 `lsParameters` @ pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/tools/LsTool.kt:13；guidelines 0 条 |

### 7.3 压缩 / 摘要提示

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:4` | SUMMARIZATION_SYSTEM_PROMPT | SDK·摘要 | You are a context summarization assistant… | 摘要模型 system prompt；只输出摘要 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:6` | SUMMARIZATION_PROMPT | SDK·摘要 | Create a structured context checkpoint summary…（Goal/Prog… | 改格式会影响压缩后上下文可用性 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:8` | UPDATE_SUMMARIZATION_INSTRUCTIONS | SDK·摘要 | Update the existing structured summary…PRESERVE/ADD/UPDAT… | 增量压缩用 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:24` | UPDATE_SUMMARIZATION_PROMPT | SDK·摘要 | The messages above are NEW conversation messages to incor… | 增量压缩实际发给模型的提示（含 INSTRUCTIONS） |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:10` | TURN_PREFIX_SUMMARIZATION_PROMPT | SDK·摘要 | …earlier context from an ongoing conversation… | split-turn 前缀摘要 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:14` | BRANCH_SUMMARY_PROMPT | SDK·摘要 | Create a structured summary of this conversation branch… | 分支切换摘要 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:16` | COMPACTION_SUMMARY_PREFIX / SUFFIX | SDK·注入 | The conversation history before this point was compacted…… | 压缩摘要作为 UserMessage 回放给模型 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:20` | BRANCH_SUMMARY_PREFIX / SUFFIX | SDK·注入 | The following is a summary of a branch…<summary> | 分支摘要回放 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:28` | SPLIT_TURN_MARKER | SDK·摘要 | **Turn Context (split turn):** | split-turn 拼接分隔 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:30` | CONVERSATION_OPEN / CLOSE | SDK·摘要 | <conversation>…</conversation> | 序列化对话包裹标签 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:32` | PREVIOUS_SUMMARY_OPEN / CLOSE | SDK·摘要 | <previous-summary>…</previous-summary> | 增量摘要输入包裹 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:35` | TURN_PREFIX_INPUT_PREFIX / INSTRUCTIONS_PREFIX | SDK·摘要 | # Conversation / # Instructions | split-turn 输入分节 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/SummaryPrompts.kt:37` | NO_PRIOR_HISTORY | SDK·摘要 | No prior history. | 历史为空时占位 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/Projection.kt:67` | CompactionEntry → UserMessage | SDK·注入 | 压缩摘要包装后作为用户消息回放 | 压缩后请求不再含被摘要前缀 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/compaction/Projection.kt:53` | BranchSummaryEntry → UserMessage | SDK·注入 | 分支摘要包装后回放 | 同上 |

### 7.4 技能注入与 /skill:

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/Skill.kt:18` | renderAvailableSkills | SDK·技能 | 生成 <available_skills>（name/description/location） | 仅存在 read/bash 时注入；disable-model-invocation 不显示 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/Skill.kt:28` | available_skills 指引 | SDK·技能 | The following skills provide specialized instructions… | 正文按需 read，不进每轮上下文 |
| `pi2kotlin/pi-coding-agent/src/commonMain/kotlin/dev/pi/coding/prompt/Skill.kt:53` | renderSkillPromptBlock | SDK·技能 | <skill name location>References are relative to… | DeepDeep /skill: 用它展开正文 |

### 7.5 提醒 / 中断（SDK）

`pi2kotlin` 内 grep `reminder|提醒|interrupt|中断` 在 commonMain 无匹配：SDK 不提供提醒/中断提示文案，全部由宿主注入（见 §8 与 §9）。

## 8. 运行期注入消息（DeepDeep runtime，会作为用户消息进入模型上下文）

| 位置 | 标识 | 类别 | 当前内容（首句/摘要） | 修改提示 |
|---|---|---|---|---|
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:365` | reminderText | 注入消息·定时提醒 | [定时提醒] <名称>…这是你设置的定时提醒，现在到时间了。 | 多提醒合并成一条；改前缀需同步 HistoryMapper |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:18` | REMINDER_PREFIX | 注入消息·提醒解析 | [定时提醒] 前缀用于历史卡片识别 | 与 §8 reminderText 一致 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:267` | roundPrompt | 注入消息·目标轮 | <goal_round> Objective/Round…Continue working toward the … | 目标轮自动投递；文案决定模型是否续跑 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:530` | finishPrompt | 注入消息·后台任务 | background job <id> (<label>) finished [status: …]; read … | 仅开启「后台任务完成唤醒」时注入 |
| `runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:518` | INTERRUPTED_OUTPUT_NOTE | 注入消息·中断 | [上次运行被中断，结果未知] | 进程重启后任务输出标记 |

## 9. 不在本仓库控制内 / 已知缺口

**外部 / 用户可编辑，不随代码发布：**

- MCP 服务器名、`description`/`instructions` 正文、MCP 工具名与 description：来自用户配置或第三方服务器；我们只截断（`McpSection.kt` 首行 200 字、`McpToolAdapter.kt` description 上限）。
- 网页搜索结果/抓取正文、飞书/企微 CLI 的 `--doc`/`--schema` 输出、小米云接口字段：外部数据，模型按资料处理。
- 用户自填字段：`mcp.json` description、个人化昵称/长期偏好（`Personalization` 设置）、`files/agent/SYSTEM.md`/`APPEND_SYSTEM.md`、工作区 `AGENTS.md`/`CLAUDE.md`、用户上传技能包的 `description`。
- 模型/提供商名称与能力说明来自 provider 配置与 SDK 模型目录（`pi2kotlin` 的 Model 定义）。
- Android 系统权限弹窗/系统设置文案、第三方 App 界面文案（GUI 工具观察到的内容）。

**本轮迁移后的口径：**

- workshop 数据工具已合并为单工具 `workshop_edit`（action=get/set/delete/keys），§2 已按新工具登记，旧四工具位置作废。
- 内置 workshop 技能已迁到内部插件 `workshop`（`plugin/internal/WorkshopPlugin.kt`，技能开关 + 最大回合设置）；`BuiltinSkills` 已移除，assets 仍是技能正文来源。
- 内部插件设置（工坊技能开关、工坊 Agent 最大回合、小米云功能开关等）会改变注入的工具/技能与 promptBody 拼接内容，修改 §4.2 摘要时以插件页当前设置为准。
- 纯 UI 文案（通知、toast、空态、按钮）不在本清单；见附录 A。

**已确认无对应文案：**

- `pi2kotlin` SDK 无提醒/中断提示文案（§7.5）；提醒、目标轮、后台任务注入都在 DeepDeep runtime（§8）。
- `gui` 插件无 `promptBody`；其提示由 §3 工具 guidelines 与 §4.2 `deepdeep-gui` 技能承担。
- 网络搜索插件无技能；飞书/企微/MiCloud/安排/工坊有技能。

## 附录 A. 纯 UI 文案位置（不计入清单条目）

所有按钮/标题/空态/状态/错误 toast 文案在资源文件，按功能拆分；每个文件都有 `values-en/` 英文同名文件。

| 文件 | 内容 |
|---|---|
| `app/src/main/res/values/strings.xml` | 通用/应用级文案 |
| `app/src/main/res/values/strings_chat.xml` | 聊天、输入框、停止/排队、压缩提示等 |
| `app/src/main/res/values/strings_drawer.xml` | 抽屉入口 |
| `app/src/main/res/values/strings_commands.xml` | 斜杠命令与命令面板 |
| `app/src/main/res/values/strings_files.xml` | 文件附件 |
| `app/src/main/res/values/strings_goal.xml` | 目标状态与按钮 |
| `app/src/main/res/values/strings_jobs.xml` | 后台任务列表/状态 |
| `app/src/main/res/values/strings_schedule.xml` | 定时提醒列表/编辑 |
| `app/src/main/res/values/strings_mcp.xml` | MCP 页面（其中说明类文案见 §6.2） |
| `app/src/main/res/values/strings_plugins.xml` | 插件页框架文案 |
| `app/src/main/res/values/strings_plugin_feishu.xml` | 飞书插件页 |
| `app/src/main/res/values/strings_plugin_wecom.xml` | 企业微信插件页 |
| `app/src/main/res/values/strings_plugin_gui.xml` | 界面操作插件页 |
| `app/src/main/res/values/strings_plugin_arrange.xml` | 安排插件页 |
| `app/src/main/res/values/strings_plugin_micloud.xml` | 小米云插件页 |
| `app/src/main/res/values/strings_plugin_micloud_features.xml` | 小米云功能开关 |
| `app/src/main/res/values/strings_web_search.xml` | 网络搜索插件页 |
| `app/src/main/res/values/strings_skills.xml` | 技能页 |
| `app/src/main/res/values/strings_workshop.xml` | 创意工坊页 |
| `app/src/main/res/values/strings_search.xml` | 会话搜索 |

---

生成说明：本文件由脚本按「标识锚点」在当前工作区逐条 grep 定位生成（非手工抄行号）；代码改动后行号会漂移，修改前以标识重新核对。
