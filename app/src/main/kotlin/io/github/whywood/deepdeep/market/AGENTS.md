# market · 市场数据层

远程签名索引的拉取、验签、解析、缓存与回退，以及把插件 / 技能 / 连接器三类条目安装到现有子系统。界面在 `ui/common/market/` 与 `ui/settings/MarketSettingsScreen.kt`。

| 文件 | 职责 |
|---|---|
| `MarketModels.kt` | 条目、元信息、状态与安装状态模型 |
| `MarketDefaults.kt` | 官方源地址、公钥、条数与体积上限、缓存时长 |
| `MarketSignature.kt` | 对 `registry.json` 原始字节做 ECDSA P-256 SHA-256 验签 |
| `MarketRegistryParser.kt` | schema v2 解析与字段校验（id 唯一、sha256、体积、MCP 仅 http） |
| `MarketRepository.kt` | 状态、安装与已安装版本查询接口 |
| `MarketRepositoryImpl.kt` | 网络拉取 + 缓存/种子回退、三类安装流水线、安装状态汇总 |

## 要点

- 官方源固定（`MarketDefaults.PAGE_BASE_URL`），不提供自定义源。
- 签名对象是 `registry.json` 原始字节，签名文件 `registry.json.sig` 为 DER base64；公钥内嵌，keyId 需匹配。
- schema 3 起 `name/summary/description` 为 `{zh,en}` 本地化对象、`tags` 为 `{zh,en}` 数组；parser 同时兼容 schema 2 的纯文本写法。
- 连接器条目可带 `mcp.auth`：`none` / `apiKey`（header+prefix+placeholder+helpUrl）/ `oauth`；API Key 令牌由安装时传入，`resolvedHeaders()` 负责拼头。
- 限额：registry ≤ 2MB、条目 ≤ 512、插件 ≤ 50MB、技能 ≤ 20MB、图标 ≤ 128KB；MCP 仅 http。
- 离线回退顺序：网络 → 本地缓存（`files/market`）→ 内置种子（`assets/market`）。
- 安装：插件走 `PluginManagerFacade.inspectExternal` + `installExternal`；技能走 `SkillRepository.install` + 启用 + `syncActiveSkills`；连接器走 `McpConfigStore.upsert`。
- 内容仓库与签名脚本在独立仓库 `deepdeep-market`，不在本仓库。
