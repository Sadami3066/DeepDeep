# Legacy recovery record

- Source bundle: `~/.local/state/deepdeep/recovery/20261021-212541/repository.bundle`
- sha256: `26c7c39b077ddc6ca444904108269c43e34efe420c834d5e1d5dbd656aad39bd`
- Restored to: `~/project/deepdeep-legacy`
- HEAD: `9abcf562d8da15c3d72b0ac0a7e0a2de102aeb8d`
- Restore date: 2026-10-22

## Copied into this repository

- `core/ui` → `:design` (theme, fonts, Lucide icons, components)
- `core/linux` → `:shell` (Alpine rootfs, PRoot, installer, executor)
- Selected chat/settings Compose files → `:app`
- `app` theme/icon/strings resources and local stores

## Not copied

- `core/ai` (Koog runtime)
- `core/tools`, `core/platform` (custom Android tools are not part of this app)
- `ConversationFileStore` (sessions now use pi2kotlin JSONL)
- Provider list/detail screens and approval UI (DeepSeek only, no approval system)
- Project/scheduled/plugin placeholders

## New base

The new repository starts from an empty history. The legacy repository stays read-only for reference.
