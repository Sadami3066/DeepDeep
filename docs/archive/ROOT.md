> **已归档 · 2026-10-30 基线**：本文不再维护，内容可能与当前代码不符，仅作历史与决策输入保留。
> 当前文档入口见 [文档索引](../README.md)。

# Root 与 Android 高级能力调研

状态：**研究，不实现**。本文只记录可行性、风险与改造边界，不涉及代码改动与实施计划。

调研时间 2026-10-25；调研对象为当前 DeepDeep（`io.github.whywood.deepdeep`，minSdk 29）与本机 Redmi Note 12 Pro+（Android 13 / MIUI 14）。root 方案、解锁政策与 Play Integrity 对抗都在持续变化，落地前需要重新核实。

## 结论摘要

- 本机已解锁并已 root（APatch + Zygisk Next + LSPosed + 隐藏链），root 能力可以直接验证；但产品不能假设用户设备已 root，绝大多数目标设备是未解锁、未 root 的普通手机。
- root 相对当前公开 API 的增量能力主要是：跨应用数据与短信库直读、无需系统授权的截图、系统设置/包管理/输入注入、更底层的自动化与虚拟屏。这些能力同时是隐私与安全上最敏感的部分，风险和产品信任成本高于收益。
- 现有公开 API（无障碍 GUI、短信 ContentProvider、分享、语音、PRoot bash）已经覆盖日常需求；root 只适合作为高级用户在设置里显式开启的增强层，不应成为默认路径。
- 若目标是“比现在多拿一点系统能力”，优先评估 **Shizuku（adb shell 级，无需解锁 bootloader）**；root 改造按 P1→P4 分阶段，每阶段都必须带确认、审计与降级。
- 不建议：自动/静默 root、后台或定时 root 操作、关闭 SELinux、修改系统分区、通用读取任意应用数据、把 root 工具暴露给工坊页面 Agent。

## 本机证据

### 设备与系统

| 项 | 值 |
|---|---|
| 型号 | Redmi Note 12 Pro+（`22101316UCP`，codename `rubypro`，MTK `mt6877`） |
| Android | 13（`ro.build.version.release=13`） |
| ROM | MIUI 14（`ro.miui.ui.version.name=V140`，`V14.0.5.0.TMOCNXM`） |
| 构建 | `user` / `release-keys`，安全补丁 2026-09-05 |
| 内核 | 4.19.191（MTK，2023-11 构建） |

### root 环境

| 项 | 证据 |
|---|---|
| su | `su -c id` → `uid=0(root) gid=0(root) groups=0(root) context=u:r:magisk:s0` |
| 方案 | `su --version` → `11224:APatch`；管理包 `me.bmax.apatch`（versionCode 11224）；`/data/adb/apd`、`/data/adb/ap/` |
| 授权 | `/system/bin/su`；`ADB shell` 已被授权，命令耗时约 10ms |
| 原始 boot 备份 | `/data/adb/ap/ori.img`（约 128MB），APatch 自带恢复入口 |
| SELinux | `Enforcing` |
| 隐藏链 | 模块：Zygisk Next 1.5.0、LSPosed 2.2.0、Play Integrity Fix v4.7-1、Tricky Store v1.4.1、bszip 环境隐藏、HMA OSS Zygisk |
| 隐藏工具 | `/data/adb/ap/bin/resetprop` 存在（属性伪装能力） |
| Shizuku | 未安装 |

### 解锁与完整性状态（关键判读）

```text
# /proc/cmdline（真实启动参数，需要 root 才能读）
androidboot.vbmeta.device_state=unlocked
androidboot.verifiedbootstate=orange
androidboot.veritymode=enforcing
```

```text
# getprop（当前用户态看到的属性）
ro.boot.flash.locked=1
ro.boot.verifiedbootstate=green
ro.boot.vbmeta.device_state=locked
```

