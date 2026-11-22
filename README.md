# UnifiedTV

**Tu catálogo de streaming en una sola pantalla, sin anuncios del launcher.**

UnifiedTV es un fork independiente de [CleanYourTV](https://github.com/Gathin23/cleanyourtv) para **Android TV y Google TV**. Mantiene sus utilidades de launcher, dock y cuadrícula de aplicaciones, y añade una portada unificada de películas y series con información de las plataformas de streaming que tienes instaladas.

**Estado:** primera implementación / MVP, pendiente de compilar y probar en televisores reales. El código de desarrollo está en [`feature/unified-catalog`](https://github.com/spidey000/Unifiedtv/tree/feature/unified-catalog).

## ¿Qué incluye?

- **Inicio unificado:** tendencias de la semana, últimas películas en cartelera y series del momento, con pósteres y títulos en español.
- **Buscador global** de películas y series en TMDB.
- **Disponibilidad por país:** por defecto España (`ES`), con otros países configurables.
- **Marcas de plataforma:** muestra las plataformas compatibles que están **instaladas y seleccionadas**.
- **Preferencias y fallback:** activar/desactivar Netflix, Prime Video, Max/HBO Max, Disney+, Apple TV+, SkyShowtime, Movistar Plus+ y Filmin, y cambiar su prioridad con el mando.
- **Apertura:** prueba la búsqueda externa de la aplicación de mayor prioridad. Si no es compatible, abre la propia aplicación y te indica qué título buscar. Si la app falla al arrancar, prueba la siguiente plataforma.
- **Error explícito** si el título no figura disponible en ninguna de tus apps seleccionadas; enlace a opciones online cuando TMDB lo proporciona.
- **Caché:** últimas respuestas de catálogo, disponibilidad y carteles, para reducir consultas y mostrar datos previamente guardados sin conexión.
- **Launcher original preservado:** pulsar «Aplicaciones» o Atrás desde el catálogo muestra la cuadrícula y dock originales de CleanYourTV. Se puede desactivar el catálogo como Home desde Ajustes.

### Flujo de usuario

1. Pulsas **Home** y aparece el catálogo de UnifiedTV.
2. Navegas por los carteles con el mando; las etiquetas muestran Netflix, Max, Prime Video, etc. cuando figuran disponibles en TMDB **y** tienes la aplicación instalada/activada.
3. Seleccionas un título. UnifiedTV intenta abrir la búsqueda de la primera aplicación compatible en tu orden de preferencias.
4. Si no acepta búsquedas externas, abre la app para que localices manualmente el título; si no arranca, intenta la siguiente.
5. Si ninguna plataforma disponible está instalada/activada, aparece un aviso.

> **Limitación real:** TMDB no devuelve una URL directa de ficha para cada proveedor y las apps no publican una interfaz universal para abrir fichas. Por tanto, el MVP **no garantiza abrir la ficha exacta** ni comprobar suscripciones activas. Instalar Netflix no implica poder reproducir cualquier título; la disponibilidad cambia con el tiempo y el país. No se hace ninguna reproducción no autorizada ni se elude DRM.

## Requisitos

- Android TV / Google TV con Android 8+ (API 26).
- Android SDK 34 y JDK 17 para compilar.
- Una **clave personal TMDB API v3** (se introduce desde Ajustes del televisor; nunca en el repositorio).
- Las aplicaciones de streaming que quieras utilizar instaladas y configuradas con sus cuentas.

### Instalación para desarrollo

```bash
git clone https://github.com/spidey000/Unifiedtv.git
cd Unifiedtv
git checkout feature/unified-catalog
cd launcher
./gradlew testReleaseUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Si ya tienes otra versión de CleanYourTV con una firma distinta, evita sustituirla involuntariamente. Puedes usar un identificador de paquete propio durante la compilación:

```bash
./gradlew -PlauncherApplicationId=io.github.spidey000.unifiedtv assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Selecciona UnifiedTV como launcher predeterminado desde Android TV cuando corresponda. **No deshabilites el Home original sin comprobar antes que UnifiedTV abre correctamente**, especialmente si instalas desde un dispositivo diferente. El script de instalación original puede asumir el nombre del paquete original; revísalo antes de usarlo con un identificador distinto.

### Primer arranque

1. Abre **UnifiedTV → Ajustes**.
2. Obtén una API Key v3 en [TMDB](https://www.themoviedb.org/settings/api) e introdúcela.
3. Comprueba **País = ES** (u otro país).
4. Activa las plataformas que utilizas y ordénalas con **↑ / ↓**.
5. Pulsa **Actualizar**. El catálogo cargará los pósteres y comprobará la disponibilidad en segundo plano.

También encontrarás «UnifiedTV · Streaming y preferencias» en los ajustes del launcher original.

## Arquitectura

| Componente | Función |
|---|---|
| `UnifiedCatalogActivity.java` | Catálogo TV, secciones, carteles, búsqueda y selección |
| `UnifiedCatalogApi.java` | API TMDB, proveedores y caché offline |
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
