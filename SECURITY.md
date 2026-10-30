# Security Policy

## 报告漏洞

请不要开公开 issue。用 GitHub 的私密漏洞报告：仓库 **Security → Advisories → Report a vulnerability**（`https://github.com/Sadami3066/DeepDeep/security/advisories/new`）。

报告请包含：影响版本（tag 或 commit）、复现步骤、影响面（是否可读写用户数据 / 是否可执行命令）、以及你愿意公开的时间点。

## 适用范围

- **本地 App 与仓库代码**：本仓库所有 Kotlin/资源/构建脚本。
- **不在范围**：`pi2kotlin` SDK 自身的问题（去 <https://github.com/Sadami3066/pi2kotlin> 报告）；用户自装的第三方 CLI/插件上游漏洞。

## 已知设计边界（不是漏洞）

- Linux 环境是 **proot 用户空间，不是安全沙箱**；在 App 内执行 `bash` 属于预期能力（ADR-0002）。
- 不做工具级权限审批；只有 Linux/root 一个总开关（ADR-0002）。
- 创意工坊页面运行在独立 origin 的 WebView 中，只能经 bridge 读写自己的数据；能绕过该边界才算漏洞（ADR-0003）。
- 本地分发使用 debug 证书签名是已记录的取舍（ADR-0001），不视为漏洞。

## 处置

- 确认后 7 天内给初步回复；修复版本会在 Release Notes 里标注，并对报告者致谢（除非要求匿名）。
- 严重问题优先修，必要时先发临时缓解（关闭相关插件/能力），再发正式版本。
