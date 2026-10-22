# Device testing

设备：Redmi 22101316UCP / Android 13 / arm64-v8a。

## 构建与安装

```bash
cd ~/project/DeepDeep
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.whywood.deepdeep/.MainActivity
```

## 导入 DeepSeek Key（仅 debug）

Key 保存在主机 `~/.config/deepdeep/deepseek.key`（0600）。

```bash
./scripts/import-key.sh
adb shell am force-stop io.github.whywood.deepdeep
adb shell am start -n io.github.whywood.deepdeep/.MainActivity
```

App 启动时会读取 `files/import/deepseek.key`，写入 Keystore 加密存储后删除导入文件。Release 不包含这段逻辑。

## 日志

```bash
adb logcat -c
adb logcat -s DeepDeepRuntime AndroidRuntime
```

## 验收

1. 流式聊天正常，停止可用。
2. 安装 Linux，`bash` 执行 `uname -a`。
3. `/workspace` 写文件并读回。
4. 杀进程重开，历史恢复。
5. 无 root 全流程可用。
