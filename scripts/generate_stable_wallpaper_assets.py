#!/usr/bin/env python3
"""Generate blockstates, hidden item models, and the mining tag for stable wallpapers."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/stardewcraft"
DATA = ROOT / "src/main/resources/data/stardewcraft"


def style_ids() -> list[str]:
    return [str(index) for index in range(112)] + [f"MoreWalls:{index}" for index in range(26)]


def registry_path(style_id: str) -> str:
    if style_id.startswith("MoreWalls:"):
        return f"wallpaper_morewalls_{style_id.removeprefix('MoreWalls:')}"
    return f"wallpaper_{style_id}"


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    ids = style_ids()
    for visual_index, style_id in enumerate(ids):
        name = registry_path(style_id)
        write_json(
            ASSETS / "blockstates" / f"{name}.json",
            {
                "variants": {
                    f"segment={segment}": {
                        "model": f"stardewcraft:block/deco/wallpaper/style_{visual_index}_segment_{segment}"
                    }
                    for segment in range(3)
                }
            },
        )
        if style_id != "0":
            write_json(
                ASSETS / "models/item" / f"{name}.json",
                {"parent": f"stardewcraft:block/deco/wallpaper/style_{visual_index}_segment_0"},
            )

    write_json(
        DATA / "tags/block/stable_wallpapers.json",
        {
            "replace": False,
            "values": [f"stardewcraft:{registry_path(style_id)}" for style_id in ids],
        },
    )
    write_json(
        DATA / "tags/item/stable_wallpapers.json",
        {
            "replace": False,
            "values": [f"stardewcraft:{registry_path(style_id)}" for style_id in ids if style_id != "0"],
        },
    )


if __name__ == "__main__":
    main()
