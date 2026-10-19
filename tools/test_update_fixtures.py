"""Maintainer-only regeneration of public signed test vectors after key rotation."""
from pathlib import Path
import base64
import json
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

root=Path(__file__).resolve().parents[1]
key=serialization.load_pem_private_key((root/'.signing/updates-private.pem').read_bytes(),None)
for platform,path in [('windows',root/'tests/fixtures/windows-update.json'),('android',root/'android/app/src/androidTest/res/raw/update_test.json')]:
    payload={'schema':1,'platform':platform,'version':'0.1.1','version_code':2,'url':'https://example.invalid/package','size':1,'sha256':'0'*64,'notes':'Public test vector, not an installable package'}
    data=json.dumps(payload,separators=(',',':')).encode();envelope={'payload':base64.b64encode(data).decode(),'signature':base64.b64encode(key.sign(data,padding.PKCS1v15(),hashes.SHA256())).decode()}
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(envelope),encoding='utf-8')
print('Public update verification fixtures generated.')
