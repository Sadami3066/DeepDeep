# 设计取舍

这些是**有意为之**的决定，不是缺陷。改代码前按这里判断；要推翻先改本文件并写清迁移代价。

1. **签名**：release 也用 debug 证书。换正式 keystore 会让现装设备无法覆盖安装，`allowBackup=false` 下卸载即丢全部会话与凭据；只在「愿意让所有人重装」的发版点换。
2. **没有权限/审批系统**：只有一个 Linux/root 总开关。个人设备、单人使用，风险自担。
3. **单 Activity + 自研 `AnimatedScreenHost` 导航**，不引入 Navigation-Compose；跨层级转场与页面状态保存已按此实现。
4. **`:app` 不拆 `:feature:*`/`:core:*`**：`:runtime-api` 是唯一纯 JVM 契约层；`:app` 只依赖 `:runtime/:shell/:design`，模块边界由 Gradle 任务强制。
5. **本地分发**：不上架；Maven Central 与仓库公开都推迟，等真有外部使用者再说。
6. **会话以 pi JSONL 为唯一权威**：置顶/归档/改名/草稿都是本地元数据，不回写会话正文。
7. **创意工坊是安全边界**：独立 origin + document-start bridge + 工具白名单；页面不能调 App 内部函数、不能执行 shell、读不到其他数据。
8. **可选 root**：默认关闭的增强层，只注册 `root_shell` 单工具；授权交给系统 root 管理器，App 不做二次审批；只在「前台 + 用户主动发起的轮次」可用。
9. **不做无实测的性能优化**：声称变快必须给 `gfxinfo` 前后数字。
10. **持久化纪律**：原子写（tmp + rename）、坏文件先备份 `.corrupt` 再重建、带 `schemaVersion`。
11. **许可边界**：自有代码 Apache-2.0；PRoot（GPL-2.0-or-later）、Alpine 包、字体/图标、pi2kotlin SDK（MIT）各自许可；不对外说「整个 APK 是 Apache-2.0」。
12. **会话状态只有一个所有者**：`SessionCommandPlane` 用每会话锁与单调 epoch 管理 busy/stop/retry；只有当前 run 能清除 busy，迟到事件按 runId 隔离。
13. **状态与事件分工**：运行状态以 `activeRuns` 为准；事件保持多订阅者广播，UI 从运行快照与历史对账，结构事件不得被流式 delta 挤出顺序。
14. **能力重配置走安全点**：插件能力变化经 `host.reconfigure()`；忙碌会话在本轮结束的安全点应用，不提前关闭正在使用的会话。
15. **会话实时状态是运行时事件的纯投影**：主聊天页、系统助手浮层等任何 surface 都用同一个 `reduce` 与 `SessionProjection` 从 `runtime.events` 还原当前会话；`RunStarted` 对用户轮次携带原文 prompt，界面不得假设「这一轮由自己发起」。界面外壳只保留输入、滚动、可见性等本地状态；消息/过程/工具渲染共用同一套组件。
16. **shell 工具统一执行管线**：proot Alpine 与 Android root 是两个命名空间，不合并运行环境；但 `root_shell` 复用 SDK `bash` 工具的执行、截断与格式化管线，两者输出契约一致（截断提示 + 退出码）。