`cmdline` 与 `getprop` 相互矛盾，且本机存在 `resetprop` 与隐藏模块，说明**属性值已被隐藏链改写**。结论：

- 系统实际处于**已解锁**状态（orange / unlocked），只是用户态属性被伪装成 locked / green。
- **不能用 `ro.boot.*` / `getprop` 判断设备是否解锁或是否 root**，属性可以伪造。
- 本机已经是一套成熟的“root + 反检测”环境，Play Integrity 通过依赖模块与 keybox 的持续维护，不是稳定状态。

### 能力探测（root shell 下实测）

| 能力 | 结果 |
|---|---|
| `settings get global adb_enabled` | 可用（返回 `1`） |
| `pm path` / `pm list packages` | 可用（358 个包） |
| `screencap -p /data/local/tmp/...` | 可用，**没有系统授权弹窗**，截图后已删除、未保留 |
| 短信库 | `/data/data/com.android.providers.telephony/databases/mmssms.db` 可列出（未读取正文） |
| `input` | 支持 `-d DISPLAY_ID`；系统也有 `uiautomator`、`app_process`、`dumpsys`、`cmd` |
| 显示 | 只有物理屏 `mDisplayId=0`（1080×2400），没有现成虚拟屏 |
| 系统分区 | `/vendor`、`/product`、`/system_ext` 为 erofs **只读**；`/data` 为 f2fs 可写 |

为避免打扰设备与收集隐私，本次没有触发 App 进程的 su 授权弹窗、没有读取短信正文/其他应用数据、没有截图留存。**App 授权弹窗、拒绝与超时行为需要在实现阶段用真机确认。**

## 一、Bootloader 解锁（小米）

### 官方流程

1. 在小米社区提交「BL 解锁申请」（或在海外官网 `mi.com/global/unlock` 走对应流程），开启开发者选项与 USB 调试、在「设备解锁状态」里绑定小米账号与设备。
2. 等待审核/资格：申请通过后仍有等待期，经典规则是绑定后 **168 小时（约 7 天）**；HyperOS 时代小米又叠加了门槛：小米社区等级（通常要求 5 级）、解锁答题、实名信息、账号状态、名额与频次限制。具体分数、等级、名额以当期申请页公告为准。
3. 使用小米官方解锁工具（Windows 程序，fastboot 模式）执行解锁；Linux 上需要 Windows 虚拟机/双系统，不建议第三方解锁工具。

### 限制与不确定性

- 解锁资格细则随小米社区公告调整，且国行/海外、机型、系统版本各不相同；**不存在长期通用的“攻略”**，必须以当期申请页与官方帮助为准。
- 官方明确提示解锁有风险（安全、数据、保修、系统稳定性），并要求用户自行承担。
- 解锁会触发 `fastboot flashing unlock`，**清空用户数据**，并让设备进入 orange state；重锁需要刷回官方完整镜像，否则可能无法开机。

### 对本项目的含义

- 不能把“需要解锁 + root”当作默认产品前提；这会把大多数用户挡在门外，并在隐私/保修上承担解释成本。
- 如果保留 root 能力，必须在设置/隐私页明确写出：需要先解锁 bootloader、可能失去保修/被银行 App 拒绝、解锁会清数据。
- 本机已完成解锁（orange state），仅能证明“技术上可用”，不能代表用户设备现状。

