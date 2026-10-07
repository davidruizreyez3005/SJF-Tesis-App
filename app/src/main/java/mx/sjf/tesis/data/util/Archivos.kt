package mx.sjf.tesis.data.util

/** Clase de archivo generado por la app, según su nombre (ver [mx.sjf.tesis.pdf.DocumentosPdf]). */
enum class TipoArchivo(val etiqueta: String) {
    TESIS("Tesis"),
    EJECUTORIA("Ejecutoria"),
    VOTO("Voto"),
    EXPEDIENTE("Expediente"),
    DOCUMENTOS("Documentos de la tesis"),
    COMPILACION("Compilación"),
    LOTE("Lote de tesis"),
    OTRO("Archivo")
}

/** Tipo y título legible de un archivo de Descargas. */
data class ArchivoDescrito(val tipo: TipoArchivo, val titulo: String)

/**
 * «Tesis_P-J-2-2006_175940.pdf» → Tesis · «P-J-2-2006 · Registro 175940»;
 * «Ejecutoria_19501.pdf» → Ejecutoria · «Registro digital 19501», etc.
 * Los nombres que no siguen el patrón se muestran tal cual.
 */
fun describirArchivo(nombre: String): ArchivoDescrito {
    // Sin extensión ni el «(1)» que agrega el sistema a los duplicados.
    val base = nombre.substringBeforeLast('.').replace(Regex(" \\(\\d+\\)$"), "")
    fun partes(prefijo: String) = base.removePrefix(prefijo).split('_').filter { it.isNotBlank() }
    fun claveYRegistro(p: List<String>): String {
        val registro = p.lastOrNull()?.takeIf { it.all(Char::isDigit) }
        val clave = (if (registro != null) p.dropLast(1) else p).joinToString(" ")
        return when {
            clave.isNotBlank() && registro != null -> "$clave · Registro $registro"
            registro != null -> "Registro digital $registro"
            else -> clave.ifBlank { nombre }
        }
    }
    return when {
        base.endsWith("_documentos") && base.startsWith("Tesis_") ->
            ArchivoDescrito(TipoArchivo.DOCUMENTOS, claveYRegistro(partes("Tesis_").dropLast(1)))
        base.startsWith("Tesis_") -> ArchivoDescrito(TipoArchivo.TESIS, claveYRegistro(partes("Tesis_")))
        base.startsWith("Expediente_") -> ArchivoDescrito(TipoArchivo.EXPEDIENTE, claveYRegistro(partes("Expediente_")))
        base.startsWith("Ejecutoria_") -> ArchivoDescrito(TipoArchivo.EJECUTORIA, claveYRegistro(partes("Ejecutoria_")))
        base.startsWith("Voto_") -> ArchivoDescrito(TipoArchivo.VOTO, claveYRegistro(partes("Voto_")))
        base.startsWith("SJF_Tesis_Compilacion") -> ArchivoDescrito(TipoArchivo.COMPILACION, "Varias tesis en un PDF")
        base.startsWith("SJF_Tesis_Combinado") -> ArchivoDescrito(TipoArchivo.COMPILACION, "Varias tesis en un PDF")
        base.startsWith("SJF_Tesis_") -> ArchivoDescrito(TipoArchivo.LOTE, "Un PDF por tesis")
        else -> ArchivoDescrito(TipoArchivo.OTRO, nombre)
    }
}
