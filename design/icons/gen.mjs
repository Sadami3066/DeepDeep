#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const appRoot = resolve(scriptDir, '../..');
const resDir = join(appRoot, 'design/src/main/res/drawable-nodpi');
const kotlinDir = join(appRoot, 'design/src/main/kotlin/io/github/whywood/deepdeep/design');
const ddIconPath = join(kotlinDir, 'DdIcon.kt');
const ddIconsPath = join(kotlinDir, 'DdIcons.kt');
const catalogPath = join(scriptDir, 'catalog.json');
const previewPath = join(scriptDir, 'preview.html');
const checkMode = process.argv.includes('--check');

const errors = [];
const fail = (message) => errors.push(message);

const ID_RE = /^[a-z][a-z0-9_]*$/;
const VAL_RE = /^[A-Z][A-Za-z0-9]*$/;
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

const escapeHtml = (value) => String(value)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;');

const sourceExt = (icon) => (icon.upstream === null ? 'xml' : 'png');
const sourcePath = (icon) => join(scriptDir, icon.category, `${icon.id}.${sourceExt(icon)}`);
const resourceName = (icon) => `ic_${icon.category}_${icon.id}`;

function readCatalog() {
  return JSON.parse(readFileSync(catalogPath, 'utf8'));
}

function validateCatalog(catalog) {
  if (!Array.isArray(catalog.categories) || catalog.categories.length === 0) fail('catalog.categories 为空');
  if (!Array.isArray(catalog.icons) || catalog.icons.length === 0) fail('catalog.icons 为空');
  if (errors.length) return;
  const categoryIds = new Set();
  for (const category of catalog.categories) {
    if (typeof category.id !== 'string' || !ID_RE.test(category.id)) fail(`分类 id 不合法：${category.id}`);
    else if (categoryIds.has(category.id)) fail(`分类 id 重复：${category.id}`);
    categoryIds.add(category.id);
    if (typeof category.name_zh !== 'string' || category.name_zh.length === 0) fail(`分类 ${category.id} 缺少中文名`);
    if (typeof category.description !== 'string' || category.description.length === 0) fail(`分类 ${category.id} 缺少描述`);
  }
  const iconIds = new Set();
  const valNames = new Set();
  for (const icon of catalog.icons) {
    if (typeof icon.id !== 'string' || !ID_RE.test(icon.id)) fail(`图标 id 不合法：${icon.id}`);
    else if (iconIds.has(icon.id)) fail(`图标 id 重复：${icon.id}`);
    iconIds.add(icon.id);
    if (!categoryIds.has(icon.category)) fail(`图标 ${icon.id} 的分类不存在：${icon.category}`);
    if (typeof icon.name_zh !== 'string' || icon.name_zh.length === 0) fail(`图标 ${icon.id} 缺少中文名`);
    if (!Array.isArray(icon.dd) || icon.dd.length === 0) fail(`图标 ${icon.id} 缺少 DdIcons 映射`);
    else {
      for (const name of icon.dd) {
        if (typeof name !== 'string' || !VAL_RE.test(name)) fail(`图标 ${icon.id} 的映射名不合法：${name}`);
        else if (valNames.has(name)) fail(`DdIcons 成员重复：${name}`);
        valNames.add(name);
      }
    }
    if (icon.upstream !== null && (typeof icon.upstream !== 'string' || icon.upstream.length === 0)) fail(`图标 ${icon.id} 的 upstream 不合法`);
    const path = sourcePath(icon);
    if (!existsSync(path)) fail(`缺少源文件：${relative(appRoot, path)}`);
  }
  for (const category of catalog.categories) {
    const categoryDir = join(scriptDir, category.id);
    if (!existsSync(categoryDir) || !statSync(categoryDir).isDirectory()) {
      fail(`缺少分类目录：${category.id}`);
      continue;
    }
    const expected = new Set(catalog.icons.filter((icon) => icon.category === category.id).map((icon) => `${icon.id}.${sourceExt(icon)}`));
    for (const name of readdirSync(categoryDir)) {
      if (!expected.has(name)) fail(`分类目录 ${category.id} 存在未登记文件：${name}`);
    }
  }
  if (!existsSync(ddIconPath)) fail('缺少 DdIcon.kt');
  else {
    const source = readFileSync(ddIconPath, 'utf8');
    if (!source.includes('class DdIcon(') || !source.includes('fun DdIcon(')) fail('DdIcon.kt 缺少 DdIcon 类型或 composable');
  }
}

