# Publicación de APK firmadas · UnifiedTV

[Última versión](https://github.com/spidey000/Unifiedtv/releases/latest) · [Todas las releases](https://github.com/spidey000/Unifiedtv/releases) · [Ejecuciones](https://github.com/spidey000/Unifiedtv/actions/workflows/release.yml)

## Preparar la firma (una vez)

1. Verifica si ya se distribuyeron APK de `io.github.spidey000.unifiedtv`, incluso fuera de GitHub. Si existen, recupera el **mismo keystore** y confirma su certificado con `apksigner verify --print-certs archivo.apk` antes de continuar. `v0.1.2` se publicó solo con código fuente. No reemplaces ni regeneres una clave existente sin consultar al responsable de la firma.
2. Si confirmas que no hay una clave previa, genera una en un equipo de confianza con JDK 17, **fuera del repositorio**. `keytool` pide las contraseñas sin incluirlas en el comando. Guarda una copia cifrada en almacenamiento privado y los datos de recuperación en un gestor de contraseñas. No continúes sin la copia de seguridad.

   ```bash
   umask 077
   mkdir -p "$HOME/.local/share/unifiedtv-signing"
   chmod 700 "$HOME/.local/share/unifiedtv-signing"
   KEYSTORE="$HOME/.local/share/unifiedtv-signing/unifiedtv-release.jks"
   test ! -e "$KEYSTORE" && keytool -genkeypair -storetype JKS -keystore "$KEYSTORE" \
     -alias unifiedtv -keyalg RSA -keysize 3072 -validity 10000
   ```

3. Con una cuenta GitHub que pueda administrar Actions Secrets en `spidey000/Unifiedtv`, configura los cuatro secretos de repositorio. Desde una terminal Bash autenticada con `gh`, puedes hacerlo sin imprimir valores ni escribir archivos Base64:

   ```bash
   set +x
   set -o pipefail
   REPO=spidey000/Unifiedtv
   base64 -w 0 "$KEYSTORE" | gh secret set UNIFIEDTV_KEYSTORE_BASE64 --repo "$REPO"
   read -rs -p 'Contraseña del keystore: ' password; printf '\n'
   printf '%s' "$password" | gh secret set UNIFIEDTV_KEYSTORE_PASSWORD --repo "$REPO"
   unset password
   printf '%s' unifiedtv | gh secret set UNIFIEDTV_KEY_ALIAS --repo "$REPO"
   read -rs -p 'Contraseña de la clave: ' password; printf '\n'
   printf '%s' "$password" | gh secret set UNIFIEDTV_KEY_PASSWORD --repo "$REPO"
   unset password
   gh secret list --repo "$REPO"   # muestra nombres, no valores
   ```

   Adapta el alias si reutilizas una clave que ya se distribuyó. También puedes gestionar los secretos en [Settings → Secrets and variables → Actions](https://github.com/spidey000/Unifiedtv/settings/secrets/actions). Nunca subas el `.jks` como archivo, ni pegues claves o contraseñas en chats, issues o logs.
4. En **Settings → Secrets and variables → Actions → Variables**, define `UNIFIEDTV_SIGNER_SHA256` con la huella SHA-256 del **certificado público**. Se obtiene con `keytool -list -v -keystore "$KEYSTORE" -alias unifiedtv`; el workflow acepta la huella con o sin `:`. No uses el SHA-256 del `.jks` o del APK como huella.

## Validar y publicar la primera APK

1. Habilita Actions si el fork todavía no los tiene activos. Deja `UNIFIEDTV_PUBLISH_ENABLED` sin configurar. Fusiona el workflow revisado a `main`.
2. En [Publish UnifiedTV releases](https://github.com/spidey000/Unifiedtv/actions/workflows/release.yml) selecciona **Run workflow** desde `main` con `publish=false`. Descarga el artefacto `unifiedtv-signed-release` del run exitoso. Verifica `sha256sum -c SHA256SUMS`, el paquete y la huella. Instálalo en un emulador Android TV o una TV de pruebas con Android 8+ mediante `adb install -r`; no cambies el launcher predeterminado para la prueba. La comprobación estática del APK no sustituye este paso.
3. Prueba con el mando: Películas y Series (Destacados, Más vistos, Novedades, Populares y Mejor valorados), búsqueda, visibilidad de proveedores y prioridad en Ajustes, Watch Next con apps que lo publiquen, y regreso al launcher original. Introduce una clave TMDB de pruebas localmente. No pruebes con las preferencias o cuentas de usuarios sin permiso.
4. Tras la validación, define la variable `UNIFIEDTV_PUBLISH_ENABLED=true`. Lanza el workflow manualmente desde el mismo commit de `main` con `publish=true`; los pushes no pueden publicar la primera APK. El workflow comprueba que el tag apunte al SHA del run y vuelve a descargar sin autenticación la APK y `SHA256SUMS` para comprobar el hash. Puedes repetir `sha256sum -c SHA256SUMS` y `apksigner verify --print-certs UnifiedTV-vX.Y.Z.apk` sobre la descarga pública. No sobrescribas `v0.1.2`.

## Publicaciones sucesivas

- Después de activar la publicación, los merges y pushes a `main` disparan el workflow. Las ejecuciones se serializan. GitHub puede reemplazar ejecuciones pendientes y el workflow omite heads ya superados; la siguiente release incorpora todos los commits incluidos en el `main` validado. Si necesitas una release por cada SHA, espera a que termine la publicación antes de fusionar el siguiente cambio.
- Se toma el mayor tag estable `vMAJOR.MINOR.PATCH`, contrastado con las releases publicadas. El mayor incremento entre los commits nuevos gana: `tipo!:` / `tipo(ámbito)!:` o `BREAKING CHANGE:` → **MAJOR**; `feat:` / `feat(ámbito):` → **MINOR**; cualquier otro cambio → **PATCH**. Usa esos prefijos en el título final si haces squash merge.
- El `versionName` coincide con el tag sin `v`. El primer `versionCode` empieza en `11000`; luego se verifica la última APK oficial publicada y se incrementa su código en uno. Si hubo APK oficiales fuera de GitHub, registra y revisa ese historial antes de usar el código inicial. Cada APK nueva y la anterior deben tener el mismo paquete y el certificado fijado.
- GitHub Actions compila con Java 17, Android SDK 34 y Build Tools 34.0.0, ejecuta pruebas, lint y `assembleRelease` **sin credenciales de firma**. Un paso posterior firma con la clave permanente, comprueba firma y certificado, paquete, versión, SDK, permisos, tamaño y patrones de credenciales conocidas. Este último control no sustituye una revisión de secretos. La release queda como borrador hasta volver a descargar y verificar los dos assets. Las notas automáticas abarcan los cambios desde el tag anterior.

## Recuperación y actualizaciones

- Si no hay secretos, certificado válido o compilación correcta, no se publica nada. Consulta el run y corrige la causa en una PR. Para pausar la publicación, cambia `UNIFIEDTV_PUBLISH_ENABLED` a `false`.
- Si un run falla después de crear el tag, comprueba primero si llegó a publicarse, qué SHA tiene y si existe un borrador. **No borres ni reescribas tags publicados**. Si quedó un borrador incompleto o un tag huérfano, un mantenedor puede retirar únicamente ese borrador y su tag **sin publicar**, después de verificar el SHA, y relanzar desde `main`. El cálculo de la versión se bloquea ante inconsistencias en vez de saltarse un tag.
- Instala y actualiza la APK oficial con `adb install -r UnifiedTV-vX.Y.Z.apk` tras verificar `SHA256SUMS`. No desinstales ni borres datos ante un error. Comprueba el certificado si Android rechaza la actualización. Mantén el ID `io.github.spidey000.unifiedtv`, las preferencias y el almacenamiento existentes en cualquier migración; prueba una actualización sobre la APK anterior con ajustes TMDB y prioridades de plataformas ya guardados.
- Watch Next solo puede mostrar el progreso que las apps publican en Android TV. No asumas que todos los proveedores exponen su historial ni que una instalación sin datos puede recuperar progreso privado.
