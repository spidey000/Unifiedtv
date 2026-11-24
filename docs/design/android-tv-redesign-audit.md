# Revisión integral Android TV — 24/11/2026

## Alcance y decisiones

Se revisan `StreamUi`, catálogo, ajustes, geometría, DPAD, ficha y búsqueda.
El repositorio ya implementaba Watch Next, filtros de progreso, enlaces verificables,
QR con caducidad, actualización firmada, permisos y recuperación del launcher.
No se reemplazan esas integraciones ni se modifican `addons/`.

Tokens: fondo #0B0B0F, panel #19191E, texto #F7F7F8, secundario #B7B7C0,
acento configurable #E3293F, estrella #F4C66F, transición de foco 180 ms.
La referencia lógica 960×540 dp usa márgenes horizontales 24 dp y verticales 14 dp.
La barra mantiene 52 dp colapsada y se expande por encima del contenido.
Se conservan seis columnas, pósteres 2:3 y banners de apps 16:9.
El foco conserva escala moderada e insets; 106 % no cabe en todas las celdas existentes.

## Defectos corregidos

- Expandir el rail ya no cambia el ancho del viewport ni recalcula posiciones.
- Hero del 44 % del alto útil, mínimo dependiente del tamaño de fuente;
  elimina padding superior fijo de 71 dp, mayúsculas forzadas y tracking excesivo.
- Las acciones tienen espacio reservado, títulos de tarjetas hasta dos líneas.
- Márgenes seguros en catálogo y ajustes; Settings junto a las demás rutas.
- Series y Entradas usan iconos diferentes.
- Scroll de foco en coordenadas de contenido: se elimina la suma duplicada de scrollY.
  Ver `ViewGroup.offsetRectBetweenParentAndChild` de AOSP Android 14:
  https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/view/ViewGroup.java
- La última fila incompleta del grid se alcanza desde las columnas superiores.
- Al reconstruir Home/Watch tras regresar, el foco se recupera por clave de contenido,
  no por índice ni por un View ID efímero. Una nueva pulsación DPAD cancela la restauración pendiente.
- Búsqueda con debounce de 450 ms: resultados antiguos invalidados y editor estable.
- Ficha: sinopsis alcanzable y desplazable con mando. Back del catálogo vuelve primero al rail.

## Validación y límites

Validación local: 97 tests JVM, 41 comprobaciones Python y build/lint de release correctos.
El primer workflow web produjo 32 capturas sin errores JavaScript.

Los tests JVM cubren geometría del hero, seis columnas, insets, scroll ya desplazado,
filas incompletas, Watch Next, deeplinks y actualización. Los tests Python son
comprobaciones estáticas: por sí solos no certifican una interfaz renderizada.

`tools/visual-lab/` conserva la maqueta React reconstruida anterior y exporta
los recursos Android reales. Sus películas, ilustraciones, QR y progreso son ejemplos:
no prueban disponibilidad ni reproducción. `run_audit.py` captura páginas, diálogos,
foco, scroll y resoluciones. No forma parte de la APK.

`tools/validation/capture_android_tv.py` instala la APK debug, recorre las rutas
públicas mediante mando y genera PNG + XML a 720p, 1080p y 4K. Captura también las
secciones de ajustes; funciona sin claves privadas. Requiere un AVD Android TV.
El workflow `TV visual validation` conserva ambos conjuntos de resultados por siete días.

El entorno local no tiene KVM. Arrancar por TCG no acredita rendimiento en TV.
Chromium local rechaza un socket de arranque; la sesión de navegador remota tampoco
puede acceder al servidor local. No se presentan capturas ficticias como evidencia.

Pendiente antes de certificar el plan completo: inspección de las capturas nativas y
web, contenido real con una clave TMDB de prueba, pulsación larga, QR móvil/TV,
retorno desde reproductores instalados, actualizaciones con firma oficial y prueba
física de legibilidad/overscan. La APK local usa la clave debug y no sustituye una
release oficial. El pipeline existente firma y publica las versiones de main.
