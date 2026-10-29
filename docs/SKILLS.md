# Skills

状态：App 侧仓库、安装与全局/项目启停、Agent 自写技能自动入库、PRoot 技能目录挂载已实现；官方插件（含内部插件）贡献的技能走同一仓库/启用路径，创意工坊技能由内部插件「创意工坊」声明；SDK 负责扫描与 `<skills>` 注入。

## 作用域

| 层 | 路径 | SDK 读取 |
|---|---|---|
| 仓库 | `files/skills/<id>/` | 否 |
| 全局启用 | `files/agent/skills/<id>/` | 是 |
| 项目启用 | `<cwd>/.pi/skills/<id>/` | 是 |

Linux 内固定挂载两个目录，Agent 可以直接用 bash 读写：

| Linux 路径 | 宿主目录 | 含义 |
|---|---|---|
| `/skills` | `files/agent/skills` | 全局生效技能目录 |
| `/skill-repo` | `files/skills` | 技能仓库 |

技能是目录型 `<id>/SKILL.md`（可带 `scripts/`、`references/`、`assets/`）；插件贡献的技能同样是目录型，`SKILL.md` 之外可带 `references/`、`examples/`、`scripts/`、`assets/` 等附带文件，目录里的 `.deepdeep-plugin` 标记记录插件归属，Agent 写入时不要使用。宿主目录（含 `files/skills`）在执行前自动创建，rootfs 内的 `/skills`、`/skill-repo` 在安装与执行前自动创建。

`/home/.pi/skills` 不是 DeepDeep 的全局技能目录，也没有被挂载；全局技能用 `/skills`。`files/home/.pi/skills` 只是 home 工作区的项目技能，会被自动入库扫描；项目技能始终是当前工作区的 `<cwd>/.pi/skills`（Linux 内通常是 `/workspace/.pi/skills`）。

仓库是唯一安装入口；安装后不会立即被模型看到。打开开关后复制到启用路径，新会话生效。官方插件（含内部插件）会把自己声明的技能写入仓库 `files/skills/`，并在插件启用时复制到 `files/agent/skills/`（两处都带 `.deepdeep-plugin` 标记）；因此插件技能会出现在技能页，关闭插件时只移除生效副本，仓库副本保留，重新启用时恢复。插件设置也可以单独关闭某个技能（例如创意工坊的「启用创意工坊技能」）：插件不再声明该技能，仓库与生效副本一起移除，重新打开时由插件重新写入。插件技能以插件状态为准，在技能页手动改动会在插件下次同步时恢复。

## 自动入库

打开技能页（以及页面回到前台）时，先同步再列出：

- 扫描 `files/agent/skills`、`files/home/.pi/skills`、各工作区 `<cwd>/.pi/skills`。
- 合法技能：目录名是安全 id，`SKILL.md` 能解析出 `name` 与必填的 `description`。
- 只导入仓库 `files/skills` 中还不存在的技能；同名以 `files/agent/skills` 优先，其次 home，最后按工作区名排序，先到先得。
- 带 `.deepdeep-plugin` 的插件副本跳过；隐藏目录、缺失 `SKILL.md`、缺少 `description`、不安全 id 一律跳过。
- 导入是幂等复制；只有真正新增技能时才通知界面刷新。重复扫描不产生副本，也不覆盖已有仓库内容。
- 已入库并位于 `files/agent/skills` 的技能自然显示为全局启用；从项目目录导入的技能默认未全局启用。

因此 Agent 用 write 写 `files/agent/skills/<id>/` 或用 bash 写 `/skills/<id>/` 后，打开技能页即可看到；写入 `/skill-repo/<id>/` 则直接进入仓库，可在技能页启用。

创意工坊的 workshop 技能由内部插件「创意工坊」声明：App 启动时插件把 assets 里的技能目录（`SKILL.md`、`references/`、`examples/`）同步到仓库与全局生效目录，之后随 App 升级刷新内容；插件详情页的「启用创意工坊技能」可关闭。旧版本由 App 启动时直接写入的同名目录会被插件接管，不需要手动清理。除系统提示里的技能目录外，还可以在聊天框用 `/skill:<name>` 强制加载任意已启用技能：发送前读取技能文件正文并以 `<skill>` 块作为本轮上下文，未知技能会提示。命令可以放在开头，也可以放在文字中间或末尾（例如「用这个做 /skill:workshop」）；一条消息可提及多个技能，代码块里的 `/skill:` 不会触发。

