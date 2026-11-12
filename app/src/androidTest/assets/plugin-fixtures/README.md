# plugin-fixtures · 设备二进制回归夹具

`scripts/plugin-binary-compat.sh` 把这些 `.ddp` 推到设备 app 外部目录，`PluginBinaryCompatTest` 在设备上做安装 → 启用 → 停用 → 升级 → 卸载链路回归。每个宿主发版前手工跑一次；后续每次发版把上一版市场发布包追加到这里。

首版来源：[DeepDeep-Plugins](https://github.com/Sadami3066/DeepDeep-Plugins) 的 `sample` 模块（`a57e941`，API level 6），使用仓库内提交的开发签名密钥 `sample/signing/dev-ed25519.pem` 签名，keyId 统一为 `ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213`。

| 文件 | 插件 id | 版本 | 用途 | 大小 (B) | sha256 |
|---|---|---|---|---|---|
| `sample-1.0.0.ddp` | `io.github.whywood.deepdeep.pluginsample` | 1.0.0 | 首装基线 | 816538 | `2bfe17e2ac3d9495bc0dd79b1c2d542058c5dd92a4beeb2b09ade70b06bea051` |
| `sample-1.0.1.ddp` | `io.github.whywood.deepdeep.pluginsample` | 1.0.1 | 升级目标（同 dex、仅 manifest 版本号不同） | 816542 | `e9b68a80e983d293fa3a5a0d63ed7c3d44d40ea462d713293266a1c049d4c5eb` |

两个夹具均为 `apiLevel=6`、`minHostVersion=1.0.4`、`ui.apiLevel=2`，提供 `sample_echo` 工具与 `sample.main` 页面。

重新生成（v1 在插件仓库内构建，v2 复制到临时目录改版本后重签，均用同一开发密钥）：

```bash
cd ../DeepDeep-Plugins/sample && ./gradlew verifyPlugin
cp build/plugin/sample.ddp <repo>/app/src/androidTest/assets/plugin-fixtures/sample-1.0.0.ddp
cp -a . /tmp/plugin-fixtures-build/sample-v2 && cd /tmp/plugin-fixtures-build/sample-v2
rm -rf build .gradle
# 修改 src/main/assets/plugin.json 的 version 与 build.gradle.kts 的 versionName/versionCode
./gradlew verifyPlugin
cp build/plugin/sample.ddp <repo>/app/src/androidTest/assets/plugin-fixtures/sample-1.0.1.ddp
```
