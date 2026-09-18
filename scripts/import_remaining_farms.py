#!/usr/bin/env python3
"""Import and audit the authored late-game farm layouts."""

import copy
import hashlib
import importlib.util
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / 'run/config/worldedit/schematics'

FARMS = {
    'hilltop': {
        'source': 'farm_4.schem', 'size': (288, 93, 272), 'height': 83,
        'selection': ([736, 48, -1296], [1023, 140, -1025]),
        'greenhouse': (101, 24, 81), 'ruins_non_air_only': False,
        'ore_bbox': ((75, 24, 154), (110, 24, 180)), 'ore_count': 876,
        'checks': {
            'spawn': ((199, 25, 93), 'minecraft:air'),
            'pet_bowl': ((178, 25, 78), 'minecraft:air'),
            'totem': ((163, 25, 77), 'minecraft:air'),
            'cave_wall': ((130, 26, 72), 'minecraft:black_concrete'),
            'cave_portal': ((130, 25, 73), 'minecraft:air'),
            'north_exit': ((142, 25, 37), 'minecraft:air'),
            'east_exit': ((251, 25, 95), 'minecraft:air'),
            'south_exit': ((144, 21, 237), 'minecraft:air'),
        },
    },
    'wilderness': {
        'source': 'farm_5.schem', 'size': (288, 94, 272), 'height': 81,
        'selection': ([1408, 48, -1296], [1695, 141, -1025]),
        'greenhouse': (101, 24, 81), 'ruins_non_air_only': False,
        'ore_bbox': None, 'ore_count': 0,
        'checks': {
            'spawn': ((199, 25, 93), 'minecraft:air'),
            'pet_bowl': ((177, 25, 70), 'minecraft:air'),
            'totem': ((175, 25, 70), 'minecraft:air'),
            'cave_wall': ((127, 26, 72), 'minecraft:black_concrete'),
            'cave_portal': ((127, 25, 73), 'minecraft:air'),
            'north_exit': ((142, 25, 37), 'minecraft:air'),
            'east_exit': ((251, 25, 95), 'minecraft:air'),
            'south_exit': ((144, 25, 237), 'minecraft:air'),
        },
    },
    'four_corners': {
        'source': 'farm_6.schem', 'size': (288, 94, 272), 'height': 81,
        'selection': ([1072, 48, -1296], [1359, 141, -1025]),
        'greenhouse': (132, 24, 110), 'ruins_non_air_only': True,
        'ore_bbox': ((157, 24, 189), (171, 24, 197)), 'ore_count': 135,
        'checks': {
            'spawn': ((199, 25, 93), 'minecraft:air'),
            'pet_bowl': ((155, 25, 142), 'minecraft:air'),
            'totem': ((150, 25, 140), 'minecraft:air'),
            'cave_wall': ((123, 26, 134), 'minecraft:black_concrete'),
            'cave_front_glass': ((123, 25, 138), 'minecraft:black_stained_glass'),
            'cave_portal': ((123, 25, 139), 'minecraft:air'),
            'north_exit': ((142, 25, 37), 'minecraft:air'),
            'east_exit': ((251, 25, 95), 'minecraft:air'),
            'south_exit': ((144, 29, 237), 'minecraft:air'),
            'renewable_stump': ((78, 25, 132), 'stardewcraft:large_stump[facing=south,part=main]'),
        },
    },
    'beach': {
        'source': 'farm_7.schem', 'size': (288, 97, 272), 'height': 78,
        'selection': ([400, 48, -1616], [687, 144, -1345]),
        'greenhouse': (72, 24, 85), 'ruins_non_air_only': True,
        'ore_bbox': None, 'ore_count': 0,
        'checks': {
            'spawn': ((154, 25, 99), 'minecraft:air'),
            'pet_bowl': ((178, 25, 102), 'minecraft:air'),
            'totem': ((183, 25, 111), 'minecraft:air'),
            'cave_wall': ((108, 26, 85), 'minecraft:black_concrete'),
            'cave_portal': ((108, 25, 86), 'minecraft:air'),
            'north_exit': ((148, 25, 35), 'minecraft:air'),
            'east_exit': ((251, 25, 95), 'minecraft:air'),
            'south_exit': ((181, 25, 237), 'minecraft:air'),
        },
    },
    'meadowlands': {
        'source': 'farm_8.schem', 'size': (288, 94, 272), 'height': 81,
        'selection': ([1408, 48, -1616], [1695, 141, -1345]),
        'greenhouse': (118, 24, 103), 'ruins_non_air_only': True,
        'ore_bbox': None, 'ore_count': 0,
        'coop_anchor': (145, 24, 77),
        'checks': {
            'spawn': ((199, 25, 93), 'minecraft:air'),
            'pet_bowl': ((221, 25, 79), 'minecraft:air'),
            'totem': ((175, 25, 77), 'minecraft:air'),
            'cave_wall': ((207, 26, 171), 'minecraft:black_concrete'),
            'cave_portal': ((207, 25, 172), 'minecraft:air'),
            'north_exit': ((164, 25, 37), 'minecraft:air'),
            'east_exit': ((251, 25, 95), 'minecraft:air'),
            'south_exit': ((144, 25, 237), 'minecraft:air'),
        },
    },
}


