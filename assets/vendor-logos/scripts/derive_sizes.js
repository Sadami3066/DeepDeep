#!/usr/bin/env node
// Derive 256/192 px PNGs (palette-compressed) from a 512 px master.
// usage: node derive_sizes.js <master-512.png> <dir256> <dir192>
const sharp = require('sharp');
const path = require('path');
const [src, dir256, dir192] = process.argv.slice(2);
if (!src || !dir256 || !dir192) { console.error('usage: node derive_sizes.js <master> <dir256> <dir192>'); process.exit(2); }
const name = path.basename(src).replace(/-512\.png$/, '.png');
(async () => {
  for (const [size, dir] of [[256, dir256], [192, dir192]]) {
    const out = `${dir}/${name}`;
    await sharp(src)
      .resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
      .png({ compressionLevel: 9, palette: true, quality: 92, effort: 9 })
      .toFile(out);
    console.log('derived', out);
  }
})();
