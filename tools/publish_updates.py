"""Build signed update feeds for GitHub Releases or the optional sync server.
Build-time dependency only: cryptography. Server and clients need no Python libs.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding


def publish(key_path, output, base_url, version, code, android=None, windows=None):
    key=serialization.load_pem_private_key(Path(key_path).read_bytes(),None)
    output=Path(output);output.mkdir(parents=True,exist_ok=True)
    for platform,file,name in [('android',android,'DayFlow-android.apk'),('windows',windows,'DayFlow-windows.zip')]:
        if not file:continue
        source=Path(file)
        with source.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
        payload={'schema':1,'platform':platform,'version':version,'version_code':code,'url':base_url.rstrip('/')+'/'+name,
                 'sha256':digest,'size':source.stat().st_size,'notes':'日栖 DayFlow '+version+' · 离线优先，更新保留本地数据'}
        raw=json.dumps(payload,ensure_ascii=False,separators=(',',':')).encode()
        envelope={'payload':base64.b64encode(raw).decode(),'signature':base64.b64encode(key.sign(raw,padding.PKCS1v15(),hashes.SHA256())).decode()}
        if source.resolve()!=(output/name).resolve():shutil.copyfile(source,output/name)
        target=output/(platform+'-update.json');temp=target.with_suffix('.tmp');temp.write_text(json.dumps(envelope),encoding='utf-8');temp.replace(target)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--key',required=True);p.add_argument('--output',required=True);p.add_argument('--base-url',required=True)
    p.add_argument('--version',required=True);p.add_argument('--code',type=int,required=True);p.add_argument('--android');p.add_argument('--windows');a=p.parse_args()
    publish(a.key,a.output,a.base_url,a.version,a.code,a.android,a.windows)
    print('Signed update feeds published. No private keys included.')
