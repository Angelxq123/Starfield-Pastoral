"""Package native 16px blend surfaces from production textures; no study/source files needed."""
from pathlib import Path
from PIL import Image
import json
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/stardewcraft'
WEIGHTS=[0,.3,.65,1]
def blend(a,b,t):return tuple(round(a[i]*(1-t)+b[i]*t) for i in range(3))+(255,)
def generate():
 for season in ['spring','summer','fall','winter']:
  path=Path('block/terrain/farmland')/season
  tex=ASSETS/'textures'/path;model=ASSETS/'models'/path
  dry=Image.open(tex/'dry.png').convert('RGBA');wet=Image.open(tex/'wet.png').convert('RGBA')
  earth=Image.open(ASSETS/'textures/block/terrain'/('' if season=='spring' else season)/'yellow_earth_top.png').convert('RGBA')
  (tex/'blends').mkdir(exist_ok=True);(model/'blends').mkdir(exist_ok=True)
  for state in ['dry','wet']:
   for moisture in range(4 if state=='dry' else 1):
    for soil in range(4):
     image=Image.new('RGBA',(16,16))
     image.putdata([blend(blend(a,b,WEIGHTS[moisture]) if state=='dry' else b,c,WEIGHTS[soil]) for a,b,c in zip(dry.getdata(),wet.getdata(),earth.getdata())])
     name=f'{state}_{moisture}_{soil}';image.save(tex/'blends'/(name+'.png'))
     m={'parent':'minecraft:block/block','textures':{'surface':f'stardewcraft:{path}/blends/{name}'},'elements':[{'from':[0,15,0],'to':[16,15,16],'faces':{'up':{'uv':[0,0,16,16],'texture':'#surface'}}}]}
     (model/'blends'/(name+'.json')).write_text(json.dumps(m,indent=2)+'\n')
 print('Generated 80 native farmland blend surfaces across four seasons.')
if __name__=='__main__':generate()
