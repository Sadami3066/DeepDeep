# theme · 宿主主题

宿主主题的唯一入口：内置主题与插件主题包统一变成 `DeepDeepColors`，由 `HostTheme` 在四个界面根生效；选择与调色板缓存落 SharedPreferences，冷启动首帧即可用。

## 文件

| 文件 | 职责 |
|---|---|
| `ThemeChoice.kt` | 选择键：`system`/`deepdeep`/`santorini`/`oceanic`/`pack:<pluginId>/<themeId>`；解析失败回退 `system`，pack id 格式校验 |
| `ThemePack.kt` | 主题包模型：`ThemeBase`、`ThemeToken` 白名单、`ThemePackName`（default/zh/en）、`ThemePackTheme`、`PluginThemeSource`/`PluginThemePack` 与 token 覆盖 |
| `ThemePackCodec.kt` | `assets/themes.json` 纯函数解析/校验：schema 1、id/name/dark/base/tokens、颜色格式、对比度（ink/paper、ink/surface、inkSecondary/paper ≥4.5，onAccent/accent ≥3）、64 KiB/24 个主题上限 |
| `ThemePaletteCodec.kt` | 已解析调色板的字符串编解码，供 SharedPreferences 缓存与冷启动回读 |
| `ThemeCatalog.kt` | 主题目录：内置选项 + 插件主题来源；按 `PluginThemeSource` 懒读安装目录 `themes.json`，按 pluginId+版本+路径缓存，坏文件/缺文件跳过 |
| `ThemeController.kt` | 唯一选择状态：选择/刷新、包缺失或禁用回退 `system` 并清缓存；维护 `HostThemeRuntime.dark` |
| `HostTheme.kt` | Compose 根：解析当前调色板（系统档跟随深浅）、同步状态栏图标明暗，调用 design 的 `DeepDeepTheme(colors, fontStyle)` |

## 要点

- 插件主题安装期由 `PluginPackageStager` 用同一个 codec 校验，失败以 `THEMES_INVALID` 拒绝安装；运行期只读已安装目录，不执行插件代码。
- `provides.themes` 与 `themes.json` 的 id 集合必须一致；停用插件或删包后，`ThemeController.refresh` 回退 `system` 并清掉失效选择。
- 主题选项标签按 App 语言解析 `name.zh`/`name.en`，缺省回退 `name.default`；内置标签走 `strings_settings.xml`。
- 四个根（MainActivity、PluginHostActivity、AssistantOverlayActivity、FloatingWindowController）只经 `HostTheme`，不要在别处再包 `DeepDeepTheme`；插件的 `ui.isDark` 与状态栏图标从当前 palette 的 `dark` 推导。
- 新增颜色 token 时同步 `DeepDeepColors`、`ThemeToken`、`ThemePackCodec` 的对比度检查与文档；主题包格式变更要升 schema。