官方参考：[小米帮助中心 BL 解锁](https://www.mi.com/service/support/BL-loc)、[小米官方解锁页](https://www.mi.com/global/unlock/)、[解锁安全提示 FAQ](https://www.mi.com/uk/support/faq/details/KA-533394/)、[Bootloader 锁说明 FAQ](https://www.mi.com/uk/support/faq/details/KA-07238/)。

## 二、Root 方案：Magisk / KernelSU / APatch

| 方案 | 原理 | 安装前提 | 模块与生态 | 隐藏/对抗 | 本机 |
|---|---|---|---|---|---|
| Magisk | patch boot/init_boot 镜像，systemless 覆盖与模块 | 解锁 + 可用 boot 镜像 | 模块生态最大；MagiskHide 已在 v24 移除，改为 Zygisk + DenyList（需 Shamiko 等增强） | 依赖 Zygisk 模块，属猫鼠游戏 | 未使用 |
| KernelSU | 把 su 放进内核（内核内嵌或 GKI LKM） | 解锁；GKI 2.0（5.10+）可直接用 LKM，非 GKI 机型需自行编译内核 | 支持 overlayfs 模块；有内核级隐藏/卸载机制 | 依赖内核与机型适配 | 未使用 |
| APatch | 基于 KernelPatch 的内核补丁（不是完整内核编译），patch boot 镜像 | 解锁 + 原始 boot 镜像 | 兼容 Magisk 模块，另有 KPM 内核模块 | 自带内核级能力，隐藏仍需模块 | **当前使用（11224）** |
| Zygisk | 不是 root 方案：向 zygote 注入代码，让模块运行在每个应用进程 | 需要 root 提供注入点 | LSPosed、Play Integrity Fix 等依赖它 | 是隐藏链的基础设施 | Zygisk Next 1.5.0 |

要点：

- 三者对 App 的接口本质相同：提供一个 `su`（以及可选的 root manager 授权弹窗）。**App 侧不应绑定具体 root 方案**，只依赖“`su` 可执行 + 用户授权”这一契约；KernelSU/APatch 的 su 语义与 Magisk 兼容。
- 本机内核是 4.19（非 GKI 2.0），KernelSU 需要自行编译内核；APatch 不要求编译内核，因此本机选择了 APatch。
- Zygisk 只影响模块与隐藏，不影响 App 调用 su；没有 Zygisk 的 root 设备也能跑 root 工具。

参考：[Magisk](https://github.com/topjohnwu/Magisk)、[Magisk 安装文档](https://topjohnwu.github.io/Magisk/install.html)、[KernelSU 官方文档](https://kernelsu.org/guide/what-is-kernelsu.html)、[APatch](https://apatch.dev/)、[APatch 安装](https://apatch.dev/install.html)、[APatch FAQ](https://apatch.dev/faq.html)、[Zygisk Next](https://github.com/Dr-TSNG/ZygiskNext)。

## 三、风险清单

| 风险 | 触发 | 影响 | 缓解 |
|---|---|---|---|
| 清空数据 | 解锁 bootloader、部分救砖/刷机操作 | 用户数据不可恢复 | 解锁前强提示并备份；重锁/刷机前备份 |
| 保修与售后 | 解锁、root、刷机 | 售后可能拒保或按人为损坏判定；具体以售后检测为准 | 保留官方完整包与原始 boot 备份（本机 APatch 已有 `ori.img`）；必要时刷回官方镜像并重锁；不把“解锁不影响保修”当确定结论 |
| OTA 升级 | 改过 boot/系统分区后收到增量升级 | 增量包校验失败、升级后 root 丢失、模块不兼容导致 bootloop | A/B 机型可“安装到非活动槽位”（Magisk 能力）；APatch/KernelSU 需 OTA 后重新 patch boot；大版本升级前查兼容性并备份 boot；失败用官方包恢复 |
| Play Integrity | 解锁/root/隐藏失败 | `MEETS_DEVICE_INTEGRITY` 不通过，Play 商店/银行/流媒体/游戏降级或拒绝 | 隐藏链（PIF/TrickyStore/HMA）可短期通过，但依赖 keybox 与 Google 检测更新，**随时可能失效** |
| 银行/支付 | root、解锁、自动化注入被检测 | 支付功能禁用、风控、封号 | 不在 root 设备上使用关键银行 App；工具层不做隐蔽自动化；对用户明示风险 |
| 安全面扩大 | 恶意应用/模块、远程调试、root 授权过宽 | 任意数据被读、静默安装、设备被控制 | 最小授权、只装可信模块、默认关闭 root 工具、审计、后台禁用 |
| 二手/转卖 | 设备保留解锁与 root | 下一位用户/回收价受影响 | 恢复官方系统并重锁 |
| 自动化被检测 | 输入注入、截图、后台操作 | 银行/游戏/社交应用风控 | 可见指示 + 用户确认 + 不做隐蔽自动化 |

Play Integrity 参考：[官方 verdict 说明](https://developer.android.com/google/play/integrity/verdicts)、[API 概览](https://developer.android.com/google/play/integrity/overview)。

## 四、App 侧接入设计（su / libsu / RootShell）

### 探测与状态机

只在用户主动开启“高级能力”或首次调用 root 工具时探测，不随 App 启动探测。

| 状态 | 判定 | 界面与行为 |
|---|---|---|
| `unsupported` | 没有可执行的 `su` | 显示“本机不支持”，root 工具不出现 |
| `disabled` | 用户未开启 root 能力 | 不探测、不弹窗 |
| `not_requested` | su 可用但从未申请 | 展示“检测/授权”入口，用户点击才触发弹窗 |
| `granted` | `su -c id` 返回 uid 0 | 允许使用已开启的 root 工具 |
| `denied` | root manager 明确拒绝 | 提示去管理器授权，不自动重试 |
| `timeout` | 等待授权超时 | 提示未响应，可重试（用户主动） |
| `error` | su 执行异常/被系统拦截 | 展示错误并降级 |

判定原则：**以功能探测为准**（执行 `su -c id`，看 uid 与退出码），不扫描 su 路径、不读 `ro.boot.*`（本机证据已证明属性可被伪装）、不依赖特定 root manager 包名。

### su / libsu / RootShell

- 推荐用 [libsu](https://github.com/topjohnwu/libsu) 封装（[Shell API](https://topjohnwu.github.io/libsu/com/topjohnwu/superuser/Shell.html)）：统一处理 su 进程、stdout/stderr、退出码与长驻 shell，超时由调用方控制；也可用 `ProcessBuilder("su", "-c", command)` 从零实现。
- 两种运行方式：每次命令新建 `su -c`（实现简单，本机已授权时约 10ms）或长驻一个 root shell（延迟更低、需要分隔符协议与并发队列）。建议**单 shell 串行队列**，避免并发弹窗与输出交错。
- 每命令约束：默认超时 15s（长任务显式放宽到 ≤120s）；输出上限（如 256KB）并标记截断；stderr 与退出码单独返回；`cwd` 限定在可写目录。
- 生命周期：root 工具只在 App 前台、会话活跃时可用；后台任务、定时任务、工坊页面 Agent 一律不可用。进程死亡后 shell 重建，重新走探测状态机。

### 授权缓存

- **不把“已授权”持久化成安全凭据**：root manager 的授权随时可能被用户撤销，进程重启或工具首次使用时用 `su -c id` 重新验证（已授权时无弹窗、开销可忽略）。
- 只缓存“最近一次探测结果 + 时间”用于设置页展示；拒绝/超时后进入冷却，只有用户主动操作才重试，避免循环弹窗。
- “本次会话允许 root 工具”可以是会话级偏好（降低重复确认成本），但危险操作仍要单次确认。
- 不向模型暴露授权细节（包名、root 方案、授权列表）；模型只需要知道工具是否可用。

### 拒绝 / 超时 / SELinux

- 授权弹窗由 root manager 弹出，超时时间由 manager 决定（通常数十秒，可配置），App 无法控制；App 侧等待要留足余量，超时后 kill 进程并标记 `timeout`，提示用户去 root manager 处理。
- 拒绝后不自动重试；返回结构化错误码（`root_unavailable` / `root_denied` / `root_timeout` / `root_failed`）与可操作建议，模型不得循环重试。
- SELinux 本机为 `Enforcing`，su 运行在 root manager 的上下文（本机 `u:r:magisk:s0`），**不是无限制**：部分路径/ioctl 仍会被 SELinux 拒绝，此时返回错误并说明能力边界。
- 不采用 `setenforce 0`、不 remount `/system`；本机系统分区为 erofs 只读，写系统分区不在工具范围内。
- 输出与隐私：root 输出可能包含手机号、验证码、聊天内容；工具结果进入模型前需要截断/脱敏，审计日志不保存敏感正文。

## 五、工具改造清单

现有工具层：SDK 原生工具 + App 层 `CodingTool` + 插件工具 + PRoot bash。root 工具应作为**独立的 App 层工具族**（`CodingTool`，通过 `AppToolContext` 注册），与 `bash` 的 PRoot 用户空间区分：PRoot 里的 `bash` 是 App uid 的用户空间 Linux，**没有 Android root**；root 工具才是 uid 0。

| 工具 | 能力 | 主要风险 | 建议阶段 | 关键约束 |
|---|---|---|---|---|
| `root_shell` | Android root 命令执行 | 任意命令、最高破坏力 | P1 只读白名单 → P3 受限任意 | 默认关闭、逐次确认、危险模式拦截、审计 |
| `root_file_*` | 受控文件读/写/列目录 | 隐私、误删、系统损坏 | P2 只读 → P3 定向写 | 路径白名单/黑名单、规范化防 symlink 逃逸、写前备份 |
| `android_settings` | `settings get/put` | 改系统行为、隐私 | P2 只读 → P3 白名单写 | 键白名单、记录旧值、逐次确认 |
| `android_pm` | `pm` 查询/安装/卸载/权限 | 系统不稳定、权限绕过、不可恢复 | P1 只读 → P3 用户 App 操作 | 系统包禁止清单、`pm clear` 默认禁用 |
| `android_input` | `input tap/swipe/text/keyevent [-d]` | 模拟用户、误操作、被检测 | P3 | 仅前台、目标包名校验、可随时停止、不点授权/支付 |
| `android_screencap` | root 截图 | 隐私极高、静默监控 | P2（显式开启 + 前台指示） | 每次确认/会话开关、自动清理、不进模型 |
| `android_app_data` | 读应用私有数据/数据库 | 隐私灾难、信任崩塌 | P3 仅短信/用户指定，P4 其他 | 默认关闭、只读、逐次确认、结果截断 |
| `root_sms_db` | 直读短信库 | 隐私、ROM 差异 | P3（若公开 API 不够） | 只读、SQL 参数化、只查用户要求的时间段 |
| `ui_automation` | UiAutomation dex / 自动化服务 | 隐藏 API 兼容性、被检测 | P4 研究 | 不作默认，优先无障碍与 Shizuku |
| `virtual_display` | 虚拟屏创建/捕获/注入 | 隐蔽自动化、DRM 限制 | P4 研究 | 与“用户可见可控”原则冲突，需先定场景 |
| `physical_display` | 物理屏抓取/底层注入 | 抢输入、误操作、隐蔽控制 | P4 | 前台可见指示、单次确认、随时停止 |

### root_shell

- 与 `bash` 的区别：`bash` 在 PRoot 里以 App uid 运行，只能碰 App 能碰的东西；`root_shell` 是 Android uid 0，能读写系统与所有应用数据。
- 参数：`command`、`timeout_ms?`、`cwd?`；结果：`exitCode`、`stdout`、`stderr`、`truncated`。
- P1 只开放白名单只读命令：`id`、`getprop`（指定 key）、`dumpsys`（白名单服务）、`df`、`ls/stat`（白名单路径）、`settings get`、`pm list/path`、`date`。
- P3 才允许受限任意命令：命令预览 + 逐次确认 + 危险模式拦截（`rm -rf`、`dd`、`mkfs`、`pm clear`、`reboot`、`setenforce`、`mount -o remount`、写 `/system` 等），仍不推荐完全开放。
- 工具参数不得直接拼接进 shell 字符串；固定命令模板 + 参数校验/转义，避免模型把不可信内容变成命令。

### 受控文件

- 读：P2 只读白名单（公共存储、`/data/local/tmp`、用户显式指定的应用目录）；短信/微信等数据库读取属于 P3，逐次确认。
- 写：P3 起，仅限 App 自有目录或公共目录；写前备份、原子写、校验路径（`canonicalize` 后仍在白名单内，防 `..`/symlink 逃逸）；禁止 `/system`、`/vendor`、`/data/system`、`/data/misc`、keystore、root manager 目录（`/data/adb`）。
- 大文件/二进制分块与限流，结果不整段进模型。

### settings

- 只读 P2：`settings get`/`settings list`，用于查看开关状态。
- 写 P3：仅白名单 key（如亮度/字体缩放/勿扰类），写前读取旧值、写入后校验、支持回滚；逐次确认。
- 不开放 `settings put secure` 的任意 key（可改无障碍/输入法/隐私相关设置），不写 `--user` 多用户场景。

### pm

- 只读 P1：`pm list packages`、`pm path`、`pm dump`、`dumpsys package`。
- 写 P3：仅用户安装的 App 的安装/卸载/权限授予/撤销，逐次确认；`pm clear` 默认禁用（不可恢复），需要时仅对用户 App 且二次确认。
- 系统应用（OEM/关键包）停用/卸载列入 P4 且默认禁止清单；正常安装应优先走系统安装器 Intent，而不是 root `pm install`。

### input

- 与 `gui_*`（无障碍）高度重叠，**优先无障碍**：系统本身有可见提示，权限模型更清晰。
- root `input` 只用于无障碍覆盖不到的场景（系统弹窗、部分游戏/第三方界面），并复用 gui 的安全规则：不点系统权限/无障碍授权/设备管理员/支付确认，不读密码验证码。
- P3：仅在 App 前台 + 目标包名校验 + 用户逐次确认/本次会话允许 + 可随时停止；锁屏、DRM、安全窗口可能无效，失败时降级说明。
- `-d` 参数与虚拟屏相关，物理屏用默认 display 0（本机 `input` 帮助已确认支持 `-d`）。

### screencap

- root `screencap` 不需要 MediaProjection 的系统授权（本机实测无弹窗），这一步跨过了 Android 的隐私提示，风险等级高。
- 建议：默认关闭；设置页显式开关 + 使用前确认 + 前台可见指示（状态条/通知）；只在前台、只截当前 App；截图保存到 App 私有缓存并自动清理；不自动送模型（需要时先给用户看）。
- 替代方案：MediaProjection（官方弹窗授权，Android 14 起每次会话重新授权），更符合用户预期；root 截图只在高级用户明确要求时提供。

### 应用数据 / 短信库

- 公开 API 的短信读取受 `READ_SMS` 权限与默认短信应用/ROM 限制（MIUI 对非默认短信应用可能只暴露部分记录）；root 直读 `mmssms.db` 可以拿全量，但绕过了系统权限模型。
- 应用私有数据（`/data/data/<pkg>`）同理：root 可读所有聊天记录、账号、偏好，属最高隐私等级。
- 建议：不提供“读任意应用数据”的通用工具；P3 只做用户显式指定的短信只读查询（如果公开 API 确实不够），默认关闭、逐次确认、只读、时间范围/条数限制、正文截断，结果不默认进模型。
- 实现方式：root 下把数据库拷到 App 私有目录，再用 Android `SQLiteDatabase` 只读查询（不依赖设备上的 `sqlite3`）；查询参数化，不拼接 SQL。

### UiAutomation dex

- `UiAutomation` 是测试框架 API（节点树、事件注入、截图），需要 instrumentation 上下文，普通 App 拿不到。
- 研究中的可行路径：root/shell 用 `app_process` 启动自建 dex（类似 Shizuku 的 UserService），或通过 `uiautomator` 命令运行测试 dex，以 shell/root 身份使用隐藏 API。
- 收益：不需要用户在系统设置开无障碍、可在后台运行、可截图；但这些收益同时意味着“隐蔽自动化”，与产品的可见可控原则冲突。
- 限制：隐藏 API 随 ROM/Android 版本变化，系统升级易失效；root 不等于 system uid，很多系统服务仍会校验 shell/system 身份；维护成本高。
- 结论：P4 研究，不作默认方案；优先评估 Shizuku 或现有无障碍。

### 虚拟屏 / 物理屏

- 虚拟屏：官方路线是 MediaProjection + `VirtualDisplay`（用户授权、不能注入事件）；root 侧要以 system 权限创建虚拟屏，复杂度高、兼容性差、DRM 应用会拒绝。适合“不干扰用户物理屏的后台自动化”，但正好也是最需要警惕的隐蔽自动化场景。
- 物理屏：直接对 display 0 注入（`input -d 0`）或 `sendevent`/`/dev/uinput` 更底层注入，以及 root 截图。比无障碍更“真”、更难被检测，因此风险更高：会抢用户输入、可能误触支付/发送。
- 若做：必须前台可见指示（悬浮条/通知）+ 用户单次确认 + 随时停止 + 目标包名限制；默认不开启，列入 P4。
- 本机只有物理屏 `mDisplayId=0`，虚拟屏能力未验证。

## 六、确认、审计、降级

### 三层开关

1. **全局**：设置页「高级能力（root）」总开关，默认关闭；隐私页写明数据风险。
2. **会话/工具级**：本次会话是否允许 root 工具；危险工具（截图、输入、读短信、写文件）单独授权。
3. **单次确认**：危险操作逐次弹出 App 内确认卡片（复用 `QuestionCenter`/内联确认样式），显示工具、目标、命令/参数摘要与风险，提供“允许一次 / 本次会话允许 / 拒绝”。root manager 的授权弹窗只代表“允许 App 使用 su”，不能替代 App 内按操作的确认。

### 审计

- 记录：时间、会话、工具、参数摘要（敏感值脱敏）、退出码、耗时、结果大小、是否确认。
- 不记录：短信正文、截图内容、其他应用数据原文、可能含密钥的输出；日志本地保存，用户可在设置查看/清除，可导出。
- 工具调用卡本身仍是主要审计面；root 额外记录“为什么触发”（模型请求/用户点击）。

### 降级

- 无 root（`unsupported`/`denied`/`timeout`/SELinux 拒绝/命令失败）时，工具返回结构化错误与替代建议：改用无障碍 GUI、公开 API、MediaProjection、PRoot bash。
- 禁止自动重试授权弹窗；拒绝后冷却到用户主动重试；同一会话内对同一失败不反复提示。
- UI 上不把“root 不可用”当错误噪音，只在用户主动使用相关功能时提示。
- 工坊页面 Agent 不注册任何 root 工具（沿用当前工坊对 Android 敏感工具 `gui_*` 等的过滤原则），页面 JS 不能调用 su。
- 文档同步：`docs/user/ANDROID_CAPABILITIES.md` 目前定位是“系统公开 API”，接入 root 需要新增“可选 root 增强”章节；隐私页需要增加 root 数据范围与风险说明。

## 七、分阶段建议

| 阶段 | 内容 | 前提与落地标准 |
|---|---|---|
| P0（本文档） | 只调研、不实现、不改代码 | 结论与风险评审通过 |
| P1 | root 状态行 + 手动检测；`root_shell` 只读白名单；审计与错误码；全部默认关闭、不暴露给页面 Agent | 落地前确认授权/拒绝/超时/无 root 四种状态；后台与页面 Agent 不可用 |
| P2 | 受控文件只读、`settings` 只读、root 截图（显式开关 + 前台指示 + 逐次确认）、短信只读（仅当公开 API 不够） | 确认卡片、审计、自动清理与权限关闭路径完整 |
| P3 | 受控文件定向写、`settings` 白名单写、`pm` 用户 App 操作、`input` 基础操作、应用数据/短信库只读 | 每类操作都有回滚/备份/限流；危险操作二次确认 |
| P4 | 受限任意 `root_shell`、虚拟屏、UiAutomation dex、物理屏底层注入、系统应用操作 | 只有在明确用户场景下评估；默认不实现；仍然可见可控 |

### 替代路线：Shizuku（优先评估）

- Shizuku 通过 ADB/无线调试（或 root 启动 app_process）让普通 App 以 **shell（uid 2000）** 身份调用系统 API，不需要解锁 bootloader、不修改系统分区，风险显著低于 root。
- 可覆盖：部分 `input`/`settings`/`pm` 类操作、系统服务查询、部分截图/窗口能力；不能读其他应用私有数据与短信库。
- 对 DeepDeep：如果 root 改造的真实需求只是“系统设置、输入、截图、包管理”，**先评估 Shizuku 就够**；root 留给真正需要访问系统/其他应用数据的场景。

参考：[Shizuku 官网](https://shizuku.rikka.app/)、[Shizuku 仓库](https://github.com/RikkaApps/Shizuku)。

## 八、不建议做的事

- 默认开启 root、启动时自动探测/弹授权、静默执行 root 操作。
- 后台任务、定时任务、工坊页面 Agent 使用 root 工具。
- 用 root 绕过 App 自身的权限/隐私设计（例如绕过 READ_SMS、无障碍开关）。
- 关闭 SELinux（`setenforce 0`）、remount `/system`、改系统分区。
- 提供通用的“读任意应用数据/任意文件”工具，或默认把 root 输出原样送进模型。
- 静默截图、隐蔽输入注入、虚拟屏后台自动化。
- 在代码里绑定某个 root 方案（Magisk/KernelSU/APatch）；只依赖 `su` 契约。
- 把 root 授权状态、root 方案、隐藏链细节暴露给模型或写进日志。

## 参考

- 小米解锁：[帮助中心 BL 解锁](https://www.mi.com/service/support/BL-loc)、[官方解锁页](https://www.mi.com/global/unlock/)、[解锁安全提示](https://www.mi.com/uk/support/faq/details/KA-533394/)、[Bootloader 锁说明](https://www.mi.com/uk/support/faq/details/KA-07238/)
- Root 方案：[Magisk](https://github.com/topjohnwu/Magisk)、[Magisk 安装](https://topjohnwu.github.io/Magisk/install.html)、[libsu](https://github.com/topjohnwu/libsu)、[libsu Shell](https://topjohnwu.github.io/libsu/com/topjohnwu/superuser/Shell.html)、[KernelSU](https://kernelsu.org/guide/what-is-kernelsu.html)、[APatch](https://apatch.dev/)、[APatch 安装](https://apatch.dev/install.html)、[APatch FAQ](https://apatch.dev/faq.html)、[Zygisk Next](https://github.com/Dr-TSNG/ZygiskNext)
- 完整性：[Play Integrity verdict](https://developer.android.com/google/play/integrity/verdicts)、[Play Integrity 概览](https://developer.android.com/google/play/integrity/overview)
- Android 能力：[SELinux](https://source.android.com/docs/security/features/selinux)、[UiAutomation](https://developer.android.com/reference/android/app/UiAutomation)、[MediaProjection](https://developer.android.com/media/grow/media-projection)、[VirtualDisplay](https://developer.android.com/reference/android/hardware/display/VirtualDisplay)、[DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager)、[ADB shell](https://developer.android.com/tools/adb)、[READ_SMS](https://developer.android.com/reference/android/Manifest.permission#READ_SMS)
- 替代路线：[Shizuku](https://shizuku.rikka.app/)、[Shizuku 仓库](https://github.com/RikkaApps/Shizuku)
