# Device testing

设备：Redmi 22101316UCP / Android 13 / arm64-v8a。

## 构建与安装

```bash
cd ~/project/DeepDeep
./scripts/gradlew-locked :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.whywood.deepdeep/.MainActivity
```

MIUI 普通 adb 安装可能被拦截，开发机使用 root 安装：

```bash
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/deepdeep-debug.apk
adb shell 'su -c "pm install -r /data/local/tmp/deepdeep-debug.apk"'
adb shell 'su -c "rm -f /data/local/tmp/deepdeep-debug.apk"'
```

## 导入 DeepSeek Key（仅 debug）

Key 保存在主机 `~/.config/deepdeep/deepseek.key`（0600）。

```bash
./scripts/import-key.sh
adb shell am force-stop io.github.whywood.deepdeep
adb shell am start -n io.github.whywood.deepdeep/.MainActivity
```

App 启动时读取 `files/import/deepseek.key`，写入 Keystore 加密存储后删除导入文件；Release 不包含这段逻辑。

## 验收

现行验收条目在 `docs/dev/ACCEPTANCE.md`（稳定 ID，如 `ACC-CHAT-01`），按「前置 → 操作 → 可见判据」执行，PR 只引用 ID。历史 round-by-round 流水已归档到 `docs/archive/device-testing-rounds.md`。未完成或缺真机条件的条目在验收表中标 `status: blocked`（如旧 #128 的企业微信扫码、小米云真实账号、GUI 写操作 → `ACC-REG-05`）。

## 日志、截图与证据

设备日志：

```bash
adb logcat -s DeepDeepRuntime DeepDeepChat
```

证据采集：按验收 ID 截屏落盘，自动压到 720p 并生成 verdict 模板：

```bash
./scripts/collect-evidence.sh ACC-CHAT-01 长任务 2s
```

- 证据目录：`docs/dev/evidence/<YYYY-MM-DD>-<ID>/`，规范见 `docs/dev/evidence/README.md`。
- 原始大图留在本地 `artifacts/validation/`，不入库。
