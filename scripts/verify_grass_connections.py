"""Headless tests of production precedence code and the packaged native overlays."""
from pathlib import Path
from tempfile import TemporaryDirectory
from PIL import Image
import json
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'src/main/java/com/stardew/craft/client/model/terrain/GrassConnectionMask.java'
ASSETS = ROOT/'src/main/resources/assets/stardewcraft'

HARNESS = '''
import com.stardew.craft.client.model.terrain.GrassConnectionMask;
import com.stardew.craft.block.terrain.TerrainVariantWeights;
import com.stardew.craft.client.model.terrain.TerrainSeasonTextures;
import java.util.HashSet;
public class VerifyGrass {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        int[] grassCounts = new int[3], dirtCounts = new int[5];
        for (int roll=0;roll<100;roll++) {
            grassCounts[TerrainVariantWeights.grass(roll)]++;
            dirtCounts[TerrainVariantWeights.dirt(roll)]++;
        }
        check(java.util.Arrays.equals(grassCounts, new int[]{88,8,4}));
        check(java.util.Arrays.equals(dirtCounts, new int[]{89,1,3,4,3}));
        check(TerrainSeasonTextures.currentTextureSet()==0);
        for (int season : new int[]{0,1,2,3,0,1,-1}) {
            check(TerrainSeasonTextures.updateSeason(season));
            check(!TerrainSeasonTextures.updateSeason(season)); // Time-of-day sync cannot rebuild every packet.
            check(TerrainSeasonTextures.currentTextureSet()==(season>=0 && season<4 ? season : 0));
            for (String block : new String[]{"grass_block","dirt","dark_grass_block","farmland"}) {
                int count = block.equals("farmland") ? 8 : block.equals("dirt") ? 5 : block.equals("grass_block") ? 3 : 1;
                for (int variant=0;variant<count;variant++) {
                    for (boolean snowy : new boolean[]{false,true}) {
                        if ((block.equals("dirt") || block.equals("farmland")) && snowy) continue;
                        String spring = TerrainSeasonTextures.modelPath(0,block,variant,snowy);
                        String selected = TerrainSeasonTextures.modelPath(season,block,variant,snowy);
                        String directory = switch(season) { case 1 -> "summer/"; case 2 -> "fall/"; case 3 -> "winter/"; default -> ""; };
                        String expected = block.equals("farmland")
                                ? spring.replace("farmland/spring/", "farmland/" + (directory.isEmpty() ? "spring/" : directory))
                                : spring.replace("terrain/", "terrain/"+directory);
                        check(selected.equals(expected));
                        System.out.println("MODEL|"+selected);
                    }
                }
            }
        }
        check(TerrainSeasonTextures.modelPath(1,"dirt",2,false).endsWith("/dirt_impression"));
        check(TerrainSeasonTextures.modelPath(3,"dirt",3,false).endsWith("/dirt_marks"));
        check(TerrainSeasonTextures.modelPath(1,"grass_block",2,false).endsWith("/grass_flowers"));
        var masks = new HashSet<Integer>();
        for (int m=0;m<256;m++) {
            int c=GrassConnectionMask.canonical(m);
            masks.add(c);
            check(GrassConnectionMask.canonical(c)==c);
            check((c&15)==(m&15));
            System.out.println(m+":"+c);
        }
        check(masks.size()==47);
        check(GrassConnectionMask.canonical(17)==1); // N edge supersedes NE corner.
        check(GrassConnectionMask.canonical(32)==32); // Diagonal-only contact survives.
        check(GrassConnectionMask.canonical(255)==15);
        check(GrassConnectionMask.connections(0,1,2)==513); // Both reach dirt.
        check(GrassConnectionMask.connections(1,1,2)==512); // Only dark reaches grass.
        check(GrassConnectionMask.connections(2,255,255)==0);
        check(GrassConnectionMask.connections(-1,255,255)==0); // Unrelated block.
        for(int arrangement=0;arrangement<6561;arrangement++) {
            int n=arrangement, grass=0, dark=0;
            for(int i=0;i<8;i++) {
                int type=n%3; n/=3;
                if(type==1)grass|=1<<i;
                if(type==2)dark|=1<<i;
            }
            int earth=GrassConnectionMask.connections(0,grass,dark);
            int ordinary=GrassConnectionMask.connections(1,grass,dark);
            check((earth&255)==GrassConnectionMask.canonical(grass));
            check((earth>>>8)==GrassConnectionMask.canonical(dark));
            check((ordinary&255)==0 && (ordinary>>>8)==(earth>>>8));
            check(GrassConnectionMask.connections(2,grass,dark)==0);
        }
    }
}
'''

