# 创意工坊

状态：已实现。一个创意就是 Agent 写的一个自包含网页文件夹（HTML/CSS/JS/JSON），App 用 WebView 把它渲染成手机里的轻应用；没有预设分类，做什么完全由对话里的需求和 Agent 决定（爱心动画、待办、课程表、小游戏、计算器……都可以）。

## 入口

- 抽屉「创意工坊」进入列表；右上角刷新图标重新扫描，`+` 直接新建一个 Home 会话，并在输入框预填 `/skill:workshop `。
- 列表按最近修改排序，行 = 创意 icon（manifest 里的 emoji，缺省为 ✦）+ 名称 + 说明；格式不符的目录显示「格式无效 · 原因」，可删除。
- 点行打开详情：全屏页面 + 顶部返回 / 名称 / 更多（重新加载、重命名、删除）。详情页只读，App 界面不提供编辑或 Agent 入口。

## 创建与修改

创建由普通的 DeepDeep 会话完成，不需要额外入口或分类：

1. 在会话输入框里用 `/skill:workshop ` 开头；工坊页 `+` 会自动打开这样的新会话并预填。
2. `/skill:workshop` 会把内置的 workshop 技能完整读入当前会话（技能文件同时出现在「技能」页，可查看），DeepDeep 随即知道创意工坊的格式、工具和页面规范。
3. 后面直接描述你想做的东西，例如「做一个点一下会跳动的爱心」「做一个本周课程表，可以自己加课」；Agent 会调用 `workshop_create` 建草稿、用文件工具写页面、调用 `workshop_load` 装载进 App，然后在聊天里给出入口卡片。
4. 点卡片或到「创意工坊」列表打开。

修改已有创意分两种：

- **改界面/交互**：从工坊退出回到聊天，直接说「把那个爱心改成点两下才跳」。Agent 会用 `workshop_list` 找到目录并编辑页面文件；用户重新打开就能看到新版本，不需要重新「安装」。
- **只改内容/数据**：例如日报每天更新。Agent 用 `workshop_data_get/set/delete/keys` 直接读写该创意的数据，不重写页面；用户重新打开时读到新数据。详见下方「数据工具」。

## 斜杠命令

| 命令 | 作用 |
|---|---|
| `/skill:<name> [需求]` | 强制加载任意已启用技能（全局或当前工作区），技能内容会以 `<skill>` 块进入当前会话；未知技能会提示 |

- 这种「强制加载」把技能文件的完整内容作为本轮上下文的一部分发给模型，而不是只依赖系统提示里的技能目录。
- 新会话默认已经能看到已启用技能的目录；`/skill:` 用于确保某个技能一定被读入，尤其适合技能很多或刚改过技能文件时。
- workshop 技能随 App 内置，App 启动时确保它在技能仓库与全局生效目录里；更新 App 会刷新内容。

## 数据工具

每个创意的数据层是独立 JSON KV，页面打开时通过 bridge 读取；Agent 也有一组工具可以直接读写它，不需要改页面：

| 工具 | 参数 | 语义 |
|---|---|---|
| `workshop_data_get` | `app`, `key` | 读取一个 key，不存在返回 null |
| `workshop_data_set` | `app`, `key`, `value`（任意 JSON） | 写入/覆盖；单值 ≤ 256KB |
| `workshop_data_delete` | `app`, `key` | 删除 key |
| `workshop_data_keys` | `app` | 列出已有 key（排序） |

- `app` 是 `workshop_list` 返回的创意 id；数据与页面代码物理分离，重写页面不丢数据，改数据也不动页面文件。
- 表单结构、字段、默认值都由 Agent 用任意 JSON 定义（例如把 schema 存在 `schema` key，页面按它渲染）；App 不限制逻辑结构。
- 更新数据前可以先 `workshop_data_get`/`workshop_data_keys` 看现有结构，避免覆盖未知字段；单值超限时拆成多个 key。
- 数据工具写入后不需要 `workshop_load`；页面正在打开时不会自动刷新，重新打开或点详情页「重新加载」即可。
- 数据仍保存在每创意独立的 SQLite（`files/workshop/userdata/<id>.db`），Agent 不需要也不应该直接改数据库文件。

## 页面驱动 Agent

页面 JS 可以直接启动一次性 Agent 完成任务并在自己的流程里编排。App 只提供底层接口并校验参数；调用几个 Agent、串行还是并行，全部由页面 JS 决定。

```js
const models = await app.agent.models()
// [{ providerId, modelId, label, thinkingLevels }]

const caps = await app.agent.capabilities()
// { tools: [{ name, description }], skills: [{ name, description }] }

const text = await app.agent.run({
  prompt: "调查 2026 年 AI 手机市场，返回 5 条带来源的要点",
  providerId: "deepseek",
  modelId: "deepseek-flash",
  thinkingLevel: "high",
  systemPrompt: "你是资深行业分析师，只输出结论和来源。", // 可选
  maxTurns: 8,
  tools: ["web_search", "web_fetch"], // 省略 = 全部可用；[] = 无工具
  skills: [],                         // 省略 = 全部已启用；[] = 无技能
  onEvent(event) {                    // 可选：增量事件
    if (event.type === "text_delta") output.textContent += event.text;
    if (event.type === "tool_start") status.textContent = `正在调用 ${event.name}`;
  }
})
```

