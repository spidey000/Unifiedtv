# Paridad visual de menús: React → Android TV

## Referencia

Se toma como referencia la maqueta React *reconstruida* revisada en Chromium
el 24 de noviembre de 2026 (no sustituye una captura de la APK). La UI nativa
conserva Android Views/Java y los recursos SVG vectoriales/PNG que ya utiliza
UnifiedTV, incluidos los logotipos originales y el flujo QR de TMDB.

## Componentes migrados

| React | Android TV |
| --- | --- |
| `.main-nav`  / `.nav-entry` | `UnifiedCatalogActivity.inflateShell` y `StreamUi.navigation` |
| `.main-nav.expanded` | `resizeNavigation` mueve el rail **y el viewport** en 220 ms |
| `.settings-sidebar` | Navegación `StreamUi.navigation`, altura 38 dp y tipografía 12 sp |
| `.settings-h1` | Título 32 sp, tracking ajustado y margen inferior |
| `.settings-group` | `description` detecta encabezados en mayúsculas, tamaño 11 sp y espaciado |
| `.setting-row` | Fila horizontal clickable nativa: etiqueta, valor al extremo derecho y chevron |
| `.toggle-pill` | `android.widget.Switch` sin texto On/Off, track acentuado y thumb blanco |
| `.swatch` | Selector plano sin borde decorativo, color circular y selección nativa |
| `.search-input` | `StreamUi.field` sin underline, gris carbón y foco contrastado |
| `.dialog-actions` | Botón positivo blanco y acciones secundarias transparentes |
| `:focus-visible` | Focus DPAD con superficie #55555D, selección #28282F |
| `scrollIntoView` | `TvFocusViewport.scrollDelta` con rectángulos en coordenadas del contenido |

Los controles permanecen Android Views (no WebView ni librería React), con
soporte de teclado/mando, lectores de pantalla, permisos del sistema, estado
persistente de preferencias y accesos a ajustes OEM.

## Restricciones de diseño

- No pintar cajas y bordes en todos los estados de reposo.
- Priorizar contenido y tipografía; usar un fondo sólido oscuro.
- El elemento enfocado **debe** diferenciarse de una sección solo seleccionada.
- Usar escala y foco 2:3 para carátulas, banners 16:9 para Apps y fuentes;
  nunca sustituir imágenes reales por elementos de demostración.
- No modificar la lógica de TMDB, deep links, Watch Next, instalación o permisos.
- Respetar la preferencia de animaciones desactivadas del sistema.
- No simular permisos, no instalar launchers ni mostrar canales ficticios.

## Verificación

CI: `python3 -m unittest discover -s tests -p 'test_unifiedtv*.py'` y
`cd launcher && ./gradlew testReleaseUnitTest lintRelease assembleRelease`.

Prueba visual nativa adicional necesaria: comparar con el mosaico Chromium en
720p y 1080p, abrir el rail desde cada sección, recorrer todos los switches y
filas de Ajustes con DPAD, comprobar foco tras volver de permisos o QR,
verificar los títulos largos y el final de las pantallas desplazables.
