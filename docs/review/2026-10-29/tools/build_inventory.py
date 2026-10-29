"""Rebuild the source atlas. Import edges are structural references, not a dynamic call graph."""
from pathlib import Path
import collections, hashlib, json, re, subprocess

ROOT = Path(__file__).resolve().parents[4]
OUT = ROOT / 'docs/review/2026-10-29'
DESCRIPTIONS = '''
AppGraph|应用的装配根。创建存储、Provider、Linux、插件、Host、提醒、目标和工坊服务，并通过工厂/回调注入依赖；这里的装配代码本身有必要，业务调度也进入这里则提高了耦合。
DeepDeepApplication|Android 进程入口。在 Application.onCreate 创建单个 AppGraph；因此依赖图中同步初始化的工作也处在启动关键路径。
MainActivity|Activity 入口。接收启动、系统助手、分享、OAuth 回调及通知跳转，启动前台服务，包裹语言/主题并交给 Compose 根界面。
DeepDeepApp|Compose 根界面和页面路由装配。收集应用状态，连接抽屉、聊天、设置、各类 sheet、插件交互与分享接收；这是界面协调中心，承担的业务副作用偏多。
AssistantRole|检查、申请和跳转 Android 系统默认助手角色，衔接系统 ASSIST 入口与语音助手界面。
AgentDocStore|读取/写入当前工作目录的 AGENTS 文档，处理 override 文件优先级，给工作区设置提供编辑内容。
AppPreferences|DataStore 中的聊天/界面偏好，包括自动重试、后台任务唤醒及工作区置顶等；通过 Flow 驱动界面和 AppGraph。
Attachment|附件类型、名称清洗、MIME 推断、大小格式化及聊天附件文本编码/解码。附件提示块是模型可读的文件引用。
AttachmentStore|从 Android 内容 URI 导入附件到当前工作区，解析模型给出的文件路径，并生成 FileProvider 分享/打开 Intent。
DevPackageCatalog|开发环境可安装软件包的目录及规格，提供设置页展示与安装器所需的固定描述。
DevPackageManager|在 Linux 中安装开发软件包并发布进度/通知。属于可选执行环境的生命周期，不能替代 Agent 会话调度。
FontPreferences|保存/读取字体选择，供主题和 Activity 初始字体使用。
LanguagePreferences|保存界面语言并包装 Android Context，让字符串资源随用户选择变化。
LegacyWorkspaceMigration|迁移旧 projects/工作区登记和相关会话路径，保证旧数据可以按当前 workspace 模型继续发现。
LinuxSettingsStore|保存 Linux 与共享存储开关，检查系统所有文件访问权限，决定后续会话是否注入 shell 及共享目录映射。
PersonalizationStore|保存称呼、交互风格、预设和长期指令，并转换成 runtime 的 Personalization 输入。
SharePayload|解析 SEND/SEND_MULTIPLE 的文本、标题、URI 列表与数量限制；是 Android Intent 接收层的纯数据规则。
ShareStaging|把外部分享的 URI 暂存成应用可用文件，再交给附件导入；处理跨应用临时权限及失败原因。
SkillArchive|解析技能 ZIP、验证元数据/路径/大小，构造 ParsedSkill；把外部文件内容变成可以安装的技能包。
SkillRepository|管理技能仓库、全局启用副本和工作区副本；支持 ZIP 安装、删除、同步 Agent 自建技能与原子目录替换。
StorageMigration|在依赖图初始化前迁移 workspace 到 home，并修复会话 JSONL 头和目录中的旧路径。
ThemePreferences|保存浅色/深色/跟随系统选择，主题通过 Flow 更新。
VoicePreferences|保存语音识别后是否发送、是否自动朗读等偏好，供语音 sheet 和主界面使用。
AndroidPluginStrings|把插件框架依赖的字符串接口映射到 Android 资源，避免 PluginManager 拼接具体系统字符串。
PluginApi|定义官方插件生命周期、状态、能力贡献、交互界面和进度接口。插件是编译进 App 的实现，下载的是各自需要的工具/资源。
PluginDownloader|HTTP 下载插件资源，设连接/读取超时及最大字节数，支持多个地址、SHA256 校验和临时文件发布。
PluginManager|统一安装/升级/卸载/启停/连接/状态刷新/技能贡献和 Agent setup 入口；全局 busy 只串行插件操作，不能保证会话运行同步。
PluginRecordStore|以 schemaVersion 2 JSON 持久化插件安装版本、启用状态和技能归属，使用 Mutex 与临时文件替换。
PluginSkillStore|同步插件附带的技能到仓库/激活目录，以 .deepdeep-plugin 标记所有者，防止覆盖其他技能。
PluginState|插件列表、详情、进度、交互 sheet 的状态模型，以及简化的数字版本比较规则。
ArrangeCalendar|系统 CalendarContract 的网关、日历/事件数据类型和时间范围合并规则。执行真实系统日历 CRUD 和提醒写入。
ArrangePermissionActivity|透明 Activity 接收日历权限申请，完成后返回插件设置流程。
ArrangePlugin|把日历网关接入官方插件框架，显示权限/可用状态并贡献 arrange_calendar 工具。
ArrangeRequests|日历输入字符串与日期时间之间的解析、格式化和文本长度规则；当前全天日期与 UTC 转换链存在偏移问题。
ArrangeTools|arrange_calendar 的模型参数契约与 action 分发，把 calendars/list/get/create/update/delete 转成网关调用并返回 JSON。
FeishuCli|构造 lark-cli 的版本/配置/授权/退出命令，解析 CLI 混合文本中的 JSON、账号和授权结果。
FeishuPlugin|下载并安装飞书 CLI，执行环境检查、授权轮询、状态探测、卸载和技能/提示词贡献。
GuiAccessibilityActions|把 GuiActions 抽象转换成 Android 无障碍节点操作、手势、窗口观察与截图，处理主线程和系统回调。
GuiActions|GUI 操作的抽象接口与请求/结果类型，让工具定义不必直接依赖 AccessibilityService。
GuiNodeTree|构建有深度/节点数量上限的 GUI 快照、节点引用、选择器匹配与 JSON 渲染，供观察和定位工具使用。
GuiPlugin|GUI 插件状态与无障碍/后台保活设置入口；贡献 GUI 工具和使用技能。
GuiTools|gui_observe/find/tap/set_text/scroll/global/wait/screenshot 的参数验证和分发，把模型操作转换成 GuiActions。
MiCloudClient|携带已登录 Cookie 的小米云 JSON GET 与资源下载；负责鉴权失效识别、网络超时及响应解析。
MiCloudCookieStore|通过统一加密秘密存储保存小米云 Cookie，并提取 userId/serviceToken，避免把登录态放进普通配置文件。
MiCloudFeatureSection|小米云插件详情中的功能开关界面，向用户暴露按数据领域启停的能力。
MiCloudLoginActivity|WebView 登录 i.mi.com、轮询并验证 Cookie、处理登录状态和界面适配。
MiCloudParsers|解析 Cookie、云服务响应和功能相关结构，给客户端/工具提供纯数据转换。
MiCloudPlugin|将小米云登录、按功能授权开关、工具生成与能力变更通知连接到官方插件框架。
MiCloudPreferences|持久化七类小米云功能开关。笔记/待办开关包含读写，其余功能按当前契约只读。
MiCloudTools|各小米云数据域的工具参数、API 请求拼装、读写 action、裁剪和 JSON 结果规范；1008 行集中多个领域，宜按领域拆分。
MiCloudWebWriter|在 i.mi.com 同源 WebView 中执行写请求，通过带请求标识的 JS bridge 返回结果；限制来源、加锁及超时。
DeepSeekWebProvider|DeepSeek 搜索/抓取 HTTP 实现，复用提供商 API Key，解析搜索结果并限制返回内容长度。
WebProvider|搜索/网页抓取的提供者接口、结果类型与数量/内容上限，为替换服务提供接缝。
WebSearchPlugin|不依赖 Linux 的搜索插件，检查 DeepSeek Key 并贡献搜索/抓取工具。
WebTools|web_search/web_fetch 的参数、调用与结构化输出，把模型调用转换为 WebProvider 请求。
WecomCli|企业微信 CLI 的安装/版本/授权命令及输出解析，隔离 CLI 协议细节。
WecomPlugin|下载/安装官方企业微信 CLI、扫码授权、连接状态和插件技能管理。
WecomRuntime|企业微信插件的 Linux 执行抽象与 Android 实现，支持测试替身以及安装暂存工作目录。
AgentService|specialUse 前台服务。启动常驻通知、Host 和提醒调度，监听时钟/时区变化，记录退出原因并检查无障碍连接。
AppVisibility|跟踪 App 是否在前台，并提供回到前台的系统入口；提问和插件交互依赖此状态判断是否可展示。
GuiAccessibilityService|无障碍服务生命周期与当前窗口信息，把服务实例交给 GuiAccessibilityState。
GuiAccessibilityState|保存/刷新无障碍服务是否开启和连接，提供插件状态与保活故障诊断。
KeepAlive|检查电池优化/后台限制并跳转系统或小米设置页，辅助用户配置后台运行条件。
LinuxJobRunner|把后台任务抽象转为 Linux 的流式长命令执行，最大执行时间为 24 小时。
NotificationCenter|统一常驻运行状态、后台完成、安装进度和无障碍异常通知，连接会话跳转与停止动作。
ProcessExitLog|读取 ApplicationExitInfo 并记录退出原因，支持判断后台任务因系统杀进程中断的原因。
DeepDeepDrawer|工作区分组会话列表、置顶、抽屉导航和操作菜单的 Compose 界面。
AttachmentUi|附件缩略图、打开/分享和预览界面；包含图片缩略图缓存，减少重组时重复解码。
ChatActions|消息动作栏及复制/引用/分享等界面动作，独立于模型执行。
ChatCommands|识别人用 slash 命令，区分压缩、目标和技能调用，支持词边界与输入补全。
ChatComposer|输入框、附件入口、模型/思考选择、语音/发送/停止及 slash 面板的界面组合。
ChatMarkdown|把 MarkdownBlock/InlineNode 映射为 Compose 文本、列表、表格、代码等可见组件。
ChatScreen|聊天主布局，衔接消息列表、顶栏、输入区、滚动、交付卡、提问和当前运行状态。
ChatScrollController|消息列表滚动位置、跟随底部和用户主动滚动之间的协调，避免流式输出强行拉回。
ChatTopBar|聊天标题、工作区及更多操作入口，主要负责界面展示与动作回调。
ChatTurn|将一轮用户/助手消息与过程、最终文本、附件和交付卡组合成聊天显示单元。
ChatUiState|聊天全量界面状态、引用内容等类型，为 ViewModel 的 StateFlow 提供不可变快照。
ChatUiStrings|把运行活动/错误分类映射成本地化文案，同时决定哪些失败可自动重试。
ChatViewModel|聊天协调器：恢复/切换会话、发送、重试、附件、模型、技能、缓存、流式批处理和运行对账；职责范围已明显偏宽。
CodeHighlighter|代码 token 分析和语法着色规则，由 Markdown 代码块显示调用。
ContextMeter|上下文占用环及交互入口，显示 SessionUsage 中的估算 token 占比。
ConversationReducer|纯状态归约器，把运行事件转换成消息/步骤/错误/活动状态，使用 runId 过滤迟到事件。
InlineCodeText|行内代码/特殊文本的 Compose 展示，供 Markdown 渲染器复用。
JobsMeter|输入区附近的后台任务数量与状态入口，打开任务列表。
MarkdownImage|Markdown 图片加载/缩放/展示，连接文件或网络图片来源与 Compose。
MarkdownParser|自建 Markdown 块级和行内解析器，覆盖表格、代码、引用、列表、脚注、数学与文件引用；是独立算法模块但维护面很大。
PresentCard|解析 present 工具结果并展示文件、图片、HTML 和创意卡片，转交打开/分享动作。
ProcessSection|运行过程折叠区，按步骤呈现中间文本、推理和工具记录，结束后收起。
ReasoningCard|思考过程显示、计时与展开动画，复用设计层 motion。
SelectionActions|文本选择后的操作界面及动作回调，关联复制、引用等行为。
SelectionCoordinator|协调聊天中多文本区域选择，避免不同消息同时持有冲突选择状态。
SessionSearchHit|搜索结果命中数据类型，把会话信息与匹配片段传给搜索界面。
SkillInvocation|从输入中提取技能引用、忽略代码区域并拼接技能提示词，供发送前展开使用。
SlashPalette|slash 命令/技能候选面板与选择展示，配合 ChatCommands 只补全输入。
ToolCard|工具调用名称、参数、输出、错误、耗时和展开状态展示。
ToolNames|将运行时工具名和 MCP 工具名转换成界面友好名称。
UsageSheet|展示当前会话上下文/窗口和输入输出/缓存/费用统计。
WorkingIndicator|当前活动文字、spinner 和耗时的界面逻辑，包含计时格式化规则。
Haptics|共享触觉反馈调用，供菜单/点击等交互复用。
RelativeTime|相对时间显示转换，供会话/任务等列表复用。
FileBrowserSheet|浏览当前工作区目录，处理层级返回、选择文件和打开预览。
GoalBar|显示目标内容、轮次/阶段及暂停/继续/清除入口，动作交给目标服务。
JobsSheet|后台任务列表、输出轮询、停止确认及已结束任务清理界面。
McpEditor|添加/编辑 HTTP MCP 服务器配置、认证和参数，执行保存/删除/探测动作。
McpImport|解析并导入用户给出的 MCP 配置，保留可支持的 HTTP 连接信息。
McpOAuthSession|MCP 浏览器授权交互、loopback 回调与粘贴回退，承接 SDK OAuthInteraction。
McpScreen|MCP 服务器列表、启用/探测状态与编辑入口，连接配置存储和连接服务。
AppDestination|应用导航目标的封闭类型，定义可进入的页面及其参数。
AppNavigator|页面栈和前进/返回/重置规则，把导航与业务状态区分开。
AppScreenTransition|统一页面转场方向、持续时间和 Compose 动画，调用设计层 motion。
PluginDetailScreen|插件详情、状态、动作、保活/能力开关及安装/启停/升级入口。
PluginSetupSheet|展示统一插件安装确认/进度/二维码或授权地址等交互流程。
PluginsScreen|官方插件目录、已安装状态与详情导航。
AnsweredAskCard|历史提问的只读已回答摘要与展开内容，由 QuestionRecord 驱动。
AskQuestionCard|进行中的单选/多选/自定义提问交互和提交/跳过流程，连接 QuestionCenter。
QuestionRecord|解析 ask_user_question 工具输入输出，恢复可以展示的历史问答结构。
ScheduleDatePicker|提醒编辑器中的日期选择控件。
ScheduleDraft|提醒编辑草稿、输入有效性和模型转换，防止无效时间进入持久化层。
ScheduleEditor|创建/修改提醒的完整编辑界面，绑定会话与 trigger。
ScheduleScreen|提醒列表、状态、下次投递、开关、运行和编辑入口。
ScheduleTimePicker|提醒编辑器的时间选择控件。
ArchivedSessionsScreen|归档会话列表、打开和恢复入口，复用会话操作。
FilterSessionSearch|会话搜索的过滤/排序和匹配片段规则，是可独立测试的纯逻辑。
SessionActionMenu|会话长按/上下文菜单，提供重命名、置顶、归档和删除动作。
SessionDialogs|会话重命名、删除等共享对话框。
SessionSearchScreen|搜索输入、结果列表与命中高亮，加载与过滤分离。
ChatSettingsScreen|聊天展示、自动重试/后台唤醒等偏好界面。
DevPackagesSection|运行环境页面中的开发软件包列表和安装进度。
FontScreen|字体选择预览与保存界面。
LinuxEnvironmentSection|显示 Linux 安装/重置/启用/共享存储状态和操作。
OAuthLoginSession|Provider OAuth 登录交互状态机，承接浏览器、输入、选择和取消。
OAuthLoginSheet|Provider OAuth 状态的 Compose 展示与用户交互。
PersonalizationScreen|称呼、交互风格、长期指令等偏好编辑界面。
PrivacyScreen|隐私和数据相关信息页面。
ProviderDetailScreen|Provider API Key/OAuth 配置与退出登录，不负责替其他会话切换模型。
ProviderIcon|模型服务提供方图标映射与显示。
ProviderListScreen|已配置与全部 Provider 目录，进入对应凭据设置。
RuntimeSettingsScreen|运行环境总页面，组合 Linux 和开发软件包配置。
SettingsComponents|设置页复用的行、分组、说明和点击区域。
SettingsScreen|设置导航目录、外观/语言入口及配置状态摘要。
SkillsScreen|技能仓库列表、ZIP 导入、内容查看、全局启停/删除和安装反馈。
VoiceAssistantBody|语音助手 sheet 的输入/识别/发送/播放界面主体。
VoiceAssistantSheet|语音助手生命周期和依赖装配，连接识别、聊天状态、自动发送与朗读。
VoiceAssistantState|语音交互阶段、文本和发送/播放条件的纯状态规则。
WorkshopAssetLoader|给每个创意生成独立 HTTPS origin，解析安全路径、MIME、字节范围与文件响应，限制文件加载到该创意目录。
WorkshopBridgeHost|把可信主 frame 的 WebMessage 分发到 router，异步响应、40ms 合并流式事件，并在销毁时移除 bridge。
WorkshopChatCard|解析并展示对话中创意工坊的交付入口。
WorkshopDialogs|创意工坊的创建/重命名/删除等对话框。
WorkshopScreen|扫描创意目录、显示有效/无效条目并进入对应创意。
WorkshopViewerScreen|创意详情壳、数据存储生命周期、重载/改名/删除与 WebView 错误覆盖层。
WorkshopWebView|配置创意 WebView、安全文件设置、同源 JS 注入、外链处理、主题同步及渲染进程崩溃恢复。
AgentsDocSheet|工作区 AGENTS 文档编辑/保存 sheet。
ProjectSkillsSheet|工作区技能选择，区分全局已启用技能与工作区独立副本。
WorkspaceDialogs|新建、重命名和删除工作区的共享对话框。
WorkspaceSettingsScreen|当前工作区信息、AGENTS 文档和技能配置入口。
SpeechEvents|语音事件/错误枚举与系统错误码转换。
SpeechRecognizerController|在主线程管理系统 ASR，通过 callbackFlow 发布音量、部分文本、最终结果和错误，取消时销毁识别器。
SpeechText|把助手文本清理/分段成适合 TTS 朗读的内容。
TextSpeaker|TTS 初始化、语言选择、分段队列、完成回调和释放，跟踪可用/播放状态。
VoiceLanguage|将界面语言/系统语言映射成 ASR 语言与 TTS 候选 Locale。
AgentRuntimeHost|持有所有打开会话的 Handle，管理发送/停止/重试/提醒/后台轮、模型、压缩、MCP 重载和运行投影；当前没有覆盖全部入口的单会话串行命令队列。
ApiKeyStore|ApiKeyRecordStore 抽象及加密秘密存储实现，以 provider 为键持久化 API Key。
AppTools|会话工具装配点。收集提醒、提问、插件 setup、插件工具和额外工具工厂。
AskUserQuestionTool|模型提问参数验证、当前会话绑定、挂起等待和答案结构化回传。
BuiltinSkills|确保随 App 发布的内置技能在仓库/激活目录存在，管理版本/内容更新约定。
DeepDeepOAuthCallbackServerFactory|Android loopback OAuth 回调服务器，验证回调路径/参数并提供返回 App 的网页/深链。
EncryptedSecretStore|Android Keystore AES-GCM 加密与 DataStore secrets 存储，给 Key、OAuth 和 Cookie 提供统一底层。
GoalContracts|目标阶段、持久化目标、展示视图、轮次和控制器/驱动接口。
GoalService|目标生命周期与轮次策略，按会话保存目标并决定成功后是否自动续跑；armed 标志只在本次进程有效。
GoalStore|目标 JSON 读写/校验与原子文件替换；损坏内容会抛异常而不是静默变空。
GoalTools|create_goal/update_goal 的模型工具，绑定当前会话，执行创建/修改/暂停/完成/阻塞等操作。
HistoryMapper|把 SDK 的模型消息/工具结果合成聊天轮、过程步骤、失败状态和本地展示内容。
InstallationIdStore|每次安装生成并持久化稳定 UUID，供需要设备身份的 Provider OAuth 使用。
JobContracts|后台命令执行器、任务状态、快照、启动和增量读取结果以及控制器接口。
JobRegistry|运行后台命令、管理每会话 10 个并发上限、输出环形缓冲/游标/等待/停止及完成唤醒；记录仅在内存中。
JobTools|job_start/job_output/job_kill 的参数规则、会话绑定和增量输出契约。
McpConfigStore|读取/校验/保留未知字段并原子保存 agent/mcp.json，供 MCP 列表和 Host 热更新使用。
McpJson|MCP JSON 配置结构与字段转换辅助逻辑。
McpServices|独立 MCP 连接探测和 OAuth 登录/登出，给 UI 提供状态，不直接替代 SDK 会话内 MCP 生命周期。
ModelConfigStore|保存新会话默认 provider/model/thinking；现有会话模型由各自 JSONL 恢复。
Notices|目标轮、后台任务和压缩等通知模型与补充运行事件，连接运行系统与聊天呈现。
OAuthCredentialStore|OAuthRecordStore 抽象与加密原始 OAuth JSON 读写，保留扩展 token 字段。
Personalization|runtime 所需的个性化数据、环境/长期指令与动态提示词 section 构造。
PiSessionEngine|将工作目录、凭据、Provider、工具、提示词 hooks 和 MCP 配置装配成 SDK AgentSession；模型循环由 SDK 负责。
PluginContracts|runtime 看见的插件控制接口与 setup 结果，不依赖具体 Android 官方插件类。
PluginTools|plugin_setup 工具，把模型安装/连接请求导向统一 PluginController。
PresentTool|present 参数与文件/图片/HTML/创意交付契约，验证目标并产生可由聊天卡片恢复的结果。
ProviderCatalog|从 SDK 内置目录获取 Provider/模型/思考档位，解析默认/恢复配置并构建模型引用。
ProviderOAuthService|调用 SDK 内置 OAuth flow、传安装身份、保存登录凭据和退出登录。
QuestionCenter|按会话/工具调用登记待答问题，用 CompletableDeferred 等待，供界面回答/取消；当前等待状态不持久化。
RuntimeTypes|AgentRuntime/ProviderDirectory/ModelSettings 边界与会话、消息、工具、活动、运行事件的主要数据模型。
ScheduleCron|五段 cron 解析、字段校验与未来五年的候选时间查找；用于提醒的下一次投递时间。
ScheduleModels|提醒/trigger/错误的数据类型、JSON 编解码及 once/daily/weekly/cron 时间规则。
ScheduleService|监听存储/会话生命周期并按最近到期时间等待，批量合并同会话提醒、打开原会话并交给 Host 投递。
ScheduleStore|schemaVersion 2 提醒 JSON、Mutex、原子持久化、投递成功/失败状态与时间重新计算。
ScheduleTools|schedule_create/list/update/delete 的模型工具契约，绑定当前会话与工作区。
SdkCredentialStore|为同步 SDK CredentialStore 提供内存缓存，并异步写加密存储；同步缓存与异步持久化之间当前缺少按 Provider 顺序控制。
SessionBinding|会话创建前构造工具、创建后再附着 AgentSession 的小型绑定对象，避免工具工厂与 session 初始化循环。
SessionCatalog|跨已知 cwd 扫描 SDK JSONL，叠加标题/置顶/归档元数据，提供列表/搜索基础与删除。
SessionMetaStore|DataStore 中的会话标题覆盖、置顶与归档状态；不替代 SDK JSONL 消息权威。
SkillCatalog|从全局/当前工作区发现技能并支持显式技能展开，供 slash 面板与发送前注入。
SystemPrompt|首次生成 DeepDeep SYSTEM.md，构造当前工具的规则 section；区分 host 文件工具和 guest bash 工作路径。
UsageModels|会话 token/费用统计、手动压缩结果和 usage 事件的数据契约。
WorkshopAgent|工坊一次性 Agent 的请求、能力目录、模型、事件和 Gateway 接口，与 WebView 协议解耦。
WorkshopAgentService|每次工坊调用创建临时 cwd/agentDir，选择模型/技能/工具、运行 SDK 并在结束后删目录；限 180 秒/16 默认模型轮，但临时目录不等于权限边界。
WorkshopBridge|工坊 WebMessage 的 JSON 路由与参数限制，支持 info/data/ui/agent 方法；数据按当前创意绑定，Agent 能力另经 Gateway。
WorkshopDataStore|每个创意一个 SQLite KV 文件，按锁串行访问与关闭；网页和 Agent 数据工具复用同一数据抽象。
WorkshopModels|创意 manifest、条目、扫描/校验状态和 JSON 契约，限制 id/入口等字段。
WorkshopStore|创意 draft/apps/userdata 目录管理、manifest 校验、装载/改名/删除和目录边界保护。
WorkshopTools|workshop_create/load/list/data_* 等工具，把模型文件产物纳入创意目录并允许单独修改数据。
WorkspaceStore|DataStore 中工作区 id/name/cwd/createdAt 的登记与维护；Home 由 Host 额外提供。
LinuxEnvironment|Android Linux 环境门面：定位共享 rootfs/native binaries、安装器、PRoot 执行、DNS 和目录映射；每个 cwd 实例共享同一个 rootfs。
LinuxExecutor|Linux 执行接口与 exitCode/output/truncated/timedOut/duration 的结果契约。
LinuxInstallState|Linux 未安装、安装中、已安装（版本/大小）和失败状态类型。
LinuxRootfsInstaller|SHA256 校验、rootfs 解包/标记/激活/重置和状态读取；当前状态读取附带全目录大小扫描。
LinuxShellExecutor|将 SDK ShellExecutor 转成 LinuxEnvironment，映射取消信号、1..600 秒超时和截断信息。
PRootExecutor|构造 PRoot 参数/环境变量/挂载，启动命令线程，持续读取合并输出，以 watchdog 控制超时并杀进程树。
RootfsExtractor|解析 tar/gzip 内容，防止路径穿越和危险链接，把 rootfs 解到临时目录。
DdComponents|共享按钮、图标按钮、surface/行等基础 Compose 组件，减少业务页面各自定义交互外观。
DdControls|统一开关/选择等控件实现，使用设计主题与状态颜色。
DdDialog|共享对话框框架、标题/内容/操作布局。
DdDropdown|统一下拉菜单布局和交互。
DdIcons|集中图标定义与路径，向应用组件暴露一致图标类型。
DdMenu|统一上下文菜单和 destructive 操作样式。
DdMotion|共享动画时长、缓动与按压状态等 motion tokens。
DdSearchField|统一搜索输入组件。
DdSpinner|统一旋转加载指示器，使用 graphicsLayer 读取动画状态以减少重组。
DdTextField|共享文本输入组件及焦点/占位/错误等基础视觉规则。
DdTypingDots|统一输入中/等待中的点状动画。
DeepDeepTheme|全局颜色、字体、字号层级、主题模式及 Compose 主题桥接，给所有页面提供统一视觉来源。
'''
DESC = dict(line.split('|',1) for line in DESCRIPTIONS.strip().splitlines())
files=[]
for module in ['app','runtime','shell','design']:
    for p in sorted((ROOT/module/'src/main/kotlin').rglob('*.kt')):
        raw=p.read_text(); lines=raw.splitlines()
        if p.stem not in DESC: raise ValueError(f'Missing responsibility: {p}')
        declarations=[]
        for i,line in enumerate(lines,1):
            if re.match(r'^\s*(?:(?:public|private|internal|protected|data|sealed|enum|abstract|open|override|suspend|inline|tailrec|operator|infix|annotation|value)\s+)*(?:class|interface|object|fun|typealias)\s+',line):
                declarations.append({'line':i,'signature':line.strip()[:200]})
        imports=re.findall(r'^import\s+(io\.github\.whywood\.deepdeep\.[\w.]+)',raw,re.M)
        files.append({'path':str(p.relative_to(ROOT)),'absolute':str(p),'module':module,'package':re.search(r'^package (.+)',raw,re.M)[1],
          'name':p.name,'lines':len(lines),'role':DESC[p.stem],'declarations':declarations,'imports':imports,
          'android_imports':len(re.findall(r'^import android\.',raw,re.M)),'sdk_imports':len(re.findall(r'^import dev\.pi\.',raw,re.M)),
          'sha256':hashlib.sha256(raw.encode()).hexdigest()})
