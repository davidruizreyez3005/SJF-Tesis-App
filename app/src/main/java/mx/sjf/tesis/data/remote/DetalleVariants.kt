package mx.sjf.tesis.data.remote

/**
 * Variantes de edición para el endpoint de detalle de tesis.
 *
 * El sitio oficial NO siempre pide el detalle con los mismos parámetros: el
 * servicio Angular `obtenerDetalleTesis(t, n)` recibe los parámetros del
 * llamador, y éstos cambian según la edición en la que se publicó cada tesis.
 *
 * Durante el desarrollo de v2.2.0 se adoptó únicamente `isSemanal=true`
 * (capturado en DevTools sobre una tesis semanal de 2025). Con esa única
 * variante, las tesis publicadas en la Gaceta impresa (p. ej. ius 190955
 * «P. CLXV/2000», ius 188600 «P. XX/2001») y algunas de la Décima Época
 * (ius 2004415, 2004236) NO cargan el texto completo, aunque el sitio web
 * sí las muestra.
 *
 * La solución es probar una matriz corta de variantes hasta dar con la que
 * el servidor responde con texto completo:
 *
 *  - [SEMANAL]  `isSemanal=true`  → publicaciones del Semanario digital semanal
 *  - [PLANA]    sin parámetro de edición (formato base del sitio oficial)
 *  - [GACETA]   `isGaceta=true`   → parámetro usado por el sitio en servicios
 *                de índices/gaceta; candidata natural para la Gaceta impresa
 *  - NO_SEMANAL `isSemanal=false` → forma explícita contraria a la semanal
 *
 * Todo el mapeo variante → URL es una función pura para poder probarla con
 * pruebas unitarias JVM sin tocar la red.
 */
object DetalleVariants {

    /** `?isSemanal=true` — edición semanal digital (la mayoría de las tesis recientes). */
    const val SEMANAL = "S"

    /** Sin parámetro de edición — el formato base que usa el sitio oficial. */
    const val PLANA = "P"

    /** `?isGaceta=true` — edición Gaceta (tesis de la Gaceta impresa e históricas). */
    const val GACETA = "G"

    /** `?isSemanal=false` — forma explícita para tesis no semanales. */
    const val NO_SEMANAL = "F"

    /** Orden en que se prueban las variantes: de la más común a la menos. */
    val ORDEN = listOf(SEMANAL, PLANA, GACETA, NO_SEMANAL)

    /** Etiqueta legible para el registro de red. */
    fun etiqueta(codigo: String): String = when (codigo) {
        SEMANAL -> "semanal"
        PLANA -> "sin edición"
        GACETA -> "gaceta"
        NO_SEMANAL -> "no semanal"
        else -> "estándar"
    }

    /** Parámetros de edición que corresponden a cada código de variante. */
    fun paramsDe(codigo: String): Pair<Boolean?, Boolean> = when (codigo) {
        SEMANAL -> true to false
        PLANA -> null to false
        GACETA -> null to true
        NO_SEMANAL -> false to false
        else -> null to false
    }

    /**
     * Construye la URL final de detalle a partir de la plantilla base (que
     * contiene el marcador `%IUS%`), el registro y el código de variante.
     *
     * Reglas replicadas del sitio oficial:
     *  - `hostName` siempre presente y con protocolo `https://` (algunos
     *    microservicios rechazan el host sin protocolo).
     *  - Los parámetros de edición (`isSemanal` / `isGaceta`) solo se agregan
     *    si la plantilla no los trae ya.
     */
    fun url(urlBase: String, registro: Long, codigo: String): String {
        val (semanal, gaceta) = paramsDe(codigo)
        var u = urlBase.replace("%IUS%", registro.toString())
        val ediciones = mutableListOf<Pair<String, String>>()
        if (semanal != null) ediciones += "isSemanal" to semanal.toString()
        if (gaceta) ediciones += "isGaceta" to "true"
        for ((nombre, valor) in ediciones) {
            if (u.contains("$nombre=")) continue
            u += (if (u.contains("?")) "&" else "?") + "$nombre=$valor"
        }
        if (!u.contains("hostName=")) {
            u += (if (u.contains("?")) "&" else "?") + "hostName=https://sjf2.scjn.gob.mx"
        } else {
            // Normalizar un hostName sin protocolo (la API lo rechaza).
            u = u.replace("hostName=sjf2.scjn.gob.mx", "hostName=https://sjf2.scjn.gob.mx")
        }
        return u
    }
}
