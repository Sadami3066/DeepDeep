#!/usr/bin/env bash
# App Store 512 artwork -> transparent 512 master.
# usage: build_appstore_master.sh <white-bg|wechat> <in.jpg> <out.png>
set -euo pipefail
mode="$1"; in="$2"; out="$3"
if [ "$mode" = "white-bg" ]; then
  magick "$in" -alpha on -fuzz 8% -fill none -floodfill +0+0 white \
    -trim +repage -bordercolor none -border 4% \
    -resize 512x512 -gravity center -background none -extent 512x512 "$out"
elif [ "$mode" = "wechat" ]; then
  corner=$(magick "$in" -format '%[pixel:p{4,4}]' info:)
  magick "$in" -alpha on -fuzz 12% -fill none -floodfill +4+4 "$corner" \
    -channel RGB -fill '#07C160' -colorize 100 +channel \
    -trim +repage -bordercolor none -border 4% \
    -resize 512x512 -gravity center -background none -extent 512x512 "$out"
else
  echo "unknown mode: $mode" >&2; exit 2
fi
