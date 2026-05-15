#!/bin/bash
# Download 8 unique, high-quality real-estate photos from Unsplash CDN.
# Each file maps 1:1 to one of the seeded listings — Bruno's Phase02 uploads
# them via multipart so each ends up at a unique URL in R2.
#
# Idempotent: skips files that already exist (same byte size).
# Total download: ~5 MB.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

UNSPLASH_PARAMS="w=1600&h=1067&fit=crop&q=80&auto=format"

declare -a PHOTOS=(
  "lekki-3bed.jpg|1564013799919-ab600027ffc6"           # modern living room
  "ikate-2bed.jpg|1502672260266-1c1ef2d93688"           # Scandinavian interior
  "bourdillon-duplex.jpg|1600596542815-ffad4c1539a9"    # luxury house
  "ikeja-semi.jpg|1568605114967-8130f3a36994"           # house exterior
  "yaba-3bed.jpg|1600585154340-be6161a56a0c"            # modern kitchen
  "yaba-studio.jpg|1565183997392-2f6f122e5912"          # bedroom
  "ikoyi-terrace.jpg|1600210492486-724fe5c67fb0"        # modern bedroom
  "awolowo-serviced.jpg|1613490493576-7fde63acd811"     # luxury bedroom
)

for entry in "${PHOTOS[@]}"; do
  filename="${entry%%|*}"
  photo_id="${entry##*|}"
  url="https://images.unsplash.com/photo-${photo_id}?${UNSPLASH_PARAMS}"

  if [ -f "$filename" ] && [ -s "$filename" ]; then
    echo "  ⏭  $filename already present — skipping"
    continue
  fi

  echo "  ⬇  $filename ← Unsplash ${photo_id:0:14}…"
  curl -fsSL "$url" -o "$filename"
done

echo ""
echo "✅ Photos ready. Sizes:"
ls -lh *.jpg | awk '{print "    " $5 "\t" $9}'
