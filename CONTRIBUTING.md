# Contribuir a UnifiedTV

Las contribuciones han de tratar exclusivamente el launcher audiovisual,
Android TV, compatibilidad con mandos, servicios de catálogo y reproducción,
actualizaciones, accesibilidad o módulos futuros.

1. Crea una rama descriptiva desde `main`.
2. Lee `AGENTS.md` y el skill de diseño en `.agents/skills/`.
3. Si modificas UI, comprueba foco con DPAD, clipping, ratios 2:3 y 16:9.
4. Incluye pruebas y ejecuta Gradle (JDK 17, SDK 34).
5. Adjunta capturas reales cuando tengas emulador o TV compatible.
6. Crea PR; tras verificación y merge, publica una release si procede.

No incluyas secretos TMDB, APKs sin procedencia, logotipos sin atribución,
ni instrucciones para deshabilitar componentes críticos de Android.
Los complementos de `addons/` se conservarán sin cambios funcionales
hasta definir una API pública de extensiones.
