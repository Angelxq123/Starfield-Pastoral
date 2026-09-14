"""Lossless RGBA PNG atlas packing for Generic projects with several materials.
No external imaging dependency: the build uses only Python's standard library.
"""
import base64, struct, zlib

def png_data(texture):
 source=texture['source']
 if not source.startswith('data:image/png;base64,'):raise ValueError('Texture must be embedded PNG')
 data=base64.b64decode(source.split(',',1)[1],validate=True)
 if data[:8]!=b'\x89PNG\r\n\x1a\n':raise ValueError('Invalid PNG')
 return data

def rgba(data):
 w,h,depth,color,compression,filtering,interlace=struct.unpack('>IIBBBBB',data[16:29])
 if (depth,color,compression,filtering,interlace)!=(8,6,0,0,0):raise ValueError('Multi-material atlas requires non-interlaced RGBA8 PNG')
 compressed=b'';offset=8
 while offset<len(data):
  size=struct.unpack('>I',data[offset:offset+4])[0];kind=data[offset+4:offset+8];payload=data[offset+8:offset+8+size]
  if zlib.crc32(kind+payload)!=struct.unpack('>I',data[offset+8+size:offset+12+size])[0]:raise ValueError('PNG checksum')
  if kind==b'IDAT':compressed+=payload
  offset+=12+size
 raw=zlib.decompress(compressed);stride=w*4
 if len(raw)!=h*(stride+1):raise ValueError('PNG scanline size')
 previous=bytearray(stride);rows=[]
 for y in range(h):
  method=raw[y*(stride+1)];row=bytearray(raw[y*(stride+1)+1:(y+1)*(stride+1)])
  if method>4:raise ValueError('PNG filter')
  for x in range(stride):
   a=row[x-4] if x>=4 else 0;b=previous[x];c=previous[x-4] if x>=4 else 0
   estimate=a+b-c;distances=[abs(estimate-v) for v in (a,b,c)]
   predictor=(0,a,b,(a+b)//2,(a,b,c)[distances.index(min(distances))])[method]
   row[x]=(row[x]+predictor)&255
  rows.append(row);previous=row
 return w,h,rows

def pack(textures,resolution):
 sources=[png_data(t) for t in textures]
 if not sources:raise ValueError('Missing texture')
 sizes=[struct.unpack('>II',data[16:24]) for data in sources]
 for t,(w,h) in zip(textures,sizes):
  if t.get('render_mode','default')!='default' or t.get('render_sides','auto') not in ('auto','double','front'):raise ValueError('Unsupported material')
  if (t.get('uv_width',resolution['width']),t.get('uv_height',resolution['height']))!=(w,h):raise ValueError('Atlas bitmap and UV resolution differ')
 if len(textures)==1:
  if sizes[0]!=(resolution['width'],resolution['height']):raise ValueError('Atlas bitmap and UV resolution differ')
  return sources[0],sizes[0][0],sizes[0][1],[(0,0,*sizes[0])]
 width=1<<(max(w for w,h in sizes)-1).bit_length();height=1<<(sum(h for w,h in sizes)-1).bit_length()
 rows=[bytearray(width*4) for _ in range(height)];placements=[];top=0
 for source in sources:
  w,h,pixels=rgba(source);placements.append((0,top,w,h))
  for y,row in enumerate(pixels):rows[top+y][:w*4]=row
  top+=h
 def chunk(kind,payload):return struct.pack('>I',len(payload))+kind+payload+struct.pack('>I',zlib.crc32(kind+payload))
 png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(b'\0'+r for r in rows),9))+chunk(b'IEND',b'')
 return png,width,height,placements
