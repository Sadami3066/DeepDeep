package io.github.whywood.deepdeep.runtime

object WorkshopSpec {
    fun defaultIcon(type: WorkshopType): String = when (type) {
        WorkshopType.BOARD -> "🗂️"
        WorkshopType.WORKSTATION -> "🧰"
        WorkshopType.WORKFLOW -> "⚙️"
    }

    fun agentsMd(manifest: WorkshopManifest): String = """
# ${manifest.name} — Workshop app

This folder is a Workshop app rendered by the DeepDeep WebView. Files are plain HTML, CSS, JavaScript and JSON. There is no build step, no package manager and no network CDN: everything must be self-contained and load from relative paths inside this folder.

## Folder layout

- `manifest.json` — app metadata, required. Keep it valid after every edit.
- `${manifest.entry}` — the entry HTML page declared by `manifest.entry`.
- other `.html`, `.css`, `.js`, `.json`, images, fonts — assets referenced with relative paths.
- No `node_modules`, no compiled bundles, no external URLs for code or styles.

## manifest.json

- `id`: lowercase letters, digits, `-` or `_`, 1-48 chars, starts with a letter or digit.
- `name`: 1-40 chars, shown in the app list and the viewer title.
- `type`: `board`, `workstation` or `workflow`.
- `icon`: optional emoji, at most 8 chars. Defaults per type.
- `version`: optional string, at most 20 chars, default `1.0.0`.
- `entry`: optional entry HTML path, default `index.html`; must be relative, inside this folder, end with `.html`, no `..`.
- `description`: optional, at most 300 chars, shown in the app list.
- `permissions`: optional array, see Permissions below.

## Code and data are separate

The app folder is code, not storage. Never write user data into a file inside the folder.
Read and write all user data through the bridge:

- `data.set(key, value)` / `data.get(key)` / `data.remove(key)` / `data.keys()`.
- Values are JSON: strings, numbers, booleans, arrays, objects, or null.
- Data lives in a per-app SQLite database outside the folder, so updating the code never loses data.
- Deleting the app deletes its data. Keep keys short and stable.

## Bridge SDK

`window.deepdeep` and `window.app` are the same object. All calls return Promises.

```
info(): Promise<{app: {id, name, type, icon, version}, capabilities: string[]}>
env: {app, permissions, capabilities, theme}
data.get(key): Promise<any | null>
data.set(key, value): Promise<void>
data.remove(key): Promise<boolean>
data.keys(): Promise<string[]>
agent.run(promptOrOptions, context?): Promise<string>
agent.cancel(): Promise<boolean>
toast(message): void
log.error(error): void
```

`agent.run` accepts either a prompt string or `{prompt, context?, onEvent?}`. It starts a new Agent
run in this app folder; `context` is optional JSON data passed to the Agent with the prompt. The
Promise resolves with the final text
string. `onEvent` receives `{type: "started"}`, `{type: "text", text}` with an incremental text
delta, `{type: "tool", name}`, or `{type: "finished"}`. Failures reject the Promise with an Error
carrying a `.code` such as `busy`, `timeout`, `cancel`, `model_missing` or `agent_failed`; there is
no error event. Only one run at a time; `agent.cancel()` resolves a boolean.

`capabilities` lists the permission names currently enabled for this app, such as `data.kv` and
`agent.run`. `toast(message)` shows a short transient message. `log.error(error)` reports a handled error to the
host so the user can see it. Uncaught errors and rejected promises are captured automatically.

## Permissions

Only permissions declared in `manifest.json` work at runtime:

- `data.kv` — required for `data.get/set/remove/keys`.
- `agent.run` — required for `agent.run`.

Undeclared calls fail. Do not declare permissions the app does not use.

## Themes

The host injects these CSS custom properties and a `color-scheme` on the document element; use them
instead of hard-coded colors:

- `--dd-bg` page background
- `--dd-ink` primary text
- `--dd-ink-secondary` secondary text
- `--dd-accent` accent color
- `--dd-hairline` dividers and borders

Wrap values in `var(--dd-bg, #fallback)` so the page still looks right outside the host.

## Errors and unavailable APIs

- `window.onerror` and unhandled promise rejections are auto-reported to the host.
- `alert(message)` maps to a toast; `confirm` and `prompt` are unavailable, build the UI yourself.
- Service workers are unavailable. You may fetch static files inside this folder with relative paths.
- `localStorage` is not guaranteed; use the data bridge.
- Remote scripts, fonts, styles and images are not allowed and may be unavailable offline.

## Mobile page requirements

- Include `<meta name="viewport" content="width=device-width, initial-scale=1">`.
- Mobile first: large tap targets, no horizontal scrolling, respect safe-area insets.
- No build step: one entry HTML plus optional relative CSS/JS assets.
- Support light and dark via the theme variables, and `prefers-color-scheme` as fallback.
- Offline: no remote fonts, images, scripts or styles.

## Working rules

1. Read this file before editing.
2. Write files with absolute paths under this folder using read/write/edit; do not use bash.
3. Never write data into the code folder, and never edit `manifest.json` unless the app identity changes.
4. Keep the app runnable at every step; do not leave broken references.
""".trimIndent()

