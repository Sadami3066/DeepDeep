# app/src/androidTest · 设备测试

需要真机或模拟器的 instrumentation 测试（`AndroidJUnit4`），连不上设备不跑；默认不进 `check.sh`，按改动手工执行。测试源镜像 app 包结构，设备夹具在 `assets/` 下；`app/src/test/` 的 JVM 单测在各自目录的 AGENTS.md 维护。

## 文件

| 路径 | 职责 |
|---|---|
| `AndroidManifest.xml` | instrumentation 声明（`androidx.test.runner.AndroidJUnitRunner`） |
| `arrange/` | 系统日历读写设备测试（需日历权限） |
| `contract/` | 工具契约与夹具 Activity 设备测试 |
| `data/` | 网络明文策略、图片归一化、头像存储设备测试 |
| `plugin/manager/` | 插件设备端到端：`ExternalPluginE2ETest`（外部包安装/坏包隔离/升级）、`PluginBundleE2ETest`（官方四插件包）、`PluginBinaryCompatTest`（已发布 `.ddp` 安装→启用→停用→升级→卸载）；`ExternalPluginDdpFixture` 用测试密钥现场合成 `.ddp` |
| `assets/plugin-fixtures/` | `scripts/plugin-binary-compat.sh` 推到设备 app 外部目录的已签名 `.ddp` 夹具，见目录内 README |
| `res/values/strings_contract.xml` | 契约测试用文案 |

## 要点

- 设备夹具不随 APK 内 assets 直接读取，统一由脚本 `deepdeep_stage_app_file` 推到 `/sdcard/Android/data/<宿主包名>/files/<场景>/`，测试从 `getExternalFilesDir("<场景>")` 取；跑测试前先跑对应脚本，缺文件时测试首步报错。
- `scripts/plugin-e2e.sh` 推 sample 插件；`scripts/plugin-bundles-e2e.sh` 推官方四插件与一份改版本升级包；`scripts/plugin-binary-compat.sh` 推 `plugin-fixtures/` 下全部 `.ddp` 并跑 `PluginBinaryCompatTest`。
- 新增设备测试放对应功能包，包结构镜像 `src/main/kotlin`；测试类与夹具改动后只跑相关脚本，不默认进 CI。
