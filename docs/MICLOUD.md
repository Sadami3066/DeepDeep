# 小米云服务

状态：插件 `micloud`（「小米云服务」/ Xiaomi Cloud）已实现；登录、读取与笔记/待办写入通过 i.mi.com 网页接口。

## 登录

- 插件详情页「登录小米账号」打开 App 内 WebView，加载 `https://i.mi.com`；用户在页面完成登录。
- 登录成功后读取 `CookieManager` 的 i.mi.com cookie，先用 `/status/lite/profile` 校验，再整串加密存入 `EncryptedSecretStore`（前缀 `micloud.`）。
- 不保存账号密码；不显示、不记录、不回传 `serviceToken`。
- cookie 失效（401）时状态变为「登录已失效」，需要重新登录；退出登录会清除加密 cookie 与 WebView cookie。

### 登录方式评估

- **外部浏览器**：Chrome/系统浏览器的 cookie 与 App 的 `CookieManager` 隔离，App 读不到登录态；小米 OAuth 需要在小米开放平台注册应用与 redirect URI，本项目没有该 client。因此纯浏览器登录无法让插件读取云数据。
- **系统账号接口**：设备存在 `com.xiaomi.account` 的 `com.xiaomi` 账号与 Authenticator，但 auth token 受 `com.xiaomi.permission.AUTH_SERVICE` 等签名级权限保护；没有公开的 MiCloud SDK/本地接口。第三方 App 无法直接取 token。
- **WebView**：仍是唯一能拿到 i.mi.com cookie 的路径，作为登录入口。真机（Android 13 / MIUI 14）上出现过页面 DOM 已加载但 CSS 视口高度为 0、内容不可见的问题：WebView 在 Compose 中于 0×0 时首次加载，CSS 的 `100vh`/初始包含块高度被固定为 0。现在改为等 WebView 有非零尺寸后再加载，并在页面完成后探测 `100vh`，仅在探测到 0 时注入 `min-height` 修正；正常内核不受影响。i.mi.com 与 account.xiaomi.com 均已真机截图验证可见、可输入。

## 功能开关

所有能力默认关闭。插件详情页内嵌功能开关；只有打开对应读开关，工具才会注入新会话。

| 功能 | 读 | 写 | 对应工具 |
|---|---|---|---|
| 账号与空间 | 开关 | — | `mi_cloud_account` |
| 笔记 | 开关 | 开关 | `mi_cloud_notes` |
| 待办 | 开关 | 开关 | `mi_cloud_todo` |
| 短信与通话 | 开关 | — | `mi_cloud_sms` |
| 联系人 | 开关 | — | `mi_cloud_contacts` |
| 相册 | 开关 | — | `mi_cloud_gallery` |
| 录音 | 开关 | 下载到本地 | `mi_cloud_recordings` |

- 写开关只在笔记/待办出现；关闭读会同时关闭写。
- 工具在 execute 内再次检查开关，旧会话不能绕过。
- 开关变化会重建会话工具目录；已打开且正在运行的会话不会被中途改写。

## 工具

| 工具 | 动作 |
|---|---|
| `mi_cloud_account` | `profile`、`quota` |
| `mi_cloud_notes` | `list`、`read`、`create`、`update`、`delete` |
| `mi_cloud_todo` | `list`、`create`、`update`、`delete`（update 可改标题、完成状态、提醒时间） |
| `mi_cloud_sms` | `list`（会话消息，可选包含通话记录） |
| `mi_cloud_contacts` | `list`、`search` |
| `mi_cloud_gallery` | `list`、`albums` |
| `mi_cloud_recordings` | `list`、`download` |

- 列表结果返回分页游标（`syncTag`、`syncToken`、`offset`、`lastPage` 等）；不要一次拉全量。
- 录音下载到 `files/micloud/recordings/`，Linux 内路径为 `/micloud/recordings/`。
- 写操作只支持笔记与待办；执行前必须向用户确认，说明会直接写入小米云账号。

## 读写机制

- 读：`HttpURLConnection` 带 Cookie 请求 `i.mi.com`，`ts` 防缓存；401/403 视为登录失效。
- 写：只能走 WebView 同源 `fetch`；脚本直连会 401。隐藏 WebView 加载 `https://i.mi.com`，所有 POST form body 都带 `serviceToken`；串行执行、30 秒超时。
- 笔记写入按与待办相同的 POST 结构实现，但网页端未单独验证；失败时工具会返回错误，不静默当作成功。

## 覆盖范围

已覆盖：账号资料与空间、笔记、待办、短信与通话、联系人、相册列表、录音列表与下载。

不覆盖：

- 小米日历：没有可用的公开接口；日程继续用系统日历/Google Calendar/CalDAV。
- 小米云盘、浏览器书签、WLAN、手机管家、常用语、蓝牙/智能助理：没有可用接口。
- 云备份：需要网页端 `sign`，未接入。
- 密码管家：端到端加密，不可读取。
- 相册下载：文档没有给出可验证的下载接口，只提供列表/分组。

## 风险与隐私

- i.mi.com 是非公开接口，字段和服务可能变化；不能把它当作稳定 SDK。
- 写操作风控敏感；连续失败可能短暂影响读接口。不要高频轮询或批量写入。
- 短信、联系人、录音、相册属于敏感个人数据；只按用户明确要求读取，不要主动扩大范围。
- 读取内容会进入会话历史，并可能随对话发送给模型厂商；隐私页已说明。
- 卸载插件会删除加密 cookie、功能开关和隐藏 WebView 状态；建议同时在小米账号中管理登录设备。
