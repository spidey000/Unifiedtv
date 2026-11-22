# UnifiedTV — diseño y pruebas de integración

## Estado del MVP

Esta primera versión añade el catálogo unificado a la app nativa del fork. Implementado:

- Entrada a UnifiedTV desde el Home de Android TV, configurable.
- Pestañas **Películas**, **Series** y **Seguir viendo**. Las primeras ofrecen **Destacados**, **Más vistos**, **Novedades**, **Populares** y **Mejor valorados (histórico)**.
- Destacados = tendencia del día; Más vistos = tendencia semanal como aproximación, **no** reproducciones agregadas reales; Novedades = cartelera actual de cine o series actualmente en emisión; Populares y Mejor valorados = listas TMDB.
- Seguir viendo integrado mediante Android TV `Watch Next` (lectura con permiso) y canales `Continue Watching` disponibles, con origen, progreso y apertura de la acción de la app.
- Fuentes independientes por aplicación: Netflix, Max, Prime, Disney+, Apple TV+, SkyShowtime, Movistar, Filmin, Stremio, Nuvio y apps adicionales opcionales.
- Búsqueda por título en TMDB, idioma español.
- Carteles descargados en segundo plano y caché en el dispositivo.
- Consulta de proveedores por título y país usando los endpoints de TMDB `/movie/{id}/watch/providers` y `/tv/{id}/watch/providers`.
- Iconografía textual de plataformas compatibles solo cuando la aplicación está instalada, seleccionada y TMDB la identifica como opción de streaming.
- Preferencias: país, prioridad, casillas por servicio y modo Home; clave v3 introducida localmente.
- Apertura de apps con un intento de búsqueda Android (`ACTION_SEARCH`) y fallback al inicio de la aplicación; error cuando no existe ninguna opción de streaming seleccionada.
- Salida «Aplicaciones» y Atrás para volver al dock del launcher original.

**Pendiente:** compilar en un entorno con Android SDK 34, ejecutar las pruebas Android, verificar con mandos y TV reales los intent de búsqueda de cada proveedor, confirmar variantes de nombres de paquetes y ajustes de rendimiento/seguridad. Los identificadores de proveedores cambian según país y producto.

## Limitación de apertura de fichas

No existe un estándar Android TV que permita abrir la ficha de cualquier película de cualquier servicio usando solo un título de TMDB. En el MVP:

1. Se comprueba que el servicio está instalado, habilitado en preferencias y que TMDB lo considera disponible.
2. Se prueba una búsqueda `Intent.ACTION_SEARCH` específica de ese paquete si el propio servicio expone un handler compatible.
3. Si no existe handler, se abre la actividad principal de la app, con indicación para buscar el título manualmente.
4. Si la app falla al abrir, se prueba la siguiente plataforma disponible.
5. Si no hay proveedor instalado/activado para ese título se informa de ello, con enlace informativo opcional.

**No se garantiza la ficha exacta ni se lanza vídeo automáticamente.** No se utilizan servicios de descarga, scraping de cuentas, reproducción sin autorización, ni bypass de DRM.

La disponibilidad está indicada por TMDB/JustWatch y no demuestra que la persona tenga cuenta activa o plan compatible.

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

## Significado de los filtros

| Tipo | Películas | Series |
| --- | --- | --- |
| Destacados | Tendencias TMDB del día | Tendencias TMDB del día |
| Más vistos | Tendencias TMDB de la semana, aproximación | Tendencias TMDB de la semana, aproximación |
| Novedades | En cartelera ahora | Series actualmente en emisión |
| Populares | Lista TMDB de películas populares | Lista TMDB de series populares |
| Mejor valorados | Lista histórica TMDB por valoración | Lista histórica TMDB por valoración |

**Las cinco categorías son de descubrimiento general** y no necesariamente filtran por un servicio. La disponibilidad efectiva se consulta por título y país en TMDB; se muestra como insignia solo para proveedores instalados y seleccionados. Stremio y Nuvio son fuentes de progreso, no proveedores de suscripción en TMDB.

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
2. Con clave incorrecta: se informa del error HTTP; con clave válida: aparecen los tres carruseles.
3. Desconecta Wi-Fi: deben conservarse las últimas respuestas cacheadas, si ya se habían descargado.
4. Comprueba si un proveedor instalado aparece correctamente, y que uno ausente no aparezca como seleccionable para reproducir.
5. Cambia el orden de prioridad, vuelve al catálogo y comprueba que la primera opción disponible sea la prioritaria.
6. Desmarca todos los proveedores compatibles: al pulsar un título debe mostrar «No disponible».
7. Busca una película y una serie: deben distinguirse y consultarse los endpoints correctos.
8. Verifica búsqueda externa real y apertura del inicio de cada aplicación. Algunas apps no admiten `ACTION_SEARCH`.
9. Pulsa «Aplicaciones» y Atrás: debe reaparecer el Home clásico; vuelve a pulsar Home.
10. Prueba la navegación D-pad en todos los bordes, un televisor de poca RAM, reconexión de red y varias entradas/salidas.

## Futuro

- Enlaces de ficha validados por proveedor para abrir la pantalla exacta cuando exista un contrato oficial y estable.
- Verificación de API de disponibilidad/planes con licencia adecuada, si se decide adoptar un proveedor comercial.
- Portadas de plataformas con logos y atribuciones adecuadas, continuaciones del historial donde las apps lo expongan, filtros y favoritos.
- Mejoras de accesibilidad, selección por géneros y mejor paginación de resultados.

## Atribución

This product uses the TMDB API but is not endorsed or certified by TMDB.

Los datos de plataformas proceden de JustWatch a través de TMDB. Se mantienen la licencia MIT y la atribución a [CleanYourTV](https://github.com/Gathin23/cleanyourtv). Esta variante es un fork independiente.
