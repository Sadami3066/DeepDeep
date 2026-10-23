# 定时任务设计

状态：设计稿，本轮不实现。执行模型已定为“前台服务存活时调度 + 下次打开补跑”。

## 目标

让用户把一段提示词绑定到工作区，按计划自动运行，并在完成后收到通知。

不做：系统级精确唤醒、后台常驻 Worker、由模型自主创建任务（后续）。

## 数据模型

```text
Schedule
  id: String
  name: String
  workspaceId: String
  prompt: String
  trigger: Trigger
  enabled: Boolean
  createdAt: Long
  updatedAt: Long
  lastRunAt: Long?
  lastStatus: DONE | STOPPED | FAILED | null
  lastSessionId: String?
  nextRunAt: Long
  notify: Boolean = true

Trigger
  Daily(time: LocalTime)
  Weekly(days: Set<DayOfWeek>, time: LocalTime)
  Interval(minutes: Int)          // 最短 15 分钟
  Cron(expression: String)        // 标准 5 段：分 时 日 月 周

RunRecord
  id: String
  scheduleId: String
  startedAt: Long
  finishedAt: Long?
  status: DONE | STOPPED | FAILED
  sessionId: String?
  error: String?
```

存储：

- `files/schedules/schedules.json`，原子写（临时文件 + rename）。
- 顶层带 `schemaVersion`，读取失败时保留原文件并提示，不静默覆盖。
- 每个任务保留最近 20 条 `RunRecord`；完整对话历史仍由会话文件承担。
- 任务与运行记录都不含 API Key；提示词按本地明文保存。

## 调度

- 调度器在 DeepDeep 前台服务进程内运行，30 秒 tick。
- 每次 tick 取 `enabled && nextRunAt <= now` 的任务，串行执行。
- 同一任务重叠时跳过本次，不并发；不同任务可排队。
- 错过执行：
  - 服务存活但设备休眠导致 tick 延迟：恢复后补跑一次。
  - 服务被杀/进程退出：不唤醒；下次打开 App 时若 `nextRunAt` 已过，补跑一次。
  - 多个周期都错过：只补一次，不连发；用 `lastRunAt` 去重。
- 时钟/时区变化：重新计算 `nextRunAt`；已过期按补跑规则处理。
- 不使用 AlarmManager、WorkManager、BOOT_COMPLETED、精确闹钟权限。

## 执行

1. 校验任务仍存在、工作区未删除、模型已配置。
2. `runtime.newSession(workspaceId)` 创建新会话（不重用旧会话，避免上下文污染）。
3. 用任务名作为会话标题提示，`runtime.send(sessionId, prompt)`。
4. 监听该 session 的 `RunFinished` / `RunFailed`，记录 RunRecord；写入 `lastRunAt/lastStatus/lastSessionId/nextRunAt`。
5. App 在后台时发完成/失败通知；点击通知打开对应会话。
6. 若任务被用户停止，记录 STOPPED，不重复补跑同一时间点。

约束：执行依赖 DeepDeep 前台服务和已配置模型；不保证准点。任务运行时占用一个会话槽位，用户仍可正常使用其他会话。

## UI 草案

- 抽屉「定时任务」→ 任务列表：
  - 每行：名称、工作区、下次运行时间、启用开关、最近状态。
  - 顶部 `+` 新建；空态说明“服务存活时运行，错过会在下次打开时补跑”。
- 编辑器：
  - 名称、工作区选择、提示词多行输入。
  - 触发方式：每天 / 每周 / 间隔 / 自定义 cron；显示“下次运行”。
  - 启用开关、保存、删除。
- 任务详情：最近 20 次运行记录（时间、状态、会话入口）、立即运行、编辑、删除。
- 所有操作本地生效，不需要额外权限。

## 失败处理

| 情况 | 处理 |
|---|---|
| 模型未配置 | 不创建会话，记录 FAILED，通知“未配置模型” |
| 工作区已删除 | 禁用任务并提示，不自动改到 Home |
| 会话创建/发送失败 | 记录 FAILED，保留错误摘要 |
| 服务被杀 | 本次跳过；下次打开补跑一次 |
| 同一任务重叠 | 跳过并记录 skipped，不弹通知 |
| JSON 损坏 | 保留原文件，UI 提示，不覆盖 |
| 时间回拨 | 重算 nextRunAt；不重复跑同一时间点 |

## 后续

- AlarmManager/WorkManager 提升可靠性（需要重新评估权限和后台限制）。
- 模型通过工具创建/修改任务。
- 与「交互体验：主动」联动，生成建议任务而不是自动执行。
- 运行结果摘要、任务级通知开关、Wi-Fi/充电条件。

## 验收（实现时）

- 新建每天任务，服务存活时按计划运行一次；重启 App 后补跑一次，不重复。
- 任务运行中再次到点不重叠。
- 删除工作区后任务禁用且不崩溃。
- 后台完成通知可打开对应会话。
- JSON 损坏时不丢数据。
