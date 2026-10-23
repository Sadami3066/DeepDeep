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
- 技能仓库：zip 安装、全局/工作区启停；工作区 AGENTS.md 编辑
- App 工具：提问界面、schedule 系列（只落库，执行器后续）
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
- `pi2kotlin 0.1.1` 已发布到 mavenLocal：在 `../pi2kotlin` 执行 `./gradlew publishToMavenLocal`

## 构建

```bash
./gradlew :app:assembleDebug
```

## 边界

- 工具：pi 原生 + `bash` + App 层 `ask_user_question` / `schedule_*`；Android 能力工具暂不注册。
- 不做权限/审批系统；只有 Linux/root 功能开关。
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据。
- Linux 是 proot 用户空间，不是安全沙箱。
