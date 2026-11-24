# Laboratorio React de UnifiedTV

Referencia recuperada de `Previsualizacion_React_UnifiedTV_reconstruida.zip` (24/11/2026).
Es una reconstrucción para estudiar geometría y estados; no sustituye a la APK.
Los títulos, ilustraciones y progreso son ejemplos ficticios, indicados en pantalla.
Los iconos vectoriales y wordmarks se exportan de los recursos nativos, sin redibujarlos.
Procedencia/licencias: `launcher/ICON-LICENSE.txt`, `docs/design/provider-wordmarks.md`;
React 16.0.0 / ReactDOM 16.0.1: `assets/REACT-LICENSE.txt`.

```sh
python tools/visual-lab/export_native_resources.py
cd tools/visual-lab
python -m http.server 8000
# En una máquina con Chromium y Playwright:
python run_audit.py
```

Los PNG se generan en `capturas/`. El informe distingue capturas web de nativas.
Los resultados de navegación en Chromium no certifican DPAD Android, permisos,
QR real, aplicaciones instaladas ni el retorno desde un reproductor.
