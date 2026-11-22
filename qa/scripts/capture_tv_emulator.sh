#!/usr/bin/env bash
# Isolated screenshot test on a real Android TV emulator; QA fixtures are DEBUG ONLY.
set -uo pipefail
mkdir -p shots
PKG=com.gathin.tvhome
APP="$PKG/.MainActivity"
QA="$PKG/.UnifiedQaSeedActivity"
log(){ printf '%s\n' "$*" | tee -a shots/run.log; }
press(){ adb shell input keyevent "$1" || true; sleep 0.7; }
snap(){
  local name="$1"
  sleep 1
  adb exec-out screencap -p > "shots/$name.png" || true
  log "CAPTURE $name $(wc -c < "shots/$name.png") bytes"
  timeout 8s adb shell uiautomator dump /sdcard/unifiedtv-layout.xml >/dev/null 2>&1 &&
   timeout 5s adb pull /sdcard/unifiedtv-layout.xml "shots/$name.xml" >/dev/null 2>&1 || true
}
qa(){
  local layout="$1" page="$2" focus="$3" name="$4"
  adb shell am force-stop "$PKG" || true
  log "QA layout=$layout page=$page focus=$focus"
  adb shell am start -n "$QA" --es qa_layout "$layout" --es qa_target "$page" --es qa_focus "$focus" >>shots/run.log 2>&1 || true
  sleep 5
  snap "$name"
}
settings(){
  local section="$1" layout="$2" name="$3"
  adb shell am force-stop "$PKG" || true
  adb shell am start -n "$QA" --es qa_page settings --ei qa_section "$section" --es qa_layout "$layout" >>shots/run.log 2>&1 || true
  sleep 5
  snap "$name"
}
log "QA start $(date -u)"
adb shell wm size 1280x720 || true
adb shell wm density 160 || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

log "OFFICIAL SIGNED APK v0.7.7"
adb install -r before.apk || exit 2
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n "$APP" >>shots/run.log 2>&1 || true
sleep 4
snap "original-v077-inicio"
adb uninstall "$PKG" || true

log "OFFICIAL SIGNED APK v0.7.10"
adb install -r after.apk || exit 2
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n "$APP" >>shots/run.log 2>&1 || true
sleep 4
snap "original-v0710-inicio"
for entry in "peliculas:126" "series:164" "buscar:202" "mi-lista:240" "apps:278" "tv-directo:316" "ajustes:665"; do
  name=$(echo "$entry" | cut -d: -f1)
  y=$(echo "$entry" | cut -d: -f2)
  adb shell input tap 25 "$y" || true
  sleep 2
  snap "original-v0710-$name"
done
adb uninstall "$PKG" || true

log "DEBUG QA APP: fake TMDB-style fixtures, no external credentials"
adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk || exit 2
qa shelves HOME "" "qa-filas-01-inicio"
qa shelves MOVIES "" "qa-filas-02-peliculas"
qa shelves SERIES "" "qa-filas-03-series"
qa shelves SEARCH "" "qa-filas-04-buscar"
qa shelves WATCH "" "qa-filas-05-seguir-viendo"
qa shelves APPS "" "qa-filas-06-apps"
qa shelves LIVE "" "qa-filas-07-tv-directo"
qa shelves MOVIES "PELÍCULA QA 02" "qa-filas-08-primer-poster-focus"
press KEYCODE_DPAD_RIGHT
snap "qa-filas-09-segundo-poster-focus"
press KEYCODE_DPAD_RIGHT
snap "qa-filas-10-tercer-poster-focus"
for i in 1 2 3 4 5; do press KEYCODE_DPAD_RIGHT; done
snap "qa-filas-11-scroll-horizontal-focus"
adb shell am force-stop "$PKG" || true
adb shell am start -n "$QA" --es qa_layout shelves --es qa_target MOVIES --ez qa_details true >>shots/run.log 2>&1 || true
sleep 6
snap "qa-filas-12-ficha-completa-pulsacion-larga"
qa columns MOVIES "" "qa-columnas-01-peliculas-dos-subcolumnas"
qa columns SERIES "" "qa-columnas-02-series-dos-subcolumnas"
qa columns MOVIES "PELÍCULA QA 01" "qa-columnas-03-primer-poster-focus"
press KEYCODE_DPAD_RIGHT
snap "qa-columnas-04-segundo-poster-focus"
press KEYCODE_DPAD_DOWN
snap "qa-columnas-05-foco-segunda-fila"
press KEYCODE_DPAD_RIGHT
press KEYCODE_DPAD_RIGHT
snap "qa-columnas-06-scroll-lateral-focus"
for section in 0 1 2 3 4 5 6; do
  case "$section" in
    0) name=apariencia;;
    1) name=catalogo;;
    2) name=plataformas;;
    3) name=seguir-viendo;;
    4) name=actualizaciones;;
    5) name=launcher;;
    6) name=informacion;;
  esac
  settings "$section" columns "qa-ajustes-$section-$name"
done
settings 1 shelves "qa-ajustes-catalogo-modo-filas"
python3 qa/scripts/report_tv_emulator.py shots || true
log "DONE $(date -u)"
