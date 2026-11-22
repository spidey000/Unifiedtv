---
name: unifiedtv-cinematic-design
description: Diseño y revisión visual de UnifiedTV (Android TV, Google TV y portal móvil de vinculación TMDB). Usar para crear, rediseñar o auditar pantallas, tarjetas, filtros, navegación con mando, ajustes, diálogos y estados de emparejamiento. Estética cinematográfica inspirada en productos de streaming: contenido protagonista, vidrio oscuro y foco inequívoco.
---

# UnifiedTV — Cinematic UI design skill

## Contexto y precedencia
Este skill está hecho para el código real de UnifiedTV, no para reproducir una captura de Netflix. Lee primero `AGENTS.md`, `launcher/app/src/main/java/com/gathin/tvhome/StreamUi.java`, `UnifiedCatalogActivity.java`, `UnifiedCatalogSettingsActivity.java`, las preferencias y los recursos. Revisa también los PR abiertos: los cambios de la app pueden estar en una rama aún no fusionada.

Prioridad: (1) instrucciones explícitas del usuario y seguridad, (2) componentes y tokens existentes, (3) este skill, (4) referencias externas. No copies marcas, logotipos, iconografía, títulos ni diseños propietarios de Netflix.

## Objetivo de producto
Un único punto de entrada a películas y series de TMDB y a los reproductores instalados (Netflix, Prime Video, HBO/Max, Nuvio, Stremio, etc.). El catálogo sirve para descubrir, no para aparentar acceso a contenido o historiales privados que Android no proporciona. Cada decisión de interfaz debe reducir pasos con el mando y dar protagonismo al arte real de la película o serie.

## Sistema visual: tokens
Define los colores en un lugar compartido; evita hexadecimales dispersos y componentes duplicados.

| Uso | Valor de partida | Función |
|---|---|---|
| Fondo cine | `#09090D` | Negro casi puro, sin bloques grisáceos grandes |
| Superficie base | `#17171E` | Capas bajo los paneles |
| Glass oscuro | `#24242C` al 76–88 % | Barra de filtros, menús, botones |
| Glass elevado | `#30303A` al 90–96 % | Foco, diálogo, panel flotante |
| Borde sutil | `#FFFFFF` al 10–16 % | Delimita sin crear una cuadrícula visual |
| Texto | `#F7F7FA` | Titulares y acciones |
| Texto secundario | `#B4B4C1` | Datos, ayuda, estados |
| Acento | `#E72C4A` | Selección, progreso y detalles puntuales |
| Estrella TMDB | `#F4C66F` | Únicamente la estrella de puntuación |

No uses gradientes en **botones**. Las imágenes de fondo sí pueden tener un oscurecimiento gradual para legibilidad. Reservar translucidez/blur real para escenarios que lo soporten: el launcher debe seguir siendo fluido en Android 8+/televisores modestos. Un sólido semitransparente con brillo interior de 1 px es un fallback válido.

Tipografía: usa las fuentes ya incluidas en el proyecto; máximo dos pesos visibles por zona (semibold y bold). No uses mayúsculas en títulos por defecto. El fondo y el arte no deben competir con la tipografía.

## Geometría e interacción para TV
- Rail izquierdo **colapsado a iconos**, ancho inicial aprox. 52 dp; al recibir foco, expandir a 140–160 dp con transición corta (180–220 ms); restaurarlo al salir. No desplazar el póster protagonista ni perder el foco en medio de la animación.
- Las secciones **Películas / Series** son el único selector superior del catálogo. **Destacados / Más vistos / Novedades / Populares / Mejor valorados** son cinco filas horizontales independientes **debajo de Seguir viendo**, nunca pestañas ni botones superiores.
- Las filas horizontales muestran como máximo **seis carátulas visibles** a la vez. Todas las tarjetas se mantienen **siempre en 2:3**, tanto enfocadas como sin foco. El foco permanece en una posición lateral estacionaria mientras el carrusel se desplaza bajo él al navegar por DPAD izquierda/derecha. La señal de foco es un borde sutil y una escala uniforme mínima, sin cambiar el ancho ni la proporción del póster. En «Seguir viendo → Ver todo», usa un **grid de seis columnas y scroll vertical**, con celdas fijas.
- Mantén el contenido dominante: bordes y controles muy discretos. Todas las carátulas del catálogo, incluyendo la enfocada y «Seguir viendo», son **pósteres 2:3** con imagen **CENTER_CROP centrada**. Nunca convertir el foco a 16:9 ni mostrar una segunda preview panorámica. Prioriza el arte vertical de la plataforma (`poster_art_uri` o, si sirve, `thumbnail_uri`); si no hay una imagen vertical adecuada intenta el póster de TMDB mediante coincidencia conservadora de título, tipo y año, sin bloquear el primer render. Si solo hay miniatura horizontal, se recorta al centro dentro de la tarjeta 2:3.
- No uses un zoom fuerte para suplir la señal de foco. Una elevación moderada, borde fino luminoso y cambio de tamaño/proporción suave deben indicar qué recibirá OK. Prevén el espaciado para que el foco no tape tarjetas vecinas.
- Las valoraciones muestran **★ 8,2** junto al título, **sin la palabra TMDB**. Evita más de una línea de metadatos si las carátulas ya aportan el contexto.
- Botones oscuros de tipo glass, bordes finos, radios 10–14 dp. Cada pantalla tiene una acción principal clara y secundarios de menor peso. No uses gradiente, neón persistente ni sombras llamativas en cada tarjeta.
- Android TV se usa a 2–3 m. Los objetivos de foco deben tener áreas utilizables amplias incluso si se dibujan compactos; nunca sacrifiques accesibilidad por meter más filas.
- Haz un mapa explícito de navegación DPAD_UP/DOWN/LEFT/RIGHT y BACK. Comprueba cada borde, retorno desde ajustes, restauración de foco tras cambios de pestaña y ausencia de focus traps.
- Respeta `prefers-reduced-motion` en web y los ajustes del sistema para animaciones en TV; no añadas animaciones gratuitas.

