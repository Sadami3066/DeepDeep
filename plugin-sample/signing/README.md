# signing · plugin-sample 测试签名密钥

这里的 `dev-ed25519.pem` / `dev-ed25519.pub` 是**提交进仓库的 Ed25519 测试密钥**，只用于给 `plugin-sample` 产出的 `.ddp` 签名，供宿主加载/验签的真机与单测使用。

- 不要用它签任何对外分发的插件；它不是秘密，任何人都能用它伪造 `plugin-sample` 的更新。
- 正式插件请在插件工程里生成自己的密钥（`plugin-template` 的 `signPlugin` 首次运行会自动生成，私钥不进版本库）。
- `keyId` 由公钥派生：`ed25519:<sha256(SPKI DER 的十六进制)>`，当前测试密钥对应：
  `ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213`
- 校验命令见 `plugin-sample/AGENTS.md` 与 `docs/PLUGIN_DEV.md` 的签名章节。
