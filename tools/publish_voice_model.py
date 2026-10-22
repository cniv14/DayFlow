"""Publish an optional Whisper GGML model with a signed checksum; no API credentials in output."""
import argparse,base64,hashlib,json,shutil
from pathlib import Path
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import padding
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--model',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--key',type=Path,required=True);p.add_argument('--version',required=True)
a=p.parse_args()
with a.model.open('rb') as f:
 if f.read(4)!=b'lmgg':p.error('Expected whisper.cpp GGML multilingual model')
 f.seek(0);digest=hashlib.file_digest(f,'sha256').hexdigest()
payload=dict(schema=1,platform='android-voice-model',version=a.version,size=a.model.stat().st_size,sha256=digest)
raw=json.dumps(payload,separators=(',',':')).encode();key=serialization.load_pem_private_key(a.key.read_bytes(),None)
a.output.mkdir(parents=True,exist_ok=True);shutil.copyfile(a.model,a.output/'voice-model.bin');(a.output/'voice-model.json').write_text(json.dumps(dict(payload=base64.b64encode(raw).decode(),signature=base64.b64encode(key.sign(raw,padding.PKCS1v15(),hashes.SHA256())).decode())),encoding='utf-8')
print('Signed voice model published; keep upstream model license with deployment.')
