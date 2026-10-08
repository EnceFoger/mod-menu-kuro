#!/usr/bin/env python3
"""Create the Android res folder (theme, drawables, launcher icons) from logo.jpg.

Usage: python3 setup_res.py [logo_file] [res_dir]
Defaults: logo.jpg  app/src/main/res      (needs: pip install pillow)
"""
import os
import sys
from PIL import Image, ImageDraw

XML = {
    'drawable/seek_progress.xml': '<?xml version="1.0" encoding="utf-8"?>\n<layer-list xmlns:android="http://schemas.android.com/apk/res/android">\n    <item\n        android:id="@android:id/background"\n        android:gravity="center_vertical"\n        android:height="2dp">\n        <shape android:shape="rectangle">\n            <solid android:color="#333333" />\n        </shape>\n    </item>\n    <item\n        android:id="@android:id/progress"\n        android:gravity="center_vertical"\n        android:height="2dp">\n        <clip>\n            <shape android:shape="rectangle">\n                <solid android:color="#FFFFFF" />\n            </shape>\n        </clip>\n    </item>\n</layer-list>\n',
    'drawable/seek_thumb.xml': '<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">\n    <solid android:color="#FFFFFF" />\n    <size android:width="16dp" android:height="16dp" />\n</shape>\n',
    'drawable/switch_thumb.xml': '<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n    <item android:state_checked="true">\n        <shape android:shape="oval">\n            <solid android:color="#FFFFFF" />\n            <size android:width="20dp" android:height="20dp" />\n        </shape>\n    </item>\n    <item>\n        <shape android:shape="oval">\n            <solid android:color="#6B6B6B" />\n            <size android:width="20dp" android:height="20dp" />\n        </shape>\n    </item>\n</selector>\n',
    'drawable/switch_track.xml': '<?xml version="1.0" encoding="utf-8"?>\n<selector xmlns:android="http://schemas.android.com/apk/res/android">\n    <item android:state_checked="true">\n        <shape android:shape="rectangle">\n            <solid android:color="#8C8C8C" />\n            <corners android:radius="7dp" />\n            <size android:width="36dp" android:height="14dp" />\n        </shape>\n    </item>\n    <item>\n        <shape android:shape="rectangle">\n            <solid android:color="#333333" />\n            <corners android:radius="7dp" />\n            <size android:width="36dp" android:height="14dp" />\n        </shape>\n    </item>\n</selector>\n',
    'values/colors.xml': '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="bg">#000000</color>\n    <color name="fg">#FFFFFF</color>\n    <color name="sub">#A0A0A0</color>\n    <color name="line">#262626</color>\n</resources>\n',
    'values/styles.xml': '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <style name="AppTheme" parent="android:Theme.Material.NoActionBar">\n        <item name="android:windowBackground">@color/bg</item>\n        <item name="android:colorBackground">@color/bg</item>\n        <item name="android:statusBarColor">@color/bg</item>\n        <item name="android:navigationBarColor">@color/bg</item>\n        <item name="android:textColorPrimary">@color/fg</item>\n        <item name="android:textColorSecondary">@color/sub</item>\n        <item name="android:colorAccent">@color/fg</item>\n        <item name="android:colorControlActivated">@color/fg</item>\n    </style>\n</resources>\n',
}

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SS = 4


def center_square(img):
    w, h = img.size
    s = min(w, h)
    return img.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s))


def round_version(sq, size):
    big = sq.resize((size * SS, size * SS), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big.size[0] - 1, big.size[1] - 1), fill=255)
    big.putalpha(mask)
    return big.resize((size, size), Image.LANCZOS)


def main():
    logo = sys.argv[1] if len(sys.argv) > 1 else "logo.jpg"
    res = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/res"
    for rel, text in XML.items():
        path = os.path.join(res, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
    sq = center_square(Image.open(logo).convert("RGB"))
    for name, size in SIZES.items():
        d = os.path.join(res, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        sq.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"), optimize=True)
        round_version(sq, size).save(os.path.join(d, "ic_launcher_round.png"), optimize=True)
    print("Resources written to", res)


if __name__ == "__main__":
    main()
