# plugin-fixtures · device binary regression fixtures

`scripts/plugin-binary-compat.sh` pushes these `.ddp` files to the app's external directory on the device, and `PluginBinaryCompatTest` runs the install → enable → disable → upgrade → uninstall regression on device. Run it by hand once before each host release; with every later release, append the previous version's market package here.

The first version comes from the `sample` module of [SuperPI-Plugins](https://github.com/Sadami3066/SuperPI-Plugins) (`a57e941`, API level 6), signed with the development signing key committed there at `sample/signing/dev-ed25519.pem`; the keyId is `ed25519:8bfc1ba034dbf5bc113d469ce1f1a6350bc4c104105aa92b104eda3896fad213`.

| File | Plugin id | Version | Purpose | Size (B) | sha256 |
|---|---|---|---|---|---|
| `sample-1.0.0.ddp` | `io.github.whywood.superpi.pluginsample` | 1.0.0 | First-install baseline | 816538 | `2bfe17e2ac3d9495bc0dd79b1c2d542058c5dd92a4beeb2b09ade70b06bea051` |
| `sample-1.0.1.ddp` | `io.github.whywood.superpi.pluginsample` | 1.0.1 | Upgrade target (same dex, only the manifest version differs) | 816542 | `e9b68a80e983d293fa3a5a0d63ed7c3d44d40ea462d713293266a1c049d4c5eb` |

Both fixtures are `apiLevel=6`, `minHostVersion=1.0.4`, `ui.apiLevel=2`, and provide the `sample_echo` tool and the `sample.main` page.

Regenerate (v1 builds inside the plugin repository; v2 is copied to a temporary directory, version-bumped and re-signed, both with the same development key):

```bash
cd ../SuperPI-Plugins/sample && ./gradlew verifyPlugin
cp build/plugin/sample.ddp <repo>/app/src/androidTest/assets/plugin-fixtures/sample-1.0.0.ddp
cp -a . /tmp/plugin-fixtures-build/sample-v2 && cd /tmp/plugin-fixtures-build/sample-v2
rm -rf build .gradle
# bump version in src/main/assets/plugin.json and versionName/versionCode in build.gradle.kts
./gradlew verifyPlugin
cp build/plugin/sample.ddp <repo>/app/src/androidTest/assets/plugin-fixtures/sample-1.0.1.ddp
```
