# 扫一扫策略编写指南

这个文件夹里有两份文件：

- `strategies.json`：官方内置策略的副本（只读，改它不会生效）。
- `cookbook.md`：这份指南。

你在 App 里用「扫一扫 → 自定义策略」或直接编辑 `files/scan/strategies.json` 里的 `custom` 列表时，按下面的规则写。

## 文件结构

```json
{
  "version": 2,
  "apps": [
    {
      "package": "com.example.app",
      "hosts": ["example.com"],
      "schemes": ["example"],
      "contains": [],
      "regex": [],
      "priority": 0,
      "fallbackMain": true,
      "scan": { "uri": "example://scan" },
      "note": "走 App 的扫码 scheme"
    }
  ]
}
```

一条策略由一个匹配部分和一个启动部分组成，字段都可省略（但 `package` 和 `scan` 必须至少能解析出来）。

## 匹配：命中越具体，优先级越高

判定顺序（分高者胜）：

1. `schemes` 命中 URL scheme：100 分。最稳，优先用这个。
2. `hosts` 命中域名（支持子域，如 `weixin.qq.com` 命中 `mp.weixin.qq.com`）：90 + 域名长度。二维码内容是链接、但不是该 App 专属 scheme 时用。
3. `regex` 正则命中整段文本：80 分。只在前两者覆盖不到时用；写法要保守，避免 `.*` 之类的宽泛规则。
4. `contains` 子串命中：70 分。最弱，适合「纯文本 token」场景（如飞书会议码）。

同分时看 `priority`（越大越先）；只有已安装的 App 才会参与匹配。

> 拿不准时：先写 `schemes` / `hosts`，再考虑 `contains`。

## 启动：三种写法任选一种

优先用 App 自己扫码入口的 scheme：

```json
"scan": { "uri": "imeituan://www.meituan.com/page/scanQRCodeForMBarResult?openAR=0&shortcuts=true" }
```

需要指定 Activity / action / extras 时用组件写法：

```json
"scan": {
  "activity": "com.tencent.mm.ui.LauncherUI",
  "action": "com.tencent.mm.ui.ShortCutDispatchAction",
  "extras": { "LauncherUI.Shortcut.LaunchType": "launch_type_scan_qrcode" },
  "flags": ["CLEAR_TOP"]
}
```

extras 支持字符串、布尔、整数三种值；`flags` 只写名称（如 `CLEAR_TOP`、`NEW_TASK`）。

没有扫码入口、但整段内容就是 App 的普通链接时，不写策略也可以：非 http 的纯文本会被复制，http(s) 链接会直接打开浏览器。`fallbackMain: true`（默认）表示找不到扫码入口时仍尝试打开 App 主界面。

## 写好一条策略的检查单

1. 从二维码实际内容取一个真实样例，确认它命中 `schemes` 或 `hosts`，而不是靠 `contains` 猜。
2. 安装目标 App，在「扫一扫 → 试跑」里验证：应灰显「未安装」或显示命中的 App 与启动方式。
3. 同一 App 有多条策略时，把最具体的放 `priority` 更高；不要写会互相吞掉的宽泛规则。
4. `note` 写清依据（来源 URL、scheme 名、测试日期），方便以后维护。
5. 官方策略只读；要改先在自定义列表里覆盖，不要在 `strategies.json` 的官方部分改。
6. 改完在「扫一扫」里点「保存」，再用「导出」确认 JSON 结构与本指南一致。

## 常见坑

- 只写 `contains` 会把不相干文本误判给某个 App；能拿到 scheme 就不要用它。
- `hosts` 不要写 `com`、`cn` 这类公共后缀。
- URL 里带 `%` 编码时，`schemes` 仍按原始 scheme 匹配，不用自己解码。
- 组件写法的 `activity` 必须是完整类名；写错会静默回退到主界面。
- 正则使用 Kotlin `Regex` 语法，默认忽略大小写；非法正则会整条被跳过。
