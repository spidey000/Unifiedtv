# Arquitectura de UnifiedTV

## Punto de entrada y recuperación
- `MainActivity`: puente mínimo de compatibilidad con `HOME` y botones OEM.
  No renderiza el dock ni el grid heredado.
- `UnifiedCatalogActivity`: único inicio visual con menú lateral, carruseles
  de películas/series, sección Seguir viendo y grid de apps de seis columnas.
- `UnifiedCatalogSettingsActivity`: ajustes de catálogo, fuentes, apariencia,
  seguimiento, actualizaciones, accesos a permisos y recuperación de Android TV.
- `UnifiedPermissionChecks`: diagnóstico del estado de la red, launcher predeterminado,
  servicios de accesibilidad, listener opcional y permiso de ajustes seguros.
- `HomeSettingsActivity`: selector **del sistema** para cambiar de Home;
  no cambia ni desactiva paquetes del fabricante.

## Catálogo y reproducción
- `UnifiedCatalogApi`: TMDB, cacheo de JSON, imágenes, disponibilidad TMDB.
- `UnifiedCatalogRoute`: deep links verificables de plataformas;
  Nuvio es el proveedor P2P de último recurso cuando no hay oferta en los
  proveedores seleccionados e instalados; Stremio y otras apps son destinos
  independientes. Las insignias identifican oferta regional, aunque aún no
  exista un enlace directo. No prometen reproducción en Nuvio.
- `UnifiedWatchNext`: datos publicados por canales Android TV cuando el usuario
  conceda el permiso; no accede al historial interno privado de las apps.
- `UnifiedProviderWordmarks`: 23 marcas empaquetadas, mapeo por proveedor/paquete;
  no equivale a disponibilidad en un servicio.
- `StreamUi` y `TvSpatialNavigation`: componentes visuales y mando DPAD.

## Compatibilidad Android
`HomeKeyService`, `InputKeyService` y otros adaptadores permanecen para
las TVs con teclas OEM o funciones de sistema opcionales. No representan una
segunda interfaz visual. El puente Home conserva una ruta al selector de
inicio del fabricante.

## Módulos futuros
La carpeta `addons/` se conserva íntegra como fuente de integraciones,
sin habilitarlas automáticamente. Adaptarlas requerirá una API compatible
con el catálogo y las reglas de permisos de Android 8–14.

## Limpieza del launcher anterior
La configuración OEM se separó de la configuración visual del catálogo.
Consulta [Inventario de limpieza y compatibilidad](legacy-cleanup.md).
