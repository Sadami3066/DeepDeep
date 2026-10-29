# 逐文件模块图册

基准提交：`bdbaa07c17c7ac075b81804794e88252a00fbb61`。主代码 230 个 Kotlin 文件、45,439 个物理行。每个文件都有职责说明、声明入口、结构依赖和反向引用。

范围说明：这是完整文件清单与职责索引，不代表每一行都经过形式化验证。声明/引用由轻量词法扫描生成；import 边不等于运行调用边，同包引用、动态回调和外部 SDK 内部实现需要结合主报告时序图理解。主报告对风险集中的路径做了手工深查。

## :app

### AppGraph.kt

**职责**：应用的装配根。创建存储、Provider、Linux、插件、Host、提醒、目标和工坊服务，并通过工厂/回调注入依赖；这里的装配代码本身有必要，业务调度也进入这里则提高了耦合。

**规模**：397 行；8 个词法声明。源码：[AppGraph.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:1)。

**声明与运行入口**：

- [L85](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:85)：`class AppGraph(context: Context) {`
- [L115](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:115)：`fun skillBinds(): List<Pair<File, String>> = listOf(`
- [L120](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:120)：`fun linuxBinds(): List<Pair<File, String>> = skillBinds() + listOf(`
- [L266](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:266)：`private fun linuxEnvironmentFor(cwd: String?): LinuxEnvironment {`
- [L359](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:359)：`suspend fun prepare() {`
- [L365](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:365)：`private fun ensureBuiltinSkills() {`
- [L376](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:376)：`private fun readSkillAssets(root: String): List<BuiltinSkillFile> {`
- [L385](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt:385)：`private suspend fun importDebugKey() {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LegacyWorkspaceMigration.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/ThemePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/VoicePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/AppVisibility.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/LinuxJobRunner.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxShellExecutor.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### DeepDeepApp.kt

**职责**：Compose 根界面和页面路由装配。收集应用状态，连接抽屉、聊天、设置、各类 sheet、插件交互与分享接收；这是界面协调中心，承担的业务副作用偏多。

**规模**：1036 行；11 个词法声明。源码：[DeepDeepApp.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:1)。

**声明与运行入口**：

- [L112](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:112)：`internal fun DeepDeepApp(`
- [L359](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:359)：`fun runGoalCommand(args: String) {`
- [L378](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:378)：`fun runChatCommand(command: ChatCommand, args: String) {`
- [L385](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:385)：`fun pickCommand(item: CommandItem) {`
- [L396](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:396)：`fun submitChatInput() {`
- [L411](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:411)：`fun closeDrawer() {`
- [L415](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:415)：`fun openWorkshopCreate() {`
- [L421](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:421)：`fun openChat() {`
- [L426](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:426)：`fun openSettings() {`
- [L431](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:431)：`fun back() {`
- [L1028](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt:1028)：`private fun shareChooser(context: Context, intent: Intent, title: String): Intent =`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SharePayload.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolNames.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/FontScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PrivacyScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApplication.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`

### DeepDeepApplication.kt

**职责**：Android 进程入口。在 Application.onCreate 创建单个 AppGraph；因此依赖图中同步初始化的工作也处在启动关键路径。

**规模**：13 行；2 个词法声明。源码：[DeepDeepApplication.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApplication.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApplication.kt:5)：`class DeepDeepApplication : Application() {`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApplication.kt:9)：`override fun onCreate() {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`

### MainActivity.kt

**职责**：Activity 入口。接收启动、系统助手、分享、OAuth 回调及通知跳转，启动前台服务，包裹语言/主题并交给 Compose 根界面。

**规模**：181 行；11 个词法声明。源码：[MainActivity.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:1)。

**声明与运行入口**：

- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:33)：`class MainActivity : ComponentActivity() {`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:39)：`override fun attachBaseContext(newBase: Context) {`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:43)：`override fun onCreate(savedInstanceState: Bundle?) {`
- [L78](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:78)：`override fun onNewIntent(intent: Intent) {`
- [L84](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:84)：`override fun onSaveInstanceState(outState: Bundle) {`
- [L94](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:94)：`override fun onStart() {`
- [L102](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:102)：`override fun onStop() {`
- [L107](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:107)：`private fun handleIntent(intent: Intent?) {`
- [L146](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:146)：`private fun restoreIntake(savedInstanceState: Bundle) {`
- [L161](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:161)：`private fun startAgentService() {`
- [L165](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt:165)：`private fun requestNotificationPermission() {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SharePayload.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/AppVisibility.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApplication.kt`

### assistant/AssistantRole.kt

**职责**：检查、申请和跳转 Android 系统默认助手角色，衔接系统 ASSIST 入口与语音助手界面。

**规模**：43 行；6 个词法声明。源码：[AssistantRole.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt:1)。

**声明与运行入口**：

- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt:9)：`internal object AssistantRole {`
- [L10](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt:10)：`fun isAvailable(context: Context): Boolean {`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt:15)：`fun isHeld(context: Context): Boolean {`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt:20)：`fun requestIntent(context: Context): Intent? {`
- [L28](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt:28)：`fun settingsIntent(context: Context): Intent {`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt:41)：`private fun roleManager(context: Context): RoleManager? =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### data/AgentDocStore.kt

**职责**：读取/写入当前工作目录的 AGENTS 文档，处理 override 文件优先级，给工作区设置提供编辑内容。

**规模**：107 行；7 个词法声明。源码：[AgentDocStore.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:1)。

**声明与运行入口**：

- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:16)：`data class AgentDoc(`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:22)：`class AgentDocStore(`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:25)：`suspend fun read(cwd: String): AgentDoc? = withContext(ioDispatcher) {`
- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:55)：`suspend fun save(cwd: String, content: String): Boolean = withContext(ioDispatcher) {`
- [L73](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:73)：`suspend fun delete(cwd: String): Boolean = withContext(ioDispatcher) {`
- [L87](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:87)：`private fun effectiveFile(directory: File): File {`
- [L93](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt:93)：`private fun writeAtomically(target: File, content: String) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`

### data/AppPreferences.kt

**职责**：DataStore 中的聊天/界面偏好，包括自动重试、后台任务唤醒及工作区置顶等；通过 Flow 驱动界面和 AppGraph。

**规模**：68 行；6 个词法声明。源码：[AppPreferences.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt:1)。

**声明与运行入口**：

- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt:16)：`data class UiPreferences(`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt:23)：`enum class UiToggle {`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt:29)：`class AppPreferences(context: Context) {`
- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt:49)：`suspend fun read(): UiPreferences = preferences.first()`
- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt:51)：`suspend fun set(toggle: UiToggle, enabled: Boolean) {`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt:61)：`suspend fun setWorkspacePinned(workspaceId: String, pinned: Boolean) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`

### data/Attachment.kt

**职责**：附件类型、名称清洗、MIME 推断、大小格式化及聊天附件文本编码/解码。附件提示块是模型可读的文件引用。

**规模**：125 行；9 个词法声明。源码：[Attachment.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:5)：`enum class AttachmentKind {`
- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:11)：`data class AttachmentRef(`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:23)：`internal fun attachmentKind(mime: String, name: String): AttachmentKind {`
- [L40](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:40)：`internal fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {`
- [L60](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:60)：`internal fun sanitizeAttachmentName(raw: String): String {`
- [L65](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:65)：`fun formatAttachmentSize(bytes: Long): String = when {`
- [L71](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:71)：`fun encodeAttachmentBlock(attachments: List<AttachmentRef>): String {`
- [L91](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:91)：`data class ParsedMessageText(`
- [L96](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt:96)：`fun parseAttachmentBlock(source: String): ParsedMessageText {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`

### data/AttachmentStore.kt

**职责**：从 Android 内容 URI 导入附件到当前工作区，解析模型给出的文件路径，并生成 FileProvider 分享/打开 Intent。

**规模**：162 行；18 个词法声明。源码：[AttachmentStore.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:1)。

**声明与运行入口**：

- [L14](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:14)：`sealed interface AttachmentImport {`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:15)：`data class Imported(val ref: AttachmentRef) : AttachmentImport`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:16)：`data object TooLarge : AttachmentImport`
- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:17)：`data object Failed : AttachmentImport`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:20)：`interface AttachmentStore {`
- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:21)：`suspend fun import(uri: Uri, root: File): AttachmentImport`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:22)：`fun file(ref: AttachmentRef, root: File): File`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:23)：`fun shareIntent(ref: AttachmentRef, root: File): Intent?`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:24)：`fun viewIntent(ref: AttachmentRef, root: File): Intent? = null`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:25)：`fun resolve(raw: String, root: File): AttachmentRef?`
- [L28](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:28)：`class FileAttachmentStore(context: Context, private val home: File) : AttachmentStore {`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:31)：`override suspend fun import(uri: Uri, root: File): AttachmentImport = withContext(Dispatchers.IO) {`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:61)：`override fun file(ref: AttachmentRef, root: File): File = File(root, ref.path)`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:63)：`override fun resolve(raw: String, root: File): AttachmentRef? {`
- [L109](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:109)：`override fun shareIntent(ref: AttachmentRef, root: File): Intent? {`
- [L123](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:123)：`override fun viewIntent(ref: AttachmentRef, root: File): Intent? {`
- [L135](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:135)：`private data class Meta(val name: String, val mime: String, val size: Long)`
- [L137](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt:137)：`private fun queryMeta(resolver: ContentResolver, uri: Uri): Meta? {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`

### data/DevPackageCatalog.kt

**职责**：开发环境可安装软件包的目录及规格，提供设置页展示与安装器所需的固定描述。

**规模**：98 行；6 个词法声明。源码：[DevPackageCatalog.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt:6)：`data class DevPackage(`
- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt:12)：`data class DevBundle(`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt:22)：`object DevPackageCatalog {`
- [L93](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt:93)：`fun packageById(id: String): DevPackage? = packagesById[id]`
- [L95](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt:95)：`fun bundleById(id: String): DevBundle? = bundles.firstOrNull { it.id == id }`
- [L97](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt:97)：`fun packageIds(bundle: DevBundle): Set<String> = bundle.packageIds.toSet()`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`

### data/DevPackageManager.kt

**职责**：在 Linux 中安装开发软件包并发布进度/通知。属于可选执行环境的生命周期，不能替代 Agent 会话调度。

**规模**：222 行；10 个词法声明。源码：[DevPackageManager.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:1)。

**声明与运行入口**：

- [L19](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:19)：`data class DevPackageOperation(`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:25)：`data class DevPackagesState(`
- [L34](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:34)：`class DevPackageManager(`
- [L48](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:48)：`fun refresh() {`
- [L75](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:75)：`fun installBundle(bundle: DevBundle) {`
- [L79](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:79)：`fun installPackage(packageId: String) {`
- [L84](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:84)：`fun uninstallPackage(packageId: String) {`
- [L121](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:121)：`fun clearError() {`
- [L125](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:125)：`private fun install(packageIds: List<String>, label: String, postInstall: String? = null) {`
- [L202](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt:202)：`private suspend fun queryInstalled(): Set<String> {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`

### data/FontPreferences.kt

**职责**：保存/读取字体选择，供主题和 Activity 初始字体使用。

**规模**：19 行；3 个词法声明。源码：[FontPreferences.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt:6)：`class FontPreferences(context: Context) {`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt:9)：`fun read(): DdFontStyle = DdFontStyle.fromId(preferences.getString(KEY_FONT_STYLE, null))`
- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt:11)：`fun save(style: DdFontStyle) {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`

### data/LanguagePreferences.kt

**职责**：保存界面语言并包装 Android Context，让字符串资源随用户选择变化。

**规模**：45 行；6 个词法声明。源码：[LanguagePreferences.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt:7)：`enum class AppLanguage(val tag: String) {`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt:20)：`fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt:24)：`class LanguagePreferences(context: Context) {`
- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt:27)：`fun read(): AppLanguage = AppLanguage.fromTag(preferences.getString(KEY_LANGUAGE, null))`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt:29)：`fun save(language: AppLanguage) {`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt:37)：`fun wrap(context: Context): Context {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/VoiceLanguage.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`

### data/LegacyWorkspaceMigration.kt

**职责**：迁移旧 projects/工作区登记和相关会话路径，保证旧数据可以按当前 workspace 模型继续发现。

**规模**：44 行；2 个词法声明。源码：[LegacyWorkspaceMigration.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LegacyWorkspaceMigration.kt:1)。

**声明与运行入口**：

- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LegacyWorkspaceMigration.kt:15)：`object LegacyWorkspaceMigration {`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LegacyWorkspaceMigration.kt:16)：`suspend fun migrate(context: Context, workspaceStore: WorkspaceStore) {`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt`

### data/LinuxSettingsStore.kt

**职责**：保存 Linux 与共享存储开关，检查系统所有文件访问权限，决定后续会话是否注入 shell 及共享目录映射。

**规模**：46 行；7 个词法声明。源码：[LinuxSettingsStore.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:18)：`class LinuxSettingsStore(context: Context) {`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:26)：`suspend fun isEnabled(): Boolean = enabled.first()`
- [L28](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:28)：`suspend fun setEnabled(value: Boolean) {`
- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:32)：`suspend fun isSharedStorageEnabled(): Boolean = sharedStorageEnabled.first()`
- [L34](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:34)：`suspend fun setSharedStorageEnabled(value: Boolean) {`
- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:38)：`private fun flag(key: Preferences.Key<Boolean>): Flow<Boolean> = store.data`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt:45)：`fun isSharedStorageGranted(): Boolean =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`

### data/PersonalizationStore.kt

**职责**：保存称呼、交互风格、预设和长期指令，并转换成 runtime 的 Personalization 输入。

**规模**：75 行；6 个词法声明。源码：[PersonalizationStore.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt:1)。

**声明与运行入口**：

- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt:17)：`enum class TonePreset {`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt:24)：`data class PersonalizationUi(`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt:30)：`fun toRuntime(): Personalization = Personalization(`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt:37)：`class PersonalizationStore(context: Context) {`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt:61)：`suspend fun read(): PersonalizationUi = personalization.first()`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt:63)：`suspend fun save(value: PersonalizationUi) {`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`

### data/SharePayload.kt

**职责**：解析 SEND/SEND_MULTIPLE 的文本、标题、URI 列表与数量限制；是 Android Intent 接收层的纯数据规则。

**规模**：34 行；2 个词法声明。源码：[SharePayload.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SharePayload.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SharePayload.kt:7)：`data class SharePayload(`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SharePayload.kt:15)：`fun parseSharePayload(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### data/ShareStaging.kt

**职责**：把外部分享的 URI 暂存成应用可用文件，再交给附件导入；处理跨应用临时权限及失败原因。

**规模**：190 行；16 个词法声明。源码：[ShareStaging.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:1)。

**声明与运行入口**：

- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:15)：`sealed interface ShareStageFailure {`
- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:18)：`data class TooLarge(override val name: String) : ShareStageFailure`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:20)：`data class Unreadable(override val name: String) : ShareStageFailure`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:23)：`data class StagedShareFile(`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:29)：`data class ShareStagingResult(`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:35)：`class ShareStaging(context: Context) {`
- [L40](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:40)：`suspend fun stage(streams: List<String>): ShareStagingResult = withContext(Dispatchers.IO) {`
- [L85](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:85)：`fun shareUri(file: File): Uri? = runCatching {`
- [L89](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:89)：`fun cleanup(token: String) {`
- [L93](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:93)：`fun messageFor(failure: ShareStageFailure): String = when (failure) {`
- [L98](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:98)：`private data class Meta(val name: String, val mime: String, val size: Long)`
- [L100](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:100)：`private fun queryMeta(uri: Uri): Meta? {`
- [L128](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:128)：`private fun copy(uri: Uri, target: File): CopyOutcome {`
- [L157](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:157)：`private fun pruneStale() {`
- [L166](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:166)：`private fun uniqueName(base: String, used: MutableSet<String>): String {`
- [L180](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt:180)：`private enum class CopyOutcome {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`

### data/SkillArchive.kt

**职责**：解析技能 ZIP、验证元数据/路径/大小，构造 ParsedSkill；把外部文件内容变成可以安装的技能包。

**规模**：286 行；24 个词法声明。源码：[SkillArchive.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:1)。

**声明与运行入口**：

- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:21)：`data class ParsedSkillFile(`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:26)：`data class ParsedSkill(`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:35)：`internal data class SkillMetadata(`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:41)：`sealed interface SkillArchiveResult {`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:42)：`data class Success(val skill: ParsedSkill) : SkillArchiveResult`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:44)：`data class Failure(val error: SkillArchiveError) : SkillArchiveResult`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:47)：`enum class SkillArchiveError {`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:58)：`private data class ArchiveEntry(`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:63)：`object SkillArchive {`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:64)：`fun parse(bytes: ByteArray, fallbackName: String = ""): SkillArchiveResult {`
- [L119](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:119)：`internal fun parseSkillMetadata(`
- [L142](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:142)：`internal fun sanitizeSkillId(raw: String): String {`
- [L166](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:166)：`private sealed interface EntryRead {`
- [L167](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:167)：`data class Entries(val entries: List<ArchiveEntry>) : EntryRead`
- [L169](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:169)：`data class Failed(val error: SkillArchiveError) : EntryRead`
- [L172](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:172)：`private fun readEntries(bytes: ByteArray): EntryRead {`
- [L218](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:218)：`private fun isSafeEntryName(name: String): Boolean {`
- [L226](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:226)：`private fun isIgnoredEntry(name: String): Boolean = name.split('/').any { segment ->`
- [L230](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:230)：`private fun hasZipSignature(bytes: ByteArray): Boolean {`
- [L240](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:240)：`private fun hasEncryptedEntry(bytes: ByteArray): Boolean {`
- [L260](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:260)：`private fun findEndOfCentralDirectory(bytes: ByteArray): Int? {`
- [L271](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:271)：`private fun readUInt(bytes: ByteArray, offset: Int): Long {`
- [L279](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:279)：`private fun readUShort(bytes: ByteArray, offset: Int): Int {`
- [L284](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt:284)：`private fun failure(error: SkillArchiveError): SkillArchiveResult = SkillArchiveResult.Failure(error)`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/ShareStaging.kt`

### data/SkillRepository.kt

**职责**：管理技能仓库、全局启用副本和工作区副本；支持 ZIP 安装、删除、同步 Agent 自建技能与原子目录替换。

**规模**：359 行；30 个词法声明。源码：[SkillRepository.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:1)。

**声明与运行入口**：

- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:21)：`data class InstalledSkill(`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:30)：`class SkillRepository(`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:44)：`suspend fun list(): List<InstalledSkill> = locked {`
- [L54](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:54)：`suspend fun syncActiveSkills(): Int = locked {`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:64)：`suspend fun syncIfChanged(): Int = locked {`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:74)：`suspend fun install(bytes: ByteArray, fallbackName: String): SkillArchiveResult = locked {`
- [L90](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:90)：`suspend fun setGlobalEnabled(id: String, enabled: Boolean): Boolean = locked {`
- [L110](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:110)：`suspend fun delete(id: String): Boolean = locked {`
- [L125](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:125)：`suspend fun readContent(id: String): String? = locked {`
- [L137](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:137)：`suspend fun projectEnabledIds(cwd: String): Set<String> = locked {`
- [L151](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:151)：`suspend fun applyProjectSkills(cwd: String, enabledIds: Set<String>): Boolean = locked {`
- [L175](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:175)：`private fun syncLocked(force: Boolean): Int {`
- [L192](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:192)：`private fun importActiveSkill(directory: File): Boolean {`
- [L206](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:206)：`private fun activeSkillsFingerprint(): String = buildString {`
- [L218](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:218)：`private fun activeSkillDirectories(): List<File> = buildList {`
- [L226](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:226)：`private fun skillDirectoriesIn(root: File): List<File> = root.listFiles()`
- [L235](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:235)：`private fun installedSkills(): List<InstalledSkill> = repoSkillDirectories()`
- [L256](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:256)：`private fun repoSkillDirectories(): List<File> = repoDir.listFiles()`
- [L264](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:264)：`private fun projectSkillDirectories(cwd: String): List<File> = File(cwd, PROJECT_SKILLS_DIRECTORY)`
- [L271](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:271)：`private fun workspaceDirectories(): List<File> = workspacesDir.listFiles()`
- [L275](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:275)：`private fun projectSkillsRoot(cwd: String): File = File(cwd, PROJECT_SKILLS_DIRECTORY)`
- [L277](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:277)：`private fun projectCopyDirectories(id: String): List<File> = buildList {`
- [L284](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:284)：`private fun refreshActiveCopies(id: String) {`
- [L292](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:292)：`private fun removeProjectCopies(id: String) {`
- [L299](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:299)：`private fun writeSkillDirectory(skill: ParsedSkill, target: File) {`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:318)：`private fun copyDirectory(source: File, target: File) {`
- [L330](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:330)：`private fun replaceDirectory(source: File, target: File) {`
- [L344](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:344)：`private fun isSafeSkillId(id: String): Boolean {`
- [L351](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:351)：`private fun isSafeRelativePath(path: String): Boolean {`
- [L356](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt:356)：`private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt`

### data/StorageMigration.kt

**职责**：在依赖图初始化前迁移 workspace 到 home，并修复会话 JSONL 头和目录中的旧路径。

**规模**：87 行；6 个词法声明。源码：[StorageMigration.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt:6)：`object StorageMigration {`
- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt:12)：`fun migrate(filesDir: File) {`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt:30)：`private fun migrateSessions(sessionRoot: File) {`
- [L66](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt:66)：`private fun encodeCwdName(cwd: String): String {`
- [L78](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt:78)：`internal object StorageCwdMigration {`
- [L83](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/StorageMigration.kt:83)：`fun migrate(cwd: String): String = cwd`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LegacyWorkspaceMigration.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`

### data/ThemePreferences.kt

**职责**：保存浅色/深色/跟随系统选择，主题通过 Flow 更新。

**规模**：27 行；2 个词法声明。源码：[ThemePreferences.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ThemePreferences.kt:1)。

**声明与运行入口**：

- [L14](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ThemePreferences.kt:14)：`class ThemePreferences(context: Context) {`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/ThemePreferences.kt:24)：`suspend fun setTheme(mode: ThemeMode) {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`

### data/VoicePreferences.kt

**职责**：保存语音识别后是否发送、是否自动朗读等偏好，供语音 sheet 和主界面使用。

**规模**：34 行；3 个词法声明。源码：[VoicePreferences.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/VoicePreferences.kt:1)。

**声明与运行入口**：

- [L14](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/VoicePreferences.kt:14)：`class VoicePreferences(context: Context) {`
- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/VoicePreferences.kt:27)：`suspend fun setAutoSpeak(enabled: Boolean) {`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/data/VoicePreferences.kt:31)：`suspend fun setSendAfterRecognition(enabled: Boolean) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`

### plugin/AndroidPluginStrings.kt

**职责**：把插件框架依赖的字符串接口映射到 Android 资源，避免 PluginManager 拼接具体系统字符串。

**规模**：12 行；3 个词法声明。源码：[AndroidPluginStrings.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt:6)：`class AndroidPluginStrings(`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt:9)：`override fun get(@StringRes id: Int): String = context.getString(id)`
- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt:11)：`override fun format(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`

### plugin/PluginApi.kt

**职责**：定义官方插件生命周期、状态、能力贡献、交互界面和进度接口。插件是编译进 App 的实现，下载的是各自需要的工具/资源。

**规模**：112 行；33 个词法声明。源码：[PluginApi.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:7)：`interface OfficialPlugin {`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:25)：`suspend fun availability(): PluginAvailability`
- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:27)：`suspend fun install(progress: PluginProgress)`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:29)：`suspend fun uninstall()`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:31)：`suspend fun recover() {}`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:33)：`suspend fun status(): PluginStatus`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:35)：`suspend fun setup(ui: PluginUi) {}`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:37)：`fun actions(status: PluginStatus): List<PluginAction> = emptyList()`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:39)：`suspend fun runAction(actionId: String, ui: PluginUi) {}`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:41)：`suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = emptyList()`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:43)：`fun skills(): List<PluginSkill> = emptyList()`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:45)：`fun tools(): List<CodingTool> = emptyList()`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:47)：`fun promptBody(status: PluginStatus): String? = null`
- [L50](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:50)：`sealed interface PluginAvailability {`
- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:51)：`data object Available : PluginAvailability`
- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:53)：`data class Unavailable(val reason: String) : PluginAvailability`
- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:56)：`sealed interface PluginStatus {`
- [L59](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:59)：`data class Ready(`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:64)：`data class NotReady(override val label: String) : PluginStatus`
- [L66](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:66)：`data class Failed(override val label: String) : PluginStatus`
- [L69](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:69)：`interface PluginProgress {`
- [L70](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:70)：`fun report(percent: Int?, @StringRes labelRes: Int)`
- [L73](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:73)：`interface PluginUi {`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:74)：`fun stage(`
- [L82](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:82)：`fun progress(percent: Int?, @StringRes labelRes: Int)`
- [L84](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:84)：`fun cancelled(): Boolean`
- [L87](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:87)：`enum class PluginActionStyle {`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:92)：`data class PluginAction(`
- [L98](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:98)：`data class PluginDetailRow(`
- [L103](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:103)：`data class PluginSkill(`
- [L108](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:108)：`interface PluginStrings {`
- [L109](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:109)：`fun get(@StringRes id: Int): String`
- [L111](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt:111)：`fun format(@StringRes id: Int, vararg args: Any): String`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`

### plugin/PluginDownloader.kt

**职责**：HTTP 下载插件资源，设连接/读取超时及最大字节数，支持多个地址、SHA256 校验和临时文件发布。

**规模**：106 行；4 个词法声明。源码：[PluginDownloader.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt:12)：`class PluginDownloader(`
- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt:17)：`suspend fun download(`
- [L50](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt:50)：`private fun downloadFrom(url: String, target: File, onProgress: (Int?) -> Unit) {`
- [L94](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt:94)：`private fun sha256(file: File): String {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`

### plugin/PluginManager.kt

**职责**：统一安装/升级/卸载/启停/连接/状态刷新/技能贡献和 Agent setup 入口；全局 busy 只串行插件操作，不能保证会话运行同步。

**规模**：548 行；32 个词法声明。源码：[PluginManager.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:1)。

**声明与运行入口**：

- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:21)：`class PluginManager(`
- [L46](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:46)：`suspend fun start() {`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:61)：`fun refresh() {`
- [L71](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:71)：`fun refreshIfStale(minIntervalMillis: Long = REFRESH_STALE_INTERVAL_MS) {`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:76)：`fun tools(): List<CodingTool> = _state.value.plugins`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:81)：`fun setEnabled(pluginId: String, enabled: Boolean) {`
- [L107](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:107)：`fun install(pluginId: String) {`
- [L112](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:112)：`fun update(pluginId: String) {`
- [L117](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:117)：`fun uninstall(pluginId: String) {`
- [L130](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:130)：`fun connect(pluginId: String) {`
- [L136](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:136)：`fun runAction(pluginId: String, actionId: String) {`
- [L151](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:151)：`fun onAppForeground() {`
- [L165](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:165)：`override suspend fun setup(pluginId: String): PluginSetupResult {`
- [L259](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:259)：`fun confirmInstall() {`
- [L263](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:263)：`fun cancelInteraction() {`
- [L271](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:271)：`fun promptBody(): String? {`
- [L281](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:281)：`private fun launchBusy(block: suspend () -> Unit) {`
- [L308](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:308)：`private suspend fun installInternal(plugin: OfficialPlugin) {`
- [L354](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:354)：`private suspend fun awaitInstallConfirm(plugin: OfficialPlugin): Boolean {`
- [L394](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:394)：`private suspend fun runSetupBody(plugin: OfficialPlugin) {`
- [L417](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:417)：`private fun setupResult(plugin: OfficialPlugin, status: PluginStatus, installed: Boolean): PluginSetupResult {`
- [L430](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:430)：`private fun cancelledResult(pluginId: String, installed: Boolean): PluginSetupResult = PluginSetupResult(`
- [L438](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:438)：`private suspend fun refreshStatuses() {`
- [L466](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:466)：`private fun progressAdapter(plugin: OfficialPlugin, title: String): PluginProgress = object : PluginProgress {`
- [L467](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:467)：`override fun report(percent: Int?, labelRes: Int) {`
- [L481](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:481)：`private fun setOperation(pluginId: String, title: String, stage: String?, progress: Int?) {`
- [L485](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:485)：`private fun enabled(pluginId: String): Boolean =`
- [L491](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:491)：`override fun stage(`
- [L519](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:519)：`override fun progress(percent: Int?, labelRes: Int) {`
- [L533](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:533)：`override fun cancelled(): Boolean = cancelRequested`
- [L536](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:536)：`private fun errorMessage(error: Throwable): String =`
- [L539](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt:539)：`private fun flushContributions() {`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginContracts.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt`

### plugin/PluginRecordStore.kt

**职责**：以 schemaVersion 2 JSON 持久化插件安装版本、启用状态和技能归属，使用 Mutex 与临时文件替换。

**规模**：136 行；10 个词法声明。源码：[PluginRecordStore.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:1)。

**声明与运行入口**：

- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:24)：`data class PluginRecord(`
- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:32)：`data class PluginRecords(`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:37)：`class PluginRecordStore(`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:43)：`suspend fun load(): PluginRecords = mutex.withLock { withContext(ioDispatcher) { loadLocked() } }`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:45)：`suspend fun upsert(record: PluginRecord) = mutex.withLock {`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:52)：`suspend fun remove(id: String) = mutex.withLock {`
- [L60](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:60)：`suspend fun setEnabled(id: String, enabled: Boolean) = mutex.withLock {`
- [L69](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:69)：`private fun loadLocked(): PluginRecords {`
- [L94](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:94)：`private fun writeLocked(records: Map<String, PluginRecord>) {`
- [L134](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt:134)：`private fun JsonObject.string(name: String): String? =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt`

### plugin/PluginSkillStore.kt

**职责**：同步插件附带的技能到仓库/激活目录，以 .deepdeep-plugin 标记所有者，防止覆盖其他技能。

**规模**：167 行；13 个词法声明。源码：[PluginSkillStore.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:12)：`class PluginSkillStore(`
- [L19](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:19)：`suspend fun sync(`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:39)：`suspend fun setActive(`
- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:56)：`suspend fun ensureRepo(pluginId: String, skills: List<PluginSkill>) = mutex.withLock {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:62)：`suspend fun remove(pluginId: String, skillIds: List<String>) = mutex.withLock {`
- [L68](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:68)：`private fun ensureRepoSkill(pluginId: String, skill: PluginSkill) {`
- [L83](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:83)：`private fun writeSkill(parent: File, pluginId: String, skill: PluginSkill) {`
- [L106](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:106)：`private fun copyOwned(sourceParent: File, targetParent: File, pluginId: String, id: String) {`
- [L127](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:127)：`private fun removeSkill(pluginId: String, id: String) {`
- [L132](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:132)：`private fun removeOwned(parent: File, pluginId: String, id: String) {`
- [L139](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:139)：`private fun marker(directory: File): String? {`
- [L144](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:144)：`private fun replaceDirectory(source: File, target: File) {`
- [L158](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginSkillStore.kt:158)：`private fun isSafeId(id: String): Boolean {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginRecordStore.kt`

### plugin/PluginState.kt

**职责**：插件列表、详情、进度、交互 sheet 的状态模型，以及简化的数字版本比较规则。

**规模**：62 行；8 个词法声明。源码：[PluginState.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:3)：`data class PluginSnapshot(`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:16)：`data class PluginOperation(`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:23)：`data class PluginInteraction(`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:35)：`data class PluginsUiState(`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:43)：`fun snapshot(pluginId: String): PluginSnapshot? = plugins.firstOrNull { it.plugin.id == pluginId }`
- [L46](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:46)：`internal object PluginVersions {`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:47)：`fun isOlder(installed: String, candidate: String): Boolean {`
- [L59](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt:59)：`private fun numericParts(version: String): List<Long> = version`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/AndroidPluginStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`

### plugin/arrange/ArrangeCalendar.kt

**职责**：系统 CalendarContract 的网关、日历/事件数据类型和时间范围合并规则。执行真实系统日历 CRUD 和提醒写入。

**规模**：481 行；46 个词法声明。源码：[ArrangeCalendar.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:1)。

**声明与运行入口**：

- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:21)：`data class ArrangeCalendarInfo(`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:30)：`data class ArrangeCalendarEvent(`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:44)：`data class ArrangeEventDraft(`
- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:55)：`data class ArrangeEventPatch(`
- [L66](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:66)：`class ArrangeCalendarPermissionException(`
- [L70](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:70)：`class ArrangeNoWritableCalendarException(`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:74)：`interface ArrangeCalendarGateway {`
- [L75](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:75)：`fun hasReadPermission(): Boolean`
- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:77)：`fun hasWritePermission(): Boolean`
- [L79](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:79)：`fun calendars(): List<ArrangeCalendarInfo>`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:81)：`fun list(startMillis: Long, endMillis: Long, query: String?, limit: Int): List<ArrangeCalendarEvent>`
- [L83](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:83)：`fun get(id: Long): ArrangeCalendarEvent?`
- [L85](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:85)：`fun create(draft: ArrangeEventDraft): ArrangeCalendarEvent`
- [L87](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:87)：`fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent?`
- [L89](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:89)：`fun delete(id: Long): Boolean`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:92)：`internal fun accessLevelIsWritable(accessLevel: Int): Boolean = accessLevel >= ARRANGE_WRITABLE_ACCESS_LEVEL`
- [L94](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:94)：`internal fun limitedSortOrder(sortOrder: String, limit: Int): String {`
- [L99](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:99)：`internal fun resolveEventRange(startMillis: Long, endMillis: Long?, allDay: Boolean): Pair<Long, Long> {`
- [L111](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:111)：`internal fun validatedReminderMinutes(minutes: List<Int>): List<Int> {`
- [L117](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:117)：`internal data class ArrangeEventUpdate(`
- [L129](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:129)：`internal fun mergeEventPatch(current: ArrangeCalendarEvent, patch: ArrangeEventPatch): ArrangeEventUpdate {`
- [L150](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:150)：`private fun floorToUtcDay(millis: Long): Long = Math.floorDiv(millis, ARRANGE_DAY_MILLIS) * ARRANGE_DAY_MILLIS`
- [L152](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:152)：`internal fun arrangeEventForInstance(`
- [L176](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:176)：`class AndroidArrangeCalendarGateway(context: Context) : ArrangeCalendarGateway {`
- [L180](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:180)：`override fun hasReadPermission(): Boolean = hasPermission(Manifest.permission.READ_CALENDAR)`
- [L182](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:182)：`override fun hasWritePermission(): Boolean = hasPermission(Manifest.permission.WRITE_CALENDAR)`
- [L184](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:184)：`override fun calendars(): List<ArrangeCalendarInfo> {`
- [L206](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:206)：`override fun list(`
- [L246](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:246)：`override fun get(id: Long): ArrangeCalendarEvent? {`
- [L256](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:256)：`override fun create(draft: ArrangeEventDraft): ArrangeCalendarEvent {`
- [L287](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:287)：`override fun update(id: Long, patch: ArrangeEventPatch): ArrangeCalendarEvent? {`
- [L315](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:315)：`override fun delete(id: Long): Boolean {`
- [L321](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:321)：`fun defaultWritableCalendarId(): Long {`
- [L327](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:327)：`private fun hasPermission(permission: String): Boolean =`
- [L330](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:330)：`private fun requireReadPermission() {`
- [L336](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:336)：`private fun requireWritePermission() {`
- [L342](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:342)：`private fun requireWritableCalendar(calendarId: Long) {`
- [L348](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:348)：`private fun eventTimezone(allDay: Boolean): String =`
- [L351](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:351)：`private fun instanceFromCursor(cursor: Cursor): ArrangeCalendarEvent {`
- [L369](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:369)：`private fun eventFromCursor(cursor: Cursor): ArrangeCalendarEvent {`
- [L391](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:391)：`private fun recurringEventIds(eventIds: List<Long>): Set<Long> {`
- [L412](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:412)：`private fun replaceReminders(eventId: Long, minutes: List<Int>) {`
- [L467](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:467)：`private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))`
- [L469](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:469)：`private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))`
- [L471](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:471)：`private fun Cursor.longOrNull(column: String): Long? {`
- [L477](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt:477)：`private fun Cursor.text(column: String): String? {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### plugin/arrange/ArrangePermissionActivity.kt

**职责**：透明 Activity 接收日历权限申请，完成后返回插件设置流程。

**规模**：38 行；3 个词法声明。源码：[ArrangePermissionActivity.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePermissionActivity.kt:1)。

**声明与运行入口**：

- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePermissionActivity.kt:9)：`class ArrangePermissionActivity : Activity() {`
- [L10](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePermissionActivity.kt:10)：`override fun onCreate(savedInstanceState: Bundle?) {`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePermissionActivity.kt:22)：`override fun onRequestPermissionsResult(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### plugin/arrange/ArrangePlugin.kt

**职责**：把日历网关接入官方插件框架，显示权限/可用状态并贡献 arrange_calendar 工具。

**规模**：116 行；14 个词法声明。源码：[ArrangePlugin.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:1)。

**声明与运行入口**：

- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:20)：`class ArrangePlugin(`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:31)：`override suspend fun availability(): PluginAvailability = PluginAvailability.Available`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:33)：`override suspend fun install(progress: PluginProgress) = Unit`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:35)：`override suspend fun uninstall() = Unit`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:37)：`override suspend fun status(): PluginStatus = if (hasCalendarPermission()) {`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:43)：`override suspend fun setup(ui: PluginUi) {`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:58)：`override fun actions(status: PluginStatus): List<PluginAction> = buildList {`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:64)：`override suspend fun runAction(actionId: String, ui: PluginUi) {`
- [L71](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:71)：`override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = emptyList()`
- [L73](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:73)：`override fun tools(): List<CodingTool> = arrangeTools(calendar)`
- [L75](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:75)：`override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))`
- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:77)：`override fun promptBody(status: PluginStatus): String = PROMPT_BODY`
- [L79](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:79)：`private fun hasCalendarPermission(): Boolean = calendar.hasReadPermission() && calendar.hasWritePermission()`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt:81)：`private fun string(id: Int): String = context.resources.getString(id)`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePermissionActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt`

### plugin/arrange/ArrangeRequests.kt

**职责**：日历输入字符串与日期时间之间的解析、格式化和文本长度规则；当前全天日期与 UTC 转换链存在偏移问题。

**规模**：42 行；3 个词法声明。源码：[ArrangeRequests.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeRequests.kt:1)。

**声明与运行入口**：

- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeRequests.kt:17)：`internal fun boundedOptionalText(raw: String?, maxLength: Int, field: String): String? {`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeRequests.kt:24)：`internal fun parseCalendarMillis(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long {`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeRequests.kt:39)：`internal fun formatCalendarTime(millis: Long, allDay: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### plugin/arrange/ArrangeTools.kt

**职责**：arrange_calendar 的模型参数契约与 action 分发，把 calendars/list/get/create/update/delete 转成网关调用并返回 JSON。

**规模**：330 行；12 个词法声明。源码：[ArrangeTools.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:1)。

**声明与运行入口**：

- [L130](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:130)：`fun arrangeTools(`
- [L135](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:135)：`private fun arrangeCalendarTool(`
- [L270](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:270)：`private fun eventJson(event: ArrangeCalendarEvent, zone: ZoneId): JsonObject = buildJsonObject {`
- [L286](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:286)：`private suspend fun arrangeToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {`
- [L298](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:298)：`private fun JsonObject.reminderMinutes(): List<Int>? {`
- [L311](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:311)：`private fun JsonObject.requiredLong(name: String): Long =`
- [L314](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:314)：`private fun JsonObject.requiredString(name: String): String =`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:318)：`private fun JsonObject.optionalText(name: String, maxLength: Int): String? =`
- [L321](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:321)：`private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull`
- [L323](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:323)：`private fun JsonObject.intOrNull(name: String): Int? =`
- [L326](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:326)：`private fun JsonObject.longOrNull(name: String): Long? =`
- [L329](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeTools.kt:329)：`private fun JsonObject.booleanOrNull(name: String): Boolean? =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeCalendar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangeRequests.kt`

### plugin/feishu/FeishuCli.kt

**职责**：构造 lark-cli 的版本/配置/授权/退出命令，解析 CLI 混合文本中的 JSON、账号和授权结果。

**规模**：157 行；22 个词法声明。源码：[FeishuCli.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:12)：`object FeishuCli {`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:22)：`fun versionCommand(): String = "$ENV $BINARY --version 2>&1"`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:24)：`fun statusCommand(verify: Boolean = false): String {`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:29)：`fun configInitCommand(): String = "$ENV $BINARY config init --new --brand feishu 2>&1"`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:31)：`fun loginStartCommand(): String =`
- [L34](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:34)：`fun loginPollCommand(deviceCode: String): String =`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:37)：`fun logoutCommand(): String = "$ENV $BINARY auth logout --json 2>&1"`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:39)：`fun uninstallCommand(): String = buildString {`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:44)：`fun parseVersion(output: String): String? =`
- [L48](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:48)：`fun parseConfigInitUrl(output: String): String? {`
- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:53)：`data class LoginChallenge(`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:58)：`fun parseLoginChallenge(output: String): LoginChallenge? {`
- [L65](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:65)：`data class AuthStatus(`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:74)：`fun parseAuthStatus(output: String): AuthStatus? {`
- [L93](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:93)：`data class LoginResult(`
- [L100](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:100)：`fun parseLoginResult(output: String): LoginResult? {`
- [L120](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:120)：`private fun findLastJsonObject(output: String): JsonObject? {`
- [L134](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:134)：`private fun JsonObject.string(name: String): String? =`
- [L137](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:137)：`private fun JsonObject.stringList(name: String): List<String> {`
- [L144](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:144)：`private fun JsonObject.warningText(): String? {`
- [L151](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:151)：`private fun JsonObject.objectOrNull(name: String): JsonObject? {`
- [L156](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt:156)：`private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### plugin/feishu/FeishuPlugin.kt

**职责**：下载并安装飞书 CLI，执行环境检查、授权轮询、状态探测、卸载和技能/提示词贡献。

**规模**：441 行；19 个词法声明。源码：[FeishuPlugin.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:1)。

**声明与运行入口**：

- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:24)：`class FeishuPlugin(`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:45)：`override suspend fun availability(): PluginAvailability {`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:58)：`override suspend fun install(progress: PluginProgress) {`
- [L116](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:116)：`override suspend fun uninstall() {`
- [L125](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:125)：`override suspend fun recover() {`
- [L129](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:129)：`override suspend fun status(): PluginStatus {`
- [L167](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:167)：`override suspend fun setup(ui: PluginUi) {`
- [L174](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:174)：`private suspend fun login(ui: PluginUi) {`
- [L214](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:214)：`override fun actions(status: PluginStatus): List<PluginAction> = buildList {`
- [L232](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:232)：`override suspend fun runAction(actionId: String, ui: PluginUi) {`
- [L257](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:257)：`override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = buildList {`
- [L291](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:291)：`override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))`
- [L293](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:293)：`override fun promptBody(status: PluginStatus): String = when (status) {`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:318)：`private fun openAppScopes() {`
- [L325](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:325)：`private fun missingScopesLabel(scopes: List<String>): String {`
- [L334](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:334)：`private suspend fun readAuthStatus(): FeishuCli.AuthStatus? {`
- [L339](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:339)：`private suspend fun runConfigInit(ui: PluginUi): String? {`
- [L362](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:362)：`private suspend fun requireAvailable() {`
- [L369](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt:369)：`private suspend fun restoreOrRemoveBinary() {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuCli.kt`

### plugin/gui/GuiAccessibilityActions.kt

**职责**：把 GuiActions 抽象转换成 Android 无障碍节点操作、手势、窗口观察与截图，处理主线程和系统回调。

**规模**：320 行；22 个词法声明。源码：[GuiAccessibilityActions.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:1)。

**声明与运行入口**：

- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:23)：`class GuiAccessibilityActions(`
- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:32)：`override suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot {`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:47)：`override suspend fun tap(selector: GuiSelector, durationMs: Int) {`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:67)：`override suspend fun setText(selector: GuiSelector, text: String): GuiTextResult {`
- [L88](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:88)：`override suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?) {`
- [L107](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:107)：`override suspend fun global(action: GuiGlobalAction) {`
- [L120](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:120)：`override suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): Boolean {`
- [L136](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:136)：`override suspend fun screenshot(): GuiScreenshot {`
- [L147](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:147)：`private fun isKeyguardLocked(service: AccessibilityService): Boolean =`
- [L151](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:151)：`private suspend fun awaitScreenshot(service: GuiAccessibilityService): AccessibilityService.ScreenshotResult =`
- [L154](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:154)：`override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {`
- [L158](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:158)：`override fun onFailure(errorCode: Int) {`
- [L175](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:175)：`private fun encodeScreenshot(result: AccessibilityService.ScreenshotResult): GuiScreenshot {`
- [L190](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:190)：`private fun screenshotErrorMessage(errorCode: Int): String = when (errorCode) {`
- [L206](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:206)：`private fun requireService(): GuiAccessibilityService =`
- [L211](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:211)：`private fun requireNode(selector: GuiSelector): GuiSnapshotNode {`
- [L217](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:217)：`private fun clickNode(node: AccessibilityNodeInfo): Boolean {`
- [L233](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:233)：`private fun containsText(node: AccessibilityNodeInfo, text: String, depth: Int): Boolean {`
- [L244](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:244)：`private fun convert(node: AccessibilityNodeInfo, depth: Int = 0): GuiNode {`
- [L269](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:269)：`private suspend fun tapPoint(service: AccessibilityService, x: Int, y: Int, durationMs: Int) {`
- [L278](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:278)：`private fun swipePath(direction: GuiScrollDirection, bounds: GuiBounds): Path {`
- [L297](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt:297)：`private suspend fun dispatch(service: AccessibilityService, path: Path, durationMillis: Long) {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt`

### plugin/gui/GuiActions.kt

**职责**：GUI 操作的抽象接口与请求/结果类型，让工具定义不必直接依赖 AccessibilityService。

**规模**：48 行；13 个词法声明。源码：[GuiActions.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:3)：`class GuiUnavailableException(`
- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:7)：`data class GuiScreenshot(`
- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:13)：`data class GuiTextResult(`
- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:18)：`interface GuiActions {`
- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:21)：`suspend fun observe(maxDepth: Int, maxNodes: Int): GuiSnapshot`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:23)：`suspend fun tap(selector: GuiSelector, durationMs: Int)`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:25)：`suspend fun setText(selector: GuiSelector, text: String): GuiTextResult`
- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:27)：`suspend fun scroll(direction: GuiScrollDirection, amount: Int, target: GuiSelector?)`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:29)：`suspend fun global(action: GuiGlobalAction)`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:31)：`suspend fun waitFor(packageName: String?, text: String?, textGone: String?, timeoutMs: Long): Boolean`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:33)：`suspend fun screenshot(): GuiScreenshot`
- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:36)：`enum class GuiScrollDirection {`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt:43)：`enum class GuiGlobalAction {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt`

### plugin/gui/GuiNodeTree.kt

**职责**：构建有深度/节点数量上限的 GUI 快照、节点引用、选择器匹配与 JSON 渲染，供观察和定位工具使用。

**规模**：185 行；26 个词法声明。源码：[GuiNodeTree.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:1)。

**声明与运行入口**：

- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:13)：`data class GuiBounds(`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:24)：`fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:26)：`fun format(): String = "[$left,$top][$right,$bottom]"`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:29)：`data class GuiNode(`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:43)：`data class GuiSnapshotNode(`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:58)：`data class GuiSnapshot(`
- [L65](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:65)：`sealed interface GuiSelector {`
- [L66](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:66)：`data class Ref(val ref: String) : GuiSelector`
- [L68](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:68)：`data class Text(val text: String) : GuiSelector`
- [L70](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:70)：`data class Desc(val desc: String) : GuiSelector`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:72)：`data class ResourceId(val id: String) : GuiSelector`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:74)：`data class ClassName(val name: String) : GuiSelector`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:76)：`data class Point(val x: Int, val y: Int) : GuiSelector`
- [L79](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:79)：`object GuiNodeTree {`
- [L80](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:80)：`fun buildSnapshot(`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:92)：`fun visit(node: GuiNode, depth: Int) {`
- [L123](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:123)：`fun render(snapshot: GuiSnapshot): String = buildJsonObject {`
- [L131](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:131)：`fun nodeJson(node: GuiSnapshotNode): JsonObject = buildJsonObject {`
- [L145](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:145)：`fun parseRef(ref: String): Int? {`
- [L150](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:150)：`fun findRef(snapshot: GuiSnapshot, ref: String): GuiSnapshotNode? {`
- [L156](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:156)：`fun find(snapshot: GuiSnapshot, selector: GuiSelector): GuiSnapshotNode? = when (selector) {`
- [L161](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:161)：`fun matches(node: GuiSnapshotNode, selector: GuiSelector): Boolean = when (selector) {`
- [L170](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:170)：`fun matchesAll(node: GuiSnapshotNode, selectors: List<GuiSelector>): Boolean =`
- [L173](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:173)：`fun findAll(snapshot: GuiSnapshot, selectors: List<GuiSelector>, limit: Int): List<GuiSnapshotNode> =`
- [L176](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:176)：`private fun matchesResourceId(resourceId: String?, id: String): Boolean {`
- [L181](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt:181)：`private fun matchesClassName(className: String, name: String): Boolean {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt`

### plugin/gui/GuiPlugin.kt

**职责**：GUI 插件状态与无障碍/后台保活设置入口；贡献 GUI 工具和使用技能。

**规模**：209 行；12 个词法声明。源码：[GuiPlugin.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:1)。

**声明与运行入口**：

- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:22)：`class GuiPlugin(`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:39)：`override suspend fun availability(): PluginAvailability = PluginAvailability.Available`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:41)：`override suspend fun install(progress: PluginProgress) = Unit`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:43)：`override suspend fun uninstall() = Unit`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:45)：`override suspend fun recover() = Unit`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:47)：`override suspend fun status(): PluginStatus = when {`
- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:53)：`override fun actions(status: PluginStatus): List<PluginAction> = buildList {`
- [L66](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:66)：`override suspend fun runAction(actionId: String, ui: PluginUi) {`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:81)：`override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = listOf(`
- [L118](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:118)：`override fun tools(): List<CodingTool> = guiTools(actions, resolvedScreenshotDir, linuxScreenshotDir)`
- [L120](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:120)：`override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))`
- [L122](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt:122)：`private fun string(id: Int): String = context.resources.getString(id)`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt`

### plugin/gui/GuiTools.kt

**职责**：gui_observe/find/tap/set_text/scroll/global/wait/screenshot 的参数验证和分发，把模型操作转换成 GuiActions。

**规模**：592 行；17 个词法声明。源码：[GuiTools.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:1)。

**声明与运行入口**：

- [L231](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:231)：`fun guiTools(`
- [L246](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:246)：`private fun guiObserveTool(actions: GuiActions): CodingTool = CodingTool(`
- [L277](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:277)：`private fun guiFindTool(actions: GuiActions): CodingTool = CodingTool(`
- [L314](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:314)：`private fun guiTapTool(actions: GuiActions): CodingTool = CodingTool(`
- [L338](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:338)：`private fun guiSetTextTool(actions: GuiActions): CodingTool = CodingTool(`
- [L373](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:373)：`private fun guiScrollTool(actions: GuiActions): CodingTool = CodingTool(`
- [L404](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:404)：`private fun guiGlobalTool(actions: GuiActions): CodingTool = CodingTool(`
- [L430](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:430)：`private fun guiWaitTool(actions: GuiActions): CodingTool = CodingTool(`
- [L479](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:479)：`private fun guiScreenshotTool(`
- [L519](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:519)：`private suspend fun guiToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {`
- [L529](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:529)：`private fun requireConnected(actions: GuiActions) {`
- [L533](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:533)：`private fun JsonObject.findSelectors(): List<GuiSelector> {`
- [L546](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:546)：`private fun JsonObject.parseSelector(): GuiSelector {`
- [L576](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:576)：`private fun GuiSelector.describe(): String = when (this) {`
- [L585](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:585)：`private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull`
- [L587](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:587)：`private fun JsonObject.intOrNull(name: String): Int? =`
- [L590](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiTools.kt:590)：`private fun JsonObject.requiredString(name: String): String =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiNodeTree.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt`

### plugin/micloud/MiCloudClient.kt

**职责**：携带已登录 Cookie 的小米云 JSON GET 与资源下载；负责鉴权失效识别、网络超时及响应解析。

**规模**：108 行；10 个词法声明。源码：[MiCloudClient.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:12)：`class MiCloudAuthException(message: String) : Exception(message)`
- [L14](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:14)：`class MiCloudClient(`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:20)：`suspend fun hasSession(): Boolean = !cookieProvider().isNullOrBlank()`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:22)：`suspend fun userId(): String? = cookieProvider()?.let { parseCookie(it)[USER_ID] }`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:24)：`suspend fun getJson(pathWithQuery: String): JsonElement = withContext(Dispatchers.IO) {`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:45)：`suspend fun download(pathWithQuery: String, dest: File): File = withContext(Dispatchers.IO) {`
- [L73](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:73)：`suspend fun profile(): MiCloudProfile {`
- [L78](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:78)：`private suspend fun requireCookie(): String = cookieProvider()?.takeIf { it.isNotBlank() }`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:81)：`private fun baseHost(): String = runCatching { URL(baseUrl).host }.getOrDefault("i.mi.com")`
- [L83](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt:83)：`private fun open(pathWithQuery: String, accept: String = "application/json, text/plain, */*"): HttpURLConnection {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt`

### plugin/micloud/MiCloudCookieStore.kt

**职责**：通过统一加密秘密存储保存小米云 Cookie，并提取 userId/serviceToken，避免把登录态放进普通配置文件。

**规模**：29 行；6 个词法声明。源码：[MiCloudCookieStore.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt:6)：`class MiCloudCookieStore(context: Context) {`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt:9)：`suspend fun read(): String? = secrets.read(PREFIX, COOKIE_KEY)?.takeIf { it.isNotBlank() }`
- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt:11)：`suspend fun save(cookie: String) {`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt:15)：`suspend fun clear() {`
- [L19](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt:19)：`suspend fun userId(): String? = read()?.let { parseCookie(it)[USER_ID] }`
- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt:21)：`suspend fun serviceToken(): String? = read()?.let { parseCookie(it)[SERVICE_TOKEN] }`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt`

### plugin/micloud/MiCloudFeatureSection.kt

**职责**：小米云插件详情中的功能开关界面，向用户暴露按数据领域启停的能力。

**规模**：151 行；2 个词法声明。源码：[MiCloudFeatureSection.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt:1)。

**声明与运行入口**：

- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt:29)：`fun MiCloudFeatureSection(`
- [L128](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt:128)：`private fun FeatureRow(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt`

### plugin/micloud/MiCloudLoginActivity.kt

**职责**：WebView 登录 i.mi.com、轮询并验证 Cookie、处理登录状态和界面适配。

**规模**：362 行；23 个词法声明。源码：[MiCloudLoginActivity.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:1)。

**声明与运行入口**：

- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:64)：`class MiCloudLoginActivity : ComponentActivity() {`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:72)：`override fun attachBaseContext(newBase: Context) {`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:76)：`override fun onCreate(savedInstanceState: Bundle?) {`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:92)：`object : OnBackPressedCallback(true) {`
- [L93](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:93)：`override fun handleOnBackPressed() {`
- [L101](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:101)：`override fun onStart() {`
- [L106](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:106)：`override fun onStop() {`
- [L112](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:112)：`override fun onDestroy() {`
- [L117](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:117)：`private fun startWatching() {`
- [L131](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:131)：`private suspend fun verify(cookie: String) {`
- [L154](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:154)：`private fun retry() {`
- [L162](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:162)：`private fun currentCookie(): String? =`
- [L165](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:165)：`private fun clearOriginCookies() {`
- [L170](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:170)：`private fun LoginContent(uiState: LoginUiState) {`
- [L264](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:264)：`private fun createWebView(context: Context): WebView {`
- [L283](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:283)：`override fun onPageFinished(view: WebView, url: String) {`
- [L289](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:289)：`override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {`
- [L297](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:297)：`override fun onLayoutChange(`
- [L317](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:317)：`private sealed interface LoginUiState {`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:318)：`data object Loading : LoginUiState`
- [L320](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:320)：`data object Verifying : LoginUiState`
- [L322](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:322)：`data class Error(val message: String) : LoginUiState`
- [L351](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt:351)：`internal fun clearMiCloudWebCookies() {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/ThemePreferences.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt`

### plugin/micloud/MiCloudParsers.kt

**职责**：解析 Cookie、云服务响应和功能相关结构，给客户端/工具提供纯数据转换。

**规模**：135 行；15 个词法声明。源码：[MiCloudParsers.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:1)。

**声明与运行入口**：

- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:9)：`data class MiCloudProfile(`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:15)：`internal data class MiCloudNote(`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:23)：`internal data class MiCloudNotesPage(`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:29)：`internal data class MiCloudNoteContent(`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:35)：`internal data class MiCloudTodo(`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:44)：`internal fun parseCookie(cookie: String): Map<String, String> = buildMap {`
- [L54](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:54)：`internal fun miCloudResponseData(raw: JsonElement): JsonObject? {`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:61)：`internal fun isMiCloudAuthFailure(raw: JsonElement): Boolean {`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:67)：`internal fun parseProfile(raw: JsonElement): MiCloudProfile? {`
- [L75](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:75)：`internal fun parseNotesPage(raw: JsonElement): MiCloudNotesPage? {`
- [L96](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:96)：`internal fun parseNote(raw: JsonElement): MiCloudNoteContent? {`
- [L107](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:107)：`internal fun parseTodos(raw: JsonElement): List<MiCloudTodo> {`
- [L126](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:126)：`private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull`
- [L128](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:128)：`private fun JsonObject.longOrNull(name: String): Long? =`
- [L131](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt:131)：`private fun JsonObject.boolOrNull(name: String): Boolean? = when (val value = (this[name] as? JsonPrimitive)?.contentOrNull) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt`

### plugin/micloud/MiCloudPlugin.kt

**职责**：将小米云登录、按功能授权开关、工具生成与能力变更通知连接到官方插件框架。

**规模**：267 行；23 个词法声明。源码：[MiCloudPlugin.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:1)。

**声明与运行入口**：

- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:24)：`class MiCloudPlugin(`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:41)：`override suspend fun availability(): PluginAvailability = PluginAvailability.Available`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:43)：`override suspend fun install(progress: PluginProgress) = Unit`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:45)：`override suspend fun uninstall() {`
- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:53)：`override suspend fun recover() = Unit`
- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:55)：`override suspend fun status(): PluginStatus = when (`
- [L79](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:79)：`override suspend fun setup(ui: PluginUi) {`
- [L100](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:100)：`override fun actions(status: PluginStatus): List<PluginAction> = buildList {`
- [L115](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:115)：`override suspend fun runAction(actionId: String, ui: PluginUi) {`
- [L130](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:130)：`override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = listOf(`
- [L142](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:142)：`override fun tools(): List<CodingTool> = miCloudTools(`
- [L150](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:150)：`override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))`
- [L152](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:152)：`override fun promptBody(status: PluginStatus): String {`
- [L175](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:175)：`private fun string(id: Int): String = context.getString(id)`
- [L177](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:177)：`private fun webWriter(): MiCloudWebWriter =`
- [L226](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:226)：`fun enabledFeatureLabels(capabilities: MiCloudCapabilities): List<String> = buildList {`
- [L240](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:240)：`internal sealed interface MiCloudSessionState {`
- [L241](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:241)：`data object NotLoggedIn : MiCloudSessionState`
- [L243](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:243)：`data object Expired : MiCloudSessionState`
- [L245](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:245)：`data class NetworkError(val detail: String) : MiCloudSessionState`
- [L247](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:247)：`data class Ready(val nickname: String?) : MiCloudSessionState`
- [L250](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:250)：`internal suspend fun resolveMiCloudSessionState(`
- [L266](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt:266)：`internal fun enabledFeatureCount(capabilities: MiCloudCapabilities): Int =`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt`

### plugin/micloud/MiCloudPreferences.kt

**职责**：持久化七类小米云功能开关。笔记/待办开关包含读写，其余功能按当前契约只读。

**规模**：222 行；19 个词法声明。源码：[MiCloudPreferences.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:1)。

**声明与运行入口**：

- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:21)：`enum class MiCloudFeature {`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:31)：`data class MiCloudCapabilities(`
- [L46](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:46)：`fun MiCloudCapabilities.readEnabled(feature: MiCloudFeature): Boolean = when (feature) {`
- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:56)：`fun MiCloudCapabilities.writeEnabled(feature: MiCloudFeature): Boolean = when (feature) {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:62)：`internal fun MiCloudCapabilities.withRead(feature: MiCloudFeature, enabled: Boolean): MiCloudCapabilities = when (feature) {`
- [L82](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:82)：`internal fun MiCloudCapabilities.withWrite(feature: MiCloudFeature, enabled: Boolean): MiCloudCapabilities = when (feature) {`
- [L98](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:98)：`interface MiCloudSettings {`
- [L101](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:101)：`suspend fun setRead(feature: MiCloudFeature, enabled: Boolean)`
- [L103](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:103)：`suspend fun setWrite(feature: MiCloudFeature, enabled: Boolean)`
- [L105](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:105)：`suspend fun setFeature(feature: MiCloudFeature, enabled: Boolean) {`
- [L110](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:110)：`suspend fun clear()`
- [L125](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:125)：`class MiCloudPreferences(`
- [L151](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:151)：`override suspend fun setRead(feature: MiCloudFeature, enabled: Boolean) {`
- [L155](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:155)：`override suspend fun setWrite(feature: MiCloudFeature, enabled: Boolean) {`
- [L159](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:159)：`override suspend fun setFeature(feature: MiCloudFeature, enabled: Boolean) {`
- [L170](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:170)：`override suspend fun clear() {`
- [L179](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:179)：`private suspend fun update(transform: (MiCloudCapabilities) -> MiCloudCapabilities) {`
- [L196](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:196)：`private fun decode(values: Preferences): MiCloudCapabilities {`
- [L212](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt:212)：`private fun write(values: MutablePreferences, capabilities: MiCloudCapabilities) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`

### plugin/micloud/MiCloudTools.kt

**职责**：各小米云数据域的工具参数、API 请求拼装、读写 action、裁剪和 JSON 结果规范；1008 行集中多个领域，宜按领域拆分。

**规模**：1008 行；89 个词法声明。源码：[MiCloudTools.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:1)。

**声明与运行入口**：

- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:39)：`internal interface MiCloudReadApi {`
- [L40](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:40)：`suspend fun userId(): String?`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:41)：`suspend fun getJson(pathWithQuery: String): JsonElement`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:42)：`suspend fun download(pathWithQuery: String, dest: File): File`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:45)：`internal interface MiCloudWriteApi {`
- [L46](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:46)：`suspend fun post(path: String, form: Map<String, String>): String`
- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:49)：`private class ClientReadApi(private val client: MiCloudClient) : MiCloudReadApi {`
- [L50](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:50)：`override suspend fun userId(): String? = client.userId()`
- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:51)：`override suspend fun getJson(pathWithQuery: String): JsonElement = client.getJson(pathWithQuery)`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:52)：`override suspend fun download(pathWithQuery: String, dest: File): File = client.download(pathWithQuery, dest)`
- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:55)：`private class WriterWriteApi(private val writer: MiCloudWebWriter) : MiCloudWriteApi {`
- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:56)：`override suspend fun post(path: String, form: Map<String, String>): String = writer.post(path, form)`
- [L59](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:59)：`fun miCloudTools(`
- [L73](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:73)：`internal fun buildMiCloudTools(`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:92)：`private fun accountTool(client: MiCloudReadApi): CodingTool = CodingTool(`
- [L116](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:116)：`private fun notesTool(`
- [L202](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:202)：`private fun todoTool(`
- [L283](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:283)：`private fun smsTool(client: MiCloudReadApi): CodingTool = CodingTool(`
- [L322](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:322)：`private fun contactsTool(client: MiCloudReadApi): CodingTool = CodingTool(`
- [L355](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:355)：`private fun galleryTool(client: MiCloudReadApi): CodingTool = CodingTool(`
- [L402](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:402)：`private fun recordingsTool(`
- [L463](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:463)：`private suspend fun noteDetail(client: MiCloudReadApi, id: String): JsonObject {`
- [L468](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:468)：`private suspend fun todoRecord(client: MiCloudReadApi, id: String): JsonObject {`
- [L473](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:473)：`private suspend fun recordingName(client: MiCloudReadApi, userId: String, fileId: String): String {`
- [L481](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:481)：`private suspend fun requiredUserId(client: MiCloudReadApi): String =`
- [L484](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:484)：`private fun requireWrite(settings: MiCloudSettings, feature: MiCloudFeature, message: String) {`
- [L490](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:490)：`private suspend fun miCloudToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {`
- [L506](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:506)：`private fun now(): Long = System.currentTimeMillis()`
- [L508](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:508)：`private fun JsonElement.payloadElement(): JsonElement {`
- [L514](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:514)：`private fun JsonElement.payloadObject(): JsonObject? = payloadElement() as? JsonObject`
- [L516](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:516)：`private fun JsonElement.payloadArray(vararg keys: String): JsonArray {`
- [L524](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:524)：`private fun JsonObject?.objectOrNull(key: String): JsonObject? = (this?.get(key) as? JsonObject)`
- [L526](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:526)：`private fun JsonObject?.arrayOrNull(key: String): JsonArray? = (this?.get(key) as? JsonArray)`
- [L528](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:528)：`private fun JsonObject?.stringOrNull(key: String): String? =`
- [L531](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:531)：`private fun JsonObject?.longOrNull(key: String): Long? =`
- [L534](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:534)：`private fun JsonObject?.intOrNull(key: String): Int? =`
- [L537](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:537)：`private fun JsonObject?.boolOrNull(key: String): Boolean? = when (stringOrNull(key)) {`
- [L543](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:543)：`private fun JsonObject.argString(name: String): String? =`
- [L546](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:546)：`private fun JsonObject.requiredArgString(name: String): String =`
- [L549](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:549)：`private fun JsonObject.argInt(name: String): Int? =`
- [L552](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:552)：`private fun JsonObject.argLong(name: String): Long? =`
- [L555](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:555)：`private fun JsonObject.argBool(name: String): Boolean? =`
- [L558](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:558)：`private fun limitArg(args: JsonObject): Int {`
- [L570](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:570)：`private fun itemsResult(items: List<JsonObject>, extra: JsonObject = JsonObject(emptyMap())): AgentToolResult {`
- [L590](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:590)：`private fun writeResult(body: String): AgentToolResult {`
- [L601](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:601)：`private fun compactPayload(element: JsonElement): String =`
- [L604](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:604)：`private fun compactObject(element: JsonObject): JsonObject =`
- [L607](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:607)：`private fun compactJson(element: JsonElement, depth: Int = 0): JsonElement = when (element) {`
- [L625](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:625)：`private fun contactObjects(element: JsonElement): List<JsonObject> {`
- [L639](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:639)：`private fun noteEntry(item: JsonObject): JsonObject = buildJsonObject {`
- [L649](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:649)：`private fun todoEntry(item: JsonObject): JsonObject {`
- [L663](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:663)：`private fun galleryItem(item: JsonObject): JsonObject = buildJsonObject {`
- [L674](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:674)：`private fun recordingItem(item: JsonObject): JsonObject {`
- [L686](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:686)：`private fun etagOf(record: JsonObject): String? =`
- [L690](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:690)：`private fun uniqueFile(directory: File, name: String): File {`
- [L704](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:704)：`internal fun safeFileName(raw: String): String {`
- [L709](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:709)：`internal fun encodeComponent(value: String): String = URLEncoder.encode(value, "UTF-8")`
- [L711](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:711)：`internal fun notesPagePath(ts: Long, limit: Int, syncTag: String?): String {`
- [L716](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:716)：`internal fun noteDetailPath(id: String, ts: Long): String = "/note/note/${encodeComponent(id)}/?ts=$ts"`
- [L718](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:718)：`internal fun noteCreatePath(): String = "/note/note"`
- [L720](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:720)：`internal fun noteUpdatePath(id: String): String = "/note/note/${encodeComponent(id)}"`
- [L722](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:722)：`internal fun noteDeletePath(id: String): String = "/note/full/${encodeComponent(id)}/delete"`
- [L724](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:724)：`internal fun todoRecordsPath(ts: Long, limit: Int, cursor: String?): String {`
- [L729](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:729)：`internal fun todoRecordPath(id: String): String = "/todo/v1/user/records/${encodeComponent(id)}"`
- [L731](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:731)：`internal fun todoCreatePath(): String = "/todo/v1/user/records"`
- [L733](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:733)：`internal fun todoUpdatePath(id: String): String = "/todo/v1/user/records/${encodeComponent(id)}/update"`
- [L735](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:735)：`internal fun todoDeletePath(id: String): String = "/todo/v1/user/records/${encodeComponent(id)}/delete"`
- [L737](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:737)：`internal fun smsThreadPath(`
- [L748](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:748)：`internal fun contactsPath(userId: String, ts: Long, limit: Int): String =`
- [L751](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:751)：`internal fun galleriesPath(ts: Long): String = "/gallery/user/galleries?ts=$ts"`
- [L753](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:753)：`internal fun albumsPath(ts: Long): String = "/gallery/user/album/list?isShared=false&ts=$ts"`
- [L755](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:755)：`internal fun recordingsPath(userId: String, ts: Long, offset: Int, limit: Int): String =`
- [L758](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:758)：`internal fun recordingDownloadPath(userId: String, fileId: String): String =`
- [L761](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:761)：`internal fun noteCreateForm(entry: JsonObject): Map<String, String> = mapOf("entry" to entry.toString())`
- [L763](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:763)：`internal fun noteUpdateForm(entry: JsonObject): Map<String, String> = mapOf("entry" to entry.toString())`
- [L765](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:765)：`internal fun noteDeleteForm(tag: String, purge: Boolean): Map<String, String> = mapOf(`
- [L770](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:770)：`internal fun todoCreateForm(record: JsonObject): Map<String, String> = mapOf("record" to record.toString())`
- [L772](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:772)：`internal fun todoUpdateForm(previousETag: String, record: JsonObject): Map<String, String> = mapOf(`
- [L777](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:777)：`internal fun todoDeleteForm(prevETag: String): Map<String, String> = mapOf("prevETag" to prevETag)`
- [L779](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:779)：`internal fun buildNoteEntry(`
- [L802](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:802)：`internal fun updatedNoteEntry(`
- [L818](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:818)：`internal fun buildTodoRecord(`
- [L856](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:856)：`internal fun updatedTodoRecord(`
- [L884](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:884)：`private fun toolParams(`
- [L897](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:897)：`private fun propString(description: String): JsonObject = buildJsonObject {`
- [L902](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:902)：`private fun propInt(`
- [L915](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:915)：`private fun propLong(description: String): JsonObject = buildJsonObject {`
- [L920](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:920)：`private fun propBool(description: String, default: Boolean? = null): JsonObject = buildJsonObject {`
- [L926](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt:926)：`private fun propEnum(description: String, values: List<String>): JsonObject = buildJsonObject {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt`

### plugin/micloud/MiCloudWebWriter.kt

**职责**：在 i.mi.com 同源 WebView 中执行写请求，通过带请求标识的 JS bridge 返回结果；限制来源、加锁及超时。

**规模**：249 行；17 个词法声明。源码：[MiCloudWebWriter.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:1)。

**声明与运行入口**：

- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:35)：`internal fun miCloudUrl(path: String): String? {`
- [L48](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:48)：`internal fun cookieValue(cookie: String?, name: String): String? {`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:58)：`internal fun encodeForm(form: Map<String, String>): String = form.entries.joinToString("&") { (key, value) ->`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:62)：`internal fun isAuthFailure(status: Int, body: String): Boolean {`
- [L70](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:70)：`class MiCloudWebWriter(`
- [L85](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:85)：`suspend fun post(path: String, form: Map<String, String>): String = mutex.withLock {`
- [L93](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:93)：`fun destroy() {`
- [L109](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:109)：`private suspend fun dispatch(url: String, body: String): String = withContext(Dispatchers.Main) {`
- [L134](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:134)：`private suspend fun loadedWebView(): WebView {`
- [L158](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:158)：`private fun createWebView(cookie: String?): WebView {`
- [L167](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:167)：`override fun onPageFinished(view: WebView, url: String) {`
- [L174](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:174)：`override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {`
- [L186](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:186)：`private fun restoreCookies(cookie: String?) {`
- [L197](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:197)：`private suspend fun currentServiceToken(): String? {`
- [L204](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:204)：`private fun fetchScript(id: String, url: String, body: String): String = """`
- [L233](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:233)：`private data class FetchResponse(val status: Int, val body: String)`
- [L237](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudWebWriter.kt:237)：`fun onResult(payload: String) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudClient.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudParsers.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudTools.kt`

### plugin/websearch/DeepSeekWebProvider.kt

**职责**：DeepSeek 搜索/抓取 HTTP 实现，复用提供商 API Key，解析搜索结果并限制返回内容长度。

**规模**：117 行；7 个词法声明。源码：[DeepSeekWebProvider.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:1)。

**声明与运行入口**：

- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:17)：`class DeepSeekWebProvider(`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:25)：`override suspend fun search(query: String, maxResults: Int): WebSearchResult {`
- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:51)：`override suspend fun fetch(url: String, maxChars: Int): String {`
- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:57)：`private suspend fun post(url: String, body: String, label: String): JsonObject = withContext(Dispatchers.IO) {`
- [L89](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:89)：`private fun parseResponse(label: String, payload: String): JsonObject {`
- [L98](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:98)：`private fun errorMessage(label: String, status: Int, payload: String): String {`
- [L110](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt:110)：`private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt`

### plugin/websearch/WebProvider.kt

**职责**：搜索/网页抓取的提供者接口、结果类型与数量/内容上限，为替换服务提供接缝。

**规模**：21 行；5 个词法声明。源码：[WebProvider.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt:6)：`interface WebProvider {`
- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt:7)：`suspend fun search(query: String, maxResults: Int): WebSearchResult`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt:9)：`suspend fun fetch(url: String, maxChars: Int): String`
- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt:12)：`data class WebSearchResult(`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt:16)：`data class WebSearchItem(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt`

### plugin/websearch/WebSearchPlugin.kt

**职责**：不依赖 Linux 的搜索插件，检查 DeepSeek Key 并贡献搜索/抓取工具。

**规模**：79 行；11 个词法声明。源码：[WebSearchPlugin.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:1)。

**声明与运行入口**：

- [L14](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:14)：`class WebSearchPlugin(`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:26)：`override suspend fun availability(): PluginAvailability = PluginAvailability.Available`
- [L28](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:28)：`override suspend fun install(progress: PluginProgress) = Unit`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:30)：`override suspend fun uninstall() = Unit`
- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:32)：`override suspend fun recover() = Unit`
- [L34](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:34)：`override suspend fun status(): PluginStatus = if (configured()) {`
- [L40](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:40)：`override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = listOf(`
- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:57)：`override fun tools(): List<CodingTool> = webTools(provider)`
- [L59](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:59)：`override fun promptBody(status: PluginStatus): String = when (status) {`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:76)：`private suspend fun configured(): Boolean = apiKeyProvider()?.isNotBlank() == true`
- [L78](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt:78)：`private fun string(id: Int): String = context.resources.getString(id)`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt`

### plugin/websearch/WebTools.kt

**职责**：web_search/web_fetch 的参数、调用与结构化输出，把模型调用转换为 WebProvider 请求。

**规模**：151 行；9 个词法声明。源码：[WebTools.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:1)。

**声明与运行入口**：

- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:21)：`fun webTools(provider: WebProvider): List<CodingTool> = listOf(`
- [L65](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:65)：`private fun webSearchTool(provider: WebProvider): CodingTool = CodingTool(`
- [L88](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:88)：`private fun webFetchTool(provider: WebProvider): CodingTool = CodingTool(`
- [L112](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:112)：`private fun searchOutput(query: String, result: WebSearchResult): String {`
- [L128](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:128)：`private fun fetchOutput(url: String, content: String): String = buildString {`
- [L134](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:134)：`private suspend fun webToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {`
- [L144](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:144)：`private fun JsonObject.stringOrNull(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull`
- [L146](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:146)：`private fun JsonObject.intOrNull(name: String): Int? =`
- [L149](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebTools.kt:149)：`private fun JsonObject.requiredString(name: String): String =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/DeepSeekWebProvider.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebProvider.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt`

### plugin/wecom/WecomCli.kt

**职责**：企业微信 CLI 的安装/版本/授权命令及输出解析，隔离 CLI 协议细节。

**规模**：160 行；17 个词法声明。源码：[WecomCli.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:7)：`object WecomCli {`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:29)：`private fun command(arguments: String): String = "$ENV $BINARY $arguments 2>&1"`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:31)：`fun versionCommand(): String = command("--version")`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:33)：`fun authStatusCommand(): String = command("auth show --status")`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:35)：`fun authShowCommand(): String = command("auth show")`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:37)：`fun authInitCommand(): String =`
- [L40](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:40)：`fun logoutCommand(): String = "rm -f $CREDENTIALS_PATH; true"`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:42)：`fun uninstallCommand(): String = buildString {`
- [L48](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:48)：`fun extractCommand(): String = """`
- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:57)：`fun installCommand(): String = """`
- [L68](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:68)：`fun restoreCommand(): String = """`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:74)：`fun parseVersion(output: String): String? = VERSION_PATTERN.find(cleanOutput(output))`
- [L80](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:80)：`fun parseAuthStatus(output: String): Boolean? {`
- [L91](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:91)：`fun parseBotId(output: String): String? =`
- [L97](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:97)：`fun parseQrPageUrl(output: String): String? =`
- [L102](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:102)：`fun isCompleteQrCode(file: File): Boolean {`
- [L125](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt:125)：`private fun cleanOutput(value: String): String = value`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### plugin/wecom/WecomPlugin.kt

**职责**：下载/安装官方企业微信 CLI、扫码授权、连接状态和插件技能管理。

**规模**：318 行；15 个词法声明。源码：[WecomPlugin.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:1)。

**声明与运行入口**：

- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:30)：`class WecomPlugin internal constructor(`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:67)：`override suspend fun availability(): PluginAvailability {`
- [L80](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:80)：`override suspend fun install(progress: PluginProgress) {`
- [L116](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:116)：`override suspend fun uninstall() {`
- [L122](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:122)：`override suspend fun recover() {`
- [L126](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:126)：`override suspend fun status(): PluginStatus {`
- [L153](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:153)：`override suspend fun setup(ui: PluginUi) {`
- [L159](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:159)：`override fun actions(status: PluginStatus): List<PluginAction> = if (status is PluginStatus.Ready) {`
- [L168](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:168)：`override suspend fun runAction(actionId: String, ui: PluginUi) {`
- [L190](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:190)：`override suspend fun detailRows(status: PluginStatus): List<PluginDetailRow> = buildList {`
- [L195](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:195)：`override fun skills(): List<PluginSkill> = listOf(PluginSkill(SKILL_ID, SKILL_MARKDOWN))`
- [L197](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:197)：`override fun promptBody(status: PluginStatus): String = when (status) {`
- [L216](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:216)：`private suspend fun authorize(ui: PluginUi) {`
- [L258](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:258)：`private suspend fun publishQrCode(ui: PluginUi, qrFile: File, pageUrl: AtomicReference<String?>) {`
- [L274](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt:274)：`private suspend fun requireAvailable() {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginDownloader.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt`

### plugin/wecom/WecomRuntime.kt

**职责**：企业微信插件的 Linux 执行抽象与 Android 实现，支持测试替身以及安装暂存工作目录。

**规模**：52 行；11 个词法声明。源码：[WecomRuntime.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:1)。

**声明与运行入口**：

- [L10](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:10)：`internal interface WecomRuntime {`
- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:13)：`suspend fun isInstalled(): Boolean`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:15)：`suspend fun isEnabled(): Boolean`
- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:17)：`suspend fun execute(command: String, timeoutMillis: Long): LinuxResult`
- [L19](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:19)：`suspend fun executeStreaming(`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:26)：`internal class LinuxWecomRuntime(`
- [L34](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:34)：`override suspend fun isInstalled(): Boolean = linux.state.value is LinuxInstallState.Installed`
- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:36)：`override suspend fun isEnabled(): Boolean = settings.isEnabled()`
- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:38)：`override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult =`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:41)：`override suspend fun executeStreaming(`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt:47)：`fun staging(workspaceDir: File): WecomRuntime = LinuxWecomRuntime(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomCli.kt`

### service/AgentService.kt

**职责**：specialUse 前台服务。启动常驻通知、Host 和提醒调度，监听时钟/时区变化，记录退出原因并检查无障碍连接。

**规模**：91 行；7 个词法声明。源码：[AgentService.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:18)：`class AgentService : Service() {`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:22)：`override fun onReceive(context: Context?, intent: Intent?) {`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:30)：`override fun onCreate() {`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:52)：`override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:67)：`override fun onBind(intent: Intent?): IBinder? = null`
- [L69](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:69)：`override fun onDestroy() {`
- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt:77)：`private fun registerClockReceiver() {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApplication.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/AppVisibility.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/ProcessExitLog.kt`

### service/AppVisibility.kt

**职责**：跟踪 App 是否在前台，并提供回到前台的系统入口；提问和插件交互依赖此状态判断是否可展示。

**规模**：29 行；2 个词法声明。源码：[AppVisibility.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AppVisibility.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AppVisibility.kt:7)：`object AppVisibility {`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/AppVisibility.kt:15)：`fun bringToFront(context: Context) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### service/GuiAccessibilityService.kt

**职责**：无障碍服务生命周期与当前窗口信息，把服务实例交给 GuiAccessibilityState。

**规模**：42 行；6 个词法声明。源码：[GuiAccessibilityService.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt:7)：`class GuiAccessibilityService : AccessibilityService() {`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt:16)：`override fun onServiceConnected() {`
- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt:21)：`override fun onAccessibilityEvent(event: AccessibilityEvent?) {`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt:31)：`override fun onInterrupt() = Unit`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt:33)：`override fun onUnbind(intent: Intent?): Boolean {`
- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt:38)：`override fun onDestroy() {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt`

### service/GuiAccessibilityState.kt

**职责**：保存/刷新无障碍服务是否开启和连接，提供插件状态与保活故障诊断。

**规模**：79 行；7 个词法声明。源码：[GuiAccessibilityState.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:1)。

**声明与运行入口**：

- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:11)：`object GuiAccessibilityState {`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:23)：`fun attach(service: GuiAccessibilityService) {`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:29)：`fun detach(service: GuiAccessibilityService) {`
- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:36)：`fun refresh(context: Context) {`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:45)：`fun register(context: Context) {`
- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:55)：`fun unregister() {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityState.kt:62)：`fun isEnabled(context: Context): Boolean {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiAccessibilityActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/GuiAccessibilityService.kt`

### service/KeepAlive.kt

**职责**：检查电池优化/后台限制并跳转系统或小米设置页，辅助用户配置后台运行条件。

**规模**：93 行；12 个词法声明。源码：[KeepAlive.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:1)。

**声明与运行入口**：

- [L14](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:14)：`object KeepAlive {`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:15)：`fun isIgnoringBatteryOptimizations(context: Context): Boolean {`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:20)：`fun isBackgroundRestricted(context: Context): Boolean =`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:23)：`fun supportsAutostartSettings(context: Context): Boolean =`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:26)：`fun supportsPowerSettings(context: Context): Boolean =`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:30)：`fun requestIgnoreBatteryOptimizations(context: Context) {`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:39)：`fun openAutostartSettings(context: Context) {`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:43)：`fun openPowerSettings(context: Context) {`
- [L54](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:54)：`private fun isMiui(): Boolean {`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:63)：`private fun activityExists(context: Context, component: ComponentName): Boolean =`
- [L69](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:69)：`private fun appDetails(context: Context): Intent =`
- [L73](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt:73)：`private fun launch(context: Context, primary: Intent, fallback: Intent) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### service/LinuxJobRunner.kt

**职责**：把后台任务抽象转为 Linux 的流式长命令执行，最大执行时间为 24 小时。

**规模**：21 行；2 个词法声明。源码：[LinuxJobRunner.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/LinuxJobRunner.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/LinuxJobRunner.kt:7)：`class LinuxJobRunner(private val environment: LinuxEnvironment) : BackgroundCommandRunner {`
- [L8](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/LinuxJobRunner.kt:8)：`override suspend fun run(command: String, onOutput: (String) -> Unit): Int {`

**显式内部引用**：`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### service/NotificationCenter.kt

**职责**：统一常驻运行状态、后台完成、安装进度和无障碍异常通知，连接会话跳转与停止动作。

**规模**：355 行；22 个词法声明。源码：[NotificationCenter.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:1)。

**声明与运行入口**：

- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:31)：`class NotificationCenter(`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:35)：`private data class ActiveRun(`
- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:49)：`fun start() {`
- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:57)：`fun stop() {`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:67)：`fun onForegroundStarted() {`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:72)：`fun foregroundNotification(): android.app.Notification {        val latest = active.values.maxByOrNull { it.startedAt }`
- [L104](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:104)：`fun notifyInstall(label: String, stage: String?, progress: Int?) {`
- [L117](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:117)：`fun notifyInstallFinished(label: String, success: Boolean, error: String? = null) {`
- [L134](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:134)：`fun clearInstallNotification() {`
- [L139](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:139)：`fun notifyAccessibilityLost() {`
- [L152](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:152)：`private fun accessibilitySettingsIntent(): PendingIntent {`
- [L163](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:163)：`private suspend fun onEvent(event: SessionRuntimeEvent) {`
- [L217](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:217)：`private suspend fun refreshTitles() {`
- [L226](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:226)：`private fun updateForeground() {`
- [L231](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:231)：`private fun postResult(`
- [L259](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:259)：`private fun activityText(activity: RuntimeActivity?): String = when (activity) {`
- [L268](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:268)：`private fun openAppIntent(sessionId: String?): PendingIntent {`
- [L281](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:281)：`private fun stopSessionIntent(sessionId: String): PendingIntent {`
- [L294](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:294)：`private fun resultId(sessionId: String): Int = 1000 + (sessionId.hashCode() and 0x3FFFF)`
- [L297](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:297)：`private fun post(id: Int, notification: Notification) {`
- [L308](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:308)：`private fun createChannels() {`
- [L352](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt:352)：`fun areNotificationsEnabled(context: Context): Boolean =`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/service/AgentService.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/AppVisibility.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/KeepAlive.kt`

### service/ProcessExitLog.kt

**职责**：读取 ApplicationExitInfo 并记录退出原因，支持判断后台任务因系统杀进程中断的原因。

**规模**：46 行；3 个词法声明。源码：[ProcessExitLog.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/ProcessExitLog.kt:1)。

**声明与运行入口**：

- [L8](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/ProcessExitLog.kt:8)：`object ProcessExitLog {`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/ProcessExitLog.kt:9)：`fun record(context: Context) {`
- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/service/ProcessExitLog.kt:38)：`private fun lastTimestamp(file: File): Long {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/DeepDeepDrawer.kt

**职责**：工作区分组会话列表、置顶、抽屉导航和操作菜单的 Compose 界面。

**规模**：694 行；12 个词法声明。源码：[DeepDeepDrawer.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:1)。

**声明与运行入口**：

- [L73](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:73)：`private sealed interface DrawerEntry {`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:76)：`data object WorkspacesHeader : DrawerEntry {`
- [L80](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:80)：`data class WorkspaceEntry(val workspace: RuntimeWorkspace, val first: Boolean) : DrawerEntry {`
- [L84](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:84)：`data class WorkspaceEmpty(val workspaceId: String) : DrawerEntry {`
- [L88](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:88)：`data class Session(val conversation: ConversationSummary, val workspaceId: String) : DrawerEntry {`
- [L94](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:94)：`fun DeepDeepDrawer(`
- [L314](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:314)：`private fun DrawerWorkspacesHeader(`
- [L346](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:346)：`private fun DrawerWorkspaceRow(`
- [L495](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:495)：`private fun DrawerWorkspaceEmptyRow(modifier: Modifier = Modifier) {`
- [L509](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:509)：`private fun DrawerNavItem(`
- [L546](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:546)：`private fun DrawerSessionRow(`
- [L648](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt:648)：`private fun DrawerAccountRow(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/common/RelativeTime.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/chat/AttachmentUi.kt

**职责**：附件缩略图、打开/分享和预览界面；包含图片缩略图缓存，减少重组时重复解码。

**规模**：579 行；27 个词法声明。源码：[AttachmentUi.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:1)。

**声明与运行入口**：

- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:77)：`private sealed interface FilePreview {`
- [L78](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:78)：`data object Loading : FilePreview`
- [L79](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:79)：`data class ImageData(val bitmap: Bitmap) : FilePreview`
- [L80](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:80)：`data class TextData(val text: String) : FilePreview`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:81)：`data object Failed : FilePreview`
- [L85](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:85)：`internal fun AttachmentCards(`
- [L106](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:106)：`private fun ImageAttachmentCard(`
- [L141](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:141)：`private fun FileAttachmentCard(`
- [L201](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:201)：`internal fun AttachmentChips(`
- [L227](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:227)：`private fun AttachmentPreviewCard(`
- [L332](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:332)：`internal fun AttachmentPreviewDialog(`
- [L397](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:397)：`private fun AttachmentPreviewBody(attachment: AttachmentRef, file: File) {`
- [L438](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:438)：`private fun MarkdownPreviewBody(text: String) {`
- [L459](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:459)：`private fun TextPreviewBody(text: String) {`
- [L480](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:480)：`private fun UnpreviewableContent(attachment: AttachmentRef) {`
- [L512](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:512)：`private fun attachmentIcon(kind: AttachmentKind) = when (kind) {`
- [L517](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:517)：`private fun attachmentFile(context: Context, attachment: AttachmentRef, root: File): File =`
- [L520](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:520)：`private fun isMarkdownAttachment(attachment: AttachmentRef): Boolean {`
- [L525](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:525)：`private fun decodePreviewBitmap(file: File): Bitmap? = decodeBitmap(file, PreviewBitmapLimit)`
- [L527](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:527)：`private fun decodeThumbnailBitmap(file: File): Bitmap? {`
- [L536](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:536)：`private object AttachmentThumbnails {`
- [L538](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:538)：`override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount`
- [L541](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:541)：`fun get(file: File): Bitmap? = memory.get(key(file))`
- [L543](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:543)：`fun put(file: File, bitmap: Bitmap) {`
- [L547](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:547)：`private fun key(file: File): String =`
- [L551](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:551)：`private fun decodeBitmap(file: File, limit: Int): Bitmap? {`
- [L565](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt:565)：`private fun readPreviewText(file: File): String? {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/ChatActions.kt

**职责**：消息动作栏及复制/引用/分享等界面动作，独立于模型执行。

**规模**：11 行；1 个词法声明。源码：[ChatActions.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatActions.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatActions.kt:7)：`internal fun copyToClipboard(context: Context, text: String): Boolean {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/ChatCommands.kt

**职责**：识别人用 slash 命令，区分压缩、目标和技能调用，支持词边界与输入补全。

**规模**：158 行；18 个词法声明。源码：[ChatCommands.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:5)：`enum class ChatCommand(val commandName: String) {`
- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:11)：`fun byName(name: String): ChatCommand? = entries.firstOrNull { it.commandName == name }`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:15)：`sealed interface ParsedCommand {`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:16)：`data object None : ParsedCommand`
- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:18)：`data class Found(val command: ChatCommand, val args: String) : ParsedCommand`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:20)：`data class Skill(val name: String, val args: String) : ParsedCommand`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:22)：`data class Unknown(val name: String) : ParsedCommand`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:25)：`data class CommandTrigger(`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:30)：`fun parseChatCommand(input: String): ParsedCommand {`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:52)：`fun findCommandTrigger(input: String): CommandTrigger? {`
- [L60](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:60)：`fun commandQuery(input: String): String? = findCommandTrigger(input)?.query`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:62)：`fun formatTokenCount(value: Long): String = when {`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:76)：`data class SkillMentions(`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:81)：`fun extractSkillMentions(input: String): SkillMentions? {`
- [L103](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:103)：`private fun skillNameAt(input: String, index: Int): String? {`
- [L116](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:116)：`private fun Char.isSkillNameChar(): Boolean = this in 'a'..'z' // this in '0'..'9' // this == '_' // this == '-'`
- [L118](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:118)：`private fun codeRanges(input: String): BooleanArray {`
- [L156](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt:156)：`private fun mark(marked: BooleanArray, from: Int, to: Int) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`

### ui/chat/ChatComposer.kt

**职责**：输入框、附件入口、模型/思考选择、语音/发送/停止及 slash 面板的界面组合。

**规模**：522 行；5 个词法声明。源码：[ChatComposer.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt:1)。

**声明与运行入口**：

- [L83](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt:83)：`internal fun ChatComposer(`
- [L465](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt:465)：`private fun ComposerChip(`
- [L499](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt:499)：`internal fun thinkingLabel(level: ThinkingLevel): String = stringResource(`
- [L509](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt:509)：`private class SlashCommandColor(private val color: Color) : VisualTransformation {`
- [L510](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt:510)：`override fun filter(text: AnnotatedString): TransformedText {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDropdown.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/ChatMarkdown.kt

**职责**：把 MarkdownBlock/InlineNode 映射为 Compose 文本、列表、表格、代码等可见组件。

**规模**：691 行；21 个词法声明。源码：[ChatMarkdown.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:1)。

**声明与运行入口**：

- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:67)：`internal fun ChatMarkdown(`
- [L91](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:91)：`private fun rememberFileClickHandler(onFileClick: ((String) -> Unit)?): ((String) -> Unit)? {`
- [L103](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:103)：`private fun MarkdownBlocks(`
- [L116](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:116)：`private fun MarkdownBlockItem(`
- [L179](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:179)：`private fun QuoteBlock(`
- [L209](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:209)：`private fun FootnoteList(`
- [L242](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:242)：`private fun CodeBlock(language: String?, code: String, colors: DeepDeepColors) {`
- [L301](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:301)：`private fun codeColor(type: CodeTokenType, colors: DeepDeepColors): Color = when (type) {`
- [L311](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:311)：`private fun ListBlockBody(`
- [L324](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:324)：`private fun ListItemBody(`
- [L354](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:354)：`private fun ListMarker(marker: MarkdownMarker, colors: DeepDeepColors, depth: Int) {`
- [L391](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:391)：`private fun TableBlock(`
- [L410](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:410)：`fun measure(cell: String, style: TextStyle) {`
- [L476](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:476)：`private fun TableRow(`
- [L505](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:505)：`private fun distributeRemaining(`
- [L534](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:534)：`private fun rememberedInline(`
- [L543](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:543)：`private fun renderInline(`
- [L556](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:556)：`private fun AnnotatedString.Builder.appendFileReference(`
- [L565](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:565)：`override fun onClick(link: LinkAnnotation) {`
- [L577](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:577)：`private fun AnnotatedString.Builder.appendInlineNodes(`
- [L629](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt:629)：`override fun onClick(link: LinkAnnotation) {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/InlineCodeText.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/ChatScreen.kt

**职责**：聊天主布局，衔接消息列表、顶栏、输入区、滚动、交付卡、提问和当前运行状态。

**规模**：643 行；6 个词法声明。源码：[ChatScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt:1)。

**声明与运行入口**：

- [L109](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt:109)：`fun ChatScreen(`
- [L215](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt:215)：`fun copyText(text: String) {`
- [L525](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt:525)：`private fun EmptyState(`
- [L581](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt:581)：`private fun greeting(nickname: String): String {`
- [L591](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt:591)：`private fun ErrorRow(error: String) {`
- [L615](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt:615)：`private fun JumpToBottomChip(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ContextMeter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/JobsMeter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/WorkingIndicator.kt`

### ui/chat/ChatScrollController.kt

**职责**：消息列表滚动位置、跟随底部和用户主动滚动之间的协调，避免流式输出强行拉回。

**规模**：122 行；10 个词法声明。源码：[ChatScrollController.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:1)。

**声明与运行入口**：

- [L19](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:19)：`internal class ChatScrollController(private val listState: LazyListState) {`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:29)：`suspend fun followContent() {`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:35)：`suspend fun anchorToBottom() {`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:44)：`suspend fun jumpToBottom(animate: Boolean) {`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:63)：`fun onUserGesture() {`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:67)：`fun onBottomChanged(value: Boolean) {`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:72)：`private suspend fun awaitSettled() {`
- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:77)：`private fun overflowToBottom(): Float {`
- [L86](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:86)：`private suspend fun settleByOverflow() {`
- [L106](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScrollController.kt:106)：`internal fun rememberChatScrollController(listState: LazyListState): ChatScrollController {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`

### ui/chat/ChatTopBar.kt

**职责**：聊天标题、工作区及更多操作入口，主要负责界面展示与动作回调。

**规模**：115 行；1 个词法声明。源码：[ChatTopBar.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt:1)。

**声明与运行入口**：

- [L28](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt:28)：`internal fun ChatTopBar(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`

### ui/chat/ChatTurn.kt

**职责**：将一轮用户/助手消息与过程、最终文本、附件和交付卡组合成聊天显示单元。

**规模**：474 行；12 个词法声明。源码：[ChatTurn.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:1)。

**声明与运行入口**：

- [L59](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:59)：`private data class BubbleContent(`
- [L65](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:65)：`internal fun ChatTurn(`
- [L113](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:113)：`private fun NoticeRow(notice: TurnNotice) {`
- [L136](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:136)：`private fun NoticeText(text: String) {`
- [L151](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:151)：`private fun JobFinishedNotice(notice: TurnNotice.JobFinished) {`
- [L173](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:173)：`private fun GoalRoundNotice(notice: TurnNotice.GoalRound) {`
- [L206](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:206)：`private fun UserBubble(`
- [L276](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:276)：`private fun QuoteContext(text: String) {`
- [L296](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:296)：`private fun AssistantTurn(`
- [L413](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:413)：`private fun FailedMark(message: AssistantMessage, onRetry: (Long) -> Unit) {`
- [L446](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:446)：`private fun StatusMark(text: String, color: Color) {`
- [L456](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt:456)：`private fun splitQuoted(text: String): BubbleContent {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SkillInvocation.kt`

### ui/chat/ChatUiState.kt

**职责**：聊天全量界面状态、引用内容等类型，为 ViewModel 的 StateFlow 提供不可变快照。

**规模**：46 行；2 个词法声明。源码：[ChatUiState.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt:1)。

**声明与运行入口**：

- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt:11)：`data class QuotedMessage(`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt:16)：`data class ChatUiState(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/ChatUiStrings.kt

**职责**：把运行活动/错误分类映射成本地化文案，同时决定哪些失败可自动重试。

**规模**：160 行；7 个词法声明。源码：[ChatUiStrings.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:5)：`enum class RunErrorKind {`
- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:17)：`data class ChatUiStrings(`
- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:55)：`fun activity(activity: RuntimeActivity?): String? = when (activity) {`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:64)：`fun runErrorKind(raw: String?): RunErrorKind {`
- [L87](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:87)：`fun canAutoRetry(kind: RunErrorKind): Boolean = when (kind) {`
- [L97](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:97)：`fun runError(raw: String?): String {`
- [L121](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt:121)：`fun fallback(): ChatUiStrings = ChatUiStrings(`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/ChatViewModel.kt

**职责**：聊天协调器：恢复/切换会话、发送、重试、附件、模型、技能、缓存、流式批处理和运行对账；职责范围已明显偏宽。

**规模**：1254 行；67 个词法声明。源码：[ChatViewModel.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1)。

**声明与运行入口**：

- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:47)：`class ChatViewModel(`
- [L114](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:114)：`fun onInputChange(value: String) {`
- [L118](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:118)：`fun refreshAvailableSkills() {`
- [L132](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:132)：`fun quote(messageId: Long, text: String) {`
- [L138](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:138)：`fun clearQuote() {`
- [L142](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:142)：`fun attach(uri: Uri) {`
- [L170](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:170)：`fun removeAttachment(id: String) {`
- [L176](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:176)：`fun send() {`
- [L206](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:206)：`fun retry(messageId: Long) {`
- [L220](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:220)：`private fun beginRetry(sessionId: String) {`
- [L255](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:255)：`private fun markRetryingTail(messages: List<AssistantMessage>, now: Long): List<AssistantMessage> {`
- [L278](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:278)：`private fun maybeAutoRetry(sessionId: String, failure: RuntimeEvent.RunFailed) {`
- [L309](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:309)：`private fun clearAutoRetry(sessionId: String) {`
- [L314](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:314)：`private fun startTurn(prompt: String, titleSeed: String = prompt) {`
- [L360](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:360)：`private fun expandAndSend(name: String, args: String) {`
- [L380](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:380)：`private fun expandInlineAndSend(mentions: SkillMentions) {`
- [L412](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:412)：`fun stop() {`
- [L421](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:421)：`fun onAppForeground() {`
- [L428](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:428)：`private fun scheduleReconcile(sessionId: String, delayMs: Long) {`
- [L440](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:440)：`private suspend fun reconcile(sessionId: String) {`
- [L498](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:498)：`fun compactContext() {`
- [L528](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:528)：`fun showCommandError(message: String) {`
- [L532](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:532)：`private fun showCommandError(message: String, sessionId: String) {`
- [L538](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:538)：`suspend fun reloadSession(): Result<Unit> {`
- [L566](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:566)：`fun newSession(workspaceId: String, initialInput: String = "") {`
- [L573](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:573)：`fun openConversationById(id: String) {`
- [L590](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:590)：`fun selectWorkspace(workspaceId: String) {`
- [L606](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:606)：`fun createWorkspace(name: String) {`
- [L621](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:621)：`fun renameWorkspace(workspaceId: String, name: String) {`
- [L636](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:636)：`fun deleteWorkspace(workspaceId: String) {`
- [L656](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:656)：`suspend fun loadWorkspaceSessionCount(workspaceId: String): Int =`
- [L659](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:659)：`suspend fun loadSearchSessions(): List<SessionSearchHit> = withContext(ioDispatcher) {`
- [L672](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:672)：`fun openConversation(id: String) {`
- [L680](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:680)：`fun setModel(modelId: String) {`
- [L689](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:689)：`fun setProvider(providerId: String) {`
- [L699](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:699)：`fun setThinkingLevel(level: ThinkingLevel) {`
- [L707](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:707)：`private fun changeSessionModel(model: SessionModelSnapshot, thinkingOnly: Boolean) {`
- [L733](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:733)：`fun toggleStep(messageId: Long, stepId: Long) {`
- [L756](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:756)：`fun renameSession(id: String, title: String) {`
- [L763](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:763)：`fun setSessionPinned(id: String, pinned: Boolean) {`
- [L770](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:770)：`fun setSessionArchived(id: String, archived: Boolean) {`
- [L777](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:777)：`fun deleteSession(id: String) {`
- [L800](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:800)：`private suspend fun restoreInitialSession() {`
- [L831](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:831)：`private fun switchToSummary(summary: ConversationSummary) {`
- [L861](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:861)：`private fun openSession(summary: ConversationSummary) {`
- [L925](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:925)：`private fun createSession(workspaceId: String, initialInput: String = "") {`
- [L963](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:963)：`private fun cacheCurrentSession() {`
- [L981](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:981)：`private suspend fun refreshUsage(sessionId: String) {`
- [L993](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:993)：`private suspend fun reloadHistoryAfterCompaction(sessionId: String) {`
- [L1014](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1014)：`private fun onSessionEvent(scoped: SessionRuntimeEvent) {`
- [L1067](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1067)：`private fun bufferActiveDelta(event: RuntimeEvent) {`
- [L1087](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1087)：`private fun flushPendingDeltas() {`
- [L1093](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1093)：`private fun applyPendingDeltas() {`
- [L1104](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1104)：`private fun ChatUiState.conversationState(): ConversationState = ConversationState(`
- [L1114](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1114)：`private fun ChatUiState.applyConversation(state: ConversationState): ChatUiState = copy(`
- [L1123](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1123)：`private fun finishRunError(sessionId: String, message: String) {`
- [L1138](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1138)：`private suspend fun refreshSessions() {`
- [L1163](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1163)：`private fun quoteExcerpt(text: String): String {`
- [L1173](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1173)：`private fun buildPrompt(`
- [L1189](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1189)：`private fun applySessionModel(model: SessionModelSnapshot?) {`
- [L1204](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1204)：`private fun ChatUiState.sessionModel(): SessionModelSnapshot? =`
- [L1208](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1208)：`private fun ModelConfig.toSnapshot(): SessionModelSnapshot =`
- [L1211](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1211)：`private fun consumeFinalText(steps: List<AssistantStep>, finalText: String): List<AssistantStep> {`
- [L1223](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1223)：`private fun userFacingError(message: String): String = when {`
- [L1235](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1235)：`private fun titleFrom(text: String): String {`
- [L1240](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1240)：`private fun localizedTitle(title: String): String =`
- [L1243](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt:1243)：`private fun titleFrom(messages: List<AssistantMessage>): String {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/AttachmentStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SessionSearchHit.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt`

### ui/chat/CodeHighlighter.kt

**职责**：代码 token 分析和语法着色规则，由 Markdown 代码块显示调用。

**规模**：381 行；8 个词法声明。源码：[CodeHighlighter.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:3)：`internal enum class CodeTokenType {`
- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:13)：`internal data class CodeToken(val start: Int, val end: Int, val type: CodeTokenType)`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:15)：`private class CodeLanguage(`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:26)：`private fun words(value: String): Set<String> = value.split(' ').filter { it.isNotEmpty() }.toSet()`
- [L267](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:267)：`internal fun highlightCode(code: String, language: String?): List<CodeToken> {`
- [L275](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:275)：`private fun lex(code: String, language: CodeLanguage): List<CodeToken> {`
- [L347](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:347)：`private fun stringEnd(code: String, start: Int, delimiter: String, multiline: Boolean): Int {`
- [L363](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/CodeHighlighter.kt:363)：`private fun diffTokens(code: String): List<CodeToken> {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`

### ui/chat/ContextMeter.kt

**职责**：上下文占用环及交互入口，显示 SessionUsage 中的估算 token 占比。

**规模**：86 行；1 个词法声明。源码：[ContextMeter.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ContextMeter.kt:1)。

**声明与运行入口**：

- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ContextMeter.kt:29)：`fun ContextMeter(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`

### ui/chat/ConversationReducer.kt

**职责**：纯状态归约器，把运行事件转换成消息/步骤/错误/活动状态，使用 runId 过滤迟到事件。

**规模**：450 行；20 个词法声明。源码：[ConversationReducer.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:18)：`internal data class ConversationState(`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:29)：`internal fun reduce(`
- [L147](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:147)：`internal fun ConversationState.finishWithStatus(`
- [L178](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:178)：`internal fun ConversationState.finishWithError(`
- [L204](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:204)：`private fun ConversationState.finishTargetIndex(runId: String): Int {`
- [L221](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:221)：`private fun ConversationState.replaceAndSettle(`
- [L236](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:236)：`internal fun ConversationState.updateAssistant(`
- [L246](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:246)：`internal fun ConversationState.matchesRun(runId: String): Boolean =`
- [L249](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:249)：`internal fun ConversationState.adoptActiveRun(`
- [L283](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:283)：`internal fun nextMessageId(messages: List<AssistantMessage>): Long =`
- [L286](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:286)：`internal fun nextStepId(steps: List<AssistantStep>): Long =`
- [L289](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:289)：`internal fun consumeFinalText(steps: List<AssistantStep>, finalText: String): List<AssistantStep> {`
- [L301](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:301)：`internal fun reminderDisplayText(text: String): String {`
- [L312](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:312)：`private fun AssistantMessage.clearFailureOnProgress(): AssistantMessage = if (retrying) {`
- [L321](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:321)：`private fun ConversationState.onRun(`
- [L326](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:326)：`private fun AssistantMessage.withToolResult(event: RuntimeEvent.ToolFinished): AssistantMessage {`
- [L358](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:358)：`private fun failRunningTools(steps: List<AssistantStep>): List<AssistantStep> = steps.map { step ->`
- [L366](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:366)：`private fun ConversationState.injectTurn(`
- [L400](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:400)：`internal fun ConversationState.appendNotice(`
- [L416](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt:416)：`private fun ConversationState.startBackgroundTurn(`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/InlineCodeText.kt

**职责**：行内代码/特殊文本的 Compose 展示，供 Markdown 渲染器复用。

**规模**：72 行；2 个词法声明。源码：[InlineCodeText.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/InlineCodeText.kt:1)。

**声明与运行入口**：

- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/InlineCodeText.kt:23)：`internal data class RenderedInline(`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/InlineCodeText.kt:29)：`internal fun InlineCodeText(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatCommands.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`

### ui/chat/JobsMeter.kt

**职责**：输入区附近的后台任务数量与状态入口，打开任务列表。

**规模**：51 行；1 个词法声明。源码：[JobsMeter.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/JobsMeter.kt:1)。

**声明与运行入口**：

- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/JobsMeter.kt:24)：`fun JobsMeter(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`

### ui/chat/MarkdownImage.kt

**职责**：Markdown 图片加载/缩放/展示，连接文件或网络图片来源与 Compose。

**规模**：339 行；16 个词法声明。源码：[MarkdownImage.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:1)。

**声明与运行入口**：

- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:61)：`internal sealed interface MarkdownImageState {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:62)：`data object Loading : MarkdownImageState`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:63)：`data class Ready(val bitmap: Bitmap) : MarkdownImageState`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:64)：`data object Failed : MarkdownImageState`
- [L68](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:68)：`internal fun MarkdownImage(alt: String, source: String, title: String?, colors: DeepDeepColors) {`
- [L105](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:105)：`private fun LoadedImage(`
- [L160](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:160)：`private fun MarkdownImagePreview(`
- [L196](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:196)：`private fun ImageFailure(alt: String, title: String?, colors: DeepDeepColors) {`
- [L224](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:224)：`private object MarkdownImageLoader {`
- [L229](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:229)：`override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount`
- [L232](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:232)：`fun load(context: Context, source: String, targetWidthPx: Int): MarkdownImageState {`
- [L262](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:262)：`private fun download(context: Context, source: String): File {`
- [L304](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:304)：`private fun diskFile(context: Context, source: String): File {`
- [L309](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:309)：`private fun sha256(value: String): String {`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:318)：`private fun decode(file: File, targetWidthPx: Int): Bitmap? {`
- [L329](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt:329)：`private fun sampleSize(width: Int, height: Int, targetWidthPx: Int): Int {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/MarkdownParser.kt

**职责**：自建 Markdown 块级和行内解析器，覆盖表格、代码、引用、列表、脚注、数学与文件引用；是独立算法模块但维护面很大。

**规模**：922 行；71 个词法声明。源码：[MarkdownParser.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:3)：`internal sealed interface MarkdownBlock {`
- [L4](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:4)：`data class Heading(val level: Int, val text: String) : MarkdownBlock`
- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:5)：`data class Paragraph(val text: String) : MarkdownBlock`
- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:6)：`data class Code(val language: String?, val code: String) : MarkdownBlock`
- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:7)：`data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock`
- [L8](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:8)：`data class Image(val alt: String, val source: String, val title: String?) : MarkdownBlock`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:9)：`data class ListBlock(val items: List<MarkdownListItem>) : MarkdownBlock`
- [L10](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:10)：`data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock`
- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:11)：`data class Math(val text: String) : MarkdownBlock`
- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:12)：`data object Rule : MarkdownBlock`
- [L15](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:15)：`internal sealed interface MarkdownMarker {`
- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:16)：`data object Bullet : MarkdownMarker`
- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:17)：`data class Ordered(val number: Int) : MarkdownMarker`
- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:18)：`data class Task(val checked: Boolean) : MarkdownMarker`
- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:21)：`internal data class MarkdownListItem(`
- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:27)：`internal data class MarkdownFootnote(val number: Int, val text: String)`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:29)：`internal data class MarkdownDocument(`
- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:36)：`internal sealed interface InlineNode {`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:37)：`data class Text(val text: String) : InlineNode`
- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:38)：`data class Code(val text: String) : InlineNode`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:39)：`data class Kbd(val text: String) : InlineNode`
- [L40](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:40)：`data class FileRef(val text: String, val path: String, val code: Boolean = false) : InlineNode`
- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:41)：`data class Bold(val children: List<InlineNode>) : InlineNode`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:42)：`data class Italic(val children: List<InlineNode>) : InlineNode`
- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:43)：`data class Strike(val children: List<InlineNode>) : InlineNode`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:44)：`data class Link(val children: List<InlineNode>, val destination: String) : InlineNode`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:45)：`data class Colored(val color: Int, val children: List<InlineNode>) : InlineNode`
- [L46](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:46)：`data class Superscript(val children: List<InlineNode>) : InlineNode`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:47)：`data class Subscript(val children: List<InlineNode>) : InlineNode`
- [L48](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:48)：`data class Math(val text: String) : InlineNode`
- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:49)：`data class FootnoteRef(val number: Int) : InlineNode`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:52)：`private data class RawListItem(`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:58)：`private class InlineContext {`
- [L95](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:95)：`internal fun parseMarkdown(source: String): List<MarkdownBlock> = parseMarkdownDocument(source).blocks`
- [L97](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:97)：`internal fun parseMarkdownDocument(source: String): MarkdownDocument {`
- [L108](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:108)：`private fun parseBlocks(source: String, context: InlineContext): List<MarkdownBlock> {`
- [L233](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:233)：`private fun extractDefinitions(lines: List<String>, context: InlineContext): List<String> {`
- [L267](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:267)：`private fun addFootnote(label: String, text: String, context: InlineContext) {`
- [L275](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:275)：`private fun parseList(lines: List<String>, start: Int): Pair<MarkdownBlock.ListBlock, Int> {`
- [L305](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:305)：`private fun parseListItem(line: String): RawListItem? {`
- [L328](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:328)：`private fun buildListItems(entries: List<RawListItem>): List<MarkdownListItem> {`
- [L333](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:333)：`private fun buildItems(`
- [L369](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:369)：`private fun setextLevel(line: String): Int? {`
- [L378](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:378)：`private fun leadingWhitespace(line: String): String = line.takeWhile { it == ' ' // it == '\t' }`
- [L380](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:380)：`private fun indentWidth(whitespace: String): Int {`
- [L386](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:386)：`private fun isBlockStart(lines: List<String>, index: Int): Boolean {`
- [L399](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:399)：`private fun isTableStart(lines: List<String>, index: Int): Boolean {`
- [L406](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:406)：`private fun splitRow(line: String): List<String> =`
- [L409](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:409)：`internal fun markdownToPlainText(text: String): String {`
- [L420](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:420)：`private fun blockPlainText(block: MarkdownBlock, document: MarkdownDocument): String = when (block) {`
- [L433](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:433)：`private fun flattenBlocks(blocks: List<MarkdownBlock>, document: MarkdownDocument): String =`
- [L436](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:436)：`private fun flattenItems(items: List<MarkdownListItem>, depth: Int, document: MarkdownDocument): String =`
- [L451](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:451)：`private fun plainInline(text: String, document: MarkdownDocument): String =`
- [L454](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:454)：`internal fun inlineNodes(`
- [L460](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:460)：`internal fun inlinePlainText(`
- [L466](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:466)：`private fun inlinePlainNode(node: InlineNode): String = when (node) {`
- [L482](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:482)：`private fun inlinePlainChildren(children: List<InlineNode>): String =`
- [L485](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:485)：`private fun linkDestination(raw: String): String {`
- [L492](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:492)：`private fun indexOfIgnoreCase(text: String, needle: String, start: Int): Int {`
- [L501](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:501)：`private fun normalizeCodeSpan(content: String): String {`
- [L513](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:513)：`internal fun isPathLikeInlineCode(text: String): Boolean {`
- [L523](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:523)：`internal fun isWorkspaceFileReference(value: String): Boolean {`
- [L540](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:540)：`private fun hasKnownFileExtension(value: String): Boolean {`
- [L546](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:546)：`private fun fileReferenceEnd(text: String, index: Int): Int {`
- [L568](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:568)：`private fun parseHexColor(value: String): Int? {`
- [L585](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:585)：`private class InlineScanner(`
- [L589](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:589)：`fun parse(text: String): List<InlineNode> {`
- [L592](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:592)：`fun flush() {`
- [L847](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:847)：`private fun isUrlAt(text: String, index: Int): Boolean {`
- [L856](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:856)：`private fun angleAutolink(text: String, index: Int): Pair<InlineNode, Int>? {`
- [L870](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt:870)：`private fun inlineHtml(text: String, index: Int): Pair<List<InlineNode>, Int>? {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt`

### ui/chat/PresentCard.kt

**职责**：解析 present 工具结果并展示文件、图片、HTML 和创意卡片，转交打开/分享动作。

**规模**：472 行；25 个词法声明。源码：[PresentCard.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:1)。

**声明与运行入口**：

- [L70](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:70)：`enum class PresentedItemType { FILE, IMAGE, HTML, CREATIVE }`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:72)：`data class PresentedItem(`
- [L86](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:86)：`fun parsePresentedItems(trace: ToolTrace): List<PresentedItem>? {`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:92)：`internal fun parsePresentedItems(raw: String): List<PresentedItem>? = try {`
- [L105](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:105)：`private fun parsePresentedItem(element: JsonElement): PresentedItem? {`
- [L138](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:138)：`private fun parseLegacyFiles(array: JsonArray?): List<PresentedItem> {`
- [L152](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:152)：`private fun inferPresentedType(path: String): PresentedItemType = when {`
- [L158](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:158)：`private fun derivePresentedName(path: String): String =`
- [L161](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:161)：`private fun String.extension(): String = substringAfterLast('.', "").lowercase(Locale.US)`
- [L166](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:166)：`internal fun resolvePresentedPath(raw: String, root: File): String {`
- [L179](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:179)：`fun PresentCard(`
- [L210](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:210)：`private fun PresentedImageItem(item: PresentedItem, root: File, colors: DeepDeepColors) {`
- [L221](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:221)：`private fun PresentedHtmlItem(item: PresentedItem, root: File, colors: DeepDeepColors) {`
- [L266](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:266)：`private fun PresentedFileItem(`
- [L325](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:325)：`private fun PresentedCreativeItem(item: PresentedItem, onOpenWorkshop: (String) -> Unit) {`
- [L339](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:339)：`private fun PresentedFailureRow(name: String, colors: DeepDeepColors) {`
- [L373](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:373)：`private class HtmlWebViewHolder {`
- [L376](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:376)：`fun attach(webView: WebView) {`
- [L380](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:380)：`fun release() {`
- [L389](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:389)：`private fun HtmlSurface(`
- [L411](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:411)：`private fun createHtmlWebView(context: Context, path: String): WebView = WebView(context).apply {`
- [L429](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:429)：`override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {`
- [L442](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:442)：`override fun onConsoleMessage(message: ConsoleMessage): Boolean = true`
- [L448](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:448)：`private fun PresentedHtmlDialog(path: String, onDismiss: () -> Unit) {`
- [L471](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt:471)：`private fun JsonObject.stringOrNull(name: String): String? =`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`

### ui/chat/ProcessSection.kt

**职责**：运行过程折叠区，按步骤呈现中间文本、推理和工具记录，结束后收起。

**规模**：219 行；2 个词法声明。源码：[ProcessSection.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt:1)。

**声明与运行入口**：

- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt:56)：`internal fun ProcessSection(`
- [L204](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt:204)：`internal fun durationLabel(durationMs: Long): String = when {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AnsweredAskCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ReasoningCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt`

### ui/chat/ReasoningCard.kt

**职责**：思考过程显示、计时与展开动画，复用设计层 motion。

**规模**：119 行；1 个词法声明。源码：[ReasoningCard.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ReasoningCard.kt:1)。

**声明与运行入口**：

- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ReasoningCard.kt:43)：`internal fun ReasoningCard(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`

### ui/chat/SelectionActions.kt

**职责**：文本选择后的操作界面及动作回调，关联复制、引用等行为。

**规模**：297 行；14 个词法声明。源码：[SelectionActions.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:1)。

**声明与运行入口**：

- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:53)：`internal class SelectionMenuController : TextContextMenuProvider {`
- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:57)：`override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {`
- [L60](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:60)：`override fun close() {`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:72)：`fun dismiss() {`
- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:77)：`internal data class SelectionMenuRequest(`
- [L83](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:83)：`internal fun SelectableMessageText(`
- [L103](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:103)：`private fun SelectableMessageTextImpl(`
- [L149](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:149)：`private fun SelectionActionsMenu(`
- [L177](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:177)：`fun close() {`
- [L181](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:181)：`fun copySelection(): String? {`
- [L243](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:243)：`private fun SelectionTextAction(`
- [L265](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:265)：`internal object ReadAloud {`
- [L270](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:270)：`fun speak(context: Context, text: String) {`
- [L293](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt:293)：`fun stop() {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt`

### ui/chat/SelectionCoordinator.kt

**职责**：协调聊天中多文本区域选择，避免不同消息同时持有冲突选择状态。

**规模**：71 行；9 个词法声明。源码：[SelectionCoordinator.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:1)。

**声明与运行入口**：

- [L11](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:11)：`internal class SelectionCoordinator {`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:24)：`fun activate(messageId: Long, bounds: Rect?) {`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:29)：`fun updateBounds(messageId: Long, bounds: Rect) {`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:33)：`fun updateMenuDismisser(messageId: Long, dismiss: (() -> Unit)?) {`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:37)：`fun deactivate(messageId: Long) {`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:42)：`fun clear() {`
- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:51)：`fun isInsideSelectionArea(positionInWindow: Offset, marginPx: Float): Boolean {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:62)：`private fun reset() {`
- [L68](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionCoordinator.kt:68)：`fun tokenFor(messageId: Long): Int = if (resetMessageId == messageId) resetToken else 0`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`

### ui/chat/SessionSearchHit.kt

**职责**：搜索结果命中数据类型，把会话信息与匹配片段传给搜索界面。

**规模**：8 行；1 个词法声明。源码：[SessionSearchHit.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SessionSearchHit.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SessionSearchHit.kt:5)：`data class SessionSearchHit(`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/FilterSessionSearch.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/SkillInvocation.kt

**职责**：从输入中提取技能引用、忽略代码区域并拼接技能提示词，供发送前展开使用。

**规模**：31 行；3 个词法声明。源码：[SkillInvocation.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SkillInvocation.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SkillInvocation.kt:3)：`data class SkillInvocation(`
- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SkillInvocation.kt:12)：`fun parseSkillInvocation(text: String): SkillInvocation? {`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SkillInvocation.kt:23)：`internal fun stripSkillInvocation(text: String): String {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/SlashPalette.kt

**职责**：slash 命令/技能候选面板与选择展示，配合 ChatCommands 只补全输入。

**规模**：179 行；2 个词法声明。源码：[SlashPalette.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt:1)。

**声明与运行入口**：

- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt:51)：`data class CommandItem(`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt:61)：`fun SlashPalette(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/ToolCard.kt

**职责**：工具调用名称、参数、输出、错误、耗时和展开状态展示。

**规模**：228 行；2 个词法声明。源码：[ToolCard.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt:1)。

**声明与运行入口**：

- [L50](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt:50)：`internal fun ToolCard(`
- [L212](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt:212)：`private fun toolArgsPreview(arguments: Map<String, String>): String {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolNames.kt`

### ui/chat/ToolNames.kt

**职责**：将运行时工具名和 MCP 工具名转换成界面友好名称。

**规模**：14 行；1 个词法声明。源码：[ToolNames.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolNames.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolNames.kt:6)：`fun displayToolName(name: String): String {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/UsageSheet.kt

**职责**：展示当前会话上下文/窗口和输入输出/缓存/费用统计。

**规模**：185 行；6 个词法声明。源码：[UsageSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt:1)。

**声明与运行入口**：

- [L34](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt:34)：`fun UsageSheet(usage: SessionUsage, onDismiss: () -> Unit) {`
- [L141](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt:141)：`private fun UsageStatRow(label: String, value: String) {`
- [L162](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt:162)：`internal fun formatTokens(value: Long): String = when {`
- [L176](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt:176)：`private fun formatContextTokens(value: Long): String = when {`
- [L182](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt:182)：`private fun compact(value: Double): String =`
- [L185](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt:185)：`private fun formatCost(cost: Double): String = String.format(Locale.US, "%.3f", cost)`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/chat/WorkingIndicator.kt

**职责**：当前活动文字、spinner 和耗时的界面逻辑，包含计时格式化规则。

**规模**：92 行；2 个词法声明。源码：[WorkingIndicator.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/WorkingIndicator.kt:1)。

**声明与运行入口**：

- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/WorkingIndicator.kt:27)：`internal fun formatElapsed(`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/WorkingIndicator.kt:45)：`internal fun WorkingIndicator(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTypingDots.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownParser.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

### ui/common/Haptics.kt

**职责**：共享触觉反馈调用，供菜单/点击等交互复用。

**规模**：5 行；0 个词法声明。源码：[Haptics.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/common/Haptics.kt:1)。

**声明与运行入口**：

- 顶层值/扩展与辅助逻辑，见源码。

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/common/RelativeTime.kt

**职责**：相对时间显示转换，供会话/任务等列表复用。

**规模**：19 行；1 个词法声明。源码：[RelativeTime.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/common/RelativeTime.kt:1)。

**声明与运行入口**：

- [L8](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/common/RelativeTime.kt:8)：`fun rememberRelativeTime(timestamp: Long): String {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/files/FileBrowserSheet.kt

**职责**：浏览当前工作区目录，处理层级返回、选择文件和打开预览。

**规模**：279 行；12 个词法声明。源码：[FileBrowserSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:1)。

**声明与运行入口**：

- [L50](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:50)：`private sealed interface DirectoryListing {`
- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:51)：`data object Loading : DirectoryListing`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:52)：`data class Ready(val entries: List<FileEntry>) : DirectoryListing`
- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:53)：`data object Failed : DirectoryListing`
- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:56)：`private data class FileEntry(`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:64)：`fun FileBrowserSheet(`
- [L141](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:141)：`private fun PathRow(`
- [L189](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:189)：`private fun FileRow(entry: FileEntry, onClick: () -> Unit) {`
- [L237](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:237)：`private fun BrowserLoading() {`
- [L247](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:247)：`private fun BrowserMessage(icon: DdIcon, text: String) {`
- [L264](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:264)：`private fun readDirectory(directory: File): DirectoryListing {`
- [L278](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt:278)：`private fun childPath(parent: String, name: String): String =`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/Attachment.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/goal/GoalBar.kt

**职责**：显示目标内容、轮次/阶段及暂停/继续/清除入口，动作交给目标服务。

**规模**：127 行；3 个词法声明。源码：[GoalBar.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt:1)。

**声明与运行入口**：

- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt:31)：`fun GoalBar(`
- [L109](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt:109)：`internal fun goalPhaseLabel(phase: GoalPhase): String = stringResource(`
- [L119](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt:119)：`internal fun goalPhaseColor(phase: GoalPhase): Color {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/jobs/JobsSheet.kt

**职责**：后台任务列表、输出轮询、停止确认及已结束任务清理界面。

**规模**：307 行；6 个词法声明。源码：[JobsSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt:1)。

**声明与运行入口**：

- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt:55)：`fun JobsSheet(`
- [L138](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt:138)：`private fun EmptyJobs() {`
- [L163](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt:163)：`private fun JobRow(`
- [L243](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt:243)：`private fun JobOutput(output: String?, onCloseOutput: () -> Unit) {`
- [L288](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt:288)：`private fun jobStatusLabel(status: JobStatus): String = stringResource(`
- [L297](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt:297)：`private fun formatDuration(millis: Long): String {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/mcp/McpEditor.kt

**职责**：添加/编辑 HTTP MCP 服务器配置、认证和参数，执行保存/删除/探测动作。

**规模**：458 行；10 个词法声明。源码：[McpEditor.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:1)。

**声明与运行入口**：

- [L59](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:59)：`internal data class McpEditorTarget(`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:64)：`internal fun McpEditorSheet(`
- [L94](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:94)：`fun save() {`
- [L115](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:115)：`fun test() {`
- [L130](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:130)：`fun delete() {`
- [L271](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:271)：`private fun TestResultLine(result: McpProbeResult) {`
- [L295](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:295)：`private fun OAuthSection(`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:318)：`fun signIn() {`
- [L346](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:346)：`fun signOut() {`
- [L356](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt:356)：`fun submitCallback() {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt`

### ui/mcp/McpImport.kt

**职责**：解析并导入用户给出的 MCP 配置，保留可支持的 HTTP 连接信息。

**规模**：114 行；2 个词法声明。源码：[McpImport.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt:1)。

**声明与运行入口**：

- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt:33)：`internal fun McpImportSheet(`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt:47)：`fun submit() {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/mcp/McpOAuthSession.kt

**职责**：MCP 浏览器授权交互、loopback 回调与粘贴回退，承接 SDK OAuthInteraction。

**规模**：138 行；12 个词法声明。源码：[McpOAuthSession.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:1)。

**声明与运行入口**：

- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:22)：`enum class McpOAuthError {`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:26)：`data class McpOAuthUiState(`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:33)：`class McpOAuthSession(`
- [L48](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:48)：`fun begin() {`
- [L54](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:54)：`fun attachCallback(server: OAuthCallbackServer<String>?) {`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:58)：`fun reopen() {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:62)：`override suspend fun authorize(url: String, redirectUri: String): String {`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:74)：`suspend fun awaitCallback(callback: OAuthCallbackServer<String>?, signal: RunSignal? = null): String {`
- [L95](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:95)：`fun submitManualCallback(text: String): Boolean {`
- [L109](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:109)：`fun cancel() {`
- [L117](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:117)：`suspend fun startMcpCallbackServer(serverName: String): OAuthCallbackServer<String>? {`
- [L138](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpOAuthSession.kt:138)：`private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/mcp/McpScreen.kt

**职责**：MCP 服务器列表、启用/探测状态与编辑入口，连接配置存储和连接服务。

**规模**：327 行；7 个词法声明。源码：[McpScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:1)。

**声明与运行入口**：

- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:57)：`internal data class McpServerEntry(`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:63)：`fun McpScreen(`
- [L113](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:113)：`fun toggle(entry: McpServerEntry, enabled: Boolean) {`
- [L269](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:269)：`private fun EmptyMcp() {`
- [L286](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:286)：`private fun McpServerRow(`
- [L313](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:313)：`private fun rowSubtitle(entry: McpServerEntry, result: McpProbeResult?): String {`
- [L327](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt:327)：`private fun urlHost(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty()`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt`

### ui/navigation/AppDestination.kt

**职责**：应用导航目标的封闭类型，定义可进入的页面及其参数。

**规模**：149 行；21 个词法声明。源码：[AppDestination.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:3)：`sealed interface AppDestination {`
- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:7)：`data object Chat : AppDestination {`
- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:12)：`data object Settings : AppDestination {`
- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:17)：`data object Providers : AppDestination {`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:22)：`data class ProviderDetail(val id: String) : AppDestination {`
- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:27)：`data object Archived : AppDestination {`
- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:32)：`data object Mcp : AppDestination {`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:37)：`data object RuntimeSettings : AppDestination {`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:42)：`data object Personalization : AppDestination {`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:47)：`data object Fonts : AppDestination {`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:52)：`data object ChatSettings : AppDestination {`
- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:57)：`data object Privacy : AppDestination {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:62)：`data object Search : AppDestination {`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:67)：`data object Skills : AppDestination {`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:72)：`data object Schedules : AppDestination {`
- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:77)：`data object Plugins : AppDestination {`
- [L82](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:82)：`data object Workshop : AppDestination {`
- [L87](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:87)：`data class WorkshopDetail(val id: String) : AppDestination {`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:92)：`data class PluginDetail(val id: String) : AppDestination {`
- [L97](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:97)：`data class WorkspaceSettings(val workspaceId: String) : AppDestination {`
- [L110](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt:110)：`fun fromRouteKey(routeKey: String): AppDestination? = when {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/navigation/AppNavigator.kt

**职责**：页面栈和前进/返回/重置规则，把导航与业务状态区分开。

**规模**：46 行；5 个词法声明。源码：[AppNavigator.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt:1)。

**声明与运行入口**：

- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt:9)：`class AppNavigator(initialStack: List<AppDestination> = listOf(AppDestination.Chat)) {`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt:20)：`fun push(destination: AppDestination) {`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt:25)：`fun pop(): Boolean {`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt:31)：`fun reset(destination: AppDestination = AppDestination.Chat) {`
- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt:45)：`fun rememberAppNavigator(): AppNavigator =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt`

### ui/navigation/AppScreenTransition.kt

**职责**：统一页面转场方向、持续时间和 Compose 动画，调用设计层 motion。

**规模**：83 行；5 个词法声明。源码：[AppScreenTransition.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt:1)。

**声明与运行入口**：

- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt:20)：`object AppScreenTransition {`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt:24)：`fun direction(initial: AppDestination, target: AppDestination): Int = when {`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt:30)：`fun of(scope: AnimatedContentTransitionScope<AppDestination>): ContentTransform = with(scope) {`
- [L51](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt:51)：`fun AnimatedScreenHost(`
- [L77](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt:77)：`private fun Modifier.overlayTouchBarrier(): Modifier = pointerInput(Unit) {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppDestination.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppNavigator.kt`

### ui/plugins/PluginDetailScreen.kt

**职责**：插件详情、状态、动作、保活/能力开关及安装/启停/升级入口。

**规模**：310 行；1 个词法声明。源码：[PluginDetailScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt:1)。

**声明与运行入口**：

- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt:53)：`fun PluginDetailScreen(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/plugins/PluginSetupSheet.kt

**职责**：展示统一插件安装确认/进度/二维码或授权地址等交互流程。

**规模**：186 行；3 个词法声明。源码：[PluginSetupSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt:1)。

**声明与运行入口**：

- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt:49)：`fun PluginSetupSheet(`
- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt:56)：`fun openUrl(url: String) {`
- [L160](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt:160)：`private fun PluginQrCode(path: String, description: String) {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/plugins/PluginsScreen.kt

**职责**：官方插件目录、已安装状态与详情导航。

**规模**：227 行；2 个词法声明。源码：[PluginsScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt:1)。

**声明与运行入口**：

- [L54](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt:54)：`fun PluginsScreen(`
- [L141](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt:141)：`private fun PluginRow(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/questions/AnsweredAskCard.kt

**职责**：历史提问的只读已回答摘要与展开内容，由 QuestionRecord 驱动。

**规模**：138 行；1 个词法声明。源码：[AnsweredAskCard.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AnsweredAskCard.kt:1)。

**声明与运行入口**：

- [L46](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AnsweredAskCard.kt:46)：`internal fun AnsweredAskCard(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt`

### ui/questions/AskQuestionCard.kt

**职责**：进行中的单选/多选/自定义提问交互和提交/跳过流程，连接 QuestionCenter。

**规模**：556 行；15 个词法声明。源码：[AskQuestionCard.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:1)。

**声明与运行入口**：

- [L71](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:71)：`private data class Draft(`
- [L102](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:102)：`fun AskQuestionCard(`
- [L172](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:172)：`fun select(option: QuestionOption) {`
- [L192](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:192)：`fun updateCustom(value: String) {`
- [L206](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:206)：`fun buildAnswers(): List<QuestionAnswer> = questions.mapIndexed { index, item ->`
- [L215](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:215)：`fun submit() {`
- [L219](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:219)：`fun advance() {`
- [L231](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:231)：`fun skip() {`
- [L430](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:430)：`private fun ProgressLine(progress: Float) {`
- [L449](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:449)：`private fun OptionRow(`
- [L526](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:526)：`private fun RecommendedTag() {`
- [L540](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:540)：`private fun recommendedSuffix(label: String): String? =`
- [L543](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:543)：`private fun displayLabel(label: String): String {`
- [L548](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:548)：`internal fun answerText(answer: QuestionAnswer?): String? {`
- [L555](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt:555)：`private fun <T> List<T>.replaced(index: Int, value: T): List<T> =`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/questions/QuestionRecord.kt

**职责**：解析 ask_user_question 工具输入输出，恢复可以展示的历史问答结构。

**规模**：93 行；6 个词法声明。源码：[QuestionRecord.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt:1)。

**声明与运行入口**：

- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt:16)：`data class QuestionRecord(`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt:23)：`fun parseQuestionRecord(trace: ToolTrace): QuestionRecord? {`
- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt:35)：`private fun parseQuestions(raw: String): List<UserQuestion> {`
- [L63](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt:63)：`private fun parseAnswers(raw: String, questions: List<UserQuestion>): List<QuestionAnswer> {`
- [L89](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt:89)：`private fun JsonObject.string(name: String): String? =`
- [L92](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt:92)：`private fun JsonObject.boolean(name: String): Boolean? =`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`

### ui/schedules/ScheduleDatePicker.kt

**职责**：提醒编辑器中的日期选择控件。

**规模**：221 行；4 个词法声明。源码：[ScheduleDatePicker.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt:1)。

**声明与运行入口**：

- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt:49)：`fun DdDatePicker(`
- [L123](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt:123)：`internal fun SchedulePickerHeader(title: String, onDismiss: () -> Unit) {`
- [L146](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt:146)：`private fun MonthGrid(`
- [L187](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt:187)：`private fun DayCell(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt`

### ui/schedules/ScheduleDraft.kt

**职责**：提醒编辑草稿、输入有效性和模型转换，防止无效时间进入持久化层。

**规模**：101 行；8 个词法声明。源码：[ScheduleDraft.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:1)。

**声明与运行入口**：

- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:16)：`enum class DraftError {`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:24)：`data class ScheduleDraft(`
- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:38)：`fun toTrigger(): ScheduleTrigger = if (repeat) {`
- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:44)：`fun validate(now: ZonedDateTime): DraftError? = when {`
- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:53)：`fun nextRunPreview(now: ZonedDateTime): ZonedDateTime? = toTrigger().nextRun(now)`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:58)：`fun from(schedule: Schedule, conversationTitle: String): ScheduleDraft {`
- [L86](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:86)：`fun preview(text: String): String {`
- [L96](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt:96)：`private fun weeklyCronExpression(days: Set<DayOfWeek>, time: LocalTime): String {`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/schedules/ScheduleEditor.kt

**职责**：创建/修改提醒的完整编辑界面，绑定会话与 trigger。

**规模**：319 行；11 个词法声明。源码：[ScheduleEditor.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:1)。

**声明与运行入口**：

- [L27](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:27)：`internal fun ScheduleEditor(`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:61)：`fun submit() {`
- [L184](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:184)：`private fun FieldLabel(text: String) {`
- [L189](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:189)：`private fun TimeRow(time: LocalTime, onClick: () -> Unit) {`
- [L199](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:199)：`private fun EditorRow(`
- [L228](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:228)：`private fun EnableRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {`
- [L236](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:236)：`private fun RepeatRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {`
- [L244](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:244)：`private fun ErrorText(text: String) {`
- [L249](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:249)：`private fun CronSummaryLine(expression: String) {`
- [L261](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:261)：`private fun conversationLabel(draft: ScheduleDraft): String = when {`
- [L268](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt:268)：`private fun ConversationPickerSheet(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleTimePicker.kt`

### ui/schedules/ScheduleScreen.kt

**职责**：提醒列表、状态、下次投递、开关、运行和编辑入口。

**规模**：349 行；16 个词法声明。源码：[ScheduleScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:1)。

**声明与运行入口**：

- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:38)：`fun ScheduleScreen(`
- [L70](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:70)：`fun openNew() {`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:81)：`fun openExisting(schedule: Schedule) {`
- [L86](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:86)：`fun saveDraft(draft: ScheduleDraft) {`
- [L112](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:112)：`fun runEditorNow() {`
- [L129](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:129)：`fun toggleEnabled(schedule: Schedule, enabled: Boolean) {`
- [L137](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:137)：`fun deleteDraft(draft: ScheduleDraft) {`
- [L207](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:207)：`private fun ScheduleGroup(`
- [L227](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:227)：`private fun ScheduleRow(schedule: Schedule, conversationText: String?, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {`
- [L255](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:255)：`private fun EmptySchedules(modifier: Modifier = Modifier) {`
- [L262](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:262)：`private fun ruleLabel(trigger: ScheduleTrigger): String = when (trigger) {`
- [L277](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:277)：`internal fun cronSummaryLabel(expression: String): String? {`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:318)：`private fun weekdayLabel(days: Set<DayOfWeek>, locale: Locale, separator: String): String =`
- [L324](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:324)：`private fun nextDeliveryLabel(timestamp: Long?): String {`
- [L336](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:336)：`internal fun deliveryErrorRes(error: ScheduleError?): Int = when (error) {`
- [L343](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleScreen.kt:343)：`internal fun draftErrorRes(error: DraftError): Int = when (error) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleEditor.kt`

### ui/schedules/ScheduleTimePicker.kt

**职责**：提醒编辑器的时间选择控件。

**规模**：140 行；2 个词法声明。源码：[ScheduleTimePicker.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleTimePicker.kt:1)。

**声明与运行入口**：

- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleTimePicker.kt:49)：`fun DdTimePicker(`
- [L99](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleTimePicker.kt:99)：`private fun TimeColumn(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt`

### ui/sessions/ArchivedSessionsScreen.kt

**职责**：归档会话列表、打开和恢复入口，复用会话操作。

**规模**：261 行；2 个词法声明。源码：[ArchivedSessionsScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt:1)。

**声明与运行入口**：

- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt:55)：`fun ArchivedSessionsScreen(`
- [L194](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt:194)：`private fun ArchivedSessionRow(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSearchField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt`

### ui/sessions/FilterSessionSearch.kt

**职责**：会话搜索的过滤/排序和匹配片段规则，是可独立测试的纯逻辑。

**规模**：12 行；1 个词法声明。源码：[FilterSessionSearch.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/FilterSessionSearch.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/FilterSessionSearch.kt:5)：`internal fun filterSessionSearch(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SessionSearchHit.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/sessions/SessionActionMenu.kt

**职责**：会话长按/上下文菜单，提供重命名、置顶、归档和删除动作。

**规模**：95 行；3 个词法声明。源码：[SessionActionMenu.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt:1)。

**声明与运行入口**：

- [L16](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt:16)：`enum class SessionAction { RENAME, TOGGLE_PIN, TOGGLE_ARCHIVE, DELETE }`
- [L19](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt:19)：`fun SessionActionMenu(`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt:81)：`private fun MenuAction(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/sessions/SessionDialogs.kt

**职责**：会话重命名、删除等共享对话框。

**规模**：89 行；2 个词法声明。源码：[SessionDialogs.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt:1)。

**声明与运行入口**：

- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt:22)：`fun RenameSessionDialog(`
- [L60](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt:60)：`fun DeleteSessionDialog(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/sessions/SessionSearchScreen.kt

**职责**：搜索输入、结果列表与命中高亮，加载与过滤分离。

**规模**：254 行；2 个词法声明。源码：[SessionSearchScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt:1)。

**声明与运行入口**：

- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt:67)：`fun SessionSearchScreen(`
- [L205](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt:205)：`private fun SearchSessionRow(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SessionSearchHit.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/common/RelativeTime.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSearchField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/FilterSessionSearch.kt`

### ui/settings/ChatSettingsScreen.kt

**职责**：聊天展示、自动重试/后台唤醒等偏好界面。

**规模**：140 行；2 个词法声明。源码：[ChatSettingsScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt:1)。

**声明与运行入口**：

- [L35](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt:35)：`fun ChatSettingsScreen(`
- [L119](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt:119)：`private fun SwitchRow(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AppPreferences.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/DevPackagesSection.kt

**职责**：运行环境页面中的开发软件包列表和安装进度。

**规模**：191 行；2 个词法声明。源码：[DevPackagesSection.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt:1)。

**声明与运行入口**：

- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt:31)：`internal fun DevPackagesSection(`
- [L104](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt:104)：`private fun BundleRow(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/FontScreen.kt

**职责**：字体选择预览与保存界面。

**规模**：173 行；2 个词法声明。源码：[FontScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/FontScreen.kt:1)。

**声明与运行入口**：

- [L41](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/FontScreen.kt:41)：`fun FontScreen(`
- [L106](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/FontScreen.kt:106)：`private fun FontPreviewRow(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/LinuxEnvironmentSection.kt

**职责**：显示 Linux 安装/重置/启用/共享存储状态和操作。

**规模**：227 行；3 个词法声明。源码：[LinuxEnvironmentSection.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt:1)。

**声明与运行入口**：

- [L43](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt:43)：`internal fun LinuxEnvironmentSection(`
- [L187](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt:187)：`private fun SharedStorageRow(`
- [L220](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt:220)：`private fun openSharedStorageSettings(context: Context) {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LinuxSettingsStore.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`

### ui/settings/OAuthLoginSession.kt

**职责**：Provider OAuth 登录交互状态机，承接浏览器、输入、选择和取消。

**规模**：243 行；17 个词法声明。源码：[OAuthLoginSession.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:12)：`data class OAuthPrompt(`
- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:18)：`data class OAuthLoginState(`
- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:32)：`class OAuthLoginSession(`
- [L47](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:47)：`override suspend fun openUrl(url: String, instructions: String?) {`
- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:53)：`override suspend fun showMessage(message: String) {`
- [L68](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:68)：`override suspend fun prompt(message: String, placeholder: String?, secret: Boolean): String =`
- [L96](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:96)：`override suspend fun select(message: String, options: List<OAuthSelectOption>): String =`
- [L129](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:129)：`fun submitPrompt(text: String): Boolean {`
- [L145](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:145)：`fun choose(id: String): Boolean {`
- [L162](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:162)：`fun fail(message: String) {`
- [L178](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:178)：`fun cancel() {`
- [L196](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:196)：`fun succeed() {`
- [L211](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:211)：`private fun update(transform: (OAuthLoginState) -> OAuthLoginState) {`
- [L218](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:218)：`private fun notifyStateChanged() {`
- [L222](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:222)：`private fun takeWaitersLocked(): Waiters {`
- [L230](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:230)：`private class Waiters(`
- [L234](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt:234)：`fun cancel() {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/settings/OAuthLoginSheet.kt

**职责**：Provider OAuth 状态的 Compose 展示与用户交互。

**规模**：194 行；3 个词法声明。源码：[OAuthLoginSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt:1)。

**声明与运行入口**：

- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt:42)：`fun OAuthLoginSheet(`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt:52)：`fun openBrowser(url: String) {`
- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt:58)：`fun submit() {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`

### ui/settings/PersonalizationScreen.kt

**职责**：称呼、交互风格、长期指令等偏好编辑界面。

**规模**：269 行；6 个词法声明。源码：[PersonalizationScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt:1)。

**声明与运行入口**：

- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt:45)：`fun PersonalizationScreen(`
- [L78](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt:78)：`fun currentValue() = PersonalizationUi(`
- [L91](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt:91)：`fun flushAndBack() {`
- [L244](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt:244)：`private fun interactionLabel(style: InteractionStyle): String = stringResource(`
- [L253](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt:253)：`private fun interactionDescription(style: InteractionStyle): String = stringResource(`
- [L262](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt:262)：`private fun toneLabel(tone: TonePreset): String = stringResource(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/PrivacyScreen.kt

**职责**：隐私和数据相关信息页面。

**规模**：105 行；1 个词法声明。源码：[PrivacyScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PrivacyScreen.kt:1)。

**声明与运行入口**：

- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PrivacyScreen.kt:30)：`fun PrivacyScreen(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/ProviderDetailScreen.kt

**职责**：Provider API Key/OAuth 配置与退出登录，不负责替其他会话切换模型。

**规模**：356 行；8 个词法声明。源码：[ProviderDetailScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:1)。

**声明与运行入口**：

- [L53](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:53)：`fun ProviderDetailScreen(`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:72)：`fun beginOAuthLogin() {`
- [L97](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:97)：`fun signOutOAuth() {`
- [L185](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:185)：`private fun OAuthSection(`
- [L214](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:214)：`private fun ProviderOverview(`
- [L267](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:267)：`private fun ApiKeySection(`
- [L278](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:278)：`fun submit() {`
- [L353](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt:353)：`internal fun maskApiKey(key: String?): String {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSession.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderIcon.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/ProviderIcon.kt

**职责**：模型服务提供方图标映射与显示。

**规模**：91 行；1 个词法声明。源码：[ProviderIcon.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderIcon.kt:1)。

**声明与运行入口**：

- [L65](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderIcon.kt:65)：`fun ProviderIcon(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/ProviderListScreen.kt

**职责**：已配置与全部 Provider 目录，进入对应凭据设置。

**规模**：182 行；2 个词法声明。源码：[ProviderListScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt:1)。

**声明与运行入口**：

- [L44](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt:44)：`fun ProviderListScreen(`
- [L139](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt:139)：`private fun ProviderRow(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSearchField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderIcon.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/RuntimeSettingsScreen.kt

**职责**：运行环境总页面，组合 Linux 和开发软件包配置。

**规模**：103 行；1 个词法声明。源码：[RuntimeSettingsScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt:1)。

**声明与运行入口**：

- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt:32)：`fun RuntimeSettingsScreen(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageCatalog.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/settings/SettingsComponents.kt

**职责**：设置页复用的行、分组、说明和点击区域。

**规模**：97 行；4 个词法声明。源码：[SettingsComponents.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt:1)。

**声明与运行入口**：

- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt:32)：`internal fun SettingsGroup(`
- [L50](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt:50)：`internal fun InitialBadge(name: String, size: Dp = 36.dp) {`
- [L69](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt:69)：`internal fun StatusPill(text: String, active: Boolean) {`
- [L84](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt:84)：`internal fun PlaceholderRow(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/settings/SettingsScreen.kt

**职责**：设置导航目录、外观/语言入口及配置状态摘要。

**规模**：556 行；11 个词法声明。源码：[SettingsScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:1)。

**声明与运行入口**：

- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:72)：`fun SettingsScreen(`
- [L277](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:277)：`private fun AccountCard(`
- [L331](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:331)：`private fun NotificationRow() {`
- [L355](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:355)：`private fun ThemeControl(`
- [L384](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:384)：`private fun themeLabel(mode: ThemeMode): String = stringResource(`
- [L393](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:393)：`private fun LanguageRow(`
- [L457](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:457)：`private fun languageLabel(language: AppLanguage): String = stringResource(`
- [L465](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:465)：`private fun Context.findActivity(): Activity? {`
- [L475](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:475)：`private fun LicenseSheet(onClose: () -> Unit) {`
- [L528](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:528)：`private fun LicenseRow(title: String, subtitle: String, onClick: () -> Unit) {`
- [L551](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt:551)：`private fun linuxStatusLabel(state: LinuxInstallState): String = when (state) {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`

### ui/skills/SkillsScreen.kt

**职责**：技能仓库列表、ZIP 导入、内容查看、全局启停/删除和安装反馈。

**规模**：606 行；14 个词法声明。源码：[SkillsScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:1)。

**声明与运行入口**：

- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:74)：`private data class SkillError(`
- [L79](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:79)：`private sealed interface ArchiveRead {`
- [L80](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:80)：`data class Success(val bytes: ByteArray, val fallbackName: String) : ArchiveRead`
- [L81](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:81)：`data class Failure(@StringRes val messageRes: Int) : ArchiveRead`
- [L85](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:85)：`fun SkillsScreen(`
- [L124](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:124)：`fun toggle(skill: InstalledSkill, enabled: Boolean) {`
- [L140](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:140)：`fun delete(skill: InstalledSkill) {`
- [L157](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:157)：`fun install(uri: Uri) {`
- [L330](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:330)：`private fun EmptySkills(onInstall: () -> Unit) {`
- [L360](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:360)：`private fun SkillsGroup(`
- [L410](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:410)：`private fun ManualTag() {`
- [L425](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:425)：`private fun SkillDetailSheet(`
- [L544](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:544)：`private fun SkillArchiveError.messageRes(): Int = when (this) {`
- [L555](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt:555)：`private suspend fun readArchive(context: Context, uri: Uri): ArchiveRead = withContext(Dispatchers.IO) {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillArchive.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/voice/VoiceAssistantBody.kt

**职责**：语音助手 sheet 的输入/识别/发送/播放界面主体。

**规模**：202 行；6 个词法声明。源码：[VoiceAssistantBody.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt:1)。

**声明与运行入口**：

- [L45](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt:45)：`internal fun VoiceIdleBody(onStart: () -> Unit) {`
- [L66](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt:66)：`internal fun VoiceListeningBody(level: Float, transcript: String) {`
- [L90](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt:90)：`internal fun VoiceAnswerBody(`
- [L144](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt:144)：`internal fun VoiceErrorBody(failure: VoiceFailure) {`
- [L165](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt:165)：`private fun VoiceLevelBars(level: Float) {`
- [L191](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt:191)：`internal fun speechErrorMessage(error: SpeechError): String = when (error) {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTypingDots.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt`

### ui/voice/VoiceAssistantSheet.kt

**职责**：语音助手生命周期和依赖装配，连接识别、聊天状态、自动发送与朗读。

**规模**：285 行；2 个词法声明。源码：[VoiceAssistantSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt:1)。

**声明与运行入口**：

- [L56](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt:56)：`internal fun VoiceAssistantSheet(`
- [L278](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt:278)：`private fun Context.findActivity(): Activity? {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/assistant/AssistantRole.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/VoiceLanguage.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt`

### ui/voice/VoiceAssistantState.kt

**职责**：语音交互阶段、文本和发送/播放条件的纯状态规则。

**规模**：81 行；13 个词法声明。源码：[VoiceAssistantState.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:5)：`internal enum class VoicePhase {`
- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:13)：`internal data class VoiceFailure(`
- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:18)：`internal sealed interface VoiceEvent {`
- [L19](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:19)：`data object StartListening : VoiceEvent`
- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:20)：`data class Partial(val text: String) : VoiceEvent`
- [L21](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:21)：`data class Final(val text: String) : VoiceEvent`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:22)：`data class Failed(val error: SpeechError) : VoiceEvent`
- [L23](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:23)：`data class PermissionDenied(val permanently: Boolean) : VoiceEvent`
- [L24](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:24)：`data class AnswerCompleted(val speakable: Boolean) : VoiceEvent`
- [L25](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:25)：`data object SpeechFinished : VoiceEvent`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:26)：`data object StopSpeaking : VoiceEvent`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:29)：`internal data class VoiceAssistantState(`
- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt:36)：`internal fun reduceVoiceState(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/workshop/WorkshopAssetLoader.kt

**职责**：给每个创意生成独立 HTTPS origin，解析安全路径、MIME、字节范围与文件响应，限制文件加载到该创意目录。

**规模**：220 行；18 个词法声明。源码：[WorkshopAssetLoader.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:1)。

**声明与运行入口**：

- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:13)：`data class WorkshopByteRange(val start: Long, val endInclusive: Long) {`
- [L17](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:17)：`object WorkshopAssets {`
- [L52](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:52)：`fun resolveFile(appDir: File, path: String): File? {`
- [L65](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:65)：`fun mimeTypeFor(path: String): String {`
- [L71](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:71)：`fun encodingFor(mimeType: String): String? {`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:76)：`fun parseRange(header: String?, contentLength: Long): WorkshopByteRange? {`
- [L110](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:110)：`class WorkshopAssetServer(appDir: File, manifest: WorkshopManifest) {`
- [L115](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:115)：`override fun handle(path: String): WebResourceResponse? = openResponse(path)`
- [L122](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:122)：`fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {`
- [L134](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:134)：`private fun openResponse(path: String): WebResourceResponse {`
- [L152](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:152)：`private fun rangeResponse(path: String, rangeHeader: String): WebResourceResponse? {`
- [L169](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:169)：`private fun fullHeaders(length: Long): MutableMap<String, String> = mutableMapOf(`
- [L175](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:175)：`private fun rangeHeaders(range: WorkshopByteRange, length: Long): MutableMap<String, String> = mutableMapOf(`
- [L182](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:182)：`private fun notFound(): WebResourceResponse = WebResourceResponse(`
- [L191](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:191)：`private class RangeInputStream(`
- [L202](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:202)：`override fun read(): Int {`
- [L209](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:209)：`override fun read(buffer: ByteArray, offset: Int, length: Int): Int {`
- [L218](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt:218)：`override fun close() = source.close()`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`

### ui/workshop/WorkshopBridgeHost.kt

**职责**：把可信主 frame 的 WebMessage 分发到 router，异步响应、40ms 合并流式事件，并在销毁时移除 bridge。

**规模**：204 行；17 个词法声明。源码：[WorkshopBridgeHost.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:1)。

**声明与运行入口**：

- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:29)：`private sealed interface PendingPush {`
- [L30](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:30)：`class Event(val payload: String) : PendingPush`
- [L32](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:32)：`class Delta(val id: Long, val type: String) : PendingPush {`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:37)：`class WorkshopBridgeHost(`
- [L61](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:61)：`fun attach(): Boolean {`
- [L74](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:74)：`fun detach() {`
- [L84](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:84)：`fun documentStartScript(envJson: String, sdkSource: String): String = buildString {`
- [L91](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:91)：`private suspend fun handleMessage(raw: String) {`
- [L103](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:103)：`private fun emitEvent(envelope: JsonObject) {`
- [L133](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:133)：`private fun flushPending() {`
- [L147](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:147)：`private fun dispatchJson(payload: String) {`
- [L154](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:154)：`private fun postToPage(block: () -> Unit) {`
- [L162](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:162)：`private fun canInject(): Boolean = attached`
- [L164](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:164)：`private fun inject(script: String) {`
- [L170](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:170)：`private fun buildPushScript(pushes: List<PendingPush>): String = buildString {`
- [L191](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:191)：`private fun requestId(raw: String): Long? = runCatching {`
- [L196](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt:196)：`private fun errorEnvelope(id: Long?, message: String): String = JSONObject().apply {`

**显式内部引用**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`

### ui/workshop/WorkshopChatCard.kt

**职责**：解析并展示对话中创意工坊的交付入口。

**规模**：117 行；4 个词法声明。源码：[WorkshopChatCard.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt:1)。

**声明与运行入口**：

- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt:36)：`data class WorkshopCardApp(`
- [L46](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt:46)：`fun parseRegisteredWorkshop(trace: ToolTrace): WorkshopCardApp? {`
- [L69](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt:69)：`fun WorkshopChatCard(`
- [L116](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt:116)：`private fun JsonObject.stringOrNull(name: String): String? =`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/workshop/WorkshopDialogs.kt

**职责**：创意工坊的创建/重命名/删除等对话框。

**规模**：84 行；2 个词法声明。源码：[WorkshopDialogs.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt:1)。

**声明与运行入口**：

- [L20](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt:20)：`fun invalidReasonLabelRes(reason: WorkshopInvalidReason): Int = when (reason) {`
- [L29](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt:29)：`fun WorkshopInvalidDialog(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/workshop/WorkshopScreen.kt

**职责**：扫描创意目录、显示有效/无效条目并进入对应创意。

**规模**：296 行；6 个词法声明。源码：[WorkshopScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt:1)。

**声明与运行入口**：

- [L58](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt:58)：`fun WorkshopScreen(`
- [L84](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt:84)：`fun delete(target: WorkshopInvalid) {`
- [L202](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt:202)：`private fun WorkshopEmpty() {`
- [L226](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt:226)：`private fun WorkshopAppRow(`
- [L242](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt:242)：`private fun WorkshopRow(`
- [L295](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt:295)：`private fun invalidSubtitle(item: WorkshopInvalid): String =`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt`

### ui/workshop/WorkshopViewerScreen.kt

**职责**：创意详情壳、数据存储生命周期、重载/改名/删除与 WebView 错误覆盖层。

**规模**：399 行；6 个词法声明。源码：[WorkshopViewerScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt:1)。

**声明与运行入口**：

- [L57](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt:57)：`fun WorkshopViewerScreen(`
- [L104](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt:104)：`fun renameCreation() {`
- [L120](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt:120)：`fun deleteCreation() {`
- [L331](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt:331)：`private fun PageErrorOverlay(message: String, onRetry: () -> Unit) {`
- [L366](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt:366)：`private fun UnsupportedWebView() {`
- [L380](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt:380)：`private fun InvalidEntry(onRetry: () -> Unit) {`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`

### ui/workshop/WorkshopWebView.kt

**职责**：配置创意 WebView、安全文件设置、同源 JS 注入、外链处理、主题同步及渲染进程崩溃恢复。

**规模**：333 行；19 个词法声明。源码：[WorkshopWebView.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:1)。

**声明与运行入口**：

- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:64)：`class WorkshopWebViewState {`
- [L67](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:67)：`fun goBack() {`
- [L72](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:72)：`private class WorkshopWebViewRefs {`
- [L78](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:78)：`fun WorkshopWebView(`
- [L187](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:187)：`private fun createWorkshopWebView(`
- [L225](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:225)：`override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =`
- [L228](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:228)：`override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {`
- [L242](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:242)：`override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {`
- [L248](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:248)：`override fun onPageFinished(view: WebView, url: String?) {`
- [L255](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:255)：`override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {`
- [L261](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:261)：`override fun onReceivedHttpError(`
- [L271](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:271)：`override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {`
- [L278](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:278)：`override fun onConsoleMessage(message: ConsoleMessage): Boolean {`
- [L300](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:300)：`private fun destroyWebView(view: WebView) {`
- [L308](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:308)：`private fun buildEnvJson(manifest: WorkshopManifest, colors: DeepDeepColors): String = buildJsonObject {`
- [L318](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:318)：`private fun themeObject(colors: DeepDeepColors): JsonObject = buildJsonObject {`
- [L327](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:327)：`private fun hexColor(color: Color): String = String.format("#%06X", color.toArgb() and 0xFFFFFF)`
- [L329](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:329)：`private fun isWorkshopUrl(url: String?, origin: String): Boolean =`
- [L332](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt:332)：`private fun themeApplyScript(themeJson: String): String =`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`

### ui/workspaces/AgentsDocSheet.kt

**职责**：工作区 AGENTS 文档编辑/保存 sheet。

**规模**：293 行；1 个词法声明。源码：[AgentsDocSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt:1)。

**声明与运行入口**：

- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt:42)：`fun AgentsDocSheet(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/workspaces/ProjectSkillsSheet.kt

**职责**：工作区技能选择，区分全局已启用技能与工作区独立副本。

**规模**：208 行；2 个词法声明。源码：[ProjectSkillsSheet.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt:1)。

**声明与运行入口**：

- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt:42)：`fun ProjectSkillsSheet(`
- [L180](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt:180)：`private fun ProjectSkillsEmpty(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/workspaces/WorkspaceDialogs.kt

**职责**：新建、重命名和删除工作区的共享对话框。

**规模**：146 行；3 个词法声明。源码：[WorkspaceDialogs.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt:1)。

**声明与运行入口**：

- [L28](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt:28)：`enum class WorkspaceNameMode { CREATE, RENAME }`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt:31)：`fun WorkspaceNameDialog(`
- [L110](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt:110)：`fun DeleteWorkspaceDialog(`

**显式内部引用**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### ui/workspaces/WorkspaceSettingsScreen.kt

**职责**：当前工作区信息、AGENTS 文档和技能配置入口。

**规模**：153 行；1 个词法声明。源码：[WorkspaceSettingsScreen.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt:1)。

**声明与运行入口**：

- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt:42)：`fun WorkspaceSettingsScreen(`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/AgentDocStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/SkillRepository.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt`

### voice/SpeechEvents.kt

**职责**：语音事件/错误枚举与系统错误码转换。

**规模**：64 行；9 个词法声明。源码：[SpeechEvents.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:5)：`internal sealed interface SpeechEvent {`
- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:6)：`data class Partial(val text: String) : SpeechEvent`
- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:7)：`data class Final(val text: String) : SpeechEvent`
- [L8](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:8)：`data class Volume(val level: Float) : SpeechEvent`
- [L9](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:9)：`data class Failed(val error: SpeechError, val code: Int) : SpeechEvent`
- [L10](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:10)：`data object Timeout : SpeechEvent`
- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:13)：`internal enum class SpeechError {`
- [L33](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:33)：`internal fun speechEventForError(code: Int): SpeechEvent = when (code) {`
- [L64](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt:64)：`internal fun rmsLevel(rmsDb: Float): Float = ((rmsDb + 2f) / 12f).coerceIn(0.08f, 1f)`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantState.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### voice/SpeechRecognizerController.kt

**职责**：在主线程管理系统 ASR，通过 callbackFlow 发布音量、部分文本、最终结果和错误，取消时销毁识别器。

**规模**：116 行；13 个词法声明。源码：[SpeechRecognizerController.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:18)：`internal class SpeechRecognizerController(context: Context) {`
- [L22](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:22)：`fun isAvailable(): Boolean = runCatching {`
- [L26](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:26)：`fun listen(languageTag: String): Flow<SpeechEvent> = callbackFlow {`
- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:36)：`override fun onReadyForSpeech(params: Bundle?) = Unit`
- [L37](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:37)：`override fun onBeginningOfSpeech() = Unit`
- [L38](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:38)：`override fun onBufferReceived(buffer: ByteArray?) = Unit`
- [L39](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:39)：`override fun onEndOfSpeech() = Unit`
- [L40](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:40)：`override fun onEvent(eventType: Int, params: Bundle?) = Unit`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:42)：`override fun onRmsChanged(rmsdB: Float) {`
- [L49](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:49)：`override fun onError(error: Int) {`
- [L54](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:54)：`override fun onResults(results: Bundle?) {`
- [L68](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:68)：`override fun onPartialResults(partialResults: Bundle?) {`
- [L109](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt:109)：`private fun recognitionIntent(languageTag: String): Intent =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechEvents.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt`

### voice/SpeechText.kt

**职责**：把助手文本清理/分段成适合 TTS 朗读的内容。

**规模**：45 行；3 个词法声明。源码：[SpeechText.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechText.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechText.kt:5)：`internal fun splitSpeechText(text: String, maxLength: Int): List<String> {`
- [L31](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechText.kt:31)：`private fun preferredBreak(text: String, start: Int, hardEnd: Int): Int {`
- [L42](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechText.kt:42)：`private fun boundaryBefore(text: String, index: Int): Int {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### voice/TextSpeaker.kt

**职责**：TTS 初始化、语言选择、分段队列、完成回调和释放，跟踪可用/播放状态。

**规模**：177 行；15 个词法声明。源码：[TextSpeaker.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:1)。

**声明与运行入口**：

- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:13)：`internal class TextSpeaker(context: Context) {`
- [L18](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:18)：`private data class PendingSpeech(val text: String, val language: AppLanguage)`
- [L36](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:36)：`fun speak(text: String, language: AppLanguage) {`
- [L55](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:55)：`fun stop() {`
- [L62](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:62)：`fun release() {`
- [L76](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:76)：`private fun initialize() {`
- [L90](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:90)：`private fun handleInit(status: Int) {`
- [L121](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:121)：`private fun startSpeech(speech: TextToSpeech, text: String) {`
- [L146](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:146)：`private fun applyLanguage(speech: TextToSpeech, language: AppLanguage): Boolean {`
- [L156](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:156)：`private fun finishIfCurrent(utteranceId: String?) {`
- [L166](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:166)：`override fun onStart(utteranceId: String?) = Unit`
- [L168](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:168)：`override fun onDone(utteranceId: String?) = finishIfCurrent(utteranceId)`
- [L171](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:171)：`override fun onError(utteranceId: String?) = finishIfCurrent(utteranceId)`
- [L173](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:173)：`override fun onError(utteranceId: String?, errorCode: Int) = finishIfCurrent(utteranceId)`
- [L175](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/TextSpeaker.kt:175)：`override fun onStop(utteranceId: String?, interrupted: Boolean) = finishIfCurrent(utteranceId)`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`

**同包符号引用（词法候选，不是调用图）**：`app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechRecognizerController.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/SpeechText.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/voice/VoiceLanguage.kt`

### voice/VoiceLanguage.kt

**职责**：将界面语言/系统语言映射成 ASR 语言与 TTS 候选 Locale。

**规模**：18 行；3 个词法声明。源码：[VoiceLanguage.kt](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/VoiceLanguage.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/VoiceLanguage.kt:6)：`internal object VoiceLanguage {`
- [L7](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/VoiceLanguage.kt:7)：`fun recognitionTag(language: AppLanguage): String = when (language) {`
- [L13](/home/whywood/project/DeepDeep/app/src/main/kotlin/io/github/whywood/deepdeep/voice/VoiceLanguage.kt:13)：`fun ttsCandidates(language: AppLanguage): List<Locale> = when (language) {`

**显式内部引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/LanguagePreferences.kt`

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

## :runtime

### runtime/AgentRuntimeHost.kt

**职责**：持有所有打开会话的 Handle，管理发送/停止/重试/提醒/后台轮、模型、压缩、MCP 重载和运行投影；当前没有覆盖全部入口的单会话串行命令队列。

**规模**：726 行；55 个词法声明。源码：[AgentRuntimeHost.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:1)。

**声明与运行入口**：

- [L32](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:32)：`class AgentRuntimeHost(`
- [L45](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:45)：`internal class Handle(`
- [L76](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:76)：`override suspend fun start() = mutex.withLock {`
- [L83](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:83)：`override suspend fun ensureStarted() {`
- [L87](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:87)：`override suspend fun workspaces(): List<RuntimeWorkspace> = buildList {`
- [L92](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:92)：`override suspend fun createWorkspace(name: String): RuntimeWorkspace = mutex.withLock {`
- [L108](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:108)：`override suspend fun renameWorkspace(id: String, name: String) {`
- [L117](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:117)：`override suspend fun deleteWorkspace(id: String) {`
- [L136](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:136)：`override suspend fun allSessions(): List<ConversationSummary> =`
- [L139](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:139)：`override suspend fun workspaceSessionCount(workspaceId: String): Int =`
- [L142](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:142)：`override suspend fun newSession(workspaceId: String): OpenedSession = mutex.withLock {`
- [L149](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:149)：`override suspend fun openSession(filePath: String, cwd: String): OpenedSession = mutex.withLock {`
- [L158](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:158)：`override suspend fun closeSession(sessionId: String) = mutex.withLock {`
- [L162](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:162)：`override suspend fun reloadSession(sessionId: String): String = mutex.withLock {`
- [L173](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:173)：`suspend fun reloadMcp() = mcpMutex.withLock {`
- [L189](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:189)：`private fun logMcpState(handle: Handle, phase: String) {`
- [L200](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:200)：`suspend fun reconnectMcp(server: String) = mcpMutex.withLock {`
- [L211](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:211)：`override suspend fun send(sessionId: String, text: String) {`
- [L216](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:216)：`override suspend fun retryLastTurn(sessionId: String) {`
- [L252](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:252)：`override suspend fun deliverReminder(sessionId: String, text: String, reminder: String?) {`
- [L267](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:267)：`suspend fun deliverBackgroundTurn(`
- [L284](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:284)：`override suspend fun activeRunId(sessionId: String): String? = activeRunState.value[sessionId]?.runId`
- [L286](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:286)：`private suspend fun beginRun(handle: Handle, source: TurnSource = TurnSource.USER): String {`
- [L300](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:300)：`private suspend fun runTurn(`
- [L338](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:338)：`override fun stop(sessionId: String) {`
- [L359](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:359)：`override suspend fun history(sessionId: String): List<AssistantMessage> {`
- [L367](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:367)：`override suspend fun sessionTitle(sessionId: String): String? = mutex.withLock {`
- [L372](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:372)：`override suspend fun sessions(workspaceId: String): List<ConversationSummary> {`
- [L377](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:377)：`override suspend fun archivedSessions(): List<ConversationSummary> =`
- [L380](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:380)：`override suspend fun renameSession(id: String, title: String) {`
- [L385](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:385)：`override suspend fun setSessionPinned(id: String, pinned: Boolean) {`
- [L390](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:390)：`override suspend fun setSessionArchived(id: String, archived: Boolean) {`
- [L397](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:397)：`override suspend fun deleteSession(id: String) {`
- [L409](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:409)：`override suspend fun sessionModel(sessionId: String): SessionModelSnapshot? = mutex.withLock {`
- [L420](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:420)：`override suspend fun setSessionModel(`
- [L434](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:434)：`override suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel) {`
- [L439](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:439)：`override suspend fun currentConfig(): ModelConfig = modelConfigStore.read()`
- [L441](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:441)：`override suspend fun usage(sessionId: String): SessionUsage? {`
- [L460](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:460)：`override suspend fun compact(sessionId: String): CompactionOutcome {`
- [L482](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:482)：`internal suspend fun emitUsage(handle: Handle, runId: String = handle.runId.orEmpty()) {`
- [L487](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:487)：`suspend fun reconfigure() = withContext(Dispatchers.IO) {`
- [L500](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:500)：`private fun homeWorkspace(): RuntimeWorkspace = RuntimeWorkspace(`
- [L507](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:507)：`private suspend fun workspaceFor(workspaceId: String): RuntimeWorkspace {`
- [L512](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:512)：`private suspend fun visibleCwds(): List<String> =`
- [L515](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:515)：`private suspend fun attach(cwd: String, file: String?): Handle {`
- [L528](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:528)：`private suspend fun detach(sessionId: String) {`
- [L539](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:539)：`private fun publishBusy() {`
- [L543](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:543)：`private suspend fun ensureTitle(handle: Handle, text: String) {`
- [L549](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:549)：`private fun emit(handle: Handle, event: RuntimeEvent) {`
- [L560](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:560)：`private fun publishActiveRun(handle: Handle) {`
- [L572](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:572)：`private suspend fun finishRun(handle: Handle, runId: String): RunStatus {`
- [L589](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:589)：`private suspend fun maybeContinueGoal(handle: Handle, status: RunStatus) {`
- [L607](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:607)：`private suspend fun emitTerminal(`
- [L624](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:624)：`private suspend fun onSessionEvent(handle: Handle, event: SessionEvent) {`
- [L676](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt:676)：`private suspend fun onAgentEvent(handle: Handle, runId: String, event: AgentEvent) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/ApiKeyStore.kt

**职责**：ApiKeyRecordStore 抽象及加密秘密存储实现，以 provider 为键持久化 API Key。

**规模**：37 行；12 个词法声明。源码：[ApiKeyStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:5)：`interface ApiKeyRecordStore {`
- [L6](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:6)：`suspend fun readRecord(providerId: String): String?`
- [L8](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:8)：`suspend fun readAllRecords(): Map<String, String>`
- [L10](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:10)：`suspend fun saveRecord(providerId: String, key: String?)`
- [L13](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:13)：`class ApiKeyStore(private val secrets: EncryptedSecretStore) : ApiKeyRecordStore {`
- [L16](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:16)：`fun hasKey(providerId: String): Flow<Boolean> = secrets.has(KEY_PREFIX, providerId)`
- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:18)：`suspend fun read(providerId: String): String? = secrets.read(KEY_PREFIX, providerId)`
- [L20](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:20)：`suspend fun readAll(): Map<String, String> = secrets.readAll(KEY_PREFIX)`
- [L22](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:22)：`suspend fun save(providerId: String, apiKey: String?) {`
- [L26](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:26)：`override suspend fun readRecord(providerId: String): String? = secrets.read(KEY_PREFIX, providerId)`
- [L28](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:28)：`override suspend fun readAllRecords(): Map<String, String> = secrets.readAll(KEY_PREFIX)`
- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt:30)：`override suspend fun saveRecord(providerId: String, key: String?) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/AppTools.kt

**职责**：会话工具装配点。收集提醒、提问、插件 setup、插件工具和额外工具工厂。

**规模**：20 行；2 个词法声明。源码：[AppTools.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt:1)。

**声明与运行入口**：

- [L6](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt:6)：`data class AppToolContext(`
- [L14](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt:14)：`fun appTools(cwd: Path, context: AppToolContext, binding: SessionBinding): List<CodingTool> = buildList {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/AskUserQuestionTool.kt

**职责**：模型提问参数验证、当前会话绑定、挂起等待和答案结构化回传。

**规模**：185 行；6 个词法声明。源码：[AskUserQuestionTool.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:1)。

**声明与运行入口**：

- [L77](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:77)：`fun askUserQuestionTool(questionCenter: QuestionCenter, binding: SessionBinding): CodingTool = CodingTool(`
- [L112](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:112)：`private fun parseQuestions(args: JsonObject): List<UserQuestion> {`
- [L159](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:159)：`private fun answersJson(questions: List<UserQuestion>, answers: List<QuestionAnswer>): JsonObject = buildJsonObject {`
- [L179](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:179)：`private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()`
- [L181](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:181)：`private fun JsonObject.stringOrNull(name: String): String? =`
- [L184](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt:184)：`private fun JsonObject.boolOrNull(name: String): Boolean? =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/BuiltinSkills.kt

**职责**：确保随 App 发布的内置技能在仓库/激活目录存在，管理版本/内容更新约定。

**规模**：74 行；6 个词法声明。源码：[BuiltinSkills.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt:7)：`data class BuiltinSkillFile(`
- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt:12)：`object BuiltinSkills {`
- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt:15)：`fun ensure(repoDir: File, agentDir: File, id: String, content: String): Boolean =`
- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt:18)：`fun ensure(repoDir: File, agentDir: File, id: String, files: List<BuiltinSkillFile>): Boolean {`
- [L39](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt:39)：`private fun safeRelativePath(path: String): String? {`
- [L49](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/BuiltinSkills.kt:49)：`private fun writeIfChanged(target: File, content: String): Boolean {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`

### runtime/DeepDeepOAuthCallbackServerFactory.kt

**职责**：Android loopback OAuth 回调服务器，验证回调路径/参数并提供返回 App 的网页/深链。

**规模**：248 行；16 个词法声明。源码：[DeepDeepOAuthCallbackServerFactory.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:1)。

**声明与运行入口**：

- [L23](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:23)：`class DeepDeepOAuthCallbackServerFactory(`
- [L26](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:26)：`override suspend fun <T> start(options: OAuthCallbackServerOptions<T>): OAuthCallbackServer<T> =`
- [L32](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:32)：`private class CallbackServer<T>(`
- [L64](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:64)：`override suspend fun wait(): T? = completion.await()`
- [L66](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:66)：`override fun cancel() {`
- [L70](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:70)：`override fun close() {`
- [L75](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:75)：`private fun finish(result: Result<T?>) {`
- [L87](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:87)：`private suspend fun acceptLoop() {`
- [L99](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:99)：`private suspend fun handleConnection(client: Socket) {`
- [L157](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:157)：`private fun successPage(providerName: String): String = page(`
- [L163](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:163)：`private fun errorPage(message: String): String = page(`
- [L169](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:169)：`private fun page(title: String, message: String, button: String): String = """`
- [L187](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:187)：`private fun sendPage(socket: Socket, status: Int, html: String) {`
- [L211](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:211)：`private fun escape(value: String): String = value`
- [L217](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:217)：`private fun readRequestLine(input: InputStream): String? {`
- [L232](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt:232)：`private fun parseQuery(query: String): Map<String, String> {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/EncryptedSecretStore.kt

**职责**：Android Keystore AES-GCM 加密与 DataStore secrets 存储，给 Key、OAuth 和 Cookie 提供统一底层。

**规模**：127 行；12 个词法声明。源码：[EncryptedSecretStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:1)。

**声明与运行入口**：

- [L27](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:27)：`class EncryptedSecretStore(context: Context) {`
- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:30)：`fun entries(prefix: String): Flow<Set<String>> = store.data`
- [L36](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:36)：`fun has(prefix: String, providerId: String): Flow<Boolean> = store.data`
- [L42](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:42)：`suspend fun read(prefix: String, providerId: String): String? = withContext(Dispatchers.IO) {`
- [L47](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:47)：`suspend fun readAll(prefix: String): Map<String, String> = withContext(Dispatchers.IO) {`
- [L57](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:57)：`suspend fun save(prefix: String, providerId: String, value: String?) = withContext(Dispatchers.IO) {`
- [L68](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:68)：`private fun Preferences.keysOf(prefix: String): Set<String> =`
- [L73](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:73)：`private fun key(prefix: String, providerId: String) = stringPreferencesKey(prefix + providerId)`
- [L75](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:75)：`private fun encrypt(plainText: String): String {`
- [L86](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:86)：`private fun decrypt(encoded: String): String? = try {`
- [L103](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:103)：`private fun secretKey(): SecretKey = keyStore().getEntry(KEY_ALIAS, null)`
- [L119](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt:119)：`private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudCookieStore.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/GoalContracts.kt

**职责**：目标阶段、持久化目标、展示视图、轮次和控制器/驱动接口。

**规模**：60 行；16 个词法声明。源码：[GoalContracts.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:5)：`enum class GoalPhase {`
- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:12)：`data class Goal(`
- [L24](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:24)：`data class GoalView(`
- [L29](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:29)：`data class GoalRound(`
- [L36](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:36)：`interface GoalController {`
- [L39](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:39)：`suspend fun create(sessionId: String, objective: String, maxRounds: Int? = null): GoalView?`
- [L41](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:41)：`suspend fun edit(sessionId: String, objective: String): GoalView?`
- [L43](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:43)：`suspend fun pause(sessionId: String): GoalView?`
- [L45](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:45)：`suspend fun resume(sessionId: String): GoalView?`
- [L47](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:47)：`suspend fun complete(sessionId: String): GoalView?`
- [L49](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:49)：`suspend fun clear(sessionId: String)`
- [L52](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:52)：`interface GoalDriver {`
- [L53](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:53)：`suspend fun noteTurn(sessionId: String, source: TurnSource)`
- [L55](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:55)：`suspend fun nextRound(sessionId: String, source: TurnSource, succeeded: Boolean): GoalRound?`
- [L57](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:57)：`suspend fun pauseOnStop(sessionId: String)`
- [L59](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt:59)：`suspend fun clearSession(sessionId: String)`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`

### runtime/GoalService.kt

**职责**：目标生命周期与轮次策略，按会话保存目标并决定成功后是否自动续跑；armed 标志只在本次进程有效。

**规模**：244 行；20 个词法声明。源码：[GoalService.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:12)：`class GoalService(`
- [L24](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:24)：`override suspend fun create(sessionId: String, objective: String, maxRounds: Int?): GoalView? = mutex.withLock {`
- [L43](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:43)：`override suspend fun edit(sessionId: String, objective: String): GoalView? =`
- [L46](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:46)：`suspend fun edit(sessionId: String, objective: String?, maxRounds: Int?): GoalView? = mutex.withLock {`
- [L62](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:62)：`override suspend fun pause(sessionId: String): GoalView? = mutex.withLock {`
- [L75](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:75)：`override suspend fun resume(sessionId: String): GoalView? = mutex.withLock {`
- [L92](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:92)：`override suspend fun complete(sessionId: String): GoalView? = mutex.withLock {`
- [L106](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:106)：`override suspend fun clear(sessionId: String) = mutex.withLock {`
- [L111](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:111)：`suspend fun block(sessionId: String, reason: String): GoalView? = mutex.withLock {`
- [L129](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:129)：`override suspend fun noteTurn(sessionId: String, source: TurnSource) {`
- [L133](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:133)：`fun currentTurnSource(sessionId: String): TurnSource? = turnSources[sessionId]`
- [L135](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:135)：`override suspend fun nextRound(sessionId: String, source: TurnSource, succeeded: Boolean): GoalRound? =`
- [L181](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:181)：`override suspend fun pauseOnStop(sessionId: String) {`
- [L197](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:197)：`override suspend fun clearSession(sessionId: String) {`
- [L205](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:205)：`private fun persistGoal(sessionId: String, goal: Goal): GoalView {`
- [L211](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:211)：`private fun persist() {`
- [L216](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:216)：`private fun views(): Map<String, GoalView> = persisted.entries.associate { (sessionId, goal) ->`
- [L227](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:227)：`private fun normalizeMaxRounds(maxRounds: Int?): Int = when {`
- [L233](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:233)：`private fun newGoalId(): String = UUID.randomUUID().toString().replace("-", "").take(12)`
- [L235](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt:235)：`private fun roundPrompt(objective: String, round: Int, maxRounds: Int): String = buildString {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/GoalStore.kt

**职责**：目标 JSON 读写/校验与原子文件替换；损坏内容会抛异常而不是静默变空。

**规模**：121 行；14 个词法声明。源码：[GoalStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:1)。

**声明与运行入口**：

- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:15)：`class CorruptGoalDataException(message: String, cause: Throwable? = null) : Exception(message, cause)`
- [L17](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:17)：`class GoalStore(`
- [L23](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:23)：`fun read(): Map<String, Goal> = synchronized(lock) { load() }`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:25)：`fun replace(goals: Map<String, Goal>) = synchronized(lock) { persist(goals) }`
- [L27](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:27)：`fun remove(sessionId: String): Boolean = synchronized(lock) {`
- [L34](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:34)：`private fun load(): Map<String, Goal> {`
- [L51](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:51)：`private fun persist(goals: Map<String, Goal>) {`
- [L77](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:77)：`private fun Goal.toJson(): JsonObject = buildJsonObject {`
- [L93](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:93)：`private fun goalFromJson(json: JsonObject): Goal = Goal(`
- [L105](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:105)：`private fun JsonObject.stringOrNull(name: String): String? =`
- [L108](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:108)：`private fun JsonObject.requiredString(name: String): String =`
- [L111](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:111)：`private fun JsonObject.requiredInt(name: String): Int =`
- [L114](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:114)：`private fun JsonObject.requiredLong(name: String): Long =`
- [L117](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt:117)：`private fun JsonObject.requiredPhase(name: String): GoalPhase {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/GoalTools.kt

**职责**：create_goal/update_goal 的模型工具，绑定当前会话，执行创建/修改/暂停/完成/阻塞等操作。

**规模**：315 行；11 个词法声明。源码：[GoalTools.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:1)。

**声明与运行入口**：

- [L81](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:81)：`fun goalTools(binding: SessionBinding, service: GoalService): List<CodingTool> = listOf(`
- [L87](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:87)：`private fun getGoalTool(binding: SessionBinding, service: GoalService): CodingTool = CodingTool(`
- [L107](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:107)：`private fun createGoalTool(binding: SessionBinding, service: GoalService): CodingTool = CodingTool(`
- [L139](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:139)：`private fun updateGoalTool(binding: SessionBinding, service: GoalService): CodingTool = CodingTool(`
- [L267](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:267)：`private fun goalViewJson(view: GoalView?): JsonObject = buildJsonObject {`
- [L290](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:290)：`private suspend fun goalToolResult(block: suspend () -> AgentToolResult): AgentToolResult =`
- [L301](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:301)：`private fun TurnSource?.isUserInitiated(): Boolean =`
- [L304](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:304)：`private fun JsonObject.stringOrNull(name: String): String? =`
- [L307](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:307)：`private fun JsonObject.intOrNull(name: String): Int? =`
- [L310](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:310)：`private fun JsonObject.requiredString(name: String): String =`
- [L314](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt:314)：`private fun JsonObject.requiredInt(name: String): Int =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/HistoryMapper.kt

**职责**：把 SDK 的模型消息/工具结果合成聊天轮、过程步骤、失败状态和本地展示内容。

**规模**：204 行；8 个词法声明。源码：[HistoryMapper.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:18)：`object HistoryMapper {`
- [L19](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:19)：`fun mapHistory(messages: List<AgentMessage>): List<AssistantMessage> {`
- [L26](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:26)：`fun flush() {`
- [L166](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:166)：`private data class ReminderMessage(`
- [L171](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:171)：`private fun parseReminder(text: String): ReminderMessage? {`
- [L190](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:190)：`private fun turnStatus(base: MessageStatus, reason: StopReason): MessageStatus = when {`
- [L196](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:196)：`private fun dev.pi.ai.MessageContent.asText(): String = when (this) {`
- [L201](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt:201)：`private fun JsonObject.toStringMap(): Map<String, String> = entries.associate { (key, value) ->`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`

### runtime/InstallationIdStore.kt

**职责**：每次安装生成并持久化稳定 UUID，供需要设备身份的 Provider OAuth 使用。

**规模**：34 行；3 个词法声明。源码：[InstallationIdStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt:1)。

**声明与运行入口**：

- [L8](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt:8)：`class InstallationIdStore(`
- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt:15)：`suspend fun get(): String = mutex.withLock {`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt:25)：`private fun readExisting(): String? {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`

### runtime/JobContracts.kt

**职责**：后台命令执行器、任务状态、快照、启动和增量读取结果以及控制器接口。

**规模**：53 行；12 个词法声明。源码：[JobContracts.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:5)：`enum class JobStatus {`
- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:12)：`data class JobSnapshot(`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:25)：`data class JobStart(`
- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:30)：`data class JobRead(`
- [L37](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:37)：`interface JobController {`
- [L40](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:40)：`fun start(sessionId: String, command: String, label: String): JobStart`
- [L42](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:42)：`suspend fun read(jobId: String): JobRead`
- [L44](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:44)：`suspend fun kill(jobId: String, reason: String?): Boolean`
- [L46](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:46)：`fun clearSettled()`
- [L48](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:48)：`fun clearSession(sessionId: String)`
- [L51](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:51)：`fun interface BackgroundCommandRunner {`
- [L52](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt:52)：`suspend fun run(command: String, onOutput: (String) -> Unit): Int`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/JobRegistry.kt

**职责**：运行后台命令、管理每会话 10 个并发上限、输出环形缓冲/游标/等待/停止及完成唤醒；记录仅在内存中。

**规模**：400 行；37 个词法声明。源码：[JobRegistry.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:1)。

**声明与运行入口**：

- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:15)：`class JobRegistry(`
- [L29](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:29)：`override fun start(sessionId: String, command: String, label: String): JobStart =`
- [L32](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:32)：`fun start(sessionId: String, command: String, label: String, cwd: String?): JobStart {`
- [L61](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:61)：`fun readIncremental(jobId: String): JobRead = find(jobId).readIncremental()`
- [L63](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:63)：`fun remove(jobId: String) {`
- [L72](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:72)：`suspend fun awaitRead(jobId: String, wait: Boolean, timeoutMs: Long = DEFAULT_WAIT_MS): JobRead {`
- [L93](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:93)：`override suspend fun read(jobId: String): JobRead = find(jobId).readTail()`
- [L95](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:95)：`override suspend fun kill(jobId: String, reason: String?): Boolean {`
- [L108](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:108)：`override fun clearSettled() {`
- [L115](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:115)：`override fun clearSession(sessionId: String) {`
- [L132](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:132)：`private fun runnerFor(cwd: String?): BackgroundCommandRunner =`
- [L137](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:137)：`private fun find(jobId: String): Record =`
- [L141](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:141)：`private fun newIdLocked(): String {`
- [L148](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:148)：`private suspend fun execute(record: Record) {`
- [L163](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:163)：`private suspend fun settle(record: Record, exitCode: Int?, detail: String?, cancelled: Boolean) {`
- [L203](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:203)：`private fun publish() {`
- [L211](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:211)：`private class Record(`
- [L235](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:235)：`fun append(chunk: String) {`
- [L240](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:240)：`fun isRunning(): Boolean = synchronized(lock) { status == JobStatus.RUNNING }`
- [L242](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:242)：`fun readIncremental(): JobRead = synchronized(lock) { readIncrementalLocked() }`
- [L244](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:244)：`fun readTail(): JobRead = synchronized(lock) {`
- [L249](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:249)：`fun beginWait(): JobRead? = synchronized(lock) {`
- [L260](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:260)：`fun endWait() {`
- [L266](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:266)：`fun snapshot(): JobSnapshot = synchronized(lock) {`
- [L281](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:281)：`private fun readIncrementalLocked(): JobRead {`
- [L288](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:288)：`private class OutputRing {`
- [L298](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:298)：`fun append(chunk: String) {`
- [L325](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:325)：`fun seal(limit: Int) {`
- [L337](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:337)：`fun drain(offset: Long): Triple<String, Long, Boolean> = synchronized(lock) {`
- [L345](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:345)：`fun snapshot(): Pair<String, Boolean> = synchronized(lock) {`
- [L349](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:349)：`fun totalBytes(): Long = synchronized(lock) { endOffset }`
- [L351](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:351)：`private fun write(bytes: ByteArray, offset: Int, count: Int) {`
- [L359](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:359)：`private fun readAt(offset: Long, count: Int): String {`
- [L369](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:369)：`private fun decode(bytes: ByteArray): String {`
- [L385](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:385)：`internal fun jobStatusText(status: JobStatus, exitCode: Int?): String = when (status) {`
- [L392](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:392)：`private fun finishPrompt(snapshot: JobSnapshot): String =`
- [L397](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt:397)：`private fun commandPreview(command: String): String {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/JobTools.kt

**职责**：job_start/job_output/job_kill 的参数规则、会话绑定和增量输出契约。

**规模**：199 行；11 个词法声明。源码：[JobTools.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:1)。

**声明与运行入口**：

- [L69](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:69)：`fun jobTools(binding: SessionBinding, registry: JobRegistry): List<CodingTool> = listOf(`
- [L76](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:76)：`private fun jobStartTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(`
- [L106](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:106)：`private fun jobOutputTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(`
- [L137](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:137)：`private fun jobListTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(`
- [L156](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:156)：`private fun jobKillTool(binding: SessionBinding, registry: JobRegistry): CodingTool = CodingTool(`
- [L175](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:175)：`private fun activeJobSession(binding: SessionBinding): BoundSession =`
- [L178](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:178)：`private suspend fun jobToolResult(block: suspend () -> AgentToolResult): AgentToolResult =`
- [L189](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:189)：`private fun JsonObject.jobStringOrNull(name: String): String? =`
- [L192](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:192)：`private fun JsonObject.requiredJobString(name: String): String =`
- [L195](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:195)：`private fun JsonObject.jobBoolOrNull(name: String): Boolean? =`
- [L198](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt:198)：`private fun JsonObject.jobLongOrNull(name: String): Long? =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/McpConfigStore.kt

**职责**：读取/校验/保留未知字段并原子保存 agent/mcp.json，供 MCP 列表和 Host 热更新使用。

**规模**：207 行；16 个词法声明。源码：[McpConfigStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:1)。

**声明与运行入口**：

- [L21](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:21)：`data class McpConfigSnapshot(`
- [L27](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:27)：`class McpConfigStore(`
- [L33](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:33)：`suspend fun load(): McpConfigSnapshot = mutex.withLock { loadLocked() }`
- [L35](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:35)：`suspend fun loadServers(): Map<String, McpServerConfig> = load().servers`
- [L37](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:37)：`suspend fun upsert(name: String, config: McpServerConfig) = mutex.withLock {`
- [L44](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:44)：`suspend fun importServers(text: String): List<String> = mutex.withLock {`
- [L57](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:57)：`suspend fun updateFromJson(originalName: String, text: String): String = mutex.withLock {`
- [L72](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:72)：`suspend fun entryJson(name: String): String? = mutex.withLock {`
- [L79](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:79)：`suspend fun remove(name: String) = mutex.withLock {`
- [L85](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:85)：`suspend fun setEnabled(name: String, enabled: Boolean) = mutex.withLock {`
- [L93](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:93)：`private suspend fun loadLocked(): McpConfigSnapshot = withContext(Dispatchers.IO) {`
- [L113](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:113)：`private data class RootRead(val root: JsonObject, val error: String?)`
- [L115](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:115)：`private fun readRootLocked(): RootRead {`
- [L128](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:128)：`private suspend fun writeLocked(entries: Map<String, JsonObject>) = withContext(Dispatchers.IO) {`
- [L147](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:147)：`private fun validateEntry(name: String, entry: JsonObject) {`
- [L161](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt:161)：`fun McpServerConfig.toConfigJsonObject(): JsonObject = buildJsonObject {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/McpJson.kt

**职责**：MCP JSON 配置结构与字段转换辅助逻辑。

**规模**：113 行；8 个词法声明。源码：[McpJson.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:1)。

**声明与运行入口**：

- [L14](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:14)：`data class McpParsedEntry(`
- [L19](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:19)：`fun parseMcpEntries(text: String): List<McpParsedEntry> {`
- [L32](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:32)：`fun formatMcpEntry(name: String, entry: JsonObject): String {`
- [L46](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:46)：`private fun extractServers(text: String): Map<String, JsonObject> {`
- [L58](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:58)：`private fun extractFragment(text: String): Pair<String, JsonObject>? {`
- [L70](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:70)：`private fun balancedObject(text: String, start: Int): String? {`
- [L98](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:98)：`private fun JsonObject.objects(): Map<String, JsonObject> {`
- [L106](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt:106)：`private fun normalizeType(value: JsonObject): JsonObject {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`

### runtime/McpServices.kt

**职责**：独立 MCP 连接探测和 OAuth 登录/登出，给 UI 提供状态，不直接替代 SDK 会话内 MCP 生命周期。

**规模**：150 行；10 个词法声明。源码：[McpServices.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:1)。

**声明与运行入口**：

- [L22](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:22)：`enum class McpProbeState {`
- [L29](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:29)：`data class McpProbeResult(`
- [L36](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:36)：`class McpServerProbe(`
- [L40](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:40)：`suspend fun probe(`
- [L86](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:86)：`class McpOAuthService(`
- [L90](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:90)：`suspend fun hasToken(server: String, config: McpServerConfig.Http): Boolean =`
- [L95](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:95)：`suspend fun signIn(`
- [L118](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:118)：`suspend fun signOut(server: String, config: McpServerConfig.Http) {`
- [L123](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:123)：`internal fun McpServerConfig.Http.oauthOptions(serverName: String): McpOAuthOptions = McpOAuthOptions(`
- [L135](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt:135)：`internal fun McpServerConfig.Http.httpAuthProvider(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/ModelConfigStore.kt

**职责**：保存新会话默认 provider/model/thinking；现有会话模型由各自 JSONL 恢复。

**规模**：51 行；4 个词法声明。源码：[ModelConfigStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt:1)。

**声明与运行入口**：

- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt:15)：`data class ModelConfig(`
- [L21](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt:21)：`class ModelConfigStore(context: Context) : ModelSettings {`
- [L42](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt:42)：`override suspend fun read(): ModelConfig = config.first()`
- [L44](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt:44)：`override suspend fun save(config: ModelConfig) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/Notices.kt

**职责**：目标轮、后台任务和压缩等通知模型与补充运行事件，连接运行系统与聊天呈现。

**规模**：34 行；9 个词法声明。源码：[Notices.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:3)：`enum class TurnSource {`
- [L11](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:11)：`sealed interface TurnNotice {`
- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:12)：`data class Compacted(val tokensBefore: Long, val tokensAfter: Long?) : TurnNotice`
- [L14](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:14)：`data class GoalRound(val round: Int, val maxRounds: Int, val objective: String) : TurnNotice`
- [L16](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:16)：`data class GoalFinished(val completed: Boolean) : TurnNotice`
- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:18)：`data class JobFinished(val label: String, val failed: Boolean) : TurnNotice`
- [L21](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:21)：`data class NoticeEvent(`
- [L26](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:26)：`data class BackgroundTurnStarted(`
- [L31](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt:31)：`data class UsageChanged(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`

### runtime/OAuthCredentialStore.kt

**职责**：OAuthRecordStore 抽象与加密原始 OAuth JSON 读写，保留扩展 token 字段。

**规模**：49 行；12 个词法声明。源码：[OAuthCredentialStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:1)。

**声明与运行入口**：

- [L7](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:7)：`interface OAuthRecordStore {`
- [L8](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:8)：`suspend fun readRawRecords(): Map<String, JsonElement>`
- [L10](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:10)：`suspend fun saveRawRecord(providerId: String, credential: JsonElement)`
- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:12)：`suspend fun deleteRecord(providerId: String)`
- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:15)：`class OAuthCredentialStore(private val secrets: EncryptedSecretStore) : OAuthRecordStore {`
- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:18)：`suspend fun readRaw(): Map<String, JsonElement> = readRawRecords()`
- [L20](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:20)：`suspend fun readRaw(providerId: String): JsonElement? =`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:25)：`suspend fun saveRaw(providerId: String, credential: JsonElement) {`
- [L29](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:29)：`suspend fun delete(providerId: String) {`
- [L33](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:33)：`override suspend fun readRawRecords(): Map<String, JsonElement> =`
- [L38](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:38)：`override suspend fun saveRawRecord(providerId: String, credential: JsonElement) {`
- [L42](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt:42)：`override suspend fun deleteRecord(providerId: String) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/Personalization.kt

**职责**：runtime 所需的个性化数据、环境/长期指令与动态提示词 section 构造。

**规模**：110 行；9 个词法声明。源码：[Personalization.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:5)：`enum class InteractionStyle {`
- [L11](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:11)：`data class Personalization(`
- [L17](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:17)：`internal fun buildPersonalizationSections(`
- [L56](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:56)：`internal fun buildSessionSystemPromptSections(`
- [L76](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:76)：`internal fun sharedStorageSection(): Map<String, String> = mapOf(`
- [L87](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:87)：`private fun interactionLabel(style: InteractionStyle): String = when (style) {`
- [L93](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:93)：`private fun interactionInstructions(style: InteractionStyle): String = when (style) {`
- [L104](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:104)：`internal fun wrapPromptSection(name: String, content: String): String =`
- [L107](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt:107)：`internal fun escapePromptXml(value: String): String = value`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/PersonalizationStore.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`

### runtime/PiSessionEngine.kt

**职责**：将工作目录、凭据、Provider、工具、提示词 hooks 和 MCP 配置装配成 SDK AgentSession；模型循环由 SDK 负责。

**规模**：187 行；14 个词法声明。源码：[PiSessionEngine.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:1)。

**声明与运行入口**：

- [L26](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:26)：`class PiSessionEngine(`
- [L44](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:44)：`suspend fun createSession(`
- [L115](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:115)：`fun availableTools(): List<CodingTool> = buildTools(shellExecutor, SessionBinding())`
- [L117](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:117)：`private fun buildTools(activeShell: ShellExecutor?, binding: SessionBinding): List<CodingTool> = buildList {`
- [L131](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:131)：`suspend fun loadMcpServers(): Map<String, McpServerConfig> {`
- [L137](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:137)：`suspend fun listSessions(): List<ConversationSummary> = SessionManager.list(paths, fs).map { info ->`
- [L141](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:141)：`fun history(session: AgentSession): List<AssistantMessage> = HistoryMapper.mapHistory(session.messages)`
- [L143](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:143)：`suspend fun deleteSessionFile(filePath: String) {`
- [L147](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:147)：`fun titleFor(text: String): String {`
- [L153](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:153)：`private fun SessionInfo.toSummary(): ConversationSummary = ConversationSummary(`
- [L160](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:160)：`private fun parseTimestamp(value: String): Long = runCatching {`
- [L165](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:165)：`fun ThinkingLevel.runtimeValue(): String = when (this) {`
- [L173](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:173)：`internal fun thinkingLevelOf(value: String?): ThinkingLevel = when (value?.trim()?.lowercase()) {`
- [L181](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt:181)：`fun ThinkingLevel.displayName(): String = when (this) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpServices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`

### runtime/PluginContracts.kt

**职责**：runtime 看见的插件控制接口与 setup 结果，不依赖具体 Android 官方插件类。

**规模**：15 行；3 个词法声明。源码：[PluginContracts.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginContracts.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginContracts.kt:3)：`interface PluginController {`
- [L4](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginContracts.kt:4)：`suspend fun setup(pluginId: String): PluginSetupResult`
- [L7](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginContracts.kt:7)：`data class PluginSetupResult(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginManager.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`

### runtime/PluginTools.kt

**职责**：plugin_setup 工具，把模型安装/连接请求导向统一 PluginController。

**规模**：71 行；2 个词法声明。源码：[PluginTools.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginTools.kt:1)。

**声明与运行入口**：

- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginTools.kt:30)：`fun pluginSetupTool(controller: PluginController?): CodingTool? {`
- [L63](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginTools.kt:63)：`private fun setupResultJson(result: PluginSetupResult): JsonObject = buildJsonObject {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PluginContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/PresentTool.kt

**职责**：present 参数与文件/图片/HTML/创意交付契约，验证目标并产生可由聊天卡片恢复的结果。

**规模**：307 行；13 个词法声明。源码：[PresentTool.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:1)。

**声明与运行入口**：

- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:25)：`data class PresentedCreative(`
- [L32](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:32)：`fun workshopCreativeLookup(store: WorkshopStore): suspend (String) -> PresentedCreative? = { id ->`
- [L94](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:94)：`fun presentTool(`
- [L130](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:130)：`private suspend fun parsePresentedItems(`
- [L152](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:152)：`private suspend fun normalizeItem(`
- [L174](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:174)：`private enum class PresentKind(val json: String) {`
- [L180](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:180)：`private fun fileItem(`
- [L214](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:214)：`private fun normalizeLegacyFile(`
- [L241](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:241)：`private suspend fun creativeItem(`
- [L264](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:264)：`private fun resolvePresentedFile(rawPath: String, cwd: File): File {`
- [L272](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:272)：`private fun presentedItemsJson(items: List<JsonObject>): JsonObject = buildJsonObject {`
- [L278](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:278)：`private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.US)) {`
- [L306](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt:306)：`private fun JsonObject.argumentString(name: String): String? =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/ProviderCatalog.kt

**职责**：从 SDK 内置目录获取 Provider/模型/思考档位，解析默认/恢复配置并构建模型引用。

**规模**：89 行；11 个词法声明。源码：[ProviderCatalog.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:1)。

**声明与运行入口**：

- [L10](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:10)：`class ProviderCatalog(`
- [L28](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:28)：`override fun summary(providerId: String): ProviderSummary? = summaries.firstOrNull { it.id == providerId }`
- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:30)：`override fun models(providerId: String): List<ModelOption> =`
- [L33](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:33)：`fun defaultModel(providerId: String): ModelOption? = models(providerId).firstOrNull()`
- [L35](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:35)：`fun model(providerId: String, modelId: String): ModelOption? =`
- [L38](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:38)：`override fun sdkModel(providerId: String, modelId: String): Model? =`
- [L41](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:41)：`override fun resolveConfig(config: ModelConfig): ModelConfig {`
- [L51](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:51)：`override fun modelReference(providerId: String, modelId: String): String = "$providerId/$modelId"`
- [L53](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:53)：`fun modelsForConfiguredProviders(): List<ModelOption> =`
- [L56](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:56)：`private fun Model.toOption(providerId: String): ModelOption = ModelOption(`
- [L63](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt:63)：`private fun thinkingLevels(model: Model): List<ThinkingLevel> {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/ProviderOAuthService.kt

**职责**：调用 SDK 内置 OAuth flow、传安装身份、保存登录凭据和退出登录。

**规模**：53 行；6 个词法声明。源码：[ProviderOAuthService.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt:1)。

**声明与运行入口**：

- [L11](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt:11)：`class ProviderOAuthService(`
- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt:18)：`fun supports(providerId: String): Boolean = flows.containsKey(providerId)`
- [L20](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt:20)：`fun flow(providerId: String): OAuthAuth? = flows[providerId]`
- [L22](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt:22)：`fun loginLabel(providerId: String): String? =`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt:25)：`suspend fun login(`
- [L46](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt:46)：`suspend fun logout(providerId: String) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`

### runtime/QuestionCenter.kt

**职责**：按会话/工具调用登记待答问题，用 CompletableDeferred 等待，供界面回答/取消；当前等待状态不持久化。

**规模**：94 行；12 个词法声明。源码：[QuestionCenter.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:1)。

**声明与运行入口**：

- [L10](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:10)：`data class QuestionOption(`
- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:15)：`data class UserQuestion(`
- [L23](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:23)：`data class QuestionAnswer(`
- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:30)：`data class PendingQuestion(`
- [L37](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:37)：`sealed interface QuestionOutcome {`
- [L38](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:38)：`data class Answered(val answers: List<QuestionAnswer>) : QuestionOutcome`
- [L40](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:40)：`data object Cancelled : QuestionOutcome`
- [L42](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:42)：`data object Unavailable : QuestionOutcome`
- [L45](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:45)：`class QuestionCenter(private val isForeground: () -> Boolean) {`
- [L52](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:52)：`suspend fun ask(`
- [L83](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:83)：`fun answer(requestId: String, answers: List<QuestionAnswer>) {`
- [L89](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt:89)：`fun cancel(requestId: String) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/RuntimeTypes.kt

**职责**：AgentRuntime/ProviderDirectory/ModelSettings 边界与会话、消息、工具、活动、运行事件的主要数据模型。

**规模**：319 行；79 个词法声明。源码：[RuntimeTypes.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:5)：`enum class ThinkingLevel {`
- [L13](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:13)：`data class ModelOption(`
- [L20](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:20)：`data class ProviderSummary(`
- [L29](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:29)：`data class ConversationSummary(`
- [L39](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:39)：`data class RuntimeWorkspace(`
- [L47](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:47)：`data class OpenedSession(`
- [L53](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:53)：`data class SessionRuntimeEvent(`
- [L58](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:58)：`data class ActiveRunSnapshot(`
- [L63](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:63)：`data class SessionModelSnapshot(`
- [L69](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:69)：`enum class AssistantRole {`
- [L74](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:74)：`enum class MessageStatus {`
- [L81](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:81)：`sealed interface AssistantStep {`
- [L85](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:85)：`data class Reasoning(`
- [L91](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:91)：`data class Tool(`
- [L98](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:98)：`data class Text(`
- [L106](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:106)：`enum class ToolActivityStatus {`
- [L112](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:112)：`data class ToolTrace(`
- [L125](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:125)：`data class AssistantMessage(`
- [L141](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:141)：`enum class RunStatus {`
- [L147](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:147)：`sealed interface RuntimeActivity {`
- [L148](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:148)：`data object Thinking : RuntimeActivity`
- [L150](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:150)：`data class Retrying(val attempt: Int) : RuntimeActivity`
- [L152](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:152)：`data object Compacting : RuntimeActivity`
- [L154](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:154)：`data object CompactionFailed : RuntimeActivity`
- [L156](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:156)：`data class RunningTool(val name: String) : RuntimeActivity`
- [L159](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:159)：`sealed interface RuntimeEvent {`
- [L162](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:162)：`data class RunStarted(`
- [L167](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:167)：`data class ActivityChanged(`
- [L172](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:172)：`data class TextDelta(`
- [L177](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:177)：`data class ReasoningDelta(`
- [L182](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:182)：`data class ToolStarted(`
- [L190](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:190)：`data class ToolFinished(`
- [L199](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:199)：`data class RunFinished(`
- [L206](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:206)：`data class RunFailed(`
- [L211](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:211)：`data class TurnInjected(`
- [L217](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:217)：`data class SessionRemoved(`
- [L221](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:221)：`data class SessionArchived(`
- [L226](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:226)：`data class SessionListChanged(`
- [L231](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:231)：`interface ProviderDirectory {`
- [L234](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:234)：`fun summary(providerId: String): ProviderSummary?`
- [L236](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:236)：`fun models(providerId: String): List<ModelOption>`
- [L238](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:238)：`fun resolveConfig(config: ModelConfig): ModelConfig`
- [L240](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:240)：`fun sdkModel(providerId: String, modelId: String): dev.pi.ai.Model?`
- [L242](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:242)：`fun modelReference(providerId: String, modelId: String): String`
- [L245](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:245)：`interface ModelSettings {`
- [L248](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:248)：`suspend fun read(): ModelConfig`
- [L250](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:250)：`suspend fun save(config: ModelConfig)`
- [L253](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:253)：`interface AgentRuntime {`
- [L258](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:258)：`suspend fun start()`
- [L260](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:260)：`suspend fun ensureStarted()`
- [L262](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:262)：`suspend fun workspaces(): List<RuntimeWorkspace>`
- [L264](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:264)：`suspend fun createWorkspace(name: String): RuntimeWorkspace`
- [L266](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:266)：`suspend fun renameWorkspace(id: String, name: String)`
- [L268](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:268)：`suspend fun deleteWorkspace(id: String)`
- [L270](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:270)：`suspend fun allSessions(): List<ConversationSummary>`
- [L272](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:272)：`suspend fun workspaceSessionCount(workspaceId: String): Int`
- [L274](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:274)：`suspend fun newSession(workspaceId: String): OpenedSession`
- [L276](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:276)：`suspend fun openSession(filePath: String, cwd: String): OpenedSession`
- [L278](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:278)：`suspend fun closeSession(sessionId: String)`
- [L280](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:280)：`suspend fun reloadSession(sessionId: String): String`
- [L282](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:282)：`suspend fun send(sessionId: String, text: String)`
- [L284](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:284)：`suspend fun retryLastTurn(sessionId: String)`
- [L286](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:286)：`suspend fun deliverReminder(sessionId: String, text: String, reminder: String?)`
- [L288](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:288)：`suspend fun activeRunId(sessionId: String): String?`
- [L290](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:290)：`fun stop(sessionId: String)`
- [L292](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:292)：`suspend fun history(sessionId: String): List<AssistantMessage>`
- [L294](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:294)：`suspend fun sessionTitle(sessionId: String): String?`
- [L296](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:296)：`suspend fun sessions(workspaceId: String): List<ConversationSummary>`
- [L298](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:298)：`suspend fun archivedSessions(): List<ConversationSummary>`
- [L300](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:300)：`suspend fun renameSession(id: String, title: String)`
- [L302](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:302)：`suspend fun setSessionPinned(id: String, pinned: Boolean)`
- [L304](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:304)：`suspend fun setSessionArchived(id: String, archived: Boolean)`
- [L306](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:306)：`suspend fun deleteSession(id: String)`
- [L308](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:308)：`suspend fun sessionModel(sessionId: String): SessionModelSnapshot?`
- [L310](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:310)：`suspend fun setSessionModel(sessionId: String, providerId: String, modelId: String, thinkingLevel: ThinkingLevel)`
- [L312](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:312)：`suspend fun setSessionThinkingLevel(sessionId: String, thinkingLevel: ThinkingLevel)`
- [L314](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:314)：`suspend fun currentConfig(): ModelConfig`
- [L316](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:316)：`suspend fun usage(sessionId: String): SessionUsage? = null`
- [L318](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt:318)：`suspend fun compact(sessionId: String): CompactionOutcome = CompactionOutcome.Unavailable`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/NotificationCenter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiStrings.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ReasoningCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SessionSearchHit.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/QuestionRecord.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Notices.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/ScheduleCron.kt

**职责**：五段 cron 解析、字段校验与未来五年的候选时间查找；用于提醒的下一次投递时间。

**规模**：236 行；23 个词法声明。源码：[ScheduleCron.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:1)。

**声明与运行入口**：

- [L8](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:8)：`class InvalidCronExpressionException(message: String) : Exception(message)`
- [L10](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:10)：`internal class CronExpression private constructor(`
- [L19](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:19)：`internal fun minuteValues(): Set<Int> = (0..59).filter { minutes[it] }.toSet()`
- [L21](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:21)：`internal fun hourValues(): Set<Int> = (0..23).filter { hours[it] }.toSet()`
- [L23](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:23)：`internal fun dayOfMonthValues(): Set<Int> = (1..31).filter { daysOfMonth[it] }.toSet()`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:25)：`internal fun monthValues(): Set<Int> = (1..12).filter { months[it] }.toSet()`
- [L27](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:27)：`internal fun weekdayValues(): Set<DayOfWeek> =`
- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:30)：`fun nextRun(now: ZonedDateTime): ZonedDateTime? {`
- [L49](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:49)：`private fun matchesDate(date: LocalDate): Boolean {`
- [L66](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:66)：`fun parse(expression: String): CronExpression {`
- [L90](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:90)：`private fun parseField(`
- [L138](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:138)：`private fun parseValue(`
- [L160](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:160)：`sealed interface CronSummary {`
- [L161](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:161)：`data object EveryMinute : CronSummary`
- [L163](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:163)：`data class EveryMinutes(val every: Int) : CronSummary`
- [L165](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:165)：`data class EveryHours(val every: Int, val minute: Int) : CronSummary`
- [L167](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:167)：`data class Daily(val time: LocalTime) : CronSummary`
- [L169](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:169)：`data class Weekly(val days: Set<DayOfWeek>, val time: LocalTime) : CronSummary`
- [L171](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:171)：`data class Monthly(val day: Int, val time: LocalTime) : CronSummary`
- [L173](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:173)：`data class Yearly(val month: Int, val day: Int, val time: LocalTime) : CronSummary`
- [L175](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:175)：`data class Custom(val expression: String) : CronSummary`
- [L178](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:178)：`fun summarizeCron(expression: String): CronSummary? {`
- [L227](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt:227)：`private fun stepOf(values: Set<Int>, full: Int): Int? {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`

### runtime/ScheduleModels.kt

**职责**：提醒/trigger/错误的数据类型、JSON 编解码及 once/daily/weekly/cron 时间规则。

**规模**：274 行；25 个词法声明。源码：[ScheduleModels.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:1)。

**声明与运行入口**：

- [L17](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:17)：`class CorruptScheduleDataException(message: String, cause: Throwable? = null) : Exception(message, cause)`
- [L19](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:19)：`enum class ScheduleError {`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:25)：`sealed interface ScheduleTrigger {`
- [L26](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:26)：`data class Once(val date: LocalDate, val time: LocalTime) : ScheduleTrigger`
- [L28](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:28)：`data class Daily(val time: LocalTime) : ScheduleTrigger`
- [L30](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:30)：`data class Weekly(val days: Set<DayOfWeek>, val time: LocalTime) : ScheduleTrigger`
- [L32](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:32)：`data class Cron(val expression: String) : ScheduleTrigger`
- [L35](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:35)：`data class Schedule(`
- [L50](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:50)：`fun nextRun(now: ZonedDateTime): ZonedDateTime? = trigger.nextRun(now)`
- [L53](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:53)：`fun ScheduleTrigger.nextRun(now: ZonedDateTime): ZonedDateTime? = when (this) {`
- [L83](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:83)：`fun ScheduleTrigger.validationError(now: ZonedDateTime): String? = when (this) {`
- [L97](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:97)：`private fun ScheduleTrigger.Cron.nextRunForCron(now: ZonedDateTime): ZonedDateTime? =`
- [L104](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:104)：`fun ScheduleTrigger.toJsonObject(): JsonObject = buildJsonObject {`
- [L129](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:129)：`fun Schedule.toJsonObject(): JsonObject = buildJsonObject {`
- [L157](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:157)：`fun scheduleTriggerFromJsonObject(json: JsonObject): ScheduleTrigger {`
- [L184](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:184)：`fun scheduleFromJsonObject(json: JsonObject): Schedule = Schedule(`
- [L218](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:218)：`private fun weekdayFromJsonElement(element: kotlinx.serialization.json.JsonElement): DayOfWeek {`
- [L230](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:230)：`private fun JsonObject.stringOrCorrupt(name: String): String =`
- [L234](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:234)：`private fun JsonObject.booleanOrCorrupt(name: String): Boolean =`
- [L238](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:238)：`private fun JsonObject.longOrCorrupt(name: String): Long =`
- [L242](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:242)：`private fun JsonObject.optionalLong(name: String): Long? {`
- [L249](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:249)：`private fun JsonObject.optionalString(name: String): String? {`
- [L255](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:255)：`private fun JsonObject.objectOrCorrupt(name: String): JsonObject =`
- [L258](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:258)：`private fun JsonObject.timeOrCorrupt(name: String): LocalTime {`
- [L267](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt:267)：`private fun JsonObject.dateOrCorrupt(name: String): LocalDate {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDraft.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/ScheduleService.kt

**职责**：监听存储/会话生命周期并按最近到期时间等待，批量合并同会话提醒、打开原会话并交给 Host 投递。

**规模**：210 行；17 个词法声明。源码：[ScheduleService.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:1)。

**声明与运行入口**：

- [L19](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:19)：`class ScheduleDeliveryException(val error: ScheduleError) : Exception(error.name)`
- [L21](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:21)：`class ScheduleService(`
- [L34](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:34)：`fun start() {`
- [L55](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:55)：`fun stop() {`
- [L60](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:60)：`suspend fun list(): List<Schedule> {`
- [L66](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:66)：`suspend fun recomputeTargets() {`
- [L71](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:71)：`suspend fun create(newSchedule: NewSchedule): Schedule {`
- [L77](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:77)：`suspend fun create(`
- [L97](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:97)：`suspend fun update(id: String, patch: SchedulePatch): Schedule? {`
- [L103](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:103)：`suspend fun delete(id: String): Boolean {`
- [L109](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:109)：`suspend fun runNow(id: String): Result<Unit> = deliveryMutex.withLock {`
- [L125](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:125)：`internal suspend fun deliverDue() = deliveryMutex.withLock {`
- [L143](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:143)：`internal suspend fun handleRuntimeEvent(event: SessionRuntimeEvent) {`
- [L152](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:152)：`private suspend fun deliverSchedules(group: List<Schedule>): ScheduleError? {`
- [L175](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:175)：`private suspend fun timerLoop() {`
- [L190](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:190)：`private suspend fun refresh() {`
- [L199](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt:199)：`private fun reminderText(group: List<Schedule>): Pair<String, String> {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`

### runtime/ScheduleStore.kt

**职责**：schemaVersion 2 提醒 JSON、Mutex、原子持久化、投递成功/失败状态与时间重新计算。

**规模**：285 行；17 个词法声明。源码：[ScheduleStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:1)。

**声明与运行入口**：

- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:25)：`data class NewSchedule(`
- [L35](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:35)：`data class SchedulePatch(`
- [L45](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:45)：`class ScheduleStore(`
- [L54](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:54)：`suspend fun list(): List<Schedule> = mutex.withLock { load() }`
- [L56](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:56)：`suspend fun create(newSchedule: NewSchedule): Schedule = mutex.withLock {`
- [L83](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:83)：`suspend fun update(id: String, patch: SchedulePatch): Schedule? = mutex.withLock {`
- [L121](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:121)：`suspend fun delete(id: String): Boolean = mutex.withLock {`
- [L129](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:129)：`suspend fun markDelivered(id: String, at: Long): Schedule? = mutex.withLock {`
- [L146](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:146)：`suspend fun markDeliveryFailed(id: String, error: ScheduleError, at: Long): Schedule? = mutex.withLock {`
- [L162](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:162)：`suspend fun markRunNow(id: String, at: Long, error: ScheduleError? = null): Schedule? = mutex.withLock {`
- [L176](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:176)：`suspend fun deleteBySession(sessionId: String): Boolean = mutex.withLock {`
- [L184](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:184)：`suspend fun disableBySession(sessionId: String): Boolean = mutex.withLock {`
- [L199](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:199)：`suspend fun recomputeTargets(): Unit = mutex.withLock {`
- [L223](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:223)：`private fun computeNextRunAt(trigger: ScheduleTrigger, now: ZonedDateTime = ZonedDateTime.now(clock)): Long? =`
- [L226](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:226)：`private fun nextRunAtAfter(trigger: ScheduleTrigger, at: Long): Long? =`
- [L231](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:231)：`private fun load(): List<Schedule> {`
- [L257](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt:257)：`private fun persist(schedules: List<Schedule>) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/ScheduleTools.kt

**职责**：schedule_create/list/update/delete 的模型工具契约，绑定当前会话与工作区。

**规模**：354 行；16 个词法声明。源码：[ScheduleTools.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:1)。

**声明与运行入口**：

- [L122](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:122)：`fun scheduleTools(cwd: Path, context: AppToolContext, binding: SessionBinding): List<CodingTool> = listOf(`
- [L129](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:129)：`private fun scheduleCreateTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(`
- [L166](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:166)：`private fun scheduleListTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(`
- [L190](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:190)：`private fun scheduleUpdateTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(`
- [L224](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:224)：`private fun scheduleDeleteTool(cwd: Path, context: AppToolContext, binding: SessionBinding): CodingTool = CodingTool(`
- [L251](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:251)：`private fun requireSession(binding: SessionBinding): BoundSession =`
- [L254](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:254)：`private suspend fun scheduleToolResult(block: suspend () -> dev.pi.agent.AgentToolResult): dev.pi.agent.AgentToolResult =`
- [L265](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:265)：`private fun scheduleJson(schedule: Schedule): JsonObject = buildJsonObject {`
- [L288](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:288)：`private fun parseTrigger(args: JsonObject, base: ScheduleTrigger?, requestedFrequency: String?): ScheduleTrigger {`
- [L319](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:319)：`private fun parseTimeArg(args: JsonObject, fallback: LocalTime?): LocalTime? {`
- [L324](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:324)：`private fun parseTime(raw: String): LocalTime {`
- [L334](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:334)：`private fun parseDate(raw: String): LocalDate = try {`
- [L340](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:340)：`private fun promptPreview(prompt: String): String {`
- [L345](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:345)：`private fun JsonObject.stringOrNull(name: String): String? =`
- [L348](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:348)：`private fun JsonObject.boolOrNull(name: String): Boolean? =`
- [L351](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt:351)：`private fun JsonObject.requiredString(name: String): String =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/SdkCredentialStore.kt

**职责**：为同步 SDK CredentialStore 提供内存缓存，并异步写加密存储；同步缓存与异步持久化之间当前缺少按 Provider 顺序控制。

**规模**：201 行；21 个词法声明。源码：[SdkCredentialStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:1)。

**声明与运行入口**：

- [L22](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:22)：`enum class ProviderAuthKind {`
- [L27](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:27)：`class SdkCredentialStore(`
- [L41](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:41)：`suspend fun preload(vararg providerIds: String) {`
- [L52](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:52)：`suspend fun preloadAll() {`
- [L67](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:67)：`fun cachedApiKey(providerId: String): String? = apiKeyCache[providerId]?.takeIf { it.isNotBlank() }`
- [L69](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:69)：`fun invalidate(providerId: String) {`
- [L75](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:75)：`fun kindOf(providerId: String): ProviderAuthKind? = when {`
- [L81](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:81)：`override fun read(providerId: String): Credential? {`
- [L86](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:86)：`override fun readAll(): Map<String, Credential> {`
- [L97](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:97)：`override fun readApiKey(providerId: String): String? = apiKeyCache[providerId]?.takeIf { it.isNotBlank() }`
- [L99](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:99)：`override fun readEnv(providerId: String): Map<String, String>? = null`
- [L101](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:101)：`override fun list(): List<CredentialInfo> = keys().map { providerId ->`
- [L108](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:108)：`override fun keys(): List<String> = (apiKeyCache.keys + oauthCache.keys).distinct()`
- [L110](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:110)：`override fun modify(providerId: String, transform: (Credential?) -> Credential?): Credential? {`
- [L117](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:117)：`override fun writeApiKey(providerId: String, key: String, env: Map<String, String>?) {`
- [L137](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:137)：`override fun write(providerId: String, credential: Credential) {`
- [L151](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:151)：`override fun writeRawCredential(providerId: String, credential: JsonElement) {`
- [L167](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:167)：`override fun delete(providerId: String) {`
- [L182](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:182)：`override fun readRaw(): Map<String, JsonElement> = oauthCache.toMap()`
- [L184](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:184)：`private fun publishKinds() {`
- [L192](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt:192)：`private fun decodeBasic(raw: JsonElement): OAuthCredential? {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/SessionBinding.kt

**职责**：会话创建前构造工具、创建后再附着 AgentSession 的小型绑定对象，避免工具工厂与 session 初始化循环。

**规模**：24 行；4 个词法声明。源码：[SessionBinding.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt:1)。

**声明与运行入口**：

- [L5](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt:5)：`data class BoundSession(`
- [L11](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt:11)：`class SessionBinding {`
- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt:15)：`fun attach(session: AgentSession) {`
- [L23](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt:23)：`fun current(): BoundSession? = bound`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`

### runtime/SessionCatalog.kt

**职责**：跨已知 cwd 扫描 SDK JSONL，叠加标题/置顶/归档元数据，提供列表/搜索基础与删除。

**规模**：72 行；11 个词法声明。源码：[SessionCatalog.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:1)。

**声明与运行入口**：

- [L13](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:13)：`class SessionCatalog(`
- [L19](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:19)：`suspend fun list(cwd: String): List<ConversationSummary> = withContext(Dispatchers.IO) {`
- [L27](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:27)：`suspend fun listAll(cwds: List<String>): List<ConversationSummary> = withContext(Dispatchers.IO) {`
- [L41](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:41)：`suspend fun find(cwds: List<String>, sessionId: String): ConversationSummary? =`
- [L44](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:44)：`suspend fun delete(filePath: String) = withContext(Dispatchers.IO) {`
- [L49](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:49)：`suspend fun rename(id: String, title: String) = metaStore.update(id) {`
- [L53](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:53)：`suspend fun setPinned(id: String, pinned: Boolean) = metaStore.update(id) { it.copy(pinned = pinned) }`
- [L55](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:55)：`suspend fun setArchived(id: String, archived: Boolean) = metaStore.update(id) { it.copy(archived = archived) }`
- [L57](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:57)：`suspend fun removeMeta(id: String) = metaStore.remove(id)`
- [L59](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:59)：`private fun SessionInfo.toSummary(meta: SessionMeta?): ConversationSummary = ConversationSummary(`
- [L69](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt:69)：`private fun parseTimestamp(value: String): Long = runCatching {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/SessionMetaStore.kt

**职责**：DataStore 中的会话标题覆盖、置顶与归档状态；不替代 SDK JSONL 消息权威。

**规模**：124 行；14 个词法声明。源码：[SessionMetaStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:18)：`data class SessionMeta(`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:25)：`class SessionMetaStore(context: Context) {`
- [L35](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:35)：`suspend fun read(): Map<String, SessionMeta> = metas.first()`
- [L37](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:37)：`suspend fun update(sessionId: String, transform: (SessionMeta) -> SessionMeta) {`
- [L46](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:46)：`suspend fun remove(sessionId: String) {`
- [L54](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:54)：`private fun decode(raw: String?): Map<String, SessionMeta> {`
- [L70](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:70)：`private fun encode(entries: Map<String, SessionMeta>): String {`
- [L86](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:86)：`class SessionRepository(`
- [L90](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:90)：`suspend fun list(includeArchived: Boolean = false): List<ConversationSummary> = withContext(Dispatchers.IO) {`
- [L105](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:105)：`suspend fun archivedCount(): Int = list(includeArchived = true).count { it.archived }`
- [L107](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:107)：`suspend fun rename(id: String, title: String) {`
- [L111](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:111)：`suspend fun setPinned(id: String, pinned: Boolean) {`
- [L115](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:115)：`suspend fun setArchived(id: String, archived: Boolean) {`
- [L119](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt:119)：`suspend fun delete(id: String) = withContext(Dispatchers.IO) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/SkillCatalog.kt

**职责**：从全局/当前工作区发现技能并支持显式技能展开，供 slash 面板与发送前注入。

**规模**：47 行；9 个词法声明。源码：[SkillCatalog.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:1)。

**声明与运行入口**：

- [L10](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:10)：`data class SkillExpansion(val name: String, val prompt: String)`
- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:12)：`data class SkillSummary(`
- [L17](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:17)：`interface SkillExpander {`
- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:18)：`suspend fun expand(cwd: String, name: String, args: String): SkillExpansion?`
- [L20](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:20)：`suspend fun list(cwd: String): List<SkillSummary>`
- [L23](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:23)：`class SkillCatalog(private val agentDir: File) : SkillExpander {`
- [L24](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:24)：`override suspend fun expand(cwd: String, name: String, args: String): SkillExpansion? {`
- [L34](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:34)：`override suspend fun list(cwd: String): List<SkillSummary> =`
- [L39](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SkillCatalog.kt:39)：`private suspend fun discover(cwd: String): List<Skill> = runCatching {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/SystemPrompt.kt

**职责**：首次生成 DeepDeep SYSTEM.md，构造当前工具的规则 section；区分 host 文件工具和 guest bash 工作路径。

**规模**：33 行；2 个词法声明。源码：[SystemPrompt.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:1)。

**声明与运行入口**：

- [L17](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:17)：`internal fun ensureSystemPromptFile(agentDir: File) {`
- [L24](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SystemPrompt.kt:24)：`internal fun buildToolRuleSection(tools: List<CodingTool>): Map<String, String> {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/UsageModels.kt

**职责**：会话 token/费用统计、手动压缩结果和 usage 事件的数据契约。

**规模**：23 行；7 个词法声明。源码：[UsageModels.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:3)：`data class SessionUsage(`
- [L13](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:13)：`sealed interface CompactionOutcome {`
- [L14](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:14)：`data class Done(val tokensBefore: Long, val tokensAfter: Long?) : CompactionOutcome`
- [L16](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:16)：`data object Empty : CompactionOutcome`
- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:18)：`data object Busy : CompactionOutcome`
- [L20](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:20)：`data object Unavailable : CompactionOutcome`
- [L22](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/UsageModels.kt:22)：`data class Failed(val message: String?) : CompactionOutcome`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatUiState.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatViewModel.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ConversationReducer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/QuestionCenter.kt`

### runtime/WorkshopAgent.kt

**职责**：工坊一次性 Agent 的请求、能力目录、模型、事件和 Gateway 接口，与 WebView 协议解耦。

**规模**：78 行；13 个词法声明。源码：[WorkshopAgent.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:3)：`data class WorkshopAgentModel(`
- [L10](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:10)：`data class WorkshopAgentTool(`
- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:15)：`data class WorkshopAgentSkill(`
- [L20](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:20)：`data class WorkshopAgentCapabilities(`
- [L25](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:25)：`data class WorkshopAgentRequest(`
- [L36](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:36)：`data class WorkshopAgentEvent(`
- [L48](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:48)：`object WorkshopAgentEventType {`
- [L59](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:59)：`object WorkshopAgentRunStatus {`
- [L64](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:64)：`object WorkshopAgentErrorCode {`
- [L69](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:69)：`interface WorkshopAgentGateway {`
- [L70](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:70)：`suspend fun models(): List<WorkshopAgentModel>`
- [L72](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:72)：`suspend fun capabilities(): WorkshopAgentCapabilities`
- [L74](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt:74)：`suspend fun run(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`

### runtime/WorkshopAgentService.kt

**职责**：每次工坊调用创建临时 cwd/agentDir，选择模型/技能/工具、运行 SDK 并在结束后删目录；限 180 秒/16 默认模型轮，但临时目录不等于权限边界。

**规模**：407 行；23 个词法声明。源码：[WorkshopAgentService.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:1)。

**声明与运行入口**：

- [L42](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:42)：`class WorkshopAgentService(`
- [L55](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:55)：`override suspend fun models(): List<WorkshopAgentModel> =`
- [L58](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:58)：`override suspend fun capabilities(): WorkshopAgentCapabilities {`
- [L78](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:78)：`override suspend fun run(`
- [L98](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:98)：`suspend fun emit(event: WorkshopAgentEvent) {`
- [L105](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:105)：`suspend fun awaitSettled() {`
- [L201](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:201)：`private fun createEngine(`
- [L217](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:217)：`private suspend fun discoverSkills(cwd: File): List<Skill> = runCatching {`
- [L226](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:226)：`private fun createRunDirectory(): File {`
- [L238](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:238)：`internal fun buildWorkshopAgentModels(`
- [L254](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:254)：`internal fun buildWorkshopAgentTools(tools: List<CodingTool>): List<WorkshopAgentTool> =`
- [L257](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:257)：`internal fun visibleWorkshopAgentTools(tools: List<CodingTool>): List<CodingTool> =`
- [L260](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:260)：`internal fun workshopAgentToolVisible(name: String): Boolean =`
- [L265](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:265)：`internal fun workshopAgentEventOf(sessionEvent: SessionEvent): WorkshopAgentEvent? = when (sessionEvent) {`
- [L275](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:275)：`internal fun workshopAgentEventOf(event: AgentEvent): WorkshopAgentEvent? = when (event) {`
- [L298](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:298)：`internal fun validateWorkshopAgentRequest(request: WorkshopAgentRequest) {`
- [L317](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:317)：`internal fun workshopAgentMaxTurns(maxTurns: Int?): Int = maxTurns ?: DEFAULT_MAX_TURNS`
- [L319](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:319)：`internal fun resolveWorkshopAgentConfig(`
- [L355](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:355)：`internal fun parseWorkshopThinkingLevel(value: String): ThinkingLevel {`
- [L361](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:361)：`internal fun selectWorkshopAgentNames(`
- [L379](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:379)：`internal fun workshopSkillDirectoryName(name: String): String {`
- [L384](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:384)：`internal fun copySelectedSkills(skills: List<Skill>, skillsRoot: File, targetSkillsRoot: File) {`
- [L400](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt:400)：`internal fun writeAppendSystemPrompt(agentDir: File, systemPrompt: String?) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AppTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/Personalization.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PiSessionEngine.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`

### runtime/WorkshopBridge.kt

**职责**：工坊 WebMessage 的 JSON 路由与参数限制，支持 info/data/ui/agent 方法；数据按当前创意绑定，Agent 能力另经 Gateway。

**规模**：368 行；28 个词法声明。源码：[WorkshopBridge.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:18)：`object WorkshopBridgeErrors {`
- [L36](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:36)：`class WorkshopBridgeRouter(`
- [L42](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:42)：`suspend fun handle(rawMessage: String, emit: suspend (JsonObject) -> Unit): String {`
- [L70](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:70)：`private suspend fun dispatch(`
- [L88](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:88)：`private fun info(): JsonObject = buildJsonObject {`
- [L97](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:97)：`private suspend fun dataGet(params: JsonObject): JsonElement {`
- [L102](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:102)：`private suspend fun dataSet(params: JsonObject): JsonElement {`
- [L118](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:118)：`private suspend fun dataRemove(params: JsonObject): JsonElement {`
- [L123](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:123)：`private suspend fun dataKeys(): JsonElement {`
- [L128](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:128)：`private fun showToast(params: JsonObject): JsonElement {`
- [L142](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:142)：`private fun requireAgent(): WorkshopAgentGateway = agent`
- [L145](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:145)：`private suspend fun agentModels(): JsonElement {`
- [L166](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:166)：`private suspend fun agentCapabilities(): JsonElement {`
- [L193](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:193)：`private suspend fun agentRun(`
- [L215](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:215)：`private fun eventEnvelope(id: Long, event: WorkshopAgentEvent): JsonObject = buildJsonObject {`
- [L230](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:230)：`private fun promptOf(params: JsonObject): String {`
- [L247](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:247)：`private fun systemPromptOf(params: JsonObject): String? {`
- [L258](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:258)：`private fun maxTurnsOf(params: JsonObject): Int? {`
- [L276](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:276)：`private fun stringListOf(params: JsonObject, field: String): List<String>? {`
- [L292](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:292)：`private fun requiredText(params: JsonObject, field: String): String? {`
- [L297](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:297)：`private fun optionalText(params: JsonObject, field: String): String? {`
- [L302](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:302)：`private fun textOf(element: JsonElement, field: String): String? {`
- [L311](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:311)：`private suspend fun <T> agentCall(block: suspend () -> T): T = try {`
- [L327](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:327)：`private fun keyOf(params: JsonObject): String {`
- [L341](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:341)：`private suspend fun <T> dataCall(block: suspend () -> T): T = try {`
- [L352](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:352)：`private fun successResponse(id: Long, result: JsonElement): String = buildJsonObject {`
- [L358](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:358)：`private fun errorResponse(id: Long?, code: String, message: String): String = buildJsonObject {`
- [L368](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopBridge.kt:368)：`private class BridgeException(val code: String, message: String) : Exception(message)`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopBridgeHost.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgent.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/WorkshopDataStore.kt

**职责**：每个创意一个 SQLite KV 文件，按锁串行访问与关闭；网页和 Agent 数据工具复用同一数据抽象。

**规模**：115 行；15 个词法声明。源码：[WorkshopDataStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:12)：`interface WorkshopData {`
- [L13](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:13)：`suspend fun get(key: String): JsonElement?`
- [L15](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:15)：`suspend fun set(key: String, value: JsonElement)`
- [L17](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:17)：`suspend fun remove(key: String): Boolean`
- [L19](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:19)：`suspend fun keys(): List<String>`
- [L21](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:21)：`fun close()`
- [L24](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:24)：`class SqliteWorkshopData(private val databaseFile: File) : WorkshopData {`
- [L29](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:29)：`override suspend fun get(key: String): JsonElement? = withContext(Dispatchers.IO) {`
- [L33](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:33)：`override suspend fun set(key: String, value: JsonElement) {`
- [L39](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:39)：`override suspend fun remove(key: String): Boolean = withContext(Dispatchers.IO) {`
- [L43](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:43)：`override suspend fun keys(): List<String> = withContext(Dispatchers.IO) {`
- [L63](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:63)：`override fun close() {`
- [L72](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:72)：`private fun database(): SQLiteDatabase {`
- [L87](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:87)：`private fun SQLiteDatabase.read(key: String): JsonElement? =`
- [L102](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt:102)：`private fun SQLiteDatabase.write(key: String, value: String) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/WorkshopModels.kt

**职责**：创意 manifest、条目、扫描/校验状态和 JSON 契约，限制 id/入口等字段。

**规模**：158 行；19 个词法声明。源码：[WorkshopModels.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:1)。

**声明与运行入口**：

- [L11](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:11)：`class WorkshopManifestException(message: String) : Exception(message)`
- [L13](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:13)：`data class WorkshopManifest(`
- [L21](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:21)：`fun toJson(): String = JSON.encodeToString(`
- [L33](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:33)：`fun withName(name: String): WorkshopManifest = copy(name = normalizedName(name))`
- [L43](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:43)：`fun parse(text: String): WorkshopManifest {`
- [L81](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:81)：`internal fun normalizedName(raw: String): String {`
- [L89](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:89)：`internal fun validateEntry(entry: String) {`
- [L101](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:101)：`private fun requiredString(root: JsonObject, field: String): String =`
- [L104](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:104)：`private fun optionalString(root: JsonObject, field: String): String? {`
- [L116](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:116)：`data class WorkshopCreation(`
- [L121](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:121)：`enum class WorkshopInvalidReason {`
- [L129](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:129)：`data class WorkshopInvalid(`
- [L136](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:136)：`data class WorkshopScan(`
- [L141](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:141)：`sealed interface WorkshopEntry {`
- [L142](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:142)：`data class Valid(val creation: WorkshopCreation) : WorkshopEntry`
- [L144](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:144)：`data class Invalid(`
- [L150](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:150)：`data object Missing : WorkshopEntry`
- [L153](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:153)：`data class WorkshopDraft(`
- [L158](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt:158)：`class WorkshopLoadException(message: String) : Exception(message)`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopAssetLoader.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt`

### runtime/WorkshopStore.kt

**职责**：创意 draft/apps/userdata 目录管理、manifest 校验、装载/改名/删除和目录边界保护。

**规模**：421 行；35 个词法声明。源码：[WorkshopStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:1)。

**声明与运行入口**：

- [L18](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:18)：`data class CreatedDraft(`
- [L23](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:23)：`class WorkshopStore(`
- [L32](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:32)：`suspend fun scan(): WorkshopScan = withContext(Dispatchers.IO) {`
- [L46](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:46)：`suspend fun load(id: String): WorkshopEntry = withContext(Dispatchers.IO) {`
- [L60](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:60)：`suspend fun drafts(): List<WorkshopDraft> = withContext(Dispatchers.IO) {`
- [L66](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:66)：`suspend fun createDraft(`
- [L91](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:91)：`suspend fun loadDraft(draftPath: String): WorkshopCreation = withContext(Dispatchers.IO) {`
- [L145](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:145)：`suspend fun rename(id: String, name: String): WorkshopCreation? = withContext(Dispatchers.IO) {`
- [L154](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:154)：`suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {`
- [L161](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:161)：`fun appDirectory(id: String): File = File(appsDir, id)`
- [L163](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:163)：`fun dataFile(id: String): File = File(userdataDir, "$id.db")`
- [L165](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:165)：`private fun ensureRoot() {`
- [L173](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:173)：`private fun newManifest(`
- [L197](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:197)：`private fun slug(raw: String): String {`
- [L213](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:213)：`private fun uniqueId(base: String): String {`
- [L224](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:224)：`private fun takenIds(): Set<String> {`
- [L234](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:234)：`private fun newDraftDirectory(id: String): File {`
- [L242](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:242)：`private fun appDirectories(): List<File> = childDirectories(appsDir)`
- [L244](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:244)：`private fun draftDirectories(): List<File> = childDirectories(buildDir)`
- [L246](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:246)：`private fun childDirectories(parent: File): List<File> {`
- [L258](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:258)：`private fun inspect(directory: File): Inspection {`
- [L322](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:322)：`private fun readManifest(directory: File): WorkshopManifest? = try {`
- [L329](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:329)：`private fun guardedAppDirectory(id: String): File? {`
- [L337](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:337)：`private fun deleteDataFiles(id: String) {`
- [L345](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:345)：`private fun writeManifest(directory: File, manifest: WorkshopManifest) {`
- [L349](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:349)：`private fun writeTextAtomic(file: File, text: String) {`
- [L368](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:368)：`private fun validateDraftContents(draft: File) {`
- [L373](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:373)：`object : SimpleFileVisitor<Path>() {`
- [L374](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:374)：`override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {`
- [L381](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:381)：`override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {`
- [L396](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:396)：`override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {`
- [L403](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:403)：`private sealed interface Inspection {`
- [L404](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:404)：`data class Valid(val creation: WorkshopCreation) : Inspection`
- [L406](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:406)：`data class Invalid(val invalid: WorkshopInvalid) : Inspection`
- [L408](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt:408)：`data object Ignored : Inspection`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/OAuthCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleCron.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/WorkshopTools.kt

**职责**：workshop_create/load/list/data_* 等工具，把模型文件产物纳入创意目录并允许单独修改数据。

**规模**：427 行；16 个词法声明。源码：[WorkshopTools.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:1)。

**声明与运行入口**：

- [L54](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:54)：`fun workshopTools(store: WorkshopStore): List<CodingTool> = listOf(`
- [L60](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:60)：`private fun workshopCreateTool(store: WorkshopStore): CodingTool = CodingTool(`
- [L97](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:97)：`private fun workshopLoadTool(store: WorkshopStore): CodingTool = CodingTool(`
- [L132](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:132)：`private fun workshopListTool(store: WorkshopStore): CodingTool = CodingTool(`
- [L261](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:261)：`fun workshopDataTools(store: WorkshopStore): List<CodingTool> =`
- [L264](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:264)：`internal fun workshopDataTools(store: WorkshopStore, dataFactory: (File) -> WorkshopData): List<CodingTool> = listOf(`
- [L271](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:271)：`private fun workshopDataGetTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(`
- [L297](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:297)：`private fun workshopDataSetTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(`
- [L327](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:327)：`private fun workshopDataDeleteTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(`
- [L353](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:353)：`private fun workshopDataKeysTool(store: WorkshopStore, dataFactory: (File) -> WorkshopData): CodingTool = CodingTool(`
- [L379](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:379)：`private suspend fun requireWorkshopApp(store: WorkshopStore, id: String): WorkshopCreation =`
- [L389](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:389)：`private fun workshopDataKey(raw: String): String {`
- [L398](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:398)：`private suspend fun withWorkshopData(`
- [L412](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:412)：`private suspend fun workshopToolResult(block: suspend () -> AgentToolResult): AgentToolResult = try {`
- [L422](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:422)：`private fun JsonObject.workshopString(name: String): String? =`
- [L425](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopTools.kt:425)：`private fun JsonObject.requiredWorkshopString(name: String): String =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AskUserQuestionTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/DeepDeepOAuthCallbackServerFactory.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/HistoryMapper.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/InstallationIdStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpJson.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/PresentTool.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ScheduleTools.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopModels.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt`

### runtime/WorkspaceStore.kt

**职责**：DataStore 中工作区 id/name/cwd/createdAt 的登记与维护；Home 由 Host 额外提供。

**规模**：90 行；7 个词法声明。源码：[WorkspaceStore.kt](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:1)。

**声明与运行入口**：

- [L17](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:17)：`class WorkspaceStore(context: Context) {`
- [L27](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:27)：`suspend fun read(): List<RuntimeWorkspace> = workspaces.first()`
- [L29](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:29)：`suspend fun add(workspace: RuntimeWorkspace) {`
- [L38](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:38)：`suspend fun rename(id: String, name: String) {`
- [L47](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:47)：`suspend fun remove(id: String) {`
- [L53](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:53)：`private fun decode(raw: String?): List<RuntimeWorkspace> {`
- [L77](/home/whywood/project/DeepDeep/runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkspaceStore.kt:77)：`private fun encode(workspaces: List<RuntimeWorkspace>): String {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/LegacyWorkspaceMigration.kt`

**同包符号引用（词法候选，不是调用图）**：`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/AgentRuntimeHost.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ApiKeyStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/EncryptedSecretStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/GoalStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobContracts.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/JobRegistry.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/McpConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ModelConfigStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/ProviderOAuthService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/RuntimeTypes.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SdkCredentialStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionBinding.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionCatalog.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/SessionMetaStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopAgentService.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopDataStore.kt`、`runtime/src/main/kotlin/io/github/whywood/deepdeep/runtime/WorkshopStore.kt`

## :shell

### shell/LinuxEnvironment.kt

**职责**：Android Linux 环境门面：定位共享 rootfs/native binaries、安装器、PRoot 执行、DNS 和目录映射；每个 cwd 实例共享同一个 rootfs。

**规模**：112 行；9 个词法声明。源码：[LinuxEnvironment.kt](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:1)。

**声明与运行入口**：

- [L9](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:9)：`class LinuxEnvironment(`
- [L41](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:41)：`suspend fun install(): LinuxInstallState {`
- [L54](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:54)：`suspend fun reset(): LinuxInstallState = installer.reset()`
- [L56](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:56)：`suspend fun refreshState(): LinuxInstallState = installer.refreshState()`
- [L58](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:58)：`override suspend fun execute(`
- [L69](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:69)：`suspend fun executeStreaming(`
- [L81](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:81)：`suspend fun refreshDns() {`
- [L100](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:100)：`private fun writeNotices() {`
- [L109](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt:109)：`private fun requireSupported() {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/LinuxJobRunner.kt`

**同包符号引用（词法候选，不是调用图）**：`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt`

### shell/LinuxExecutor.kt

**职责**：Linux 执行接口与 exitCode/output/truncated/timedOut/duration 的结果契约。

**规模**：17 行；3 个词法声明。源码：[LinuxExecutor.kt](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt:3)：`data class LinuxResult(`
- [L11](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt:11)：`interface LinuxExecutor {`
- [L12](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt:12)：`suspend fun execute(command: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): LinuxResult`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt`

**同包符号引用（词法候选，不是调用图）**：`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt`

### shell/LinuxInstallState.kt

**职责**：Linux 未安装、安装中、已安装（版本/大小）和失败状态类型。

**规模**：16 行；5 个词法声明。源码：[LinuxInstallState.kt](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt:1)。

**声明与运行入口**：

- [L3](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt:3)：`sealed interface LinuxInstallState {`
- [L4](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt:4)：`data object NotInstalled : LinuxInstallState`
- [L6](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt:6)：`data object Installing : LinuxInstallState`
- [L8](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt:8)：`data class Installed(`
- [L13](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt:13)：`data class Failed(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/data/DevPackageManager.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomRuntime.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/service/LinuxJobRunner.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### shell/LinuxRootfsInstaller.kt

**职责**：SHA256 校验、rootfs 解包/标记/激活/重置和状态读取；当前状态读取附带全目录大小扫描。

**规模**：137 行；8 个词法声明。源码：[LinuxRootfsInstaller.kt](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:1)。

**声明与运行入口**：

- [L19](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:19)：`class LinuxRootfsInstaller(`
- [L29](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:29)：`suspend fun install(): LinuxInstallState = mutex.withLock {`
- [L83](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:83)：`suspend fun reset(): LinuxInstallState = mutex.withLock {`
- [L95](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:95)：`suspend fun refreshState(): LinuxInstallState = mutex.withLock {`
- [L101](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:101)：`fun currentState(): LinuxInstallState = readState()`
- [L103](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:103)：`private fun readState(): LinuxInstallState {`
- [L117](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:117)：`private fun directorySize(root: java.nio.file.Path): Long = runCatching {`
- [L125](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt:125)：`private fun removeStaleTemps(parent: File) {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxInstallState.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt`

### shell/LinuxShellExecutor.kt

**职责**：将 SDK ShellExecutor 转成 LinuxEnvironment，映射取消信号、1..600 秒超时和截断信息。

**规模**：41 行；2 个词法声明。源码：[LinuxShellExecutor.kt](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxShellExecutor.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxShellExecutor.kt:12)：`class LinuxShellExecutor(`
- [L16](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxShellExecutor.kt:16)：`override suspend fun run(command: String, timeoutSeconds: Long?, signal: RunSignal?): ShellResult {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/AppGraph.kt`

**同包符号引用（词法候选，不是调用图）**：`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt`

### shell/PRootExecutor.kt

**职责**：构造 PRoot 参数/环境变量/挂载，启动命令线程，持续读取合并输出，以 watchdog 控制超时并杀进程树。

**规模**：308 行；14 个词法声明。源码：[PRootExecutor.kt](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:1)。

**声明与运行入口**：

- [L12](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:12)：`class PRootExecutor(`
- [L27](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:27)：`override suspend fun execute(command: String, timeoutMillis: Long): LinuxResult =`
- [L30](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:30)：`suspend fun executeStreaming(`
- [L66](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:66)：`fun buildCommand(command: String): List<String> = buildList {`
- [L98](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:98)：`private fun executeBlocking(`
- [L188](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:188)：`private fun startProcess(`
- [L231](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:231)：`private fun killTree(process: Process, pid: Long) {`
- [L245](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:245)：`private fun collectChildren(pid: Long, output: MutableList<Long>, visited: MutableSet<Long>) {`
- [L253](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:253)：`private fun childPids(pid: Long): List<Long> {`
- [L272](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:272)：`private fun killPid(pid: Long) {`
- [L281](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:281)：`private data class RunningProcess(`
- [L287](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:287)：`private fun readPidFile(pidFile: File): Long {`
- [L296](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:296)：`internal fun prepareBindDirectories(rootfsDir: File, binds: List<Pair<File, String>>) {`
- [L303](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/PRootExecutor.kt:303)：`private fun guestRelativePath(guest: String): String? {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxEnvironment.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxExecutor.kt`、`shell/src/main/kotlin/io/github/whywood/deepdeep/shell/LinuxRootfsInstaller.kt`

### shell/RootfsExtractor.kt

**职责**：解析 tar/gzip 内容，防止路径穿越和危险链接，把 rootfs 解到临时目录。

**规模**：216 行；16 个词法声明。源码：[RootfsExtractor.kt](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:1)。

**声明与运行入口**：

- [L11](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:11)：`class RootfsExtractor {`
- [L12](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:12)：`fun extract(tarStream: InputStream, target: Path): Int {`
- [L82](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:82)：`internal fun safeRelative(name: String): Path? {`
- [L93](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:93)：`private fun ensureParent(root: Path, rootReal: Path, path: Path) {`
- [L108](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:108)：`private fun ensureDirectory(root: Path, path: Path) {`
- [L118](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:118)：`private fun requireAbsent(path: Path) {`
- [L122](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:122)：`private fun resolveLinkTarget(root: Path, link: Path, rawTarget: String): Path {`
- [L136](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:136)：`private fun verifyChecksum(header: ByteArray) {`
- [L145](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:145)：`private fun readFully(input: InputStream, buffer: ByteArray): Int {`
- [L155](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:155)：`private fun copyEntry(input: InputStream, output: java.io.OutputStream, size: Long) {`
- [L167](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:167)：`private fun skipEntry(input: InputStream, size: Long) {`
- [L172](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:172)：`private fun skipBytes(input: InputStream, count: Long) {`
- [L182](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:182)：`private fun readString(header: ByteArray, offset: Int, length: Int): String {`
- [L187](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:187)：`private fun readOctal(header: ByteArray, offset: Int, length: Int): Long {`
- [L192](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:192)：`private fun setMode(path: Path, mode: Int) {`
- [L200](/home/whywood/project/DeepDeep/shell/src/main/kotlin/io/github/whywood/deepdeep/shell/RootfsExtractor.kt:200)：`private fun modeToPermissions(mode: Int): Set<PosixFilePermission> = buildSet {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

## :design

### design/DdComponents.kt

**职责**：共享按钮、图标按钮、surface/行等基础 Compose 组件，减少业务页面各自定义交互外观。

**规模**：385 行；11 个词法声明。源码：[DdComponents.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:1)。

**声明与运行入口**：

- [L56](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:56)：`fun DdPressable(`
- [L105](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:105)：`fun DdIconButton(`
- [L129](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:129)：`fun DdWordmark(`
- [L151](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:151)：`fun DdHairline(`
- [L159](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:159)：`fun DdSectionLabel(text: String, modifier: Modifier = Modifier) {`
- [L169](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:169)：`fun DdTextAction(`
- [L194](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:194)：`fun DdListRow(`
- [L264](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:264)：`fun DdChip(`
- [L305](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:305)：`fun DdPrimaryButton(`
- [L330](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:330)：`fun DdSheet(`
- [L362](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt:362)：`fun DdSheetHeader(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ContextMeter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/JobsMeter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleTimePicker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/FontScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PrivacyScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdControls.kt

**职责**：统一开关/选择等控件实现，使用设计主题与状态颜色。

**规模**：184 行；2 个词法声明。源码：[DdControls.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt:1)。

**声明与运行入口**：

- [L41](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt:41)：`fun DdSwitch(`
- [L114](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdControls.kt:114)：`fun <T> DdSegmentedControl(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdDialog.kt

**职责**：共享对话框框架、标题/内容/操作布局。

**规模**：85 行；1 个词法声明。源码：[DdDialog.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt:1)。

**声明与运行入口**：

- [L33](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDialog.kt:33)：`fun DdDialog(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdDropdown.kt

**职责**：统一下拉菜单布局和交互。

**规模**：88 行；1 个词法声明。源码：[DdDropdown.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDropdown.kt:1)。

**声明与运行入口**：

- [L34](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdDropdown.kt:34)：`fun DdDropdown(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdIcons.kt

**职责**：集中图标定义与路径，向应用组件暴露一致图标类型。

**规模**：79 行；3 个词法声明。源码：[DdIcons.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt:1)。

**声明与运行入口**：

- [L15](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt:15)：`class DdIcon(@param:DrawableRes val res: Int)`
- [L18](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt:18)：`fun DdIcon(`
- [L33](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt:33)：`object DdIcons {`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/PluginApi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/arrange/ArrangePlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/feishu/FeishuPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/gui/GuiPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/websearch/WebSearchPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/wecom/WecomPlugin.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/JobsMeter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ReasoningCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AnsweredAskCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/FontScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PrivacyScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### design/DdMenu.kt

**职责**：统一上下文菜单和 destructive 操作样式。

**规模**：97 行；2 个词法声明。源码：[DdMenu.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt:1)。

**声明与运行入口**：

- [L26](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt:26)：`fun DdMenu(`
- [L53](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMenu.kt:53)：`fun DdMenuItem(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionActionMenu.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdMotion.kt

**职责**：共享动画时长、缓动与按压状态等 motion tokens。

**规模**：32 行；2 个词法声明。源码：[DdMotion.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt:1)。

**声明与运行入口**：

- [L9](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt:9)：`object DdMotion {`
- [L30](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt:30)：`fun <T> spec(duration: Int = BASE, delayMillis: Int = 0, easing: Easing = EaseOut): TweenSpec<T> =`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ReasoningCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/navigation/AppScreenTransition.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AnsweredAskCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。

### design/DdSearchField.kt

**职责**：统一搜索输入组件。

**规模**：114 行；1 个词法声明。源码：[DdSearchField.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSearchField.kt:1)。

**声明与运行入口**：

- [L34](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSearchField.kt:34)：`fun DdSearchField(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdComponents.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdSpinner.kt

**职责**：统一旋转加载指示器，使用 graphicsLayer 读取动画状态以减少重组。

**规模**：52 行；1 个词法声明。源码：[DdSpinner.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt:1)。

**声明与运行入口**：

- [L22](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdSpinner.kt:22)：`fun DdSpinner(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdTextField.kt

**职责**：共享文本输入组件及焦点/占位/错误等基础视觉规则。

**规模**：151 行；2 个词法声明。源码：[DdTextField.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt:1)。

**声明与运行入口**：

- [L34](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt:34)：`fun DdTextField(`
- [L98](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTextField.kt:98)：`fun DdTextArea(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt`

### design/DdTypingDots.kt

**职责**：统一输入中/等待中的点状动画。

**规模**：68 行；1 个词法声明。源码：[DdTypingDots.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTypingDots.kt:1)。

**声明与运行入口**：

- [L32](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DdTypingDots.kt:32)：`fun DdTypingDots(`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/WorkingIndicator.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt`

**同包符号引用（词法候选，不是调用图）**：`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdMotion.kt`

### design/DeepDeepTheme.kt

**职责**：全局颜色、字体、字号层级、主题模式及 Compose 主题桥接，给所有页面提供统一视觉来源。

**规模**：367 行；14 个词法声明。源码：[DeepDeepTheme.kt](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:1)。

**声明与运行入口**：

- [L25](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:25)：`enum class ThemeMode { SYSTEM, LIGHT, DARK }`
- [L28](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:28)：`data class DeepDeepColors(`
- [L114](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:114)：`enum class DdFontStyle(val id: String, val displayName: String) {`
- [L123](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:123)：`fun fromId(id: String?): DdFontStyle = entries.firstOrNull { it.id == id } ?: NEWSREADER`
- [L128](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:128)：`object DdFonts {`
- [L147](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:147)：`fun apply(style: DdFontStyle) {`
- [L151](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:151)：`fun familyFor(style: DdFontStyle): FontFamily = when (style) {`
- [L166](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:166)：`private fun variableFamily(resId: Int): FontFamily = FontFamily(`
- [L172](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:172)：`private fun weightVariant(weight: Int): FontVariation.Settings =`
- [L176](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:176)：`object DdType {`
- [L261](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:261)：`private fun deepDeepTypography(): Typography = Typography(`
- [L287](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:287)：`private fun DeepDeepColors.materialScheme() = if (dark) {`
- [L342](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:342)：`fun DeepDeepTheme(`
- [L367](/home/whywood/project/DeepDeep/design/src/main/kotlin/io/github/whywood/deepdeep/design/DeepDeepTheme.kt:367)：`fun deepDeepColors(): DeepDeepColors = LocalDeepDeepColors.current`

**显式内部引用**：没有可由 import 扫描确认的跨文件引用；同包调用不在该统计中。

**被哪些文件显式引用**：`app/src/main/kotlin/io/github/whywood/deepdeep/DeepDeepApp.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/MainActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/FontPreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/data/ThemePreferences.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudFeatureSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/plugin/micloud/MiCloudLoginActivity.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/DeepDeepDrawer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/AttachmentUi.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatComposer.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatMarkdown.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTopBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatTurn.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ContextMeter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/InlineCodeText.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/JobsMeter.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/MarkdownImage.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/PresentCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ProcessSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ReasoningCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SelectionActions.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/SlashPalette.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ToolCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/UsageSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/WorkingIndicator.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/files/FileBrowserSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/goal/GoalBar.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/jobs/JobsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpEditor.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpImport.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/mcp/McpScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginSetupSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/plugins/PluginsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AnsweredAskCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/questions/AskQuestionCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleDatePicker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/schedules/ScheduleTimePicker.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/ArchivedSessionsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/sessions/SessionSearchScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ChatSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/DevPackagesSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/FontScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/LinuxEnvironmentSection.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/OAuthLoginSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PersonalizationScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/PrivacyScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderDetailScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderIcon.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/ProviderListScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/RuntimeSettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsComponents.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/settings/SettingsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/skills/SkillsScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/voice/VoiceAssistantBody.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopChatCard.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopViewerScreen.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workshop/WorkshopWebView.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/AgentsDocSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/ProjectSkillsSheet.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceDialogs.kt`、`app/src/main/kotlin/io/github/whywood/deepdeep/ui/workspaces/WorkspaceSettingsScreen.kt`

**同包符号引用（词法候选，不是调用图）**：未发现额外候选。
