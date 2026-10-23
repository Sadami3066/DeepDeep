# 定时任务设计

状态：工具、本地存储、前台服务调度、任务页与隐藏工作区均已实现。

## 已实现

- `files/schedules/schedules.json`，`schemaVersion` 1，原子写；损坏时保留原文件并报错。
- 触发：一次性、每日、工作日、周末、每周指定星期；使用设备本地时区。
- `nextRunAt` 在创建/更新/执行后计算；一次性时间必须是未来。
- 工具：`schedule_create`、`schedule_list`、`schedule_update`、`schedule_delete`；结果带 `execution: "enabled"`。
- `ScheduleRunner` 在 `AgentService` 内 30 秒 tick；执行后记录 `lastRunAt/lastStatus/lastSessionId/lastError`。
- 定时任务页：列表、启用开关、下次运行、详情、删除、打开运行会话、安排记录；`+` 打开隐藏工作区的安排会话。
- 聊天右上角「重新加载」可让当前会话重新读取技能/AGENTS.md，不需要新建会话。

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
  lastRunAt: Long?
  lastStatus: RUNNING | DONE | STOPPED | FAILED?
  lastSessionId: String?
  lastError: String?

Trigger
  Once(date: LocalDate, time: LocalTime)
  Daily(time: LocalTime)
  Weekly(days: Set<DayOfWeek>, time: LocalTime)
```

不保存 API Key；提示词按本地明文保存。

## 隐藏工作区

- 工作区 id `schedules`，cwd `files/schedules/workspace`，`hidden = true`。
- 抽屉、搜索、启动恢复、普通会话列表都不展示隐藏工作区及其会话。
- 定时任务页的 `+` 在该工作区新建一个「定时任务会话」；用户与 agent 对话，通过 schedule 工具安排任务。
- 控制会话与执行会话分离：任务保存目标 `workspaceId`；执行时在目标工作区新建会话，不混入控制会话。
- 未指定目标工作区时默认 Home。
- 目标工作区删除后任务记录失败，不自动改到 Home。
- 隐藏工作区本身不做文件执行，只承担控制面。

## 执行

执行模型是“前台服务存活时调度 + 下次打开补跑”：

- 调度器在 `AgentService` 进程内运行，30 秒 tick。
- 每次 tick 取 `enabled && nextRunAt <= now` 的第一条任务，串行执行；同一任务重叠跳过。
- 标记 RUNNING 后新建目标工作区会话并发送 prompt；收到 `RunFinished/RunFailed` 后写回状态与 nextRunAt。
- 一次性任务执行后禁用；周期任务按触发规则计算下一次。
- 错过：服务存活但设备休眠导致 tick 延迟时恢复后补跑一次；进程退出则不唤醒，下次打开补跑一次；多个周期只补一次。
- 时钟/时区变化重算 `nextRunAt`；已过期按补跑规则处理。
- 不使用 AlarmManager、WorkManager、BOOT_COMPLETED 或精确闹钟权限。
- 完成/失败沿用运行时会话通知；App 在后台时发送完成通知。
- 任务运行中占用一个会话槽位，用户仍可使用其他会话。

## 失败

| 情况 | 处理 |
|---|---|
| 模型未配置 | 不创建会话，记录失败，通知 |
| 工作区已删除 | 记录失败并保留任务 |
| 会话创建/发送失败 | 记录失败与错误摘要 |
| 服务被杀 | 本次跳过，下次打开补跑一次 |
| 同一任务重叠 | 跳过，不并发 |
| JSON 损坏 | 保留原文件，工具/页面报错 |
| 时间回拨 | 重算 nextRunAt，不重复跑同一时间点 |

## UI

- 抽屉「定时任务」→ 列表：名称、触发、工作区、下次运行、启用开关。
- `+` 进入隐藏工作区的安排会话；安排记录可重新打开。
- 详情：触发、工作区、下次/上次运行、状态、错误、prompt、打开运行会话、删除。
- 不提供独立表单编辑器；创建和修改通过安排会话里的 schedule 工具完成。

## 验收

- 新建每天任务，服务存活时按计划运行一次；重启 App 后补跑一次，不重复。
- 一次性任务运行后自动禁用，`lastStatus=DONE`，`lastSessionId` 可打开。
- 任务运行中再次到点不重叠。
- 删除工作区后任务记录失败且不崩溃。
- 后台完成通知可打开对应会话。
- JSON 损坏时不丢数据。
