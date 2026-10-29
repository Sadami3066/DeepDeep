# 教程：从零搭一个多步骤 Agent 工作台

本教程以「公众号工作台」为例：页面 1 填主题/受众，页面 2 选模型，页面 3 看运行结果，页面 4 对结果再处理。示例用 section 切换，不依赖后端；实际形态按需求决定。

## 0. 设计页面结构

四个 section：

```html
<div class="app">
  <header>公众号工作台</header>
  <main>
    <section id="step1">…表单…</section>
    <section id="step2">…模型选择…</section>
    <section id="step3">…loading/结果…</section>
    <section id="step4">…处理…</section>
  </main>
</div>
```

用 JS 的 `showStep(n)` 控制 `hidden`。

## 1. 先写视觉和表单

- 移动端 viewport；最大宽度 680px；`min-height: 100dvh` 或 `height: 100%`。
- 只用主题变量：`background: var(--dd-bg)`、`color: var(--dd-ink)`。
- 输入框、按钮用系统字体；按钮点击要有 active 反馈。
- 空态和错误文案提前留好位置。

```html
<section id="step1" class="step">
  <label>主题<input id="topic" placeholder="例如：AI 手机会取代电脑吗"></label>
  <label>受众<input id="audience" placeholder="例如：大学生"></label>
  <button id="next1">下一步</button>
</section>
```

## 2. 页面状态与持久化

用一个 `state` 对象保存当前流程：

```js
const state = {
  topic: "",
  audience: "",
  providerId: "",
  modelId: "",
  thinkingLevel: "off",
  draft: "",
  article: ""
};

async function saveState() {
  await app.data.set("workbench_state", state);
}

async function loadState() {
  const saved = await app.data.get("workbench_state");
  if (saved && typeof saved === "object") Object.assign(state, saved);
}
```

- `app.data` 是 JSON，没有 localStorage 的同步/容量限制问题。
- 用 `workbench_` 前缀避免和其他 key 冲突。
- 页面重开时调用 `loadState()`，恢复用户上次进度。

## 3. 模型选择

页面 2 加载 `app.agent.models()`，给每个模型的每个 thinking level 生成一个 option：

```js
async function fillModels() {
  const select = document.querySelector("#model");
  select.innerHTML = "";
  const models = await app.agent.models();
  if (!models.length) {
    select.innerHTML = "<option>没有已配置的模型</option>";
    return;
  }
  for (const model of models) {
    for (const level of model.thinkingLevels) {
      const option = document.createElement("option");
      option.value = JSON.stringify({
        providerId: model.providerId,
        modelId: model.modelId,
        thinkingLevel: level
      });
      option.textContent = `${model.label} · ${level}`;
      select.appendChild(option);
    }
  }
}
```

选择结果存到 `state`；如果用户不选，就传 `undefined` 让 App 用默认模型。

## 4. 第一次 Agent：调查/写初稿

页面 3 的“开始运行”：

```js
async function runDraft() {
  const choice = document.querySelector("#model").value;
  const selected = choice ? JSON.parse(choice) : {};

  document.querySelector("#status").textContent = "Agent 正在调查和写稿…";
  document.querySelector("#run").disabled = true;
  try {
    const text = await app.agent.run({
      prompt: [
        `主题：${state.topic}`,
        `受众：${state.audience}`,
        "先调查关键信息，再写一篇 800 字左右的公众号初稿。",
        "输出：标题、正文、3 条可核实的来源。"
      ].join("\n"),
      providerId: selected.providerId,
      modelId: selected.modelId,
      thinkingLevel: selected.thinkingLevel,
      systemPrompt: "你是资深公众号编辑。事实要有来源；不要编造数据。",
      maxTurns: 12,
      tools: ["web_search", "web_fetch"],
      skills: []
    });
    state.draft = text;
    await saveState();
    renderDraft(text);
    showStep(3);
  } catch (error) {
    document.querySelector("#status").textContent = `失败：${error.message}`;
  } finally {
    document.querySelector("#run").disabled = false;
  }
}
```

要点：

- 调查类需要显式请求 `web_search` / `web_fetch`；名称来自 `capabilities()`。
- 不要用 `tools: "all"` 之类字符串；数组。
- 需要过程反馈就传 `onEvent`，按事件类型更新状态；不传则只等最终文本。
- 失败时保留用户输入，不要清空页面。

### 用 onEvent 显示进度