## Organización de pantallas
**Inicio:** navegación izquierda; selector compacto Películas / Series, un hero con arte 16:9 de Destacados, después Seguir viendo cuando corresponda y las cinco filas de categorías. No llenes el primer viewport de texto ni de botones. El arte manda.

**Seguir viendo:** en Inicio es horizontal con hasta seis títulos visibles; «Ver todo» es un grid de seis columnas con scroll vertical y todas las entradas disponibles. Solo títulos que superen el umbral configurable (por defecto, **5 minutos o 5 %** reproducido, según la regla implementada), con progreso distinguible y plataforma asociada cuando el dato sea accesible. No inventes progreso ni atribuyas contenido a otra plataforma.

**Ver en:** al pulsar una carátula del catálogo se prioriza automáticamente la primera plataforma instalada, seleccionada y con oferta válida en TMDB que disponga de un enlace a la ficha: primero `intent_uri` del proveedor Android TV con coincidencia verificada; si no, enlace a partir de las propiedades de Wikidata enlazadas por el ID TMDB. El logotipo superpuesto muestra esa plataforma elegida solo con enlace verificable. Nuvio, Stremio y otras apps instaladas siguen en el selector. Si falta un enlace directo, explica la limitación sin prometer que la app abrirá la ficha y no presenta abrir su portada como equivalente.

**Ajustes:** panel lateral estable + columna principal con controles agrupados. Usa etiquetas concretas, estados elegidos visualmente distintos y explicación corta. La clave TMDB ha de poderse introducir con el mando como alternativa de accesibilidad.

**Vinculación de TMDB por QR:** pantalla de TV con QR grande, instrucciones breves y caducidad; móvil con formulario responsivo en panel glass oscuro. El botón primario debe ser "Guardar en la TV" y nunca un genérico "Enviar". Estados: clave incorrecta, problema de red, caducidad, guardado correcto. El móvil y la TV confirman el resultado. No imprimas ni registres claves. Si se usa HTTP local, advierte que solo se use en una red doméstica de confianza. Mantén cierre y caducidad del servidor, sin convertirlo en un servicio persistente.

## Método del skill (obligatorio para cambios visuales)
1. **Inventario.** Identifica pantallas/componentes afectados, tokens actuales, limitaciones del dispositivo y PR abiertos. No rediseñes a ciegas.
2. **Diseño primero.** Escribe un plan corto con paleta, tipografía, geometría, estado de foco y una jerarquía de información. Pregunta: ¿el arte de cine sigue siendo protagonista? ¿Es único el gesto de selección?
3. **Implementación reutilizable.** Cambia primero `StreamUi` o recursos compartidos cuando proceda; luego una pantalla representativa; después propaga. No copies CSS Java en cada clase.
4. **Estados reales.** Renderiza con títulos y pósteres válidos (y también sin API, sin conexión, carga lenta, clave errónea). No juzgues un diseño lleno de placeholders.
5. **Capturas comparables.** Obtén capturas de la misma resolución y estado antes/después; revisa foco, saturación, espacio libre, texto y recortes. Para web, también móvil estrecho. Si no hay dispositivo o navegador, dilo y deja la validación física pendiente; no inventes capturas.
6. **Calidad.** Verifica compilación, lint y tests aplicables, navegación completa por mando, accesibilidad y rendimiento. Conservar el fallback sin blur en hardware limitado.

## Checklist de revisión de 60 segundos
- ¿En un primer vistazo identifico película/serie, título, valoración y acción?
- ¿Las imágenes ocupan más atención que la UI?
- ¿Un solo elemento está inequívocamente enfocado?
- ¿El rail se repliega y las cinco categorías aparecen como filas bajo Seguir viendo, sin botones de filtro?
- ¿Los botones son glass oscuros, no gradientes?
- ¿Todos los pósteres mantienen 2:3, también en foco, sin preview 16:9?
- ¿Funciona el mando sin ratón ni teclado?
- ¿Qué ocurre con ausencia de red, TMDB y apps de reproducción?
- ¿El móvil puede guardar TMDB en menos de un minuto sin teclear en TV?
- ¿Se tomaron capturas y se revisaron frente al resultado solicitado?

## Referencias de método (inspiración, no copia)
- [Anthropic — frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design): dirección visual con decisiones deliberadas, jerarquía y autocrítica.
- [ui-craft — Achref Arabi](https://github.com/Achref23illi/ui-craft): auditoría de pantallas reales de apps, incluyendo Netflix y Apple TV; referencia de geometría, densidad y captura comparativa. Las medidas de iOS no se trasladan literalmente a TV.
