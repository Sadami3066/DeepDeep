# Legacy recovery record

- Source bundle: `~/.local/state/deepdeep/recovery/20261021-212541/repository.bundle`
- sha256: `26c7c39b077ddc6ca444904108269c43e34efe420c834d5e1d5dbd656aad39bd`
- Restored to: `~/project/deepdeep-legacy`
- HEAD: `9abcf562d8da15c3d72b0ac0a7e0a2de102aeb8d`
- Restore date: 2026-10-22

## Status

The current repository no longer depends on the legacy repository. Design, shell, chat, settings, provider directory and session actions are now owned by this repository; UI and runtime contracts have been rewritten around pi2kotlin and no longer reference the old Koog/custom-tool stack.

The legacy checkout is kept read-only for provenance only and can be deleted after acceptance.
