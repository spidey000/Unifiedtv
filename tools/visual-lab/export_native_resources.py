"""Export the exact Android paths/wordmarks, without a second icon library."""
import base64
import json
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
RES = ROOT/'launcher/app/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'
NAMES = {'house':'uic_house','film':'uic_film','tv':'ic_livetv','search':'uic_search',
         'layers':'uic_layers','apps':'ic_apps','settings':'ic_settings','play':'uic_play',
         'palette':'uic_palette','download':'uic_download','check':'uic_check',
         'input':'ic_input','info':'uic_info','back':'uic_arrow_left','arrow':'uic_arrow_right'}
icons = {}
for name, resource in NAMES.items():
    root = ET.parse(RES/'drawable'/f'{resource}.xml').getroot()
    paths = []
    for node in root.iter('path'):
        path = {'d':node.get(A+'pathData'), 'fill':'currentColor' if node.get(A+'fillColor') else 'none'}
        if node.get(A+'strokeColor'):
            path.update(stroke='currentColor',strokeWidth=node.get(A+'strokeWidth','2'),
                        strokeLinecap=node.get(A+'strokeLineCap','round'),
                        strokeLinejoin=node.get(A+'strokeLineJoin','round'))
        paths.append(path)
    icons[name] = paths
brands = {}
for label, resource in {'NETFLIX':'netflix','prime video':'prime','Disney+':'disney',
                         'HBO MAX':'hbomax','Apple TV+':'appletv','SkyShowtime':'skyshowtime',
                         'ATRESplayer':'atresplayer','Movistar+':'movistar'}.items():
    path = RES/'drawable-nodpi'/f'uic_provider_{resource}.png'
    if path.exists():
        brands[label] = 'data:image/png;base64,'+base64.b64encode(path.read_bytes()).decode()
(Path(__file__).parent/'assets/native-resources.js').write_text(
    'window.UNIFIEDTV_ICONS='+json.dumps(icons)+';\nwindow.UNIFIEDTV_WORDMARKS='+json.dumps(brands)+';\n')
