# 整板生成提示词

工具：内置 image_gen；transparent_background=true；一次请求生成整板。

参考输入依次为 `references/style-reference.png`、`references/mascot-reference.png`。用户明确要求整板生成后脚本分割，因此按单张 4×4 atlas 生成。

```text
Use case: stylized-concept
Asset type: ONE production-oriented raster UI sprite atlas, intentionally designed as one sheet that a script will cut into 16 separate PNGs.
Input images: Image 1 is the user's master style reference for a simple dopey whale-maid character. Ignore its screenshot background, cyan vertical line, right-side UI fragment and cropping. Image 2 is a previous matching mascot bust, for character identity only.
Primary request: draw a cohesive, self-contained cute whale-themed Android UI art kit on ONE transparent square canvas. Flat muted periwinkle/navy/sky-blue/white colors, thick smooth dark-indigo contours, large simple shapes, restrained cel shading, small pink blush. The mascot has chunky navy-blue hair, fin ears, blue side bow, white maid headband, curved ahoge, cream round face, half-lidded blue eyes and very small body. Make it graphic, clean, readable, and much simpler than detailed anime art.
CRITICAL CUTTING LAYOUT: exactly FOUR COLUMNS and FOUR ROWS of invisible equal-sized square cells, 16 cells total, row-major order. Prefer a 2048 by 2048 square atlas, each cell 512 by 512. All cell centers are at 12.5%, 37.5%, 62.5% and 87.5% of canvas on both axes. Each cell contains ONLY its one specified element, centered, entirely within the INNER 70 percent of its cell. Leave genuinely EMPTY TRANSPARENT GUTTERS between cells and a transparent outer margin. No part crosses a quarter-width or quarter-height line. Keep layout rigid so a uniform 4x4 crop works. This is an asset atlas, NOT a screenshot or an app layout.
Row 1, left to right, 4 matching head-and-tiny-shoulder stickers, same scale and hair:
1: idle mascot with half-lidded eyes and tiny dot mouth, hands near collar.
2: welcoming mascot, tiny waving hand, one eye winking, little happy mouth.
3: thinking mascot, small hand under chin, half-lidded eyes looking up.
4: sleepy mascot, closed curved eyelids, resting cheek on little hands.
Row 2, left to right, 4 standalone matching cute functional pictograms:
5: a simple rounded friendly blue whale with small flipper and pale belly.
6: a navy-outlined white chat bubble with three BLUE DOTS, no writing.
7: a rounded blue microphone with a simple navy stand, no writing.
8: a rounded navy-and-pale-blue settings gear with a circular empty center.
Row 3, left to right, 4 BLANK UI component faces, each one horizontal, width about 65% of the cell and height about 24% of the cell:
9: primary rounded pill button, navy/periwinkle fill, tiny white whale motif at its left, wide blank middle.
10: secondary rounded pill button, white fill and navy outline, tiny light-blue tail accent integrated into right edge, wide blank middle.
11: selected rounded tab/chip, pale-blue fill and firm navy border, one small blue circular status dot at left, wide blank middle.
12: inactive rounded tab/chip, very pale-blue fill and subtle blue border, wide blank middle, no status dot.
Row 4, left to right:
13: blank rounded rectangular card panel, white fill, navy outline and a tiny integrated blue fin corner ornament. Width 68% and height 52% of its cell, empty center. No text-like lines.
14: blank assistant chat bubble, white/pale-blue fill, navy outline and small lower-left tail. Width 68% and height 44% of its cell, entirely empty center.
15: decorative horizontal divider composed of two smooth blue wave lines with one tiny whale motif in the middle, the ENTIRE divider fits in this one cell.
16: one small sky-blue ribbon bow with a pale gem center and navy outline.
Background: TRUE transparent RGBA over the entire atlas outside the illustrated shapes; opaque white only INSIDE the specified character and UI elements. Do not draw a checkerboard or white artboard.
Avoid: any text, labels, numerals, captions, font samples, mockup screens, grid lines, cell borders, watermark, logos, drop shadows, glossy 3D rendering, extra decorations, extra cells, missing cells, repeated extra versions. All 16 elements must remain isolated and fully cuttable.
```
