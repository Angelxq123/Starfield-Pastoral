#!/usr/bin/env python3
"""Generate area-density ecology profiles from Stardew Valley farm maps.

The Paths layer is statistical input, not a placement mask.  Every 10x10
source-map neighborhood is emitted, including neighborhoods with no marker of
their own, and its rates are blended with neighboring and farm-wide samples.
This lets the larger Minecraft farms keep the original regional character
without copying exact tiles or leaving enlarged margins empty.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "源文件" / "Content" / "Maps"
OUTPUT_DIR = ROOT / "src" / "main" / "resources" / "data" / "stardewcraft" / "farm_ecology"
ZONE_SIZE = 10
FLIP_MASK = 0x1FFFFFFF

TILE_KIND = {
    9: "oak",
    10: "maple",
    11: "pine",
    13: "weeds",
    14: "weeds",
    15: "weeds",
    16: "stones",
    17: "stones",
    18: "twigs",
    22: "pasture",
    23: "saplings",
    24: "large_bush",
    25: "large_bush",
    26: "small_bush",
    33: "small_bush",
    36: "blue_pasture",
}
KINDS = tuple(dict.fromkeys(TILE_KIND.values()))


@dataclass(frozen=True)
class FarmSpec:
    output: str
    source: str
    scale_x: float
    scale_z: float
    offset_x: int
    offset_z: int


FARMS = (
    FarmSpec("standard", "Farm.tmx", 2.00, 2.15, 70, 63),
    FarmSpec("riverland", "Farm_Fishing.tmx", 1.90, 2.00, 68, 66),
    FarmSpec("forest", "Farm_Foraging.tmx", 1.90, 2.05, 67, 65),
    FarmSpec("hilltop", "Farm_Mining.tmx", 2.15, 2.00, 58, 79),
    FarmSpec("wilderness", "Farm_Combat.tmx", 1.90, 2.05, 68, 73),
    FarmSpec("four_corners", "Farm_FourCorners.tmx", 1.90, 2.00, 70, 62),
    FarmSpec("beach", "Farm_Island.tmx", 1.58, 1.18, 52, 75),
    FarmSpec("meadowlands", "Farm_Ranching.tmx", 1.55, 2.05, 74, 58),
)


def parse_csv(layer: ET.Element, expected: int) -> list[int]:
    data = layer.find("data")
    if data is None or data.get("encoding") != "csv":
        raise ValueError(f"layer {layer.get('name')} is not CSV")
    values = [int(value.strip()) & FLIP_MASK for value in (data.text or "").split(",") if value.strip()]
    if len(values) != expected:
        raise ValueError(f"layer {layer.get('name')} has {len(values)} cells, expected {expected}")
    return values


def tile_properties(root: ET.Element) -> tuple[dict[int, dict[str, str]], int]:
    properties: dict[int, dict[str, str]] = {}
    paths_first_gid = -1
    for tileset in root.findall("tileset"):
        first_gid = int(tileset.get("firstgid", "0"))
        if tileset.get("name") == "Paths":
            paths_first_gid = first_gid
        for tile in tileset.findall("tile"):
            gid = first_gid + int(tile.get("id", "0"))
            values: dict[str, str] = {}
            props = tile.find("properties")
            if props is not None:
                for prop in props.findall("property"):
                    values[prop.get("name", "")] = prop.get("value", prop.text or "")
            properties[gid] = values
    if paths_first_gid < 0:
        raise ValueError("map has no Paths tileset")
    return properties, paths_first_gid


def source_ground(props: dict[str, str]) -> str | None:
    tile_type = props.get("Type", "").lower()
    if tile_type == "grass":
        return "grass"
    if tile_type == "dirt" or "Diggable" in props:
        return "dirt"
    return None


def nearest_ground(grounds: list[str | None], width: int, height: int, x: int, z: int) -> str:
    ground = grounds[z * width + x]
    if ground is not None:
        return ground
    for radius in (1, 2, 3):
        votes: Counter[str] = Counter()
        for dz in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                nx, nz = x + dx, z + dz
                if 0 <= nx < width and 0 <= nz < height:
                    candidate = grounds[nz * width + nx]
                    if candidate is not None:
                        votes[candidate] += 1
        if votes:
            return votes.most_common(1)[0][0]
    return "dirt"


def rounded_rate(value: float) -> float:
    return round(value, 7)


def build_profile(spec: FarmSpec) -> dict[str, object]:
    source_path = SOURCE_DIR / spec.source
    source_bytes = source_path.read_bytes()
    root = ET.fromstring(source_bytes)
    width = int(root.get("width", "0"))
    height = int(root.get("height", "0"))
    cell_count = width * height
    layers = {layer.get("name"): layer for layer in root.findall("layer")}
    back = parse_csv(layers["Back"], cell_count)
    paths = parse_csv(layers["Paths"], cell_count)
    props, paths_first_gid = tile_properties(root)
    grounds = [source_ground(props.get(gid, {})) if gid else None for gid in back]

    zone_columns = math.ceil(width / ZONE_SIZE)
    zone_rows = math.ceil(height / ZONE_SIZE)
    ground_cells: dict[tuple[int, int], Counter[str]] = defaultdict(Counter)
    markers: dict[tuple[int, int], dict[str, Counter[str]]] = defaultdict(
        lambda: defaultdict(Counter)
    )
    global_cells: Counter[str] = Counter()
    global_markers: dict[str, Counter[str]] = defaultdict(Counter)
    source_totals: Counter[str] = Counter()

    for z in range(height):
        for x in range(width):
            zone_key = (x // ZONE_SIZE, z // ZONE_SIZE)
            ground = grounds[z * width + x]
            if ground is not None:
                ground_cells[zone_key][ground] += 1
                global_cells[ground] += 1
            gid = paths[z * width + x]
            if not gid:
                continue
            path_tile = gid - paths_first_gid
            kind = TILE_KIND.get(path_tile)
            if kind is None:
                continue
            marker_ground = nearest_ground(grounds, width, height, x, z)
            markers[zone_key][marker_ground][kind] += 1
            global_markers[marker_ground][kind] += 1
            source_totals[kind] += 1

    global_rates: dict[str, dict[str, float]] = {"dirt": {}, "grass": {}}
    for ground in ("dirt", "grass"):
        denominator = max(1, global_cells[ground])
        for kind in KINDS:
            global_rates[ground][kind] = global_markers[ground][kind] / denominator

    zones: list[dict[str, object]] = []
    for zone_z in range(zone_rows):
        for zone_x in range(zone_columns):
            rates: dict[str, dict[str, float]] = {"dirt": {}, "grass": {}}
            for ground in ("dirt", "grass"):
                local_cells = 0.0
                local_counts: Counter[str] = Counter()
                for dz in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        nx, nz = zone_x + dx, zone_z + dz
                        if not (0 <= nx < zone_columns and 0 <= nz < zone_rows):
                            continue
                        weight = 4 if dx == 0 and dz == 0 else 2 if dx == 0 or dz == 0 else 1
                        key = (nx, nz)
                        local_cells += ground_cells[key][ground] * weight
                        for kind, count in markers[key][ground].items():
                            local_counts[kind] += count * weight
                for kind in KINDS:
                    local_rate = (local_counts[kind] / local_cells) if local_cells else global_rates[ground][kind]
                    rate = 0.65 * local_rate + 0.35 * global_rates[ground][kind]
                    if rate > 0.0:
                        rates[ground][kind] = rounded_rate(rate)

            source_min_x = zone_x * ZONE_SIZE
            source_min_z = zone_z * ZONE_SIZE
            source_max_x = min(width, source_min_x + ZONE_SIZE)
            source_max_z = min(height, source_min_z + ZONE_SIZE)
            # Use the same rounded source-grid edges as the farm imports.  Each
            # edge is shared by adjacent zones, so this covers every projected
            # column exactly once, including the beach's far-right/bottom strip.
            bounds = [
                round(spec.offset_x + source_min_x * spec.scale_x),
                round(spec.offset_z + source_min_z * spec.scale_z),
                round(spec.offset_x + source_max_x * spec.scale_x) - 1,
                round(spec.offset_z + source_max_z * spec.scale_z) - 1,
            ]
            zones.append({"bounds": bounds, "rates": rates})

    return {
        "format": 3,
        "source_map": spec.source,
        "source_sha256": hashlib.sha256(source_bytes).hexdigest(),
        "source_size": [width, height],
        "source_totals": {kind: source_totals[kind] for kind in KINDS if source_totals[kind]},
        "zones": zones,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if generated files differ")
    args = parser.parse_args()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    changed: list[str] = []
    for spec in FARMS:
        profile = build_profile(spec)
        encoded = json.dumps(profile, ensure_ascii=False, separators=(",", ":")) + "\n"
        output = OUTPUT_DIR / f"{spec.output}.json"
        if not output.exists() or output.read_text(encoding="utf-8") != encoded:
            changed.append(spec.output)
            if not args.check:
                output.write_text(encoded, encoding="utf-8")
        print(
            f"{spec.output}: zones={len(profile['zones'])} "
            f"totals={profile['source_totals']}"
        )
    if args.check and changed:
        raise SystemExit("outdated farm ecology profiles: " + ", ".join(changed))


if __name__ == "__main__":
    main()
