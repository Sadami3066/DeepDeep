# ui/files · 目录文件

`FileBrowserScreen` 是聊天菜单「目录文件」与文件选择入口共用的浏览页，按 HostFiles 的能力浏览三类根：应用文件（app home）、共享存储、系统根目录。

## 文件

| 文件 | 职责 |
|---|---|
| `FileBrowserScreen.kt` | 三态根切换（DdSegmentedControl）、相对路径导航、目录列出与文件打开；共享存储未授权时引导授权，不切换根 |

## 要点

- 进入时的 `initialPath` 决定初始根与相对目录（会话 cwd 通常在应用文件下）；切根只重置相对路径，不改变入口来源。
- 三态始终可见，不按特权档位隐藏系统根；能否列出目录由 HostFiles 与当前宿主环境决定。
- 切根时列表先进入加载态、按新根重新读取，不与上一根的列表混排。
- 共享存储授权走 `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`（Android R+），返回前台后自动切到共享存储。
