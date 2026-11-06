# plugin/host/external · 外部 .ddp 包管线

外部插件包（`.ddp`）的读取、Ed25519 签名校验、发布者 TOFU 信任库、v4 安装记录、安装/更新与 DEX 装载。全部为 app 模块内部实现（`internal`），由 `PluginHostManager`/`PluginExternalFlow`/`ExternalPluginRuntime` 接线，不建议在别处直接使用。数据目录约定见 `../PluginPaths.kt`。

## 文件

| 文件 | 职责 |
|---|---|
| `DdpPackage.kt` | `.ddp` zip 读取与校验：必需 `assets/plugin.json`、至少一个根 `classes*.dex`，可选 `assets/**`、`lib/<abi>/**`、`META-INF/plugin-signature.json`；逐项 sha256、整包 sha256、规范化 entry digest；entry 读取/解压/复制并再次校验摘要 |
| `DdpSigning.kt` | 签名格式与密码学：规范化 digest bytes、sha256/hex、Ed25519 公钥解析与验签（平台优先、BouncyCastle 兜底）、`ed25519:<hex>` keyId 派生 |
| `PluginPackageVerifier.kt` | 外部包验证：签名必需、算法/字段/ keyId/签名校验，再走 `PluginManifestCodec.decode`（宿主 apiLevel/hostVersion），返回 `Ok(manifest, publisher, digests)` / `Rejected(code, reason)` |
| `PublisherTrustStore.kt` | `files/plugins/trusted_publishers.json`（schemaVersion 1，keyId → name/publicKey/firstSeenAt），TOFU：新增/已知/公钥冲突；原子写、损坏备份 `.corrupt` 后重建 |
| `ExternalPluginRecord.kt` | v4 安装记录模型（用户意图）：version/installedAt/enabled/skillIds/installPath/activeBundleSha256/size/publisherKeyId/apiLevel/uiApiLevel/lastError/pendingVersion/pendingBundleSha256/previousVersion/previousInstallPath/previousBundleSha256；v4 不存相位，相位由 `PluginPhase.derive` 从 record+运行期事实派生 |
| `ExternalPluginRecordStore.kt` | `files/plugins/plugins.json` schemaVersion 4：非 v4/损坏时备份 `plugins.json.v2.bak` 后破坏式重建，原子写，`load/get/records/upsert/remove/setEnabled/mutate`；旧 v4 文件缺 previous\*/pending\*/*phase* 字段时按 null/忽略读取，不 bump schema |
| `PluginInstaller.kt` | 安装编排：`inspect`（确认页数据）、`install`（新装/更新暂存、发布者确认、id/工具名冲突、版本冲突）、pending 提交/丢弃（commit 写 previous\* 并清理更旧版本目录）、启用、卸载、`markFailure`；装载前 sha256 复核在 `LoadedPluginHandle` |
| `PluginInstallModels.kt` | 安装结果与状态 DTO：`InstallRejection`/`InspectionResult`/`InstallResult`/`BundleState`/`CommitResult` |
| `PluginPackageStager.kt` | 候选读取（DdpPackage + 验签 + `PluginManifestCodec.decode`）、staging 解包到 `<version>/{bundle,assets,lib}`、bundle sha256 复核、版本目录清理 |
| `ExternalPluginRecovery.kt` | 激活恢复原语：`<id>/activation.json` marker（版本/连续次数/时间）用于激活中断与启动崩溃判定；`rollbackToPrevious`（校验 previous bundle 后切回并删失败版本目录）；`pruneVersions` 只保留 active/pending/previous 与 data |
| `LoadedPluginHandle.kt` | 外部插件装载句柄：校验 bundle sha256，`InMemoryDexClassLoader` 优先、只读 jar + `PathClassLoader` 兜底，实例化 `entryClass`；装载期任何 `Throwable` 归一为 `PluginLoadException`（`CancellationException` 重抛），单插件链接错误不中断宿主启动；`close()` 调 `plugin.close()` 并释放引用/删除兜底 jar |
| `JsonFiles.kt` | JSON 原子写（tmp + ATOMIC_MOVE，失败退 REPLACE_EXISTING）与备份工具 |

## 签名规范 ddp-1（与 plugin-template / docs 对齐）

- `.ddp` 是普通 zip；`plugin.json` 必须在 `assets/plugin.json`；`classes*.dex` 在根；目录条目忽略。
- 规范化 digest bytes：所有非目录、路径不以 `META-INF/` 开头的 entry，按路径 UTF-8 字节序升序，每行 UTF-8 `"<sha256-hex> <uncompressed-size> <entry-name>\n"`（sha256 对解压后内容，行尾 LF，无额外前后缀）。
- Ed25519 签名对上述 digest bytes 签名；`META-INF/plugin-signature.json`：
  `{"algorithm":"Ed25519","keyId":"ed25519:<lowercase hex sha256(SPKI DER)>","publicKey":"<base64 std X.509 SubjectPublicKeyInfo DER>","signature":"<base64 std 64-byte signature>"}`
- keyId 必须等于由 publicKey 重新派生的值；外部插件签名必需；更新必须与记录的 `publisherKeyId` 同钥。
- 整包 sha256（含签名文件）单独记录在 `activeBundleSha256`/索引条目；每次装载前复核。

## 要点

- 安装暂存流程：解压到 `<id>/.staging-<version>-<ts>` → 复制并复核 bundle → 重命名到 `<id>/<version>`；新装写 `enabled=manifest.defaultEnabled`，更新只写 `pendingVersion`/`pendingBundleSha256`，由激活协议在下次启动 commit 后生效。
- `commitPending()` 把旧 active（version/installPath/sha256）写入 `previous*` 并删除更早的 previous 版本目录；`rollbackToPrevious()` 反向切回并清理失败版本；`pruneVersions()` 统一清理未被引用的版本目录与 `.staging-*`。
- 激活 marker `<id>/activation.json`：激活开始前写、任意终态清除；进程死在激活中会残留，宿主下次启动据此跳过自动激活并给出「上次激活中断」，`attempts` 用于连续启动崩溃自动禁用。
- `PluginInstaller` 的冲突检查叠加 `HostToolNames`（引擎/宿主/SDK 保留工具名）；工具名冲突只看 manifest `provides.tools`。
- `plugins.json` 只由 v4 `ExternalPluginRecordStore` 读写；读到非 v4 或损坏文件时先备份 `.v2.bak` 再破坏式重建，不做数据迁移。
- 签名规范变更会同时影响 plugin-template 的 `signPlugin` 与 `docs/PLUGIN_DEV.md`；改格式前先同步 contract-owner，并升 `.ddp` 格式版本。
- 新增文件时同步本索引，并在 `../AGENTS.md` 的文件/子包表中补一行。
