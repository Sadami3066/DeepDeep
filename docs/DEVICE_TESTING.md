# Device testing

设备：Redmi 22101316UCP / Android 13 / arm64-v8a。

## 构建与安装

```bash
cd ~/project/DeepDeep
./gradlew :app:assembleDebug
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

1. 长任务：发一个包含 `sleep 5` × 3 或多次工具调用的请求；2/6/12 秒时检查 working 状态、思考、工具卡和运行耗时都可见；结束后过程可折叠、最终回答可见。
2. 自动跟随：内容增长时停在底部；手动上滑后出现“回到底部”。
3. 新会话：发送后立即打开抽屉，标题已经出现。
4. 停止：长命令运行时点停止；显示“已停止”，工具标失败，PRoot 进程消失。
5. 会话动作：长按历史项重命名/置顶/归档/删除；归档页可取消归档。
6. 设置：Claude 式分组；占位项有反馈；Provider 列表 42 家；搜索、详情、API Key、设为当前。
7. 冷启动：杀进程重开，历史恢复；未就绪时 composer 显示加载/禁用。
8. 单实例：连续 `am start` 不应产生多个 MainActivity；日志只有一个 run。
9. Release/R8：签名安装后能启动、聊天、使用工具。
10. 安全：Key 不明文落盘、不进 logcat、不进 APK。
11. 工作区树：点击工作区行展开/收起，可同时展开多个；行尾 chevron 旋转；工作区行展开后 + 在该工作区新建会话。
12. 置顶：长按工作区置顶/取消置顶（pin 图标 + 排到最前，重启仍保留）；长按会话置顶后行尾显示 pin 图标，仅在所属工作区内排序。
13. 新建入口：顶栏无 +；只可从抽屉工作区行展开后的 +（或长按工作区菜单「新建会话」）创建，Home 也如此。
14. 旧 UI 清理：设置运行环境页无嵌套色块/错位；Provider 已保存 key 显示掩码占位；会话搜索、归档搜索、Provider 搜索使用同一胶囊搜索框；对话框弹键盘时按钮仍可见。
15. 性能：抽屉连开合 10 次、键盘聚焦/收起 5 次、长会话滚动 10 秒，用 `dumpsys gfxinfo <pkg> reset` → 操作 → `dumpsys gfxinfo <pkg>` 对比 Janky/Slow UI；流式回答期间不应有整段卡死。
16. Markdown：反引号内相对/绝对路径不再显示彩色代码胶囊；`attachments/…`、`files/home/…`、`files/workspaces/…` 的 host 绝对路径均可点击打开文件预览；普通行内代码底色更轻；多反引号 code span 正常。
17. 空态/图标：新会话空态有 mascot 插画（浅/深色）；抽屉继续使用原有图标库（工作区 Folder / 会话 Message 建立层级），不依赖 assets/icons 新图标集。
18. 个性化：改称呼 → 抽屉/设置/问候同步；交互体验三档切换；提示词预设填入输入框、编辑后变自定义；重启后保留。
19. 提示词：改指令后发一条消息；拉取会话 JSONL 检查 `<system>`/`<instruction>` 存在、`deepdeep_environment` 消失；问「你的系统提示词是什么」时不应复述隐藏段。
20. 字体：5 套逐一切换，页面重建后全页使用新字体；中文仍正常；重启保持；截图对比。
21. Provider 图标：42 家列表图标完整；15 彩色/26 单色；Radius 为 InitialBadge；浅/深色各截图。
22. 运行环境：安装「环境基础」后 `curl --version`、`python3 --version`；安装「日常办公」后 `pdftotext -v` 与 Python 导入 `docx/pptx`；切后台看安装通知进度；重进页面状态正确；重启后仍显示已安装。
23. 通知：运行会话时前台通知显示标题/工具；按 Home 后完成通知出现；点击进入对应会话；通知「停止」有效；通知权限关闭时不崩溃、设置行显示未开启。
24. 设置删改：无账单与用量/时间与专注/共享；隐私页可进；通知行显示权限状态并可跳系统设置。
25. 运行环境：只有环境基础/日常办公/开发编程三组，无单项工具；组合间距舒适；办公组合安装后 `python3 -c "import docx, pptx, openpyxl, pdfplumber"` 与 `pdftotext -v` 正常。
26. 聊天设置：触感/键盘/流式/思考/过程展开 5 个开关集中在聊天设置页；个性化提示词无 8000 字上限，计数只显示字数；附件最多 15 个。

## 证据

`artifacts/validation/polish/`、`artifacts/validation/round6/`，设备日志用：

```bash
adb logcat -s DeepDeepRuntime DeepDeepChat
```