symbols={}
for f in files:
    for d in f['declarations']:
        m=re.search(r'(?:class|interface|object|fun|typealias)\s+(?:<[^>]+>\s*)?(\w+)',d['signature'])
        if m:symbols.setdefault(f['package']+'.'+m[1],set()).add(f['path'])
for f in files:
    f['dependencies']=sorted(set(p for imp in f['imports'] for p in symbols.get(imp,set()) if p!=f['path']))
    raw=(ROOT/f['path']).read_text()
    # Supplemental lexical references. Deliberately labeled as approximate, not a call graph.
    code=re.sub(r'/\*[\s\S]*?\*/|//[^\n]*|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"',' ',raw)
    tokens=set(re.findall(r'\b[A-Za-z_]\w*\b',code))
    f['package_refs']=sorted(set(p for name,paths in symbols.items()
        if name.rsplit('.',1)[0]==f['package'] and name.rsplit('.',1)[1] in tokens
        for p in paths if p!=f['path'] and p not in f['dependencies']))
for f in files:
    f['dependents']=sorted(x['path'] for x in files if f['path'] in x['dependencies'])
metrics={'head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'modules':{},
 'main_kotlin_files':len(files),'main_kotlin_lines':sum(f['lines'] for f in files),
 'tests_kotlin_files':len(list(ROOT.glob('*/src/test/**/*.kt'))),'android_test_files':len(list(ROOT.glob('*/src/androidTest/**/*.kt')))}
