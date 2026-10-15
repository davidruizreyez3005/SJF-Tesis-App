package mx.sjf.tesis.data.util

/** Documento del Semanario al que apunta un enlace. */
sealed interface EnlaceSjf {
    val registro: Long
    data class DeTesis(override val registro: Long) : EnlaceSjf
    data class DeEjecutoria(override val registro: Long) : EnlaceSjf
    data class DeVoto(override val registro: Long) : EnlaceSjf
}

private val RX_SJF2 = Regex(
    """https?://sjf2\.scjn\.gob\.mx/detalle/(tesis|ejecutoria|voto)/(\d{1,9})""",
    RegexOption.IGNORE_CASE
)

// Enlaces del sistema anterior, todavía comunes en demandas y sentencias:
// https://sjf.scjn.gob.mx/SJFSist/Paginas/DetalleGeneralV2.aspx?ID=175940&Clase=DetalleTesisBL
private val RX_SJF_ANTERIOR = Regex(
    """https?://sjf\.scjn\.gob\.mx/SJFSist/Paginas/DetalleGeneral\w*\.aspx\?\S+""",
    RegexOption.IGNORE_CASE
)
private val RX_PARAM_ID = Regex("""[?&]id=(\d{1,9})""", RegexOption.IGNORE_CASE)
private val RX_PARAM_CLASE = Regex("""[?&]clase=([^&#\s]+)""", RegexOption.IGNORE_CASE)

/**
 * Busca un enlace del Semanario en [texto] (un enlace suelto o un texto
 * compartido que lo contiene). Devuelve null si no hay ninguno.
 */
fun enlaceSjf(texto: String): EnlaceSjf? {
    RX_SJF2.find(texto)?.let { m ->
        val registro = m.groupValues[2].toLong()
        return when (m.groupValues[1].lowercase()) {
            "ejecutoria" -> EnlaceSjf.DeEjecutoria(registro)
            "voto" -> EnlaceSjf.DeVoto(registro)
            else -> EnlaceSjf.DeTesis(registro)
        }
    }
    RX_SJF_ANTERIOR.find(texto)?.let { m ->
        val url = m.value
        val registro = RX_PARAM_ID.find(url)?.groupValues?.get(1)?.toLong() ?: return null
        val clase = RX_PARAM_CLASE.find(url)?.groupValues?.get(1).orEmpty()
        return when {
            clase.contains("ejecutoria", ignoreCase = true) -> EnlaceSjf.DeEjecutoria(registro)
            clase.contains("voto", ignoreCase = true) -> EnlaceSjf.DeVoto(registro)
            else -> EnlaceSjf.DeTesis(registro)
        }
    }
    return null
}
