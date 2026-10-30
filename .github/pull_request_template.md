## 这是什么

（三行以内人话；第一条写「用户会看到什么变化」）

## 档位（先选一档，定义见 `docs/dev/refactor/PLAYBOOK.md §0`）

- [ ] **T0 文档**：免卡、免 G2/G3，CI 只跑 `scripts/check-docs.sh`
- [ ] **T1 普通代码**：一卡；G1 必填；G2 由 Lead 抽检；涉 UI 才要 G3
- [ ] **T2 行为/数据/UI**：全量 G1 + G2 + G3 + 证据

## 关联

任务卡: **[T1/T2 卡 ID；T0 写 -]**　issue: #___　类型: feat / fix / refactor / perf / docs / test / chore / build

## 变更面（自动填，不手写）

```
git diff --name-only <base>..HEAD
```

文件数 ___ / 净增删 ___ 行 / 涉及模块 ___
是否 ⊆ 任务卡白名单：是 / 否 / T0 无需（否 → 说明理由）

## G1 机器闸门（T1/T2 贴原始输出；T0 只贴 `scripts/check-docs.sh` 输出）

- [ ] `./scripts/verify.sh` 通过
- [ ] 单测 ___ 条全绿（基线 1165，不得下降；0 skip）
- [ ] lint 0 error（警告 ___，基线 156）
- [ ] 编译警告 ___（基线 13）
- [ ] 模块边界校验通过
- [ ] 新增/修改行为有测试；**把修复回退后这些测试会红**（贴红的那次输出）

## G2 证伪结论（T2 必填；T1 可留空由 Lead 抽检）

- 最可能在哪类设备或状态下崩：
- 哪些现有测试其实没覆盖：
- 哪部分照做会变糟：
- 判定：成立 / 部分成立 / 不成立（不成立必须写理由）

## G3 人工验收（T2 必填；T1 仅 UI 变化时填）

- [ ] 真机覆盖安装（不卸载）
- [ ] 验收条目：#___ ～ #___（`docs/dev/ACCEPTANCE.md`），逐条打勾，截图在 `docs/dev/evidence/<date>-<id>/`
- [ ] 回滚演练：`git revert --no-commit <sha> && ./scripts/verify.sh` 干净通过
- [ ] 未覆盖风险（写清楚「这条我没验」）：

## 回滚

```
git revert <sha>
adb install -r <上一个 tag 的 APK>
```

数据影响：无 / 涉及文件 ___ / 升级验证条目 #___
