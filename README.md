# deepdeep

Android 上的 personal agent，运行时使用 [pi2kotlin](../pi2kotlin) 的 `pi-coding-agent` SDK。

当前状态：无 root 的基本 agent 已跑通。可用：DeepSeek 流式聊天、历史会话、Alpine `bash`、前台服务。root、定时任务、手机系统能力后续接入。

## 结构

```
:app        Compose 聊天/设置、前台服务（M2）、装配
:runtime    pi2kotlin 适配、DeepSeek、会话、历史、Key
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
- 会话以 pi JSONL 为唯一权威。
- Linux 是 proot 用户空间，不是安全沙箱。
