# Limpieza del launcher heredado

La interfaz CleanYourTV se sustituyó por la pantalla de catálogo UnifiedTV.
Este documento distingue el código activo de compatibilidad del código visual
y los recursos antiguos que se han retirado.

## Código principal conservado

- `MainActivity` es solo el punto de entrada **HOME** y redirige a
  `UnifiedCatalogActivity`. La pantalla de ajustes es `UnifiedCatalogSettingsActivity`.
- `HomeSettingsActivity` conserva la recuperación del launcher del fabricante;
  no se desinstala ni deshabilita ningún paquete del sistema.
- `HomeKeyService`, `InputKeyService`, `RemoteKeys`, `GamepadPassthrough`,
  `GuardKeep` y `ServiceRepair` permiten recuperar botones OEM y controlar
  algunos fallos de firmware. Requieren autorización y nunca son obligatorios
  para utilizar TMDB o navegar por el catálogo.
- `Config.java` solo lee valores **allowlisted** para mandos, inicio OEM,
  preferencias de idioma del sistema y fallbacks de entrada/Live TV.
  No puede personalizar dock, fondos, reloj, temas, carruseles o skins.
- `SystemSettings` conserva fallbacks de ajustes del fabricante.
  `InputsRequestActivity` permanece deshabilitada por defecto como puente
  de compatibilidad; no expone ningún menú de entradas del launcher antiguo.

## Retirado

- Modelos de festividades, horóscopo, Luna, sesiones de Fórmula 1, reloj y
  fondos rotativos que no se invocaban desde la interfaz nueva.
- Modelos de tarjetas y utilidades del grid/dock/menú antiguo que quedaron
  sin consumidores tras la migración.
- Recursos de animación, iconos de píldoras, temas y estilos anteriores.
- Traducciones obsoletas del launcher antiguo, que no traducían las vistas
  Java actuales de UnifiedTV.
- Parámetros de apariencia del antiguo `config.xml`, ahora sustituido por
  la configuración visual del catálogo.

## Compatibilidad de configuración

Los controles visuales están en **Ajustes → Apariencia, Catálogo,
Plataformas y Permisos** y se guardan en `UnifiedCatalogPrefs`.

La integración de determinados mandos de fabricantes aún puede leer un archivo
opcional `config.json` dentro de los archivos privados de la app. Solo se
aceptan claves para `app_keys`, `settings_key_keycodes`, `input_key_keycodes`,
`key_filter`, `restore_key_services`, `stock_home_redirect` y otras
opciones relacionadas exclusivamente con mandos, arranque e idioma.
Las propiedades anteriores sobre fondos, dock, reloj, decoraciones o festividades
se ignoran, sin migración automática.

## Add-ons y pruebas

Se mantiene `addons/` **sin modificaciones** para su posible portado futuro.
Su código no es parte de la APK UnifiedTV. La compilación y las pruebas
regulares son `testReleaseUnitTest lintRelease assembleRelease`, junto a
`python -m unittest discover -s tests -p 'test_unifiedtv*.py' -v`.

En dispositivos reales se debe comprobar Home, BACK, teclas de Entradas/HDMI,
recuperación del launcher del fabricante, permisos y navegación DPAD;
las pruebas de CI no sustituyen una prueba en emulador o televisor.
