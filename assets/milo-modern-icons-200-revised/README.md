# Milo Modern Icons — 200 revised

Keeps the original 200 glyphs, states and English file names, and reworks the transparent padding, stroke weight and color.

## Pick one weight

- `regular/`: recommended for everyday UI, 200 transparent PNGs. Target stroke width is about 1.75dp in a 24dp container.
- `semibold/`: a more prominent version, 200 transparent PNGs. Target stroke width is about 2.15dp in a 24dp container.
- The two directories are the same icon set at different weights: 200 glyphs, 400 PNGs in total. Keep one weight within a given area.
- `comparison.png`: old, standard and bold versions in the same 24dp container, rendered at 3x.
- `preview_regular.png`, `preview_semibold.png`: previews of every icon.
- `icon_names.csv`, `icon_names.json`: Chinese and English names with file names.
- `state_families.json`: state categories.

## What changed

Every image is a 256×256 RGBA transparent PNG. Outlines are thickened evenly from the original, corners stay round, anti-aliased edges are cleaned, and the glyph takes up more of the canvas. The longest edge of a glyph covers about 85% of the canvas, and the color is #273140. Existing shape details are preserved; local stroke width varies where lines meet.

## Android / Compose

Pick one set of PNGs from regular or semibold, put them in `res/drawable-nodpi/` and keep the original `ic_milo_*.png` file names. The new versions trim transparent padding, so the 24dp icon container works as is; use 26dp if you need more presence. The outer touch target can stay 48dp.

Display icons at full opacity: #273140 or #374151 for regular icons, the app's primary blue for selected items. Check whether a parent applies lower alpha; if the component uses a tint or ColorFilter, that value decides the final color. Pick one color entry point and stick to it.

The PNGs are bitmap assets that adapt to dark mode through a single-color tint. The old standalone archive is kept for rollback.
