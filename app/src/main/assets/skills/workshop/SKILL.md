---
name: workshop
description: 在 DeepDeep「创意工坊」里创建或修改 HTML/CSS/JS 轻量小应用（待办、课程表、纪念日、抽签、小游戏、动画、计算器、工具页等）。页面可以经 bridge 读写自己的数据，也可以用 app.agent 编排一次性 Agent（调查、写作、处理文件）搭建多步骤工作台。当用户想要一个手机上的创意/小应用/小页面/小工具，或要修改已有创意时使用。
---

# 创意工坊

创意工坊把 Agent 写的一个网页变成手机里的轻应用：用户在抽屉「创意工坊」打开它，像小程序一样使用。页面由你直接编写，没有预设分类或模板，形态完全由需求决定；不要套用「看板 / 工作台 / 工作流」这类分类。

工坊同时提供三层能力：

1. **页面数据**：页面通过 `app.data` 读写自己的 JSON KV；Agent 也可用 `workshop_edit` 直接更新数据，不用改页面。
2. **页面驱动 Agent**：页面 JS 用 `app.agent.models/capabilities/run` 编排一次性 Agent（可并行、可串行），拿最终文本，也可用 `onEvent` 接收增量事件。
3. **页面 UI**：HTML/CSS/JS 全部由你写，移动端优先、深浅色适配、无构建步骤。

## 什么时候使用

- 用户想要一个手机上的互动小页面、小应用或小工具：待办、课程表、日程、纪念日、抽签、小游戏、动画、计算器、记账、习惯打卡、工具页等。
- 用户想要一个多步骤工作台：表单输入 → 选模型 → Agent 调查/写作/处理 → 页面展示结果 → 再处理。
- 用户要修改、迭代已有创意。
- 普通报告、幻灯片、文档、图片不要用创意工坊，用 `present`。

## 快速开始

1. 理解需求；不确定的交互先一句话问清。
2. 调 `workshop_create`：`name`（1–40 字）、可选 `description`、可选 `icon`（emoji）。返回草稿绝对路径 `<draft>`。
3. 用 `read`/`write`/`edit` 的绝对路径在 `<draft>` 下写 `index.html`（入口）及 CSS/JS/资源。工坊 Agent 只能读写本次运行目录里的文件，不能执行命令。
4. 调 `workshop_load`（`path=<draft>`）。按报错修到成功；成功后聊天出现入口卡片。
5. 打开检查布局与交互，再告诉用户去「创意工坊」使用。

修改已有创意：`workshop_list` 找到 id 和路径；改界面/交互就编辑 `apps/<id>/` 文件；只改内容/数据用 `workshop_edit`。

## 文件夹格式

`manifest.json`：

```json
{
  "id": "heart-app",
  "name": "爱心",
  "icon": "💗",
  "version": "1.0.0",
  "entry": "index.html",
  "description": "点一下会跳动的爱心"
}
```

- `id` 工具生成，不要改；`name`、`icon`、`description` 可改；`entry` 默认 `index.html`。
- 目录内只放静态资源；不要放构建产物、node_modules、密钥或用户数据。
- 入口文件必须存在（`entry` 默认 `index.html`）；页面结构、文件数量由需求决定。

## 页面基础能力

页面脚本运行前会注入 `window.deepdeep`（别名 `window.app`），全部返回 Promise：

```js
const todos = await app.data.get("todos");
await app.data.set("todos", [...todos, { text: "写作业", done: false }]);
app.toast("已保存");
```

| 接口 | 说明 |
|---|---|
| `app.info()` | `{ app: { id, name, icon, version } }` |
| `app.data.get/set/remove/keys` | 本创意独立的 JSON KV，单值 ≤ 256KB |
| `app.toast(text)` | 原生轻提示 |
| `app.agent.*` | 页面驱动的 Agent，见下节 |

- 主题变量：`--dd-bg`、`--dd-ink`、`--dd-ink-secondary`、`--dd-accent`、`--dd-hairline`，并设置 `color-scheme`。
- `alert` 会变成原生 toast；`confirm`/`prompt`/Service Worker 不可用，用页面内 UI。
- 页面不能调用 App 其他内部函数、不能读写其他创意或 App 数据。

## 页面驱动 Agent

页面 JS 可以直接启动一次性 Agent 完成调查、写作、总结、文件处理等任务，并自己编排。

```js
const models = await app.agent.models();
// [{ providerId, modelId, label, thinkingLevels }]

const caps = await app.agent.capabilities();
// { tools: [{ name, description }], skills: [{ name, description }] }

const text = await app.agent.run({
  prompt: "调查 2026 年 AI 手机市场，返回 5 条带来源的要点",
  providerId: "deepseek",
  modelId: "deepseek-flash",
  thinkingLevel: "high",
  systemPrompt: "你是资深行业分析师，只输出结论和来源。",
  maxTurns: 8,
  tools: ["web_search", "web_fetch"],
  skills: [],
  onEvent(event) {
    if (event.type === "text_delta") output.textContent += event.text;
    if (event.type === "tool_start") status.textContent = `正在调用 ${event.name}`;
  }
});
```

