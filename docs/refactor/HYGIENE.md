# 仓库卫生（M1-6）

规则落点：`.gitignore`（哪些不入库）｜`.gitattributes`（换行与二进制）｜`.mailmap`（作者归一）。改动这三处时先看本文件；新增二进制类型必须同时补 `.gitignore` 与 `.gitattributes`。

## 1. 入库与不入库

| 路径 | 处置 | 说明 |
|---|---|---|
| 源码与运行资源（`app/`、`runtime/`、`shell/`、`design/`） | 入库 | 构建所需 |
| 构建与协作（`gradle/`、`scripts/`、`docs/`、`*.gradle.kts`、`AGENTS.md`） | 入库 | 构建、门禁、文档 |
| `docs/review/10-30/`（报告 + `findings.tsv`） | 入库 | 评审 backlog 是对账对象，`status` 列机器可读 |
| `assets/` 的文本记录 | 入库 | 154 个文件 / 373 KiB，见 §1.1 |
| `assets/**/*.png`、`*.jpg`、`*.webp` | 不入库 | 325 个文件 / 19.46 MiB 光栅母版与派生图 |
| `rs.html` | 不入库 | 4.54 MiB 的 Android `R.styleable` 文档快照，一次性参考，可重新导出 |
| `artifacts/validation/` | 不入库 | 240 MB 真机证据；结论进 `docs/evidence/`，原始大图留本地 |
| `local.properties`、`keystore.properties`、`.notes/`、`build/`、`.gradle/`、`.kotlin/` | 不入库 | 本机配置、机密、产物 |

### 1.1 `assets/` 入库清单（以 `git ls-files --others --exclude-standard assets` 为准）

| 类别 | 数量 | 作用 |
|---|---|---|
| 文档与来源记录（`*.md`） | 11 | 8 份 README/PROVIDERS/官方规范模板 + `PROMPT.md` + 2 份 NOTICE |
| 结构化清单（`*.json`） | 9 | manifest×4、图标 slug 映射、尺寸布局、prompts、verification、上游配色 |
| 矢量母版（`*.svg`） | 124 | 42 provider × 明暗 84、lobehub 上游 30、radius 2、vendor 7、branding 1 |
| 许可与校验文本（`*.txt`） | 4 | LobeHub MIT、Tabler MIT、`SHA256SUMS.txt`、上游 provider 清单 |
| 派生脚本（`*.js` / `*.sh` / `*.py`） | 4 | `build.py`、`build_appstore_master.sh`、`derive_sizes.js`、`render_svg.js`（SVG→PNG） |
| 其他（`preview.html`、`providers.csv`） | 2 | 预览页与 provider 元数据表 |

合计 154 个文本文件 / 372.9 KiB（381,842 B）。`sha256sum` 可复核：`assets/pi-provider-logos/SHA256SUMS.txt` 覆盖该目录 253 张 PNG、116 个 SVG 及清单类文本（csv/json/md/txt）的校验和。

### 1.2 为什么这样切

- **光栅图可再生**：`svg/` + `scripts/` + `SHA256SUMS.txt` 足以重建或校验 PNG，母版留在本地不增加 clone 体积、不制造 diff 噪音。
- **文本能审计**：来源（README/prompts）、清单（manifest）、许可（licenses）、校验（SHA256SUMS）都是小而可 diff 的记录，缺了它们光栅图就变成无法追溯的黑盒。
- **单文件 >4 MB 一律先找 Lead 批准**（见 `AGENTS.md` 硬边界），不要用 `git add -f` 绕过忽略规则。

## 2. 复算体积

```bash
# 光栅图（应被忽略的部分）
find assets -type f \( -name '*.png' -o -name '*.jpg' -o -name '*.webp' \) \
  -printf '%s\n' | awk '{s+=$1} END{printf "%d files, %.2f MiB\n", NR, s/1048576}'

# assets 文本（应入库的部分）
find assets -type f ! -name '*.png' ! -name '*.jpg' ! -name '*.webp' \
  -printf '%s\n' | awk '{s+=$1} END{printf "%d files, %.1f KiB\n", NR, s/1024}'

# 工作区磁盘占用（du 按块计，比字节数略大）
du -sh assets rs.html .git

# 已跟踪内容体积（HEAD 快照，不含 .git 开销）
git ls-tree -r -l HEAD | awk '{s+=$4} END{printf "%d files, %.2f MiB\n", NR, s/1048576}'

# 对象库
git count-objects -vH
```

2026-10-30 入库前实测：`assets` 20,785,401 B（`du -sh` 22M）、`rs.html` 4,756,099 B、`.git` 52M（`count: 5462`、`size: 50.31 MiB`、`packs: 0`）、HEAD 快照 645 文件 / 12.53 MiB。

## 3. 忽略规则自证

```bash
# 应命中：.gitignore 的行号 + 实际匹配的规则
git check-ignore -v assets/mascot/generated/mascot_avatar.png rs.html
# .gitignore:17:assets/**/*.png	assets/mascot/generated/mascot_avatar.png
# .gitignore:20:rs.html	rs.html

# 应无输出：docs/review 要入库，不能被任何规则命中
git check-ignore -v docs/review/10-30/findings.tsv

# 应无输出：工作区里不再把光栅图当待提交文件
git status --short -uall | grep -E 'assets/.+\.(png|jpg|webp)$'

# 清点将被入库的 assets 文本文件（应为 154）
git ls-files --others --exclude-standard assets | wc -l
```

`git add assets` 会自动跳过被忽略的光栅图；不要加 `-f`。

## 4. `git gc` 前后对比

由 Lead 在本次提交完成后执行（本任务不执行）。判定看 `git count-objects -vH` 的 `size`／`size-pack`；`du -sh .git` 受 reflog 与块大小影响，仅作参考。

```bash
# 1) 执行前
git count-objects -vH | tee /tmp/git-size-before.txt
du -sh .git | tee -a /tmp/git-size-before.txt

# 2) 执行（提交完成后，期间不要并发 git 操作）
git gc --prune=now

# 3) 执行后并对比
git count-objects -vH | tee /tmp/git-size-after.txt
du -sh .git | tee -a /tmp/git-size-after.txt
diff -u /tmp/git-size-before.txt /tmp/git-size-after.txt
```

预期：`count` 归零、`size-pack` 出现且等于历史对象总大小；`.git` 目录因打包变紧凑。**历史里已有的大二进制（rootfs、字体、APK）不会因 gc 变小**，真要减体积得靠后续外链/LFS 决策，不在 M1-6 范围。

## 5. 换行、二进制与作者身份

`.gitattributes`：

| 规则 | 作用 |
|---|---|
| `* text=auto eol=lf` | 文本仓库内与检出均统一 LF |
| `gradlew text eol=lf` / `gradlew.bat text eol=crlf` | 两个 wrapper 脚本各自可执行 |
| `*.jar *.png *.jpg *.webp *.ttf *.so *.rootfs *.apk binary` | 不转换、不当作文本 diff |

`.mailmap`：历史作者统一显示为 `whywood <3066584511@qq.com>`——107 条 `whywwood`（双 w 笔误）+ 7 条 `whywood <sadarewe3066@gmail.com>`（作者另一邮箱，同一人）。不引入新身份，只影响显示，不改写历史。复核：

```bash
git log --format='%aN <%aE>' | sort | uniq -c   # 应只输出 whywood <3066584511@qq.com>
```
