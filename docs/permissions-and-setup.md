# Permisos y configuración de UnifiedTV

En la TV abre **Ajustes → Permisos y funcionamiento**. El panel comprueba el
estado real de las autorizaciones y ofrece accesos a las pantallas de Android.
Las autorizaciones no se conceden solas, ni se exige activar opciones avanzadas
para usar el catálogo. Al volver desde los ajustes del sistema, los estados
se actualizan conservando el elemento seleccionado con el mando.

| Elemento | ¿Es necesario? | Acceso / comportamiento |
| --- | --- | --- |
| Clave TMDB | Sí, para catálogo/puntuaciones | Vinculación QR o clave v3 mediante mando |
| Internet | Sí, para TMDB/descargas | Abre los ajustes de Wi-Fi; Ethernet también es válido |
| `READ_TV_LISTINGS` | Solo para Watch Next y canales que lo publiquen | Diálogo nativo Android; alternativa: permisos de la aplicación |
| Instalar APK de esta app | Solo para actualizar desde GitHub | Android solicita autorizar instalaciones desde UnifiedTV |
| Home predeterminado | Para usar UnifiedTV al pulsar Home | Selector Android TV, sin desactivar el launcher del fabricante |
| `HomeKeyService` accesibilidad | Solo para mandos OEM que lo necesiten | Configuración de accesibilidad del dispositivo |
| `InputKeyService` accesibilidad | Solo para tecla Entradas/HDMI en determinados televisores | Configuración de accesibilidad |
| `GuardRevival` listener | Opcional, algunas TVs matan procesos en segundo plano | Pantalla de acceso a notificaciones (permiso sensible) |
| `WRITE_SECURE_SETTINGS` | Solo funciones técnicas vía ADB | No puede concederse con un diálogo de Android TV |

El estado de red significa **red disponible detectada**; no implica que TMDB
esté funcionando ni que el móvil pueda alcanzar el QR. El permiso
`READ_TV_LISTINGS` no permite recuperar historiales privados de reproducción:
solo se leen las entradas que las aplicaciones publiquen a Android TV.

## Privacidad y seguridad

Un servicio de accesibilidad puede gestionar eventos de teclas o recibir
información sensible. El acceso a notificaciones también es un permiso amplio
aunque el listener de UnifiedTV no lea sus contenidos. Ambos son **opcionales**,
requieren una decisión explícita del usuario y nunca se habilitan automáticamente.

No solicitamos ubicación precisa: el catálogo, Wi-Fi/Ethernet y el emparejamiento
por QR no la necesitan. El antiguo permiso de ubicación se eliminó del manifest.

`WRITE_SECURE_SETTINGS` es una autorización avanzada mediante ADB. Solo si
sabes que necesitas la recuperación de servicios en un fabricante concreto,
puedes concederla desde un ordenador de confianza:

```bash
adb shell pm grant io.github.spidey000.unifiedtv android.permission.WRITE_SECURE_SETTINGS
```

El identificador real de la APK puede cambiar en compilaciones personalizadas.
Esta autorización permite modificar ajustes seguros y no es necesaria para
TMDB, Seguir viendo ni para elegir el Home predeterminado.

## Compatibilidad con fabricantes

Android TV/Google TV y los firmwares OEM no siempre ofrecen las mismas
pantallas de permisos. Cuando una acción de ajustes no está disponible,
UnifiedTV intenta abrir los ajustes generales y muestra instrucciones.
El permiso de instalación externa puede ocultarse según la versión de Android
y ciertas funciones de accesibilidad pueden estar restringidas por el fabricante.

Consulta [Recuperación del Home original](recovery.md) para evitar redirecciones
forzadas del botón Home si eliges volver al escritorio del fabricante.
