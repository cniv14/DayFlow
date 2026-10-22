"""Publish optional ABI-specific native engine archives and a publisher-signed manifest."""
import argparse,base64,hashlib,json,subprocess,zipfile
from pathlib import Path
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--ndk',type=Path,required=True);p.add_argument('--key',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--version',required=True);p.add_argument('--code',type=int,required=True);p.add_argument('--base-code',type=int,required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[1];work=root/'.tools/voice-engines';work.mkdir(parents=True,exist_ok=True);a.output.mkdir(parents=True,exist_ok=True);files={}
with zipfile.ZipFile(root/'android/voicePlugin/build/outputs/aar/voicePlugin-release.aar') as aar:
 for abi,label in [('arm64-v8a','arm64'),('armeabi-v7a','armv7')]:
  lib=work/(label+'.so');lib.write_bytes(aar.read('jni/'+abi+'/libdayflow_asr.so'))
  subprocess.run([str(a.ndk/'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'),'--strip-unneeded',str(lib)],check=True)
  name='voice-engine-'+label+'.zip';archive=a.output/name
  with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:z.write(lib,'libdayflow_asr.so')
  files[abi]=dict(name=name,size=archive.stat().st_size,sha256=hashlib.sha256(archive.read_bytes()).hexdigest(),library_size=lib.stat().st_size,library_sha256=hashlib.sha256(lib.read_bytes()).hexdigest());print(label,'compressed engine',archive.stat().st_size,flush=True)
payload=dict(schema=1,platform='android-voice-engine',api=1,version=a.version,version_code=a.code,min_app_code=a.base_code,max_app_code=a.base_code,files=files)
raw=json.dumps(payload,separators=(',',':')).encode();key=serialization.load_pem_private_key(a.key.read_bytes(),None);(a.output/'voice-engine.json').write_text(json.dumps(dict(payload=base64.b64encode(raw).decode(),signature=base64.b64encode(key.sign(raw,padding.PKCS1v15(),hashes.SHA256())).decode())),encoding='utf-8')