- 返回值是最终文本（`Promise<string>`）。`onEvent` 可选，事件类型：`run_start`、`text_delta`、`thinking_delta`、`tool_start`、`tool_end`、`turn_end`、`run_end`、`run_error`；终态事件（`run_end` / `run_error`）一定是最后一个，随后 Promise 才 resolve / reject。
- `run_end` 带 `status`（`completed` / `truncated`）和 `truncated`；`run_error` 带 `code`（`timeout` / `agent_failed`）和 `message`。增量事件可能合并成更大片段，按 `text` 追加即可。
- 并行：`Promise.all([app.agent.run(...), app.agent.run(...)])`；串行：把上一步返回的文本拼进下一步 prompt。每个 Agent 是一次性、无会话的，彼此不共享历史，上下文完全由 JS 传递。
- Agent 生命周期绑定页面：页面关闭/返回/WebView 销毁会取消正在运行的 Agent；不提供后台编排，页面需要结果就在离开前 `await` 完。
- `models()` 只返回已配置的 provider/model；页面负责模型选择 UI，App 在运行时校验。
- `tools` / `skills` 名称必须来自 `capabilities()`；省略表示全部可用，空数组表示完全不用。页面可以提供默认 Agent 的完整工具/技能，也可只选子集。
- 页面 Agent 的能力是显式白名单：文件工具 `read`、`write`、`edit`、`ls`、`grep`、`find`，联网查询 `web_search`、`web_fetch`（装了网络搜索插件时）；没有 bash，也没有 GUI、日历、小米云、job、goal 等 App 工具。文件工具只能读写本次运行的临时目录，`../`、绝对路径和指向目录外的符号链接都会被拒绝；构造这类路径属于错误用法，页面应把数据通过 prompt 传进去。
- 页面 Agent 不启用 MCP；临时工作区与临时 agent 目录在每次运行后清理，不写入聊天会话列表。
- `systemPrompt` 会追加到本次 Agent 的系统提示；`maxTurns` 默认 16，超过后中止并让 `run_end.truncated=true`；失败会在 `run_error` 后 reject，带错误信息。

## 文件夹格式

Agent 使用的位置（App 私有目录）：

```
files/workshop/
  build/<草稿>/          Agent 写作中的草稿，不在列表中显示
  apps/<id>/             已上架的创意
  userdata/<id>.db       每个创意的数据（与页面代码分开存放）
```

已上架创意目录：

```
apps/heart-app/
  manifest.json          必需，应用元信息
  index.html             入口页面
  style.css / app.js …   页面自己的静态资源
```

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

- `id` 由工具生成、与目录名一致；`name` 1–40 字；`icon` 是一个 emoji（可省略）；`entry` 默认 `index.html`。
- 没有类型、分类或权限字段；旧版本 manifest 里的 `type` / `permissions` 会被忽略，不影响打开。
- 页面必须是可独立运行的静态站点：移动端 viewport、相对路径引用资源、无构建步骤；支持深/浅色。
- 页面数据不要写进代码目录；持久化数据走 bridge，存在 App 侧 SQLite。

## 页面接口

App 会在页面脚本运行前注入 `window.deepdeep`（别名 `window.app`），所有方法返回 Promise：

```js
const todos = await app.data.get("todos");
await app.data.set("todos", [...todos, { text: "写作业", done: false }]);
app.toast("已保存");
```

| 接口 | 说明 |
|---|---|
| `app.info()` | `{ app: { id, name, icon, version } }` |
| `app.data.get(key)` / `.set(key, value)` / `.remove(key)` / `.keys()` | 每个创意独立的 JSON 键值数据，单值上限 256KB |
| `app.toast(text)` | 原生轻提示 |
| `app.agent.models()` / `.capabilities()` / `.run(options)` | 页面驱动一次性 Agent，见「页面驱动 Agent」 |

- 主题变量：`--dd-bg`、`--dd-ink`、`--dd-ink-secondary`、`--dd-accent`、`--dd-hairline`，同时设置 `color-scheme`；页面用它跟随 App 深浅色。
- `alert` 会显示成原生轻提示；`confirm` / `prompt` / Service Worker 不可用，请用页面内 UI。
- 页面不能调用其他 App 内部函数、不能读写其他创意或 App 数据；页面可以按上方 `app.agent.*` 启动一次性任务 Agent；需要改页面时退出创意，在聊天里说。

## 数据与安全

- 代码与数据物理分开：`apps/<id>/` 是代码，`userdata/<id>.db` 是数据；Agent 重写整个页面不影响数据。
- 删除创意会删除页面目录和对应数据，无法恢复。
- 每个创意运行在独立虚拟域名下，只能访问自己的页面文件；页面可以请求外部网络，但不会自动获得 App 的其他能力。
- 创意内容由本机 DeepDeep/用户生成；不要在页面或代码里写 API Key、Cookie 等敏感信息。

## 限制

- 页面内不支持编辑页面代码；修改统一回到聊天。
- 创意列表不提供导入/导出或市场；把合法目录直接放进 `apps/` 会被扫描到。
- 数据目前是键值存储，不支持 SQL 查询；后台/定时运行、媒体选择等能力后续按需扩展。
- 内置 workshop 技能随 App 更新，用户对它的修改可能在下次启动时被刷新；技能目录包含 `references/` 完整 API/教程/设计清单和 `examples/` 可运行样例，Agent 写创意时会按需读取。
