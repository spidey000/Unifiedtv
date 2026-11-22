# Publicación automática de versiones · UnifiedTV

Cada fusión o push directo a `main` activa `.github/workflows/release.yml`. El proceso:

1. Ejecuta las pruebas específicas de UnifiedTV.
2. Compila el launcher con JDK 17 y Android SDK 34, pasando las pruebas unitarias y Android Lint.
3. Incrementa el código de versión de la APK mediante el número de ejecución de GitHub Actions.
4. Genera el tag `v0.1.N` y crea un GitHub Release con las notas de cambios.
5. **Solo con firma persistente configurada:** adjunta `UnifiedTV-v0.1.N.apk` y `SHA256SUMS`.

La publicación **no** se ejecuta si falla el build; una fusión por sí sola no prueba que la APK funcione en un televisor. Es necesario realizar pruebas reales con mando y apps instaladas antes de recomendar un release a usuarios finales.

## Activar Actions en el fork

GitHub puede deshabilitar Actions por defecto en repositorios recién bifurcados. Entra en **Repositorio → Actions** y activa los workflows de esta bifurcación si se muestra el mensaje de habilitación. Revisa también **Settings → Actions → General**, para permitir ejecutar workflows y conceder al job de publicación el permiso de contenidos `write` que declara el propio YAML. No hace falta dar permisos de escritura a los jobs de compilación.

Si Actions está deshabilitado, no se creará ningún release automáticamente, aunque el YAML esté dentro de `main`.

## Firma de APK estable (necesaria para actualizaciones)

No publiques una APK firmada con una clave debug efímera del runner: cambiaría su certificado de una ejecución a otra y Android rechazaría las actualizaciones.

Genera y conserva una clave de firma **en tu ordenador, no en GitHub ni en el chat**:

```bash
keytool -genkeypair -v \
  -keystore unifiedtv-release.jks \
  -alias unifiedtv \
  -keyalg RSA -keysize 3072 -validity 10000
```

Añade en **GitHub → Settings → Secrets and variables → Actions** estos cuatro secretos:

| Secreto | Valor |
| --- | --- |
| `UNIFIEDTV_KEYSTORE_BASE64` | Base64 del contenido del archivo `.jks` (sin saltos de línea) |
| `UNIFIEDTV_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `UNIFIEDTV_KEY_ALIAS` | Alias de la clave; por ejemplo `unifiedtv` |
| `UNIFIEDTV_KEY_PASSWORD` | Contraseña de la clave |

En Linux puedes obtener el valor Base64 con `base64 -w0 unifiedtv-release.jks`; no lo incluyas en commits, issues, PR, logs o mensajes. **Guarda una copia segura del `.jks` y sus contraseñas**. La pérdida de esa clave impide publicar actualizaciones compatibles de la misma aplicación.

El workflow comprueba que existen los cuatro secretos antes de cargar la clave en un archivo temporal privado; solo adjunta la APK si la firma se verificó. Cuando faltan secretos, publica un **release de código fuente solamente**.

## Versionado

- Tags: `v0.1.1`, `v0.1.2`, etc., generados a partir del número de ejecución del workflow de publicación.
- `versionName`: `0.1.N` dentro de la APK.
- `versionCode`: `10000 + N` dentro de la APK, para que Android acepte la siguiente versión.
- ID de paquete del release: `io.github.spidey000.unifiedtv`, distinto del CleanYourTV original.

Para un salto grande a `v1.0` hay que modificar en el workflow el prefijo `0.1` y mantener creciente el `versionCode`. Cualquier cambio significativo de contrato o permisos debe describirse en el release.

## Primer release tras la PR #1

Cuando `feature/unified-catalog` entre en `main`, el workflow de publicación se ejecutará si GitHub Actions está activo. Solo después de una ejecución satisfactoria deberá aparecer un release en [Releases](https://github.com/spidey000/Unifiedtv/releases). Si hay un fallo de compilación, revisa el log y corrige el error antes de publicar.

## Limitaciones de fuentes y continuidad

El catálogo de TMDB y el motor de recomendaciones de SweepMyTV **no son feeds de progreso de reproducción**. Para Seguir viendo, UnifiedTV usa Android TV Watch Next y canales de continuación voluntariamente publicados por las apps. SweepMyTV también observa sesiones multimedia con permiso de acceso a notificaciones y mantiene un historial propio; esa opción requiere consentimiento independiente y no permite reconstruir sesiones privadas de Netflix.

La versión actual incorpora metadatos de temporada/episodio, prioridad equitativa entre proveedores y detección por `LEANBACK_LAUNCHER`, observaciones útiles extraídas de [SweepMyTV WatchNext](https://github.com/akhiltaneja/SweepMyTv/blob/codex/initial-release/launcher/app/src/main/java/com/sweepmytv/launcher/WatchNext.java), sin copiar directamente su implementación.
