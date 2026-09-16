#!/usr/bin/env python3
"""Offline Sponge v3 -> vanilla structure intake; no WorldEdit runtime dependency."""
import argparse
import collections
import gzip
import hashlib
import json
import struct
from pathlib import Path


class NbtReader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, count):
        if count < 0 or self.offset + count > len(self.data):
            raise ValueError('Truncated NBT')
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        return result

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))[0]

    def string(self):
        return self.take(self.number('H')).decode('utf-8')

    def payload(self, kind):
        if kind in range(1, 7):
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind == 7:
            return self.take(self.number('i'))
        if kind == 8:
            return self.string()
        if kind == 9:
            subtype, count = self.number('B'), self.number('i')
            return subtype, [self.payload(subtype) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                subtype = self.number('B')
                if subtype == 0:
                    return result
                name = self.string()
                result[name] = (subtype, self.payload(subtype))
        if kind in (11, 12):
            return [self.number('i' if kind == 11 else 'q') for _ in range(self.number('i'))]
        raise ValueError(f'Unsupported NBT tag {kind}')


def encode_string(value):
    raw = value.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def encode_payload(kind, value):
    if kind in range(1, 7):
        return struct.pack('>' + {1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind], value)
    if kind == 7:
        return struct.pack('>i', len(value)) + value
    if kind == 8:
        return encode_string(value)
    if kind == 9:
        subtype, entries = value
        return bytes([subtype]) + struct.pack('>i', len(entries)) + b''.join(encode_payload(subtype, x) for x in entries)
    if kind == 10:
        return b''.join(bytes([t]) + encode_string(k) + encode_payload(t, v) for k, (t, v) in value.items()) + b'\0'
    if kind in (11, 12):
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>i' if kind == 11 else '>q', v) for v in value)
    raise ValueError(kind)


def read_nbt(path):
    reader = NbtReader(gzip.decompress(Path(path).read_bytes()))
    if reader.number('B') != 10:
        raise ValueError('Expected compound root')
    reader.string()
    result = reader.payload(10)
    if reader.offset != len(reader.data):
        raise ValueError('Trailing NBT data')
    return result


def write_nbt(path, root):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(b'\x0a\0\0' + encode_payload(10, root), mtime=0))


def value(compound, key, default=None):
    return compound[key][1] if key in compound else default


def int_list(values):
    return 9, (3, list(values))


class Schematic:
    def __init__(self, path):
        self.path = Path(path)
        root = read_nbt(path)
        self.root = value(root, 'Schematic', root)
        if value(self.root, 'Version') != 3:
            raise ValueError('Only audited Sponge v3 sources are supported')
        self.size = tuple(value(self.root, k) for k in ('Width', 'Height', 'Length'))
        blocks = value(self.root, 'Blocks')
        self.palette = {entry[1]: name for name, entry in value(blocks, 'Palette').items()}
        self.ids, number, shift = [], 0, 0
        for byte in value(blocks, 'Data'):
            number |= (byte & 127) << shift
            if byte & 128:
                shift += 7
                if shift > 28:
                    raise ValueError('Oversize palette varint')
            else:
                self.ids.append(number)
                number, shift = 0, 0
        if shift or len(self.ids) != self.size[0] * self.size[1] * self.size[2]:
            raise ValueError('Invalid block array length')
        if any(i not in self.palette for i in self.ids):
            raise ValueError('Unknown palette index')
        self.block_entities = {}
        for be in value(blocks, 'BlockEntities', (10, []))[1]:
            self.block_entities[tuple(value(be, 'Pos'))] = be
        if value(self.root, 'Entities', (10, []))[1]:
            raise ValueError('Entity-bearing source requires explicit review')

    def entries(self):
        w, _, length = self.size
        for i, key in enumerate(self.ids):
            yield (i % w, i // (w * length), (i // w) % length), self.palette[key]

    def state(self, pos):
        x, y, z = pos
        w, h, length = self.size
        if not (0 <= x < w and 0 <= y < h and 0 <= z < length):
            return None
        return self.palette[self.ids[y * length * w + z * w + x]]


def inspect(source_dir):
    for family in ('coop', 'barn'):
        for tier in range(1, 4):
            s = Schematic(source_dir / f'{family}_{tier}.schem')
            print(s.path.stem, s.size)
            print('DOORS', [(p, st) for p, st in s.entries() if '_door[' in st and 'half=lower' in st])
            for y in (0, 1, 2):
                print('LAYER', y)
                for z in range(s.size[2]):
                    row = ''
                    for x in range(s.size[0]):
                        st = s.state((x, y, z))
                        row += '.' if st == 'minecraft:air' else 'D' if '_door[' in st else 'T' if 'trough' in st else 'H' if 'hay_hopper' in st else 'I' if 'incubator' in st else '#'
                    print(f'{z:02} {row}')
            groups = collections.defaultdict(set)
            for be in s.block_entities.values():
                groups[value(be, 'Id')].update(value(be, 'Data', {}).keys())
            print('BE_FIELDS', {k: sorted(v) for k, v in groups.items()})


AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}
# Reviewed authoring fields only. Inventory, ownership, timers and bees are not templates.
APPEARANCE_FIELDS = {
    'id', 'StyleId', 'material', 'colorSelection', 'ClothColor',
    'front_text', 'back_text', 'is_waxed', 'sherds',
}


