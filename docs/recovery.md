# Recuperación del launcher original de Android TV

La interfaz CleanYourTV heredada ya no se utiliza en UnifiedTV.
Para restablecer el **inicio del fabricante**:

1. En UnifiedTV abre **Ajustes → Recuperación**.
2. Selecciona **Recuperar launcher original / Cambiar Home**.
3. Confirma **Abrir selector de inicio**.
4. En el diálogo de Android TV selecciona la aplicación de inicio del
   fabricante (Google TV, Android TV Home u otra de tu marca).
5. Pulsa Home para comprobar que se abre la pantalla original.

Si el firmware no dispone de selector HOME, UnifiedTV intentará abrir
**Aplicaciones predeterminadas** y finalmente **Ajustes del sistema**.
Busca Aplicación de inicio / Home. No deshabilites paquetes de sistema.

Si el fabricante no tiene Home accesible, primero vuelve a habilitar el
launcher original por los ajustes de aplicaciones del televisor (si antes
lo desactivaste mediante ADB) y luego repite los pasos.

El cambio de aplicación predeterminada se realiza **dentro de Android**;
UnifiedTV no solicita root y nunca promete cambiar por sí sola un Home
que el fabricante no permite seleccionar.
