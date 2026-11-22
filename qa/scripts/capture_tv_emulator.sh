#!/usr/bin/env bash
# Isolated emulator-only UI audit. No production credentials are accessed.
set -uo pipefail
mkdir -p shots
PKG=com.gathin.tvhome
APP="$PKG/.MainActivity"
QA="$PKG/.UnifiedQaSeedActivity"
log() { printf '%s\n' "$*" | tee -a shots/run.log; }
press() { adb shell input keyevent "$1" || true; sleep 0.65; }
snap() {
  local filename="$1"
  log "SCREENSHOT $filename"
  sleep 1
  if adb exec-out screencap -p > "shots/$filename.png"; then
    local bytes
    bytes=$(wc -c < "shots/$filename.png")
    log "CAPTURED $filename: $bytes bytes"
  else
    log "ERROR screenshot failed: $filename"
    rm -f "shots/$filename.png"
  fi
  if adb shell uiautomator dump /sdcard/unifiedtv-layout.xml >/dev/null 2>&1; then
    adb pull /sdcard/unifiedtv-layout.xml "shots/$filename.xml" >/dev/null 2>&1 || true
  fi
}
start() {
  log "START ACTIVITY $APP"
  adb shell am start -n "$APP" || true
  sleep 5
}
first_qa_page() {
  local mode="$1"
  log "START QA $mode"
  adb shell am start -n "$QA" --es qa_layout "$mode" || true
  sleep 12
}
menu_capture() {
  local prefix="$1"
  snap "$prefix-01-inicio-menu-focus"
  press KEYCODE_DPAD_DOWN
  snap "$prefix-02-menu-peliculas-focus"
  press KEYCODE_DPAD_CENTER
  snap "$prefix-03-peliculas"
  press KEYCODE_DPAD_DOWN
  press KEYCODE_DPAD_CENTER
  snap "$prefix-04-series"
  press KEYCODE_DPAD_DOWN
  press KEYCODE_DPAD_CENTER
  snap "$prefix-05-buscar"
  press KEYCODE_DPAD_DOWN
  press KEYCODE_DPAD_CENTER
  snap "$prefix-06-mi-lista-seguir-viendo"
  press KEYCODE_DPAD_DOWN
  press KEYCODE_DPAD_CENTER
  snap "$prefix-07-apps-y-fuentes"
  press KEYCODE_DPAD_DOWN
  press KEYCODE_DPAD_CENTER
  snap "$prefix-08-tv-en-directo"
  press KEYCODE_DPAD_DOWN
  press KEYCODE_DPAD_CENTER
  snap "$prefix-09-ajustes-apariencia"
  for section in 1 2 3 4 5 6; do
    press KEYCODE_DPAD_DOWN
    press KEYCODE_DPAD_CENTER
    case "$section" in
      1) label=catalogo;;
      2) label=plataformas;;
      3) label=seguir-viendo;;
      4) label=actualizaciones;;
      5) label=launcher;;
      6) label=informacion;;
    esac
    snap "$prefix-$((section+9))-ajustes-$label"
  done
}

log "QA started, $(date -u)"
adb shell wm size 1280x720 || true
adb shell wm density 160 || true
adb shell settings put system screen_off_timeout 600000 || true
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true

log "INSTALL v0.7.7 official release"
adb install -r before.apk || exit 2
start
snap "old-v0.7.7-inicio"
press KEYCODE_DPAD_RIGHT
snap "old-v0.7.7-elemento-en-foco"
adb uninstall "$PKG" || true

log "INSTALL v0.7.10 official release"
adb install -r after.apk || exit 2
start
menu_capture "new-v0.7.10"
adb uninstall "$PKG" || true

log "INSTALL debug QA build to inspect real poster focus geometry"
adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk || exit 2
first_qa_page shelves
snap "qa-filas-01-inicio-con-posters"
press KEYCODE_DPAD_DOWN
press KEYCODE_DPAD_CENTER
snap "qa-filas-02-peliculas-con-posters"
press KEYCODE_DPAD_RIGHT
snap "qa-filas-03-primer-control-en-foco"
press KEYCODE_DPAD_DOWN
snap "qa-filas-04-primer-poster-en-foco"
press KEYCODE_DPAD_RIGHT
snap "qa-filas-05-segundo-poster-en-foco"
press KEYCODE_DPAD_RIGHT
snap "qa-filas-06-tercer-poster-en-foco"
press KEYCODE_DPAD_DOWN
snap "qa-filas-07-desplazamiento-vertical-focus"
adb shell input keyevent --longpress KEYCODE_DPAD_CENTER || true
snap "qa-filas-08-pulsacion-larga-ficha"
adb shell input keyevent KEYCODE_BACK || true

first_qa_page columns
snap "qa-columnas-01-inicio"
press KEYCODE_DPAD_DOWN
press KEYCODE_DPAD_CENTER
snap "qa-columnas-02-peliculas"
press KEYCODE_DPAD_RIGHT
snap "qa-columnas-03-control-focus"
press KEYCODE_DPAD_DOWN
snap "qa-columnas-04-primer-poster-focus"
press KEYCODE_DPAD_RIGHT
snap "qa-columnas-05-segunda-subcolumna-focus"
press KEYCODE_DPAD_DOWN
snap "qa-columnas-06-segunda-fila-focus"
press KEYCODE_DPAD_RIGHT
press KEYCODE_DPAD_RIGHT
snap "qa-columnas-07-desplazamiento-horizontal-focus"

adb shell am start -n "$QA" --es qa_page settings --ei qa_section 1 --es qa_layout columns || true
sleep 5
snap "qa-columnas-08-ajustes-catalogo-activado"
adb shell am start -n "$QA" --es qa_page settings --ei qa_section 1 --es qa_layout shelves || true
sleep 5
snap "qa-filas-09-ajustes-catalogo-desactivado"

log "ALL CAPTURES COMPLETE"
python3 qa/scripts/report_tv_emulator.py shots || true
