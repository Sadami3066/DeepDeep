# App Store artwork notice (Feishu / WeCom / WeChat)

`upstream/feishu-appstore-512.jpg`, `upstream/wecom-appstore-512.jpg`, and `upstream/wechat-appstore-512.jpg` come from public Apple App Store icons (iTunes Search API `artworkUrl512`, China storefront, downloaded 2026-10-29).

| Icon | App name | Developer | Download URL |
|---|---|---|---|
| feishu | Feishu - ByteDance AI workplace | Beijing Feishu Technology Co., Ltd. | https://is1-ssl.mzstatic.com/image/thumb/Purple221/v4/45/f4/d8/45f4d8ef-fc19-8e6e-19eb-4e1e4ee1aabd/AppIcon-0-0-1x_U007epad-0-0-0-1-0-0-85-220.png/512x512bb.jpg |
| wecom | WeCom | Tencent Technology (Shenzhen) Company Limited | https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/3e/ba/6b/3eba6be1-a7fd-00c5-7f0d-2415c8258ef5/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-85-220.png/512x512bb.jpg |
| wechat | WeChat | Tencent Technology (Shenzhen) Company Limited | https://is1-ssl.mzstatic.com/image/thumb/Purple211/v4/84/6b/3a/846b3ab8-9dea-e3e5-861e-f6ecaa0b3387/AppIcon-0-0-1x_U007epad-0-1-0-sRGB-0-0-85-220.png/512x512bb.jpg |

Search method: `https://itunes.apple.com/search?term=<localized name>&country=cn&entity=software&limit=5`.

**License and usage limits**: these graphics are not open-source assets. Apple's public icons are used only to represent the corresponding apps; the Feishu, WeCom, and WeChat marks and trademarks belong to ByteDance and Tencent respectively. This directory is used inside Super PI Agent only to identify vendors and plugins (matching how the app displays third-party services); it is not committed to the repository. Replace these assets with licensed material before public distribution or commercial use; the original upstream JPEGs are kept under `upstream/` for review and replacement.

Processing: Feishu and WeCom had their white backgrounds removed with corner flood-fill; WeChat had its green background removed, the mark kept, and recolored to WeChat green `#07C160`; all three were trimmed, centered, and exported as transparent 512/256/192 PNG files.
