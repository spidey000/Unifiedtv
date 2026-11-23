# Catálogo UnifiedTV

## Finalidad
UnifiedTV reúne una experiencia de descubrimiento para Android TV con
catálogo TMDB, disponibilidad regional por servicios de streaming, apertura de
fichas mediante enlaces verificados, y continuidad donde las apps publiquen
datos compatibles con Android TV. No accede a suscripciones privadas ni
garantiza la apertura de fichas de cualquier proveedor.

## Navegación
El panel izquierdo permite **Inicio**, **Películas**, **Series**, **Buscar**,
**Mi lista / Seguir viendo**, **Apps y fuentes**, **TV en directo** y **Ajustes**.

- Home muestra destacado, continuar viendo (si está autorizado y hay contenido)
  y categorías Destacados, Más vistos, Novedades, Populares y Mejor valorados.
- El formato del catálogo es póster vertical 2:3 en todas las tarjetas,
  incluidas las enfocadas; no se cambia a 16:9 en el foco.
- El modo normal son carruseles horizontales; Ajustes → Catálogo permite
  categorías en columnas con dos subcolumnas de carteles.
- «Apps y fuentes» muestra seis banners 16:9 por fila y scroll vertical.
- Corto OK en título: intenta abrir ficha de la plataforma preferida con
  disponibilidad TMDB y enlace correcto; si no lo hay, abre selector explícito.
- OK largo: ficha informativa completa, desplazable y sin reproducción automática.
- Los controles DPAD y BACK mantienen foco y evitan acceder al dock clásico.

## Insignias y puntuación
Ajustes → Apariencia:
1. **Logotipo con nombre** (wordmark local) predeterminado.
2. **Icono de aplicación** obtenido del sistema Android para la app instalada.
3. **No mostrar** overlay de plataforma.

La insignia solo aparece cuando está identificado un destino de ficha verificable.
La app incluye 23 marcas transparentes para futuras instalaciones:
Netflix, Prime Video, Disney+, HBO Max, Atresplayer, Apple TV, SkyShowtime,
Movistar+, Paramount+, Crunchyroll, Hulu, Peacock, Pluto TV, MUBI,
NOW, Tubi, YouTube, Channel 4, Crave, RTL, Sky, Starz y Viaplay.
Otros proveedores utilizan el logo TMDB si está disponible.

Ajustes → Apariencia permite también **Mostrar puntuación TMDB (★)**,
independiente del overlay. La puntuación no se inventa: se oculta si no hay
votos o coincidencia inequívoca del título.

## Seguir viendo y licencias
Se solicitan permisos explícitos para leer Android TV `WatchNextPrograms` y
`PreviewPrograms`. La fuente debe estar instalada y habilitada.
Solo se muestran títulos con actividad relevante y parámetros de porcentaje
o minutos configurables. Sin permiso, las fuentes no se leen.
Los reproductores Nuvio y Stremio son destinos locales opcionales: disponer
de ellos no implica que un título TMDB esté licenciado o presente.

## Ajustes de TMDB
El token personal v3 puede introducirse con el mando o desde el móvil mediante
QR y un servidor efímero local. Los accesos se mantienen en el dispositivo,
no en repositorios ni logs. La región inicial es ES.

## Actualizaciones y restauración
UnifiedTV puede comprobar nuevas versiones firmadas en GitHub y solicitar al
usuario permiso para instalar un APK; no instala sin confirmación.
Ajustes → Recuperación abre la selección del Home original del fabricante.
Antes de mostrarlo se desactivan las redirecciones de teclas Home de la
aplicación para evitar que el sistema la reabra involuntariamente. El usuario
puede reactivarlas manualmente. No se deshabilita ningún paquete de sistema.

## Desarrollo
Consulta [Arquitectura](architecture.md),
[Recuperación](recovery.md),
[Biblioteca de marcas](design/provider-wordmarks.md) y
[Diseño de Apps y fuentes](design/apps-and-sources.md).

Los add-ons heredados se conservan íntegros en `addons/`, sin instalarse por
defecto ni disponer todavía de una API estable en UnifiedTV.
