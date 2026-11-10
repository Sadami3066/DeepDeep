# host · 宿主访问环境

「宿主访问方式」的 app 层骨架：用户只选本地 / ADB / Root 三档，界面、工具与文件能力统一从 `HostEnvironment` 派生，不再按 Root 与否散落条件分支。

## 文件

| 文件 | 职责 |
|---|---|
| `HostAccessPreferences.kt` | `host_access` DataStore 读写选中的访问方式；首次读取时从旧的 `privilege_settings` 迁移 |
| `AppHostAccessController.kt` | `HostAccessController` 实现：组合选择与探测结果，产出 `HostAccessState`，写选择并通知变更 |
| `HostEnvironments.kt` | 本地 / ADB / Root 三个 `HostEnvironment` 实现、Root 准备状态与提示词 |
| `AppHostEnvironmentProvider.kt` | 按 `effective` 返回当前 `HostEnvironment` |

## 要点

- 降级规则只有一份：`runtime/host/HostAccess.kt` 的 `resolveHostAccess`，这里直接调用，不复制判断。
- `refresh()` 只重新探测 Root；ADB 是否可用以注入的 `adbStorage` 为准。
- `select()` 先取变更前状态，再写偏好并探测 Root，仅在 `effective` 或 Root 就绪状态变化时调用 `onChanged()`。
- Root 环境在准备中或失败时 `bash()` / `files` / `system` 抛 `HostAccessUnavailableException`；`effective` 已可能是 ROOT，调用方需提示可重试。
- `promptLines` 是给模型的英文环境说明，可留在本包常量里，不走 `res` 文案。
