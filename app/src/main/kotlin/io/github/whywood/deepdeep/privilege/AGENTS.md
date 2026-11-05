# privilege · 权限三档

普通（只有 proot bash）/ 无线调试 ADB / Root 三档特权的解析与控制器。区分「用户选择」与「实际生效」两套值，按生效档位注册 shell 工具，并负责把 DeepDeep 无障碍服务注入系统设置。

## 文件

| 文件 | 职责 |
|---|---|
| `PrivilegeControllerImpl.kt` | 控制器：读写档位、探测 Root、连接 ADB、通知变更 |
| `PrivilegeContracts.kt` | 档位、状态与控制器接口 |
| `PrivilegeResolution.kt` | 可用后端、生效档位与旧值迁移的纯解析 |
| `AccessibilityBootstrap.kt` | 无障碍注入计划与系统设置命令拼装 |
| `AccessibilityBootstrapRunner.kt` | 通过 Root/ADB 执行无障碍注入并重试 |

## 要点

- `selected` 是用户选择，`effective` 是按实际可用后端降级后的结果：shell 工具注册看 `effective`；虚拟屏还要求用户选了非普通档且此时存在可用后端。
- ADB 与 Root 互备：选 ADB 但 daemon 不在线而 Root 可用会降到 Root，反之亦然；可用后端只由 daemon 在线与 Root 已授权决定。
- 有可用特权时自动把无障碍服务合并进系统启用列表；ADB 升级或重装后需重新连接一次才能恢复后端。