function buildResources(catalog) {
  const resources = [];
  for (const icon of catalog.icons) {
    const path = sourcePath(icon);
    if (!existsSync(path)) continue;
    const bytes = readFileSync(path);
    if (sourceExt(icon) === 'png') {
      if (bytes.length < 26 || !bytes.subarray(0, 8).equals(PNG_SIGNATURE)) {
        fail(`源图不是 PNG：${relative(appRoot, path)}`);
        continue;
      }
      const width = bytes.readUInt32BE(16);
      const height = bytes.readUInt32BE(20);
      const bitDepth = bytes[24];
      const colorType = bytes[25];
      if (width !== 256 || height !== 256 || bitDepth !== 8 || colorType !== 6) {
        fail(`源图规格不是 256×256 RGBA：${relative(appRoot, path)}`);
      }
    } else {
      const xml = bytes.toString('utf8');
      if (!xml.includes('<vector') || !xml.includes('android:viewportWidth="24"') || !xml.includes('android:viewportHeight="24"')) {
        fail(`矢量源不是 24 viewport：${relative(appRoot, path)}`);
      }
    }
    resources.push({ icon, name: resourceName(icon), ext: sourceExt(icon), bytes });
  }
  return resources;
}

function buildKotlin(catalog) {
  const members = [];
  for (const icon of catalog.icons) {
    for (const name of icon.dd) members.push([name, resourceName(icon)]);
  }
  members.sort((left, right) => (left[0] < right[0] ? -1 : left[0] > right[0] ? 1 : 0));
  const lines = members.map(([name, resource]) => `    val ${name} = DdIcon(R.drawable.${resource})`);
  return [`package io.github.whywood.deepdeep.design`, '', 'object DdIcons {', ...lines, '}', ''].join('\n');
}

