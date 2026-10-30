# App Store artwork notice (feishu / wecom / wechat)

`upstream/feishu-appstore-512.jpg`、`upstream/wecom-appstore-512.jpg`、`upstream/wechat-appstore-512.jpg`
来自 Apple App Store 的公开应用图标（iTunes Search API 的 `artworkUrl512`，中国区，2026-10-29 下载）。

| 图标 | App 名称 | 开发者 | 下载 URL |
|---|---|---|---|
| feishu | 飞书 - 字节跳动旗下 AI 工作平台 | Beijing Feishu Technology Co., Ltd. | https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/45/f4/d8/45f4d8ef-fc19-8e6e-19eb-4e1e4ee1aabd/AppIcon-0-0-1x_U007epad-0-0-0-1-0-0-85-220.png/512x512bb.jpg |
| wecom | 企业微信 | Tencent Technology (Shenzhen) Company Limited | https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/3e/ba/6b/3eba6be1-a7fd-00c5-7f0d-2415c8258ef5/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/512x512bb.jpg |
| wechat | 微信 | Tencent Technology (Shenzhen) Company Limited | https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/84/6b/3a/846b3ab8-9dea-e3e5-861e-f6ecaa0b3387/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-0-0-85-220.png/512x512bb.jpg |

检索方式：`https://itunes.apple.com/search?term=<中文名>&country=cn&entity=software&limit=5`。

**许可与使用限制**：这些图形不是开源素材。Apple 的公开图标仅用于展示对应 App；飞书、企业微信、微信的图形与商标分别归字节跳动、腾讯所有。
本目录只在 DeepDeep 内部用于厂商/插件识别（与 App 内展示第三方服务一致），不随仓库入库（`assets/` 为未跟踪目录）。
如需对外分发或商用，请替换为获得品牌方授权的素材；上游原始 JPEG 保留在 `upstream/` 以便复核与替换。

处理方式：feishu/wecom 去除白色背景（角点 floodfill）；wechat 去除绿色底并保留标志轮廓重着色为微信绿 `#07C160`；三者均裁剪留白、居中并输出 512/256/192 透明 PNG。
