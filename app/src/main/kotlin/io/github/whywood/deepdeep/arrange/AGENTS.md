# arrange · 系统日历

`calendar` 的 App 自带工具实现：直接读写系统日历与提醒；工具在 `graph/RuntimeGraph` 的 `AppToolContext` 装配。

## 文件

| 文件 | 职责 |
|---|---|
| `ArrangeTools.kt` | `calendar` 工具：参数解析、动作分发与 JSON 结果 |
| `ArrangeCalendar.kt` | 系统日历网关（ContentResolver 实现）、`CalendarPermissionGate` 与事件/补丁/范围模型 |
| `CalendarPermissionActivity.kt` | 透明权限申请 Activity：拉起系统权限弹窗并回传结果 |
| `CalendarPermissionBridge.kt` | 权限申请挂起等待：超时或启动失败返回未授权 |
| `ArrangeCalendarWriter.kt` | 创建/更新事务与提醒回滚 |
| `ArrangeRequests.kt` | 时间解析/格式化与文本长度限制 |

## 要点

- 读写都要求 READ_CALENDAR/WRITE_CALENDAR；首次缺权限时由 `CalendarPermissionActivity` 在应用内直接申请，拒绝后返回错误并提示授权后重试（同一网关实例不重复弹窗）。
- 修改类操作先用 list/get 拿 id；提醒写入失败时回滚事件或恢复旧提醒。
- 时间支持 `yyyy-MM-dd HH:mm`、`yyyy-MM-dd`、ISO 8601 与毫秒；全天事件按 UTC 午夜存储。
