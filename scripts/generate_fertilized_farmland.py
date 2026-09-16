"""Original SDV-informed granules baked into existing soil. Only packaged assets are inputs."""
from pathlib import Path
from PIL import Image
import json

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/stardewcraft'
OUT = ASSETS / 'textures/block/terrain/fertilized'
NAMES = ['basic_fertilizer','quality_fertilizer','deluxe_fertilizer',
         'basic_retaining_soil','quality_retaining_soil','deluxe_retaining_soil',
         'speed_gro','deluxe_speed_gro','hyper_speed_gro']
# Shadow / body / small lit facet / secondary grain. No pixels sampled from SDV.
PALETTES = [
    ['503e30','725035','b38a51','906039'],
    ['545132','82863b','c5bd65','a29945'],
    ['425541','6f914e','baca77','999f43'],
    ['694335','a9713e','d6ad70','865236'],
    ['684834','b48d46','e3c486','936236'],
    ['663b40','ab6c59','dda793','c18568'],
    ['634b65','b784a2','d4a8ba','8f829e'],
    ['63516f','c58aac','ecd0ce','9693b9'],
    ['644572','b96cac','efbfd6','d99879'],
]
# Each tile has its own hand-composed placement and cluster silhouettes. 0 is a
# recessed/contact note, 1 the body, 2 a small light facet, 3 a second pigment.
# Organic crumbs stay angular; retaining aggregates join into unequal 2–3px
# broken clods; growth powders use separated flecks with occasional paired grains.
STROKES = [
    [(3,2,['31','0.']), (10,3,['1','0']), (7,5,['3']),
     (2,7,['1']), (11,7,['21','0.']), (5,9,['1','0']),
     (8,11,['31']), (12,12,['1']), (3,12,['3']), (9,8,['0']),
     (6,3,['0']), (7,13,['1'])],
    [(2,3,['21','10']), (8,2,['3']), (11,4,['2','1']),
     (6,6,['23','1.']), (3,8,['1']), (10,9,['21','0.']),
     (5,11,['31']), (12,12,['3']), (8,13,['1']), (12,7,['1']),
     (4,5,['3']), (7,9,['0'])],
    [(4,2,['23','10']), (11,2,['1']), (8,5,['21','1.']),
     (2,6,['3']), (11,8,['23','01']), (5,8,['21']),
     (3,11,['1','3']), (8,11,['32','10']), (12,13,['1']),
     (6,5,['3']), (9,3,['1']), (6,13,['3'])],
    [(3,3,['.2.','311','.00']), (9,2,['3']),
     (10,5,['21','30']), (5,7,['213','.10']),
     (2,10,['31']), (10,11,['.2.','311','.0.']),
     (6,12,['3']), (12,9,['1']), (7,4,['0'])],
    [(2,2,['23','10']), (8,3,['.2.','311','00.']),
     (11,6,['21','30']), (3,7,['.21','310','.0.']),
     (7,10,['23','10']), (11,12,['31']),
     (3,12,['1']), (6,5,['3']), (9,8,['0'])],
    [(4,2,['21','30']), (10,3,['.2.','311','.00']),
     (2,6,['3']), (6,7,['23','10']), (11,9,['21','10']),
     (3,11,['.23','311','.00']), (8,12,['3','0']),
     (9,6,['1']), (5,5,['3']), (12,12,['1'])],
    [(2,2,['1']), (7,3,['3']), (12,2,['1']), (4,5,['2']),
     (10,5,['1']), (2,8,['3']), (6,7,['1','0']),
     (12,8,['3']), (9,9,['1']), (4,11,['1']),
     (7,12,['3']), (12,12,['2']), (2,13,['1']), (10,13,['1'])],
    [(4,2,['2']), (10,2,['13']), (2,5,['3']), (7,5,['1','0']),
     (12,6,['2']), (4,8,['21']), (9,8,['3']),
     (2,11,['1']), (7,11,['2']), (11,11,['13']),
     (5,13,['3']), (9,13,['1']), (6,3,['1']), (11,4,['3'])],
    [(2,3,['21','0.']), (8,2,['3']), (12,3,['1']),
     (5,5,['3','1']), (10,6,['2']), (2,8,['3']),
     (7,8,['21','01']), (12,9,['3']), (4,11,['2']),
     (10,11,['31','0.']), (2,13,['1']), (7,13,['3']),
     (10,4,['1']), (4,3,['3'])],
]

def marks(index):
    result = {}
    def stamp(x,y,rows):
        for dy,row in enumerate(rows):
            for dx,c in enumerate(row):
                if c != '.':
                    assert (x+dx,y+dy) not in result
                    result[x+dx,y+dy]=int(c)
    for x,y,rows in STROKES[index]:stamp(x,y,rows)
    assert all(2<=x<=13 and 2<=y<=13 for x,y in result)
    return result

