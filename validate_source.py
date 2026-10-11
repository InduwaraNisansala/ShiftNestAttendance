"""Source consistency checks. Not an Android build. Requires javalang and lxml."""
from pathlib import Path
import re
import javalang
from lxml import etree as E
base=Path(__file__).parent/'app/src/main'
names={k:set() for k in ('id','drawable','mipmap','layout','color','style','xml','string','bool')}
for p in (base/'res').rglob('*'):
    if not p.is_file():continue
    folder=p.parent.name.split('-')[0]
    if folder!='values' and folder in names:names[folder].add(p.stem)
    if p.suffix=='.xml':
        root=E.parse(str(p)).getroot()
        if folder=='values':
            for element in root:
                kind=element.get('type') if element.tag=='item' else element.tag
                if kind in names and element.get('name'):names[kind].add(element.get('name'))
        for element in root.iter():
            for value in element.attrib.values():
                names['id'].update(re.findall(r'@\+id/(\w+)',value))
errors=[]
for p in (base/'java').rglob('*.java'):
    javalang.parse.parse(p.read_text())
    for kind,name in re.findall(r'(?<!android\.)\bR\.(\w+)\.(\w+)',p.read_text()):
        if kind in names and name not in names[kind]:errors.append((p.name,kind,name))
manifest=E.parse(str(base/'AndroidManifest.xml'))
classes={p.stem for p in (base/'java').rglob('*.java')}
ns='{http://schemas.android.com/apk/res/android}'
for layout in (base/'res'/'layout').glob('*.xml'):
    for element in E.parse(str(layout)).iter():
        if isinstance(element.tag,str) and element.tag.startswith('com.shiftnest.attendance.') and element.tag.rsplit('.',1)[1] not in classes:
            errors.append((layout.name,'missing custom view',element.tag))
for activity in manifest.findall('.//activity') + manifest.findall('.//service'):
    name=activity.get(ns+'name')
    if name.startswith('.') and name[1:] not in classes:errors.append(('manifest',name))
assert not errors,errors
print(f"PASS: {len(classes)} Java files parse; resource references, XML and manifest activity classes resolve.")
