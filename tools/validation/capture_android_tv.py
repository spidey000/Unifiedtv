#!/usr/bin/env python3
"""Boot an Android TV AVD, exercise native DPAD, capture real APK screens.

All emulator/ADB subprocesses intentionally share this process's network context.
Requires ANDROID_HOME, ANDROID_AVD_HOME, an existing AVD, and a debug APK.
Does not insert TMDB keys, publish fake progress, grant permissions, or install releases.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--avd', default='unifiedtv')
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--software', action='store_true')
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
sdk = Path(os.environ['ANDROID_HOME'])
adb = str(sdk / 'platform-tools/adb')
pkg = 'io.github.spidey000.unifiedtv'
component = pkg + '/com.gathin.tvhome.MainActivity'

def cmd(*args, **kwargs):
    return subprocess.run([adb, *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=45, **kwargs).stdout

def key(*keys):
    cmd('shell', 'input', 'keyevent', *map(str, keys))
    time.sleep(.3)

records = []
def capture(name):
    time.sleep(.5)
    (a.output / (name + '.png')).write_bytes(cmd('exec-out', 'screencap', '-p'))
    cmd('shell', 'uiautomator', 'dump', '/sdcard/unifiedtv-window.xml')
    xml = cmd('exec-out', 'cat', '/sdcard/unifiedtv-window.xml')
    (a.output / (name + '.xml')).write_bytes(xml)
    focused = [x.attrib for x in ET.fromstring(xml).iter('node') if x.get('focused') == 'true']
    records.append({'screen': name, 'focused': focused})
    print('Captured', name, flush=True)

env = os.environ.copy()
if a.software:
    env['ANDROID_I_WANT_MY_TCG'] = 'yes'
emu_args = [str(sdk/'emulator/emulator'), '-avd', a.avd, '-no-window', '-no-audio',
            '-no-snapshot', '-gpu', 'swiftshader_indirect', '-memory', '1536']
if a.software:
    emu_args += ['-accel', 'off']
with (a.output/'emulator.log').open('w') as log:
    emu = subprocess.Popen(emu_args, env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        for attempt in range(150):
            if emu.poll() is not None:
                raise RuntimeError('Emulator exited; see emulator.log')
            try:
                if cmd('shell','getprop','sys.boot_completed').strip() == b'1':
                    break
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                pass
            if attempt % 15 == 0:
                print('Waiting for Android TV boot…', flush=True)
            time.sleep(2)
        else:
            raise TimeoutError('Android TV did not boot within five minutes')
        cmd('install', '-r', str(a.apk.resolve()))
        for size, density in [('1280x720','213'), ('1920x1080','320'), ('3840x2160','640')]:
            cmd('shell', 'wm', 'size', size)
            cmd('shell', 'wm', 'density', density)
            for page in ['HOME','MOVIES','SERIES','WATCH','SEARCH','APPS','LIVE']:
                cmd('shell','am','force-stop',pkg)
                cmd('shell','am','start','-n',component)
                time.sleep(1)
                index = ['HOME','MOVIES','SERIES','SEARCH','WATCH','APPS','LIVE'].index(page)
                if index:
                    key(*([20]*index),23)
                capture(size+'_'+page.lower())
                key(22) # Right: enter content, where an action exists.
                capture(size+'_'+page.lower()+'_focus')
                key(4) # Back: return to this page's rail.
        cmd('shell','wm','size','1920x1080')
        cmd('shell','wm','density','320')
        cmd('shell','am','start','-n',component,'-a','com.gathin.tvhome.action.LAUNCHER_SETTINGS')
        for i, section in enumerate(['appearance','catalog','platforms','watch','updates','permissions','recovery','about']):
            if i:
                key(20,23)
            capture('1920x1080_settings_'+section)
        (a.output/'results.json').write_text(json.dumps(records,indent=2,ensure_ascii=False))
    finally:
        emu.terminate()
        try:
            emu.wait(timeout=15)
        except subprocess.TimeoutExpired:
            emu.kill()
