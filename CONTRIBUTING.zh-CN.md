# 为 DeepDeep 贡献

感谢关注 DeepDeep。本仓库包含 Android 应用、Agent 运行时、shell 桥接、设计系统与插件契约；官方插件放在 [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins)。

## 提交 Issue 前

- 先搜索已有 Issue，避免重复。
- 反馈 Bug 时请附上应用版本、Android 版本与机型、复现步骤，以及相关日志或截图。
- 安全相关问题不要公开提交，直接联系作者。

## Pull Request

- 一次改动只做一件事，不相关的修改拆成不同提交。
- 提交标题使用 `type: 摘要`，例如 `fix: handle empty provider list`。
- PR 会跑 CI（`./scripts/check.sh`），纯 markdown / 文档改动会跳过；提交前可以本地先跑 `./scripts/check.sh`。涉及插件契约时再运行 `./scripts/check.sh --full`；涉及真机行为时尽量运行 `./scripts/check.sh --device`。
- 行为变更需要同时补测试和文档。
- 不要提交 API Key、签名密钥、Token、会话文件、录屏或个人数据。
- 插件本身的改动放到官方插件仓库，除非同时修改了宿主契约。

## 开发环境

需要 JDK 17（构建固定使用 Java 17 toolchain）、Android SDK Platform 36、build-tools 36.0.0（约 5.6 GB）；冷缓存首次构建约 15–30 分钟。Windows 不原生支持，请使用 WSL2、Linux 或 macOS。

```bash
./scripts/dev-setup.sh        # 首次：检查 JDK 与 Android SDK、构建 debug APK
./scripts/check.sh            # 编译、单测、lint、模块边界与 APK
./scripts/check.sh --full     # 再加插件 SDK ABI 基线
./scripts/check.sh --device   # 覆盖安装到已连接设备并启动
```

所有 Gradle 调用必须走 `./scripts/gradlew-locked`；本机并发构建可能 OOM。模块划分与代码约定见[开发指南](docs/DEVELOPER_GUIDE.md)。

## 许可

提交贡献即表示同意按本仓库的 [Apache-2.0](LICENSE) 许可发布。
