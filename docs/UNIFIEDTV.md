# UnifiedTV — diseño y pruebas de integración

## Estado del MVP

Esta primera versión añade el catálogo unificado a la app nativa del fork. Implementado:

- Entrada a UnifiedTV desde el Home de Android TV, configurable.
- Selector **Películas / Series**. En Inicio, debajo del destacado principal, aparece **Seguir viendo** si está habilitado y después cinco filas horizontales independientes: **Destacados**, **Más vistos**, **Novedades**, **Populares** y **Mejor valorados (histórico)**. No hay pestañas para cambiar entre categorías.
- Destacados = tendencia del día; Más vistos = tendencia semanal como aproximación, **no** reproducciones agregadas reales; Novedades = cartelera actual de cine o series actualmente en emisión; Populares y Mejor valorados = listas TMDB.
- Seguir viendo integrado mediante Android TV `Watch Next` (lectura con permiso) y canales `Continue Watching` disponibles, con origen, progreso y apertura de la acción de la app.
- Fuentes independientes por aplicación: Netflix, Max, Prime, Disney+, Apple TV+, SkyShowtime, Movistar, Filmin, Stremio, Nuvio y apps adicionales opcionales.
- Búsqueda por título en TMDB, idioma español.
- Carteles descargados en segundo plano y caché en el dispositivo.
- Consulta de proveedores por título y país usando los endpoints de TMDB `/movie/{id}/watch/providers` y `/tv/{id}/watch/providers`.
- Iconografía textual de plataformas compatibles solo cuando la aplicación está instalada, seleccionada y TMDB la identifica como opción de streaming.
- Preferencias: país, prioridad, casillas por servicio y modo Home; clave v3 introducida localmente.
- Selector de aplicaciones al pulsar **Ver ahora**: servicios identificados por TMDB y reproductores locales configurados de forma independiente. Nuvio puede abrir fichas por ID TMDB y Stremio por ID IMDb; otras apps usan búsqueda Android (`ACTION_SEARCH`) o apertura de inicio.
- Salida «Aplicaciones» y Atrás para volver al dock del launcher original.

**Pendiente:** compilar en un entorno con Android SDK 34, ejecutar las pruebas Android, verificar con mandos y TV reales los intent de búsqueda de cada proveedor, confirmar variantes de nombres de paquetes y ajustes de rendimiento/seguridad. Los identificadores de proveedores cambian según país y producto.

## Apertura de títulos de TMDB en apps instaladas

Los servicios comerciales y los reproductores locales son categorías distintas. La disponibilidad de Netflix, Prime, Max, etc. se consulta mediante TMDB/JustWatch; las apps Nuvio, Stremio, Kodi, Plex, Jellyfin, Emby, VLC y Nova son destinos de apertura, **no** proveedores de disponibilidad TMDB. No se declara que un título esté disponible simplemente por tener instalada una app.

1. Al pulsar **Ver ahora** aparece un selector compatible con el mando, en el orden de servicios seleccionados y reproductores locales instalados.
2. **Nuvio**: intenta abrir la ficha exacta mediante `nuvio://tmdb/movie/{id}` o `nuvio://tmdb/tv/{id}`.
3. **Stremio**: obtiene en segundo plano `imdb_id` a través de `/movie/{id}/external_ids` o `/tv/{id}/external_ids` y utiliza un enlace de detalle `stremio:///detail/...`. Si no está disponible, usa su enlace de búsqueda por título.
4. **Otras apps**: busca con `ACTION_SEARCH` solo si la app lo admite o, en su defecto, abre la app seleccionada para que el usuario localice el título.
5. Una URL directa fallida solo pasa a búsqueda/apertura **en esa misma app**. Nunca lanza silenciosamente otra plataforma después de que el usuario haya elegido una.
6. El selector sigue funcionando con Nuvio, Stremio u otros reproductores aunque la consulta de disponibilidad TMDB haya fallado.

En **Ajustes → Plataformas** pueden activarse/desactivarse reproductores conocidos, y elegir manualmente **otras apps instaladas** desde el listado de aplicaciones lanzables de Android TV. No se pide `QUERY_ALL_PACKAGES` ni permisos especiales. Las apps adicionales requieren opt-in y pueden no admitir búsqueda ni enlaces a fichas. Las apps que Android no exponga como lanzables no se pueden enumerar con este método.

**Límite:** Android no proporciona un contrato universal para abrir fichas de cualquier servicio ni comprobar que haya fuentes de reproducción, cuentas o suscripciones activas. Los deep links pueden variar entre versiones. No se inicia automáticamente ningún vídeo ni se eluden DRM, permisos o suscripciones.

## Seguir viendo (Android TV)

Se usa `android.permission.READ_TV_LISTINGS`, solicitado de forma explícita al entrar en Seguir viendo. La consulta se realiza con proyección de columnas concreta y **sin selección SQL** para respetar las restricciones del TV Provider de Android. No se requiere acceso privilegiado, root, servicios de accesibilidad ni credenciales de Netflix/Max.