for m in ['app','runtime','shell','design']:
    fs=[f for f in files if f['module']==m]
    metrics['modules'][m]={'files':len(fs),'lines':sum(f['lines'] for f in fs),'android_imports':sum(f['android_imports'] for f in fs),'sdk_imports':sum(f['sdk_imports'] for f in fs)}
metrics['largest']=[{'path':f['path'],'lines':f['lines']} for f in sorted(files,key=lambda x:-x['lines'])[:25]]
metrics['most_imported']=[{'path':f['path'],'dependents':len(f['dependents'])} for f in sorted(files,key=lambda x:-len(x['dependents']))[:15]]
(OUT/'evidence/source-inventory.json').write_text(json.dumps({'metrics':metrics,'files':files},ensure_ascii=False,indent=2))

def link(f,line=1,label=None):return f"[{label or f['name']}]({f['absolute']}:{line})"
doc=['# 逐文件模块图册','',f"基准提交：`{metrics['head']}`。主代码 {len(files)} 个 Kotlin 文件、{metrics['main_kotlin_lines']:,} 个物理行。每个文件都有职责说明、声明入口、结构依赖和反向引用。",'',
'范围说明：这是完整文件清单与职责索引，不代表每一行都经过形式化验证。声明/引用由轻量词法扫描生成；import 边不等于运行调用边，同包引用、动态回调和外部 SDK 内部实现需要结合主报告时序图理解。主报告对风险集中的路径做了手工深查。','']
for module in ['app','runtime','shell','design']:
    doc.extend([f'## :{module}', ''])
    for f in [f for f in files if f['module']==module]:
        doc += [f"### {f['path'].split('/kotlin/',1)[1].replace('io/github/whywood/deepdeep/','')}",'',
          f"**职责**：{f['role']}",'',f"**规模**：{f['lines']} 行；{len(f['declarations'])} 个词法声明。源码：{link(f)}。",'', '**声明与运行入口**：','']
        for d in f['declarations']:
            doc.append(f"- {link(f,d['line'],'L'+str(d['line']))}：`{d['signature'].replace('`','').replace('|','/')}`")
        if not f['declarations']:doc.append('- 顶层值/扩展与辅助逻辑，见源码。')
        for title,key in [('显式内部引用','dependencies'),('被哪些文件显式引用','dependents')]:
            dep=f[key]
            doc += ['',f"**{title}**："+('、'.join(f'`{p}`' for p in dep) if dep else '没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。')]
        doc += ['', '**同包符号引用（词法候选，不是调用图）**：'+('、'.join(f'`{p}`' for p in f['package_refs']) or '未发现额外候选。')]
        doc += ['']
(OUT/'02-MODULE-ATLAS.md').write_text('\n'.join(doc))
print(json.dumps(metrics,ensure_ascii=False,indent=2))
