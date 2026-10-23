# 定时任务设计

状态：schedule 工具与本地落库已实现；调度执行器、任务列表/编辑页、隐藏工作区代码未实现。

## 已实现

- `files/schedules/schedules.json`，`schemaVersion` 1，原子写；损坏时保留原文件并报错。
- 触发：一次性、每日、工作日、周末、每周指定星期；使用设备本地时区。
- `nextRunAt` 在创建/更新时计算；一次性时间必须是未来。
- 工具：`schedule_create`、`schedule_list`、`schedule_update`、`schedule_delete`。
- 工具结果带 `execution: "not_enabled"`：任务会被保存，但本版本不会自动运行。

## 数据

```text
Schedule
  id: String
  name: String
  prompt: String
  workspaceId: String
  trigger: Trigger
  enabled: Boolean
  notify: Boolean
  createdAt: Long
  updatedAt: Long
  nextRunAt: Long?

Trigger
  Once(date: LocalDate, time: LocalTime)
  Daily(time: LocalTime)
  Weekly(days: Set<DayOfWeek>, time: LocalTime)
```

不保存 API Key；提示词按本地明文保存。

## 隐藏工作区（设计）

- 保留工作区 id `schedules`，cwd `files/schedules/workspace`，`hidden = true`。
- 抽屉、搜索、启动恢复、普通会话列表都不展示隐藏工作区及其会话。
- 定时任务页（后续）的 `+` 在该工作区新建一个「定时任务会话」；用户与 agent 对话，通过 schedule 工具安排任务。
- 控制会话与执行会话分离：任务保存目标 `workspaceId`；执行时在目标工作区新建会话，不混入控制会话。
- 目标工作区删除后任务禁用，不自动改到 Home。
- 隐藏工作区本身不做文件执行，只承担控制面。

## 执行（后续）

执行模型沿用“前台服务存活时调度 + 下次打开补跑”：

- 调度器在 `AgentService` 进程内运行，30 秒 tick。
- 每次 tick 取 `enabled && nextRunAt <= now`，串行执行；同一任务重叠跳过。
- 错过：服务存活时恢复后补跑一次；进程退出则不唤醒，下次打开补跑一次；多个周期只补一次。
- 时钟/时区变化重算 `nextRunAt`；已过期按补跑规则处理。
- 不使用 AlarmManager、WorkManager、BOOT_COMPLETED 或精确闹钟权限。
- 执行时新建会话，使用任务 prompt；完成/失败写回状态并发通知（App 在后台时）。
- 任务运行中占用一个会话槽位，用户仍可使用其他会话。

## 失败

| 情况 | 处理 |
|---|---|
| 模型未配置 | 不创建会话，记录失败，通知 |
| 工作区已删除 | 禁用任务并提示 |
| 会话创建/发送失败 | 记录失败与错误摘要 |
| 服务被杀 | 本次跳过，下次打开补跑一次 |
| 同一任务重叠 | 跳过，不并发 |
| JSON 损坏 | 保留原文件，工具/页面报错 |
| 时间回拨 | 重算 nextRunAt，不重复跑同一时间点 |

## UI（后续）

- 抽屉「定时任务」→ 列表：名称、工作区、下次运行、启用开关、最近状态。
- `+` 进入隐藏工作区的新定时任务会话。
- 编辑器：名称、目标工作区、prompt、触发方式、启用/通知、删除。
- 详情：最近运行记录、立即运行、编辑、删除。

## 验收（实现执行器时）

- 新建每天任务，服务存活时按计划运行一次；重启 App 后补跑一次，不重复。
- 任务运行中再次到点不重叠。
- 删除工作区后任务禁用且不崩溃。
- 后台完成通知可打开对应会话。
- JSON 损坏时不丢数据。
