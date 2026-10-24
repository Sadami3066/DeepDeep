---
name: workshop
description: 在 DeepDeep「创意工坊」里创建或修改 HTML/CSS/JS 轻量小应用（待办、课程表、纪念日、抽签、小游戏、动画、计算器、工具页等任何可交互页面）。当用户想要一个手机上的创意/小应用/小页面/小工具，或要修改已有创意时使用。
---

# 创意工坊

创意工坊把 Agent 写的一个网页变成手机里的轻应用：用户在抽屉「创意工坊」打开它，像小程序一样使用。页面由你直接编写，没有预设分类或模板，形态完全由需求决定；不要套用「看板 / 工作台 / 工作流」这类分类。

## 什么时候使用

- 用户想要一个手机上的互动小页面、小应用或小工具：待办、课程表、日程、纪念日、抽签、小游戏、动画、计算器、记账、习惯打卡、工具页等。
- 用户要修改、迭代已有创意。
- 普通报告、幻灯片、文档、图片不要用创意工坊，用 `present`。

## 创建流程

1. 理解需求；不确定的交互先一句话问清，不要自己脑补大功能。
2. 调用 `workshop_create`：`name`（1–40 字）、可选 `description`（列表里的一句说明）、可选 `icon`（一个 emoji）。返回草稿的绝对路径 `<draft>`。
3. 用 `read` / `write` / `edit` 的**绝对路径**在 `<draft>` 下写页面，入口是 `index.html`，需要的话再加 CSS、JS、图片等文件。创意目录在 bash 工作区之外，**不要用 bash 写这个目录**。
4. 写完调用 `workshop_load`，参数 `path=<draft>`。工具会校验 manifest 与入口文件；失败就按报错修好再调，直到成功。成功后聊天里会出现入口卡片。
5. 告诉用户去「创意工坊」打开；用一两句话说明怎么用，不要贴大段代码。

## 修改流程

- 先 `workshop_list` 找到该创意的 id 和绝对路径。
- **只改界面/交互/逻辑**：直接编辑 `apps/<id>/` 下的文件，不需要再次 `workshop_load`，用户重新打开就会看到新版本。
- **只改内容/数据**（例如日报每天更新、表单字段、配置、记录）：不要重写页面文件，用下面的数据工具改它的 JSON 数据即可；页面重新打开时会读到新数据。
- 页面数据存在 App 侧的 SQLite（通过页面 bridge 读写），重写页面文件不会丢数据；不要直接改 `userdata` 下的数据库文件。

## 更新数据（Agent 工具）

每个创意都有独立的 JSON 键值数据层，页面打开时通过 `app.data` 读取。你可以直接读写它，不用改 HTML：

| 工具 | 参数 | 说明 |
|---|---|---|
| `workshop_data_get` | `app`, `key` | 读取一个 key；不存在时返回 null |
| `workshop_data_set` | `app`, `key`, `value`（任意 JSON） | 写入/覆盖；单值 ≤ 256KB |
| `workshop_data_delete` | `app`, `key` | 删除 key |
| `workshop_data_keys` | `app` | 列出已有 key |

- `app` 传 `workshop_list` 返回的 id。
- 数据是任意 JSON，表单结构/字段/默认值都由你定义：建议用清晰的小写 key，例如 `schema`、`settings`、`report:2026-10-24`；页面代码按约定读取并渲染。
- 更新内容前先 `workshop_data_get` 或 `workshop_data_keys` 看现有结构，避免覆盖你不想动的字段；需要整体替换时再 set。
- 写入后不需要 `workshop_load`。用户页面正开着时数据不会自动刷新，让他重新打开或点详情页的重新加载。
- 单值超过 256KB 时拆成多个 key；不要把数据写进代码目录，也不要存敏感凭据。

日报/播客这类每天更新的创意：页面只写一次，首次把 schema 和示例数据写入；以后收到「更新日报」之类指令时，用 `workshop_data_set` 更新对应日期的 key，不要重写 WebView。

## 文件夹格式

`workshop_create` 会生成 `manifest.json`：

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

- `id` 由工具生成，不要修改；`name`、`icon`、`description` 可以改。
- `entry` 一般保持 `index.html`；页面入口必须真实存在。
- 目录里只放静态资源（html/css/js/svg/png/webp/字体等）；不要放构建产物、node_modules、密钥或用户数据。

## 页面能力

App 会在页面脚本运行前注入 `window.deepdeep`（别名 `window.app`），全部返回 Promise：

```js
const todos = await app.data.get("todos");
await app.data.set("todos", [...todos, { text: "写作业", done: false }]);
app.toast("已保存");
```

| 接口 | 说明 |
|---|---|
| `app.data.get(key)` / `.set(key, value)` / `.remove(key)` / `.keys()` | 每个创意独立的 JSON 键值数据，单值 ≤ 256KB；页面关闭/重装不丢 |
| `app.toast(text)` | 原生轻提示 |
| `app.info()` | `{ app: { id, name, icon, version } }` |

- 主题变量：`--dd-bg`、`--dd-ink`、`--dd-ink-secondary`、`--dd-accent`、`--dd-hairline`，同时设置 `color-scheme`；用它适配 App 深浅色。
- `alert` 会显示为原生 toast；`confirm` / `prompt` / Service Worker 不可用，用页面内 UI 代替。
- 页面里不能调用 Agent、不能读写其他创意的数据；用户要改页面，退出创意后在聊天里告诉你。

## 页面要求

- 移动端优先：`<meta name="viewport" content="width=device-width, initial-scale=1">`，用 responsive 布局（flex/grid），不要固定桌面宽度或超大画布。
- 单文件可运行优先：无构建步骤、相对路径引用资源、不依赖登录或需要密钥的远端服务；外部 `fetch` 必须有失败兜底。
- 交互真实可用：按钮有反馈、空态和错误提示清楚、输入框可用；需要持久化的数据走 `app.data`，不要用 localStorage 当唯一真相。
- 深浅色都可读：颜色优先用主题变量，必要时用 `@media (prefers-color-scheme: dark)` 兜底。
- 控制体积：不引入大库/大字体，图标用 emoji 或内联 SVG；图片尽量压缩。
- 每次 `workshop_load` 后都要真的打开检查布局与核心交互，再告诉用户完成。

## 注意

- 一次只做一个创意；需求很大时先做可用版本，再按用户反馈迭代。
- 不要自行创建/删除/排序创意列表，也不要在工坊页之外自行安装目录。
- 不要在页面或代码里写 API Key、Cookie 等敏感信息。