with TemporaryDirectory(prefix='grass-precedence-') as directory:
    p=Path(directory);(p/'VerifyGrass.java').write_text(HARNESS)
    subprocess.run(['javac','-d',directory,str(JAVA),str(ROOT/'src/main/java/com/stardew/craft/block/terrain/TerrainVariantWeights.java'),str(JAVA.parent/'TerrainSeasonTextures.java'),str(p/'VerifyGrass.java')],check=True)
    result=subprocess.run(['java','-cp',directory,'VerifyGrass'],check=True,capture_output=True,text=True)
    normalized={int(a):int(b) for a,b in (line.split(':') for line in result.stdout.splitlines() if not line.startswith('MODEL|'))}
    seasonal_models={line.split('|',1)[1] for line in result.stdout.splitlines() if line.startswith('MODEL|')}

cases=set(normalized.values())-{0}
for layer,prefix in enumerate(['grass','dark_grass']):
    folder=prefix+'_connections'
    textures=ASSETS/'textures/block/terrain'/folder
    assert {int(p.stem) for p in textures.glob('*.png')}==cases
    source=Image.open(ASSETS/'textures/block/terrain'/(prefix+'_top.png')).convert('RGBA')
    for mask in cases:
        image=Image.open(textures/(str(mask)+'.png')).convert('RGBA')
        assert image.size==(16,16)
        assert image.getpixel((8,8))[3]==0  # Preserve the destination's interior material.
        for y in range(16):
            for x in range(16):
                pixel=image.getpixel((x,y))
                assert pixel[3] in (0,255)
                if pixel[3]:assert pixel==source.getpixel((x,y))
        model=json.loads((ASSETS/'models/block/terrain'/folder/(str(mask)+'.json')).read_text())
        element=model['elements'][0]
        assert set(element['faces'])=={'up'}
        assert element['faces']['up']['uv']==[0,0,16,16]
        assert model['render_type']=='minecraft:cutout'
        assert abs(element['from'][1]-(16.01+layer*0.01))<1e-8
    for mask,edge in [(1,[(x,0) for x in range(16)]),(2,[(15,y) for y in range(16)]),
                      (4,[(x,15) for x in range(16)]),(8,[(0,y) for y in range(16)])]:
        image=Image.open(textures/(str(mask)+'.png'))
        assert all(image.getpixel(p)[3]==255 for p in edge)
    for mask,point in [(16,(15,0)),(32,(15,15)),(64,(0,15)),(128,(0,0))]:
        assert Image.open(textures/(str(mask)+'.png')).getpixel(point)[3]==255

light=Image.open(ASSETS/'textures/block/terrain/grass_side.png').convert('RGBA')
dark=Image.open(ASSETS/'textures/block/terrain/dark_grass_side.png').convert('RGBA')
assert light.crop((0,8,16,16)).tobytes()==dark.crop((0,8,16,16)).tobytes()

# Validate the public IDs through their resource graphs, including snowy variants and drops.
def check_model(identifier, seen=None):
    if not identifier.startswith('stardewcraft:'):return
    seen=set() if seen is None else seen
    if identifier in seen:return
    seen.add(identifier)
    model=json.loads((ASSETS/'models'/(identifier.split(':',1)[1]+'.json')).read_text())
    if 'parent' in model:check_model(model['parent'],seen)
    for texture in model.get('textures',{}).values():
        if texture.startswith('stardewcraft:'):
            assert (ASSETS/'textures'/(texture.split(':',1)[1]+'.png')).is_file(),texture

for name in ['dirt','grass_block','dark_grass_block']:
    state=json.loads((ASSETS/'blockstates'/(name+'.json')).read_text())
    for variant in state['variants'].values():check_model(variant['model'])
    check_model('stardewcraft:item/'+name)
    loot=ROOT/'src/main/resources/data/stardewcraft/loot_table/blocks'/(name+'.json')
    text=loot.read_text()
    drops=json.loads(text)
    assert {entry['name'] for pool in drops['pools'] for entry in pool['entries']} == {'stardewcraft:'+name}
    assert 'silk_touch' not in text, 'Terrain self-drops must not require Silk Touch'
    for old in ['building_'+name]:
        assert not (ASSETS/'blockstates'/(old+'.json')).exists()
        assert not (ASSETS/'models/item'/(old+'.json')).exists()
        assert not (loot.parent/(old+'.json')).exists()
registry_files=[ROOT/'src/main/java/com/stardew/craft/block/ModBlocks.java',
                ROOT/'src/main/java/com/stardew/craft/item/ModItems.java']
