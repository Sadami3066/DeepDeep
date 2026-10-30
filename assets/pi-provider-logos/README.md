# Pi provider 图标素材包

按 2026-10-23 核对的 Pi 1.4.0 源码 `KnownProvider` 清单整理：42 个 provider ID，对应 31 份不同品牌图形。地区、套餐及认证入口会复用同一品牌图标。

## 直接使用

- `svg/light/`：适配浅色背景的 SVG，按 provider ID 命名。
- `svg/dark/`：适配深色背景的 SVG，按 provider ID 命名。
- `png/256/`、`png/512/`：256×256 / 512×512 透明 PNG，各含 `light`、`dark`。
- `android/res/`：可复制进 Android 项目 `app/src/main/res/` 的 PNG 资源。夜间模式使用 `drawable-night-nodpi/`，系统自动选择。
- `android/provider-icon-map.json`：provider ID 到 Android drawable 名称的映射。
- `manifest.json`：完整映射、来源网址、版本和 SHA256。
- `providers.csv`：可用表格软件打开的清单。
- `preview.png`：42 个 provider 的浅色 / 深色显示总览。

网页示例：`<img src="svg/light/deepseek.svg" width="24" height="24" alt="DeepSeek">`。

Android Compose 示例：

```kotlin
Image(
    painter = painterResource(R.drawable.provider_deepseek),
    contentDescription = "DeepSeek",
    modifier = Modifier.size(24.dp)
)
```

彩色品牌图标推荐用 `Image` 原样显示。若使用 `Icon`，设置 `tint = Color.Unspecified` 保留品牌颜色。Android 文件名中的连字符已转成下划线，例如 `openai-codex` → `provider_openai_codex`。

## 图标选择

`google` 使用 Gemini；`google-vertex` 使用 Vertex AI；`azure` 使用 Azure；`openai-codex` 复用 OpenAI；`moonshotai` 使用 Moonshot，`kimi-coding` 使用 Kimi；`xiaomi` 及其套餐节点使用 Xiaomi MiMo。Cloudflare 两个入口共用 Cloudflare 标识，OpenCode Zen / Go 共用 OpenCode 标识。

SVG 保留原始路径和品牌颜色，仅将尺寸设为 256×256，并为可变前景色指定浅色 / 深色值。所有 PNG 背景透明。Radius 使用官方图形和官方颜色 token，四周保留标志宽度 1/4 的留白。其余图标按原始 viewBox 输出，布局时可在控件外增加统一间距。

## 来源与署权

30 份不同图形来自 LobeHub `lobe-icons`，已附 MIT 原许可证；品牌标识归各品牌权利人所有，用于服务商识别。Radius 来自 Earendil 官方 `elanor` 图标源，按官方第三方品牌识别条款提供，保留原图形、色彩与留白。完整官方说明模板保存在 `upstream/radius/official-guidelines-template.md`。

图形源文件保存在 `upstream/`。文件名称与映射均以本包记录的 Pi 源码快照为准。

光栅图（`png/`、`android/res/`、`preview.png`）按 `.gitignore` 只保留在本地，不入库；入库的是 `svg/`、`upstream/` 中的矢量与文本来源、`manifest.json`、`SHA256SUMS.txt` 与许可文本。