```js
const text = await app.agent.run({
  prompt: `主题：${state.topic}\n受众：${state.audience}\n先调查再写 800 字初稿。`,
  tools: ["web_search", "web_fetch"],
  skills: [],
  maxTurns: 12,
  onEvent(event) {
    if (event.type === "tool_start") {
      document.querySelector("#status").textContent = `正在使用 ${event.name}…`;
    }
    if (event.type === "text_delta") {
      document.querySelector("#preview").textContent += event.text;
    }
    if (event.type === "run_end") {
      document.querySelector("#status").textContent = event.truncated ? "已中止" : "正在整理…";
    }
  }
});
```

- 增量可能被合并成更大的片段，只需追加 `event.text`。
- 终态事件 `run_end` / `run_error` 一定是最后一个；最终文本仍以 `await` 的结果为准。

## 5. 页面 4：对结果再处理

两种常见做法：

**A. 再跑一个 Agent 改写**

```js
const polished = await app.agent.run({
  prompt: `请把下面稿件改成更口语化的版本，保留来源：\n\n${state.draft}`,
  systemPrompt: "只输出改写后的正文。",
  maxTurns: 4,
  tools: [],
  skills: []
});
```

**B. 本地 JS 处理**（复制、统计字数、导出、保存到数据）

```js
await app.data.set("workbench_article", { text: state.draft, updatedAt: Date.now() });
app.toast("已保存");
```

## 6. 多 Agent 编排

### 并行调查

```js
const [market, competitors] = await Promise.all([
  app.agent.run({
    prompt: "调查 2026 年 AI 手机市场，列出 5 条带来源的要点",
    tools: ["web_search", "web_fetch"], skills: [], maxTurns: 10
  }),
  app.agent.run({
    prompt: "调查主流 AI 手机厂商的最新产品，列出 5 条带来源的要点",
    tools: ["web_search", "web_fetch"], skills: [], maxTurns: 10
  })
]);
```

### 汇总给总写 Agent

```js
const article = await app.agent.run({
  prompt: `根据以下两份调查写一篇公众号文章：\n\n【市场】\n${market}\n\n【厂商】\n${competitors}`,
  systemPrompt: "你是资深公众号编辑。保留来源；不要编造。",
  tools: [],
  skills: [],
  maxTurns: 8
});
```

并行时每个 Agent 都是独立的；总写 Agent 只能通过你拼进去的 prompt 得到上下文。

### 串行流水线

```js
const outline = await app.agent.run({ prompt: "先列提纲…", tools: [], skills: [] });
const draft = await app.agent.run({ prompt: `按提纲写稿：\n${outline}`, tools: [], skills: [] });
const title = await app.agent.run({ prompt: `给这篇稿起 5 个标题：\n${draft}`, tools: [], skills: [] });
```

## 7. 体验细节

- 运行中禁用开始按钮，避免重复点击；显示明确的“正在调查/正在写作”状态。
- 页面关闭会取消正在运行的 Agent；长任务在离开前要提示用户“运行完再离开”。
- 页面重新打开时读取 `app.data` 恢复上次结果，而不是重新自动跑 Agent。
- 模型列表为空时给出“去 App 设置里配置模型”的提示，而不是让按钮报错。
- 所有错误展示在页面内，不要只 `console.error`。
- 按钮只在用户点击后启动 Agent，不要在页面加载时自动启动。

## 8. 保存与测试

1. 把 `index.html` 和资源写到创意目录。
2. `workshop_load` 装载。
3. 在工坊打开：检查表单、模型列表、运行、loading、错误、结果、页面重开恢复。
4. 需要改内容时优先用 `workshop_edit`，不要重写整个页面。

## 9. 常见错误

| 现象 | 原因 | 处理 |
|---|---|---|
| `agent.models()` 为空 | 没有配置 provider/API Key | 提示用户先配置模型 |
| `invalid_params: unknown tool` | tools 名字不在 capabilities 里 | 用 `capabilities()` 返回的名称 |
| 进度不更新 | 没传 `onEvent` 或没处理对应事件 | 在 `onEvent` 里按 `type` 更新界面 |
| 关页面后结果没了 | 生命周期绑定 WebView | 运行完再离开，或写 `app.data` |
| Agent 没网感 | 没请求 web_search/web_fetch | tools 显式加入；系统提示要求来源 |
| 重复点击开多个 Agent | 按钮没禁用 | 运行时 disable，finally 恢复 |
| 页面改了数据没变 | 页面读取的是旧数据/缓存 | 重新读取 `app.data` 或重开页面 |

## 10. 适配样例

- `examples/02-agent-workbench.html`：本教程的完整可运行版本。
- `examples/03-agent-pipeline.html`：并行调查 + 总写。
- `examples/04-daily-report.html`：数据驱动日报和刷新按钮。
- `examples/01-hello.html`：最小数据/主题示例。

先通读样例，再按需求换文案、字段和 prompt；不要直接引用技能目录路径。