- `onEvent` 可选：不传时行为不变，只是 `await` 最终文本（`Promise<string>`）。
- 事件类型：`run_start`、`text_delta`、`thinking_delta`、`tool_start`、`tool_end`、`turn_end`、`run_end`、`run_error`；终态事件一定是最后一个。
- `run_end` 带 `status`（`completed` / `truncated`）和 `truncated`；`run_error` 带 `code`（`timeout` / `agent_failed`）和 `message`，之后 Promise reject。
- 增量事件可能合并成更大的片段，按 `text` 追加，不要假设粒度。

- 每个 Agent 无会话、无历史；上下文由 JS 自己传递。
- 并行用 `Promise.all([...])`；串行用 `await` 后把结果拼进下一步 prompt。
- `providerId/modelId/thinkingLevel` 来自 `models()`；省略则用当前默认模型。
- `tools`/`skills` 名称来自 `capabilities()`；**省略 = 全部可用，`[]` = 不用**。
- `systemPrompt` 是本次任务约束；`maxTurns` 默认 16，范围 1–64，超限会中止并让 `run_end.truncated=true`。
- 生命周期绑定页面：页面关闭/返回会取消正在运行的 Agent；没有后台运行。需要结果就在页面里 await 完。
- 进度交给 `onEvent`（文本增量、工具调用）；其他界面状态由页面自己维护。
- 页面 Agent 当前不启用 MCP。
- `read`/`write`/`edit` 等文件工具只能读写本次运行目录，不能执行命令；联网查询用 `web_search`/`web_fetch`。
- 失败会 reject，并在 reject 前收到 `run_error`；用 `try/catch` 展示错误，不要吞掉。

完整 API、限制与错误码：`references/api-reference.md`。

编排模板：`references/tutorial.md`。

## Agent 侧数据更新

需要改已有创意的内容/数据时，不要重写页面，直接改数据：

| 操作 | 参数 | 说明 |
|---|---|---|
| `action=get` | `app`, `key` | 读取一个 key，不存在返回 null |
| `action=set` | `app`, `key`, `value`（任意 JSON） | 写入/覆盖，≤256KB |
| `action=delete` | `app`, `key` | 删除 key |
| `action=keys` | `app` | 列出全部 key（排序） |

- 数据是任意 JSON；表单 schema、字段、默认值都可以由你定义，页面按约定读取渲染。
- 页面正在打开时数据不会自动刷新；用户重新打开或点详情页「重新加载」。
- 日报/播客类：页面写一次，之后用 `workshop_edit` 的 `action=set` 更新按日期分的 key。

## 修改流程

- `workshop_list` 找 id/path。
- 改界面/交互/逻辑：编辑 `apps/<id>/` 文件，不需要重新 `workshop_load`。
- 改内容/数据：用 `workshop_edit`，不改页面。
- 重写页面不丢数据；不要直接改 `userdata` 下的数据库文件。

## 页面要求

- 移动端优先：`<meta name="viewport" content="width=device-width, initial-scale=1">`，flex/grid，响应式，不做固定桌面宽度。
- 无构建步骤、资源用相对路径、不依赖外部登录；外部 fetch 必须有失败兜底。
- 深浅色可读：颜色优先用主题变量；必要时 `prefers-color-scheme` 兜底。
- 交互真实：按钮反馈、空态、错误提示、输入可用；持久化数据走 `app.data`，不要只依赖 localStorage。
- 控制体积：不引入大库/大字体；图标用 emoji 或内联 SVG。
- 不把 API Key、Cookie、用户隐私写进页面或 prompt。
- 页面 Agent 只在用户操作后启动（按钮），不要在页面加载时自动跑；不要让一个页面无限循环启动 Agent。

## 文档与样例

所有参考文件都在本技能目录下，路径相对 `SKILL.md`：

- `references/api-reference.md`：完整 bridge、页面 Agent、数据工具参数与错误码。
- `references/tutorial.md`：从零搭建多步骤 Agent 工作台的完整教程与编排模式。
- `references/design-checklist.md`：移动端/深浅色/错误处理/性能检查清单。
- `examples/01-hello.html`：最小页面（数据、toast、主题）。
- `examples/02-agent-workbench.html`：表单 → 选模型 → 运行 Agent → 结果 → 再处理。
- `examples/03-agent-pipeline.html`：并行调查 Agent + 总写 Agent。
- `examples/04-daily-report.html`：数据驱动日报 + 用 Agent 刷新内容。

样例是模板，先 `read` 再按需求改写；不要直接引用技能目录里的文件路径，要把内容写进创意目录。

## 完成前自测

- `workshop_load` 成功，入口能打开。
- 核心交互、空态、错误态可用；深浅色可读；小屏不溢出。
- 页面 Agent 的模型列表、loading、错误、结果都正常；需要时增量事件能驱动进度显示。
- 页面重开数据还在；不依赖 localStorage 作为唯一数据源。
- 没有在页面加载时自动调用 Agent。
