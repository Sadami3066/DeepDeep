# plugin/host/update · 插件更新索引与暂存

外部插件的更新链路：拉取并解析分发索引 `index.json`、按已安装记录筛选可更新项、下载 `.ddp` 并校验 sha256、验签后交给 `PluginInstaller` 暂存为 pending。只负责「下载 + 验签 + 暂存」，不提交 pending、不改 UI；通过 `ExternalUpdateSupport` 接入 `AppGraph`/`PluginExternalFlow`，启动时激活由 `external/PluginInstaller` + `plugin/manager/ExternalPluginRuntime` 负责。

## 文件

| 文件 | 职责 |
|---|---|
| `PluginUpdateDefaults.kt` | 常量与路径：索引/缓存/下载目录、各类上限、schemaVersion、URL scheme 白名单；`files/plugins` 下 `sources.json`、`sources_cache.json`、`downloads/` |
| `PluginIndex.kt` | 索引模型与解析：`{schemaVersion:1, plugins:[{id,version,apiLevel,minHostVersion,url,sha256,size,publisherKeyId}]}`；严格校验 id/版本/sha256/keyId/URL、条目上限与重复 id 拒绝，`entryToJson` 供缓存写回 |
| `PluginUpdatePolicy.kt` | `UpdatePolicy`（hostApiLevel/hostVersion/包体上限）、`PluginUpdateCandidate`、`UpdateRejection` 错误码、`PluginUpdateSelector`（同钥 + 版本更新 + apiLevel/minHostVersion/包体过滤） |
| `PluginFetcher.kt` | `PluginFetcher` 抽象（`readText` / `download`）与默认 `HttpPluginFetcher`：`http(s)` 走 `HttpURLConnection`、支持 `file:`；连接/读超时、体积上限、进度回调 |
| `PluginIndexCache.kt` | `sources_cache.json` 读写（原子写）：索引 + 来源 URL + fetchedAt，损坏按不可用处理（缓存可丢弃，不备份） |
| `PluginIndexClient.kt` | `fetch(indexUrl)`：网络拉取 → 解析 → 原子写缓存；失败（网络/损坏/超大）回退上次缓存并标记 `fromCache`；`cached(indexUrl)` 读缓存 |
| `PluginRepositorySettings.kt` | `sources.json` 读写：`indexUrl/autoCheck/intervalHours`，schemaVersion 1，损坏备份 `.corrupt` 后重建默认值，写入前归一化（URL scheme、interval 1..720） |
| `PluginPackageDownloader.kt` | 下载到 `downloads/*.part` → 体积比对 → sha256 比对 → 改名 `*.ddp`；失败清理残片 |
| `PluginUpdateManager.kt` | 编排：`downloadAndStage()` = TOCTOU 复核（同钥/更新/未 pending/已信任）→ 下载 → 验签与清单一致性 → `installer.install()` 暂存，返回 typed 结果，不自动生效 |
| `ExternalUpdateSupport.kt` | AppGraph/manager 用的集成门面：构造时按 `filesDir` 组装 settings/client/downloader/manager；对外只有 `loadSettings/saveSettings/checkUpdates/stageUpdate` 与 UI 形状的结果类型（`ExternalUpdateCandidate`/`UpdateCheckResultUi`/`UpdateStageResultUi`），不暴露内部 typed 类 |

## 要点

- 契约来源：`docs/PLUGIN_DEV.md` 的「信任（TOFU）与更新」「分发索引」。更新必须**同钥**；索引里的 `publisherKeyId` 只用于筛选与展示，信任仍以 TOFU 库为准（`PublisherTrustStore`），`downloadAndStage` 对未信任发布者直接拒绝，绝不因索引声明而自动信任。
- 索引 sha256 是整包 `.ddp`（含签名文件）的 sha256，与安装记录 `activeBundleSha256` 同口径；下载后先比对索引 sha256，再由 `PluginPackageVerifier` 用签名与清单复核，两处都必须过。
- 暂存即 `PluginInstaller.install()` 的更新分支：写入 `pendingVersion`/`pendingBundleSha256`（UI 相位由 record 派生为 `PENDING_RESTART`）；管理器不调用 `commitPending`，由下一次进程启动的激活流程提交；激活失败时丢弃 pending、继续使用当前版本（旧版本目录保留）。
- 缓存只在网络/索引解析失败时兜底，且来源 URL 必须匹配，避免切换源后拿旧源索引；缓存损坏直接视为不可用。
- 测试注入 `PluginFetcher`（fake 或 `file:`），不联网、不新增依赖；`HttpPluginFetcher` 的默认实现只依赖 JDK。
- 集成入口是 `ExternalUpdateSupport(filesDir, hostVersion, installer, trustStore, recordStore, fetcher = HttpPluginFetcher())`：`filesDir` 传 App 的 `filesDir`（内部拼 `files/plugins/{sources.json,sources_cache.json,downloads/}`）；`hostVersion` 与 `PluginInstaller` 构造用同一个值（App 现为 `BuildConfig.VERSION_NAME`）；`checkUpdates()` 只读+写缓存，`stageUpdate(pluginId)` 只暂存不生效。
- 新增文件时同步本索引，并把目录补进 `../AGENTS.md` 的文件表。
