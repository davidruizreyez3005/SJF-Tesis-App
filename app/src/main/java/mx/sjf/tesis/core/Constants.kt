package mx.sjf.tesis.core

import mx.sjf.tesis.BuildConfig

/** Versiones y endpoints centralizados de la aplicación. */
object Constants {
    /** Versión visible; se define una sola vez en app/build.gradle.kts. */
    val APP_VERSION: String = BuildConfig.VERSION_NAME

    const val BASE_API = "https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public"
    const val SESSION_URL = "https://sjf2.scjn.gob.mx/busqueda-principal-tesis"
    const val DETAIL_URL_TEMPLATE = "https://sjf2.scjn.gob.mx/detalle/tesis/%d"
    const val ROOT_SITE = "https://sjf2.scjn.gob.mx/"
    const val APP_ID = "SJFAPP2020"

    /** Carpeta dentro de external-files donde se guardan PDFs y ZIPs. */
    const val FILE_DIR = "SJF_Tesis"

    /** User-Agent móvil consistente con el sitio. */
    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /** User-Agent de Googlebot (para intentar prerender). */
    const val UA_BOT =
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"

    val EPOCAS_TODAS = listOf(
        "Duodécima Época", "Undécima Época", "Décima Época", "Novena Época",
        "Octava Época", "Séptima Época", "Sexta Época", "Quinta Época",
        "Cuarta Época", "Tercera Época"
    )

    val MATERIAS = listOf(
        "Constitucional", "Penal", "Civil", "Laboral",
        "Administrativa", "Amparo", "Común", "Electoral"
    )
}
