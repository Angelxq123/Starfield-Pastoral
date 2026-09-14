"""Verify shipped cave resources without authoring files, Minecraft, or third-party packages."""
from pathlib import Path
import gzip,io,json,struct,hashlib
ROOT=Path(__file__).resolve().parents[1]

def nbt(path):
    stream=io.BytesIO(gzip.decompress(path.read_bytes()))
    def number(fmt):return struct.unpack('>'+fmt,stream.read(struct.calcsize('>'+fmt)))[0]
    def string():return stream.read(number('H')).decode('utf8')
    def value(t):
        if t in (1,2,3,4,5,6):return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        if t==7:return stream.read(number('i'))
        if t==8:return string()
        if t==9:
            child=number('B');return [value(child) for _ in range(number('i'))]
        if t==10:
            out={}
            while (child:=number('B')):
                name=string();out[name]=value(child)
            return out
        if t in (11,12):return [number('i' if t==11 else 'q') for _ in range(number('i'))]
        raise ValueError(t)
    kind=number('B');string();root=value(kind);assert not stream.read();return root

def main():
    path=ROOT/'src/main/resources/data/stardewcraft/structure/farm_layouts/cave.nbt'
    meta=json.loads((ROOT/'src/main/resources/data/stardewcraft/farm_cave_layouts/cave.json').read_text())
    cave=nbt(path);assert cave['size']==meta['size']==[16,13,18]
    assert hashlib.sha256(path.read_bytes()).hexdigest()==meta['structure_sha256']
    blocks={tuple(b['pos']):cave['palette'][b['state']] for b in cave['blocks']}
    assert len(blocks)==len(cave['blocks'])==3744
    cells={tuple(c['tile']):c for c in meta['cells']}
    allowed={p for p,c in cells.items() if not c['source_gids']['Buildings'] and c['source_gids']['Front']!=78}
    reached={tuple(meta['exit_tile'])};queue=list(reached)
    for x,z in queue:
        for p in [(x-1,z),(x+1,z),(x,z-1),(x,z+1)]:
            if p in allowed and p not in reached:reached.add(p);queue.append(p)
    assert len(reached)==59
    for tile,c in cells.items():
        assert c['ground_open']==(tile in reached)
        if not c['ground_open']:continue
        x,y,z=c['block'];assert [x,y,z]==[tile[0]+2,3,tile[1]+2]
        assert blocks[x,y-1,z]['Name']=='stardewcraft:mine_earth_soil'
        assert blocks[x,y,z]['Name']=='minecraft:air'
        assert blocks[x,y+1,z]['Name'] in ('minecraft:air','stardewcraft:mine_lamp')
    assert meta['spawn']==[10.5,3,13.5] and meta['exit_block']==[10,3,14]
    assert blocks[10,3,13]['Name']=='minecraft:air'
    # The entrance's wall-mounted lamp has no collision; it occupies the head cell.
    assert blocks[10,4,13]['Name'] in ('minecraft:air','stardewcraft:mine_lamp')
    assert all(blocks[10,y,14]['Name']=='minecraft:air' for y in (3,4))
    assert meta['mushroom_box_anchors']==[[6,3,7],[8,3,7],[10,3,7],[6,3,9],[8,3,9],[10,3,9]]
    assert meta['dehydrator_anchor']==[12,3,7]
    for p in meta['mushroom_box_anchors']+[meta['dehydrator_anchor']]:assert blocks[tuple(p)]['Name']=='minecraft:air'
    lamps={p:s for p,s in blocks.items() if s['Name']=='stardewcraft:mine_lamp'};assert len(lamps)==4
    support={'north':(0,1),'south':(0,-1),'east':(-1,0),'west':(1,0)}
    for (x,y,z),s in lamps.items():
        dx,dz=support[s['Properties']['facing']]
        assert s['Properties']['lit']=='true' and blocks[x+dx,y,z+dz]['Name']=='stardewcraft:mine_earth_wall'
    old=nbt(path.with_name('cave_legacy.nbt'));assert old['size']==[9,6,10] and len(old['blocks'])==540
    print('PASS: 3744 blocks; 59 source-aligned reachable cells; safe spawn/exit; 7 machine anchors; 4 supported lanterns; legacy baseline.')
if __name__=='__main__':main()
