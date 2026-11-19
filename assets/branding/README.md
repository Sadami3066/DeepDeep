# Brand assets

## App icon

The adaptive launcher icon lives in `app/src/main/res/`:

| File | Purpose |
|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon definition: background color, foreground, monochrome layer |
| `drawable-nodpi/ic_launcher_foreground.png` | PI robot mark, drawn inside the adaptive-icon safe zone |
| `drawable-nodpi/ic_launcher_monochrome.png` | Monochrome layer for themed icons on Android 13+ |
| `drawable-nodpi/ic_notification.png` | White silhouette used for notification small icons |
| `values/colors.xml`, `values-night/colors.xml` | `pi_icon_background` (`#FFFFFF`) and window paper color |

The launcher background is white in both light and dark launchers. The foreground and monochrome layers share the same robot geometry; replace both together when the mark changes.

## Mascot

`drawable-nodpi/pi_mascot.webp` is the robot mascot used on the welcome screen and the empty conversation state. It is drawn on a white circular tile in both themes so the black silhouette stays legible.

## Wordmark

The in-app wordmark is rendered in Compose by `DdWordmark` in the `design` module as `PI.`; it uses the current theme's ink and accent colors. There is no separate wordmark image in the app.

## Fonts

| Font | File | License |
|---|---|---|
| Lora | `design/src/main/res/font/lora_variable.ttf` | SIL Open Font License 1.1 |
| Manrope | `design/src/main/res/font/manrope_variable.ttf` | SIL Open Font License 1.1 |

Upstream: <https://github.com/google/fonts/tree/main/ofl/lora>, <https://github.com/google/fonts/tree/main/ofl/manrope>. Lora and Manrope are byte-identical to upstream and unmodified. Chinese text uses the system font; no CJK font is bundled.

License texts: `design/src/main/assets/licenses/lora-OFL.txt` and `manrope-OFL.txt`, packaged in the APK.

## Icons

- UI icons come from the [Milo Modern icon set](../milo-modern-icons-200-revised/README.md) (regular weight), with the catalog in `design/icons/`.
- A few glyphs without a Milo equivalent are hand-drawn vectors based on [Tabler Icons](https://github.com/tabler/tabler-icons) v3.50.0 (MIT); the license text is packaged from `design/src/main/assets/licenses/tabler-MIT.txt`.
- Provider and vendor logos live in `assets/pi-provider-logos/` and `assets/vendor-logos/`; see their README files for sources and licenses.
