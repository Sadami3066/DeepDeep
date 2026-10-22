# UI design

## 参考

- Claude Android App：顶栏、右侧用户气泡、全宽 assistant、浮动 composer、Claude 式分组设置与层级推入。
- DSH Web chat：过程化展示思考、工具调用与结果；运行中展开、完成后收纳。

只借鉴布局与信息组织，不复制代码或素材；颜色、字体、图标仍使用本项目的「晴潮」设计系统。

## 配色

- 页面底色：浅白
- 卡片、抽屉与浮层：纯白
- 强调色：鲸蓝
- 层次只靠字重、不透明度、留白与发丝线；默认无阴影/渐变/毛玻璃。
- 分隔线统一 0.45 alpha 发丝线。

## 聊天

- 顶栏：菜单 / wordmark / 新对话 / 更多。
- 用户消息右对齐；assistant 全宽 Markdown。
- Composer：模型 chip、思考 chip、输入、发送/停止；无未实现按钮。
- 运行中固定状态行：打字点 + activity + 耗时，不随流式文字移动。
- 过程：运行中自动展开；工具/思考行默认收起；结束后过程自动收起，最终回复单独显示。
- 中间文本和工具按事件顺序进入过程；过程文本同样走 Markdown。
- 工具行极简：monospace 名称、参数预览、状态、耗时；展开才显示详情。
- Markdown：嵌套列表/任务、块级图片（异步加载 + 淡入 + 失败占位，点击全屏预览）、超宽表格横向滚动、代码语法高亮（语言标签 + 复制）、递归引用块、setext 标题、转义/硬换行、自动链接与引用式链接、内联 HTML（粗斜体、code、kbd、上下标、彩色 span）、数学降级渲染、脚注上标 + 文末列表、任务列表、引用条、有序列表起始值。
- 消息操作：长按任意消息在手指位置弹出白色菜单（复制 / 引用 / 助手消息加分享）；助手回答下方保留 copy/quote/share 图标行。
- 引用：引用内容进入 composer 顶部的引用预览（2dp 竖条 + 2 行省略 + X），发送后用户气泡内以竖条引用块展示，并作为 blockquote 前缀发给模型。
- 自动跟随：用户上拖后暂停并显示“回到底部”。

## 切换会话

- 抽屉是覆盖层，聊天内容保持常驻；抽屉用 draw-phase translation 滑入/滑出。
- 首次打开会话：旧内容隐藏，显示一个短转圈；历史加载完成后替换，不横向推入页面。
- 再次打开访问过的会话：立即展示内存中的消息快照（无转圈），SDK session 在后台打开；在此期间发送会等待 session 就绪后再提交。

## 性能

- `app/compose-stability.conf` 把 runtime 模型与 `ChatUiState` 标记为 stable；编译报告确认 ChatScreen / ChatTurn / ChatMarkdown 等全部 restartable + skippable，流式更新不会重组合所有可见消息。
- 按压 wash 的动画值只在 draw phase 读取（`DdPressable`）；图片淡入 alpha 只在 graphicsLayer 读取；Markdown inline span 与高亮结果按文本 remember。
- 流式 Markdown 解析保持 O(n)，10KB 解析有单测冒烟上界。

## 抽屉

- 顶部功能项带图标：聊天、已归档、新建对话。
- 历史项纯文字，无选中强调色；置顶区/历史区。
- 长按：菜单锚定在点击位置，白色浮层；重命名、置顶、归档、删除。
- 菜单与所有设置行共用 `DdMenu` / `DdPressable` / 圆角 wash，不使用方框 ripple。
- 菜单项左侧图标 + 左对齐文字，删除项统一 danger 红。
- 点按立即给 bounded ripple，长按给圆角 wash；两者都裁剪在卡片圆角内。
- 输入框在设置卡片内保留独立 16dp 外边距。

## 设置

Claude 式分组卡，无分组标题、无冗余说明；行只保留标题 + 右侧短值。真实项：Provider、运行环境、颜色模式、关于/许可。占位项 Toast 反馈。思考等级只在聊天 composer。

## 导航与动效

- 设置层级（Provider 列表/详情、运行环境）使用 `AnimatedScreenHost`：右侧 18% 推入 + 淡入，返回反向；系统返回手势触发 pop。
- `DdMotion` 统一时长/缓动；`DdTypingDots` 是唯一的“思考中”循环动画。
- 页面切换无尺寸回弹；按压 140ms 灰 wash；抽屉 260ms ease-out。
- 页面切换不用于会话切换——会话切换只替换内容和短 loading。
