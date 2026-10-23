# deepdeep

Android 上的 personal agent，运行时使用 [pi2kotlin](../pi2kotlin) 的 `pi-coding-agent` SDK。

当前状态：无 root 的基本 agent 可用。

- Claude 风格的聊天布局：右侧浅色用户气泡、全宽 assistant、浮动 composer
- 过程按 DSH 思路展示：运行中展开过程、工具/思考行收起；结束后自动收起，只留最终回复
- 工具调用与中间文本按顺序进入过程，历史会话按一个用户回合聚合
- Markdown：标题、段落、引用条、有序/无序/任务列表、表格、带语言标签和复制按钮的代码块
- 菜单统一圆角、图标 + 左对齐文字；删除项红色；蓝色强调色
- pi2kotlin 流式事件、DeepSeek provider、Alpine/proot 里的 `bash`
- 42 家 Provider 目录 + API Key 认证入口；思考等级在 composer 切换
- 会话历史、置顶、归档、重命名、删除；长按菜单跟随点击位置
- 常驻抽屉、设置层级推入转场、统一 motion tokens 与按压态
- 技能仓库：zip 安装、全局/工作区启停、聊天内重新加载；工作区 AGENTS.md 编辑
- App 工具：内联提问卡、schedule 系列；提醒绑定到对话，到点投递回原对话，任务页可新建/编辑/立即执行
- `/` 命令（人用）：压缩上下文、目标；命令在任意词边界触发，面板可滚动，不进入模型上下文
- 上下文 token 环与统计：上下文占用/窗口、本会话输入/输出/缓存/费用
- 目标（goal）：创建长期目标后自动续跑（默认不限轮次），可暂停/继续/完成/清除，重启后需手动继续
- 后台任务：`job_start` 创建（`command` + 可选 `label`），`job_output` 读增量、`job_kill` 停止；完成后按聊天设置唤醒或静默
- 交付卡片：`present` 工具把生成的文件展示成聊天卡片，可直接打开或分享
- 提问卡直接内联在聊天消息里，答完折叠为只读问答摘要
- 无 root；root 能力后续接入

## 结构

```
:app        Compose 聊天/设置/Provider、动画导航、前台服务、装配
:runtime    pi2kotlin 适配、事件、Provider 目录、会话元数据、Key
:shell      Alpine/proot 与 ShellExecutor
:design     主题、字体、图标、motion tokens、共享组件
```

## 环境

- JDK 17+
- Android SDK Platform 36
- `pi2kotlin 0.1.3` 已发布到 mavenLocal：在 `../pi2kotlin` 执行 `./gradlew publishToMavenLocal`

## 构建

```bash
./gradlew :app:assembleDebug
```

## 边界

- 工具：pi 原生（含前台 `bash`）+ App 层 `ask_user_question` / `schedule_*` / `goal_*` / `job_*` / `present`；提醒在服务存活时投递回原对话，错过时下次打开补投一次。
- 不做权限/审批系统；只有 Linux/root 功能开关。
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据。
- Linux 是 proot 用户空间，不是安全沙箱。
