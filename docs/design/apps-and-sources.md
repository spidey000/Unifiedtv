# Apps y fuentes — diseño Android TV

Implementación en `UnifiedCatalogActivity.apps()`, con geometría verificable en
`UnifiedCatalogLayout`. La interfaz sigue siendo Android Views/Java; no se carga
un navegador ni se copian recursos de marca desde ejemplos web.

## Referencias consultadas

- [Android TV — banner de aplicación 16:9](https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines): usar el `android:banner` real de la app como primera opción; no estirar iconos 1:1.
- [Android TV — focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system): distinguir con claridad estado normal y enfocado, conservar targets predecibles.
- [Android TV — navegación](https://developer.android.com/training/tv/get-started/navigation): desplazamiento DPAD y orden de foco bidimensional.
- [Norigin Spatial Navigation](https://github.com/NoriginMedia/Norigin-Spatial-Navigation): referencia open source establecida para navegación espacial en HTML/React Smart TV; adaptar su enfoque de cuadrículas navegables, sin importar React a la app nativa.
- [NOIR TV UI](https://github.com/zhengge6/noir-tv-ui): ejemplos web/HTML de UI cinematográfica, vidrio oscuro y foco; tomar la jerarquía y el control del foco como inspiración, no copiar assets.
- [tv-ui](https://github.com/jposn3r/tv-ui): ejemplo web de diseño para mando y pantallas de TV con TMDB; útil para comprobar el tratamiento de distintas relaciones de aspecto.

## Decisiones aplicadas

1. Seis columnas **fijas**, filas ilimitadas y un solo `ScrollView` **vertical**; ninguna carátula dispara scroll horizontal.
2. Cada miniatura ocupa un rectángulo **16:9**, preferentemente el banner de la actividad de Android TV, seguido por el banner de la aplicación. Si no existe, icono cuadrado centrado con `FIT_CENTER`; nunca deformar el logo.
3. El nombre aparece en una línea legible y truncada debajo del banner; contenido accesible con la etiqueta completa.
4. Superficie de vidrio oscuro, radio moderado, contorno blanco en foco y zoom leve, reservando margen físico para que las tarjetas no se solapen.
5. Navegación: `RIGHT` desde la sección del rail entra al primer icono; `LEFT/RIGHT` entre apps de una fila; `UP/DOWN` por filas con scroll automático; `OK` abre la app instalada; botones de gestión después del grid; `BACK` mantiene la navegación de la pantalla.
6. Carga diferida de banners cercanos a la zona visible; la enumeración del PackageManager y la lectura de recursos se hacen fuera del hilo principal.
7. Las proporciones de los pósteres del catálogo **no cambian**: siguen siendo 2:3. El 16:9 afecta exclusivamente a las aplicaciones.

## Validación

Los tests unitarios comprueban seis columnas sin overflow y el cociente 16:9. Verificar en emulador o dispositivo real que `UP/DOWN` cambia de fila y hace scroll, que el primer elemento toma foco al venir del rail, y que no hay clipping del borde al enfocar la última columna. No atribuir capturas a un emulador sin haberlas obtenido.
