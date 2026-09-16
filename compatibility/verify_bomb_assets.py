"""Check shipped native bomb geometry, atlas UVs, closed hulls and particle frames."""
import json
import math
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
AXES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1),
        'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


def png_size(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    return struct.unpack('>II', data[16:24])


for name, item in [('cherry_bomb', 'cherry_bomb'), ('bomb', 'bomb_item'), ('mega_bomb', 'mega_bomb')]:
    model = json.loads((ASSETS / f'models/entity/bomb/{name}.json').read_text())
    texture = f'stardewcraft:block/bomb/{name}'
    assert model['textures'] == {'0': texture, 'particle': texture + '_particle'}
    assert png_size(ASSETS / f'textures/block/bomb/{name}_particle.png') == (16, 16)
    width, height = png_size(ASSETS / f'textures/block/bomb/{name}.png')
    hulls = []
    for element in model['elements']:
        a, b = element['from'], element['to']
        assert min(a[1], b[1]) >= 0, (name, 'ground penetration')
        assert all(-16 <= v <= 32 for v in a + b), (name, element)
        negative = [a[i] > b[i] for i in range(3)]
        if any(negative):
            assert all(negative) and set(element['faces']) == set(AXES), 'Hull must be closed and fully inverted'
            assert 'rotation' in element, 'Preserve signed winding through the native face baker'
            hulls.append(element)
        for face, data in element['faces'].items():
            assert data['texture'] == '#0'
            u, v, U, V = data['uv']
            assert 0 <= min(u, U) <= max(u, U) <= 16
            assert 0 <= min(v, V) <= max(v, V) <= 16
            x, y = AXES[face]
            assert math.isclose(abs(U-u) * width / 16, abs(b[x]-a[x]), abs_tol=1e-6)
            assert math.isclose(abs(V-v) * height / 16, abs(b[y]-a[y]), abs_tol=1e-6)
    assert len(hulls) == (0 if name == 'cherry_bomb' else 1)
    for hull in hulls:
        socket = next(e for e in model['elements'] if e['name'].endswith('fuse_socket'))
        assert all(hull['to'][i] < socket['from'][i] < socket['to'][i] < hull['from'][i]
                   for i in range(3)), 'Hull must enclose the entire socket'
    cord = next(e for e in model['elements'] if e['name'].endswith('cord'))
    tip = [(cord['from'][0] + cord['to'][0]) / 2, cord['to'][1],
           (cord['from'][2] + cord['to'][2]) / 2]
    rotation = cord.get('rotation', {})
    angle = math.radians(rotation.get('angle', 0))
    if angle:
        assert rotation['axis'] == 'z'
        x, y, _ = rotation['origin']
        X, Y = tip[0]-x, tip[1]-y
        tip[0], tip[1] = x+X*math.cos(angle)-Y*math.sin(angle), y+X*math.sin(angle)+Y*math.cos(angle)
    expected = {'cherry_bomb': [8, 9, 8], 'bomb': [5.5-4/math.sqrt(2), 13.5+4/math.sqrt(2), 7.5],
                'mega_bomb': [8, 16.5, 8]}[name]
    assert all(math.isclose(a, b) for a, b in zip(tip, expected)), 'Update the runtime fuse anchor with the model'
    item_model = json.loads((ASSETS / f'models/item/{item}.json').read_text())
    assert item_model['parent'] == f'stardewcraft:entity/bomb/{name}'
    assert {'gui', 'ground', 'fixed', 'firstperson_righthand', 'firstperson_lefthand',
            'thirdperson_righthand', 'thirdperson_lefthand'} <= item_model['display'].keys()

for kind, count, size in [('fuse', 5, 4), ('burst', 8, 16), ('dust', 8, 16)]:
    frames = json.loads((ASSETS / f'particles/bomb_{kind}.json').read_text())['textures']
    assert len(frames) == count
    for i, texture in enumerate(frames):
        assert texture == f'stardewcraft:bomb_{kind}_{i}'
        assert png_size(ASSETS / f'textures/particle/bomb_{kind}_{i}.png') == (size, size)

print('Bomb assets OK: 3 native models/items, 2 closed socket hulls, native-density UVs, 21 particle frames.')
