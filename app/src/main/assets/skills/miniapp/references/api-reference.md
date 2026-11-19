# 小程序 API 参考

本文件是 miniapp 技能的详细参考；路径相对 `SKILL.md` 所在目录。

## manifest.json

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

| 字段 | 必需 | 说明 |
|---|---|---|
| `id` | 是 | 工具生成，1–48 位小写字母/数字/`-`/`_`；不要修改 |
| `name` | 是 | 1–40 字，列表标题 |
| `icon` | 否 | 一个 emoji；缺省显示 ✦ |
| `version` | 否 | 默认 1.0.0 |
| `entry` | 否 | 默认 index.html，必须真实存在 |
| `description` | 否 | 列表里的一句说明，≤300 字 |

入口及静态资源放在同一目录；不要放构建产物、node_modules、密钥或用户数据。

## 页面 bridge

页面启动前注入 `window.deepdeep`，别名 `window.app`。所有异步接口返回 Promise。

### app.info()

```js
const info = await app.info();
// { app: { id, name, icon, version } }
```

### app.data

本小程序独立的 JSON KV，页面和 Agent 数据工具共享同一份数据。

```js
await app.data.set("todos", [{ text: "写作业", done: false }]);
const todos = await app.data.get("todos");       // 不存在时为 null
await app.data.remove("todos");                   // 返回是否删除
const keys = await app.data.keys();               // 字符串数组
```

- key：1–64 位小写字母/数字/`-`/`_`，以字母或数字开头。
- 单值 JSON 字符串 ≤ 256KB；超限整个请求失败。
- 值可以是任意 JSON（对象、数组、字符串、数字、布尔、null）。
- 页面数据与代码物理分离；重写页面文件不会清空数据。

### app.toast

```js
app.toast("已保存");
```

原生轻提示，≤200 字。

### 主题与浏览器 API

- CSS 变量：`--dd-bg`、`--dd-ink`、`--dd-ink-secondary`、`--dd-accent`、`--dd-hairline`；同时设置 `color-scheme`。
- `alert(msg)`：被映射成 `app.toast`，不阻塞。
- `confirm` / `prompt` / Service Worker：不可用；用页面内弹层、输入框和状态实现。
- 普通 `fetch` 可用；失败要有兜底。
- `localStorage` 可做缓存，但不要作为唯一数据源；持久数据用 `app.data`。

## 系统能力

除数据、toast 和 Agent 外，页面可调用宿主系统能力。全部返回 Promise，失败时 reject；敏感能力首次调用会弹系统授权。

### app.env()

```js
const env = await app.env();
// {
//   bridgeVersion: 2,
//   capabilities: ["env", "notify.show", "files.read", ...],
//   locale: "zh-CN",
//   fontScale: 1.0,
//   safeArea: { top: 24, bottom: 48, left: 0, right: 0 }
// }
```

`capabilities` 可用于能力探测。`safeArea` 是全屏或手势区占用的像素，需要贴边布局时自行留白。

### app.on / app.off

```js
const stop = app.on("theme", e => document.body.dataset.dark = String(e.dark));
app.on("visibility", e => { if (!e.visible) pauseTimers(); });
app.on("open", e => { if (e.note) showFromNotification(e.note); });
```

| 事件 | 字段 | 触发 |
|---|---|---|
| `theme` | `dark` | 系统深浅色切换 |
| `visibility` | `visible` | 页面进入/离开前台 |
| `open` | `note`（可能为空） | 点通知或深链打开本小程序 |

### 通知与提醒

```js
await app.notify.show({ title: "喝水", body: "该喝水了", id: "water" });
await app.notify.schedule({ id: "daily", title: "背单词", body: "今天还没打卡", at: Date.now() + 3600_000, repeat: "daily" });
const reminders = await app.notify.list();
await app.notify.cancel("water");
await app.notify.cancelAll();
```

