"""Build connected/fertilized sandy soil from tracked production textures only."""
from pathlib import Path
from PIL import Image
import json
from generate_fertilized_farmland import NAMES, paint

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
WEIGHTS = [0, .3, .65, 1]


def mix(a, b, t):
    return tuple(round(a[i] * (1-t) + b[i] * t) for i in range(3)) + (255,)


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')


def plane(texture):
    return {'parent': 'minecraft:block/block',
            'textures': {'surface': 'stardewcraft:' + texture, 'particle': '#surface'},
            'elements': [{'from': [0,15,0], 'to': [16,15,16],
                          'faces': {'up': {'texture': '#surface', 'uv': [0,0,16,16]}}}]}


def generate():
    for season in ('spring','summer','fall','winter'):
        path = Path('block/terrain/sandy_farmland') / season
        tex = ASSETS / 'textures' / path
        dry = Image.open(tex / 'dry.png').convert('RGBA')
        wet = Image.open(tex / 'wet.png').convert('RGBA')
        sand = Image.open(ASSETS / f'textures/block/terrain/sand/{season}/sand.png').convert('RGBA')
        (tex / 'blends').mkdir(exist_ok=True)
        for state in ('dry','wet'):
            for moisture in range(4 if state == 'dry' else 1):
                for soil in range(4):
                    name = f'{state}_{moisture}_{soil}'
                    im = Image.new('RGBA', (16,16))
                    im.putdata([mix(mix(a,b,WEIGHTS[moisture]) if state=='dry' else b,c,WEIGHTS[soil])
                                for a,b,c in zip(dry.getdata(),wet.getdata(),sand.getdata())])
                    im.save(tex / 'blends' / (name+'.png'))
                    write(ASSETS / 'models' / path / 'blends' / (name+'.json'), plane(f'{path}/blends/{name}'))
        atlas = Image.new('RGBA', (16*len(NAMES),320))
        folder = ASSETS / f'textures/block/terrain/fertilized/sandy/{season}'
        folder.mkdir(parents=True, exist_ok=True)
        for index, name in enumerate(NAMES):
            a = paint(dry,index,season,False)
            b = paint(wet,index,season,True)
            a.save(folder/(name+'_dry.png')); b.save(folder/(name+'_wet.png'))
            for row in range(20):
                moisture,soil = divmod(row,4) if row<16 else (3,row-16)
                im = Image.new('RGBA',(16,16))
                im.putdata([mix(mix(d,w,WEIGHTS[moisture]),s,WEIGHTS[soil])
                            for d,w,s in zip(a.getdata(),b.getdata(),sand.getdata())])
                atlas.paste(im,(index*16,row*16))
        atlas.save(folder.with_suffix('.png'))
        write(ASSETS / f'models/block/terrain/fertilized/sandy/{season}.json',
              plane(f'block/terrain/fertilized/sandy/{season}'))
    write(ASSETS/'sandy_farmland_manifest.json', {
        'seasons':['spring','summer','fall','winter'], 'fertilizers':NAMES,
        'tile_size':16,'top_height':15, 'blend_weights':WEIGHTS,
        'priority':[['dark_grass_block'],['grass_block'],['farmland:wet','sandy_farmland:wet'],
                    ['farmland:dry','sandy_farmland:dry'],['dirt'],['sand'],['cliff']],
    })
    print('Generated 80 sandy blend surfaces, 72 fertilizer surfaces and 4 fertilizer atlases.')


if __name__ == '__main__':
    generate()
