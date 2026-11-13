# market · 市场数据层

远程签名索引的拉取、验签、解析、缓存与回退，以及把插件 / 技能 / 连接器三类条目安装到现有子系统。界面在 `ui/common/market/`，三个内容页头部提供市场刷新。

| 文件 | 职责 |
|---|---|
| `MarketModels.kt` | 条目、元信息（含 `requiredAccess` 权限档位）、状态与安装状态模型 |
| `MarketDefaults.kt` | 官方源地址、公钥、条数与体积上限、缓存时长 |
| `MarketSignature.kt` | 对 `registry.json` 原始字节做 ECDSA P-256 SHA-256 验签 |
| `MarketRegistryParser.kt` | schema v2/v3 解析与字段校验（id 唯一、sha256、体积、MCP 仅 http；`requiredAccess` 缺失或非法按 normal） |
| `MarketCompatibility.kt` | 插件条目安装门禁纯函数：`apiLevel` 低于 `MIN_SUPPORTED_API_LEVEL` 为插件过旧、高于宿主 level 或 `minHostVersion` 高于 `BuildConfig.VERSION_NAME` 为需要更新 App |
| `MarketRepository.kt` | 状态、安装、已安装版本查询与安装状态重算接口 |
| `MarketRepositoryImpl.kt` | 网络拉取 + 缓存/种子回退、三类安装流水线、安装状态汇总 |
| `MarketInstallStates.kt` | 已安装内容快照与条目安装状态的纯计算结果 |

## 要点

- 官方源固定（`MarketDefaults.PAGE_BASE_URL`），不提供自定义源。
- 签名对象是 `registry.json` 原始字节，签名文件 `registry.json.sig` 为 DER base64；公钥内嵌，keyId 需匹配。
- schema 3 起 `name/summary/description` 为 `{zh,en}` 本地化对象、`tags` 为 `{zh,en}` 数组；parser 同时兼容 schema 2 的纯文本写法。
- 连接器条目可带 `mcp.auth`：`none` / `apiKey`（header+prefix+placeholder+helpUrl）/ `oauth`；API Key 令牌由安装时传入，`resolvedHeaders()` 负责拼头。
- 插件条目可带 `plugin.requiredAccess`：`normal` / `adb` / `root`，声明运行所需的系统访问档位；缺失或非枚举值按 `normal`，兼容 schema 2/3。客户端按 effective `HostAccess` 显示标签与权限不足提示，内容仓库 validate 脚本校验枚举。
- 限额：registry ≤ 2MB、条目 ≤ 512、插件 ≤ 50MB、技能 ≤ 20MB、图标 ≤ 128KB；MCP 仅 http。
- 离线回退顺序：网络 → 本地缓存（`files/market`）→ 内置种子（`assets/market`）。
- 安装门禁：插件条目安装前按 `pluginCompatibility` 判定，不兼容时 `install()` 直接置 `Failed`（文案展示所需宿主版本）且条目保持不可安装；技能与连接器不受影响。
- 安装：插件走 `PluginManagerFacade.inspectExternal` + `installExternal`；技能走 `SkillRepository.install` + 启用 + `syncActiveSkills`；连接器走 `McpConfigStore.upsert`。
- 安装身份：连接器按 URL 判定是否已安装（不看名字），同名不同 URL 时安装生成 `id-2` 这类不冲突的名字，同 URL 已存在则复用原名字更新，不静默覆盖用户条目；技能安装版本记录在 `files/market/skills.json`（市场层 sidecar，不改 SKILL.md），与市场版本比较得出 `UpdateAvailable`。
- 安装状态：`refreshInstalledStates()` 只重算已安装状态、不联网；插件状态、技能变更与连接器配置变更（`McpConfigStore.changes`）都会自动触发重算，市场页进入时也会重算；安装成功后状态由重算结果派生，不无条件写 `Installed`，插件还会确认 `PluginSnapshot.installedVersion` 非空；`Installing` 在重算中保留，`Failed` 重算回可重试状态。
- 刷新不自动安装：`refresh()` 在缓存 TTL 内不重复联网，完成后只重算已安装状态并通过 `MarketInstallStates` 标记 `UpdateAvailable`；插件/技能/连接器的更新都由用户在条目上手动点按安装，刷新图标只负责拉索引与刷新状态。插件更新安装后进入待更新态，需在插件详情点「立即更新」或下次进程启动时激活。
- 内容仓库与签名脚本在独立仓库 `deepdeep-market`，不在本仓库。