def native_state(state):
    name, _, tail = state.partition('[')
    result = {'Name': (8, name)}
    if tail:
        result['Properties'] = (10, {k: (8, v) for k, v in (p.split('=', 1) for p in tail.rstrip(']').split(','))})
    return result


def rotate(pos, turns):
    x, y, z = pos
    for _ in range(turns):
        x, z = -z, x
    return [x, y, z]


def relative(pos, anchor):
    return [a - b for a, b in zip(pos, anchor)]


def walkable(schematic, pos, manager):
    x, y, z = pos
    return (pos != tuple(manager)
            and schematic.state(pos) in AIR
            and schematic.state((x, y + 1, z)) in AIR
            and schematic.state((x, y - 1, z)) not in AIR | {None})


def approach_path(schematic, start, finish, manager):
    queue = collections.deque([tuple(start)])
    previous = {tuple(start): None}
    while queue:
        pos = queue.popleft()
        if pos == tuple(finish):
            path = []
            while pos is not None:
                path.append(list(pos))
                pos = previous[pos]
            return path[::-1]
        for dx, dz in ((0, -1), (1, 0), (0, 1), (-1, 0)):
            next_pos = (pos[0] + dx, pos[1], pos[2] + dz)
            if next_pos not in previous and walkable(schematic, next_pos, manager):
                previous[next_pos] = pos
                queue.append(next_pos)
    raise ValueError(f'{schematic.path.name}: manager approach is obstructed')