def material_palette(index,season,wet):
    base = [tuple(bytes.fromhex(c)) for c in PALETTES[index]]
    # Separate seasonal environment adjustments; identity hues remain legible.
    target,amount = {'spring':((150,120,80),0), 'summer':((192,162,99),.06),
                     'fall':((164,110,100),.10), 'winter':((153,183,204),.24)}[season]
    # Damp bodies deepen, while the tiny lit facets remain readable. Uniform
    # dimming washed every wet grain into the furrow in the first draft.
    return [tuple(round((c*(1-amount)+target[i]*amount)*([.82,.88,.96,.9][role] if wet else 1))
                  for i,c in enumerate(colour)) for role,colour in enumerate(base)]

def paint(base,index,season,wet,pot=False):
    image=base.copy().convert('RGBA');colours=material_palette(index,season,wet)
    for (x,y),role in marks(index).items():
        if pot:x,y=15-x,15-y
        soil=base.getpixel((x,y));colour=colours[role]
        # Contacts partially inherit the ground, while pigments retain their hue.
        # No dark outline around every crumb: only explicitly placed contact pixels.
        coverage = .60 if role == 0 else .94
        image.putpixel((x,y),tuple(round(colour[i]*coverage+soil[i]*(1-coverage)) for i in range(3))+(255,))
    return image

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def plane(name,height=15):
    return {'parent':'minecraft:block/block','textures':{'surface':'stardewcraft:block/terrain/fertilized/'+name},
            'elements':[{'from':[0,height,0],'to':[16,height,16],
                         'faces':{'up':{'texture':'#surface','uv':[0,0,16,16]}}}]}

def generate():
    weights=[0,.3,.65,1]
    for season in ['spring','summer','fall','winter']:
        soil=ASSETS/'textures/block/terrain/farmland'/season
        dry=Image.open(soil/'dry.png').convert('RGBA');wet=Image.open(soil/'wet.png').convert('RGBA')
        earth=Image.open(ASSETS/'textures/block/terrain'/('' if season=='spring' else season)/'yellow_earth_top.png').convert('RGBA')
        atlas=Image.new('RGBA',(144,320))
        for index,name in enumerate(NAMES):
            a=paint(dry,index,season,False);b=paint(wet,index,season,True)
            folder=OUT/season;folder.mkdir(parents=True,exist_ok=True)
            a.save(folder/(name+'_dry.png'));b.save(folder/(name+'_wet.png'))
            for row in range(20):
                moisture,edge=(divmod(row,4) if row<16 else (3,row-16))
                mixed=Image.new('RGBA',(16,16));pixels=[]
                for d,w,e in zip(a.getdata(),b.getdata(),earth.getdata()):
                    colour=tuple(round(d[i]*(1-weights[moisture])+w[i]*weights[moisture]) for i in range(3))
                    pixels.append(tuple(round(colour[i]*(1-weights[edge])+e[i]*weights[edge]) for i in range(3))+(255,))
                mixed.putdata(pixels);atlas.paste(mixed,(index*16,row*16))
        atlas.save(OUT/(season+'.png'))
        write(ASSETS/'models/block/terrain/fertilized'/(season+'.json'),plane(season))
    # Vanilla keeps its own base texture and geometry. Garden pots keep their 64px native atlas.
    for wet in [False,True]:
        state='wet' if wet else 'dry'
        vanilla=Image.open(OUT/'vanilla_base'/(state+'.png')).convert('RGBA')
        pot_name='garden_pot_watered' if wet else 'garden_pot'
        pot=Image.open(ASSETS/'textures/block/utility'/(pot_name+'.png')).convert('RGBA')
        for index,name in enumerate(NAMES):
            folder=OUT/'vanilla';folder.mkdir(exist_ok=True)
            paint(vanilla,index,'spring',wet).save(folder/(name+'_'+state+'.png'))
            write(ASSETS/'models/block/terrain/fertilized/vanilla'/(name+'_'+state+'.json'),
                  {'parent':'minecraft:block/template_farmland','textures':{'dirt':'minecraft:block/dirt',
                   'top':'stardewcraft:block/terrain/fertilized/vanilla/'+name+'_'+state}})
            folder=OUT/'pot';folder.mkdir(exist_ok=True)
            paint(pot,index,'spring',wet,True).save(folder/(name+'_'+state+'.png'))
            write(ASSETS/'models/block/terrain/fertilized/pot'/(name+'_'+state+'.json'),
                  {'parent':'stardewcraft:block/utility/'+pot_name,'textures':{
                      '2':'stardewcraft:block/terrain/fertilized/pot/'+name+'_'+state}})
    write(ASSETS/'fertilized_farmland_manifest.json',{'names':NAMES,'source_indices':list(range(9)),
          'palettes':PALETTES,'marks':[[[x,y,c] for (x,y),c in marks(i).items()] for i in range(9)],
          'atlas_size':[144,320],'tile_size':16,'rows':'dry: moisture*4+soil (0..15); wet: 16+soil (16..19)',
          'seasons':['spring','summer','fall','winter']})
    print('Generated 72 seasonal fertilizer surfaces, 720 blended cells, vanilla and garden-pot variants.')

if __name__=='__main__':generate()
