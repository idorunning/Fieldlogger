"""Build signed release files using credentials supplied through environment variables."""
import os,subprocess,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]
required=['FIELDLOGGER_KEYSTORE','FIELDLOGGER_STORE_PASSWORD','FIELDLOGGER_KEY_PASSWORD']
if not all(os.environ.get(name) for name in required):raise SystemExit('Set private release signing environment variables; see docs/ANDROID.md.')
if not os.environ.get('ANDROID_HOME'):raise SystemExit('Set ANDROID_HOME to an Android SDK with platform 36 and build tools 36.0.0.')
subprocess.run([str(root/'android/gradlew'),'bundleRelease','assembleRelease','--no-daemon'],cwd=root/'android',check=True)
output=root.parent/'artifacts';output.mkdir(exist_ok=True)
for src,dst in [('bundle/release/app-release.aab','fieldlogger-1.0.0-play.aab'),('apk/release/app-release.apk','fieldlogger-1.0.0-release.apk')]:
 shutil.copy2(root/'android/app/build/outputs'/src,output/dst)
 print('Built '+str(output/dst))
