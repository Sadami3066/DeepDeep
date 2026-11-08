# arrange · 系统日历

`calendar` 的 App 自带工具实现：直接读写系统日历与提醒；工具在 `graph/RuntimeGraph` 的 `AppToolContext` 装配。

## 文件

| 文件 | 职责 |
|---|---|
| `ArrangeTools.kt` | `calendar` 工具：参数解析、动作分发与 JSON 结果 |
| `ArrangeCalendar.kt` | 系统日历网关（ContentResolver 实现）与事件/补丁/范围模型 |
| `ArrangeCalendarWriter.kt` | 创建/更新事务与提醒回滚 |
| `ArrangeRequests.kt` | 时间解析/格式化与文本长度限制 |

## 要点

- 读写都要求 READ_CALENDAR/WRITE_CALENDAR；缺权限返回错误，提示用户在系统设置 → 应用 → DeepDeep 的权限页授予（无应用内授权页）。
- 修改类操作先用 list/get 拿 id；提醒写入失败时回滚事件或恢复旧提醒。
- 时间支持 `yyyy-MM-dd HH:mm`、`yyyy-MM-dd`、ISO 8601 与毫秒；全天事件按 UTC 午夜存储。