- Se leen filas visibles de `TvContract.WatchNextPrograms` con tipo *continue* y opcionalmente *next episode*.
- Opcionalmente se consultan `TvContract.Channels` y `TvContract.PreviewPrograms` para canales cuyo nombre anuncie claramente continuidad (algunas versiones de Stremio).
- Se muestra un elemento solo si está instalado el proveedor que publica la fila y el usuario lo ha activado. Se pueden habilitar otras apps no enumeradas explícitamente.
- Se ordenan primero las filas con actividad reciente, se pueden descartar filas antiguas (30–365 días) y progreso completo (≥95%), y se deduplican por aplicación/título.
- Se usan las URLs de póster y acción declaradas por el propio TV Provider. Los recursos se limitan a https, content o android.resource y su tamaño se acota.
- Si el enlace de continuar falla se abre el inicio de la app que lo publicó. El sistema no finge poder saber si se abrió el episodio exacto.
- Un canal de recomendaciones que no se declare de continuidad **no** se presenta como historial.
- La ausencia de filas no prueba ausencia de reproducciones. Algunos fabricantes y aplicaciones nunca publican su progreso a terceros o solo lo publican a la experiencia de Google TV.

Estos datos **no** se sincronizan con servidores del proyecto. El permiso puede revocarse desde Ajustes de Android TV.

## Significado de las filas de descubrimiento

| Tipo | Películas | Series |
| --- | --- | --- |
| Destacados | Tendencias TMDB del día | Tendencias TMDB del día |
| Más vistos | Tendencias TMDB de la semana, aproximación | Tendencias TMDB de la semana, aproximación |
| Novedades | En cartelera ahora | Series actualmente en emisión |
| Populares | Lista TMDB de películas populares | Lista TMDB de series populares |
| Mejor valorados | Lista histórica TMDB por valoración | Lista histórica TMDB por valoración |

**Las cinco categorías son de descubrimiento general** y no necesariamente filtran por un servicio. La disponibilidad efectiva se consulta por título y país en TMDB; se muestra como insignia solo para proveedores instalados y seleccionados. Stremio y Nuvio pueden ofrecer progreso en «Seguir viendo» y abrir fichas desde el catálogo, pero no son proveedores de suscripción de TMDB.

## Claves, privacidad y red

- La API key v3 de TMDB la introduce cada usuario en la TV. Se guarda mediante `SharedPreferences` privadas; no se registra en logs, no se envía al repositorio y no es una contraseña de streaming.
- La app solicita `android.permission.INTERNET` para consultar `api.themoviedb.org` y `image.tmdb.org`, sin servidor del desarrollador.
- JSON de catálogo se cachea por endpoint durante 6 horas; disponibilidad por título durante 12 horas. Si la red falla, se utilizan respuestas anteriores si existen.
- Los pósteres se cachean en disco y RAM (presupuesto de memoria de 8 MiB).
- No se solicitan permisos para controlar otras aplicaciones, leer cuentas o ver el historial de reproducción.

La caché tiene carácter informativo: la fuente definitiva es siempre la aplicación de streaming al abrirla.

## Comandos

```sh
git checkout feature/unified-catalog
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Para instalar al lado del paquete del proyecto original puede configurarse otro `applicationId`:

```sh
./gradlew --no-daemon -PlauncherApplicationId=io.github.spidey000.unifiedtv assembleRelease
```

No deshabilites el launcher de fábrica hasta comprobar que el nuevo funciona correctamente en la TV y que puedes volver atrás. Conserva un método de recuperación del sistema.

## Pruebas funcionales recomendadas

1. Sin clave TMDB: debe aparecer un mensaje y un acceso a los ajustes, sin ANR.
2. Con clave incorrecta: se informa del error HTTP; con clave válida: aparecen las cinco filas horizontales y se actualiza el destacado principal.
3. Desconecta Wi-Fi: deben conservarse las últimas respuestas cacheadas, si ya se habían descargado.
4. Comprueba si un proveedor instalado aparece correctamente, y que uno ausente no aparezca como seleccionable para reproducir.
5. Cambia el orden de prioridad, vuelve al catálogo y comprueba el orden de los servicios en el selector.
6. Desmarca todos los proveedores y reproductores: al pulsar un título se debe informar de que no existen apps habilitadas.
7. Busca una película y una serie: deben distinguirse y consultarse los endpoints correctos.
8. Verifica apertura directa de fichas en Nuvio/Stremio, búsqueda externa e inicio de los demás reproductores. Prueba tanto con la API TMDB disponible como con un fallo de red.
9. Pulsa «Aplicaciones» y Atrás: debe reaparecer el Home clásico; vuelve a pulsar Home.
10. Prueba la navegación D-pad en todos los bordes, el cambio Películas / Series, y que el póster 2:3 se expande a miniatura 16:9 sin saltos; comprueba también la opción de animaciones desactivadas, un televisor de poca RAM, reconexión de red y varias entradas/salidas.

## Futuro

- Enlaces de ficha validados por proveedor para abrir la pantalla exacta cuando exista un contrato oficial y estable.
- Verificación de API de disponibilidad/planes con licencia adecuada, si se decide adoptar un proveedor comercial.
- Portadas de plataformas con logos y atribuciones adecuadas, continuaciones del historial donde las apps lo expongan, filtros y favoritos.
- Mejoras de accesibilidad, selección por géneros y mejor paginación de resultados.

## Atribución

This product uses the TMDB API but is not endorsed or certified by TMDB.

Los datos de plataformas proceden de JustWatch a través de TMDB. Se mantienen la licencia MIT y la atribución a [CleanYourTV](https://github.com/Gathin23/cleanyourtv). Esta variante es un fork independiente.