- `show` 立即发通知，返回 id；省略 `id` 时自动生成。
- `schedule` 的 `at` 是毫秒时间戳，必须在一小时内到一年内；`repeat: "daily"` 表示每天同一时刻重复。提醒落盘，页面关闭或 App 重启后仍然生效，点通知回到本小程序（`app.on("open")`）。
- 每个小程序最多 64 条提醒；通知权限被拒时 reject `permission_denied`。

### 文件

```js
const files = await app.files.pick({ types: ["text/csv", "text/plain"], multiple: true });
// [{ name, path, size, mimeType }]
const text = await app.files.read(files[0].path);
await app.files.write("notes/today.txt", "hello");
await app.files.write("img/cover.bin", base64, "base64");
const url = await app.files.url("img/cover.bin");
const entries = await app.files.list("notes");
await app.files.export("notes/today.txt");
await app.files.delete("notes/today.txt");
```

- `path` 是相对小程序文件区的相对路径，不能是绝对路径或包含 `..`。
- `read` 默认按 UTF-8 文本返回；二进制用 `encoding: "base64"`，得到 `base64` 字段。`write` 同理。
- 单文件 ≤16MB、总计 ≤100MB；`url()` 返回可直接用于 `<img>`/`<audio>`/`<video>` 的本地地址。
- `export` 打开系统保存面板，`pick` 打开系统文件选择器。

### 分享与剪贴板

```js
await app.share.text("分享内容");
await app.share.file("notes/today.txt", "text/plain");
await app.clipboard.write("已复制");
const pasted = await app.clipboard.read();
```

### 媒体

```js
const images = await app.media.pick({ type: "image", multiple: false });
const photo = await app.media.capture();          // 拍照，返回文件引用
await app.media.saveToGallery("photos/cover.jpg");
const text = await app.media.scanQr();            // 扫码，返回文本
```

- `pick` 的 `type` 为 `image`（默认）、`video` 或 `any`；选中文件会导入小程序文件区。
- `capture` 打开相机，取消返回 `null`；拍照后可用 `files.url()` 展示。
- `saveToGallery` 把文件写入系统相册（图片或视频）。

### 语音

```js
await app.speech.speak("今天的天气不错", "zh");
await app.speech.stop();
const heard = await app.speech.listen({ timeoutMs: 10000 }); // { text }
```

`listen` 首次调用请求麦克风权限，返回识别文本；超时或识别失败会 reject。

### 打开链接与定位

```js
await app.openUrl("https://example.com");        // http/https/mailto 等，不能是 file/content/javascript
const location = await app.location.current();   // { latitude, longitude, accuracyM, provider, time }
```

### 日历

```js
const events = await app.calendar.list({ from: Date.now(), to: Date.now() + 7 * 86400000, limit: 20 });
await app.calendar.add({
  title: "复习",
  start: Date.now() + 3600_000,
  end: Date.now() + 7200_000,
  allDay: false,
  notes: "线性代数",
  location: "图书馆"
});
```

首次调用请求日历读写权限；事件字段为 `{ id, calendarId, title, start, end, allDay, notes?, location? }`。

### 触感与屏幕

```js
app.haptic("light");        // light | medium | heavy
app.keepScreenOn(true);
```

### 错误

Promise reject，`Error.code` 可能为：

| code | 含义 |
|---|---|
| `invalid_params` | 参数不合法（缺少必填、范围超限、路径非法等） |
| `permission_denied` | 用户拒绝授权或系统权限未开启 |
| `not_found` | 文件或目录不存在 |
| `too_large` | 超出单文件、总量、数量和提醒上限 |
| `cancelled` | 用户取消选择 |
| `unavailable` | 当前设备或环境不支持该能力 |
| `host_unavailable` | 宿主未提供对应能力（异常情况） |
| `failed` | 系统调用失败（通知、保存、语音等） |

## 页面驱动 Agent

页面 JS 可以启动一次性、无会话的 Agent，自己决定数量、并行/串行和每个 Agent 的参数。生命周期绑定页面：页面关闭会取消正在运行的 Agent；没有后台执行。

