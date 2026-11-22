# Navegación espacial por mando — UnifiedTV

Adaptación nativa del comportamiento de [Norigin Spatial Navigation](https://github.com/NoriginMedia/Norigin-Spatial-Navigation) (conceptos, **no** la dependencia React): proximidad espacial, preferencia por elementos alineados, foco recordado y jerarquía rail/contenido.

## Ámbito

El controlador `TvSpatialNavigation` se aplica a todas las secciones de `UnifiedCatalogActivity`: Inicio, Películas, Series, Buscar, Mi lista/Seguir viendo, Apps y fuentes, TV en directo; y a todas las categorías de `UnifiedCatalogSettingsActivity`. Las ventanas y diálogos independientes, los editores de texto, los controles deslizantes y el launcher clásico conservan el foco Android original.

## Comportamiento

| Gesto | Regla |
| --- | --- |
| DPAD RIGHT desde rail | Recuperar el último elemento del panel, si aún existe; después enlace explícito o elemento espacialmente más cercano |
| DPAD LEFT desde contenido | Respetar `nextFocusLeft`; buscar vecino alineado, incluido el que la lista desplazará automáticamente; si no existe, regresar a la sección seleccionada del rail |
| DPAD UP/DOWN dentro de las listas | Respetar búsqueda nativa, enlaces explícitos y desplazamiento de Android para alcanzar elementos no visibles; solo corregir saltos fuera de la zona |
| DPAD UP/DOWN en rail | Mantener el orden de la navegación lateral |
| OK, pulsación larga, BACK | Sin cambios; los handlers de lanzamiento, ficha y regreso siguen siendo los originales |
| Buscar / EditText / diálogos / sliders | No capturar sus teclas: teclado y controles nativos conservan prioridad |

Las coordenadas son físicas y se prefieren candidatos que se solapan en el eje perpendicular. No se aplica `wrap-around` ni se fuerza el foco en una tarjeta oculta. `View.requestRectangleOnScreen` desplaza los contenedores cuando entra el foco; el scroll de carruseles y cuadrículas permanece en manos del framework.

## Validación

`TvSpatialNavigationTest` cubre dirección, alineación y distancia de candidatos. La compilación y los tests Android se ejecutan en CI. Falta comprobar con mando real o emulador: entradas y salidas del rail en cada pantalla, grid de 6 columnas, carruseles en sus extremos, Settings con scroll, teclado al editar TMDB, diálogos de detalles y retorno desde una plataforma. No se ha obtenido ninguna captura de emulador en esta implementación.
