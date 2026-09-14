"""Replace packaged terrain palettes, leaving live worlds untouched.
Requires nbtlib (pip install nbtlib). Default is read-only; --apply writes atomically
with an external backup. --verify fails if any source block remains in the assets.
"""
from pathlib import Path
from collections import Counter
import argparse, gzip, hashlib, io, json, math, shutil, struct, tempfile, zlib
import nbtlib
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
REPLACEMENTS={'minecraft:grass_block':'stardewcraft:grass_block','minecraft:dirt':'stardewcraft:dirt','stardewcraft:yellow_dirt':'stardewcraft:dirt'}
def parse(data):return nbtlib.File.parse(io.BytesIO(data))
def dump(tag):
 out=io.BytesIO();tag.write(out);return out.getvalue()
def rename(name):
 head,sep,tail=name.partition('[')
 return REPLACEMENTS.get(head,head)+(sep+tail if sep else '')
def decompress(data,kind):
 if kind==1:return gzip.decompress(data)
 if kind==2:return zlib.decompress(data)
 if kind==3:return data
 raise ValueError('Unsupported region compression '+str(kind))
def compress(data,kind):return gzip.compress(data,mtime=0) if kind==1 else zlib.compress(data) if kind==2 else data
def count_section(section,palette):
 if len(palette)==1:return Counter({0:4096})
 data=section['data'];bits=max(4,(len(palette)-1).bit_length());per=64//bits;mask=(1<<bits)-1
 assert len(data)==math.ceil(4096/per)
 counts=Counter()
 for i in range(4096):
  value=(int(data[i//per]) & ((1<<64)-1)) >> ((i%per)*bits)&mask
  assert value<len(palette);counts[value]+=1
 return counts

def region(path):
 original=path.read_bytes();result=bytearray(original);counts=Counter();changed_chunks=0
 for slot in range(1024):
  loc=int.from_bytes(original[slot*4:slot*4+4],'big');offset=loc>>8;sectors=loc&255
  if not offset:continue
  start=offset*4096;length=struct.unpack_from('>I',original,start)[0];kind=original[start+4]
  assert offset>=2 and length>0 and length+4<=sectors*4096 and start+sectors*4096<=len(original)
  raw=decompress(original[start+5:start+4+length],kind)
  if not any(k.encode() in raw for k in REPLACEMENTS):continue
  tag=parse(raw);changes=[];chunk_counts=Counter()
  assert 'sections' in tag, 'Expected modern region sections'
  for section in tag['sections']:
   states=section.get('block_states');palette=states.get('palette') if states is not None else None
   if palette is None:continue
   hits=[(i,item,str(item['Name'])) for i,item in enumerate(palette) if str(item['Name']) in REPLACEMENTS]
   if not hits:continue
   used=count_section(states,palette)
   for i,item,old in hits:
    item['Name']=nbtlib.String(REPLACEMENTS[old]);changes.append((item,old));chunk_counts[old]+=used[i]
  if not changes:continue
  new_raw=dump(tag)
  # Round-trip and restore only the changed names to prove all other NBT survived.
  verified=parse(new_raw)
  for section in verified['sections']:
   states=section.get('block_states')
   if states and 'palette' in states:count_section(states,states['palette'])
  for item,old in changes:item['Name']=nbtlib.String(old)
  assert dump(tag)==dump(parse(raw)), 'Unrelated chunk NBT was modified'
  data=compress(new_raw,kind);needed=math.ceil((len(data)+5)/4096);assert needed<=255
  if needed>sectors:
   offset=len(result)//4096;sectors=needed;result.extend(bytes(needed*4096))
   result[slot*4:slot*4+4]=((offset<<8)|needed).to_bytes(4,'big')
  start=offset*4096;encoded=struct.pack('>I',len(data)+1)+bytes([kind])+data
  result[start:start+sectors*4096]=encoded+bytes(sectors*4096-len(encoded))
  counts.update(chunk_counts);changed_chunks+=1
 return bytes(result),counts,changed_chunks

def varints(data):
 result=[];value=0;shift=0
 for byte in data:
  byte=int(byte)&255;value|=(byte&127)<<shift
  if byte&128:shift+=7;assert shift<35
  else:result.append(value);value=0;shift=0
 assert shift==0
 return result

def schematic(path):
 original=path.read_bytes();gz=original[:2]==b'\x1f\x8b';tag=parse(gzip.decompress(original) if gz else original)
 node=tag.get('Schematic',tag);blocks=node.get('Blocks',node);palette=blocks.get('Palette')
 if palette is None or not any(rename(n)!=n for n in palette):return original,Counter(),0
 key='Data' if 'Data' in blocks else 'BlockData'
 data=varints(blocks[key]);assert len(data)==int(node['Width'])*int(node['Height'])*int(node['Length'])
 old_names={int(i):str(n) for n,i in palette.items()};counts=Counter(old_names[i].split('[',1)[0] for i in data if i in old_names and old_names[i].split('[',1)[0] in REPLACEMENTS)
  # Keep sparse indices, including pre-existing references with no palette entry.
 # Only duplicate renamed states redirect to their first original index.
 new=nbtlib.Compound();mapping={i:i for i in set(data)}
 for old_id,name in sorted(old_names.items()):
  target=rename(name)
  if target not in new:new[target]=nbtlib.Int(old_id)
  mapping[old_id]=int(new[target])
 encoded=bytearray()
 for i in data:
  value=mapping[i]
  while value>=128:encoded.append((value&127)|128);value>>=7
  encoded.append(value)
 blocks['Palette']=new;blocks[key]=nbtlib.ByteArray(np.frombuffer(bytes(encoded), dtype=np.int8))
 if 'PaletteMax' in blocks:blocks['PaletteMax']=nbtlib.Int(max(map(int,new.values()))+1)
 payload=dump(tag);check=parse(payload);check_node=check.get('Schematic',check);check_blocks=check_node.get('Blocks',check_node)
 inverse={int(v):k for k,v in check_blocks['Palette'].items()};decoded=varints(check_blocks[key])
 assert all(inverse[j]==rename(old_names[i]) if i in old_names else j==i and j not in inverse for i,j in zip(data,decoded)) and len(decoded)==len(data)
 # Restore the three edited palette fields; all dimensions, block entities, biome
 # data, offsets and author metadata must serialize exactly as before.
 previous=parse(gzip.decompress(original) if gz else original);prev_node=previous.get('Schematic',previous);prev_blocks=prev_node.get('Blocks',prev_node)
 for k in ['Palette',key,'PaletteMax']:
  if k in prev_blocks:check_blocks[k]=prev_blocks[k]
 assert dump(check)==dump(previous)
 return gzip.compress(payload,mtime=0) if gz else payload,counts,1

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--apply',action='store_true');ap.add_argument('--verify',action='store_true');ap.add_argument('--backup',type=Path);args=ap.parse_args()
 backup=(args.backup or Path(tempfile.mkdtemp(prefix='stardew-terrain-map-backup-'))) if args.apply else None
 report={'mode':'apply' if args.apply else 'verify' if args.verify else 'dry-run','backup':str(backup) if backup else None,'files':[]};total=Counter()
 files=sorted((RES/'pregen').rglob('*.mca'))+sorted(RES.rglob('*.schem'))
 for index,p in enumerate(files):
  rel=p.relative_to(ROOT)
  source=backup/rel if backup and (backup/rel).exists() else p
  data,counts,changed=(region if p.suffix=='.mca' else schematic)(source);total.update(counts)
  if changed:
   rel=p.relative_to(ROOT);before=hashlib.sha256(source.read_bytes()).hexdigest()
   if args.apply:
    saved=backup/rel;saved.parent.mkdir(parents=True,exist_ok=True)
    if saved.exists():
     current=p.read_bytes()
     if current not in [saved.read_bytes(),data] and p.suffix=='.schem':
      def states(raw):
       tag=parse(gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw);node=tag.get('Schematic',tag);b=node.get('Blocks',node)
       names={int(i):str(n) for n,i in b['Palette'].items()}
       values=[names.get(i, ('missing',i)) for i in varints(b.get('Data',b.get('BlockData')))]
       for key in ['Palette','Data','BlockData','PaletteMax']:b.pop(key,None)
       return values,dump(tag)
      assert states(current)==states(data), 'Asset changed since interrupted conversion: '+str(p)
      data=current
     else:assert current in [saved.read_bytes(),data], 'Asset changed since interrupted conversion: '+str(p)
    else:shutil.copy2(p,saved)
    temporary=p.with_suffix(p.suffix+'.terrain-tmp');temporary.write_bytes(data);temporary.replace(p)
   report['files'].append({'path':str(rel),'blocks':dict(counts),'chunks_or_schematics':changed,'before':before,'after':hashlib.sha256(data).hexdigest()})
  if args.apply:(ROOT/'docs/terrain-map-replacement.json').write_text(json.dumps(report|{'replaced_blocks':dict(total),'scanned_files':index+1,'complete':False},ensure_ascii=False,indent=2)+'\n')
  if index%20==0:print(f'Checked {index+1}/{len(files)} assets',flush=True)
 if args.apply:
  manifest=RES/'pregen/stardew_valley/region_manifest.txt';saved=backup/manifest.relative_to(ROOT);saved.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(manifest,saved)
  lines=[]
  for line in manifest.read_text().splitlines():
   if line.startswith('copy '):
    _,name,*_=line.split();f=manifest.parent/'region'/name;line=f'copy {name} {f.stat().st_size} {hashlib.sha256(f.read_bytes()).hexdigest()}'
   lines.append(line)
  manifest.write_text('\n'.join(lines)+'\n')
 report['complete']=True;report['scanned_files']=len(files);report['replaced_blocks']=dict(total)
 dest=ROOT/'docs/terrain-map-replacement.json' if args.apply else None
 if dest:dest.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({k:v for k,v in report.items() if k!='files'},ensure_ascii=False),flush=True)
 print(f'Changed assets: {len(report["files"])}',flush=True)
 if args.verify:
  assert not report['files'],'Old terrain remains in packaged assets'
  manifest=RES/'pregen/stardew_valley/region_manifest.txt';checked=0
  for line in manifest.read_text().splitlines():
   if not line or line.startswith('#'):continue
   fields=line.split();name=fields[1];_,rx,rz,_=name.split('.')
   assert not (int(rx)>=36 and int(rz)>=36),'Manifest touches player farm/interior: '+name
   if fields[0]=='copy':
    data=(manifest.parent/'region'/name).read_bytes()
    assert len(data)==int(fields[2]) and hashlib.sha256(data).hexdigest()==fields[3], 'Stale manifest: '+name
    checked+=1
  print(f'PASS: {checked} manifest copy hashes/sizes and protected farm/interior exclusion')
if __name__=='__main__':main()
