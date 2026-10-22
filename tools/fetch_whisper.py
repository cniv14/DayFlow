"""Fetch the exact MIT whisper.cpp source used by the Android local voice module."""
import hashlib,io,urllib.request,zipfile
from pathlib import Path

COMMIT='d1be6fde11ac6e0407606b4e42fe72d34add8037' # upstream v1.9.5
root=Path(__file__).resolve().parents[1];cache=root/'.tools/whisper';target=cache/('whisper.cpp-'+COMMIT)
if not (target/'include/whisper.h').is_file():
 cache.mkdir(parents=True,exist_ok=True)
 request=urllib.request.Request('https://codeload.github.com/ggml-org/whisper.cpp/zip/'+COMMIT,headers={'User-Agent':'DayFlow-build'})
 with urllib.request.urlopen(request,timeout=120) as response:raw=response.read()
 with zipfile.ZipFile(io.BytesIO(raw)) as archive:
  for entry in archive.infolist():
   if not (cache/entry.filename).resolve().is_relative_to(cache.resolve()):raise ValueError('Unsafe archive path')
  archive.extractall(cache)
 print('Archive SHA256',hashlib.sha256(raw).hexdigest())
print('Whisper source ready:',COMMIT)