    fun starterHtml(manifest: WorkshopManifest): String = when (manifest.type) {
        WorkshopType.BOARD -> boardStarter(manifest)
        WorkshopType.WORKSTATION -> workstationStarter(manifest)
        WorkshopType.WORKFLOW -> workflowStarter(manifest)
    }

    private fun boardStarter(manifest: WorkshopManifest): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${manifest.name}</title>
<style>
:root { color-scheme: light dark; }
body { margin: 0; padding: 20px; background: var(--dd-bg, #ffffff); color: var(--dd-ink, #111111);
  font-family: -apple-system, system-ui, "Noto Sans SC", sans-serif; }
h1 { margin: 0 0 4px; font-size: 22px; }
p.hint { margin: 0 0 18px; color: var(--dd-ink-secondary, #666666); font-size: 14px; }
.row { display: flex; gap: 8px; }
input[type=text] { flex: 1; min-width: 0; padding: 12px; font-size: 16px; color: inherit;
  background: transparent; border: 1px solid var(--dd-hairline, #dddddd); border-radius: 10px; }
button { padding: 12px 16px; font-size: 16px; color: var(--dd-bg, #ffffff); background: var(--dd-accent, #111111);
  border: 0; border-radius: 10px; }
ul { list-style: none; margin: 18px 0 0; padding: 0; }
li { display: flex; align-items: center; gap: 12px; padding: 14px 0; border-bottom: 1px solid var(--dd-hairline, #eeeeee); }
li span { flex: 1; font-size: 16px; word-break: break-word; }
li.done span { color: var(--dd-ink-secondary, #666666); text-decoration: line-through; }
li button { padding: 6px 10px; font-size: 14px; background: transparent; color: var(--dd-ink-secondary, #666666); }
.empty { margin-top: 24px; color: var(--dd-ink-secondary, #666666); font-size: 14px; }
</style>
</head>
<body>
<h1 id="title">${manifest.name}</h1>
<p class="hint">Demo: a todo list saved through the data bridge.</p>
<div class="row">
  <input id="input" type="text" placeholder="Add a task" autocomplete="off">
  <button id="add">Add</button>
</div>
<ul id="list"></ul>
<p class="empty" id="empty">Nothing yet.</p>
<script>
var bridge = window.deepdeep || window.app;
var KEY = "todos";
var todos = [];
var list = document.getElementById("list");
var input = document.getElementById("input");
var empty = document.getElementById("empty");

function render() {
  list.textContent = "";
  empty.style.display = todos.length ? "none" : "block";
  todos.forEach(function (todo, index) {
    var item = document.createElement("li");
    if (todo.done) item.className = "done";
    var box = document.createElement("input");
    box.type = "checkbox";
    box.checked = Boolean(todo.done);
    box.addEventListener("change", function () { toggle(index); });
    var text = document.createElement("span");
    text.textContent = todo.text;
    var remove = document.createElement("button");
    remove.textContent = "Delete";
    remove.addEventListener("click", function () { deleteTodo(index); });
    item.appendChild(box);
    item.appendChild(text);
    item.appendChild(remove);
    list.appendChild(item);
  });
}

function save() {
  bridge.data.set(KEY, todos).catch(function (error) { bridge.log.error(error); });
}

function addTodo() {
  var text = input.value.trim();
  if (!text) return;
  todos.push({ text: text, done: false });
  input.value = "";
  render();
  save();
}

function toggle(index) {
  todos[index].done = !todos[index].done;
  render();
  save();
}

function deleteTodo(index) {
  todos.splice(index, 1);
  render();
  save();
}

document.getElementById("add").addEventListener("click", addTodo);
input.addEventListener("keydown", function (event) { if (event.key === "Enter") addTodo(); });

bridge.info().then(function (info) { document.getElementById("title").textContent = info.app.name; });
bridge.data.get(KEY).then(function (stored) {
  todos = Array.isArray(stored) ? stored.filter(function (todo) { return todo && typeof todo.text === "string"; }) : [];
  render();
}).catch(function (error) { bridge.log.error(error); });
</script>
</body>
</html>
""".trimIndent()

    private fun workstationStarter(manifest: WorkshopManifest): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${manifest.name}</title>
<style>
:root { color-scheme: light dark; }
body { margin: 0; padding: 20px; background: var(--dd-bg, #ffffff); color: var(--dd-ink, #111111);
  font-family: -apple-system, system-ui, "Noto Sans SC", sans-serif; }
h1 { margin: 0 0 4px; font-size: 22px; }
p.hint { margin: 0 0 18px; color: var(--dd-ink-secondary, #666666); font-size: 14px; }
textarea { width: 100%; box-sizing: border-box; min-height: 120px; padding: 12px; font-size: 16px;
  font-family: inherit; color: inherit; background: transparent;
  border: 1px solid var(--dd-hairline, #dddddd); border-radius: 10px; resize: vertical; }
.row { display: flex; gap: 8px; margin: 12px 0 18px; }
button { padding: 12px 16px; font-size: 16px; border: 0; border-radius: 10px;
  color: var(--dd-bg, #ffffff); background: var(--dd-accent, #111111); }
button.secondary { background: transparent; color: var(--dd-ink, #111111);
  border: 1px solid var(--dd-hairline, #dddddd); }
button:disabled { opacity: 0.5; }
pre { margin: 0; padding: 14px; min-height: 120px; white-space: pre-wrap; word-break: break-word;
  font-size: 14px; line-height: 1.5; border: 1px solid var(--dd-hairline, #dddddd); border-radius: 10px; }
</style>
</head>
<body>
<h1 id="title">${manifest.name}</h1>
<p class="hint">Describe what you want, then let an Agent generate a result here.</p>
<textarea id="prompt" placeholder="What should the Agent do?"></textarea>
<div class="row">
  <button id="run">让 Agent 生成</button>
  <button id="stop" class="secondary" disabled>Stop</button>
</div>
<pre id="output"></pre>
<script>
var bridge = window.deepdeep || window.app;
var KEY = "last_prompt";
var prompt = document.getElementById("prompt");
var output = document.getElementById("output");
var runButton = document.getElementById("run");
var stopButton = document.getElementById("stop");
var running = false;

function setRunning(value) {
  running = value;
  runButton.disabled = value;
  stopButton.disabled = !value;
}

function onEvent(event) {
  if (event.type === "text") output.textContent += event.text;
}

async function generate() {
  var text = prompt.value.trim();
  if (!text || running) return;
  output.textContent = "";
  setRunning(true);
  try {
    await bridge.data.set(KEY, text);
    await bridge.agent.run({ prompt: text, onEvent: onEvent });
  } catch (error) {
    output.textContent += "\n[error] " + (error && error.message ? error.message : error);
  } finally {
    setRunning(false);
  }
}

runButton.addEventListener("click", generate);
stopButton.addEventListener("click", function () { bridge.agent.cancel(); });
bridge.info().then(function (info) { document.getElementById("title").textContent = info.app.name; });
bridge.data.get(KEY).then(function (stored) {
  if (typeof stored === "string") prompt.value = stored;
}).catch(function (error) { bridge.log.error(error); });
</script>
</body>
</html>
""".trimIndent()

    private fun workflowStarter(manifest: WorkshopManifest): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${manifest.name}</title>
<style>
:root { color-scheme: light dark; }
body { margin: 0; padding: 20px; background: var(--dd-bg, #ffffff); color: var(--dd-ink, #111111);
  font-family: -apple-system, system-ui, "Noto Sans SC", sans-serif; }
h1 { margin: 0 0 4px; font-size: 22px; }
p.hint { margin: 0 0 18px; color: var(--dd-ink-secondary, #666666); font-size: 14px; }
.row { display: flex; gap: 8px; margin-bottom: 14px; }
button { padding: 10px 16px; font-size: 15px; border: 0; border-radius: 10px;
  color: var(--dd-bg, #ffffff); background: var(--dd-accent, #111111); }
button.secondary { background: transparent; color: var(--dd-ink, #111111);
  border: 1px solid var(--dd-hairline, #dddddd); }
button:disabled { opacity: 0.5; }
ol { list-style: none; margin: 0; padding: 0; }
li { padding: 12px 0; border-bottom: 1px solid var(--dd-hairline, #eeeeee);
  font-size: 14px; line-height: 1.55; white-space: pre-wrap; word-break: break-word; }
li .step { display: block; margin-bottom: 4px; font-size: 12px; color: var(--dd-ink-secondary, #666666); }
</style>
</head>
<body>
<h1 id="title">${manifest.name}</h1>
<p class="hint">Runs an Agent automatically when the page loads, then renders the log.</p>
<div class="row">
  <button id="run">Run again</button>
  <button id="stop" class="secondary" disabled>Stop</button>
</div>
<ol id="log"></ol>
<script>
var bridge = window.deepdeep || window.app;
var log = document.getElementById("log");
var runButton = document.getElementById("run");
var stopButton = document.getElementById("stop");
var running = false;
var current = null;

function appendStep(label, text) {
  current = document.createElement("li");
  var step = document.createElement("span");
  step.className = "step";
  step.textContent = label;
  current.appendChild(step);
  current.appendChild(document.createTextNode(text));
  log.appendChild(current);
  window.scrollTo(0, document.body.scrollHeight);
}

function setRunning(value) {
  running = value;
  runButton.disabled = value;
  stopButton.disabled = !value;
}

var PROMPT = "Review the workshop app in this folder and write one short status line about what it does.";

async function run() {
  if (running) return;
  setRunning(true);
  log.textContent = "";
  var started = Date.now();
  appendStep("start", "");
  var onEvent = function (event) {
    if (event.type === "started") appendStep("started", "");
    if (event.type === "text" && current) {
      current.lastChild.textContent += event.text;
      window.scrollTo(0, document.body.scrollHeight);
    }
    if (event.type === "tool") appendStep("tool: " + (event.name || "unknown"), "");
  };
  try {
    var finalText = await bridge.agent.run({ prompt: PROMPT, onEvent: onEvent });
    appendStep("done in " + Math.round((Date.now() - started) / 100) / 10 + "s", finalText || "");
  } catch (error) {
    appendStep("failed", error && error.message ? error.message : String(error));
  } finally {
    setRunning(false);
  }
}

runButton.addEventListener("click", run);
stopButton.addEventListener("click", function () { bridge.agent.cancel(); });
bridge.info().then(function (info) { document.getElementById("title").textContent = info.app.name; });
run();
</script>
</body>
</html>
""".trimIndent()
}
