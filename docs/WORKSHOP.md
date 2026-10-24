# 创意工坊

状态：已实现。每个创意是一个自包含的网页文件夹，由 Agent 编写，App 用 WebView 渲染成可交互的轻应用。

## 入口

- 抽屉「创意工坊」进入列表；右上角刷新图标重新扫描，`+` 新建。
- 列表按最近修改排序，行上显示名称与「类型 · 说明」；格式不符的文件夹显示为「格式无效」，可删除。
- 点任意行打开详情：全屏页面 + 顶部返回 / 名称 / ✨（让 Agent 修改）/ 更多（重新加载、重命名、删除）。

## 三种创意

| 类型 | 适合什么 | 页面行为 |
|---|---|---|
| 看板 `board` | 课程表、待办、日程等点击型工具 | 人类操作，数据经 bridge 持久化 |
| 工作台 `workstation` | AI 视频剪辑、提示词工作台等需要人类参与的工具 | 人类点击为主，可调用 Agent 生成中间结果 |
| 工作流 `workflow` | 打开即自动运行、人类只看进度/结果的流程页 | 页面自行调用 Agent，无需人类逐步操作 |

三种类型共用同一套渲染与 bridge 能力；区别在默认骨架和展示文案。`workflow` 在页面打开时自动运行页面里的逻辑（页面关闭即停止）；后台/定时执行属于后续能力。

## 创建与修改

两种方式，结果相同：

1. **在对话里让 Agent 创建**：例如「做一个学习看板创意」。Agent 会先建草稿、按格式写页面，最后调用注册工具上架；成功后聊天回复下方出现入口卡片，点一下就能打开。
2. **工坊页右上角 `+`**：填名称、选类型，直接生成一个可运行的起始页，再点 ✨ 让 Agent 继续做。

新建的创意默认带 `data.kv` 与 `agent.run` 两项权限；手工放进 `apps/` 的创意需要自己在 manifest 里声明。

修改已有创意：打开详情并点 ✨，输入需求（例如「改成深色并加周末课程」），Agent 会读取当前页面文件并直接修改，完成后页面自动刷新。看板/工作台的数据保存在 App 侧，重写页面不会丢失。

页面里的 JavaScript 还可以主动调用受限 Agent（见下方 `agent.run`），适合「按钮触发生成」这类交互；需要跑命令、处理媒体的重活请用详情页的 ✨。

## 文件夹格式

Agent 写入的位置（App 私有目录）：

```
files/workshop/
  build/<草稿>/          Agent 写作中的草稿，不在列表中显示
  apps/<id>/             已上架的创意
  userdata/<id>.db       每个创意的数据（与页面代码分开存放）
```

已上架创意目录：

```
apps/study-board/
  manifest.json          必需，应用元信息
  AGENTS.md              给 Agent 的格式与接口说明
  index.html             入口页面（可在 manifest 里改）
  style.css / app.js …   页面自己的静态资源
  assets/ …              图片、字体等
```

`manifest.json`：

```json
{
  "id": "study-board",
  "name": "学习看板",
  "type": "board",
  "icon": "📚",
  "version": "1.0.0",
  "entry": "index.html",
  "description": "课程表 + 待办，点按编辑",
  "permissions": ["data.kv", "agent.run"]
}
```

- `id`：只能用小写字母、数字、`-`、`_`，且与目录名一致。
- `name`：1–40 字；`description`：≤300 字；`icon`：一个 emoji，省略时按类型给默认值。
- `entry`：目录内的相对路径，必须以 `.html` 结尾。
- `permissions`：页面要用到的桥能力，当前支持 `data.kv`、`agent.run`。
- 页面必须是可独立运行的静态站点：移动端 viewport、相对路径引用资源、无构建步骤；支持 `prefers-color-scheme` 深浅色。

## 页面接口

App 会在页面脚本运行前注入 `window.deepdeep`（别名 `window.app`），并提供主题 CSS 变量。所有方法返回 Promise。

```js
const todos = await app.data.get("todos");
await app.data.set("todos", [...todos, { id: 1, text: "写完作业", done: false }]);

const answer = await app.agent.run({
  prompt: "根据课程表和待办安排今天的学习任务",
  context: { courses, todos },
  onEvent: (event) => renderProgress(event),
});

app.toast("已保存");
```

| 接口 | 说明 |
|---|---|
| `app.info()` | `{ app: { id, name, type, icon, version }, capabilities }` |
| `app.data.get(key)` / `app.data.set(key, value)` / `app.data.remove(key)` / `app.data.keys()` | 每个创意独立的键值数据，值可以是任意 JSON，单值上限 256KB |
| `app.agent.run({ prompt, context?, onEvent? })` | 调受限 Agent，返回最终文本；`onEvent` 收到 `started` / `text` / `tool` / `finished` |
| `app.agent.cancel()` | 取消当前运行 |
| `app.agent.onProgress(fn)` | 订阅运行事件，返回取消订阅函数 |
| `app.toast(text)` | 原生轻提示 |
| `app.log.error(message, stack?)` | 手动上报错误（未捕获异常会自动上报） |

- 每个创意同一时间只有一个 Agent 运行；运行中再次调用会返回 busy。
- 页面触发的 Agent 只能读写本创意的页面文件，不能读 App 其他数据、不能执行 shell 或 MCP；需要完整能力时用详情页的 ✨。
- 页面可以使用 `fetch` 读取本创意内的静态文件；写数据请走 `app.data`。

## 主题与错误

- 注入的 CSS 变量：`--dd-bg`、`--dd-ink`、`--dd-ink-secondary`、`--dd-accent`、`--dd-hairline`，同时设置 `color-scheme`；页面可直接使用，App 切换主题时同步更新。
- 未捕获异常、未处理的 Promise 拒绝与 `console.error` 会回报给 App：详情页 ✨ 按钮上出现红点，打开 Agent 面板可以「根据报错修复」，把堆栈交给 Agent 直接改；主框架加载失败则显示整页错误与重试。
- `alert` 会显示成原生轻提示；`confirm` / `prompt` / Service Worker 不可用，请用页面内 UI。

## 数据与安全

- 页面代码与数据物理分开：`apps/<id>/` 是代码，`userdata/<id>.db` 是数据（SQLite）。Agent 重写整个页面不会影响数据。
- 删除创意会删除页面目录和对应数据，无法恢复。
- 每个创意运行在独立虚拟域名下，只能访问自己的页面文件；页面发起的 Agent 调用受权限声明和路径沙箱限制。
- 创意内容由本机 Agent/用户生成；页面可以请求外部网络，但不会自动获得 App 的其他能力。后续能力按需扩展，并在 `manifest.json` 中声明权限。

## 限制

- 创意列表不提供导入/导出或市场；直接把合法目录放进 `apps/` 会被扫描到。
- 后台/定时运行工作流、媒体选择、完整 Agent 等能力尚未开放，后续按 App 能力逐步加入。
- 页面数据目前是键值存储，不支持 SQL 查询；需要复杂查询时可以在数据之上自行建索引。
