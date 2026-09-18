#!/usr/bin/env python3
"""Import the authored riverland farm schematic with audited coordinates."""

import argparse
import copy
import hashlib
import importlib.util
import json
from pathlib import Path


def load_intake(script_dir):
    path = script_dir / 'import_farm_building_prefabs.py'
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


def require_state(schematic, position, expected):
    actual = schematic.state(tuple(position))
    if actual != expected:
        raise ValueError(f'{position}: expected {expected}, found {actual}')


def verify_ruins(intake, farm, source_dir):
    ruins = intake.Schematic(source_dir / 'green_house_ruins.schem')
    anchor = (101, 24, 81)
    manager = (12, 1, 11)
    differences = []
    for (x, y, z), state in ruins.entries():
        if y == 0 or (x, y, z) == manager:
            continue
        farm_state = farm.state((anchor[0] + x, anchor[1] + y, anchor[2] + z))
        if farm_state != state:
            differences.append(((x, y, z), state, farm_state))
    if differences:
        raise ValueError(f'embedded greenhouse ruins diverge: {differences[:5]}')
    return list(ruins.size)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path,
                        default=Path('run/config/worldedit/schematics/farm_2.schem'))
    parser.add_argument('--source-dir', type=Path,
                        default=Path('run/config/worldedit/schematics'))
    parser.add_argument('--output', type=Path,
                        default=Path('src/main/resources/data/stardewcraft/structures/farm/riverland.schem'))
    parser.add_argument('--report', type=Path,
                        default=Path('scripts/data/riverland_farm_report.json'))
    args = parser.parse_args()

    intake = load_intake(Path(__file__).resolve().parent)
    farm = intake.Schematic(args.source)
    expected_size = (288, 92, 272)
    if farm.size != expected_size:
        raise ValueError(f'farm_2 size changed: {farm.size} != {expected_size}')

    non_air = [(position, state) for position, state in farm.entries()
               if state not in intake.AIR]
    cropped_height = max(position[1] for position, _ in non_air) + 1
    if cropped_height != 81:
        raise ValueError(f'unexpected authored height: {cropped_height}')

    checks = {
        'spawn': ([199, 25, 93], 'minecraft:air'),
        'spawn_ground': ([199, 24, 93], 'stardewcraft:dirt[variant=0]'),
        'pet_bowl': ([182, 25, 83], 'minecraft:air'),
        'pet_bowl_ground': ([182, 24, 83], 'stardewcraft:grass_block[snowy=false,variant=0]'),
        'totem': ([221, 25, 81], 'minecraft:air'),
        'totem_ground': ([221, 24, 81], 'stardewcraft:grass_block[snowy=false,variant=0]'),
        'cave_wall': ([119, 26, 77], 'minecraft:black_concrete'),
        'cave_portal': ([119, 25, 78], 'minecraft:air'),
        'north_exit': ([141, 25, 37], 'minecraft:air'),
        'east_exit': ([251, 25, 95], 'minecraft:air'),
        'south_exit': ([145, 25, 237], 'minecraft:air'),
    }
    for position, expected in checks.values():
        require_state(farm, position, expected)
    ruins_size = verify_ruins(intake, farm, args.source_dir)

    root = copy.deepcopy(intake.read_nbt(args.source))
    schematic = intake.value(root, 'Schematic', root)
    blocks = intake.value(schematic, 'Blocks')
    width, _, length = farm.size
    kept_cells = width * cropped_height * length
    schematic['Height'] = (schematic['Height'][0], cropped_height)
    blocks['Data'] = (7, encode_varints(farm.ids[:kept_cells]))
    subtype, entities = intake.value(blocks, 'BlockEntities', (10, []))
    entities = [entity for entity in entities
                if intake.value(entity, 'Pos')[1] < cropped_height]
    blocks['BlockEntities'] = (9, (subtype, entities))

    intake.write_nbt(args.output, root)
    imported = intake.Schematic(args.output)
    if imported.size != (width, cropped_height, length):
        raise ValueError(f'output size mismatch: {imported.size}')
    if imported.ids != farm.ids[:kept_cells]:
        raise ValueError('cropped block data failed round-trip verification')
    if imported.block_entities.keys() != farm.block_entities.keys():
        raise ValueError('block entities changed during crop')

    report = {
        'source': str(args.source),
        'source_sha256': hashlib.sha256(args.source.read_bytes()).hexdigest(),
        'authored_selection': {
            'min': [736, 48, -1616],
            'max': [1023, 139, -1345],
        },
        'source_size': list(farm.size),
        'resource_size': list(imported.size),
        'cropped_pure_air_layers': farm.size[1] - cropped_height,
        'non_air_blocks': len(non_air),
        'block_entities': len(imported.block_entities),
        'anchors': {name: position for name, (position, _) in checks.items()
                    if not name.endswith('_ground')},
        'greenhouse_ruins': {'anchor': [101, 24, 81], 'size': ruins_size,
                             'geometry_match': 'all non-floor, non-manager cells'},
        'output_sha256': hashlib.sha256(args.output.read_bytes()).hexdigest(),
        'verification': 'Sponge v3 round-trip, anchor states, cropped air, block entities, embedded ruins',
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'Imported riverland farm {farm.size} -> {imported.size}')


if __name__ == '__main__':
    main()
