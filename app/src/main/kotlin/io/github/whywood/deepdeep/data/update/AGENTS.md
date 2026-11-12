# data/update · 应用内更新

从 GitHub 最新 Release 发现新版本、下载安装包并交给系统安装器。只服务 GitHub Release 这一条分发路径：版本号、更新说明、安装包与 sha256 都来自同一个 release，不另建版本清单、镜像或推送服务。

## 文件

| 文件 | 职责 |
|---|---|
| `UpdatePolicy.kt` | 纯逻辑：版本号解析与比较、latest release JSON 解析（选 APK、digest）、自动检查节流判定、下载校验与归档包名/versionCode 判定 |
| `UpdateStore.kt` | `update_state` DataStore：上次成功/尝试时间与缓存的可用版本；`UpdateStore` 接口便于测试替身 |
| `UpdateInstaller.kt` | 安装权限判断、「安装未知应用」设置跳转、安装包归档校验与系统安装器唤起；`UpdateInstaller` 接口便于测试替身 |
| `UpdateManager.kt` | 唯一状态源 `StateFlow<UpdateUi>`：检查/下载/待安装/失败；App 级 scope 内下载，前台与页面打开触发检查 |

## 要点

- 发布约定在 `docs/RELEASE.md`：tag `vX.Y.Z`、唯一资产 `DeepDeep-X.Y.Z.apk`、release body 是给用户看的更新说明、不勾 prerelease/draft。
- 自动检查只由 `MainActivity.onStart` 触发，成功 24h 节流、失败 15min 退避、失败静默；手动检查与页面打开走同一个检查入口。
- 安装包只落在 `filesDir/updates/`（FileProvider `updates/`），下载后校验大小/sha256 并解包核对包名与 versionCode 才交给系统安装器；不做静默安装。
- 本地版本 ≥ 缓存版本时缓存与残留安装包在启动时清掉；已下载的包按文件名识别，进程重启后可直接继续安装。