def export(source_dir, output_dir, config, report_path):
    report, family_definitions = [], {}
    for entry in config['templates']:
        sid, family = entry['id'], entry['family']
        source = Schematic(source_dir / (sid + '.schem'))
        anchor, manager = entry['anchor'], entry['manager']['position']
        approach, spawn = entry['manager']['approach'], entry['animal_spawn']
        if not walkable(source, tuple(manager), []):
            raise ValueError(f'{sid}: manager needs two clear blocks and support')
        if not walkable(source, tuple(spawn), manager):
            raise ValueError(f'{sid}: animal spawn is obstructed')
        path = approach_path(source, [anchor[0], 1, anchor[2] - 1], approach, manager)
        if '_door[' not in source.state((anchor[0], 1, anchor[2])):
            raise ValueError(f'{sid}: anchor is not the main ground-floor door')
        if any(source.state((x, 0, z)) in AIR for x in range(source.size[0]) for z in range(source.size[2])):
            raise ValueError(f'{sid}: incomplete authored floor layer')
        state_names = [source.palette[i] for i in sorted(source.palette)]
        manager_state = f'stardewcraft:{family}_manager[facing={entry["manager"]["facing"]}]'
        state_names.append(manager_state)
        palette_index = {s: i for i, s in enumerate(state_names)}
        blocks, removed, kept_entities = [], collections.Counter(), 0
        for pos, state in source.entries():
            if list(pos) == manager:
                state = manager_state
            # Keep air in the resource: future upgrades must clear obsolete geometry.
            block = {'pos': int_list(pos), 'state': (3, palette_index[state])}
            if pos in source.block_entities:
                be = source.block_entities[pos]
                data = value(be, 'Data', {})
                unknown = set(data) - APPEARANCE_FIELDS
                removed.update(unknown)
                cleaned = {k: v for k, v in data.items() if k in APPEARANCE_FIELDS}
                cleaned['id'] = (8, value(be, 'Id'))
                block['nbt'] = (10, cleaned)
                kept_entities += 1
            blocks.append(block)
        root = {
            'DataVersion': (3, value(source.root, 'DataVersion')),
            'size': int_list(source.size),
            'palette': (9, (10, [native_state(s) for s in state_names])),
            'blocks': (9, (10, blocks)),
            'entities': (9, (10, [])),
        }
        destination = output_dir / 'structure' / 'farm_buildings' / (sid + '.nbt')
        write_nbt(destination, root)
        if read_nbt(destination) != root:
            raise ValueError(f'{sid}: native NBT round-trip failed')
        corners = [(x, y, z) for x in (0, source.size[0] - 1) for y in (0, source.size[1] - 1) for z in (0, source.size[2] - 1)]
        local_corners = [relative(p, anchor) for p in corners]
        bounds = {'min': [min(p[i] for p in local_corners) for i in range(3)],
                  'max_exclusive': [max(p[i] for p in local_corners) + 1 for i in range(3)]}
        rotated = []
        for turn in range(4):
            points = [rotate(p, turn) for p in local_corners]
            if any(rotate(rotate(p, turn), (4 - turn) % 4) != p for p in local_corners):
                raise ValueError('Non-invertible coordinate rotation')
            rotated.append({'quarter_turns_clockwise': turn,
                            'min': [min(p[i] for p in points) for i in range(3)],
                            'max_exclusive': [max(p[i] for p in points) + 1 for i in range(3)],
                            'manager_offset': rotate(relative(manager, anchor), turn)})
        definition = {
            'tier': entry['tier'], 'structure': 'stardewcraft:farm_buildings/' + sid,
            'size': list(source.size), 'anchor': anchor,
            'bounds_from_anchor': bounds,
            'manager': entry['manager'], 'entry': entry['entry'],
            'animal_spawn': spawn,
            'construction_worker': {'animation': 'animation.robin.construction_low',
                                    'horizontal_offset_from_anchor': [0, -2],
                                    'facing': 'north', 'height_policy': 'stand_on_current_surface'},
        }
        rules = config.get('family_rules', {}).get(family, {})
        upgrade = rules.get('upgrades', {}).get(str(entry['tier']))
        if upgrade is not None:
            definition['upgrade'] = upgrade
        facilities = rules.get('facilities', {}).get(str(entry['tier']))
        if facilities is not None:
            definition['facilities'] = facilities
        family_definitions.setdefault(family, []).append(definition)
        report.append({'id': sid, 'source_sha256': hashlib.sha256(source.path.read_bytes()).hexdigest(),
                       'size': list(source.size), 'manager_offset': relative(manager, anchor),
                       'manager_approach_path': path, 'preserved_block_entities': kept_entities,
                       'removed_runtime_fields': dict(sorted(removed.items())),
                       'coordinate_rotations': rotated,
                       'verification': 'NBT round-trip, floor, manager clearance, approach path, anchor rotation; no in-game render claim'})
    for family, tiers in family_definitions.items():
        envelope = {
            'min': [min(t['bounds_from_anchor']['min'][i] for t in tiers) for i in range(3)],
            'max_exclusive': [max(t['bounds_from_anchor']['max_exclusive'][i] for t in tiers) for i in range(3)],
        }
        definition = {'format_version': 1, 'family': family, 'authored_front': 'south',
                      'anchor_convention': 'floor block below eastmost main entrance door',
                      'reservation_bounds_from_anchor': envelope, 'tiers': tiers}
        self_build = config.get('family_rules', {}).get(family, {}).get('self_build')
        if self_build is not None:
            definition['self_build'] = self_build
        target = output_dir / 'farm_building_prefabs' / (family + '.json')
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(definition, ensure_ascii=False, indent=2) + '\n')
    if report_path:
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(f'Exported and verified {len(report)} structures and {len(family_definitions)} family definitions')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path, required=True)
    parser.add_argument('--config', type=Path)
    parser.add_argument('--output-dir', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    if bool(args.config) != bool(args.output_dir):
        parser.error('--config and --output-dir must be supplied together')
    if args.config:
        export(args.source_dir, args.output_dir, json.loads(args.config.read_text()), args.report)
    else:
        inspect(args.source_dir)
