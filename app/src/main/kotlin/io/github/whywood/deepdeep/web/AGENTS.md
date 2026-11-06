# web · 网络工具

`web_search` / `web_fetch` 的 App 自带工具实现与 DeepSeek web provider；工具在 `graph/RuntimeGraph` 的 `AppToolContext` 装配，API Key 取 `ProviderCatalog` 默认厂商（DeepSeek）的凭据缓存。

## 文件

| 文件 | 职责 |
|---|---|
| `WebTools.kt` | 两个 `CodingTool` 的参数 schema、结果格式化与外部内容提示 |
| `WebProvider.kt` | provider 契约与结果模型（搜索条数、抓取字符上限） |
| `DeepSeekWebProvider.kt` | 走 DeepSeek tools API 的搜索与网页抓取实现，超时与错误映射 |

## 要点

- 工具结果里的网页内容是外部数据，固定附加「不可当指令」提示。
- 缺 API Key 时工具直接返回错误，不静默回退；密钥只从凭据存储读取。
