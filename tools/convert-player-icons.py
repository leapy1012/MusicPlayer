"""Convert the project-root SVGs and update the existing player vector variants.

Run from the repository root. Existing resource names, state selectors, colors,
alpha and tints are preserved so callers keep their current theme behavior.
"""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / 'app/src/main/res/drawable'
A = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', A)

def attr(name):
    return '{' + A + '}' + name

def svg_paths(name):
    root = ET.parse(ROOT / f'{name}-svgrepo-com.svg').getroot()
    return [p.attrib['d'] for p in root if p.tag.endswith('path')]

PLAY = svg_paths('play')
NEXT = svg_paths('next')
STAR = svg_paths('star')
# Complete the two deliberate gaps only for the filled, selected state. The
# unselected star and play retain the supplied SVG's open, rounded strokes.
star_left, star_right = STAR[0].split('M19.7199')
FILLED_STAR = [star_left +
    'L18.46502 16.5776C18.39297 15.8341 18.35695 15.4624 18.46414 15.1177 '
    'C18.57132 14.773 18.80957 14.4944 19.28608 13.9372L19.7199' + star_right +
    'L8.8255 5.99623C8.4655 6.64193 8.2856 6.96479 8.0049 7.17781 '
    'C7.7243 7.39083 7.3749 7.4699 6.6759 7.62805L6.03954 7.77203Z']
PAUSE = [
    'M7 3.5C5.9 3.5 5 4.4 5 5.5V18.5C5 19.6 5.9 20.5 7 20.5'
    'C8.1 20.5 9 19.6 9 18.5V5.5C9 4.4 8.1 3.5 7 3.5Z',
    'M17 3.5C15.9 3.5 15 4.4 15 5.5V18.5C15 19.6 15.9 20.5 17 20.5'
    'C18.1 20.5 19 19.6 19 18.5V5.5C19 4.4 18.1 3.5 17 3.5Z',
]

def kind(name):
    if 'favorite' in name or 'favourite' in name:
        return 'star'
    if name in ('vector_main_play_previous',) or re.fullmatch(r'vector_desk_lrc_previous(?:_pressed)?', name):
        return 'previous'
    if name in ('vector_main_play_next', 'vector_mini_play_next', 'vector_editor_play_next') or re.fullmatch(r'vector_desk_lrc_next(?:_pressed)?', name):
        return 'next'
    if re.fullmatch(r'vector_(main|mini|drive|trim|audio_preview|desk_lrc)_play(?:_pressed)?', name) or name in ('vector_edit_play', 'vector_lyric_play'):
        return 'play'
    if re.fullmatch(r'vector_(main|mini|drive|trim|audio_preview|desk_lrc)_pause(?:_pressed)?', name):
        return 'pause'
    return None

def selected(name):
    if 'unfavorite' in name or 'unselected' in name:
        return False
    return name in ('main_favourite', 'main_favourite_simple', 'vector_favorite_button', 'vector_favorite_dark',
                    'notify_favorite_new', 'vector_notify_favorite', 'widget_ic_favorite') or any(
        token in name for token in ('_selected', '_select', '_red'))

def make_vector(name, icon, old=None):
    attributes = {attr('width'): '24dp', attr('height'): '24dp'}
    color = '#FFFFFFFF'
    if old is not None:
        for key in ('width', 'height', 'alpha', 'tint', 'autoMirrored'):
            if attr(key) in old.attrib:
                attributes[attr(key)] = old.attrib[attr(key)]
        for path in old.iter('path'):
            candidate = path.get(attr('strokeColor')) or path.get(attr('fillColor'))
            if candidate and candidate not in ('#00000000', '@android:color/transparent'):
                color = candidate
                break
    # All roles share the same 24-unit canvas. The home icon must also occupy
    # its full ImageView when centerInside is used (24dp previously stayed small).
    if name == 'main_favourite':
        attributes[attr('width')] = attributes[attr('height')] = '@dimen/main_item_image_size'
    if name.startswith(('vector_main_', 'vector_mini_', 'vector_drive_')) and icon != 'star':
        attributes[attr('width')] = attributes[attr('height')] = '24dp'
    attributes.update({attr('viewportWidth'): '24', attr('viewportHeight'): '24'})
    root = ET.Element('vector', attributes)
    parent = root
    if name in ('notify_favorite_new', 'notify_unfavorite_new', 'vector_notify_favorite', 'vector_notify_unfavorite'):
        parent = ET.SubElement(parent, 'group', {
            attr('pivotX'): '12', attr('pivotY'): '12', attr('scaleX'): '.8', attr('scaleY'): '.8',
        })
    if icon in ('next', 'previous'):
        # SVG viewBox is -0.5 0 25 25; translate then scale to 24 units.
        parent = ET.SubElement(root, 'group', {attr('scaleX'): '.96', attr('scaleY'): '.96'})
        parent = ET.SubElement(parent, 'group', {attr('translateX'): '.5'})
        if icon == 'previous':
            parent = ET.SubElement(parent, 'group', {attr('pivotX'): '12', attr('scaleX'): '-1'})
    filled = icon == 'star' and selected(name)
    paths = {'play': PLAY, 'next': NEXT, 'previous': NEXT, 'star': FILLED_STAR if filled else STAR, 'pause': PAUSE}[icon]
    for data in paths:
        ET.SubElement(parent, 'path', {
            attr('pathData'): data, attr('fillColor'): color if filled else '#00000000',
            attr('strokeColor'): color, attr('strokeWidth'): '1.5',
            attr('strokeLineCap'): 'round', attr('strokeLineJoin'): 'round',
        })
    ET.indent(root, space='    ')
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding='unicode') + '\n'

if __name__ == '__main__':
    updated = []
    for file in sorted(DRAWABLE.glob('*.xml')):
        icon = kind(file.stem)
        if not icon:
            continue
        old = ET.parse(file).getroot()
        if old.tag != 'vector':  # Keep existing state-list wiring.
            continue
        file.write_text(make_vector(file.stem, icon, old), encoding='utf-8')
        updated.append(file.name)
    for name, icon in [('ic_player_play_outline', 'play'), ('ic_player_next_outline', 'next'),
                       ('ic_player_previous_outline', 'previous'), ('ic_player_star_outline', 'star'),
                       ('ic_player_star_selected', 'star'), ('ic_player_pause_outline', 'pause')]:
        (DRAWABLE / (name + '.xml')).write_text(make_vector(name, icon), encoding='utf-8')
    provenance = ROOT / 'tools/oppo-music-assets.json'
    if provenance.exists():
        data = json.loads(provenance.read_text(encoding='utf-8-sig'))
        data['resources'] = {name: source for name, source in data['resources'].items() if not kind(name)}
        provenance.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    print(f'Updated {len(updated)} existing vectors and generated 6 base vectors.')
    print('\n'.join(updated))
