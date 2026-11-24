# Diseño nativo de UnifiedTV: adaptación de la auditoría React

La referencia visual analizada el 24-11-2026 es una **maqueta React reconstruida**
para revisión en Chromium; no es la implementación de producción. La APK sigue
usando Android Views/Java. No se copian los contenidos ficticios ni los QR de
demostración al dispositivo.

## Equivalencias de interfaz

| React de revisión | Android TV (producción) |
| --- | --- |
| `--bg / --panel / --rail` | `StreamUi.BLACK / PANEL / rail()` |
| `.nav-entry.active` | `StreamUi.navigation()`: icono/texto acentuado, fondo transparente |
| `.nav-entry:focus-visible` | `StreamUi.navigation()`: superficie gris contrastada |
| `.hero-button.primary` | `StreamUi.heroPrimary()`: acción blanca con foco evidente |
| `.poster:focus-visible` | `bindTileFocus()`: escala de 1,025, insets y scroll fijo |
| `.grid-six` | `UnifiedCatalogLayout.COLUMNS = 6` |
| `.settings-content` | `UnifiedCatalogSettingsActivity` + scroll de foco |
| `.main-viewport` | `UnifiedCatalogActivity` + `TvFocusViewport` |
| Escape/teclas de navegación | `TvSpatialNavigation` y control DPAD nativo |

## Cambios aplicados

- Foco del mando distinto de la ruta seleccionada: el seleccionado no compite
  con el foco y mantiene el acento visual incluso con el lateral colapsado.
- Botón `Ver ahora` más claro; se conserva su acción real y deep link.
- Scroll vertical de contenido y ajustes en coordenadas de contenido, con un margen adicional para revelar
  el elemento enfocado, sin interferir con el desplazamiento horizontal de
  carruseles ni con el editor de búsqueda.
- Espacio inferior adicional en pantallas con scroll y grids de seis columnas.
- La navegación y el título usan **Seguir viendo** para Android Watch Next;
  no se anuncia una lista de guardados inexistente.
- **Entradas de TV** sustituye a **TV en directo** hasta ofrecer canales reales.

## Validación

`TvFocusViewportTest` prueba márgenes y scroll de tarjetas desde la JVM.
`tests/test_unifiedtv_visual_parity.py` protege las rutas y su estilo.
La auditoría de Chromium no prueba el comportamiento del SO Android; tras el
merge hay que ejecutar `testReleaseUnitTest lintRelease assembleRelease`
y un smoke test real con DPAD y capturas en un emulador Android TV (720p y 1080p),
contrastando los estados de Inicio, Películas, Series, Seguir viendo, Buscar,
Apps y fuentes, Entradas de TV y las ocho secciones de Ajustes.

Verificar en equipo real: el elemento enfocado debe estar enteramente visible;
las flechas no deben atraparse en menús; las filas de contenido deben conservar
imágenes y overlays auténticos; el QR de TMDB, permisos y cambio de Home deben
usar sus flujos nativos y mantener el foco al volver.

Revisión posterior: [auditoría integral Android TV](android-tv-redesign-audit.md).
