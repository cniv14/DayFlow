"""Package an Android-only release while retaining unchanged companion downloads."""
import argparse
import hashlib
from pathlib import Path
import shutil
from publish_updates import publish


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--code', type=int, required=True)
    parser.add_argument('--companions', type=Path, required=True,
                        help='Previous release directory containing Windows/server assets and Windows feed')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / 'dist' / ('v' + args.version)
    output.mkdir(parents=True, exist_ok=True)
    # Preserve the signed Windows version and URL: its binary has not changed.
    for name in ('DayFlow-windows.zip', 'DayFlow-server.zip', 'windows-update.json'):
        shutil.copyfile(args.companions / name, output / name)
    publish(root / '.signing/updates-private.pem', output,
            'https://github.com/cniv14/DayFlow/releases/download/v' + args.version,
            args.version, args.code,
            android=root / 'android/app/build/outputs/apk/release/app-release.apk')
    shutil.copyfile(root / 'docs' / ('Android-' + args.version + '.md'), output / 'Testing.md')
    shutil.copyfile(root / 'examples/课表模板.csv', output / 'timetable-template.csv')
    names = ('DayFlow-android.apk', 'DayFlow-windows.zip', 'DayFlow-server.zip',
             'android-update.json', 'windows-update.json', 'Testing.md', 'timetable-template.csv')
    checks = []
    for name in sorted(names):
        file = output / name
        with file.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        checks.append(digest + '  ' + name)
        print(f'{name}: {file.stat().st_size:,} bytes')
    (output / 'SHA256SUMS.txt').write_text('\n'.join(checks) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
