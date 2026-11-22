# UnifiedTV

**Tu catálogo de streaming en una sola pantalla, sin anuncios del launcher.**

UnifiedTV es un fork independiente de [CleanYourTV](https://github.com/Gathin23/cleanyourtv) para **Android TV y Google TV**. Mantiene sus utilidades de launcher, dock y cuadrícula de aplicaciones, y añade una portada unificada de películas y series con información de las plataformas de streaming que tienes instaladas.

**Estado:** primera implementación / MVP, pendiente de compilar y probar en televisores reales. El código de desarrollo está en [`feature/unified-catalog`](https://github.com/spidey000/Unifiedtv/tree/feature/unified-catalog).

## ¿Qué incluye?

- **Tres pestañas principales:** **Películas**, **Series** y **Seguir viendo**, navegables con el mando. El catálogo recuerda la última pestaña.
- **Cinco filtros en Películas y Series:** Destacados (tendencia diaria), Más vistos (aproximación por tendencia semanal), Novedades (estrenos en cartelera / series en emisión), Populares y Mejor valorados (histórico).
- **Valoraciones TMDB junto al título:** estrella dorada discreta y puntuación sobre 10 con un decimal (por ejemplo, `★ 8,5`), en películas, series y resultados de búsqueda. Se leen de `vote_average`/`vote_count` en los resultados existentes; sin votos o con nota inválida, la valoración no aparece. No se confunden con las notas de las plataformas.
- **Seguir viendo unificado:** lee las entradas visibles del proveedor Android TV `Watch Next` y, cuando existen, los canales «Continue Watching» de las aplicaciones. Muestra portada, origen y barra de progreso, y permite abrir la entrada publicada por la app.
- **Fuentes configurables de Seguir viendo:** Netflix, Prime Video, Max/HBO Max, Disney+, Apple TV+, SkyShowtime, Movistar Plus+, Filmin, **Stremio** y **Nuvio**, además de otras apps compatibles opcionalmente.
- **Buscador global** de películas y series en TMDB.
- **Disponibilidad por país:** por defecto España (`ES`), con otros países configurables.
- **Marcas de plataforma:** muestra las plataformas compatibles que están **instaladas y seleccionadas**.
- **Preferencias y fallback:** activar/desactivar Netflix, Prime Video, Max/HBO Max, Disney+, Apple TV+, SkyShowtime, Movistar Plus+ y Filmin, y cambiar su prioridad con el mando.
- **Apertura:** prueba la búsqueda externa de la aplicación de mayor prioridad. Si no es compatible, abre la propia aplicación y te indica qué título buscar. Si la app falla al arrancar, prueba la siguiente plataforma.
- **Error explícito** si el título no figura disponible en ninguna de tus apps seleccionadas; enlace a opciones online cuando TMDB lo proporciona.
- **Caché:** últimas respuestas de catálogo, disponibilidad y carteles, para reducir consultas y mostrar datos previamente guardados sin conexión.
- **Launcher original preservado:** pulsar «Aplicaciones» o Atrás desde el catálogo muestra la cuadrícula y dock originales de CleanYourTV. Se puede desactivar el catálogo como Home desde Ajustes.

### Flujo de usuario

1. Pulsas **Home**. Eliges **Películas**, **Series** o **Seguir viendo**.
2. Dentro de Películas o Series cambias entre **Destacados · Más vistos · Novedades · Populares · Mejor valorados (histórico)**. Las etiquetas de plataformas se basan en disponibilidad TMDB y aplicaciones instaladas/activadas.
3. Seleccionas un título. UnifiedTV intenta abrir la búsqueda de la primera app según tu prioridad; si no ofrece búsqueda externa, abre la aplicación para que busques el título. Cuando no hay plataforma compatible se informa del motivo.
4. En **Seguir viendo** aparecen las entradas que las apps comparten voluntariamente con Android TV. Pulsar una abre su acción de continuación si funciona en la TV, o su app como alternativa.
5. En **Ajustes → Seguir viendo** puedes filtrar fuentes, activar la fila en Home, incluir próximos episodios o canales de continuación, definir antigüedad máxima y ocultar títulos completados.

> **Más vistos:** TMDB no ofrece cifras reales de visionado agregadas de plataformas. Este filtro utiliza la tendencia semanal como indicador aproximado, y lo señala en pantalla.

> **Seguir viendo:** Android TV solo permite leer las entradas que las aplicaciones hayan publicado y marcado como visibles, después de conceder `READ_TV_LISTINGS`. **No puede extraer automáticamente el historial privado de todas tus apps**. Dependiendo del dispositivo, Netflix, Prime Video, Max, Stremio y Nuvio pueden no publicar ninguna entrada. Se muestran únicamente los datos accesibles.

> **Limitación real:** TMDB no devuelve una URL directa de ficha para cada proveedor y las apps no publican una interfaz universal para abrir fichas. Por tanto, el MVP **no garantiza abrir la ficha exacta** ni comprobar suscripciones activas. Instalar Netflix no implica poder reproducir cualquier título; la disponibilidad cambia con el tiempo y el país. No se hace ninguna reproducción no autorizada ni se elude DRM.

## Versiones y descargas

**[Descargar la última versión de UnifiedTV](https://github.com/spidey000/Unifiedtv/releases/latest)**

Busca `UnifiedTV-vX.Y.Z.apk` y `SHA256SUMS` en **Assets**. `v0.1.2` es una edición histórica de solo código fuente. No uses los ZIP o tarballs de GitHub como instaladores.

Cuando la publicación está habilitada, los cambios en `main` generan una release SemVer después de pasar las pruebas, Android Lint y la verificación de firma. Si faltan las credenciales o falla una comprobación, no se publica nada. Consulta la [guía de firma y publicación](docs/RELEASES.md).

### Instalar o actualizar la APK oficial

Descarga ambos archivos en la misma carpeta y verifica el hash antes de instalar. Con ADB conectado al televisor:

```bash
sha256sum -c SHA256SUMS
adb install -r UnifiedTV-vX.Y.Z.apk  # sustituye X.Y.Z por la versión descargada
```

Usa el mismo comando para actualizar **sin desinstalar**: el paquete oficial `io.github.spidey000.unifiedtv` conserva su firma y aumenta `versionCode`. Android mantiene los datos de la app, incluidos los ajustes de TMDB y plataformas. Si rechaza la actualización por firma incompatible, no desinstales la versión existente: comprueba si era un build de desarrollo.

## Requisitos

- Android TV / Google TV con Android 8+ (API 26).
- Android SDK 34 y JDK 17 para compilar.
- Una **clave personal TMDB API v3** (se introduce desde Ajustes del televisor; nunca en el repositorio).
- Las aplicaciones de streaming que quieras utilizar instaladas y configuradas con sus cuentas.

### Instalación para desarrollo

```bash
git clone https://github.com/spidey000/Unifiedtv.git
cd Unifiedtv
cd launcher
./gradlew -PlauncherApplicationId=io.github.spidey000.unifiedtv.dev testReleaseUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Los builds de desarrollo usan firma debug. El sufijo `.dev` evita sustituir CleanYourTV o crear una instalación incompatible con la APK oficial.

Prueba UnifiedTV desde la lista de apps de tu televisor. No es necesario deshabilitar el launcher original.

### Primer arranque

1. Abre **UnifiedTV → Ajustes**.
2. Obtén una API Key v3 en [TMDB](https://www.themoviedb.org/settings/api) e introdúcela.
3. Comprueba **País = ES** (u otro país).
4. Activa las plataformas que utilizas y ordénalas con **↑ / ↓**.
5. Configura **Seguir viendo**: elige sus fuentes (Netflix, Max, Prime, Stremio, Nuvio...), límite de antigüedad y si debe aparecer en Home.
6. Entra en **Seguir viendo** y concede permiso de **listados TV** si Android lo solicita.
7. Pulsa **Actualizar** para obtener nuevas películas, series y disponibilidad. La información de Seguir viendo se actualiza al entrar en esa sección.

También encontrarás «UnifiedTV · Streaming y preferencias» en los ajustes del launcher original.

## Arquitectura

| Componente | Función |
|---|---|
| `UnifiedCatalogActivity.java` | Pestañas Películas, Series, Seguir viendo, cinco filtros y tarjetas con progreso |
| `UnifiedCatalogApi.java` | Cinco categorías TMDB para películas/series, proveedores y caché offline |
| `UnifiedWatchNext.java` | Lectura autorizada de Android TV Watch Next y canales de continuación |
| `UnifiedCatalogPrefs.java` | Servicios instalados, país, preferencias y orden |
| `UnifiedCatalogSettingsActivity.java` | Ajustes utilizables con el mando |
| `UnifiedCatalogRoute.java` | Búsqueda o apertura de apps, errores y fallback |
| `UnifiedTvHome.java` | Entrada desde Home y salida al launcher clásico |
| `MainActivity.java` | Integración mínima con el launcher original |

La app consulta **TMDB directamente desde el dispositivo**, sin servidor propio, telemetría ni recopilación de credenciales de servicios. La clave TMDB se almacena en las preferencias privadas de la aplicación. Los resultados cacheados pueden estar desactualizados, por lo que debe verificarse la disponibilidad final en el servicio elegido.

Consulta [la guía de desarrollo](docs/UNIFIEDTV.md) para limitaciones, pruebas y próximos pasos.

## Atribuciones y proyecto original

This product uses the TMDB API but is not endorsed or certified by TMDB.

Los metadatos y carteles proceden de [The Movie Database](https://www.themoviedb.org/), y la información de dónde ver un título procede de los datos de JustWatch expuestos por TMDB. JustWatch y las marcas de streaming pertenecen a sus respectivos propietarios.

El proyecto se basa en **CleanYourTV**, de Gathin23, licenciado bajo **MIT**. Se conservan sus avisos en [LICENSE](LICENSE). La [documentación original](README.upstream.md) permanece disponible, pero sus afirmaciones sobre tamaño de APK y ausencia de red **no describen esta variante**. No somos el proyecto oficial [cleanyour.tv](https://cleanyour.tv).

**Repositorio:** [spidey000/Unifiedtv](https://github.com/spidey000/Unifiedtv) · **Origen:** [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv).
