# plugin/host/images · 插件远程图片缓存

插件卡片图片的宿主侧缓存：`ImagePluginService` 把 `RemoteImageCache` 暴露为 `PluginHost.images()`（capability `images` 门禁）。

## 文件

| 文件 | 职责 |
|---|---|
| `RemoteImageCache.kt` | 进程内共享的远程图片缓存：`load(url, maxEdgePx): RemoteImage?`；内存 LRU 24 MB + `<cacheDir>/remote-images` 磁盘缓存 128 MB；6 MB 单图上限、15s 超时、请求去重、http 失败回退 https、仅 http(s)、按长边采样解码；`ImageCachePolicy` 放 URL 归一/候选/缓存 key/采样比纯函数 |
| `AGENTS.md` | 本索引 |

## 要点

- 只写应用私有 cacheDir；不写 `/sdcard`、不进相册、不写插件数据目录；失败返回 null，由卡片显示占位。
- 同一进程内所有插件共用一份缓存；`RemoteImageCache` 由 `PluginGraph` 构造一次。
- 只接受 http/https；不解析 `file://`/`content://`/`data:`；不跟随非图片内容到任意本地路径。
- URL 归一、候选（http→https 回退）、缓存 key、采样比是可单测纯逻辑；解码与网络靠真机卡片截验。
