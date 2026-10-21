"""Fetch the optional, pinned Apache-2.0 RapidOCR Android library."""
from pathlib import Path
from urllib.request import urlopen
import hashlib
root=Path(__file__).resolve().parents[1]
target=root/'android/ocrPlugin/libs/OcrLibrary-1.3.0-release.aar'
url='https://github.com/RapidAI/RapidOcrAndroidOnnx/releases/download/1.3.0/OcrLibrary-1.3.0-release.aar'
expected='735efdaeb7e43591eeabbb52d34064dea68aa0fcfc4554d04a0bfe44334e6e18'
if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest()==expected:
 print('Pinned OCR library already present.')
else:
 target.parent.mkdir(parents=True,exist_ok=True)
 data=urlopen(url,timeout=120).read(50*1024*1024)
 if hashlib.sha256(data).hexdigest()!=expected: raise SystemExit('OCR SHA-256 mismatch; refusing to use download.')
 temporary=target.with_suffix('.download');temporary.write_bytes(data);temporary.replace(target)
 print('Downloaded and SHA-256 verified official RapidOCR 1.3.0 AAR.')
