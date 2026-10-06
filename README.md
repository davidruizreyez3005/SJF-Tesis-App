# SJF Tesis

App Android nativa para consultar, leer y descargar tesis y jurisprudencias del Semanario Judicial de la Federación (SCJN). Es una versión mejorada de [sjf2.scjn.gob.mx/busqueda-principal-tesis](https://sjf2.scjn.gob.mx/busqueda-principal-tesis) para dispositivos móviles.

## ✨ Funciones principales

- **🔍 Búsqueda en tiempo real** contra `sjf2.scjn.gob.mx`, con filtros combinables por tipo (Jurisprudencia / Tesis Aislada) y materia (Penal, Civil, Laboral, Constitucional). Repite automáticamente palabra por palabra si la frase completa no arroja resultados.
- **⚡ Cachés en memoria**: los detalles consultados se guardan en un LRU (reabrir una tesis es instantáneo), las búsquedas repetidas se sirven de caché con TTL de 5 minutos y el microservicio correcto se recuerda por registro — sin probar los 4 endpoints cada vez.
- **🔄 Pull-to-refresh**: desliza hacia abajo en los resultados para reconsultar la SCJN; si la red falla, conservas lo que ya está en pantalla.
- **📚 Precedentes y votos**: los precedentes de la tesis (ejecutorias) con su localización y la sentencia completa, los asuntos que la integran y los votos particulares, concurrentes y aclaratorios con su texto completo.
- **📖 Lectura del texto completo**: la app obtiene el contenido de la página de detalle, intenta primero el prerendering de Googlebot y cae al cascarón JS si hace falta.
- **📑 PDF individual**: cada tesis se exporta a PDF con encabezado dorado, tabla de metadatos y cita al pie. Usa `android.graphics.pdf.PdfDocument` nativo, sin dependencias externas.
- **🗂 PDF combinado con índice**: varias tesis en un solo documento, con portada premium, tabla de contenido con números de página reales y puntos de guía.
- **📦 ZIP de PDFs**: cada tesis como PDF individual, empaquetados en un ZIP descargable y compartible.
- **✍️ Constructor de citas jurídicas** en 5 formatos, armados con los datos oficiales de localización (clave de la tesis, fuente, libro/tomo/página o fecha y hora de publicación, registro digital):
  - **Cita jurídica** — frase de sustento para escritos, con rubro, texto y precedentes
  - **Ficha oficial** — mismos campos y orden que la ficha del Semanario
  - **Académica** — criterios editoriales de la SCJN («Tesis [J.]: …, p. 52. Reg. digital …»)
  - **Cita breve** — una sola línea con clave, rubro y localización
  - **HTML** — fragmento embebible en páginas web
- **🔖 Selección múltiple**: activa el modo selección desde el ícono superior, marca varias tesis y ejecuta acciones por lote (exportar PDF combinado, copiar todas las citas, compartir). Las descargas por lote van en paralelo (máx. 3 simultáneas).
- **💾 Guardado offline**: tesis favoritas disponibles sin conexión; sin red, la búsqueda se hace sobre ellas.
- **🕘 Historial**: últimas 50 búsquedas (las más recientes también en la pantalla de inicio); desliza para eliminar, con opción de deshacer.
- **🎨 Tema premium**: paleta navy + dorado; sigue el modo claro/oscuro del sistema o se fija en Ajustes; escala de tipografía configurable.
- **🐛 Registro de red** (Ajustes › Avanzado): cada solicitud HTTP queda asentada con hora, cuerpo enviado y respuesta — útil para diagnosticar fallas o reportar bloqueos del WAF.

## 🧪 Pruebas

```bash
./gradlew testDebugUnitTest   # pruebas JVM: citas, fechas, texto, HTML, variantes de la API
```

Las pruebas corren también en CI antes de compilar la APK.

## 🏗 Arquitectura

El código está organizado por capas:

```
app/src/main/java/mx/sjf/tesis/
├── MainActivity.kt                       # Entry point
├── core/
│   ├── Constants.kt                     # URLs, versiones, épocas
│   └── NetLog.kt                        # Registro de red global
├── data/
│   ├── model/Models.kt                   # Tesis, Settings, BatchState, PdfExportMode
│   ├── local/Store.kt                    # SharedPreferences: settings, saved, history
│   ├── remote/SjfApi.kt                  # Cliente OkHttp, sesión Incapsula, reintentos ante el firewall
│   ├── remote/DetalleVariants.kt         # Variantes de edición del endpoint de detalle
│   └── util/
│       ├── TextUtils.kt                 # foldAccents, parseFecha, sanitizeFileName
│       ├── HtmlUtils.kt                 # decodificarEntidades, limpiarHtml, parrafosDeTexto
│       ├── CitationBuilder.kt            # 5 formatos de cita con datos oficiales
│       ├── PdfExporter.kt               # PDF individual / combinado / ZIP
│       ├── PdfStorage.kt                # Guardado en Documents/SJF_Tesis (MediaStore)
│       └── FileSharer.kt                # FileProvider intents
├── ui/
│   ├── theme/                            # Color, Typography, Theme
│   ├── components/                       # BrandMark, Buttons, Tags, Loading, ResultCard…
│   ├── navigation/SjfApp.kt             # Scaffold + NavigationBar
│   └── screens/
│       ├── search/SearchScreen.kt
│       ├── saved/SavedScreen.kt
│       ├── downloads/DownloadsScreen.kt
│       ├── history/HistoryScreen.kt
│       ├── settings/SettingsScreen.kt
│       ├── detail/DetailSheet.kt        # Hoja de detalle con acciones
│       ├── ejecutoria/EjecutoriaReader.kt # Lector de sentencias (precedentes de la tesis)
│       ├── batch/BatchActionSheet.kt    # Acciones por lote
│       ├── citation/CitationDialog.kt   # Constructor de citas
│       └── debug/DebugSheet.kt          # Panel de registro de red (Ajustes › Avanzado)
└── viewmodel/AppViewModel.kt            # Estado único de la app
```

## 🚀 Build

```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Requiere JDK 17 y Android SDK 34; el wrapper descarga Gradle 8.5 (con
verificación de checksum). Compatible con Android 8.0+ (API 26).

Para una APK de release firmada, exporta las variables `SIGNING_*` y ejecuta
`./gradlew assembleRelease` (ver [SIGNING.md](SIGNING.md)). La llave de firma
nunca se guarda en el repositorio.

## 📦 CI/CD

El workflow [`build.yml`](.github/workflows/build.yml) corre en cada push a
`main`, en tags `v*`, en pull requests y a mano (**Actions › Build APK › Run
workflow**):

1. Pruebas unitarias.
2. **Con** los secretos de firma: APK de release firmada, verificación de la
   firma y publicación como release `v<versión>` en la página de
   [Releases](../../releases).
3. **Sin** los secretos (o en pull requests): APK de depuración, descargable
   como artefacto de la corrida.

La versión se toma de `versionName` en `app/build.gradle.kts` y las notas de
`RELEASE_NOTES.md`: para publicar una versión nueva basta con actualizar esos
dos archivos.

## 📋 Endpoints utilizados

La API pública del microservicio `sjftesismicroservice`:

- **`GET /busqueda-principal-tesis`** — Establece sesión (cookies Incapsula `visid_incap_*`, `incap_ses_*`)
- **`POST /services/sjftesismicroservice/api/public/tesis?page=0&size=20`** — Búsqueda con clasificadores
- **`GET /services/sjftesismicroservice/api/public/query-field/SJFAPP2020`** — Metadatos de facetas
- **`GET /detalle/tesis/{registro}`** — Página de detalle (HTML cascarón o prerendered)
- **`GET /content/images/tipo-juris/*.png`** — Iconos de tipo de jurisprudencia

El cuerpo oficial de búsqueda replica el del sitio, con `classifiers` por época (todas), tipo de documento y materia, y `searchTerms` con campos `localizacionBusqueda`, `rubro`, `texto`.

## ⚖️ Aviso

Aplicación **no oficial**. Los datos provienen de fuentes públicas de la Suprema Corte de Justicia de la Nación. Esta app no está afiliada ni respaldada por la SCJN.

## 📝 Licencia

Código abierto para uso educativo y profesional.