### app.agent.models()

```js
const models = await app.agent.models();
// [
//   { providerId: "deepseek", modelId: "deepseek-flash",
//     label: "DeepSeek V4.1 Flash", thinkingLevels: ["off","low","high","max"] }
// ]
```

只返回当前已配置 provider 的模型；页面用它做模型选择 UI。

### app.agent.capabilities()

```js
const caps = await app.agent.capabilities();
// {
//   tools:  [{ name: "web_search", description: "..." }, ...],
//   skills: [{ name: "some-skill", description: "..." }, ...]
// }
```

返回当前页面 Agent 可请求的完整工具/技能目录（含编码工具、当前配置的命令/插件/MCP，以及可用 skills）。`tools`/`skills` 参数只能使用这里的名称。

### app.agent.run(options)

```js
const text = await app.agent.run({
  prompt: "…",                  // 必需
  providerId: "deepseek",       // 可选；与 modelId 成对
  modelId: "deepseek-flash",
  thinkingLevel: "high",        // 可选；off|low|medium|high|max
  systemPrompt: "…",            // 可选；本次任务约束
  maxTurns: 8,                  // 可选；1–64，默认 16
  tools: ["web_search"],        // 可选；省略=全部，[]=无工具
  skills: [],                   // 可选；省略=全部，[]=无技能
  onEvent(event) { … }          // 可选；接收增量事件
});
```

参数与限制：

| 参数 | 类型 | 说明 |
|---|---|---|
| `prompt` | string | 必需，非空，≤64KB |
| `providerId` / `modelId` | string | 可选成对；必须是 `models()` 返回的已配置模型；省略用默认模型 |
| `thinkingLevel` | string | 可选；`off`/`low`/`medium`/`high`/`max`，必须在模型支持列表里 |
| `systemPrompt` | string | 可选，≤16KB；追加到系统提示 |
| `maxTurns` | number | 可选，1–64，默认 16；超限中止本轮 |
| `tools` | string[] | 可选；省略=全部可用，空数组=完全不用；名称必须在 `capabilities().tools` 里，≤100 个 |
| `skills` | string[] | 可选；同上，空数组=不加载技能 |

返回：**最终文本字符串**（`Promise<string>`）。`onEvent` 可选，用于接收增量事件；没有会话历史。

### onEvent 事件

`onEvent(event)` 在运行过程中按发生顺序收到事件，终态事件（`run_end` / `run_error`）一定是最后一个，随后 Promise 才 resolve / reject。

| `event.type` | 额外字段 | 说明 |
|---|---|---|
| `run_start` | — | 运行开始 |
| `text_delta` | `text` | 可见文本增量 |
| `thinking_delta` | `text` | 思考增量（模型开启 thinking 时） |
| `tool_start` | `name` | 开始调用工具 |
| `tool_end` | `name`、`isError`、`durationMs` | 工具调用结束 |
| `turn_end` | — | 完成一轮 |
| `run_end` | `status`、`truncated` | 正常结束；`status` 为 `completed` 或 `truncated`（达到 `maxTurns` 被中止） |
| `run_error` | `code`、`message` | 运行失败；`code` 为 `timeout`（180 秒超时）或 `agent_failed` |

- 增量事件可能被合并成更大的片段，按 `text` 追加，不要假设粒度；`thinking_delta` 只用于可选的过程展示。
- `run_end` 之后 Promise resolve 为最终文本；`run_error` 之后 Promise reject（`agent_failed`）。
- 参数错误（`invalid_params`）不会产生事件，直接 reject。

```js
const output = document.querySelector("#output");
const status = document.querySelector("#status");
const text = await app.agent.run({
  prompt: "调查 2026 年 AI 手机市场，返回 5 条带来源的要点",
  tools: ["web_search", "web_fetch"],
  skills: [],
  onEvent(event) {
    if (event.type === "run_start") status.textContent = "开始运行…";
    if (event.type === "text_delta") output.textContent += event.text;
    if (event.type === "tool_start") status.textContent = `正在调用 ${event.name}…`;
    if (event.type === "run_end") status.textContent = event.truncated ? "已中止" : "完成";
  }
});
```


