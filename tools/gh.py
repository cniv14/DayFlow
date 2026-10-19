"""Run official GitHub CLI with the user's existing OS proxy configuration.

Go's HTTP client does not automatically use the Windows Internet Settings proxy.
This wrapper changes only the child process environment, never system settings.
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request

root=Path(__file__).resolve().parents[1]
bundled=root/'.tools/gh/bin/gh.exe'
binary=str(bundled) if bundled.exists() else shutil.which('gh')
if not binary:
    raise SystemExit('Install the official GitHub CLI from https://cli.github.com/ first.')
env=os.environ.copy()
for scheme,url in urllib.request.getproxies().items():
    if scheme in ('http','https','no'):
        env.setdefault(scheme.upper()+'_PROXY',url)
env['GIT_SSL_NO_VERIFY']='false'
raise SystemExit(subprocess.call([binary,*sys.argv[1:]],env=env))
