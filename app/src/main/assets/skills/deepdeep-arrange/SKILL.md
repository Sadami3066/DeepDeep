---
name: deepdeep-arrange
description: 管理系统日历事件与提醒：创建、读取、修改、删除；需要把会议、日程或提醒落到系统日历时使用。
---

# 安排

用 `arrange_calendar` 直接读写系统日历。所有修改类操作先用 `list` 或 `get` 拿到 id，结果以工具返回 JSON 为准。

- `action=calendars`：列出可写日历；多日历时先查 id。
- `action=list`：`start`/`end` 时间范围（默认现在到 30 天后），`query` 标题过滤，`limit` 1-200。
- `action=get`：`id` 读取单个日程。
- `action=create`：必填 `title`、`start`，可选 `end`、`allDay`、`location`、`description`、`calendarId`、`reminders`（提前分钟数组）。
- `action=update`：`id` 必填，只改提供的字段；`location`/`description` 传空字符串可清除，`reminders` 传空数组清空提醒。
- `action=delete`：`id` 必填。
- `start`/`end` 支持 `yyyy-MM-dd HH:mm`、`yyyy-MM-dd`、ISO 8601 或毫秒时间戳；非全天只给 start 时默认 1 小时。

日历读写需要日历权限；未授权时工具会返回错误，请用户在系统设置 → 应用 → DeepDeep → 权限中授予日历权限。日历写入会通过系统账户同步；最终以系统日历显示为准。