错误：

- Promise reject，`Error.code` 可能是：
  - `invalid_params`：参数非法、模型/thinking/工具/技能不存在、超出限制；
  - `agent_failed`：Agent 运行失败（模型调用错误、超时、没有返回文本等）；
  - `agent_unavailable`：当前环境没有启用页面 Agent（正常 App 不会出现）。
- 页面应用 `try/catch` 展示错误，不要吞掉。

限制与生命周期：

- 每次 `run` 都是独立的一次性 Agent；多个 `run` 之间没有上下文，结果由 JS 传递。
- 并行：`Promise.all([...])`；串行：`await` 每一步。
- 页面关闭/返回/WebView 销毁会取消正在运行的所有 Agent；没有后台队列。
- 每次运行超时 180 秒；超时前会收到 `run_error`（`code=timeout`），随后 Promise reject。
- 页面 Agent 按当前配置启用 MCP；capabilities() 列出可选工具。
- 页面 Agent 可以使用你显式请求的工具/技能；是否请求 App 工具、联网工具由页面作者决定。不要把不必要的用户隐私放进 prompt。

### 模型选择示例

```js
const select = document.querySelector("#model");
for (const model of await app.agent.models()) {
  for (const thinking of model.thinkingLevels) {
    const option = document.createElement("option");
    option.value = JSON.stringify({
      providerId: model.providerId,
      modelId: model.modelId,
      thinkingLevel: thinking
    });
    option.textContent = `${model.label} · ${thinking}`;
    select.appendChild(option);
  }
}
```

### 加载态与错误

```js
button.disabled = true;
status.textContent = "Agent 正在工作…";
try {
  const text = await app.agent.run({ prompt });
  result.textContent = text;
} catch (error) {
  status.textContent = `失败：${error.message}`;
} finally {
  button.disabled = false;
}
```

## Agent 侧数据工具（对话里的 Agent 使用）

页面不需要调用这些；它们供普通 PI 会话在修改/更新小程序时使用。页面通过 `app.data` 读取同一份数据。

| 工具 | 参数 | 行为 |
|---|---|---|
| `miniapp_edit` | `action=get`, `app`, `key` | 读取 key，不存在返回 null |
| `miniapp_edit` | `action=set`, `app`, `key`, `value` | 写入/覆盖任意 JSON，≤256KB |
| `miniapp_edit` | `action=delete`, `app`, `key` | 删除 key |
| `miniapp_edit` | `action=keys`, `app` | 列出 key（排序） |

示例：日报页面把内容存在 `report_2026-10-24`；Agent 每天用 `miniapp_edit`（`action=set`）更新对应 key，页面重开读取。

## 页面与 App 的边界

- 页面 bridge 不提供任意内部函数、直接文件访问或直接 shell 调用；app.agent.run 复用当前配置的文件、命令、插件和 MCP，不按目录隔离。
- 页面 Agent 是任务型运行：没有聊天会话、不写入聊天记录、不后台续跑。
- 页面可主动指定较少的工具/技能；省略时使用全部可用能力，一次性运行不提供目标、提醒或聊天交付卡。
- 用户关闭页面会取消运行；页面需要在离开前完成或保存结果。
- 不要自动在页面加载时启动 Agent；由用户按钮触发，避免无感消耗 tokens。

## 本地数据位置

- 页面代码：`files/miniapp/apps/<id>/`
- 页面数据：`files/miniapp/userdata/<id>.db`（SQLite KV，页面与数据工具共享）
- 页面文件区：`files/miniapp/userdata/<id>.files/`（`app.files.*` 读写，与代码目录分开）
- 页面 Agent 的临时会话状态由 App 管理并在结束后清理；默认工作区中的产物保留。并行命令仅本次运行有效，结束或取消时终止并等待收尾。
