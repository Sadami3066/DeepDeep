# 真机验收证据

本目录只放验收结论与压缩后的截图，不放原始大图。条目定义见 `docs/dev/ACCEPTANCE.md`。

## 目录与命名

- 目录：`docs/dev/evidence/<YYYY-MM-DD>-<ACC-ID>/`，例如 `docs/dev/evidence/2026-10-31-ACC-CHAT-01/`。
- 截图：`NN-<slug>.png`，`NN` 从 `01` 递增（两位数字），`slug` 是备注里的 ASCII 片段；没有备注时为 `shot`。
- 结论：每轮一个 `verdict.md`，同一轮重复采集会追加截图与备注，不覆盖已有判定。

## 采集

```bash
adb devices                       # 先确认设备在线且已授权
./scripts/collect-evidence.sh ACC-CHAT-01 长任务 2s 状态行
./scripts/collect-evidence.sh ACC-CHAT-01 6s 工具行
```

- 每调用一次截一张当前屏幕；没有设备、没有 adb、ID 不在 `docs/dev/ACCEPTANCE.md` 时脚本立即报错退出，不会挂起。
- 截图长边压到 720px（依次尝试 magick、convert、ffmpeg、python3-PIL）；都没有时保存原图并提示，原图不入库。
- 单张目标 ≤500KB；超过时脚本会提示，先重新取景或删减内容再入库。
- 多设备：`ANDROID_SERIAL=<serial> ./scripts/collect-evidence.sh ...`。
- 自定义 adb 路径：`ADB=/path/to/adb ./scripts/collect-evidence.sh ...`。

## 每轮规范

- 每轮 5–10 张截图；超过 10 张脚本会提示，先合并场景再重新采集。
- 截图必须能指向判据里的「看到什么」：状态行、工具卡、列表行、详情页字段、错误提示各一张，避免同一屏连拍。
- `verdict.md` 由脚本生成，固定字段：条目 ID、旧编号、日期、设备、截图规格、判定（通过 / 失败 / 待定）、一行现象、截图清单；只补判定与现象，不改字段名。失败时写清与判据的偏差，不写「功能异常」这类无法复核的描述。
- 通过：保留 720p 截图与 verdict，随 PR 提交。
- 失败：保留原始截图作为问题证据，先修后重测；已提交的失败证据不删，新增一轮目录并在 verdict 里写轮次。

## 原始大图

历史原始截图共约 240MB，留在本地 `artifacts/validation/`，**不入库**。本目录只提交 720p 截图（单张 <500KB）与 `verdict.md`；原始图、录屏、日志原文留在本地，需要时在 PR 里引用路径。

`.gitignore` 目前只忽略原始大图目录 `artifacts/validation/`，不忽略本目录；提交前用 `git status --short docs/dev/evidence` 确认只暂存 720p 截图与 `verdict.md`。

## 引用方式

- PR、提交信息、任务卡只引用 `ACC-<域>-<序号>`，不复制条目正文、不回写旧流水号。
- 条目 ID 必须先存在于 `docs/dev/ACCEPTANCE.md`，格式为 `^ACC-[A-Z][A-Z0-9]{1,11}-[0-9]{2}$`（与 `scripts/collect-evidence.sh` 的校验一致）。
- 证据路径写在 PR 的验收段，例如 `docs/dev/evidence/2026-10-31-ACC-CHAT-01/`。
