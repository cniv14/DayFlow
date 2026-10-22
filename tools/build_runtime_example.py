"""Compile the small runtime example with the installed JDK/SDK; no Gradle or tests."""
import argparse
from pathlib import Path
import subprocess
import zipfile

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--sdk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a=p.parse_args();root=Path(__file__).resolve().parents[1]
classes=a.output/'classes';classes.mkdir(parents=True,exist_ok=True)
android=a.sdk/'platforms/android-36/android.jar'
subprocess.run([str(a.java_home/'bin/javac.exe'),'-encoding','UTF-8','-source','8','-target','8','-classpath',str(android),'-d',str(classes),str(root/'android/app/src/main/java/app/dayflow/HotModule.java'),str(root/'examples/hotupdate/Entry.java')],check=True)
jar=a.output/'module.jar'
with zipfile.ZipFile(jar,'w') as out:
    # Never duplicate the host ABI class in a module.
    for file in (classes/'app/dayflow/hotfix').rglob('*.class'):out.write(file,file.relative_to(classes).as_posix())
dex=a.output/'dex';dex.mkdir(exist_ok=True)
subprocess.run([str(a.java_home/'bin/java.exe'),'-cp',str(a.sdk/'build-tools/36.1.0/lib/d8.jar'),'com.android.tools.r8.D8','--min-api','26','--lib',str(android),'--classpath',str(classes),'--output',str(dex),str(jar)],check=True)
print(dex/'classes.dex')
