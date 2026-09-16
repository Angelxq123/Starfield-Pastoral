#!/usr/bin/env python3
"""Check model texture files and membership in Minecraft's block atlas, without a client."""
import json
from pathlib import Path


def check(assets: Path) -> list[str]:
    atlas = json.loads((assets / "minecraft/atlases/blocks.json").read_text())
    singles = {s.get("sprite", s["resource"]) for s in atlas["sources"]
               if s["type"] in ("single", "minecraft:single")}
    directories = [s for s in atlas["sources"] if s["type"] in ("directory", "minecraft:directory")]
    regions = {r["sprite"]: s["resource"] for s in atlas["sources"]
               if s["type"] in ("unstitch", "minecraft:unstitch") for r in s["regions"]}
    errors = []
    for model in sorted((assets / "stardewcraft/models").rglob("*.json")):
        for texture in set(json.loads(model.read_text(encoding="utf-8-sig")).get("textures", {}).values()):
            if not isinstance(texture, str) or not texture.startswith("stardewcraft:"):
                continue
            namespace, name = texture.split(":", 1)
            image_namespace, image_name = regions.get(texture, texture).split(":", 1)
            image = assets / image_namespace / "textures" / (image_name + ".png")
            if not image.is_file():
                errors.append(f"{model.relative_to(assets)}: texture file missing: {texture}")
            # Vanilla's block/item directory sources apply to every resource namespace.
            included = name.startswith(("block/", "item/")) or texture in singles or texture in regions
            for source in directories:
                prefix = source.get("prefix", "")
                if name.startswith(prefix):
                    path = source["source"].rstrip("/") + "/" + name[len(prefix):] + ".png"
                    included |= (assets / namespace / "textures" / path).is_file()
            if not included:
                errors.append(f"{model.relative_to(assets)}: texture absent from minecraft:blocks atlas: {texture}")
    return sorted(set(errors))


if __name__ == "__main__":
    failures = check(Path(__file__).resolve().parents[1] / "src/main/resources/assets")
    if failures:
        raise SystemExit("\n".join(failures))
    print("Model textures exist and are included in the block atlas.")
