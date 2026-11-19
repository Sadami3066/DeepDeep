# Brand assets

## App icon

The adaptive launcher icon lives in `app/src/main/res/`:

| File | Purpose |
|---|---|
| `mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon definition: background color, foreground, monochrome layer |
| `drawable-nodpi/ic_launcher_foreground.png` | PI robot mark extracted from `assets/branding/app-icon.png`, drawn inside the adaptive-icon safe zone |
| `drawable-nodpi/ic_launcher_monochrome.png` | Monochrome layer for themed icons on Android 13+ |
| `drawable-nodpi/ic_notification.png` | White silhouette used for notification small icons |
| `values/colors.xml`, `values-night/colors.xml` | `pi_icon_background` (`#FFFFFF`) and window paper color |

The launcher background is white in both light and dark launchers. The foreground and monochrome layers share the same robot geometry; replace both together when the mark changes.

## Mascot

`drawable-nodpi/mascot_wave.png` is the waving robot used by the drawer brand mark, the welcome screen and the README. `drawable-nodpi/v2_*.webp` are the floating-window expressions: `v2_wave` idle, `v2_thinking` running, `v2_waiting` sleepy, `v2_confused` waiting for an answer, `v2_done` completed and `v2_alert` failed.

The black robot is always drawn on a white circular tile in both light and dark themes so the silhouette stays legible. `drawable/process_activity.webp` is the animated activity icon used in the process area.

## Wordmark

The in-app wordmark is rendered in Compose by `DdWordmark` in the `design` module as `PI.`; it uses the current theme's ink and accent colors. The drawer brand mark renders `Super PI.` and places `drawable-nodpi/v2_wave` in a white circle before it.

## Fonts

| Font | File | License |
|---|---|---|
| Fredoka (default) | `design/src/main/res/font/fredoka_variable.ttf` | SIL Open Font License 1.1 |
| Open Runde | `design/src/main/res/font/open_runde_regular.otf`, `open_runde_medium.otf`, `open_runde_semibold.otf` | SIL Open Font License 1.1 |
| Lora | `design/src/main/res/font/lora_variable.ttf` | SIL Open Font License 1.1 |
| Manrope | `design/src/main/res/font/manrope_variable.ttf` | SIL Open Font License 1.1 |

Font licenses are packaged from `design/src/main/assets/licenses/`. Chinese text uses the system font; no CJK font is bundled.

License texts live in `design/src/main/assets/licenses/` (`fredoka-OFL.txt`, `open-runde-OFL.txt`, `lora-OFL.txt`, `manrope-OFL.txt`) and are packaged in the APK.

## Icons

- UI icons come from the [Milo Modern icon set](../milo-modern-icons-200-revised/README.md) (regular weight), with the catalog in `design/icons/`.
- A few glyphs without a Milo equivalent are hand-drawn vectors based on [Tabler Icons](https://github.com/tabler/tabler-icons) v3.50.0 (MIT); the license text is packaged from `design/src/main/assets/licenses/tabler-MIT.txt`.
- Provider and vendor logos live in `assets/pi-provider-logos/` and `assets/vendor-logos/`; see their README files for sources and licenses.
