"""Sign one native runtime DEX module; uses the existing publisher key, never uploads it."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--dex', type=Path, required=True)
    p.add_argument('--key', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--version', required=True)
    p.add_argument('--code', type=int, required=True, help='Monotonically increasing runtime revision, independent of APK version')
    p.add_argument('--min-app', type=int, default=131)
    p.add_argument('--max-app', type=int, default=131)
    p.add_argument('--entry', default='app.dayflow.hotfix.Entry')
    p.add_argument('--notes', default='')
    a = p.parse_args()
    import re
    if a.code <= 0 or a.min_app > a.max_app or not re.fullmatch(r'app\.dayflow\.hotfix\.[A-Za-z0-9_.$]+', a.entry):
        p.error('Invalid revision, base range or entry')
    dex = a.dex.read_bytes()
    if not 112 <= len(dex) <= 8*1024*1024 or not dex.startswith(b'dex\n'):
        p.error('Provide one valid classes.dex (at most 8 MiB)')
    payload = dict(schema=1, platform='android-runtime', api=1, version=a.version,
                   version_code=a.code, min_app_code=a.min_app, max_app_code=a.max_app,
                   entry=a.entry, sha256=hashlib.sha256(dex).hexdigest(),
                   notes=a.notes, dex=base64.b64encode(dex).decode())
    raw = json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode()
    key = serialization.load_pem_private_key(a.key.read_bytes(), None)
    envelope = dict(payload=base64.b64encode(raw).decode(), signature=base64.b64encode(key.sign(raw, padding.PKCS1v15(), hashes.SHA256())).decode())
    encoded = json.dumps(envelope, separators=(',', ':')).encode()
    if len(encoded) > 12*1024*1024:
        p.error('Encoded signed envelope exceeds 12 MiB; split the module')
    a.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = a.output.with_suffix('.tmp')
    with temporary.open('wb') as stream:
        stream.write(encoded)
        stream.flush()
        os.fsync(stream.fileno())
    temporary.replace(a.output)
    print(f'Signed runtime {a.version}: {len(encoded):,} bytes; no signing key included')


if __name__ == '__main__':
    main()
