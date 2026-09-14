"""Build 16px overlays with precedence: dark grass > ordinary grass > dirt."""
from pathlib import Path
from PIL import Image
import json

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/stardewcraft'
TEXTURES = ASSETS/'textures/block/terrain'
MODELS = ASSETS/'models/block/terrain'

def canonical(mask):
    for corner in range(4):
        if mask & ((1 << corner) | (1 << ((corner+1)%4))):
            mask &= ~(16 << corner)
    return mask

def coverage(mask):
    # Native pixel tufts: a continuous two-pixel mat, grouped fingers, no equal sawteeth.
    edge = ['################','################','###.####..###.##',
            '.##..##....#...#','......#.........']
    corner = ['###.','##..','#...','....']
    points = set()
    def rotate(x,y,turns):
        for _ in range(turns):x,y=15-y,x
        return x,y
    for side in range(4):
        if mask & (1 << side):
            for y,row in enumerate(edge):
                for x,v in enumerate(row):
                    if v=='#':points.add(rotate(x,y,side))
    # Base corner is NE. Cardinal edges supersede a standalone diagonal corner.
    for c in range(4):
        if mask & (16 << c):
            for y,row in enumerate(corner):
                for x,v in enumerate(row):
                    if v=='#':points.add(rotate(15-x,y,c))
    return points

def generate():
    masks=sorted({canonical(i) for i in range(256)}-{0})
    for layer,prefix in enumerate(['grass','dark_grass']):
        grass=Image.open(TEXTURES/(prefix+'_top.png')).convert('RGBA')
        assert grass.size==(16,16)
        folder=prefix+'_connections'
        dest=TEXTURES/folder;dest.mkdir(parents=True,exist_ok=True)
        models=MODELS/folder;models.mkdir(parents=True,exist_ok=True)
        height=16.01+layer*0.01
        for mask in masks:
            im=Image.new('RGBA',(16,16))
            for xy in coverage(mask):im.putpixel(xy,grass.getpixel(xy))
            im.save(dest/(str(mask)+'.png'))
            model={'parent':'minecraft:block/block','render_type':'minecraft:cutout',
                   'textures':{'grass':f'stardewcraft:block/terrain/{folder}/{mask}'},
                   'elements':[{'from':[0,height,0],'to':[16,height,16],
                                'faces':{'up':{'uv':[0,0,16,16],'texture':'#grass','cullface':'up'}}}]}
            (models/(str(mask)+'.json')).write_text(json.dumps(model,indent=2))
    print(f'Generated {len(masks)} overlays per grass type; dark overlays render above ordinary grass.')

if __name__=='__main__':generate()
