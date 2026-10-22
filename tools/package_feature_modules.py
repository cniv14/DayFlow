"""Build small signed code-only update packages for the embedded Android features."""
import argparse,base64,hashlib,json,subprocess,zipfile
from pathlib import Path
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['java-home','sdk','output','key']:p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--version',required=True);p.add_argument('--code',type=int,required=True);p.add_argument('--base-code',type=int,required=True)
 a=p.parse_args();root=Path(__file__).resolve().parents[1];work=root/'.tools/feature-modules';work.mkdir(parents=True,exist_ok=True);a.output.mkdir(parents=True,exist_ok=True)
 key=serialization.load_pem_private_key(a.key.read_bytes(),None)
 classpath=[Path(v) for v in (root/'android/app/build/module-classpath.txt').read_text().splitlines() if v.strip()]
 for id in ['assistant','markdown','almanac','voice']:
  target=work/id;target.mkdir(exist_ok=True);jar=target/'classes.jar'
  with zipfile.ZipFile(root/f'android/{id}Plugin/build/outputs/aar/{id}Plugin-release.aar') as aar,zipfile.ZipFile(__import__('io').BytesIO(aar.read('classes.jar'))) as source,zipfile.ZipFile(jar,'w') as out:
   for entry in source.infolist():
    if entry.filename.endswith('.class') and entry.filename.startswith('app/dayflow/'+id+'/') and not entry.filename.endswith('/NativeSpeech.class') and not entry.filename.split('/')[-1].startswith('R$') and not entry.filename.endswith('/R.class'):out.writestr(entry,source.read(entry))
  dex=target/'dex';dex.mkdir(exist_ok=True)
  args=['-cp',str(a.sdk/'build-tools/36.1.0/lib/d8.jar'),'com.android.tools.r8.D8','--release','--min-api','28','--lib',str(a.sdk/'platforms/android-36/android.jar'),'--output',str(dex)]
  for item in classpath:args+=['--classpath',str(item)]
  args+=[str(jar)]
  argfile=target/'args.txt';argfile.write_text('\n'.join('"'+v.replace('\\','/').replace('"','\\"')+'"' for v in args),encoding='utf-8')
  subprocess.run([str(a.java_home/'bin/java.exe'),'@'+str(argfile)],check=True)
  if (dex/'classes2.dex').exists():raise RuntimeError('Feature needs multidex; split the feature before publication')
  raw=(dex/'classes.dex').read_bytes();payload=dict(schema=1,platform='android-module',api=1,id=id,version=a.version,version_code=a.code,min_app_code=a.base_code,max_app_code=a.base_code,sha256=hashlib.sha256(raw).hexdigest(),dex=base64.b64encode(raw).decode())
  body=json.dumps(payload,separators=(',',':')).encode();envelope=dict(payload=base64.b64encode(body).decode(),signature=base64.b64encode(key.sign(body,padding.PKCS1v15(),hashes.SHA256())).decode());encoded=json.dumps(envelope,separators=(',',':')).encode()
  if len(encoded)>12*1024*1024:raise RuntimeError('Module too large')
  file=a.output/(id+'-module.json');file.write_bytes(encoded);print(id,len(raw),'DEX bytes;',len(encoded),'signed package bytes',flush=True)

if __name__=='__main__':main()
