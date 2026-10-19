"""Publish only explicit project paths using the configured Git credential helper.

Never prints credentials or writes them to files. Requires prior git/GCM login.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import urllib.error


def token():
    env=dict(os.environ,GIT_TERMINAL_PROMPT='0',GCM_INTERACTIVE='never')
    result=subprocess.run(['git','credential','fill'],input='protocol=https\nhost=github.com\n\n',text=True,capture_output=True,env=env,timeout=30)
    values=dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)
    if result.returncode or not values.get('password'):
        raise SystemExit('NO_GIT_CREDENTIAL: Log into GitHub using Git Credential Manager or provide an existing repository via the GitHub connector.')
    return values['password']


def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['check','create','release']);p.add_argument('--repo',default='cniv14/DayFlow');p.add_argument('--directory');a=p.parse_args()
    secret=token()
    def api(path,body=None,raw=None,content_type='application/json'):
        data=raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
        req=urllib.request.Request(path if path.startswith('https://') else 'https://api.github.com'+path,data=data,headers={'Authorization':'Bearer '+secret,'Accept':'application/vnd.github+json','User-Agent':'DayFlow-release','Content-Type':content_type})
        try:
            with urllib.request.urlopen(req,timeout=60) as response:return json.load(response)
        except urllib.error.HTTPError as error:
            raise SystemExit('GitHub returned HTTP '+str(error.code)+'. No credentials printed.')
    me=api('/user')
    if a.action=='check':print(json.dumps({'login':me['login'],'authenticated':True}));return
    owner,name=a.repo.split('/')
    if me['login'].lower()!=owner.lower():raise SystemExit('Credential owner differs from requested owner; publication stopped.')
    if a.action=='create':
        result=api('/user/repos',{'name':name,'description':'日栖 DayFlow — offline-first Android planner, Windows widget, optional self-hosted sync','private':False,'auto_init':False,'license_template':'mit'})
        print(json.dumps({'url':result['html_url'],'default_branch':result['default_branch']}));return
    release=api('/repos/'+a.repo+'/releases',{'tag_name':'v0.1.0','name':'日栖 DayFlow v0.1.0 · 首个测试版','body':'首个测试版：Android 离线日程与笔记、QQ/微信通知收集、课表导入、可选同步、Windows 桌面挂件。下载测试说明了解已知限制。更新包采用 RSA 签名校验。','draft':False,'prerelease':False})
    upload=release['upload_url'].split('{')[0]
    from urllib.parse import quote
    for file in sorted(Path(a.directory).iterdir()):
        if not file.is_file():continue
        if file.name not in {'DayFlow-android.apk','DayFlow-windows.zip','DayFlow-server.zip','android-update.json','windows-update.json','Testing.md','timetable-template.csv','SHA256SUMS.txt'}:continue
        api(upload+'?name='+quote(file.name),raw=file.read_bytes(),content_type='application/octet-stream')
        print('Uploaded '+file.name)
    print('Release: '+release['html_url'])


if __name__=='__main__':main()
