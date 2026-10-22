"""Package an Android-only release while retaining unchanged companion downloads."""
import argparse
import base64
import json
import hashlib
from pathlib import Path
import shutil
import zipfile
from publish_updates import publish


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--code', type=int, required=True)
    parser.add_argument('--companions', type=Path, required=True,
                        help='Previous release directory containing Windows/server assets and Windows feed')
    parser.add_argument('--refresh-companions', action='store_true',
                        help='Package newly built Windows binaries and current server with matching versions')
    parser.add_argument('--skip-ocr', action='store_true', help='Omit optional OCR APKs')
    parser.add_argument('--refresh-server', action='store_true',
                        help='Refresh server sources while preserving the existing Windows binary/feed')
    parser.add_argument('--math-source', type=Path,
                        help='Unmodified jlatexmath-android v0.2.0 corresponding source archive')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / 'dist' / ('v' + args.version)
    output.mkdir(parents=True, exist_ok=True)
    # Preserve the signed Windows version and URL: its binary has not changed.
    if args.refresh_companions:
        with zipfile.ZipFile(output / 'DayFlow-windows.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
            for project, name in (('windows','DayFlow.Widget'), ('windows/Updater','DayFlow.Updater')):
                for suffix in ('.exe','.dll','.deps.json','.runtimeconfig.json'):
                    file=root/project/'bin/Release/net10.0-windows'/(name+suffix)
                    archive.write(file,file.name)
            archive.write(root/'LICENSE','LICENSE')
            archive.writestr('README.txt','DayFlow '+args.version+'\n需要 .NET 10 Desktop Runtime。解压到可写目录，运行 DayFlow.Widget.exe。保留 Updater 文件以使用在线更新。\n')
        with zipfile.ZipFile(output / 'DayFlow-server.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
            for name in ('server.py','backup.py','test_server.py','Dockerfile','compose.yaml','Caddyfile','.env.example'):
                archive.write(root/'server'/name,'server/'+name)
            archive.writestr('server/updates/.keep','')
            archive.write(root/'LICENSE','LICENSE')
            for file in (root/'docs').glob('*.md'):
                archive.write(file,'docs/'+file.name)
    else:
        for name in ('DayFlow-windows.zip', 'DayFlow-server.zip', 'windows-update.json'):
            shutil.copyfile(args.companions / name, output / name)
    if args.refresh_server and not args.refresh_companions:
        with zipfile.ZipFile(output / 'DayFlow-server.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
            for name in ('server.py','backup.py','Dockerfile','compose.yaml','Caddyfile','.env.example'):
                archive.write(root/'server'/name,'server/'+name)
            archive.writestr('server/updates/.keep','')
            archive.write(root/'LICENSE','LICENSE')
            for file in (root/'docs').glob('*.md'):
                archive.write(file,'docs/'+file.name)
            for name in ('tools/publish_runtime_update.py','tools/build_runtime_example.py','examples/hotupdate/Entry.java','android/app/src/main/java/app/dayflow/HotModule.java'):
                archive.write(root/name,name)
    publish(root / '.signing/updates-private.pem', output,
            'https://github.com/cniv14/DayFlow/releases/download/v' + args.version,
            args.version, args.code,
            android=root / 'android/app/build/outputs/apk/release/app-release.apk',
            windows=output/'DayFlow-windows.zip' if args.refresh_companions else None)
    if not args.refresh_companions:
        # Rehost unchanged Windows bytes on this release before old releases are
        # removed. Preserve its real version/code instead of inventing a new build.
        previous = json.loads((output / 'windows-update.json').read_text(encoding='utf-8'))
        payload = json.loads(base64.b64decode(previous['payload']))
        with (output / 'DayFlow-windows.zip').open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != payload['sha256']:
                parser.error('The preserved Windows ZIP does not match its signed manifest.')
        publish(root / '.signing/updates-private.pem', output,
                'https://github.com/cniv14/DayFlow/releases/download/v' + args.version,
                payload['version'], payload['version_code'], windows=output / 'DayFlow-windows.zip')
    shutil.copyfile(root / 'docs' / ('Android-' + args.version + '.md'), output / 'Testing.md')
    shutil.copyfile(root / 'examples/课表模板.csv', output / 'timetable-template.csv')
    api_doc = root / 'docs/客户端同步API.md'
    if api_doc.exists():
        shutil.copyfile(api_doc, output / 'ClientSyncAPI.md')
    taskflow_doc = root / 'docs/TaskFlow-Windows连接协议.md'
    if taskflow_doc.exists():
        shutil.copyfile(taskflow_doc, output / 'TaskFlowProtocol.md')
    plugin_names = []
    for abi, name in (() if args.skip_ocr else (('arm64-v8a', 'arm64'), ('armeabi-v7a', 'armv7'))):
        apk = root / 'android/ocrPlugin/build/outputs/apk/release' / f'ocrPlugin-{abi}-release.apk'
        if not apk.is_file():
            parser.error(f'Missing optional OCR APK: {apk}; build :ocrPlugin:assembleRelease first.')
        destination = f'DayFlow-ocr-{name}.apk'
        shutil.copyfile(apk, output / destination)
        plugin_names.append(destination)
    for module, name in (('markdownPlugin', 'markdown'), ('almanacPlugin', 'almanac'), ('assistantPlugin', 'assistant')):
        apk = root / 'android' / module / 'build/outputs/apk/release' / f'{module}-release.apk'
        if not apk.is_file():
            parser.error(f'Missing plugin APK: {apk}')
        destination = f'DayFlow-{name}.apk'
        shutil.copyfile(apk, output / destination)
        plugin_names.append(destination)
    for source, name in (('docs/助手API.md', 'AssistantAPI.md'), ('docs/服务端稳定协议.md', 'ServerContract.md'), ('docs/Agent使用.md', 'AgentGuide.md'), ('docs/功能热更新.md', 'RuntimeUpdateAPI.md')):
        shutil.copyfile(root / source, output / name)
        plugin_names.append(name)
    shutil.copyfile(root / 'docs/插件API.md' , output / 'PluginAPI.md')
    plugin_names.append('PluginAPI.md')
    math_included = any('ext-latex' in file.read_text(encoding='utf-8') for file in (root/'android').glob('*/build.gradle'))
    if math_included:
        source = args.math_source or root/'dist/third-party/jlatexmath-android-v0.2.0-source.zip'
        if not source.is_file():
            parser.error('Formula renderer source must accompany APK. Run tools/fetch_math_source.py or pass --math-source.')
        shutil.copyfile(source, output/'MathRenderer-source.zip')
    names = ('DayFlow-android.apk', 'DayFlow-windows.zip', 'DayFlow-server.zip',
             'android-update.json', 'windows-update.json', 'Testing.md', 'timetable-template.csv')
    checks = []
    for name in sorted(names + tuple(plugin_names) + (('ClientSyncAPI.md',) if api_doc.exists() else ()) + (('TaskFlowProtocol.md',) if taskflow_doc.exists() else ()) + (('MathRenderer-source.zip',) if math_included else ())):
        file = output / name
        with file.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        checks.append(digest + '  ' + name)
        print(f'{name}: {file.stat().st_size:,} bytes')
    (output / 'SHA256SUMS.txt').write_text('\n'.join(checks) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
