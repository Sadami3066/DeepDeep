# 基线快照（M0-6）

采集于 2026-10-30，分支 `refactor/team-foundation`，M3 全部 17 项落地后。
所有数字均由命令生成；**命令输出优先于本文**。数字漂移时先改文档再改代码。

## 基线

| 指标 | 基线值 | 阈值规则 |
|---|---|---|
| 单元测试 | **1165**（app 756 / runtime 377 / shell 32） | 不得下降；`failures=0`、`skipped=0` |
| Lint | **0 error / 156 warning**（app 150、runtime 1、shell 1、design 4） | error 必须为 0；warning 不得增加，增加需在 PR 说明 |
| 编译警告 | **13 条** Kotlin `w:` | 不得增加 |
| `verify.sh` 耗时 | 单实例 `--max-workers=1` 全量 1m18s（本机）；CI 4 核约 3–5 分钟 | 仅记录，不作门禁；构建必须串行 |
| release APK | 未纳入门禁（本地分发取舍，见 ADR-0001 待写） | — |

## 复算命令

```bash
./scripts/verify.sh

# 单测数（XML 是权威）
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
tot=fail=skip=0; per={}
for f in glob.glob("*/build/test-results/testDebugUnitTest/*.xml"):
    r=ET.parse(f).getroot(); m=f.split("/")[0]
    per[m]=per.get(m,0)+int(r.get("tests",0))
    tot+=int(r.get("tests",0)); fail+=int(r.get("failures",0))+int(r.get("errors",0)); skip+=int(r.get("skipped",0))
print("tests",tot,"failures",fail,"skipped",skip,per)
PY

# lint（error/warning 分开数）
for m in app runtime shell design; do
  python3 -c "
import xml.etree.ElementTree as ET
r=ET.parse('$m/build/reports/lint-results-debug.xml').getroot()
print('$m', sum(1 for i in r if i.get('severity')=='Error'), 'errors',
      sum(1 for i in r if i.get('severity')=='Warning'), 'warnings')"
done

# 编译警告（需要 --rerun-tasks 才会重新打印）
./gradlew :app:compileDebugKotlin :runtime:compileDebugKotlin \
  :shell:compileDebugKotlin :design:compileDebugKotlin --rerun-tasks --max-workers=2 2>&1 | grep -c '^w: '
```

## 已知环境

- JDK 21（字节码目标 17）、Android SDK 36 / build-tools 36.0.0、Gradle 9.8.2（wrapper 已加 sha256）
- 本机可用内存约 3–4G：`verify.sh` 必须串行执行（M0-7 加锁）
- `dev.pi` SDK 来自 pi2kotlin `v0.2.4` Release 的 m2 zip（sha256 `bc1e9fed229129e085079815ea4f0b5e9e1aeb9d72f2a72d8979fd2b865f0684`）

## 并发构建红线

2026-10-30 出现一次整机 OOM 死机：多个 agent 同时直接 `./gradlew`（绕过 verify.sh 的锁）叠加 lint 分析，超出本机可用内存。

- 所有 Gradle 调用必须走 `./scripts/gradlew-locked`（`flock` 串行 + 默认 `--max-workers=1` + `-Xmx1536m`）。
- 同一时间只允许一个构建；`verify.sh` 已封装该锁。
- 内存紧张时 lint 会报 `AsyncExecutionService.getService must not return null`（AGP lint 在低内存下的崩溃，不是代码问题）；串行 + 足够空闲内存下可稳定通过。
