# Contributing to DeepDeep

Thanks for your interest in DeepDeep. This repository contains the Android app, agent runtime, shell bridge, design system, and plugin API. Official plugins live in [DeepDeep-Official-Plugins](https://github.com/Sadami3066/DeepDeep-Official-Plugins).

## Before opening an issue

- Search existing issues first.
- For bugs, include the app version, Android version and device, steps to reproduce, and relevant logs or screenshots.
- For security issues, contact the author privately instead of opening a public issue.

## Pull requests

- Keep each change focused. Split unrelated work into separate commits.
- Use `type: summary` for commit subjects, for example `fix: handle empty provider list`.
- Run `./scripts/check.sh` before submitting. For plugin API changes, also run `./scripts/check.sh --full`. For device behavior, run `./scripts/check.sh --device` when possible.
- Add or update tests and documentation with the change.
- Do not commit API keys, signing keys, tokens, session files, recordings, or personal data.
- Plugin changes belong in the official plugins repository unless they change the host contract.

## Development setup

Requirements: JDK 17 or newer, Android SDK Platform 36, and build-tools 36.0.0.

```bash
./scripts/dev-setup.sh        # first run
./scripts/check.sh            # compile, tests, lint, module boundaries, APK
./scripts/check.sh --full     # add plugin SDK ABI baseline
./scripts/check.sh --device   # install over a connected device and launch
```

All Gradle invocations must go through `./scripts/gradlew-locked`; concurrent local builds may run out of memory. See the [Developer Guide](docs/DEVELOPER_GUIDE.md) for module layout and conventions.

## License

By contributing, you agree that your contribution is licensed under the repository's [Apache-2.0](LICENSE) license.