for path in registry_files:
    text=path.read_text()
    for name in ['dirt','grass_block','dark_grass_block']:
        assert '.register("'+name+'"' in text
        assert '"building_'+name+'"' not in text  # No registrations or compatibility aliases.
renderer=(JAVA.parent/'GrassTransitionModels.java').read_text()
assert 'ModBlocks.DIRT' in renderer and 'ModBlocks.YELLOW_DIRT' not in renderer
print('PASS: 19,683 precedence cases, 256 masks, 92 native overlays, all edge/corner directions, dark-above-grass depth, unchanged lower soil.')
print('PASS: three independent IDs, complete model/texture/drop references, no old registrations or aliases.')

# Every saved variant has exactly one model, including snowy grass. No random
# model arrays: placement makes the persistent choice on the server.
for name, count in [('grass_block',3),('dirt',5)]:
    states=json.loads((ASSETS/'blockstates'/(name+'.json')).read_text())['variants']
    expected={f'snowy={snow},variant={v}' for snow in ['false','true'] for v in range(count)} if name=='grass_block' else {f'variant={v}' for v in range(count)}
    assert set(states)==expected
    item=json.loads((ASSETS/'models/item'/(name+'.json')).read_text())
    assert 'overrides' not in item  # Runtime seasonal resolver replaces static spring-only predicates.
