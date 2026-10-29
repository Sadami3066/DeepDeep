# 证据说明

本目录属于审阅产物；未改变 app/runtime/shell/design 的产品实现和原测试。用户后续要求静态分析后没有再执行产品测试或设备操作。以下命令仅供以后选择性复现，不表示现在需要继续测试。

## 关键文件

| 文件 | 内容 |
|---|---|
| gradle-validation.log | 构建、lint、模块边界与原有测试任务的首轮日志；含 up-to-date 任务 |
| audit-tests.log | Test 任务强制重新执行后的最终日志，含原有 804 个测试与本轮 11 个观察用例 |
| test-summary.json | 最后一次运行的 existing/audit 分组计数、失败、跳过与测试内部时间 |
| test-results/ | 新增观察用例的 JUnit XML；观察缺陷的断言通过意味着问题已复现 |
| lint-results-debug.xml / lint-summary.json | 本轮 lint 的原始明细/聚合，不引用历史 STATUS 的旧计数 |
| source-inventory.json | 230 文件的职责、声明行号、import/同包词法候选、反向引用、SHA256 和规模 |
| sdk-0.2.2/ | 从实际 Maven Local Android SDK 0.2.2 sources.jar 选取的 8 个关键源码文件；避免拿旁边 SDK 工作区最新源码冒充当前依赖 |
| host.json | 执行平台和当前 HEAD，不包含用户凭据 |
| device-build.log / device-package-metadata.json | 独立包构建尝试与修正后的应用标识；编译成功不等于安装/设备测试成功 |
| device-install.log | 隔离副本安装被系统拒绝的记录；后续已停止设备测试 |
| atlas-preview.log | 报告图览的渲染检查日志，与 App 性能无关 |

## 本机观察用例

从仓库根运行的方式（会执行产品 JVM 测试，不会安装 APK）：

```bash
./gradlew -I docs/review/2026-10-29/evidence/audit.init.gradle \
  :app:testDebugUnitTest :runtime:testDebugUnitTest :shell:testDebugUnitTest
```

init 文件只为本次命令给单测增加本目录的 harness 路径，并禁用 Test 的 up-to-date 跳过。普通构建没有传此 init，不会把这些审阅用例纳入产品。

8 个缺陷观察：JSONL 坏尾追加、凭据乱序、工坊目录越界、提醒时钟重算、无换行输出、失败升级、全天日期、停止后重试。3 个性能观察：会话列表、rootfs 状态、Markdown/reducer（合在一个观察用例）。可控替身和合成文件降低副作用，但也意味着它们不是全部真实 Android/第三方服务集成测试。

## 重建报告与预览

```bash
python3 docs/review/2026-10-29/tools/build_inventory.py
python3 docs/review/2026-10-29/tools/build_atlas.py
python3 docs/review/2026-10-29/tools/serve_atlas.py
```

预览只绑定 `127.0.0.1:8786`，没有外网发布。源码预览只允许打开库存中的 230 个主 Kotlin 文件，不允许任意文件读取。Markdown 保持原始绝对文件/行链接，HTML 在本地预览时提供只读源码页。

SDK 源码快照和性能数字绑定当前 0.2.2/提交。未来改动后必须重建库存并重新决定测试范围，不能把本轮结果长期当作当前产品证明。