def load_intake():
    path = Path(__file__).resolve().parent / 'import_farm_building_prefabs.py'
    spec = importlib.util.spec_from_file_location('farm_building_intake', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def encode_varints(values):
    encoded = bytearray()
    for value in values:
        while True:
            current = value & 0x7f
            value >>= 7
            encoded.append(current | (0x80 if value else 0))
            if not value:
                break
    return bytes(encoded)


def verify_ruins(intake, farm, anchor, non_air_only):
    ruins = intake.Schematic(SOURCE_DIR / 'green_house_ruins.schem')
    manager = (12, 1, 11)
    differences = []
    for (x, y, z), state in ruins.entries():
        if y == 0 or (x, y, z) == manager or (non_air_only and state in intake.AIR):
            continue
        actual = farm.state((anchor[0] + x, anchor[1] + y, anchor[2] + z))
        if actual != state:
            differences.append(((x, y, z), state, actual))
    if differences:
        raise ValueError(f'embedded greenhouse ruins diverge: {differences[:5]}')
    return list(ruins.size)


def verify_coop(intake, farm, anchor):
    coop = intake.Schematic(SOURCE_DIR / 'coop_1.schem')
    differences = []
    checked = 0
    for (x, y, z), state in coop.entries():
        # The farm owns its terrain layer; Robin's completed projection owns
        # every authored building cell above it, including air and manager data.
        if y == 0 or state in intake.AIR:
            continue
        checked += 1
        actual = farm.state((anchor[0] + x, anchor[1] + y, anchor[2] + z))
        if actual != state:
            differences.append(((x, y, z), state, actual))
    if differences:
        raise ValueError(f'embedded tier-one coop diverges: {differences[:5]}')
    return {'anchor': list(anchor), 'size': list(coop.size),
            'matched_non_air_cells_above_floor': checked}


def import_farm(intake, name, config):
    source = SOURCE_DIR / config['source']
    output = ROOT / f'src/main/resources/data/stardewcraft/structures/farm/{name}.schem'
    report_path = ROOT / f'scripts/data/{name}_farm_report.json'
    farm = intake.Schematic(source)
    if farm.size != config['size']:
        raise ValueError(f'{name}: source size {farm.size} != {config["size"]}')
    non_air = [(pos, state) for pos, state in farm.entries() if state not in intake.AIR]
    cropped_height = max(pos[1] for pos, _ in non_air) + 1
    if cropped_height != config['height']:
        raise ValueError(f'{name}: authored height {cropped_height} != {config["height"]}')
    for label, (position, expected) in config['checks'].items():
        actual = farm.state(position)
        if actual != expected:
            raise ValueError(f'{name} {label} {position}: expected {expected}, found {actual}')

    ore_positions = [pos for pos, state in farm.entries()
                     if state.startswith('stardewcraft:mine_earth_loose_soil')]
    if len(ore_positions) != config['ore_count']:
        raise ValueError(f'{name}: ore mask count {len(ore_positions)} != {config["ore_count"]}')
    if ore_positions:
        found = (tuple(min(p[i] for p in ore_positions) for i in range(3)),
                 tuple(max(p[i] for p in ore_positions) for i in range(3)))
        if found != config['ore_bbox']:
            raise ValueError(f'{name}: ore mask bounds {found} != {config["ore_bbox"]}')
    ruins_size = verify_ruins(intake, farm, config['greenhouse'], config['ruins_non_air_only'])
    coop = verify_coop(intake, farm, config['coop_anchor']) if 'coop_anchor' in config else None

    root = copy.deepcopy(intake.read_nbt(source))
    schematic = intake.value(root, 'Schematic', root)
    blocks = intake.value(schematic, 'Blocks')
    width, _, length = farm.size
    kept_cells = width * cropped_height * length
    schematic['Height'] = (schematic['Height'][0], cropped_height)
    blocks['Data'] = (7, encode_varints(farm.ids[:kept_cells]))
    subtype, entities = intake.value(blocks, 'BlockEntities', (10, []))
    entities = [entity for entity in entities if intake.value(entity, 'Pos')[1] < cropped_height]
    blocks['BlockEntities'] = (9, (subtype, entities))
    intake.write_nbt(output, root)
    imported = intake.Schematic(output)
    if imported.size != (width, cropped_height, length) or imported.ids != farm.ids[:kept_cells]:
        raise ValueError(f'{name}: cropped schematic failed round-trip')
    if imported.block_entities.keys() != farm.block_entities.keys():
        raise ValueError(f'{name}: block entities changed during crop')

    report = {
        'source': str(source.relative_to(ROOT)),
        'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'authored_selection': {'min': config['selection'][0], 'max': config['selection'][1]},
        'source_size': list(farm.size), 'resource_size': list(imported.size),
        'cropped_pure_air_layers': farm.size[1] - cropped_height,
        'non_air_blocks': len(non_air), 'block_entities': len(imported.block_entities),
        'anchors': {label: list(pos) for label, (pos, _) in config['checks'].items()},
        'greenhouse_ruins': {'anchor': list(config['greenhouse']), 'size': ruins_size,
                             'geometry_match': 'non-air cells' if config['ruins_non_air_only'] else 'all non-floor, non-manager cells'},
        'ore_spawn_mask': {'block': 'stardewcraft:mine_earth_loose_soil',
                           'count': len(ore_positions), 'bounds': config['ore_bbox']},
        'output_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
        'verification': 'Sponge v3 round-trip, anchors, cropped air, block entities, ruins, ore mask',
    }
    if coop is not None:
        report['starter_coop'] = coop
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'Imported {name}: {farm.size} -> {imported.size}')


def main():
    intake = load_intake()
    for name, config in FARMS.items():
        import_farm(intake, name, config)


if __name__ == '__main__':
    main()
