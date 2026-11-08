# 图标源

App 内通用 UI 图标的唯一手工维护入口。一个图标 = 分类目录里的一个源文件 + `catalog.json` 里的一条登记；运行时资源、`DdIcons.kt` 与分类预览都由 `gen.mjs` 生成，生成产物不要手改。

## 结构

```
design/icons/
  catalog.json          唯一登记表：分类、中文名、DdIcons 成员、上游路径
  gen.mjs               生成脚本（node，无依赖；--check 校验漂移）
  preview.html          分类预览页（生成产物，图标读同目录源文件）
  navigation/ action/ status/ file/ chat/ ai/ system/
                        <id>.png | <id>.xml
```

## 命名

- 源文件 `<id>.png|.xml`，id 用 snake_case，取分类内的语义名（如 `menu`、`folder_open`、`audio_high`）。
- 生成资源 `ic_<category>_<id>.png|xml`，落在 `design/src/main/res/drawable-nodpi/`，由 `DdIcon` 统一 tint。
- `DdIcons` 成员名保留历史命名（`DdIcons.Add` 等），由 `catalog.json` 的 `dd` 数组映射，调用点无需改动。

## 分类

| 目录 | 中文 | 内容 |
|---|---|---|
| `navigation` | 导航与方向 | 菜单、返回、展开与方向指示 |
| `action` | 操作 | 添加、分享、下载与编辑操作 |
| `status` | 状态与反馈 | 确认、提示与通知 |
| `file` | 文件与数据 | 文件、文件夹、附件与数据类型 |
| `chat` | 对话与语音 | 对话、语音与消息 |
| `ai` | 智能与集成 | 智能体、模型与扩展能力 |
| `system` | 系统与设备 | 设置、外观、设备与工具 |

## 生成

在仓库根目录（`app/`）执行：

```
node design/icons/gen.mjs
node design/icons/gen.mjs --check
```

输出：`design/src/main/res/drawable-nodpi/ic_<category>_<id>.png|xml`、`design/src/main/kotlin/io/github/whywood/deepdeep/design/DdIcons.kt`、`design/icons/preview.html`。脚本会清掉旧命名（`ic_milo_*`、`ic_dd_*`）与不再登记的生成资源。

`--check` 只比对不写文件，有漂移时非零退出，可直接用于 CI。

## 新增或替换图标

1. 把源文件放进对应分类目录，文件名即 id；位图用 256×256 RGBA PNG，手写矢量用 24 viewport、2.1dp 圆头描边、纯黑描边。
2. 在 `catalog.json` 的 `icons` 里登记 `id`、`category`、`name_zh`、`dd`（DdIcons 成员名数组）、`upstream`（Milo 包内相对路径，手写矢量填 `null`）。
3. 运行生成并编译。未登记的源文件、缺登记的资源都会让脚本报错。

## 视觉规格

- 位图来自 `assets/milo-modern-icons-200-revised/semibold/`：256 画布、线宽约 2.15dp、`#273140` 线稿、图形约占画布 85%，只收录 App 实际用到的图标。
- 无 Milo 对应语义的少数线稿为手绘 vector：24 viewport、2.1dp 圆头描边、纯黑描边以便 tint。
- 两种都交给 `DdIcon` 统一 tint，不要在业务代码里直接 `painterResource`。

## 品牌与平台素材

Provider 图标（`assets/pi-provider-logos/`）、厂商图标（`assets/vendor-logos/`）、启动图标（`assets/branding/`）、鲸鱼娘素材（`assets/ui-kit/`）各自带 manifest、来源许可与生成流程，跨目录被市场与通知复用，因此不并入通用图标目录；索引见 `assets/README.md` 与本目录预览页页脚。
