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

产物：`app/build/outputs/apk/release/app-release.apk`。release 开启 R8 代码压缩与资源压缩，规则在 `app/proguard-rules.pro`；插件 SDK 与设计系统的公开类型必须保持原名，新增反射入口时同步补 keep 规则。

## 换签迁移

首次从 debug 证书切到 `release.keystore`（或以后更换证书）时，Android 不允许覆盖安装，必须先卸载旧 App。`android:allowBackup="false"`，卸载会删除本机会话、设置与凭据；需要保留时先在 App 内导出会话备份，装好新版本后再导入。

安装前确认设备上已安装的包名与 `applicationId` 一致；升级同一证书签名的后续版本可直接覆盖安装。
