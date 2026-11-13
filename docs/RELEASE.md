# Release 构建

## 签名材料

| 文件 | 作用 | 是否进 git |
|---|---|---|
| `app/release.keystore` | release 签名证书（RSA 4096，别名 `deepdeep`） | 否 |
| `keystore.properties` | 证书路径与口令 | 否 |

`keystore.properties` 由构建脚本自动读取，字段：

```properties
storeFile=release.keystore
storePassword=...
keyAlias=deepdeep
keyPassword=...
```

`storeFile` 相对 `app/` 模块目录解析。缺少 `keystore.properties` 时 release 会回落到 debug 签名（只为让无证书环境仍能编译；正式分发不要用这种产物）。

## 构建

```bash
./scripts/gradlew-locked :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。release 开启 R8 代码压缩与资源压缩，规则在 `app/proguard-rules.pro`。

## 版本号与发布流程

`app/build.gradle.kts` 里的版本号在发版前手动修改：

- `versionCode`：每次发版递增，按 `MAJOR*10000 + MINOR*100 + PATCH` 计算（例如 `1.2.3` → `10203`）。
- `versionName`：`X.Y.Z`，与 tag 一致。

版本号只写在这些地方：`app/build.gradle.kts`、`CHANGELOG.md` 顶部节、`release-notes/vX.Y.Z.md`、git tag。README、USER_GUIDE 等产品文档不写版本。

1. 改好 `versionCode` 与 `versionName`，`./scripts/check.sh --full` 通过后提交。
2. 打 tag `vX.Y.Z`，与 `versionName` 一致。
3. 在 GitHub 创建 release：只上传一个资产，命名为 `DeepDeep-X.Y.Z.apk`。
4. 更新说明手写成用户能看懂的文案：App 更新页会原样显示。不要用 `--generate-notes` 生成 commit 列表，也不要写「见下方附件」这类必须跳到 GitHub 才能看懂的说明。
5. 不要勾 prerelease 或 draft：App 只读 latest 正式版。

App 侧读取 `releases/latest`、校验资产与 digest 的规则见「应用内更新（发布约定）」。

## R8 keep 兜底

release 的 R8 会裁剪、改名类与成员；这类问题只在混淆包出现，debug 包正常。已踩过的坑：BouncyCastle 兜底被混淆导致插件验签失败；宿主 ABI 被裁剪导致插件页 `NoClassDefFoundError`；无线调试 shell daemon 入口被裁剪导致 adb 模式启动失败。

必须保持原名的两类符号：

- **插件 ABI**：插件在运行期直接复用宿主里的 Kotlin、协程、Compose、`plugin-api`、`design`。保留规则写在 `plugin-api/consumer-rules.pro`（随 SDK AAR 下发，任何依赖它的混淆构建都会自动应用），`app/proguard-rules.pro` 保留同样一份兜底。插件 UI 常用的 `androidx.lifecycle` / `androidx.activity` / `androidx.savedstate` 也在 keep 范围内。
- **反射 / 动态加载入口**：`app_process` 启动的 `ShellDaemon`、虚拟屏幕 helper、BouncyCastle、MLKit barcode 等。
- **Room / WorkManager**：WorkManager 自带的 Room 数据库实现由 Room 反射创建，`app/proguard-rules.pro` 用 `-keep class * extends androidx.room.RoomDatabase { <init>(); }` 保住无参构造器；删掉这条会让 release 包在启动阶段崩溃（1.0.6 曾因此返工）。

改动约束：新增反射入口、给 `plugin-api` 加 `api()` 依赖、或让插件 UI 使用新的宿主库时，必须同步补 keep 规则；否则 release 构建成功但运行到该路径才崩。keep 只保 ABI 名字，不承诺宿主与插件的版本兼容，插件仍需随 SDK 版本重编译。

## 应用内更新（发布约定）

App 只从 GitHub Release 读取更新：读 `releases/latest`，按 tag `vX.Y.Z` 判断新版本，release body 原样作为更新说明，唯一 `.apk` 资产的下载地址与 GitHub 计算的 sha256 digest 用于下载校验。

发布步骤、版本号与更新说明的约定见[版本号与发布流程](#版本号与发布流程)。资产上传后 GitHub 会自动计算 sha256 digest，App 下载安装包后用它校验。

## 发布前检查

1. `./scripts/check.sh --full` 通过。
2. 安装**混淆后的 release APK** 冒烟：四个官方插件逐个「安装 → 启用 → 打开插件页」，各触发一个工具与一个 UI 槽位；`.ddp` 的打开与分享导入入口各验一次。
3. debug 包通过不算数；只跑单元测试也不够，R8 问题只在 release 混淆包暴露。
4. 发布前用旧版本真机走一次应用内更新验收：旧版检测到新版 → 下载 → 系统安装；发布后也要用应用内更新升级一次。

## 换签迁移

首次从 debug 证书切到 `release.keystore`（或以后更换证书）时，Android 不允许覆盖安装，必须先卸载旧 App。`android:allowBackup="false"`，卸载会删除本机会话、设置与凭据；需要保留时先在聊天顶栏「导出会话」把要留的会话逐个导出为 JSONL 文件。

安装前确认设备上已安装的包名与 `applicationId` 一致；升级同一证书签名的后续版本可直接覆盖安装。
