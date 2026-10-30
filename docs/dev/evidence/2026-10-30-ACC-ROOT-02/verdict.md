# ACC-ROOT-02 验收

- 条目 ID：ACC-ROOT-02
- 旧编号：新增（M7）
- 日期：2026-10-30
- 设备：Redmi 22101316UCP / Android 13 (MIUI V140)
- 截图规格：720p 长边
- 判定：通过
- 现象：聊天中让模型调用 root_shell 执行 id，返回 exitCode 0、uid=0(root)、context=u:r:magisk:s0；命令仅在 App 前台执行；timeout_ms=2000 执行 sleep 4242 时，内层 `timeout -s KILL` 在 2 秒后杀掉进程（真机复查无残留 sleep）。
- 截图：01-uid0.png、02-timeout-kill.png
