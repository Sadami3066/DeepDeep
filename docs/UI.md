# UI design

## 参考

- Claude Android App：顶栏、右侧用户气泡、全宽 assistant、浮动 composer、Claude 式分组设置与层级推入。
- DSH Web chat：过程化展示思考、工具调用与结果；运行中展开、完成后收纳。

只借鉴布局与信息组织，不复制代码或素材；颜色、字体、图标仍使用本项目的「晴潮」设计系统。

## 配色

- 页面底色：浅白
- 卡片、抽屉与浮层：纯白
- 强调色：鲸蓝
- 层次只靠字重、不透明度、留白与发丝线；常规面不用阴影/渐变/毛玻璃，仅开关 thumb 与分段选中 pill 带柔和投影。
- 分隔线统一 0.45 alpha 发丝线。

## 聊天

- 顶栏：菜单 / wordmark / 新对话 / 更多。顶栏 + 固定以 Home 为 cwd 新建会话；工作区行展开后的 + 在该工作区新建。
- 用户消息右对齐；assistant 全宽 Markdown。
- Composer：模型 chip、思考 chip、输入、发送/停止；无未实现按钮。
- 运行中固定状态行：打字点 + activity + 耗时，不随流式文字移动。
- 过程：运行中自动展开；工具/思考行默认收起；结束后过程自动收起，最终回复单独显示。
- 中间文本和工具按事件顺序进入过程；过程文本同样走 Markdown。
- 工具行极简：monospace 名称、参数预览、状态、耗时；展开才显示详情。
- Markdown：嵌套列表/任务、块级图片（异步加载 + 淡入 + 失败占位，点击全屏预览）、超宽表格横向滚动、代码语法高亮（语言标签 + 复制）、递归引用块、setext 标题、转义/硬换行、自动链接与引用式链接、内联 HTML（粗斜体、code、kbd、上下标、彩色 span）、数学降级渲染、脚注上标 + 文末列表、任务列表、引用条、有序列表起始值。
- 选择文本：长按 assistant 回答或用户气泡进入系统文本选择（可跨段落/代码/列表），拖动左右手柄可调整范围；自绘选择工具条使用文字标签：复制 / 全选 / 引用 / 朗读（全选后菜单保留，可继续操作）；点选区外或点其他消息自动取消选择并收起菜单。不再使用长按消息菜单。助手回答下方保留 copy/quote/share 图标行。
- 引用：工具条或图标行的引用把选中文字送入 composer 顶部的引用预览（2dp 竖条 + 2 行省略 + X），发送后用户气泡内以竖条引用块展示，并作为 blockquote 前缀发给模型。
- 朗读：选中文字用系统 TTS 从头播放，再次朗读或离开页面会被新朗读覆盖。
- 设置：主题使用分段控件（自动/浅色/深色）；「交互」组提供触感反馈、发送后收起键盘、流式输出跟随、显示思考过程、过程默认展开 5 个开关，均即时生效并持久化。
- 键盘：IME 高度变化逐帧驱动聊天内容上移/回落，不做「键盘完全展开后二次跳动」；键盘弹起时打开抽屉也保持平滑。
- 附件：composer 回形针打开系统文件选择器（可多选）；选中后在输入框上方显示 Claude 式预览卡（图片缩略图 / 文本内容片段 / 文件图标 + 名称），右上角圆形 X 可移除，最多 5 个；发送后附件在气泡外单独横向排列：图片与文件统一 140dp 方形卡（图片按裁切填满、文件卡带扩展名徽标），下方才是文字气泡（回复引用仍在气泡内）。文件复制到 workspace/attachments 供 agent 用 read/bash 读取，消息里以 `<attachments>` 块携带路径。
- 附件预览：点击气泡附件或 composer 预览卡打开全屏预览；图片直接渲染、`.md` 用聊天同款 Markdown 渲染、其他文本等宽滚动、其他类型显示“无法预览此文件”；右上角分享按钮通过 FileProvider 调起系统分享。
- 文件引用：assistant 消息中的 `attachments/…`、`workspace/…`、`/…/files/workspace/…`、`file://` 路径、行内 code 路径和指向这些路径的 markdown 链接都渲染为可点击链接，点击按 workspace 解析后打开预览；文件不存在时 Toast 提示。
- 自动跟随：用户上拖后暂停并显示“回到底部”。

## 切换会话

