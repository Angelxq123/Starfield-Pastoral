"""Build the placed Generic furniture and its static item/particle assets from one source."""
import io
import json
import sys
from pathlib import Path

from PIL import Image
from compile_native_npc import compile_model

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'assets-src/furniture/sebastian_computer/sebastian_computer.bbmodel'
ASSETS = (Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'build/generated/native-furniture') / 'assets/stardewcraft'


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')


def main():
    model = json.loads(SOURCE.read_text())
    # Reuse the Generic geometry/curve compiler; furniture has no character profile at runtime.
    profile = dict(intervalMin=4, intervalMax=6, durationMin=.9, durationMax=1.1,
                   doubleChance=0, groundOffset=0, walkStride=1)
    compiled, png = compile_model(model, profile, 'sebastian_computer',
                                  required_clips=['animation.sebastian_computer.use'])
    compiled['profile'] = None
    compiled['texture'] = 'stardewcraft:textures/block/furniture/sebastian_computer.png'
    write(ASSETS / 'furniture_native/sebastian_computer.json', compiled)
    texture = ASSETS / 'textures/block/furniture/sebastian_computer.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    texture.write_bytes(png)
    # A dedicated opaque material particle avoids sampling atlas gutters.
    atlas = Image.open(io.BytesIO(png)).convert('RGBA')
    shell = next(e for e in model['elements'] if e['name'] == 'crt_front_shell')
    u, v = shell['faces']['north']['uv'][:2]
    color = atlas.getpixel((int(u), int(v)))
    assert color[3] == 255
    Image.new('RGBA', (16, 16), color).save(texture.with_name('sebastian_computer_particle.png'))
    particle = 'stardewcraft:block/furniture/sebastian_computer_particle'
    empty = dict(textures=dict(particle=particle), elements=[])
    write(ASSETS / 'models/block/furniture/sebastian_computer_empty.json', empty)
    # The world uses animated Generic geometry. Only the inventory uses a baked bind pose.
    elements = []
    for cube in model['elements']:
        faces = {}
        for side, face in cube['faces'].items():
            if face.get('texture') is None:
                continue
            faces[side] = dict(uv=[n / 8 for n in face['uv']], texture='#0')
            if face.get('rotation', 0):
                faces[side]['rotation'] = face['rotation']
        elements.append(dict(name=cube['name'], **{'from':cube['from'], 'to':cube['to']}, faces=faces))
    item = dict(parent='minecraft:block/block', ambientocclusion=False,
                textures={'0':'stardewcraft:block/furniture/sebastian_computer', 'particle':particle},
                elements=elements,
                display={'gui':dict(rotation=[30,225,0], translation=[-2,-1,0], scale=[.45,.45,.45]),
                         'ground':dict(translation=[-2,2,-2], scale=[.3,.3,.3]),
                         'fixed':dict(rotation=[0,180,0], translation=[-2,0,-2], scale=[.45,.45,.45])})
    write(ASSETS / 'models/item/sebastian_computer.json', item)
    write(ASSETS / 'blockstates/sebastian_computer.json',
          {'multipart':[{'apply':{'model':'stardewcraft:block/furniture/sebastian_computer_empty'}}]})
    print(f'Sebastian computer: {len(compiled["bones"])} bones, {len(compiled["quads"])} quads')


if __name__ == '__main__':
    main()