function vectorPaths(xml) {
  const parts = [];
  for (const match of xml.matchAll(/<path\b[^>]*>/g)) {
    const tag = match[0];
    const attr = (name) => {
      const found = new RegExp(`android:${name}="([^"]*)"`).exec(tag);
      return found ? found[1] : null;
    };
    const pathData = attr('pathData');
    if (!pathData) continue;
    const fillColor = attr('fillColor');
    const strokeColor = attr('strokeColor');
    const attributes = [`d="${pathData}"`];
    if (!fillColor || /^#0{2,8}$/i.test(fillColor) || /transparent/i.test(fillColor)) attributes.push('fill="none"');
    else attributes.push('fill="currentColor"');
    if (strokeColor && !/transparent/i.test(strokeColor)) {
      attributes.push('stroke="currentColor"');
      attributes.push(`stroke-width="${attr('strokeWidth') || '2.1'}"`);
      attributes.push(`stroke-linecap="${attr('strokeLineCap') || 'round'}"`);
      attributes.push(`stroke-linejoin="${attr('strokeLineJoin') || 'round'}"`);
    }
    parts.push(`<path ${attributes.join(' ')}/>`);
  }
  return parts.join('');
}

function renderIcon(icon, bytes) {
  if (sourceExt(icon) === 'png') {
    return `<img class="pic" src="${icon.category}/${icon.id}.png" alt="">`;
  }
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">${vectorPaths(bytes.toString('utf8'))}</svg>`;
}

function buildPreview(catalog, resources) {
  const bytesById = new Map(resources.map((resource) => [resource.icon.id, resource.bytes]));
  const total = catalog.icons.length;
  const raster = catalog.icons.filter((icon) => icon.upstream !== null).length;
  const vector = total - raster;
  const valCount = catalog.icons.reduce((sum, icon) => sum + icon.dd.length, 0);

  const sections = catalog.categories.map((category) => {
    const icons = catalog.icons.filter((icon) => icon.category === category.id);
    const cards = icons.map((icon) => {
      const resource = resourceName(icon);
      const kind = sourceExt(icon) === 'png' ? '位图 · semibold' : '矢量 · 手绘';
      const search = [icon.name_zh, icon.id, icon.category, resource, kind, ...icon.dd, icon.upstream || ''].join(' ').toLowerCase();
      const title = icon.upstream ? `上游：${icon.upstream}` : '手绘 vector 源';
      const vals = icon.dd.map((name) => `<span class="val">${escapeHtml(name)}</span>`).join('');
      return `<article class="card" data-search="${escapeHtml(search)}" title="${escapeHtml(title)}">
  <div class="icon">${renderIcon(icon, bytesById.get(icon.id))}</div>
  <div class="zh">${escapeHtml(icon.name_zh)}</div>
  <div class="id">${escapeHtml(icon.id)}</div>
  <div class="res">${escapeHtml(resource)}</div>
  <div class="kind">${escapeHtml(kind)}</div>
  <div class="badges">${vals}</div>
</article>`;
    }).join('\n');
    return `<section class="category" data-category="${escapeHtml(category.id)}">
  <div class="cat-head"><h2>${escapeHtml(category.name_zh)}</h2><span class="cat-id">${escapeHtml(category.id)}</span><span class="count">${icons.length}</span></div>
  <p class="desc">${escapeHtml(category.description)}</p>
  <div class="grid">${cards}</div>
</section>`;
  }).join('\n');

  const tree = ['design/icons/', '  catalog.json', '  gen.mjs', '  preview.html'];
  for (const category of catalog.categories) {
    const names = catalog.icons.filter((icon) => icon.category === category.id).map((icon) => `${icon.id}.${sourceExt(icon)}`);
    tree.push(`  ${category.id}/`);
    let line = '    ';
    for (const name of names) {
      if (line.length + name.length + 2 > 100) {
        tree.push(line.trimEnd());
        line = '    ';
      }
      line += `${name}  `;
    }
    if (line.trim().length > 0) tree.push(line.trimEnd());
  }

  const brandRows = [
    ['Provider 图标', 'assets/pi-provider-logos/', '42 个厂商（31 份不同图形）', '运行时 `provider_*`，位于 app 的 drawable-nodpi 与 drawable-night-nodpi'],
    ['厂商图标', 'assets/vendor-logos/', '10 个厂商（7 份已入库）', '源素材，当前未随包发布'],
    ['应用标识 / 启动图标', 'assets/branding/', '品牌字母 d', '不走 DdIcon 体系'],
    ['鲸鱼娘素材', 'assets/ui-kit/whale-flat-v1/', 'mascot_* / whale_*', '动画与表情素材，非通用图标'],
    ['上游 Milo 素材包', 'assets/milo-modern-icons-200-revised/', '200 枚 × regular/semibold', '只读参考，本仓库取 semibold 档'],
  ].map(([name, path, scope, status]) => `<tr><td>${escapeHtml(name)}</td><td><a href="../../${escapeHtml(path)}">${escapeHtml(path)}</a></td><td>${escapeHtml(scope)}</td><td>${escapeHtml(status)}</td></tr>`).join('\n');

  const systemRows = [
    ['图标源', 'design/icons/&lt;category&gt;/&lt;id&gt;.png|.xml', '唯一手改入口'],
    ['登记表', 'design/icons/catalog.json', 'id / 分类 / 中文名 / DdIcons 成员 / 上游路径'],
    ['生成入口', 'design/icons/gen.mjs', '写出运行时资源、DdIcons.kt、本预览页'],
    ['运行时资源', 'design/src/main/res/drawable-nodpi/ic_&lt;category&gt;_&lt;id&gt;.png|.xml', '只读，由脚本覆盖'],
    ['预览页', 'design/icons/preview.html', '本页，图标读同目录源文件'],
  ].map(([name, path, note]) => `<tr><td>${escapeHtml(name)}</td><td>${path}</td><td>${escapeHtml(note)}</td></tr>`).join('\n');

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>DeepDeep 图标目录</title>
<style>
:root{color-scheme:light dark;--bg:#f7f6f3;--card:#ffffff;--ink:#273140;--muted:#6b7280;--line:#e3e0da;--chip:#f1efe9;--accent:#3b6ea5}
.dark{color-scheme:dark;--bg:#111316;--card:#191c21;--ink:#e7e9ec;--muted:#9aa1ab;--line:#2a2e35;--chip:#22262c;--accent:#7aa7d8}
*{box-sizing:border-box}
[hidden]{display:none!important}
body{margin:0;padding:40px 32px 72px;background:var(--bg);color:var(--ink);font:14px/1.6 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Noto Sans CJK SC","Microsoft YaHei",sans-serif}
h1{font-size:26px;margin:0 0 8px}
h2{font-size:16px;margin:0 0 6px}
code,pre,.id,.res,.cat-id{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
.page{display:flex;gap:24px;justify-content:space-between;flex-wrap:wrap;align-items:flex-start;max-width:1200px;margin:0 auto 24px}
.lead{max-width:680px;color:var(--muted);margin:0}
.tools{display:flex;gap:8px;flex-wrap:wrap}
input[type=search]{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:8px 12px;color:var(--ink);min-width:260px;font:inherit}
button{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:8px 14px;color:var(--ink);cursor:pointer;font:inherit}
button:hover{border-color:var(--accent)}
main,footer{max-width:1200px;margin:0 auto}
.stats{color:var(--muted);margin:0 0 22px}
.category{margin:0 0 30px}
.cat-head{display:flex;align-items:baseline;gap:10px}
.cat-id{font-size:12px;color:var(--muted)}
.count{font-size:12px;color:var(--muted)}
.desc{color:var(--muted);margin:2px 0 12px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(152px,1fr));gap:12px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px 12px 12px;display:flex;flex-direction:column;align-items:center;text-align:center}
.icon{width:46px;height:46px;display:flex;align-items:center;justify-content:center;color:var(--ink);background:var(--chip);border-radius:12px;padding:9px;margin-bottom:8px}
.icon .pic{width:100%;height:100%;object-fit:contain;display:block}.dark .icon .pic{filter:invert(1) hue-rotate(180deg)}
.icon svg{width:100%;height:100%}
.zh{font-weight:600}
.id{font-size:12px;color:var(--muted);margin-top:1px}
.res{font-size:11px;color:var(--muted);word-break:break-all;margin-top:1px}
.kind{font-size:10px;color:var(--muted);margin-top:5px}
.badges{display:flex;flex-wrap:wrap;gap:3px;justify-content:center;margin-top:6px}
.val{font-size:10px;color:var(--muted);background:var(--chip);border-radius:6px;padding:1px 6px}
footer{margin-top:44px;border-top:1px solid var(--line);padding-top:26px}
footer section{margin-bottom:30px}
pre{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px 16px;overflow:auto;font-size:12px;line-height:1.7;margin:0}
table{border-collapse:collapse;width:100%;font-size:13px}
th,td{border-bottom:1px solid var(--line);padding:8px 10px;text-align:left;vertical-align:top}
th{color:var(--muted);font-weight:500}
a{color:var(--accent)}
ol{margin:8px 0 0;padding-left:20px}
@media (max-width:640px){body{padding:24px 16px}.page{gap:12px}input[type=search]{min-width:0;width:100%}}
</style>
</head>
<body>
<header class="page">
  <div>
    <h1>DeepDeep 图标目录</h1>
    <p class="lead">应用内通用 UI 图标单源：<code>design/icons/</code> 分类源经 <code>design/icons/gen.mjs</code> 生成运行时资源与 <code>DdIcons.kt</code>；本页同为生成产物，图标直接读取同目录源文件。</p>
  </div>
  <div class="tools">
    <input id="q" type="search" placeholder="搜索名称 / id / 资源 / DdIcons" autocomplete="off">
    <button id="theme" type="button">切换明暗</button>
  </div>
</header>
<main>
  <div class="stats" id="stats">${total} 个图标 · ${catalog.categories.length} 类 · ${raster} 位图 · ${vector} 矢量 · ${valCount} 个 DdIcons 成员</div>
${sections}
</main>
<footer>
  <section>
    <h2>目录结构</h2>
    <pre>${escapeHtml(tree.join('\n'))}</pre>
  </section>
  <section>
    <h2>生成命令</h2>
    <p class="lead">在仓库根目录（<code>app/</code>）执行；<code>--check</code> 只校验漂移不写文件，可直接用于 CI。</p>
    <pre>node design/icons/gen.mjs
node design/icons/gen.mjs --check</pre>
  </section>
  <section>
    <h2>分类与命名</h2>
    <table>
      <thead><tr><th>项</th><th>位置 / 值</th><th>说明</th></tr></thead>
      <tbody>
${systemRows}
      </tbody>
    </table>
    <p class="lead">源文件与 id 用 snake_case；生成资源名 = <code>ic_&lt;category&gt;_&lt;id&gt;</code>；<code>DdIcons</code> 成员名保持历史命名，调用点无需改动。</p>
  </section>
  <section>
    <h2>新增或替换图标</h2>
    <ol>
      <li>把 PNG 放进对应分类目录（或手写 vector XML），文件名即 id；</li>
      <li>在 <code>catalog.json</code> 的 <code>icons</code> 中登记 id / 分类 / 中文名 / DdIcons 成员 / 上游路径；</li>
      <li>运行生成命令并编译；未登记或未使用的源文件会让生成失败，避免留下死资源。</li>
    </ol>
  </section>
  <section>
    <h2>品牌与平台素材</h2>
    <table>
      <thead><tr><th>素材</th><th>位置</th><th>范围</th><th>状态</th></tr></thead>
      <tbody>
${brandRows}
      </tbody>
    </table>
    <p class="lead">这些素材各自带 manifest、来源与许可，跨目录被市场与通知使用，因此留在 <code>assets/</code>，不并入通用图标目录。</p>
  </section>
</footer>
<script>
const input = document.getElementById('q');
const stats = document.getElementById('stats');
const cards = [...document.querySelectorAll('.card')];
const sections = [...document.querySelectorAll('.category')];
const root = document.documentElement;
const themeButton = document.getElementById('theme');
const setTheme = (dark) => {
  root.classList.toggle('dark', dark);
  themeButton.textContent = dark ? '浅色' : '深色';
};
setTheme(window.matchMedia('(prefers-color-scheme: dark)').matches);
themeButton.addEventListener('click', () => setTheme(!root.classList.contains('dark')));
const update = () => {
  const query = input.value.trim().toLowerCase();
  let visible = 0;
  for (const card of cards) {
    const show = query.length === 0 || card.dataset.search.includes(query);
    card.hidden = !show;
    if (show) visible += 1;
  }
  for (const section of sections) {
    section.hidden = ![...section.querySelectorAll('.card')].some((card) => !card.hidden);
  }
  const categoryCount = sections.filter((section) => !section.hidden).length;
  stats.textContent = query.length === 0 ? '${total} 个图标 · ${catalog.categories.length} 类 · ${raster} 位图 · ${vector} 矢量 · ${valCount} 个 DdIcons 成员' : \`\${visible} / \${cards.length} 个图标 · \${categoryCount} 类\`;
};
input.addEventListener('input', update);
</script>
</body>
</html>
`;
}

function main() {
  const catalog = readCatalog();
  validateCatalog(catalog);
  if (errors.length) {
    console.error(errors.join('\n'));
    process.exit(1);
  }
  const resources = buildResources(catalog);
  if (errors.length) {
    console.error(errors.join('\n'));
    process.exit(1);
  }
  const kotlin = buildKotlin(catalog);
  const preview = buildPreview(catalog, resources);
  const expected = new Map(resources.map((resource) => [`${resource.name}.${resource.ext}`, resource.bytes]));
  const categoryPattern = catalog.categories.map((category) => category.id).join('|');
  const generatedPattern = new RegExp(`^ic_(?:milo|dd|${categoryPattern})_[a-z0-9_]+\\.(?:png|xml)$`);
  const stale = existsSync(resDir) ? readdirSync(resDir).filter((name) => generatedPattern.test(name) && !expected.has(name)).sort() : [];

  if (checkMode) {
    const mismatches = [];
    for (const [name, bytes] of expected) {
      const target = join(resDir, name);
      if (!existsSync(target)) mismatches.push(`缺少资源：${relative(appRoot, target)}`);
      else if (!readFileSync(target).equals(bytes)) mismatches.push(`资源内容过期：${relative(appRoot, target)}`);
    }
    for (const name of stale) mismatches.push(`过期资源：${relative(appRoot, join(resDir, name))}`);
    for (const [target, content] of [[ddIconsPath, kotlin], [previewPath, preview]]) {
      if (!existsSync(target)) mismatches.push(`缺少生成文件：${relative(appRoot, target)}`);
      else if (readFileSync(target, 'utf8') !== content) mismatches.push(`生成文件过期：${relative(appRoot, target)}`);
    }
    if (mismatches.length) {
      console.error(mismatches.join('\n'));
      process.exit(1);
    }
    console.log(`图标生成结果一致：${catalog.icons.length} 个图标 / ${expected.size} 个资源 / ${catalog.icons.reduce((sum, icon) => sum + icon.dd.length, 0)} 个 DdIcons 成员`);
    return;
  }

  mkdirSync(resDir, { recursive: true });
  for (const name of stale) rmSync(join(resDir, name));
  for (const [name, bytes] of expected) writeFileSync(join(resDir, name), bytes);
  writeFileSync(ddIconsPath, kotlin);
  writeFileSync(previewPath, preview);
  const catalogCount = catalog.categories.length;
  const raster = catalog.icons.filter((icon) => icon.upstream !== null).length;
  console.log(`图标已生成：${catalog.icons.length} 个图标 / ${catalogCount} 类（${raster} 位图 + ${catalog.icons.length - raster} 矢量）/ ${expected.size} 个资源 / ${catalog.icons.reduce((sum, icon) => sum + icon.dd.length, 0)} 个 DdIcons 成员`);
  if (stale.length) console.log(`清理过期资源：${stale.length} 个`);
  console.log(`写出：${relative(appRoot, ddIconsPath)}、${relative(appRoot, previewPath)}`);
}

main();
