"""Leapy_tts added begin: Export typed compile-only APIs from the matching built AOSP framework.

Usage: python tools/export_tts_framework_api.py <framework-minus-apex.jar>
No implementation is packaged in the APK; the installed framework provides these classes.
"""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

root=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1])
target=root/'app/libs/dream-tts-framework-api.jar'
names=['com/Legal/Java/Interface.class','android/common/DreamCreatedMediaManager.class']
with zipfile.ZipFile(source) as original:
    entries={name:original.read(name) for name in names}
with zipfile.ZipFile(target,'w',compression=zipfile.ZIP_DEFLATED) as exported:
    for name,data in entries.items():
        info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED
        exported.writestr(info,data)
    exported.writestr('META-INF/dream-tts-api.json',json.dumps({
        'usage':'compileOnly; classes must be supplied by the installed AOSP framework',
        'class_sha256':{name:hashlib.sha256(data).hexdigest() for name,data in entries.items()}
    },indent=2))
print('Exported matching AOSP framework API classes:',target)
# Leapy_tts end
