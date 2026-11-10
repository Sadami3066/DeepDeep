# data · 偏好与本地存储

本包只做本机偏好与文件存储：DataStore/SharedPreferences 偏好、附件与分享暂存、技能仓库、图片缩放、运行环境包和目录迁移，供 UI 与 AppGraph 装配使用。会话历史不在这里——pi JSONL 是唯一权威，由 runtime 维护。

## 文件

| 文件 | 职责 |
|---|---|
| `AgentDocStore.kt` | 项目 AGENTS.md 读写（override 优先、限 64 KB、原子写） |
| `BundledSkills.kt` | 随包技能：`assets/skills/**` 按内容同步到技能仓库与全局启用目录，可传入门控谓词并删除被排除的自有技能；跳过插件标记/用户自有目录 |
| `CapabilitySettingsStore.kt` | 能力开关 `files/capabilities.json`：屏幕操作 `screenOps` StateFlow 默认关闭 |
| `AppPreferences.kt` | 聊天界面偏好：过程折叠、自动重试、后台唤醒、置顶、字号 |
| `Attachment.kt` | 附件模型/分类/MIME 猜测与 `<attachments>` 块编解码 |
| `AttachmentStore.kt` | 导入附件到工作区，校验大小/空间/像素；文件读写经 `HostFiles`（Root 档可读系统目录，其余档自然失败），解析绝对路径时按共享存储开关放行 `/storage/emulated/0` 内文件；分享/预览前把不可读文件暂存到 cache，暂存不了（超大文件）时回退导出到共享存储或系统下载目录 |
| `DevPackageCatalog.kt` | 运行环境可装 Alpine 包与组合清单（体积、描述、安装后命令） |
| `DevPackageManager.kt` | 运行环境包安装/卸载/检测：经当前 `HostEnvironment.bash(cwd)` 执行 apk（LOCAL/ADB 走 proot、ROOT 走 Root Linux，按档位环境隔离），进度与通知 |
| `Downloads.kt` | 通过 MediaStore 把文件/流存到系统「下载」目录（`saveStreamToDownloads` 供 `HostFiles` 导出的文件复用；读文件走 `HostFiles.openStream`，Root 档同样可用） |
| `FilePreview.kt` | 文件预览类型判定与文本限量读取（256 KB、拒绝二进制，可经 `HostFiles` 读取 Root 档文件） |
| `HostFiles.kt` | 文件访问统一入口：经当前 `HostEnvironment.files` 做元数据/列目录/读流/写文件/暂存到 cache（Root 档暂存后再交给 FileProvider 或外部应用，1 小时清理）；超过 2 GiB 的文件不再暂存，ROOT 档经 root 复制到共享存储 `Download/DeepDeep/<uuid>/`（24 小时清理）后交给 FileProvider |
| `FloatingWindowPreferences.kt` | 悬浮窗开关与气泡坐标 DataStore |
| `FontPreferences.kt` | 界面字体样式（SharedPreferences，历史实现） |
| `ImageNormalizer.kt` | 聊天图片按 EXIF 摆正、缩放、压到 ≤1.5 MB base64 |
| `ImageScaling.kt` | 解码采样与长边 2000 缩放计算（纯函数） |
| `LanguagePreferences.kt` | App 语言读写与 LocalizedContext 资源包装 |
| `LegacyWorkspaceMigration.kt` | 旧 projects DataStore 补迁到 runtime WorkspaceStore |
| `LinuxSettingsStore.kt` | Linux 环境启用（默认开启）/共享存储、共享存储授权判断与目录 helper（`sharedStorageDirectory`/`appHomeDirectory`/`hasSharedStorageAccess`） |
| `MarketPresets.kt` | 技能/连接器/插件的市场本地预设（名称/说明/标签/类型）与纯函数 `filterMarketPresets` |
| `OnboardingPreferences.kt` | 新手引导开始/完成标记（SharedPreferences） |
| `OverlayEntryPreferences.kt` | 助手浮层入口方式（语音/拍照/无）DataStore |
| `PersonalizationStore.kt` | 昵称、交互风格、语气与附加指令 DataStore |
| `ProfileAvatarStore.kt` | 头像裁成 512 方图存 files/profile，revision 通知刷新 |
| [remote/](remote/AGENTS.md) | 远程连接：电脑与入口模型、配对链接解析、加密凭据、证书固定、网关请求与连接协调（详见子包文档） |
| `SharePayload.kt` | 解析系统分享的 URI/文本（去重、最多 20 条） |
| `ShareStaging.kt` | 分享附件暂存到 cache token 目录（限额、重名、1 小时清理） |
| `SkillArchive.kt` | 技能 zip 解析：单 SKILL.md、frontmatter、路径安全与体积限制 |
| `SkillRepository.kt` | 技能仓库：安装/删除/全局开关与项目级 `.pi/skills` 同步 |
| `StorageMigration.kt` | 旧目录迁到 home/workspaces 并重写会话 JSONL 的 cwd |
| `StorageMountStore.kt` | 公共存储挂载目录 `files/storage-mounts.json`：`StorageMount(id,name,description,path)`、最多 20 个、名称≤32/说明≤160、限定 `/storage/emulated/0/`、原子写 |
| `WorkspaceToolPolicyStore.kt` | 工作区工具策略 `files/workspace-tool-policies.json`：按 canonical cwd 存 `WorkspaceToolPolicy(skills, plugins, mcpServers)`，记录该工作区不继承的全局技能名、插件 id 与 MCP 服务器名；Mutex + 原子写 |
| `ThemePreferences.kt` | 深浅色主题 DataStore |
| `VoicePreferences.kt` | 语音自动朗读开关 DataStore |

## 要点

- 偏好默认 DataStore（一个 store 文件一个 `preferencesDataStore`），Flow 都 catch IOException 回退默认值；只有 Font/Language/Onboarding 仍是 SharedPreferences，新增偏好别再照抄。
- 技能目录分三层：`files/skills` 是安装仓库、`agentDir/skills` 是全局启用副本、`home` 与各工作区 `.pi/skills` 是项目级；`.deepdeep-plugin` marker 表示目录归插件所有，SkillRepository 不会把它导入仓库。
- 迁移只在启动时跑一次且幂等：StorageMigration 用 `.storage-migrated` 标记并重写会话 JSONL 头里的 cwd，LegacyWorkspaceMigration 仅在 WorkspaceStore 为空时补数据；目录名是 home/workspaces，不是旧 workspace/projects。
- 附件进入会话时以工作区绝对路径为 ID：AttachmentStore 把 Uri 拷进工作区 `attachments/` 并校验大小/空间/像素；分享入口先暂存到 cache 再走同一导入，暂存文件不进会话，历史靠 `<attachments>` 块还原路径。共享存储内的文件只在「共享存储」开关开启时按绝对路径解析，关闭后不再接受外部路径。
- 用户可见的文件读写一律经 `HostFiles`（`HostEnvironmentProvider.current().files`）：Root 档可直接读 `/data` 等系统目录，LOCAL/ADB 档沿用应用沙箱；不可直接读的文件（Root 档工作区文件）先暂存到 cache 再预览/分享，不要用裸 `java.io.File` 假设可读。
