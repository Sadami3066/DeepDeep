# 功能清单

本文是 `README.md` 的详细版：功能是什么、边界在哪。安装与开发见 `../dev/DEVELOPMENT.md`。

## 对话

- Claude 风格布局：右侧浅色用户气泡、全宽 assistant、浮动 composer
- 过程按 DSH 思路展示：运行中展开过程、工具/思考行收起；结束后自动收起，只留最终回复
- 工具调用与中间文本按顺序进入过程，历史会话按一个用户回合聚合
- Markdown：标题、段落、引用条、有序/无序/任务列表、表格、带语言标签和复制按钮的代码块
- 42 家 Provider + API Key / OAuth 登录（Anthropic、OpenAI、GitHub Copilot、Codex、xAI、OpenRouter、Kimi、Meta、Radius）；模型与思考等级**按会话独立**
- 会话历史、置顶、归档、重命名、删除；长按菜单跟随点击位置
- 运行状态对账：后台唤醒/锁屏断网重试结束后不会停在「运行中」；思考计时显示「1 小时 2 分 3 秒」
- `/` 命令（人用）：压缩上下文、目标；命令不进入模型上下文
- 上下文 token 环与统计：上下文占用/窗口、本会话输入/输出/缓存/费用

## 动手能力

- Alpine/proot 里的 `bash`（无 root，用户空间，不是安全沙箱）
- 后台任务：`job_start` 创建、`job_output` 读增量、`job_kill` 停止
- 定时任务：提醒绑定到对话，到点投递回原对话；错过时下次打开补投一次
- 目标（goal）：长期目标自动续跑，可暂停/继续/完成/清除；只在当前进程内有效
- 交付卡片：`present` 支持文件、图片、HTML 页面与创意，图片内联预览

## 接入

- MCP：管理 Streamable HTTP 服务器，支持 OAuth 与连接检测；工具经 `tool_search` 按需加载
- 插件：外部=飞书 CLI、企业微信、小米云服务（按需下载/连接/更新/卸载）；内部=网络搜索、界面操作、安排、目标、后台任务、定时任务、创意工坊（随 App 内置，可单独启停）
- GUI 操作插件：通过无障碍服务 `gui_observe/find/tap/set_text/scroll/global/wait/screenshot` 观察并操作当前界面
- 安排插件：读写系统日历事件与提醒（需日历权限）
- 小米云服务插件：WebView 登录 i.mi.com，按开关读写账号、笔记、待办、短信与通话、联系人、相册、录音
- 企业微信插件：安装官方 `wecom-cli`，扫码授权后操作消息、文档、日程、待办
- 语音助手：系统 ASR 转写、系统 TTS 朗读；可申请 Android 系统助手角色
- 接收分享：从其他 App 分享图片/文件/文本，插入当前会话或预填输入框

## 创意工坊

- 每个创意是 Agent 按需求编写的 HTML/CSS/JS 文件夹，用 WebView 渲染成可交互轻应用；没有分类/模板
- 内置 workshop 技能可用 `/skill:workshop` 强制加载；普通会话里建好后用 `workshop_load` 装载并 `present` 交付
- 页面经 bridge 读写自己的数据；`workshop_edit` 可不改页面只更新数据；页面 JS 可通过 `app.agent.*` 编排一次性 Agent
- 能力边界：页面不能调用 App 其他内部函数、不能执行 shell、不能读取 App 其他数据

## 界面

- 常驻抽屉、设置层级推入转场、统一 motion tokens 与按压态
- 每个工作区的会话默认只显示最近 5 条，更多历史可展开/收起
- 字体：英文 Lora（默认）/ Manrope，中文固定使用内置思源黑体子集，不走系统衬线回退

## 已知边界

- 不做权限/审批系统；只有 Linux/root 功能开关
- Linux 是 proot 用户空间，不是安全沙箱
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据
- 后台唤醒/Service 被系统杀死后的定时投递不保证（只保证服务存活时投递 + 下次打开补投一次）
- 本地分发；release 变体用 debug 证书签名，见 `docs/dev/adr/0001-local-distribution-and-signing.md`
