#!/usr/bin/env python3
"""Capture the real debug APK using a physical AVD framebuffer per resolution.

Requires ANDROID_HOME and an existing Android TV AVD. No TMDB key or permissions
are inserted. PNG dimensions and settings headings are verified before success.
"""
import argparse
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--avd', default='unifiedtv')
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--software', action='store_true')
p.add_argument('--resolutions', nargs='+', choices=['720p','1080p','4k'], default=['720p','1080p','4k'])
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
sdk = Path(os.environ['ANDROID_HOME'])
avd_home = Path(os.environ.get('ANDROID_AVD_HOME', str(Path.home()/'.android/avd')))
config = avd_home/(a.avd+'.avd')/'config.ini'
adb = str(sdk/'platform-tools/adb')
pkg = 'io.github.spidey000.unifiedtv'
component = pkg+'/com.gathin.tvhome.MainActivity'
records = []
expected_size = (1920,1080)

def cmd(*args, input_data=None):
    r = subprocess.run([adb,*map(str,args)], stdout=subprocess.PIPE,
                       stderr=subprocess.PIPE, timeout=45, input=input_data)
    if r.returncode:
        raise RuntimeError(r.stderr.decode(errors='replace'))
    return r.stdout

def key(*keys):
    cmd('shell','input','keyevent',*keys)
    time.sleep(.4)

def hierarchy():
    cmd('shell','uiautomator','dump','/sdcard/unifiedtv-window.xml')
    return cmd('exec-out','cat','/sdcard/unifiedtv-window.xml')

def await_text(label):
    for _ in range(5):
        xml = hierarchy()
        if any(x.get('text') == label and int(re.match(r'\[(\d+),', x.get('bounds', '[0,0]'))[1]) > expected_size[0]*.2 for x in ET.fromstring(xml).iter('node')):
            return
        time.sleep(1)
    raise AssertionError('Expected screen heading: '+label)

def capture(name):
    time.sleep(.5)
    png = cmd('exec-out','screencap','-p')
    actual = struct.unpack('>II',png[16:24])
    if actual != expected_size:
        raise AssertionError(f'{name}: expected {expected_size}, got PNG {actual}')
    (a.output/(name+'.png')).write_bytes(png)
    xml = hierarchy()
    (a.output/(name+'.xml')).write_bytes(xml)
    nodes = list(ET.fromstring(xml).iter('node'))
    if not any(x.get('package') == pkg for x in nodes):
        raise AssertionError(name+': UnifiedTV window is not visible')
    focus = [x.attrib for x in nodes if x.get('focused') == 'true']
    if not focus:
        raise AssertionError(name+': DPAD focus is not visible')
    records.append({'screen':name,'pixels':actual,'focused':focus})
    (a.output/'results.json').write_text(json.dumps(records,indent=2,ensure_ascii=False))
    print('Captured',name,actual,flush=True)

def configure(width,height,density):
    values = dict(line.split('=',1) for line in config.read_text().splitlines() if '=' in line)
    values = {k.strip():v.strip() for k,v in values.items()}
    values.update({'hw.lcd.width':str(width),'hw.lcd.height':str(height),
                   'hw.lcd.density':str(density),'skin.name':f'{width}x{height}',
                   'skin.dynamic':'no','showDeviceFrame':'no'})
    values.pop('skin.path',None)
    config.write_text('\n'.join(k+'='+v for k,v in values.items())+'\n')

env = os.environ.copy()
if a.software:
    env['ANDROID_I_WANT_MY_TCG'] = 'yes'
for label,width,height,density in [('720p',1280,720,213),('1080p',1920,1080,320),('4k',3840,2160,640)]:
    if label not in a.resolutions:
        continue
    expected_size = (width,height)
    size = f'{width}x{height}'
    configure(width,height,density)
    # Generate/load the host ADB key before the emulator copies its public key.
    # Starting both together races into an unauthorized device on fresh runners.
    cmd('start-server')
    args = [str(sdk/'emulator/emulator'),'-avd',a.avd,'-skin',size,'-no-window',
            '-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader_indirect','-memory','2048']
    if a.software:
        args += ['-accel','off']
    with (a.output/('emulator-'+size+'.log')).open('w') as log:
        emu = subprocess.Popen(args,env=env,stdout=log,stderr=subprocess.STDOUT)
        try:
            for attempt in range(150):
                if emu.poll() is not None:
                    raise RuntimeError('Emulator exited; inspect emulator-'+size+'.log')
                try:
                    if cmd('shell','getprop','sys.boot_completed').strip() == b'1':
                        break
                except (RuntimeError,subprocess.TimeoutExpired):
                    pass
                if attempt % 15 == 0:
                    print('Waiting for Android TV boot:',size,flush=True)
                time.sleep(2)
            else:
                raise TimeoutError('Android TV did not boot within five minutes')
            cmd('shell','wm','size','reset')
            cmd('shell','wm','density','reset')
            print(cmd('shell','wm','size').decode(),flush=True)
            cmd('install','-r',str(a.apk.resolve()))
            cmd('shell','am','force-stop',pkg)
            # Isolate screenshots from release announcements in this disposable AVD.
            # Production defaults remain unchanged; no account or API key is seeded.
            cmd('shell','run-as',pkg,'mkdir','-p','shared_prefs')
            prefs = b'<map><boolean name="update_auto_check" value="false" /></map>'
            cmd('shell',f"run-as {pkg} sh -c 'cat > shared_prefs/unifiedtv_catalog.xml'",
                input_data=prefs)
            for page in ['HOME','MOVIES','SERIES','WATCH','SEARCH','APPS','LIVE']:
                cmd('shell','am','force-stop',pkg)
                cmd('shell','am','start','-n',component)
                time.sleep(1)
                index = ['HOME','MOVIES','SERIES','SEARCH','WATCH','APPS','LIVE'].index(page)
                if index:
                    key(*([20]*index),23)
                capture(size+'_'+page.lower())
                key(22)
                capture(size+'_'+page.lower()+'_focus')
                key(4)
            if width == 1920:
                # A fresh bridge instance must receive the explicit settings action.
                cmd('shell','am','force-stop',pkg)
                cmd('shell','am','start','-n',component,'-a','com.gathin.tvhome.action.LAUNCHER_SETTINGS')
                headings = ['Apariencia','Catálogo','Plataformas','Seguir viendo',
                            'Actualizaciones','Permisos y funcionamiento','Recuperación','Información']
                sections = ['appearance','catalog','platforms','watch','updates','permissions','recovery','about']
                for i,(section,heading) in enumerate(zip(sections,headings)):
                    if i:
                        key(20,23)
                    await_text(heading)
                    capture(size+'_settings_'+section)
        finally:
            try:
                cmd('emu','kill')
            except (RuntimeError,subprocess.TimeoutExpired):
                emu.terminate()
            try:
                emu.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emu.kill()
            cmd('kill-server')
