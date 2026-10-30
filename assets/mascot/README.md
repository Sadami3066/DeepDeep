# 鲸鱼娘素材库 · 第一批候选

2026-10-21，Codex 使用内置 image_gen，基于用户提供的两张设定图生成。方向：蓝白鲸鱼娘、Q 版比例、柔和赛璐璐、透明底；供 deepdeep 后续界面探索。

## 素材一览

六张成品均为 **1254 × 1254 RGBA PNG**，保留生成器原始像素与透明通道，未裁切、缩放或压缩。

| 预览 | 文件 | 建议用途 / 起始显示尺寸 |
| --- | --- | --- |
| <img src="generated/mascot_avatar.png" width="140" alt="鲸鱼娘头像"> | [mascot_avatar.png](generated/mascot_avatar.png) | 助手头像、关于页；48–96 dp |
| <img src="generated/mascot_welcome.png" width="140" alt="挥手欢迎"> | [mascot_welcome.png](generated/mascot_welcome.png) | 欢迎页、新对话、空状态；144–240 dp |
| <img src="generated/mascot_thinking.png" width="140" alt="坐姿思考"> | [mascot_thinking.png](generated/mascot_thinking.png) | 等待、处理中、思考说明；128–200 dp |
| <img src="generated/mascot_success.png" width="140" alt="开心完成"> | [mascot_success.png](generated/mascot_success.png) | 操作完成、设置成功；128–200 dp |
| <img src="generated/mascot_retry.png" width="140" alt="摊手重试"> | [mascot_retry.png](generated/mascot_retry.png) | 可恢复错误、重试提示；128–200 dp |
| <img src="generated/whale_mark.png" width="140" alt="蓝色小鲸鱼"> | [whale_mark.png](generated/whale_mark.png) | 装饰标记、小图标候选；32–64 dp |

完整清单、尺寸、SHA-256 与状态见 [manifest.json](manifest.json)；准确提示词与每次输入顺序见 [prompts.json](prompts.json)。本轮使用内置 image_gen，未使用 CLI。

## 预览

用浏览器打开 [preview.html](preview.html)，可切换浅色、深色、棋盘背景，查看全图与小尺寸效果。所有资源都在本地，不依赖 CDN。

如果浏览器不允许直接打开文件，可在本目录执行：

```bash
python3 -m http.server 8765 --bind 127.0.0.1
# 浏览器访问 http://127.0.0.1:8765/preview.html
```

## 参考与来源

| 文件 | 来源 / 用途 |
| --- | --- |
| [character-turnaround.png](references/character-turnaround.png) | 用户提供的常规比例三视图；服装、配色与细节参考 |
| [chibi-turnaround.png](references/chibi-turnaround.png) | 用户提供的 Q 版三视图；本批主要角色与比例参考 |

原图按字节保留。欢迎立绘先生成，其余素材同时参考欢迎立绘和 Q 版原图，以维持角色一致。原作者和使用许可尚未随原图提供；此目录不另行声明 CC、MIT 或商用授权，也不沿用旧占位素材的 CC BY-NC-SA 许可。对外发行时由项目所有者补齐来源/授权记录。

`generated/`、`references/` 下的光栅图按 `.gitignore` 只保留在本地，不入库；入库记录是 `manifest.json`（尺寸、SHA-256、状态）与 `prompts.json`（提示词与输入顺序）。

## 前端选用

- `mascot_welcome.webp` 已导出并用于新会话空态（`app/src/main/kotlin/io/github/whywood/deepdeep/ui/chat/ChatScreen.kt`）；其余候选尚未接入，启动图标继续为自绘白色 d。
- 页面插画用 `ContentScale.Fit`，保留发饰与尾巴；由页面容器额外留出间距，避免紧贴文字。头像如需圆形裁切，先检查两侧鳍饰和头顶呆毛是否被截断。
- 此批原始文件较大，整库约 12.4 MiB（含参考图）；不要将原图和全部候选复制到 Android resources。选定后根据实际 dp、密度与包体预算导出 PNG/WebP，优先使用 `drawable-nodpi` 并显式设置布局尺寸。
- 鲸鱼标记是栅格插画候选；24 dp 工具栏图标仍优先用现有 Tabler 派生矢量图标。需要正式 launcher 时另做 Android adaptive icon 的前景、安全区和 monochrome 版本。
- 已目视检查角色、姿态、完整轮廓，并检查真实透明通道；细发丝仍有少量蓝色半透明边缘。最终显示尺寸、深色背景边缘和真机观感留给 UI 接入时验收。
- 图片仅表达状态；操作是否成功继续以工具真实结果和文字为准。纯装饰图不重复朗读，表达状态的图由页面提供本地化无障碍描述。

## 后续增补

沿用 `mascot_<用途>.png`；保留来源、提示词、尺寸和候选/已采用状态。需要新姿态时参考 Q 版原图与本批欢迎图。选中某张后再精修和导出，避免不断积累近似版本。
