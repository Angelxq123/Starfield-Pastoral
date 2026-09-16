"""Check shipped seasonal soil resources; no source-art or client dependency."""
from pathlib import Path
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / 'src/main/resources/assets/stardewcraft'
manifest = json.loads((A/'sandy_farmland_manifest.json').read_text())


def read(path):
    im = Image.open(path).convert('RGBA')
    assert im.getextrema()[3] == (255,255), f'Transparent soil: {path}'
    return im


tops = []
for season in manifest['seasons']:
    folder = A/f'textures/block/terrain/sandy_farmland/{season}'
    sand = read(A/f'textures/block/terrain/sand/{season}/sand.png')
    assert sand.size == (16,16)
    for state in ('dry','wet'):
        top = read(folder/f'{state}.png'); end = read(folder/f'{state}_end.png'); side = read(folder/f'{state}_side.png')
        assert top.size == end.size == side.size == (16,16)
        tops.append(top.tobytes())
        for u in range(16):
            assert top.getpixel((u,0)) == top.getpixel((u,15)) == top.getpixel((15-u,0))
            assert top.getpixel((0,u)) == top.getpixel((15,u)) == top.getpixel((0,15-u))
            assert end.getpixel((u,1)) == top.getpixel((u,0))
            assert side.getpixel((u,1)) == top.getpixel((0,u))
            assert end.getpixel((0,u)) == end.getpixel((15,u)) == side.getpixel((0,u)) == side.getpixel((15,u))
        for moisture in range(4 if state=='dry' else 1):
            for soil in range(4):
                blend = read(folder/f'blends/{state}_{moisture}_{soil}.png')
                assert blend.size == (16,16)
                if soil == 3: assert blend.tobytes() == sand.tobytes(), 'Grooves escaped into sand'
                if moisture == soil == 0: assert blend.tobytes() == top.tobytes()
        atlas = read(A/f'textures/block/terrain/fertilized/sandy/{season}.png')
        assert atlas.size == (144,320)
        for index, name in enumerate(manifest['fertilizers']):
            painted = read(A/f'textures/block/terrain/fertilized/sandy/{season}/{name}_{state}.png')
            assert painted.size == (16,16)
            base_row = 0 if state=='dry' else 16
            assert atlas.crop((index*16,base_row*16,index*16+16,base_row*16+16)).tobytes()==painted.tobytes()
            assert 0 < sum(a!=b for a,b in zip(top.getdata(),painted.getdata())) < 64
            for x in range(16):
                for y in range(16):
                    if x<2 or x>=14 or y<2 or y>=14: assert painted.getpixel((x,y))==top.getpixel((x,y)), 'Fertilizer crossed a seam'
            for row in (3,7,11,15,19):
                assert atlas.crop((index*16,row*16,index*16+16,row*16+16)).tobytes()==sand.tobytes()
assert len(set(tops))==8, 'Season or moisture state silently reused another surface'

models = []
for folder in ('sand','sandy_farmland'):
    models += list((A/f'models/block/terrain/{folder}').rglob('*.json'))
models += list((A/'models/block/terrain/fertilized/sandy').glob('*.json'))
for path in models:
    model=json.loads(path.read_text()); textures=model['textures']
    assert 'particle' in textures, f'Missing particle in {path}'
    for key,value in textures.items():
        seen=set()
        while value.startswith('#'):
            assert value not in seen;seen.add(value);value=textures[value[1:]]
        namespace,name=value.split(':')
        assert namespace=='stardewcraft' and (A/'textures'/f'{name}.png').exists(), (path,key,value)
    for element in model.get('elements',[]):
        assert element['to'][1]==15, f'Wrong farmland height in {path}'
        for face,definition in element['faces'].items():
            assert definition['texture'][1:] in textures
            assert definition['uv']==([0,1,16,16] if face in ('north','east','south','west') else [0,0,16,16])
for name in ('sand','sandy_farmland'):
    blockstate=json.loads((A/f'blockstates/{name}.json').read_text())
    assert len(blockstate['variants'])==(1 if name=='sand' else 8)
    for variant in blockstate['variants'].values(): assert (A/'models'/(variant['model'].split(':')[1]+'.json')).exists()
    item=json.loads((A/f'models/item/{name}.json').read_text())
    assert (A/'models'/(item['parent'].split(':')[1]+'.json')).exists()
    loot=json.loads((ROOT/f'src/main/resources/data/stardewcraft/loot_table/blocks/{name}.json').read_text())
    assert loot['pools'][0]['entries'][0]['name']=='stardewcraft:sand'
print(f'PASS: 4 sand seasons, 8 dry/wet surfaces, 80 blends, 72 fertilizers, {len(models)} model paths/particles, face seams, state/item models and sand drops.')
