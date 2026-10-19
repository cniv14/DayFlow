"""Create local signing material once. Never publish .signing/ or print secrets."""
import json
import os
from pathlib import Path
import secrets
import subprocess
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization

root=Path(__file__).resolve().parents[1]
private=root/'.signing';private.mkdir(exist_ok=True)
keyfile=private/'updates-private.pem'
if not keyfile.exists():
    key=rsa.generate_private_key(public_exponent=65537,key_size=3072)
    keyfile.write_bytes(key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
else:
    key=serialization.load_pem_private_key(keyfile.read_bytes(),None)
public=key.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo)
(root/'update-public.pem').write_bytes(public)
raw=root/'android/app/src/main/res/raw';raw.mkdir(exist_ok=True)
(raw/'update_public.pem').write_bytes(public)
config=private/'android.json'
if not config.exists():
    password=secrets.token_urlsafe(32)
    keytool=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-17'))/'bin/keytool.exe'
    if not keytool.exists():
        import shutil
        keytool=shutil.which('keytool')
    args=[str(keytool),'-genkeypair','-keystore',str(private/'dayflow.jks'),'-storepass',password,'-keypass',password,
          '-alias','dayflow','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=DayFlow, O=DayFlow', '-noprompt']
    result=subprocess.run(args,capture_output=True)
    if result.returncode:
        raise SystemExit('Android signing key generation failed; no secrets were printed.')
    config.write_text(json.dumps({'storeFile':str(private/'dayflow.jks'),'storePassword':password,'keyAlias':'dayflow','keyPassword':password}),encoding='utf-8')
print('Signing keys ready in ignored .signing/. Public verification key exported.')
