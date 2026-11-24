# Leapy added begin: Derive narrow data/reflection/JNI rules from the application's compiled types.
from pathlib import Path
import argparse
import collections
import json
import os
import re
from classfile_api import inspect

parser = argparse.ArgumentParser()
parser.add_argument('--project', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
classes = {}
xml_files = []
native_files = []
for base, directories, files in os.walk(args.project):
    directories[:] = [d for d in directories if d not in {
        '.git', '.gradle', 'node_modules', 'tmp_verify', 'Reference', 'Uxdesign-opp', 'upstream'}]
    path = Path(base)
    normalized = path.as_posix()
    compiled = ('/build/intermediates/javac/' in normalized and '/classes' in normalized
                or '/build/tmp/kotlin-classes/' in normalized)
    if compiled and 'release' in normalized.lower() and not re.search(r'(?:UnitTest|AndroidTest|/test)', normalized):
        for name in files:
            if name.endswith('.class'):
                info = inspect((path/name).read_bytes())
                classes[info['name']] = info
    elif '/build/' not in normalized:
        xml_files += [path/name for name in files if name.endswith('.xml')]
        native_files += [path/name for name in files if name.endswith(('.cpp', '.c', '.h'))]
if not classes:
    raise RuntimeError('No compiled application types; refusing to generate empty protection rules.')

rules = set()
models = set()
reflect_fields = set()
reflect_methods = set()
dynamic_types = set()


def name(value):
    return value.replace('/', '.')


def rule(value):
    rules.add(value)


def plain(value):
    return value in classes and classes[value]['parent'] in {'java/lang/Object', 'java/lang/Enum', 'java/lang/Record', None}


for key, info in classes.items():
    methods = {member for flags, member, desc in info['methods']}
    if 'copy' in methods and 'component1' in methods:
        models.add(key)
    if info['parent'] == 'com/google/gson/reflect/TypeToken':
        models.update(t for t in re.findall(r'L([^;<]+)', info['signature']) if t in classes)
    if 'com/google/gson/Gson' in info['references']:
        models.update(t for t in info['references'] if plain(t))
    if 'java/io/Serializable' in info['interfaces']:
        # Preserve the serialized class identity/UID contract; methods of other implementation types remain free.
        rule('-keep class ' + name(key) + ' { *; }')
    symbols = set(info['strings'])
    if symbols & {'getDeclaredField', 'getField'}:
        reflect_fields.update(v for v in info['literals'] if re.fullmatch(r'[A-Za-z_$][\w$]*', v))
    if symbols & {'getDeclaredMethod', 'getMethod'}:
        reflect_methods.update(v for v in info['literals'] if re.fullmatch(r'[A-Za-z_$][\w$]*', v))
    if symbols & {'getDeclaredFields', 'getFields'}:
        models.update(t for t in info['references'] if plain(t))
    for literal in info['literals']:
        target = literal.replace('.', '/')
        if target in classes:
            dynamic_types.add(target)
        if symbols & {'ofFloat', 'ofInt', 'ofObject'} and re.fullmatch(r'[a-zA-Z][\w]*', literal):
            reflect_methods.update({'set' + literal[0].upper() + literal[1:],
                                    'get' + literal[0].upper() + literal[1:]})

for file in xml_files:
    text = file.read_text(encoding='utf-8-sig', errors='replace')
    for value in re.findall(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*){2,}', text):
        target = value.replace('.', '/')
        if target in classes:
            dynamic_types.add(target)
for file in native_files:
    text = file.read_text(encoding='utf-8-sig', errors='replace')
    for value in re.findall(r'FindClass\s*\(\s*"([^"]+)"', text):
        if value in classes:
            rule('-keep class ' + name(value) + ' { *; }')
    reflect_methods.update(re.findall(r'Get(?:Static)?MethodID\s*\([^;]{0,300}?"([\w$]+)"', text))
    reflect_fields.update(re.findall(r'Get(?:Static)?FieldID\s*\([^;]{0,300}?"([\w$]+)"', text))

queue = collections.deque(models)
while queue:
    key = queue.popleft()
    info = classes[key]
    values = [desc for _, _, desc in info['fields']]
    values += [value for value in info['strings'] if '<' in value and 'L' in value]
    for value in values:
        for target in re.findall(r'L([^;<]+)', value):
            if plain(target) and target not in models:
                models.add(target)
                queue.append(target)
for key in models:
    if classes[key]['parent'] == 'java/lang/Enum':
        rule('-keep,allowoptimization enum ' + name(key) + ' { *; }')
    else:
        rule('-keep,allowoptimization,allowobfuscation class ' + name(key) + ' { <init>(...); }')
        rule('-keepclassmembers,allowoptimization class ' + name(key) + ' { <fields>; }')
for key in dynamic_types:
    rule('-keep,allowoptimization class ' + name(key) + ' { <init>(...); }')
for key, info in classes.items():
    fields = {member for flags, member, desc in info['fields']} & reflect_fields
    methods = {member for flags, member, desc in info['methods']} & reflect_methods
    if fields or methods:
        members = ['*** ' + member + ';' for member in sorted(fields)]
        members += ['*** ' + member + '(...);' for member in sorted(methods)]
        rule('-keepclassmembers class ' + name(key) + ' { ' + ' '.join(members) + ' }')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text('# Generated from compiled application contracts; implementation remains obfuscatable.\n'
                       + '\n'.join(sorted(rules)) + '\n', encoding='utf-8')
(args.output.parent/'audit.json').write_text(json.dumps(dict(types=len(classes), models=sorted(models),
    dynamic=sorted(dynamic_types), rules=len(rules), program=sorted(classes),
    model_fields={key: sorted(n for flags,n,_ in classes[key]['fields'] if not flags & (0x8 | 0x80 | 0x1000)) for key in sorted(models)}), indent=2), encoding='utf-8')
print(f'Protected {len(models)} data models and {len(dynamic_types)} dynamic types in {len(classes)} program types.')
# Leapy end
