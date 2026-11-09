# remote · 远程连接

连接并操作电脑端 DSH。手机负责连接管理：电脑列表、扫码或粘贴配对、凭据与证书固定、状态探测、重命名/重新配对/删除，以及承载电脑页面的连接页。

## 文件

| 文件 | 职责 |
|---|---|
| [RemoteScreen.kt](RemoteScreen.kt) | 电脑列表：顶栏返回与添加电脑菜单（扫码/粘贴/使用教程）、空态、电脑图标与状态圆点、行尾管理菜单 |
| [RemoteStateHolder.kt](RemoteStateHolder.kt) | 列表页状态与动作：刷新、扫码/粘贴、一步配对（记录配对来源）、重命名、删除 |
| [RemoteDialogs.kt](RemoteDialogs.kt) | 配对中、连接失败、粘贴链接、使用教程、重命名、删除对话框 |
| [RemoteScanSheet.kt](RemoteScanSheet.kt) | 扫码配对 sheet：相机识别、权限提示、粘贴链接回退 |
| [RemoteConnectScreen.kt](RemoteConnectScreen.kt) | 连接页：探测与续期、WebView 承载电脑页面、证书校验、失败重试 |
| [RemoteInjection.kt](RemoteInjection.kt) | 连接页注入脚本：DeepDeep 手机样式与页面适配 |
| [RemoteTexts.kt](RemoteTexts.kt) | 状态与失败原因的文案映射；配对失败区分「需要重新生成配对码」与可重试错误 |

## 要点

- 电脑端零代码：复用电脑端 dsh-mobile 插件提供的 HTTPS 网关、配对与设备管理；这里不实现会话协议，也不改电脑端文件。
- 配对链接与服务端响应全部经 `data/remote/` 解析与校验；页面只收状态与回调，动作交给 `RemoteStateHolder`。
- 连接页只允许访问已配对电脑的 origin；证书变化时失败关闭，提示重新配对，不静默降级。
- WebView 注入只影响本 App 的页面；电脑端与手机浏览器不受影响。
- 一次只连一台电脑；不做后台多连与分屏。
- 同一台电脑的局域网/远程入口合并成一行；局域网不通时 `data/remote` 会自动尝试远程入口。
- 配对码无效/过期/被拒时提示用户重新扫码或粘贴新链接，只有网络/证书/响应异常类失败才提供「重试」；扫码与粘贴输入框的状态用 `rememberSaveable` 保持。
