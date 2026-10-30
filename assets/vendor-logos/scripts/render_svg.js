#!/usr/bin/env node
// Render an SVG to a transparent PNG at a given pixel size using sharp (libvips/librsvg).
// Usage: NODE_PATH=<dir with sharp> node render_svg.js <in.svg> <out.png> <size> [density]
const sharp = require('sharp');
const fs = require('fs');
const [inp, outp, sizeArg, densityArg] = process.argv.slice(2);
if (!inp || !outp || !sizeArg) {
  console.error('usage: node render_svg.js <in.svg> <out.png> <size> [density]');
  process.exit(2);
}
const size = parseInt(sizeArg, 10);
const density = parseInt(densityArg || '2400', 10);
sharp(fs.readFileSync(inp), { density })
  .resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
  .png({ compressionLevel: 9 })
  .toFile(outp)
  .then(() => console.log('rendered', outp))
  .catch((e) => { console.error('error', e.message); process.exit(1); });
