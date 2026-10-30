# 发布流程（M4 准备稿，尚未启用）

> 状态：**未启用**。当前 release 变体仍用 debug 证书（ADR-0001），仓库也暂不公开。本文只在决定公开发版时执行。

## 0. 前置（缺一不可）

- [ ] ADR-0001 的「推翻条件」满足：愿意让所有现装设备卸载重装（换证书后无法覆盖安装）。
- [ ] 正式 keystore 已生成并离线双备份（`keytool -genkeypair -keystore ~/keys/deepdeep-release.jks -alias deepdeep -keyalg RSA -keysize 4096 -validity 10000`）。
- [ ] keystore 口令进 GitHub Secrets（`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`），仓库内不出现任何密钥文件。
- [ ] `app/build.gradle.kts` 的 `signingConfigs.release` 从 `keystore.properties` 读取（本地）或由 CI 生成；无配置时回退 debug 以保证本地仍能构建。
- [ ] 合规清单通过：`docs/OPEN_SOURCE_CHECKLIST.md`。

## 1. 版本号

- 唯一事实源：git tag `vX.Y.Z`。
- `versionName = X.Y.Z`；`versionCode = X*10000 + Y*100 + Z`（patch < 100）。
- 不手改 `versionCode`；CI 从 tag 推导。

## 2. 发版步骤

```bash
# 1) 确认干净 + 门禁绿
git switch master && git pull --ff-only
./scripts/verify.sh

# 2) 打 tag（触发手动 release workflow 后按需执行）
git tag -a v0.3.0 -m "v0.3.0"
git push origin v0.3.0

# 3) GitHub Actions → Release → Run workflow（workflow_dispatch），选择该 tag
#    产物：app-release.apk（signed）+ app-release.aab + mapping.txt
# 4) 在 Release 页面补 CHANGELOG 摘要与「数据影响/升级须知」
```

## 3. 产物与验收

- `app-release.apk`：`apksigner verify --print-certs` 必须显示正式证书（不是 `CN=Android Debug`）。
- 升级验证：旧版本（同证书）覆盖安装成功；`allowBackup=false` 时数据仍在。
- Release Notes 必须写：数据影响（schema 迁移/损坏恢复）、已知限制、回滚方式（安装上一版 APK）。

## 4. 回滚

1. 撤销 Release 资产（不要删 tag 历史）。
2. 公告「安装上一版 APK」；代码回滚用 `git revert` + 新 patch 版本。
3. 数据层：runtime 的 `.corrupt` 备份与升级验证条目（`docs/ACCEPTANCE.md` 的 DATA 域）是最后防线。

## 5. SDK 依赖

- `pi2kotlin` 当前从 GitHub Release `pi2kotlin-<version>-m2.zip` 拉取（`syncPiSdk`）。
- v0.3 起计划切 Maven Central（group `io.github.sadami3066`，见 ADR-0007）；切换时同步改根 Gradle 任务与 `gradle/pi-sdk.sha256`。
