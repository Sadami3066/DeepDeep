# deepdeep

Android 上的 personal agent，运行时使用 [pi2kotlin](../pi2kotlin) 的 `pi-coding-agent` SDK。

当前状态：无 root 的基本 agent 可用。

- Claude 风格的聊天布局：左侧浅色用户气泡、全宽 assistant、浮动 composer
- DSH 风格的过程展示：思考、工具调用参数/结果/状态/耗时、可折叠过程摘要、实时 working 状态
- pi2kotlin 流式事件、DeepSeek provider、Alpine/proot 里的 `bash`
- 42 家 Provider 目录 + API Key 认证入口；思考等级在 composer 切换
- 会话历史、置顶、归档、重命名、删除；前台服务常驻
- 无 root；root 能力后续接入

## 结构

```
:app        Compose 聊天/设置/Provider、前台服务、装配
:runtime    pi2kotlin 适配、事件、Provider 目录、会话元数据、Key
:shell      Alpine/proot 与 ShellExecutor
:design     主题、字体、Lucide 图标、共享组件
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

- 只使用 pi 原生工具 + `bash`；不写自定义 Android 工具。
- 不做权限/审批系统；只有 Linux/root 功能开关。
- 会话以 pi JSONL 为唯一权威；置顶/归档/重命名是本地元数据。
- Linux 是 proot 用户空间，不是安全沙箱。
