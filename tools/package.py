"""Package explicit release outputs; never traverses secrets, caches or profiles."""
from pathlib import Path
import hashlib
import shutil
import zipfile
from publish_updates import publish

root=Path(__file__).resolve().parents[1];dist=root/'dist';dist.mkdir(exist_ok=True)
apk=root/'android/app/build/outputs/apk/release/app-release.apk'
shutil.copyfile(apk,dist/'DayFlow-android.apk')
with zipfile.ZipFile(dist/'DayFlow-windows.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for project,name in [('windows','DayFlow.Widget'),('windows/Updater','DayFlow.Updater')]:
        folder=root/project/'bin/Release/net10.0-windows'
        for suffix in ['.exe','.dll','.deps.json','.runtimeconfig.json']:
            file=folder/(name+suffix);archive.write(file,file.name)
    archive.write(root/'LICENSE','LICENSE')
    archive.writestr('README.txt','日栖 DayFlow 0.1.0\n运行 DayFlow.Widget.exe，需要 .NET 10 Desktop Runtime。\n数据保存在 %LOCALAPPDATA%\\DayFlow，更新保留。\n请解压到可写的固定目录，保留 DayFlow.Updater.* 文件以使用在线更新。\n')
with zipfile.ZipFile(dist/'DayFlow-server.zip','w',zipfile.ZIP_DEFLATED) as archive:
    for name in ['server.py','test_server.py','Dockerfile','compose.yaml','Caddyfile','.env.example']:
        archive.write(root/'server'/name,'server/'+name)
    archive.writestr('server/updates/.keep','')
    archive.write(root/'LICENSE','LICENSE')
    for file in (root/'docs').glob('*.md'):archive.write(file,'docs/'+file.name)
shutil.copyfile(root/'docs/测试说明.md',dist/'测试说明.md')
shutil.copyfile(root/'examples/课表模板.csv',dist/'课表模板.csv')
publish(root/'.signing/updates-private.pem',dist,'https://github.com/cniv14/DayFlow/releases/download/v0.1.0','0.1.0',1,dist/'DayFlow-android.apk',dist/'DayFlow-windows.zip')
checks=[]
for file in sorted(dist.iterdir()):
    if file.name=='SHA256SUMS.txt' or not file.is_file():continue
    with file.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    checks.append(digest+'  '+file.name)
(dist/'SHA256SUMS.txt').write_text('\n'.join(checks)+'\n',encoding='utf-8')
for file in sorted(dist.iterdir()):
    if file.is_file():print(f'{file.name}: {file.stat().st_size:,} bytes')
