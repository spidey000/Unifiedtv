# UnifiedTV — guía para agentes

Este repositorio contiene una app **Android TV de catálogo unificado**,
no un proyecto de debloat o sustitución manual del launcher del sistema.

Lee `.agents/skills/unifiedtv-cinematic-design/SKILL.md`, `README.md`,
`docs/architecture.md` y `docs/recovery.md` antes de cambiar interfaz.

Principios:
- Nunca elimines el selector de launcher predeterminado del sistema.
- No desactives paquetes ni acciones del fabricante desde el APK.
- Las películas se abren por deep links verificados; un logo o app instalada
  nunca afirma disponibilidad de una película.
- Mantén los overlays por preferencia: wordmark, icono Android o ninguno;
  puntuación ★ TMDB independiente.
- Todas las tarjetas de contenido son 2:3 y el grid de apps seis columnas 16:9;
  el mando debe alcanzar cada elemento sin clipping ni focus trap.
- Prioriza una sola interfaz y evita añadir pantallas clásicas, fondos
  estacionales, ClockScene, SkyView o temáticas del launcher anterior.
- `addons/` debe permanecer intacto hasta que exista un contrato de módulos.
- Registra procedencia y licencias al añadir marcas, fuentes o imágenes.

Validación obligatoria: `cd launcher && ./gradlew testReleaseUnitTest lintRelease assembleRelease`
y `python -m unittest discover -s tests -p 'test_unifiedtv*.py' -v`.
Cada modificación mayor se envía mediante PR con pruebas y release.
