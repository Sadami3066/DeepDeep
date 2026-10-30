# 0001 本地分发与 debug 签名

- 状态：已采纳 · 2026-10-30 · 任务 M1-8（签名切换归 M4-2，未执行）

## 背景

App 当前只做本地分发：开发者自己构建、`adb install -r` 覆盖安装，没有商店渠道（`README.md §跑起来`、`docs/DEVELOPMENT.md §日常命令`）。签名现状写在构建脚本里：

- `app/build.gradle.kts:37-42`：`release { signingConfig = signingConfigs.getByName("debug"); isMinifyEnabled = false }`——release 变体复用 debug 证书（`CN=Android Debug`），因此 debug/release 两个包同证书，可互相覆盖安装。
- `.gitignore:13` 忽略 `keystore.properties`：密钥配置本来就不入库；目前也没有任何 `signingConfigs` 自定义块。
- `AndroidManifest.xml:34 allowBackup="false"`，`app/src/main/res/xml/data_extraction_rules.xml:3-9` 把 `cloud-backup` 与 `device-transfer` 的 `root` 域全部排除，`fullBackupContent="false"`（`AndroidManifest.xml:37`）。结论：**所有会话、提醒、凭据只存在本机 `files/` 与 DataStore 里，没有系统备份兜底，卸载即全部消失。**

这使“换签名”不是配置调整，而是一次**用户数据删除事件**：Android 拒绝同包名、不同证书的覆盖安装（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`），唯一升级路径是先卸载旧包；卸载后 `allowBackup=false` 没有任何恢复手段。会话 JSONL 是唯一权威（`docs/ARCHITECTURE.md §2`），丢了就是全丢。

必须现在写成 ADR 的原因：2026-10-30 评审与复核里，“release 用 debug 签名”被列为 P2 缺陷（`docs/review/10-30/findings.tsv` GL-006），复核则明确它是**照做会让项目变糟**的建议（`docs/review/10-30/REVIEW_OF_REVIEW.md §4` 第 1 条、`docs/refactor/PLAYBOOK.md:87`）。不把理由固定下来，下一个 agent 会按“生产实践”顺手换掉。

## 决定

**首次公开发版之前，`release` 变体继续使用 debug 证书签名；该证书视为长期密钥，先做项目外副本备份；正式 keystore 只在“愿意让所有现装设备卸载重装”的发版节点生成并启用。**

落地口径：

1. 签名配置保持 `app/build.gradle.kts:39` 现状，不新增 `signingConfigs`；release 与 debug 同证书，保证覆盖安装。
2. 把 `~/.android/debug.keystore` 复制到项目外受控位置（加密备份/密码管理器），副本必须与原件字节一致并登记 sha256；**绝不入库**。这是硬要求：换电脑、重装 IDE 会自动生成不同证书，等同于换签名。
3. 正式 keystore 的创建归 M4-2（`app/build.gradle.kts` + tag 驱动 release workflow，human-approve），触发前提见「推翻条件」。
4. 相关但独立的取舍得单独记录、不要顺手做：`applicationId`/包名变更（等于换应用，数据不迁移）、`allowBackup` 打开（会把会话与凭据交给云备份）、minify（本 ADR 不管）。

## 被否决的方案

- **现在生成并启用专用 release keystore**（GL-006 的建议）：换签后现装 APK 无法覆盖安装，卸载即丢 `files/` 下全部会话、schedule/目标/任务记录与 DataStore 凭据；等“愿意让用户卸载重装”的发版节点再做。
- **重建/新建 debug keystore（换机器后让 IDE 自动生成、或手工 `keytool -genkey`）**：证书变了效果与换签完全相同，且更难发现；正确做法是保留并备份原证书。
- **把 keystore 提交进仓库以便任何机器可签**：私钥入库会随公开仓分发；debug 证书口令是公开默认值，入库也不等于安全，只增加泄露面与仓库卫生问题。
- **只发源码不发 APK、把签名问题绕过去**：不解决日常使用与更新通道，且首次发 APK 时问题原样回来。
- **声明“本地分发签名无所谓”但不写决定**：评审已两次建议改（GL-006、`docs/research/ANDROID_PRACTICES.md` 的 P1），不记录就会被反复当缺陷修。

## 后果

- 代价：证书 `CN=Android Debug`，不是“正式发布”形态；任何构建机想发可覆盖安装的更新，都必须持有这份证书副本。备份丢失 = 永久失去更新通道（只能让用户卸载重装 = 丢数据）。
- 公开分发/上架会被商店或信任链拒绝，这属于 M4 的发布工程，不是本 ADR 的缺陷。
- **被永久驳回的评审建议**：GL-006 的“为 release 生成专用 keystore”（推后到 M4-2）；`docs/research/ANDROID_PRACTICES.md` 中立即更换 release 签名的 P1 项；任何“签名是本地分发的工程债”式表述——它是明确取舍。
- 相关动作一律按“数据迁移事件”处理：换签名、改 `applicationId`、改包名、卸载清理，都必须单独任务卡 + 数据影响说明 + 回退路径，不允许夹在普通任务里做。

## 推翻条件

推翻（才可以生成/启用正式 keystore）当且仅当满足其一：

1. **首次公开发版，且接受所有现装用户卸载重装**：生成正式 keystore，启用 M4-2 的 tag/release workflow，同时提供迁移说明与数据影响清单。
2. **提供并验证了完整的数据导出/导入**（会话 JSONL、schedules、凭据等），使卸载不再等于丢数据：换签成本下降，可择机执行。
3. **上架应用商店**（商店要求正式签名/上传密钥体系）：必须换签，迁移方案随发布任务给出。

无论哪种，执行前先更新本 ADR（新开一篇“被取代”或追加修订记录），并在 PR 里写明：现装用户会经历卸载重装、数据不保留、回退只能装旧包再卸载。