assert set(json.loads((ASSETS/'blockstates/dark_grass_block.json').read_text())['variants'])=={'snowy=false','snowy=true'}
terrain=ASSETS/'textures/block/terrain'
for name in ['grass_blades','grass_flowers','dirt_tuft','dirt_impression','dirt_marks','dirt_stones']:
    im=Image.open(terrain/'spring'/(name+'.png')).convert('RGBA')
    base=Image.open(terrain/('grass_top.png' if name.startswith('grass') else 'yellow_earth_top.png')).convert('RGBA')
    assert im.size==((16,32) if name=='grass_flowers' else (16,16))
    for frame in range(im.height//16):
        assert all(im.getpixel((x,y+frame*16))==base.getpixel((x,y)) for y in range(16) for x in range(16) if x<2 or x>13 or y<2 or y>13)
meta=json.loads((terrain/'spring/grass_flowers.png.mcmeta').read_text())['animation']
assert meta=={'frametime':19,'width':16,'height':16,'frames':[0,1],'interpolate':False}
# Regression: the four corners must join the surrounding midtone, rather than
# becoming the isolated four-pixel island responsible for the square pit.
im=Image.open(terrain/'yellow_earth_top.png').convert('RGB')
connected={(0,0)}; todo=[(0,0)]
while todo:
    x,y=todo.pop()
    for dx,dy in [(1,0),(-1,0),(0,1),(0,-1)]:
        pos=((x+dx)%16,(y+dy)%16)
        if pos not in connected and im.getpixel(pos)[0]>=161:
            connected.add(pos);todo.append(pos)
assert len(connected)>4
print('PASS: exact 100-slot placement weights; all 11 variant/snow models and fixed-copy item models; native two-frame animation; variant borders; no isolated four-corner soil pit.')

# Validate the actual paths emitted by the production seasonal selector.
for path in seasonal_models:check_model('stardewcraft:'+path)
assert len(seasonal_models)==60  # (6 grass + 5 dirt + 2 dark/snow) x 2 texture sets.
summer=terrain/'summer'
for prefix in ['grass','dark_grass']:
    source=Image.open(summer/(prefix+'_top.png')).convert('RGBA')
    for mask in cases:
        name=prefix+'_connections/'+str(mask)
        im=Image.open(summer/(name+'.png')).convert('RGBA')
        spring=Image.open(terrain/(name+'.png')).convert('RGBA')
        assert im.size==(16,16) and im.getchannel('A').tobytes()==spring.getchannel('A').tobytes()
        assert all(im.getpixel((x,y))==source.getpixel((x,y)) for y in range(16) for x in range(16) if im.getpixel((x,y))[3])
        check_model('stardewcraft:block/terrain/summer/'+name)
        summer_model=json.loads((ASSETS/'models/block/terrain/summer'/(name+'.json')).read_text())
        spring_model=json.loads((ASSETS/'models/block/terrain'/(name+'.json')).read_text())
        assert summer_model['elements']==spring_model['elements']
for name in ['grass_blades','grass_flowers','dirt_tuft','dirt_impression','dirt_marks','dirt_stones']:
    im=Image.open(summer/(name+'.png')).convert('RGBA')
    base=Image.open(summer/('grass_top.png' if name.startswith('grass') else 'yellow_earth_top.png')).convert('RGBA')
    assert im.size==((16,32) if name=='grass_flowers' else (16,16))
    for frame in range(im.height//16):
        assert all(im.getpixel((x,y+frame*16))==base.getpixel((x,y)) for y in range(16) for x in range(16) if x<2 or x>13 or y<2 or y>13)
assert json.loads((summer/'grass_flowers.png.mcmeta').read_text())['animation']==meta
client=(ROOT/'src/main/java/com/stardew/craft/StardewCraftClient.java').read_text()
assert 'resolveYellowDirtColor' not in client and 'STARDEW_YELLOW_DIRT_WINTER' not in client
assert '}, Blocks.GRASS_BLOCK);' not in client
assert 'Blocks.SHORT_GRASS, Blocks.FERN' in client and 'resolveSeasonalLeafColor' in client
print('PASS: spring/summer/fall/winter/logout selection, stable variant paths, one refresh per calendar change, spring/summer model and overlay regression checks, summer animation and borders; legacy ground tint hooks removed.')

# Autumn animation stays two-frame; winter flower slot is intentionally snow-covered and static.
for season in ['fall','winter']:
    folder=terrain/season
    for prefix in ['grass','dark_grass']:
        for mask in cases:
            im=Image.open(folder/(prefix+'_connections')/(str(mask)+'.png')).convert('RGBA')
            assert im.size==(16,16) and all(a in [0,255] for a in im.getchannel('A').getdata())
            check_model('stardewcraft:block/terrain/'+season+'/'+prefix+'_connections/'+str(mask))
    for name in ['grass_blades','grass_flowers','dirt_tuft','dirt_impression','dirt_marks','dirt_stones']:
        im=Image.open(folder/(name+'.png')).convert('RGBA')
        expected=(16,32) if season=='fall' and name=='grass_flowers' else (16,16)
        assert im.size==expected
        base=Image.open(folder/('grass_top.png' if name.startswith('grass') else 'yellow_earth_top.png')).convert('RGBA')
        for frame in range(im.height//16):
            assert all(im.getpixel((x,y+frame*16))==base.getpixel((x,y)) for y in range(16) for x in range(16) if x<2 or x>13 or y<2 or y>13)
assert json.loads((terrain/'fall/grass_flowers.png.mcmeta').read_text())['animation']==meta
assert not (terrain/'winter/grass_flowers.png.mcmeta').exists()
assert Image.open(terrain/'winter/grass_flowers.png').tobytes()==Image.open(terrain/'winter/grass_top.png').tobytes()

from generate_farmland_blends import blend,WEIGHTS
for season in ['spring','summer','fall','winter']:
    folder=terrain/'farmland'/season
    dry=Image.open(folder/'dry.png').convert('RGBA');wet=Image.open(folder/'wet.png').convert('RGBA')
    dirt=Image.open(terrain/('' if season=='spring' else season)/'yellow_earth_top.png').convert('RGBA')
    for state in ['dry','wet']:
        check_model('stardewcraft:block/terrain/farmland/'+season+'/'+state)
        m=json.loads((ASSETS/'models/block/terrain/farmland'/season/(state+'.json')).read_text())
        assert m['elements'][0]['from']==[0,0,0] and m['elements'][0]['to']==[16,15,16]
        assert 'cullface' not in m['elements'][0]['faces']['up']
        for side in ['north','south','east','west']:assert m['elements'][0]['faces'][side]['uv']==[0,1,16,16]
        for moisture in range(4 if state=='dry' else 1):
            for soil in range(4):
                name=f'blends/{state}_{moisture}_{soil}'
                im=Image.open(folder/(name+'.png')).convert('RGBA')
                expected=[blend(blend(a,b,WEIGHTS[moisture]) if state=='dry' else b,c,WEIGHTS[soil]) for a,b,c in zip(dry.getdata(),wet.getdata(),dirt.getdata())]
                assert list(im.getdata())==expected
                check_model('stardewcraft:block/terrain/farmland/'+season+'/'+name)
                surface=json.loads((ASSETS/'models/block/terrain/farmland'/season/(name+'.json')).read_text())['elements'][0]
                assert surface['from']==[0,15,0] and surface['to']==[16,15,16]
                assert 'cullface' not in surface['faces']['up']
check_model('stardewcraft:item/farmland')
assert len(json.loads((ASSETS/'blockstates/farmland.json').read_text())['variants'])==8
print('PASS: 60 seasonal state models, 368 grass overlays, 80 farmland blend surfaces; four seasons, animation, native recessed farmland geometry and three-way edge composition.')
