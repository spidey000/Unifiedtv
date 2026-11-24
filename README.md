# UnifiedTV — Android TV / Google TV

UnifiedTV es un launcher de streaming para descubrir películas y series en TMDB,
consultar disponibilidad de proveedores, abrir fichas verificadas en las apps
instaladas, y unificar el contenido de «Seguir viendo» que Android TV exponga.

El diseño utiliza una barra lateral colapsable, pósteres 2:3 y una biblioteca
de apps en tarjetas 16:9 con seis columnas. La pulsación corta abre la ficha
del título en su plataforma cuando existe un enlace verificable; mantener OK
abre una ficha informativa. **TMDB no garantiza abrir la ficha exacta** de cada
servicio: puede depender de los enlaces públicos de la plataforma.

## Configuración
1. Instala la APK publicada en [Releases](https://github.com/spidey000/Unifiedtv/releases).
2. Abre **Ajustes → Catálogo** y vincula una clave TMDB con QR o mando.
3. En **Ajustes → Permisos**, comprueba el acceso a Android TV
   Watch Next, las actualizaciones y los permisos opcionales del mando.
4. En **Ajustes → Plataformas**, selecciona proveedores y reproductores.
5. En **Ajustes → Apariencia**, selecciona overlay: logotipo con nombre,
   icono de aplicación o ninguno. Configura también la puntuación ★ TMDB.
6. Para volver al inicio original del televisor, entra en **Ajustes → Recuperación**
   y selecciona la aplicación de inicio del fabricante en los ajustes de Android TV.

La aplicación no instala ni desactiva launchers del sistema automáticamente.
La elección de Home es responsabilidad del sistema y del usuario.

## Documentación del proyecto
- [Arquitectura y módulos](docs/architecture.md)
- [Limpieza de legacy y compatibilidad](docs/legacy-cleanup.md)
- [Permisos y puesta en marcha](docs/permissions-and-setup.md)
- [Guía de recuperación](docs/recovery.md)
- [Biblioteca y procedencia de logotipos](docs/design/provider-wordmarks.md)
- [Diseño de Apps y fuentes](docs/design/apps-and-sources.md)
- [Criterios de paridad visual React → Android TV](docs/design/native-tv-visual-parity.md)
- [Catálogo y API TMDB](docs/UNIFIEDTV.md)

## Desarrollo
Java / Android SDK 34, Android 8.0+; compilar con JDK 17:
`cd launcher && ./gradlew testReleaseUnitTest lintRelease assembleRelease`.
El CI publica releases firmadas; no almacenes claves ni certificados en el código.

## Add-ons
Se mantiene la carpeta `addons/` para integraciones modulares futuras.
Los complementos no están integrados en el launcher actual y deberán
adaptarse a la nueva arquitectura antes de habilitarse.

Los servicios de terceros y sus logotipos son marcas de sus titulares.
UnifiedTV es un proyecto independiente, sin afiliación comercial.
