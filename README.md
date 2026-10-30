# deepdeep

Android 上的 personal agent，运行时使用 [pi2kotlin](../pi2kotlin) 的 `pi-coding-agent` SDK。

当前状态：无 root 的基本 agent 可用。

- Claude 风格的聊天布局：右侧浅色用户气泡、全宽 assistant、浮动 composer
- 过程按 DSH 思路展示：运行中展开过程、工具/思考行收起；结束后自动收起，只留最终回复
- 工具调用与中间文本按顺序进入过程，历史会话按一个用户回合聚合
- Markdown：标题、段落、引用条、有序/无序/任务列表、表格、带语言标签和复制按钮的代码块
- 菜单统一圆角、图标 + 左对齐文字；删除项红色；蓝色强调色
- pi2kotlin 流式事件、DeepSeek provider、Alpine/proot 里的 `bash`
- 42 家 Provider + API Key / OAuth 登录（Anthropic、OpenAI、GitHub Copilot、Codex、xAI、OpenRouter、Kimi、Meta、Radius）；模型与思考等级**按会话独立**，在 composer 切换，设置页只做配置
- 会话历史、置顶、归档、重命名、删除；长按菜单跟随点击位置
- 常驻抽屉、设置层级推入转场、统一 motion tokens 与按压态；每个工作区的会话默认只显示最近 5 条，更多历史可展开/收起
- 字体：英文 Lora（默认）/ Manrope，中文固定使用内置思源黑体子集（无衬线），不走系统衬线回退
- 技能仓库：zip 安装、全局/工作区启停、聊天内重新加载；工作区 AGENTS.md 编辑；Agent 自写技能自动入库；Linux 内 `/skills`、`/skill-repo` 可直接 bash 读写
- App 工具：内联提问卡、schedule 系列；提醒绑定到对话，到点投递回原对话，任务页可新建/编辑/立即执行
- `/` 命令（人用）：压缩上下文、目标；命令在任意词边界触发，面板可滚动，不进入模型上下文
- 上下文 token 环与统计：上下文占用/窗口、本会话输入/输出/缓存/费用
- 目标（goal）：创建长期目标后自动续跑（默认不限轮次，可在「插件 → 内部 → 目标」设置轮数上限），可暂停/继续/完成/清除；目标只在当前进程内有效，重启后需重新创建
- 后台任务：`job_start` 创建（`command` + 可选 `label`），`job_output` 读增量、`job_kill` 停止；完成后按聊天设置唤醒或静默
- 运行状态对账：后台唤醒/锁屏断网重试结束后不会停在“运行中”；思考计时显示 `1h 2m 3s` 格式
- 交付卡片：`present` 支持文件、图片、HTML 页面与创意，图片内联预览、HTML 内联渲染、创意入口卡直达工坊
- 提问卡直接内联在聊天消息里，答完折叠为只读问答摘要
- MCP：抽屉「MCP」管理 Streamable HTTP 服务器，支持 OAuth 登录与连接检测、列表搜索；工具经 `tool_search` 在对话中按需加载
- 插件：抽屉「插件」分「外部 / 内部」两类并支持搜索。外部=飞书 CLI、企业微信、小米云服务，按需下载、连接、更新、卸载；内部=网络搜索、界面操作、安排、目标、后台任务、定时任务、创意工坊，随 App 内置，可单独启停、在详情页调参数（目标轮数、任务并发、工坊技能与轮数等）。Agent 可通过 `plugin_setup` 走外部插件授权流程；插件贡献的技能与工具随启用状态注入
- GUI 操作插件：通过 App 自己的无障碍服务 `gui_observe/find/tap/set_text/scroll/global/wait/screenshot` 观察并操作当前界面，附带 `deepdeep-gui` 技能；需用户在系统设置开启无障碍服务
- 安排插件：`arrange_calendar` 直接读写系统日历事件与提醒（创建、读取、修改、删除）；需要日历权限；不提供闹钟与本地待办
- 小米云服务插件：WebView 登录 i.mi.com，按功能开关读取账号、笔记、待办、短信与通话、联系人、相册、录音；笔记/待办开关同时可写，写操作走同源 WebView
- 企业微信插件：安装官方 `wecom-cli`，扫码授权后由 Agent 通过 CLI 操作消息、文档、日程、待办等
- 语音助手：composer 麦克风或系统助手入口，系统 ASR 转写、系统 TTS 朗读回答，可申请 Android 系统助手角色；后台常听/悬浮窗不做
- 接收分享：支持从其他 App 分享图片/文件/文本，自动插入当前会话附件或预填输入框（冷/热启动均可用）
- 创意工坊：抽屉「创意工坊」里每个创意是 Agent 按需求编写的 HTML/CSS/JS 文件夹，用 WebView 渲染成可交互轻应用，没有分类/模板；内置 workshop 技能可用 `/skill:workshop` 强制加载，普通会话里的 Agent 建好后用 `workshop_load` 装载并 `present` 交付；页面经 bridge 读写自己的数据，Agent 也可用 `workshop_edit`（action=get/set/delete/keys）只更新数据（不改页面）；页面 JS 可通过 `app.agent.*` 编排一次性 Agent（支持 `onEvent` 事件流），代码与数据分离
- 无 root；root 方法与工具改造调研见 `docs/ROOT.md`，系统助手/悬浮 UI 调研见 `docs/ANDROID_ASSISTANT.md`

## 结构

```
:app        Compose 聊天/设置/Provider、动画导航、前台服务、装配
:runtime    pi2kotlin 适配、事件、Provider 目录、会话元数据、凭据、MCP 配置
:shell      Alpine/proot 与 ShellExecutor
:design     主题、字体、图标、motion tokens、共享组件
```

## 环境

- JDK 17+
- Android SDK Platform 36
- `pi2kotlin 0.2.2` 已发布到 mavenLocal：在 `../pi2kotlin` 执行 `./gradlew publishToMavenLocal`

## 构建

```bash
./gradlew :app:assembleDebug
```

## 边界

- 工具：pi 原生（含前台 `bash`）+ App 层 `ask_user_question` / `schedule_*` / `goal_*` / `job_*` / `present` / `plugin_setup` / `workshop_*` + 插件贡献的 `web_search` / `web_fetch` / `gui_*` / `arrange_*` / `mi_cloud_*`；提醒在服务存活时投递回原对话，错过时下次打开补投一次。
- 不做权限/审批系统；只有 Linux/root 功能开关。
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据。
- Linux 是 proot 用户空间，不是安全沙箱。
- 创意工坊的页面由 Agent 生成、App 在私有目录内渲染；页面只能通过 bridge 读写自己的数据、弹轻提示，并用 `app.agent.*` 启动一次性任务 Agent；不能调用 App 其他内部函数、执行 shell 或读取 App 其他数据。
