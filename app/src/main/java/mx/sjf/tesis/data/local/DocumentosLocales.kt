package mx.sjf.tesis.data.local

import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.model.Voto
import org.json.JSONObject
import java.io.File

/**
 * Ejecutorias y votos de las tesis guardadas, en archivos dentro del
 * almacenamiento privado de la app, para leerlos y descargarlos sin conexión.
 *
 * Un archivo JSON por documento: las ejecutorias pueden pasar de 150 000
 * caracteres, demasiado para SharedPreferences. La escritura pasa por un
 * archivo temporal y un renombrado para que un cierre a medias nunca deje un
 * documento corrupto.
 */
class DocumentosLocales(private val dir: File) {

    fun ejecutoria(registro: Long): Ejecutoria? = leer("ejecutoria", registro)?.let(Ejecutoria::fromJson)
    fun voto(registro: Long): Voto? = leer("voto", registro)?.let(Voto::fromJson)

    fun tieneEjecutoria(registro: Long) = archivo("ejecutoria", registro).exists()
    fun tieneVoto(registro: Long) = archivo("voto", registro).exists()

    fun guardar(e: Ejecutoria) = escribir("ejecutoria", e.registro, e.toJson())
    fun guardar(v: Voto) = escribir("voto", v.registro, v.toJson())

    /** Borra los documentos que ya no pertenecen a ninguna tesis guardada. */
    fun conservarSolo(ejecutorias: Set<Long>, votos: Set<Long>) {
        dir.listFiles()?.forEach { f ->
            val m = PATRON.matchEntire(f.name)
            val conservar = when (m?.groupValues?.get(1)) {
                "ejecutoria" -> m.groupValues[2].toLong() in ejecutorias
                "voto" -> m.groupValues[2].toLong() in votos
                else -> false
            }
            if (!conservar) f.delete()
        }
    }

    /** Espacio ocupado, en bytes. */
    fun tamano(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun archivo(tipo: String, registro: Long) = File(dir, "${tipo}_$registro.json")

    private fun leer(tipo: String, registro: Long): JSONObject? =
        runCatching { JSONObject(archivo(tipo, registro).readText()) }.getOrNull()

    @Synchronized
    private fun escribir(tipo: String, registro: Long, json: JSONObject) {
        dir.mkdirs()
        val destino = archivo(tipo, registro)
        val temporal = File(dir, destino.name + ".tmp")
        temporal.writeText(json.toString())
        if (!temporal.renameTo(destino)) {
            destino.delete()
            temporal.renameTo(destino)
        }
    }

    private companion object {
        val PATRON = Regex("""(ejecutoria|voto)_(\d+)\.json""")
    }
}
