# PI provider logos

Built from the `KnownProvider` list in the PI 1.4.0 source as checked on 2026-10-23: 42 provider IDs mapping to 31 distinct brand marks. Regions, plans and auth entries reuse the same brand icon.

## Direct use

- `svg/light/`: SVGs for light backgrounds, named by provider ID.
- `svg/dark/`: SVGs for dark backgrounds, named by provider ID.
- `png/256/`, `png/512/`: 256×256 / 512×512 transparent PNGs, each with `light` and `dark`.
- `android/res/`: PNG resources to copy into an Android project's `app/src/main/res/`. Night mode uses `drawable-night-nodpi/` and is selected automatically.
- `android/provider-icon-map.json`: mapping from provider ID to Android drawable name.
- `manifest.json`: full mapping, source URLs, versions and SHA256.
- `providers.csv`: list that opens in spreadsheet software.
- `preview.png`: light/dark overview of all 42 providers.

Web example: `<img src="svg/light/deepseek.svg" width="24" height="24" alt="DeepSeek">`.

Android Compose example:

```kotlin
Image(
    painter = painterResource(R.drawable.provider_deepseek),
    contentDescription = "DeepSeek",
    modifier = Modifier.size(24.dp)
)
```

Show colored brand icons as they are with `Image`. If you use `Icon`, set `tint = Color.Unspecified` to keep the brand colors. Hyphens in Android file names become underscores, e.g. `openai-codex` → `provider_openai_codex`.

## Icon choices

`google` uses Gemini; `google-vertex` uses Vertex AI; `azure` uses Azure; `openai-codex` reuses OpenAI; `moonshotai` uses Moonshot and `kimi-coding` uses Kimi; `xiaomi` and its plan entries use Xiaomi MiMo. The two Cloudflare entries share the Cloudflare mark, and OpenCode Zen / Go share the OpenCode mark.

SVGs keep the original paths and brand colors; only the size is set to 256×256, and light/dark values are given for a variable foreground color. Every PNG has a transparent background. Radius uses the official mark and color tokens, with padding of a quarter of the logo width on all sides. All other icons keep their original viewBox, so add consistent spacing outside the control when laying out.

## Sources and attribution

30 distinct marks come from LobeHub `lobe-icons` with the MIT license attached; brand marks belong to their respective owners and are used to identify the services. Radius comes from Earendil's official `elanor` icon source under the official third-party brand identification terms, keeping the original mark, colors and padding. The full official guidance template is kept at `upstream/radius/official-guidelines-template.md`.

Source files live in `upstream/`. File names and mappings follow the PI source snapshot recorded in this package.

Raster images (`png/`, `android/res/`, `preview.png`) are excluded by `.gitignore` and stay local; the repository holds the vector and text sources under `svg/` and `upstream/`, `manifest.json`, `SHA256SUMS.txt` and the license texts.
