# SJF Tesis v2.5.0

App Android para consultar el Semanario Judicial de la Federación.

## Novedades de esta versión (2.5.0)

### ⚖️ Precedente(s) de la tesis
- Nueva sección **Precedentes de la tesis**, como en el Semanario: cada ejecutoria de la que deriva la tesis aparece numerada con su registro y localización. Ejemplo, P./J. 2/2006:
  > **1. Registro 19501** — Novena Época. Pleno. Semanario Judicial de la Federación y su Gaceta, Tomo XXIII, Mayo de 2006, Pág. 339.
- Tócala para leer la **sentencia completa** dentro de la app, compartirla o abrirla en el sitio oficial
- Los asuntos con su votación y ponente siguen debajo, como «Asuntos que integran la tesis»
- Cada voto incluye ahora un enlace a su página en el sitio oficial

### 🔍 Pestaña «Buscar»
- La pestaña **Buscar** de la barra inferior siempre regresa a la pantalla de inicio, con una búsqueda nueva

## Novedades de la versión 2.4.0

### 🔍 Botón «Buscar»
- Regresa el botón **Buscar** junto al campo de búsqueda (en la 2.3.0 solo se podía buscar con la tecla del teclado). Con el campo vacío muestra las tesis más recientes

### 📚 Precedentes y votos
- **Precedentes** ordenados: cada asunto que integra la tesis aparece por separado y numerado («Amparo directo en revisión 6627/2025») con su fecha, votación y ponente; las notas, la aprobación y los criterios contendientes aparecen aparte
- Nueva sección **Votos**: los votos particulares, concurrentes y aclaratorios publicados con la tesis, con su autor; tócalos para leer el texto completo
- Las tesis guardadas conservan la lista de sus votos

## Novedades de la versión 2.3.0

### ⚖️ Citas con los datos oficiales
- Ahora se obtienen de la SCJN la **clave de la tesis** (p. ej. *P./J. 191/2026 (12a.)*), la **fuente**, el **libro, tomo y página** de la Gaceta y la **fecha y hora de publicación** del Semanario electrónico
- Las citas se rehicieron con esos datos:
  - **Cita jurídica**: «Lo anterior encuentra sustento en la jurisprudencia P./J. 191/2026 (12a.), emitida por el Pleno de la Suprema Corte de Justicia de la Nación, publicada en el Semanario Judicial de la Federación, Duodécima Época, el viernes 2 de octubre de 2026 a las 10:12 horas, registro digital 2032690, de rubro y texto siguientes: …»
  - **Ficha oficial**: los mismos campos y en el mismo orden que la ficha del Semanario
  - **Académica**: criterios editoriales de la SCJN («Tesis [J.]: …, p. 52. Reg. digital …»)
  - **Cita breve**: una línea con clave, rubro y localización, para notas al pie
- Si a una tesis le faltan datos oficiales, el diálogo de cita lo advierte
- El diálogo de cita abre en tu formato predeterminado

### 📖 Lectura de tesis
- La clave de la tesis aparece arriba del rubro; el rubro se muestra completo
- Las secciones **Hechos**, **Criterio jurídico** y **Justificación** se destacan
- Nuevo recuadro de **Publicación**: fecha de publicación y desde cuándo es de aplicación obligatoria
- Datos de localización completos: registro digital, tesis, época, instancia, tipo, materias, fuente, libro/tomo y página
- Las tesis guardadas con versiones anteriores se completan solas al abrirlas

### 🔌 Conexión más confiable
- **La primera tesis que se abría en cada sesión fallaba**: el firewall de la SCJN bloquea las consultas durante los primeros segundos de una sesión nueva, y la app respondía con ~30 s de intentos y un mensaje erróneo. Ahora la sesión se abre al iniciar la app, y si hay bloqueo se reintenta con espera (la tesis carga en segundos)
- Las tesis de la Gaceta impresa se piden directamente en su edición, con una consulta menos
- **Se eliminó la «base local» de tesis de ejemplo**: sin conexión, la búsqueda se hace en tus tesis guardadas (datos reales), nunca en contenido de muestra

### ✨ Interfaz más pulida
- **Mensajes con acción**: los avisos ahora aparecen en la parte inferior con botones útiles — **Deshacer** al quitar una tesis guardada o una búsqueda, **Abrir** al generar un PDF y **Ver** tras una descarga por lote
- **Filtros combinables**: tipo de documento y materia se eligen por separado (p. ej. *Jurisprudencia + Penal*)
- **Tema automático**: la app sigue el modo claro/oscuro del teléfono; también puedes fijar Claro u Oscuro en Ajustes
- **Búsquedas recientes** en la pantalla de inicio
- **Guardadas**: nuevo campo para filtrar por rubro, texto o número de registro
- Tarjetas de resultados más limpias, emblema vectorial y menos emojis en la interfaz
- Las herramientas de diagnóstico se movieron a *Ajustes › Avanzado*

### 🛠 Corregido
- El botón **PDF** de una tesis ya no abría una hoja vacía de «acciones por lote»
- Después de una descarga por lote, la barra quedaba atorada y no permitía descargar de nuevo
- El botón **Atrás** cerraba la app: ahora sale del modo selección o regresa a «Buscar»
- La lista de resultados conserva su posición al cambiar de pestaña
- La animación de inicio ya no se repite al girar la pantalla
- Borrar un archivo de Descargas pide confirmación, y los archivos de instalaciones anteriores ya se pueden eliminar
- Si falla la conexión al abrir una tesis, se muestra un botón **Reintentar** en lugar de un mensaje engañoso

## Cómo instalar

> ⚠️ **A partir de esta versión la app se firma con una llave nueva.** Android no permite instalarla
> encima de la versión anterior: hay que **desinstalar la app una sola vez** y
> después instalar `SJF-Tesis-v2.5.0.apk`. Las siguientes versiones volverán a
> instalarse como actualización normal.
>
> Al desinstalar se borran las **tesis guardadas** y el **historial**. Los PDF
> descargados se conservan en *Documents/SJF_Tesis*. Si necesitas una tesis
> guardada, expórtala a PDF o cópiala antes de desinstalar.

**Requiere Android 8.0 (API 26) o superior.**
