"""Download the unmodified formula renderer's corresponding source for releases."""
from pathlib import Path
from urllib.request import urlopen

root = Path(__file__).resolve().parents[1]
target = root / 'dist/third-party/jlatexmath-android-v0.2.0-source.zip'
target.parent.mkdir(parents=True, exist_ok=True)
url = 'https://codeload.github.com/noties/jlatexmath-android/zip/refs/tags/v0.2.0'
with urlopen(url, timeout=60) as response:
    target.write_bytes(response.read())
print(target)
