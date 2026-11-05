# plugin/micloud · 小米云服务插件

小米云插件（外部）：WebView 登录 i.mi.com 拿 cookie 后加密保存，用 `mi_cloud_*` 工具读取账号、短信、联系人、相册与录音，读写笔记与待办；工具是否出现受插件详情页功能开关控制，写操作还要单独打开写开关。

## 文件

| 文件 | 职责 |
|---|---|
| `MiCloudPlugin.kt` | 插件入口：登录状态机、动作、详情行、技能/promptBody 与工具装配 |
| `MiCloudSession.kt` | 登录态缓存（SIGNED_OUT/VERIFIED/EXPIRED/UNVERIFIED）与 TTL、cookie 指纹失效 |
| `MiCloudClient.kt` | 只读 HTTP 客户端：带 cookie 请求 i.mi.com、401/403 判定失效、JSON 与文件下载 |
| `MiCloudWebWriter.kt` | 写操作走隐藏 WebView 的 JS fetch（复刻浏览器环境），表单 POST 与失败判定 |
| `MiCloudLoginActivity.kt` | 小米账号 WebView 登录页：主题/语言适配、cookie 捕获与验证 |
| `MiCloudCookieStore.kt` | cookie 加密存取（EncryptedSecretStore）并解析 userId/serviceToken |
| `MiCloudPreferences.kt` | 功能开关（读写能力）DataStore 与 MiCloudSettings 接口 |
| `MiCloudFeatureSection.kt` | 插件详情页的「功能」开关区 Composable |
| `MiCloudTools.kt` | 按能力开关组装 `mi_cloud_*` 工具列表（含 account） |
| `MiCloudToolSupport.kt` | 工具公共设施：参数校验、结果截断、错误包装、JSON 压缩、路径拼接 |
| `MiCloudNotesTools.kt` | `mi_cloud_notes`：列表、正文、新建、修改、删除 |
| `MiCloudTodoTools.kt` | `mi_cloud_todo`：列表、新建、修改（含完成态）、删除 |
| `MiCloudMessagesTools.kt` | `mi_cloud_sms`：短信会话与通话记录列表（只读） |
| `MiCloudContactsTools.kt` | `mi_cloud_contacts`：联系人列表与搜索（只读） |
| `MiCloudMediaTools.kt` | `mi_cloud_gallery` 相册/媒体列表与 `mi_cloud_recordings` 录音列表、下载 |
| `MiCloudParsers.kt` | 响应解析：profile、笔记、待办与 cookie/鉴权失败判定 |

## 要点

- 登录 = `MiCloudLoginActivity` 的 WebView 页面捕获 cookie 后加密存入 EncryptedSecretStore；状态由 `MiCloudSession` 缓存（验证 60s TTL、失效 10 分钟内不重复打接口）。
- 读走 `MiCloudClient`（HttpURLConnection + cookie），写（笔记/待办）必须走 `MiCloudWebWriter` 的隐藏 WebView JS fetch——小米云写接口校验浏览器环境，直接 HTTP POST 会失败。
- 工具按 `MiCloudPreferences` 的能力开关动态出现：读开关关掉就不注册该工具；笔记/待办写还要各自写开关，写操作前 agent 必须先向用户确认，删除/批量改不可逆。
- 短信、联系人、录音、相册只按开关和用户要求读取；401/403 统一标记会话过期并提示到插件详情页重新登录，不要重试刷接口。