- 抽屉是覆盖层，聊天内容保持常驻；抽屉用 draw-phase translation 滑入/滑出。wordmark 右侧搜索图标进入独立会话搜索页。
- 工作区直接纵向排列（Home 作为默认工作区在列），每个工作区行右侧是展开/收起 chevron；点击行展开该工作区的会话，可同时展开多个工作区。没有「历史对话 / 置顶」分组小标题，也没有当前工作区/当前会话的强调色。
- 工作区支持置顶：长按工作区 → 置顶/取消置顶，置顶工作区排在列表最前并显示小 pin 图标；Home 不可重命名/删除。
- 会话置顶只在所属工作区内生效：置顶会话排在该工作区最前，行尾用 13dp pin 图标替代相对时间；运行中的会话行尾显示 12dp spinner。
- 工作区行展开时行尾出现 +，在该工作区新建会话；长按工作区菜单：置顶/取消置顶、新建会话，非 Home 追加重命名/删除。长按会话：重命名、置顶、归档、删除。
- 工作区与会话的展开/收起用 LazyColumn item 动画；首次打开会话显示短转圈，历史加载完成后替换，不横向推入页面。
- 再次打开访问过的会话：立即展示内存中的消息快照（无转圈），SDK session 在后台打开；在此期间发送会等待 session 就绪后再提交。

## 性能

- `app/compose-stability.conf` 把 runtime 模型与 `ChatUiState` 标记为 stable；编译报告确认 ChatScreen / ChatTurn / ChatMarkdown 等全部 restartable + skippable，流式更新不会重组合所有可见消息。
- 按压 wash 的动画值只在 draw phase 读取（`DdPressable`）；图片淡入 alpha 只在 graphicsLayer 读取；Markdown inline span 与高亮结果按文本 remember。
- 流式 Markdown 解析保持 O(n)，10KB 解析有单测冒烟上界。

## 抽屉

- 顶部功能项带图标：聊天、已归档、新建对话、技能、插件、定时任务。
- 工作区标题行右侧 + 新建工作区；下方为工作区树，纯文字、无选中强调色；展开状态只靠行尾 chevron 的旋转表达。
- 长按菜单锚定在点击位置，白色浮层；菜单项左侧图标 + 左对齐文字，删除项统一 danger 红。
- 工作区行右侧自左至右：置顶 pin（已置顶时）、新建会话 +（展开时）、chevron（展开时旋转 90°）。
- 所有滚动列表项插入/移除使用 `Modifier.animateItem()`，无 Material 默认 loading/ripple。

## 控件

- `DdSwitch`：52×32dp 轨道、24dp thumb、thumb 柔和阴影、弹簧滑动，无 ripple。
- `DdSegmentedControl`：wash 容器 + 选中 pill 柔和阴影、弹簧位移；点击不显示按压反馈（无方框 ripple）。
- `DdDialog`：自写居中卡片（raised、24dp 圆角、标题 heading、右对齐文字操作），不使用 Material AlertDialog；键盘弹起时窗口随 IME 上移，操作按钮始终可见。
- `DdSearchField`：统一胶囊搜索框（raised、发丝边框、搜索图标、清除按钮、可选 Esc 回调），用于会话搜索、归档搜索与 Provider 搜索。
- `DdTextField`：统一输入框（聚焦 accent 边框动画、密码变换、IME Done），用于 Provider API Key 与重命名输入。
- `DdSpinner`：自写旋转弧线 loading，替代所有 Material CircularProgressIndicator。

## 设置

Claude 式分组卡，无分组标题、无冗余说明；行只保留标题 + 右侧短值。真实项：Provider、运行环境、颜色模式、关于/许可。占位项 Toast 反馈。思考等级只在聊天 composer。

- 颜色模式用分段控件（跟随系统/浅色/深色）；「交互」组 5 个开关均即时生效并持久化。
- 运行环境页用同一套分组行：Linux 开关（DdSwitch）、占用空间（标题 + 右值）、重置（确认用 DdDialog）；不再嵌套第二层卡片。
- Provider 详情：已保存 API Key 时输入框占位显示掩码（如 `sk-*****`），输入新 Key 才替换；Provider 列表搜索使用统一 `DdSearchField`。

## 导航与动效

- 设置层级（Provider 列表/详情、运行环境）使用 `AnimatedScreenHost`：右侧 18% 推入 + 淡入，返回反向；系统返回手势触发 pop。
- `DdMotion` 统一时长/缓动；`DdTypingDots` 是唯一的“思考中”循环动画。
- 页面切换无尺寸回弹；按压 140ms 灰 wash；抽屉 260ms ease-out。
- 页面切换不用于会话切换——会话切换只替换内容和短 loading。