## SDK 事实

pi2kotlin `loadSkills(cwd, agentDir, fs, projectTrusted)`：

- 扫描工作区 `<cwd>/.pi/skills` 与全局 `agent/skills`；同名工作区优先，按 name 去重。
- 目录型 `<skill>/SKILL.md`，可带 `scripts/`、`references/`、`assets/`；扫描根第一层也接受 `<skill>.md`。
- `description` 必填，缺失不加载；`name` 缺省用父目录名；`disable-model-invocation: true` 加载但不进入模型可见列表。
- 只有存在 `read` 或 `bash` 时注入 `<skills>`；只注入 name/description/location，正文由模型按需读取。
- 技能列表在 `createAgentSession` 时加载一次；已有会话不感知新增/删除。

DeepDeep 不使用 `<skill>.md` 单文件导入，也不编辑技能正文。

## 安装包

- 选择 `.zip`；一个压缩包只能包含一个 `SKILL.md`。
- `SKILL.md` 可以在 zip 根目录，也可以在唯一的包装目录内。
- 校验：ZIP 签名、`description` 非空、只有一个 `SKILL.md`、无绝对路径/`..`、≤32MB、解压 ≤64MB、≤1000 条目。
- 忽略 `__MACOSX`、`.DS_Store`、隐藏目录。
- 解压先写 staging，成功后替换仓库目录；失败清理 staging。
- 同名技能安装视为更新：替换仓库并刷新已存在的全局/项目副本。

## 启停

- 全局开关：复制/删除 `files/agent/skills/<id>`。
- 项目开关：复制/删除 `<cwd>/.pi/skills/<id>`。
- 项目列表显示仓库内全部技能；已全局启用的技能默认开启、不可关闭，并标记「全局」。
- 全局开启时删除该技能在所有工作区的项目副本，避免关闭全局后又“偷偷”生效。
- 全局关闭不自动恢复项目副本。
- 删除技能：仓库、全局副本、所有工作区项目副本一起删除。
- 使用复制而不是软链接：PRoot 只把当前 cwd 绑到 `/workspace`，指向仓库的软链接会让 `scripts/` 在 `bash` 内失效。

## 生效时机

- 技能在创建会话时读取一次；全局/项目启停、安装、删除都在新会话生效。插件安装、启停或设置变更会触发会话重配，在安全点切换到新技能快照，运行中的回合保持旧快照。
- 已有会话可用聊天框 `/skill:<name>` 强制加载一轮，或点聊天右上角「重新加载」重新读取技能/AGENTS.md，历史不中断。
- bash 写入 `/skills` 的新技能对新会话直接生效；打开技能页后同时补入仓库，之后可在 App 内启停。写入 `/skill-repo` 只进仓库，需要启用后才生效。

## UI

- 抽屉「技能」→ 仓库页。
- 右上角安装；列表显示名称、描述、全局开关；点击详情查看正文并删除。插件贡献的技能与普通技能一起展示。
- 空态说明安装 `.zip`；不再显示底部说明文字。
- 工作区设置「项目技能」→ bottom sheet 展示全部仓库技能；全局技能固定开启，其余多选，保存后写 `.pi/skills`。

## 失败

| 情况 | 处理 |
|---|---|
| 无 `SKILL.md` / 多个 | 拒绝，整包不落盘 |
| `description` 缺失 | 拒绝 |
| 路径穿越/绝对路径 | 拒绝 |
| 包过大/条目过多 | 拒绝 |
| 仓库技能损坏 | 列表跳过，不崩溃 |
| 同名重装 | 更新仓库与已有启用副本 |
| 全局/项目同时启用 | 全局优先；开启全局时移除项目副本 |
| 自动入库时仓库已有同名 | 跳过，不覆盖仓库内容 |
| 插件技能副本 | 跳过，仓库副本由插件维护 |
| 插件技能与现有无标记目录同名 | 内容不同时保留现有目录并跳过该技能；创意工坊技能会接管旧 App 写入的同名目录 |
| 运行中会话 | 保持旧快照；新会话读取新状态 |

## 后续

- 技能编辑、新建、技能市场、`.md` 单文件导入。
- 技能启用白名单（SDK 过滤，不改文件）。
- 热重载/文件监听（技能目录变化后自动刷新列表，不必打开技能页触发入库）。
- 仓库更新后批量同步项目副本。
