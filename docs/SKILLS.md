# Skills 设计

状态：设计稿，本轮不实现。SDK 已有扫描与 `<skills>` 注入；app 只做文件与 UI 管理。

## SDK 已实现的事实

pi2kotlin `loadSkills(cwd, agentDir, fs, projectTrusted)` 的行为：

- 扫描两个根：
  - 工作区 `<cwd>/.pi/skills`（DeepDeep 中 `projectTrusted = true`）
  - 全局 `files/agent/skills`
- 同名以工作区优先；按 `name` 去重。
- 结构：
  - 目录型 `<skill>/SKILL.md`，可带 `scripts/`、`references/`、`assets/`。
  - 扫描根第一层的 `<skill>.md` 扁平文件。
  - 子目录递归找 `SKILL.md`；跳过隐藏目录与 `node_modules`。
- frontmatter：
  - `description` 必填，缺失则整个技能不加载。
  - `name` 缺省用父目录名。
  - `disable-model-invocation: true` 会加载但不进入模型可见列表。
  - 其余字段（`license`、`allowed-tools`、`whenToUse` 等）当前不消费。
- 注入：
  - 仅当存在 `read` 或 `bash` 工具时，`SystemPromptInput.skills` 生成 `skills` section，最终为：
    ```xml
    <skills>
    ...
    <available_skills>
      <skill><name>…</name><description>…</description><location>…</location></skill>
    </available_skills>
    </skills>
    ```
  - 只注入 name/description/location，正文由模型按需 `read`。
  - 相对路径以技能目录为基准。
- 生命周期：
  - 技能列表在 `createAgentSession` 时加载一次。
  - 已打开的会话不会感知新增/删除；新建会话、重启进程或 `host.reconfigure()` 后重新加载。
  - 每次 prompt 的 section diff 只更新当前会话已有 section，不重新扫盘。

## App 方案

### 范围

- 全局 + 当前工作区。
- 列表、详情、创建、编辑、删除、导入 `.md`、启用/停用。
- 目录导入、技能市场、`/skill:name` 命令列为后续。

### 路径与启停

| 作用域 | 启用目录 | 停用目录 |
|---|---|---|
| 全局 | `files/agent/skills` | `files/agent/skills-disabled` |
| 工作区 | `<cwd>/.pi/skills` | `<cwd>/.pi/skills-disabled` |

- SDK 只扫启用目录；停用 = 移动到 `skills-disabled`，保留原文件。
- 同名冲突以工作区优先；UI 标出“工作区覆盖全局”。
- 删除 = 移到回收目录或直接删除（二次确认）；回收站列为后续。

### 数据模型

```text
SkillEntry
  id: String            // 稳定标识，可用作用域 + 名称哈希
  name: String
  description: String
  scope: GLOBAL | WORKSPACE
  path: String          // SKILL.md 或 .md 的绝对路径
  enabled: Boolean
  disableModelInvocation: Boolean
```

- 列表展示 `name + description + scope + enabled`。
- 详情展示正文与附属文件；编辑只改 `SKILL.md` 的 frontmatter `name/description` 和正文。
- 新建时强制 description；name 缺省用目录名。
- 导入 `.md`：保存到全局或当前工作区根层，作为扁平技能。
- 无效 frontmatter / 缺 description：显示为“不可用”，不进入注入列表。

### 生效

- 保存后 UI 提示“新会话生效”。
- 「重新加载」按钮调用 `host.reconfigure()`：先确认（会重建所有已打开的 SDK 会话），运行中的回答可能被打断。
- 不自动重建；不偷偷改用户正在进行的对话。
- 修改技能文件后，已有会话继续使用旧快照；新会话重新读取。

### UI

- 抽屉「技能」→ 技能页；顶部切换「全局 / 当前工作区」。
- 列表分组：可用技能、已停用、不可用。
- 行尾开关（移动目录）；点击进入详情。
- `+` 新建；`导入` 选择 `.md`。
- 详情：名称、描述、作用域、路径、正文编辑、保存/删除。
- 顶部提示：技能可以要求模型执行命令或读取文件，导入前确认来源。
- 空态说明扫描路径与“新建会话后生效”。

### 分层

- app：文件管理、frontmatter 解析/写入、UI。
- runtime：可选暴露 `SkillCatalog`（复用 SDK `loadSkills` 读取启用技能）供 UI 列表；不重复实现注入。
- pi2kotlin：本轮不改。后续若需要 `.agents/skills`、按技能启停过滤、热重载、`/skill:` 命令，再在 SDK 中增加配置面。

## 失败与边界

| 情况 | 处理 |
|---|---|
| 缺 description | 不进入可用列表，详情提示修复 |
| 重名 | 工作区优先；UI 警告全局被覆盖 |
| 非法 frontmatter | 不加载，保留文件，UI 标记 |
| 技能目录不存在 | 首次创建时自动建目录 |
| 停用目录与启用目录同名 | 以启用目录为准；提示冲突 |
| 编辑中会话正在运行 | 保存不打断；提示新会话生效 |
| 重载时正在运行 | 二次确认；用户取消则不重载 |
| 导入非 Markdown/超大文件 | 拒绝并提示 |

## 后续

- `.agents/skills` / 包安装技能。
- 技能启用白名单（SDK 过滤，不需要移动文件）。
- 热重载/文件监听。
- `/skill:name` 参数与用户显式调用。
- 技能详情里的 scripts/references 预览。

## 验收（实现时）

- 在全局和当前工作区各建一个技能，列表正确显示；新建会话的会话 JSONL 中出现对应 `<skills>` 条目。
- 停用后新会话不再出现；重新启用后恢复。
- 同名时工作区覆盖全局，UI 有标记。
- 缺 description 的文件不进入模型可见列表。
- 「重新加载」能刷新已打开会话，取消时不重建。
