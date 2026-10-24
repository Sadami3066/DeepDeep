# 创意工坊

状态：已实现。一个创意就是 Agent 写的一个自包含网页文件夹（HTML/CSS/JS/JSON），App 用 WebView 把它渲染成手机里的轻应用；没有预设分类，做什么完全由对话里的需求和 Agent 决定（爱心动画、待办、课程表、小游戏、计算器……都可以）。

## 入口

- 抽屉「创意工坊」进入列表；右上角刷新图标重新扫描，`+` 直接新建一个 Home 会话，并在输入框预填 `/workshop-skill `。
- 列表按最近修改排序，行 = 创意 icon（manifest 里的 emoji，缺省为 ✦）+ 名称 + 说明；格式不符的目录显示「格式无效 · 原因」，可删除。
- 点行打开详情：全屏页面 + 顶部返回 / 名称 / 更多（重新加载、重命名、删除）。详情页只读，不在页面内驱动 Agent。

## 创建与修改

创建由普通的 DeepDeep 会话完成，不需要额外入口或分类：

1. 在会话输入框里用 `/workshop-skill ` 开头（工坊页 `+` 会自动打开这样的新会话并预填），也可以手动输入 `/skill:workshop `。
2. `/workshop-skill` 会把内置的 workshop 技能完整读入当前会话（技能文件同时出现在「技能」页，可查看），DeepDeep 随即知道创意工坊的格式、工具和页面规范。
3. 后面直接描述你想做的东西，例如「做一个点一下会跳动的爱心」「做一个本周课程表，可以自己加课」；Agent 会调用 `workshop_create` 建草稿、用文件工具写页面、调用 `workshop_load` 装载进 App，然后在聊天里给出入口卡片。
4. 点卡片或到「创意工坊」列表打开。

修改已有创意：从工坊退出回到聊天，直接说「把那个爱心改成点两下才跳」或「课程表加一列备注」。Agent 会用 `workshop_list` 找到该创意的目录并修改文件；用户重新打开就能看到新版本，不需要重新「安装」。

## 斜杠命令

| 命令 | 作用 |
|---|---|
| `/workshop-skill [需求]` | 强制加载内置的 workshop 技能；后面的文字作为本次需求一起发出去 |
| `/skill:<name> [需求]` | 强制加载任意已启用技能（全局或当前工作区），技能内容会以 `<skill>` 块进入当前会话；未知技能会提示 |

- 这种「强制加载」把技能文件的完整内容作为本轮上下文的一部分发给模型，而不是只依赖系统提示里的技能目录。
- 新会话默认已经能看到已启用技能的目录；`/skill:` 用于确保某个技能一定被读入，尤其适合技能很多或刚改过技能文件时。
- workshop 技能随 App 内置，App 启动时确保它在技能仓库与全局生效目录里；更新 App 会刷新内容。

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

- 主题变量：`--dd-bg`、`--dd-ink`、`--dd-ink-secondary`、`--dd-accent`、`--dd-hairline`，同时设置 `color-scheme`；页面用它跟随 App 深浅色。
- `alert` 会显示成原生轻提示；`confirm` / `prompt` / Service Worker 不可用，请用页面内 UI。
- 页面不能调用 Agent、不能读写其他创意或 App 数据；需要改页面时退出创意，在聊天里说。

## 数据与安全

- 代码与数据物理分开：`apps/<id>/` 是代码，`userdata/<id>.db` 是数据；Agent 重写整个页面不影响数据。
- 删除创意会删除页面目录和对应数据，无法恢复。
- 每个创意运行在独立虚拟域名下，只能访问自己的页面文件；页面可以请求外部网络，但不会自动获得 App 的其他能力。
- 创意内容由本机 DeepDeep/用户生成；不要在页面或代码里写 API Key、Cookie 等敏感信息。

## 限制

- 不支持在创意页面里调用 Agent，也不支持页面内编辑；修改统一回到聊天。
- 创意列表不提供导入/导出或市场；把合法目录直接放进 `apps/` 会被扫描到。
- 数据目前是键值存储，不支持 SQL 查询；后台/定时运行、媒体选择等能力后续按需扩展。
- 内置 workshop 技能随 App 更新，用户对它的修改可能在下次启动时被刷新。
