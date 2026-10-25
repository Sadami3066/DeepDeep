# 页面设计检查清单

## 移动端与视觉

- `<meta name="viewport" content="width=device-width, initial-scale=1">`，`body` 无横向滚动。
- 最大内容宽度约 680px，居中；小屏两侧至少 16px 留白。
- 使用主题变量而不是写死颜色：`--dd-bg`、`--dd-ink`、`--dd-ink-secondary`、`--dd-accent`、`--dd-hairline`。
- 深浅色都可读；必要时用 `@media (prefers-color-scheme: dark)` 兜底。
- 按钮至少 44px 高；输入框有 label/placeholder；点击有 active/focus 反馈。
- 不依赖 hover；触摸操作优先。
- 图标用 emoji 或内联 SVG；不加载大字体、大图、大库。
- 空态、加载态、错误态都要有文字，不要只留空白。

## 状态与交互

- 明确当前步骤；多步骤工作台用 section 切换，提供“上一步/重新开始”。
- 运行 Agent 时禁用重复触发的按钮，并显示“正在…”。
- 运行失败保留用户输入和已有结果，提供重试。
- 数据写入失败（例如超过 256KB）要有提示。
- 页面重开时从 `app.data` 恢复必要状态；不要假设内存状态还在。
- 不要在页面加载时自动启动 Agent 或消耗 tokens。

## 页面 Agent

- 模型列表用 `app.agent.models()` 动态生成，不写死 provider/model。
- 工具/技能名称只用 `app.agent.capabilities()` 返回的名称。
- 不需要工具时显式传 `tools: []`，不要依赖“省略=全部”。
- 需要调查时显式请求 `web_search`/`web_fetch`，并在 prompt 里要求来源。
- 用 `onEvent` 驱动进度（`text_delta` 追加文本、`tool_start` 显示工具名）；不传则用 loading，最终以 Promise 的文本为准。
- 并行用 `Promise.all`；串行把上一步结果拼进下一步 prompt。
- 关闭页面会取消 Agent；长任务在页面内提示用户不要中途离开。
- 错误用页面内文本展示，保留 `error.message` 的关键信息。
- 不要把用户隐私、API Key、Cookie 放进 prompt。

## 数据

- 持久数据统一用 `app.data`；localStorage 最多做缓存。
- key 用小写字母/数字/`-`/`_`，带业务前缀，例如 `workbench_state`。
- 单值 ≤256KB；大列表拆 key 或压缩字段。
- 写入前读取现有结构，避免覆盖未知字段。
- 页面代码文件和数据分离；不要用页面目录保存用户数据。
- 删除/重置操作确认一次，避免误触。

## 性能

- 静态资源用相对路径引用；单个页面不要一次加载用不到的脚本。
- 不引入 React/Vue 等运行时，除非需求确实需要且体积可控。
- 列表只渲染必要数量；大列表做分页/懒加载。
- 动画用 CSS transform/opacity；避免长时间高负载动画。
- 页面启动时不要并行发起大量网络请求。

## 安全与边界

- 页面不能调用 App 其他内部函数、不能读其他创意或 App 数据。
- 不执行远程脚本；不把不可信文本当 HTML 插入（用 `textContent`）。
- 外部链接用普通 `<a href target="_blank">`。
- 不上传用户数据到未声明的第三方；prompt 中不要包含敏感信息。
- 页面内 Agent 的能力由你选择的 tools/skills 决定；只请求完成当前任务所需的最小集合。

## 完成前自测

- `workshop_load` 成功，入口打开无白屏。
- 表单/按钮/空态/错误态/结果都能操作。
- 深色、浅色、小屏、键盘弹出都不破版。
- 页面重开数据还在。
- 模型列表、Agent 运行、增量事件进度、失败重试都正常。
- 没有页面加载时自动运行 Agent。
