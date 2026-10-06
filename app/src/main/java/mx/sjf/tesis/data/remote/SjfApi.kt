package mx.sjf.tesis.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mx.sjf.tesis.core.Constants
import mx.sjf.tesis.core.NetLog
import mx.sjf.tesis.data.model.SearchResponse
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.data.model.Ejecutoria
import mx.sjf.tesis.data.model.Voto
import mx.sjf.tesis.data.util.decodificarEntidades
import mx.sjf.tesis.data.util.extraerFecha
import mx.sjf.tesis.data.util.fechaLegibleDeEpoch
import mx.sjf.tesis.data.util.limpiarHtml
import mx.sjf.tesis.data.util.parseFechaLegible
import mx.sjf.tesis.data.util.tituloYTipoDeVoto
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cliente de la API pública del Semanario Judicial de la Federación.
 *
 * El sitio sjf2.scjn.gob.mx está protegido por Incapsula (WAF) y requiere
 * cookies de sesión que se obtienen al visitar la página pública. Este
 * objeto las gestiona automáticamente: establece sesión al primer uso y
 * reestablece cuando aparece un 403.
 *
 * El cuerpo oficial de búsqueda replica el que envía el sitio web, con
 * clasificadores por época, tipo de documento y materia. El texto completo
 * de cada tesis se obtiene de la página de detalle (con prerendering de
 * Googlebot cuando está disponible).
 */
/**
 * El firewall del sitio (Incapsula) respondió con su página de verificación
 * en lugar de JSON. No significa que el documento no exista: es un bloqueo
 * temporal que se resuelve esperando unos segundos.
 */
class WafChallengeException(message: String) : IOException(message)

object SjfApi {
    private const val BASE = Constants.BASE_API
    private const val SESSION_URL = Constants.SESSION_URL
    private const val UA = Constants.UA_MOBILE
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * Endpoint de detalle de tesis conocido y documentado en el sitio oficial
     * (extraído del bundle Angular 680.9372a67aa2a375f8.js):
     *
     *   obtenerDetalleTesis(t,n) {
     *     return this.http.get(`${this.resourceUrl}/${t}`, {params:..., observe:"response"})
     *   }
     *
     * El sitio oficial lo usa para GET /services/sjftesismicroservice/api/public/tesis/{ius}.
     * Lo adoptamos como valor por defecto para evitar el descubrimiento JS de ~30s.
     * Si alguna vez cambia, el discovery automático (cazarDetalle) seguirá disponible
     * como plan B en byId.
     */
    private const val DETALLE_TESIS_BASE =
        "GET|https://sjf2.scjn.gob.mx/services/sjftesismicroservice/api/public/tesis/%IUS%"

    private val DEFAULT_DETALLE_ENDPOINT = DETALLE_TESIS_BASE + "|" + DetalleVariants.SEMANAL

    /**
     * Lista de microservicios del SJF. Cuando la búsqueda devuelve un ius que
     * pertenece a ejecutorias, votos o acuerdos (no tesis propiamente dicha), el
     * endpoint /tesis/{ius} devuelve 404 o un JSON vacío. Probamos entonces los
     * demás microservicios hasta encontrar el que tiene el documento.
     *
     * Orden empírico: tesis es el más común, luego ejecutorias, luego votos,
     * luego acuerdos. El histórico (/historicalfile) no se incluye aquí porque
     * usa POST con cuerpo distinto y su endpoint GET siempre devuelve 500.
     *
     * NOTA: el microservicio «tesis» NO está en esta lista porque se prueba con
     * la matriz completa de variantes de edición (ver [DetalleVariants]) dentro
     * de byId; aquí solo quedan los microservicios alternativos.
     */
    private val DETALLE_ENDPOINTS_FALLBACK = listOf(
        "GET|https://sjf2.scjn.gob.mx/services/sjfejecutoriamicroservice/api/public/ejecutorias/%IUS%" to "ejecutorias",
        "GET|https://sjf2.scjn.gob.mx/services/sjfvotosmicroservice/api/public/votos/%IUS%" to "votos",
        "GET|https://sjf2.scjn.gob.mx/services/sjfacuerdosmicroservice/api/public/acuerdos/%IUS%" to "acuerdos"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS) // guarda total por llamada: evita hangs indefinidos
        .build()

    @Volatile private var cookies: List<String> = emptyList()

    fun cookieCount() = cookies.size
    fun hasSession() = cookies.isNotEmpty()

    // Serializa el establecimiento de sesión: si búsqueda, detalle y exportación
    // por lote la necesitan a la vez, solo una petición la establece de verdad.
    private val sessionMutex = Mutex()

    /** Establece la sesión una sola vez aunque varias corutinas la pidan a la vez. */
    private suspend fun ensureSession() {
        if (hasSession()) return
        sessionMutex.withLock {
            if (!hasSession()) establishSession().getOrThrow()
        }
    }

    /**
     * Abre la sesión en segundo plano al iniciar la app. Medido contra el
     * sitio real: durante los primeros ~5–10 s de una sesión nueva el firewall
     * responde las consultas de detalle con su página de verificación; si la
     * sesión ya está «caliente» cuando el usuario abre la primera tesis, esta
     * carga a la primera.
     */
    suspend fun warmUp() {
        runCatching { ensureSession() }
    }

    // ── Cachés en memoria para reducir latencia ──

    /** Detalle por registro (LRU 60): reabrir una tesis ya consultada es instantáneo. */
    private val detailCache = object : LinkedHashMap<Long, Tesis>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Tesis>) = size > 60
    }

    /** Respuesta de búsqueda cacheada con marca de tiempo. */
    private class CachedSearch(val response: SearchResponse, val at: Long)

    /** Búsquedas recientes (LRU 16, TTL 5 min): repetir una consulta es instantáneo. */
    private val searchCache = object : LinkedHashMap<String, CachedSearch>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearch>) = size > 16
    }
    private val searchCacheTtl = 5 * 60_000L

    /** Microservicio que respondió para cada registro — evita probar los 4 en cada apertura. */
    private val endpointHits = ConcurrentHashMap<Long, String>()

    /** Invalida las cachés (pull-to-refresh, diagnóstico). */
    fun bustCaches() {
        synchronized(detailCache) { detailCache.clear() }
        synchronized(searchCache) { searchCache.clear() }
    }

    /** Resumen de cachés para el registro de depuración. */
    fun cacheStats(): String =
        "detalle=${synchronized(detailCache) { detailCache.size }} · " +
        "búsqueda=${synchronized(searchCache) { searchCache.size }} · " +
        "endpoints=${endpointHits.size}"

    private fun cacheDetalle(t: Tesis) {
        if (t.registro != 0L && (t.texto.isNotBlank() || t.rubro.isNotBlank())) {
            synchronized(detailCache) { detailCache[t.registro] = t }
        }
    }

    private fun searchCacheKey(terms: List<String>, tipo: String?, materia: String?, page: Int, size: Int) =
        "${terms.joinToString("·")}|${tipo ?: "-"}|${materia ?: "-"}|$page|$size"

    private fun apiHeaders(referer: String = SESSION_URL): Headers {
        val b = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("Accept", "application/json, text/plain, */*")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Referer", referer)
            .add("Origin", "https://sjf2.scjn.gob.mx")
            .add("Accept-Language", "es-MX,es;q=0.9")
            .add("User-Agent", UA)
        if (cookies.isNotEmpty()) b.add("Cookie", cookies.joinToString("; "))
        return b.build()
    }

    suspend fun establishSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            NetLog.log("GET /busqueda-principal-tesis · estableciendo sesión…")
            val ini = System.currentTimeMillis()
            val request = Request.Builder().url(SESSION_URL)
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get().build()
            client.newCall(request).execute().use { response ->
                val ms = System.currentTimeMillis() - ini
                val setCookies = response.headers.values("Set-Cookie")
                fun pair(raw: String): String? {
                    val name = raw.substringBefore('=').trim()
                    val value = raw.substringAfter('=', "").substringBefore(';').trim()
                    return if (name.isNotEmpty() && value.isNotEmpty()) "$name=$value" else null
                }
                val incap = setCookies.mapNotNull { raw ->
                    val name = raw.substringBefore('=').trim()
                    if (name.startsWith("visid_incap_") || name.startsWith("incap_ses_")) pair(raw) else null
                }
                cookies = if (incap.isNotEmpty()) incap else setCookies.mapNotNull(::pair)
                if (cookies.isEmpty()) {
                    NetLog.log("← HTTP ${response.code} · ${ms}ms · sin cookies Set-Cookie ✗")
                    throw IOException("La página de sesión no entregó cookies (HTTP ${response.code})")
                }
                val nombres = cookies.joinToString(", ") { it.substringBefore('=') }
                NetLog.log("← HTTP ${response.code} · ${ms}ms · ${cookies.size} cookies: ${nombres.take(180)} ✓")
            }
        }.onFailure { NetLog.log("Sesión ✗ · ${it.message}") }
    }

    private suspend fun executeWithRetry(
        customHeaders: Headers? = null,
        build: (Headers) -> Request
    ): okhttp3.Response {
        val h = customHeaders ?: apiHeaders()
        var response = client.newCall(build(h)).execute()
        if (response.code == 403) {
            response.close()
            NetLog.log("HTTP 403 · sesión rechazada · restableciendo y reintentando…")
            establishSession().getOrThrow()
            // Reconstruir headers con cookies nuevas y mismo referer.
            val referer = h["Referer"] ?: SESSION_URL
            response = client.newCall(build(apiHeaders(referer))).execute()
        }
        return response
    }

    private val EPOCAS_TODAS = Constants.EPOCAS_TODAS

    // Códigos numéricos de época que usa el sitio oficial (capturado de DevTools).
    // 210=Duodécima, 200=Undécima, 100=Décima, 5=Novena, 4=Octava, 3=Séptima,
    // 2=Sexta, 1=Quinta. Tercera y Cuarta Época no aparecen en el sitio actual.
    private val ID_EPOCAS_TODAS = listOf("210", "200", "100", "5", "4", "3", "2", "1")

    // Códigos de instancia que usa el sitio oficial.
    // 6=Pleno, 0=??, 60=Sala Superior?, 7=Primera Sala, 70=??, 80=Segunda Sala,
    // 1/2=Tribunales Colegiados, 50=Tribunal Electoral, 3/4/5=otros.
    private val NUM_INSTANCIAS_TODAS = listOf("6", "0", "60", "7", "70", "80", "1", "2", "50", "3", "4", "5")

    private fun tipoCodigo(tipoDocumento: String?): String? = when (tipoDocumento) {
        "Tesis" -> "1"
        "Jurisprudencia" -> "2"
        else -> null
    }

    fun cuerpoActivo(): String = "oficial · todas las épocas · filtros por clasificadores"

    private fun classifier(name: String, values: List<String>): JSONObject = JSONObject().apply {
        put("name", name); put("value", JSONArray(values))
        put("allSelected", false); put("visible", false); put("isMatrix", false)
    }

    private fun searchTermObj(expression: String): JSONObject = JSONObject().apply {
        put("expression", expression)
        put("fields", JSONArray(listOf("localizacionBusqueda", "rubro", "texto")))
        put("fieldsUser", "Localización: \nRubro (título y subtítulo): \nTexto: \n")
        put("fieldsText", "Localización, Rubro (título y subtítulo), Texto")
        put("operator", 0); put("operatorUser", "Y"); put("operatorText", "Y")
        put("lsFields", JSONArray()); put("esInicial", true); put("esNRD", false)
    }

    private fun cuerpoOficial(terms: List<String>, tipoDocumento: String?, materia: String?): JSONObject {
        // Réplica exacta del body que envía el sitio oficial (capturado con DevTools):
        //   - idEpoca con códigos numéricos (no nombres)
        //   - numInstancia con códigos numéricos
        //   - tipoDocumento con "1" (Tesis) o "2" (Jurisprudencia)
        //   - lbSearch: ["Todo"] (no la lista de épocas que teníamos antes)
        val classifiers = mutableListOf(
            classifier("idEpoca", ID_EPOCAS_TODAS),
            classifier("numInstancia", NUM_INSTANCIAS_TODAS),
            classifier("tipoDocumento",
                if (tipoCodigo(tipoDocumento) != null) listOf(tipoCodigo(tipoDocumento)!!)
                else listOf("1", "2"))
        )
        if (!materia.isNullOrBlank()) classifiers.add(classifier("materias", listOf(materia)))
        val termsArr = JSONArray()
        terms.forEach { termsArr.put(searchTermObj(it)) }
        return JSONObject().apply {
            put("classifiers", JSONArray(classifiers)); put("searchTerms", termsArr)
            put("bFacet", true); put("ius", JSONArray()); put("idApp", Constants.APP_ID)
            put("lbSearch", JSONArray(listOf("Todo")))
            put("filterExpression", "")
        }
    }

    suspend fun search(
        searchTerms: String,
        tipoDocumento: String?,
        materia: String?,
        page: Int,
        size: Int = 20
    ): SearchResponse = withContext(Dispatchers.IO) {
        val primera = searchList(listOf(searchTerms), tipoDocumento, materia, page, size)
        if (primera.totalElements == 0 && page == 0) {
            val palabras = searchTerms.trim().split(Regex("\\s+")).filter { it.length >= 2 }
            if (palabras.size > 1) {
                NetLog.log("Frase sin resultados · repitiendo palabra por palabra (Y)…")
                searchList(palabras, tipoDocumento, materia, page, size)
            } else primera
        } else primera
    }

    private suspend fun searchList(
        terms: List<String>,
        tipoDocumento: String?,
        materia: String?,
        page: Int,
        size: Int
    ): SearchResponse = withContext(Dispatchers.IO) {
        val cacheKey = searchCacheKey(terms, tipoDocumento, materia, page, size)
        synchronized(searchCache) { searchCache[cacheKey] }?.let { cached ->
            if (System.currentTimeMillis() - cached.at < searchCacheTtl) {
                NetLog.log("POST /tesis?page=$page · servido desde caché (${(System.currentTimeMillis() - cached.at) / 1000}s) · ${cached.response.documents.size} docs ✓")
                return@withContext cached.response
            }
        }
        ensureSession()
        val body = cuerpoOficial(terms, tipoDocumento, materia)
        val ini = System.currentTimeMillis()
        NetLog.log("POST /tesis?page=$page&size=$size · «${terms.joinToString(" ")}»" +
            (tipoDocumento?.let { " · tipo=$it" } ?: "") + (materia?.let { " · materia=$it" } ?: ""))
        val searchHeaders = apiHeaders("https://sjf2.scjn.gob.mx/listado-resultado-tesis")
        executeWithRetry(searchHeaders) { headers ->
            Request.Builder().url("$BASE/tesis?page=$page&size=$size").headers(headers)
                .post(body.toString().toRequestBody(JSON_MEDIA)).build()
        }.use { r ->
            val ms = System.currentTimeMillis() - ini
            if (r.isSuccessful) {
                val parsed = parseSearchResponse(r.body?.string() ?: "")
                synchronized(searchCache) { searchCache[cacheKey] = CachedSearch(parsed, System.currentTimeMillis()) }
                NetLog.log("← HTTP 200 · ${ms}ms · ${parsed.documents.size} docs · total ${parsed.totalElements} · totalPage ${parsed.totalPages} ✓")
                parsed
            } else {
                val detalle = runCatching { r.body?.string() }.getOrNull()?.trim()?.take(300) ?: ""
                NetLog.log("← HTTP ${r.code} · ${ms}ms · $detalle")
                throw IOException("HTTP ${r.code} en POST /tesis · $detalle")
            }
        }
    }

    private fun parseSearchResponse(raw: String): SearchResponse {
        val json = try { JSONObject(raw) } catch (e: Exception) {
            throw IOException("Respuesta no válida del servidor (posible bloqueo WAF)")
        }
        val arr = json.optJSONArray("documents")
        if (arr == null && !json.has("total") && !json.has("totalElements")) {
            throw IOException("Respuesta sin «documents» ni «total»: ${raw.take(150)}")
        }
        val docs = if (arr != null)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseTesis) }
        else emptyList()
        return SearchResponse(
            documents = docs,
            totalElements = json.optInt("total", json.optInt("totalElements", docs.size)),
            totalPages = json.optInt("totalPage", json.optInt("totalPages", 1)),
            page = json.optInt("page", 0)
        )
    }

    suspend fun browse(page: Int, size: Int = 20): SearchResponse = withContext(Dispatchers.IO) {
        val cacheKey = searchCacheKey(emptyList(), "@browse", null, page, size)
        synchronized(searchCache) { searchCache[cacheKey] }?.let { cached ->
            if (System.currentTimeMillis() - cached.at < searchCacheTtl) {
                NetLog.log("POST /tesis?page=$page · recorrido servido desde caché ✓")
                return@withContext cached.response
            }
        }
        ensureSession()
        NetLog.log("POST /tesis?page=$page&size=$size · recorrido ({})")
        val ini = System.currentTimeMillis()
        val browseHeaders = apiHeaders("https://sjf2.scjn.gob.mx/listado-resultado-tesis")
        executeWithRetry(browseHeaders) { headers ->
            Request.Builder().url("$BASE/tesis?page=$page&size=$size").headers(headers)
                .post("{}".toRequestBody(JSON_MEDIA)).build()
        }.use { r ->
            val ms = System.currentTimeMillis() - ini
            if (!r.isSuccessful) {
                val detalle = runCatching { r.body?.string() }.getOrNull()?.trim()?.take(200) ?: ""
                NetLog.log("← HTTP ${r.code} · ${ms}ms · $detalle")
                throw IOException("HTTP ${r.code} en POST /tesis (recorrido) · $detalle")
            }
            val parsed = parseSearchResponse(r.body?.string() ?: "")
            synchronized(searchCache) { searchCache[cacheKey] = CachedSearch(parsed, System.currentTimeMillis()) }
            NetLog.log("← HTTP 200 · ${ms}ms · ${parsed.documents.size} docs · total ${parsed.totalElements} ✓ recorrido")
            val paginas = if (parsed.totalPages > 1) parsed.totalPages else (parsed.totalElements + size - 1) / size
            parsed.copy(totalPages = maxOf(paginas, 1))
        }
    }

    suspend fun browseFiltrado(
        tipoDocumento: String?,
        materia: String?,
        page: Int,
        size: Int = 20
    ): SearchResponse = withContext(Dispatchers.IO) {
        val cacheKey = searchCacheKey(emptyList(), tipoDocumento, materia, page, size)
        synchronized(searchCache) { searchCache[cacheKey] }?.let { cached ->
            if (System.currentTimeMillis() - cached.at < searchCacheTtl) {
                NetLog.log("POST /tesis?page=$page · recorrido filtrado servido desde caché ✓")
                return@withContext cached.response
            }
        }
        ensureSession()
        val body = cuerpoOficial(emptyList(), tipoDocumento, materia)
        NetLog.log("POST /tesis?page=$page&size=$size · recorrido filtrado" +
            (tipoDocumento?.let { " · tipo=$it" } ?: "") + (materia?.let { " · materia=$it" } ?: ""))
        val ini = System.currentTimeMillis()
        val browseHeaders = apiHeaders("https://sjf2.scjn.gob.mx/listado-resultado-tesis")
        executeWithRetry(browseHeaders) { headers ->
            Request.Builder().url("$BASE/tesis?page=$page&size=$size").headers(headers)
                .post(body.toString().toRequestBody(JSON_MEDIA)).build()
        }.use { r ->
            val ms = System.currentTimeMillis() - ini
            if (!r.isSuccessful) {
                val detalle = runCatching { r.body?.string() }.getOrNull()?.trim()?.take(200) ?: ""
                NetLog.log("← HTTP ${r.code} · ${ms}ms · $detalle")
                throw IOException("HTTP ${r.code} en POST /tesis (recorrido filtrado) · $detalle")
            }
            val parsed = parseSearchResponse(r.body?.string() ?: "")
            synchronized(searchCache) { searchCache[cacheKey] = CachedSearch(parsed, System.currentTimeMillis()) }
            NetLog.log("← HTTP 200 · ${ms}ms · ${parsed.documents.size} docs · total ${parsed.totalElements} ✓")
            val paginas = if (parsed.totalPages > 1) parsed.totalPages else (parsed.totalElements + size - 1) / size
            parsed.copy(totalPages = maxOf(paginas, 1))
        }
    }

    // ── Texto completo: página de detalle ──
    // Arrancamos con el endpoint conocido por defecto. Si falla reiteradamente,
    // byId invocará discoverApi() como plan B y actualizará este valor.
    @Volatile private var detalleEndpoint: String? = DEFAULT_DETALLE_ENDPOINT
    @Volatile private var prerenderOk: Boolean? = null

    // Flag que indica que discovery ya corrió una vez en esta sesión.
    // Evita volver a correr los ~30 segundos de escaneo de bundles JS en cada
    // byId que falle — el endpoint por defecto siempre es el mismo.
    @Volatile private var discoveryRan: Boolean = false

    // Mutex que serializa el discovery: si varios byId fallan a la vez, solo uno
    // dispara el discovery y los demás esperan al resultado.
    private val discoveryMutex = Mutex()

    fun estadoDetalle(): String = when {
        detalleEndpoint == DEFAULT_DETALLE_ENDPOINT -> "endpoint oficial (/tesis/{ius}) ✓"
        detalleEndpoint != null -> "endpoint descubierto (${detalleEndpoint!!.substringAfter('|')})"
        prerenderOk == true -> "prerender Googlebot ✓"
        prerenderOk == false -> "sin prerender · sin endpoint"
        else -> "sin probar"
    }

    /**
     * Permite forzar una nueva corrida de discovery desde la UI de ajustes.
     * El botón "🔍 Verificar API de la SCJN" lo invoca para que el usuario pueda
     * re-escanear los bundles JS si el sitio cambió.
     */
    fun resetDiscoveryFlag() {
        discoveryRan = false
    }

    private fun fusionarCookies(resp: okhttp3.Response) {
        val nuevas = resp.headers.values("Set-Cookie").mapNotNull { raw ->
            val nombre = raw.substringBefore('=').trim()
            val valor = raw.substringAfter('=', "").substringBefore(';').trim()
            if (nombre.isNotEmpty() && valor.isNotEmpty()) "$nombre=$valor" else null
        }
        if (nuevas.isEmpty()) return
        val porNombre = LinkedHashMap<String, String>()
        cookies.forEach { porNombre[it.substringBefore('=')] = it }
        nuevas.forEach { porNombre[it.substringBefore('=')] = it }
        val previos = cookies.map { it.substringBefore('=') }.toSet()
        cookies = porNombre.values.toList()
        val agregadas = nuevas.filter { it.substringBefore('=') !in previos }
        if (agregadas.isNotEmpty()) {
            NetLog.log("Cookies fusionadas · total ${cookies.size} · nuevas: ${agregadas.joinToString(", ") { it.substringBefore('=') }}")
        }
    }

    private suspend fun descargarPagina(url: String, bot: Boolean = false): String? = withContext(Dispatchers.IO) {
        for (intento in 0..1) {
            val resp = runCatching {
                val ua = if (bot) Constants.UA_BOT else UA
                val b = Request.Builder().url(url)
                    .header("User-Agent", ua)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "es-MX,es;q=0.9")
                    .header("Referer", "https://sjf2.scjn.gob.mx/listado-resultado-tesis")
                if (cookies.isNotEmpty()) b.header("Cookie", cookies.joinToString("; "))
                client.newCall(b.get().build()).execute()
            }.getOrNull() ?: continue
            resp.use { r ->
                fusionarCookies(r)
                if (r.isSuccessful) return@withContext r.body?.string()
                NetLog.log("   HTTP ${r.code} en ${url.substringAfterLast('/').take(40)} (intento ${intento + 1})")
                if (r.code == 403) establishSession()
            }
        }
        null
    }

    suspend fun byId(tesis: Tesis): Tesis = withContext(Dispatchers.IO) {
        // 0) Caché de detalle: reabrir una tesis ya consultada es instantáneo.
        synchronized(detailCache) { detailCache[tesis.registro] }?.let { cached ->
            NetLog.log("Detalle · registro ${tesis.registro} servido desde caché ✓")
            return@withContext cached
        }
        ensureSession()

        // 1) Matriz de variantes de edición del microservicio de tesis.
        //    El sitio oficial cambia los parámetros del detalle según la
        //    edición en que se publicó cada tesis: isSemanal=true sirve para
        //    las tesis del Semanario digital semanal, pero las tesis de la
        //    Gaceta impresa (p. ej. 190955 · P. CLXV/2000) y varias de la
        //    Décima Época NO responden a esa única variante y el texto
        //    completo quedaba sin cargar. Se prueban en orden: semanal →
        //    sin edición → gaceta → no semanal, deteniéndonos al primer
        //    texto completo. Si ya sabemos qué variante sirvió para este
        //    registro (consulta previa), se prueba primero y se ahorran
        //    las peticiones fallidas.
        val conocido = endpointHits[tesis.registro]
        var mejorTesis: Tesis? = null
        var mejorPlantilla: String? = null

        suspend fun intentar(plantilla: String, etiqueta: String): Boolean {
            val intento = runCatching { pedirDetalleConEspera(plantilla, tesis) }
            // Bloqueo persistente del firewall: no tiene caso seguir probando
            // otras rutas (también estarían bloqueadas). Se informa a la UI.
            (intento.exceptionOrNull() as? WafChallengeException)?.let { throw it }
            val t = intento.getOrNull()
            return when {
                t != null && t.texto.isNotBlank() -> {
                    mejorTesis = t; mejorPlantilla = plantilla
                    NetLog.log("Detalle · $etiqueta → ✓ texto completo")
                    true
                }
                t != null && t.rubro.isNotBlank() -> {
                    // Respuesta parcial: se conserva como último recurso, pero
                    // se sigue probando el resto por si otra variante devuelve
                    // el texto completo.
                    if (mejorTesis == null) { mejorTesis = t; mejorPlantilla = plantilla }
                    NetLog.log("Detalle · $etiqueta → ◇ solo rubro (sin texto)")
                    false
                }
                else -> {
                    NetLog.log("Detalle · $etiqueta → ✗ " +
                        (intento.exceptionOrNull()?.message?.take(90) ?: "sin contenido"))
                    false
                }
            }
        }

        fun etiquetaDe(plantilla: String): String {
            val nombre = when {
                plantilla.contains("/tesis/") -> "/tesis"
                plantilla.contains("ejecutorias") -> "ejecutorias"
                plantilla.contains("/votos/") -> "votos"
                plantilla.contains("/acuerdos/") -> "acuerdos"
                else -> "endpoint"
            }
            val partes = plantilla.split('|')
            val variante = partes.getOrNull(2)
            return if (variante != null && variante in DetalleVariants.ORDEN)
                "$nombre · ${DetalleVariants.etiqueta(variante)}"
            else nombre
        }

        // Las tesis de la Gaceta impresa («…y su Gaceta») solo responden sin
        // parámetro de edición; las del Semanario semanal, con isSemanal=true.
        // Pedir primero la variante correcta ahorra una petición fallida.
        val orden = if (tesis.fuente.contains("Gaceta", ignoreCase = true))
            listOf(DetalleVariants.PLANA) + (DetalleVariants.ORDEN - DetalleVariants.PLANA)
        else DetalleVariants.ORDEN
        val variantesTesis = orden.map { "$DETALLE_TESIS_BASE|$it" }
        val cola = (listOfNotNull(conocido) + variantesTesis + DETALLE_ENDPOINTS_FALLBACK.map { it.first })
            .distinct()

        for (plantilla in cola) {
            if (intentar(plantilla, etiquetaDe(plantilla))) break
        }

        // 2) Si ninguna variante del endpoint dio texto completo: la búsqueda
        //    oficial admite un filtro por «ius» (el campo "ius" del cuerpo de
        //    búsqueda, que siempre enviamos vacío). Es la vía que usa el sitio
        //    para la búsqueda por registro digital.
        if (mejorTesis?.texto.isNullOrBlank()) {
            val porBusqueda = runCatching { buscarPorRegistro(tesis.registro) }.getOrNull()
            if (porBusqueda != null && porBusqueda.texto.isNotBlank()) {
                val completa = porBusqueda.conMetadatosDe(tesis)
                cacheDetalle(completa)
                NetLog.log("Detalle · registro ${tesis.registro} encontrado vía búsqueda por registro ✓")
                return@withContext completa
            }
        }

        // 3) Si alguna variante respondió (aunque sea solo rubro), se adopta:
        //    se recuerda la plantilla ganadora para este registro y se sirve.
        //    Antes de servir se rellenan los metadatos que falten con los del
        //    resultado de búsqueda (el detalle no siempre trae fecha/época).
        val m = mejorTesis
        if (m != null && (m.texto.isNotBlank() || m.rubro.isNotBlank())) {
            val completa = m.conMetadatosDe(tesis)
            endpointHits[tesis.registro] = mejorPlantilla!!
            cacheDetalle(completa)
            when {
                mejorPlantilla == DEFAULT_DETALLE_ENDPOINT ->
                    NetLog.log("Detalle vía endpoint · registro ${tesis.registro} ✓")
                completa.texto.isNotBlank() ->
                    NetLog.log("Detalle · registro ${tesis.registro} ✓ vía ${etiquetaDe(mejorPlantilla!!)}")
                else ->
                    NetLog.log("Detalle · registro ${tesis.registro} ◇ parcial (solo rubro) vía ${etiquetaDe(mejorPlantilla!!)}")
            }
            return@withContext completa
        }

        // 4) Discovery una sola vez por sesión, por si el sitio cambió de rutas.
        //    La bandera se marca ANTES de correr: discoverApi() llama byId() en su
        //    sección E y no debe re-entrar al mutex (que no es reentrante).
        if (!discoveryRan) {
            discoveryMutex.withLock {
                if (!discoveryRan) {
                    NetLog.log("byId · discovery (30s) como último recurso…")
                    discoveryRan = true
                    runCatching { discoverApi() }
                }
            }
            val reintento = runCatching { pedirDetalle(DEFAULT_DETALLE_ENDPOINT, tesis) }.getOrNull()
            if (reintento != null && reintento.texto.isNotBlank()) {
                val completa = reintento.conMetadatosDe(tesis)
                NetLog.log("Detalle vía reintento post-discovery · registro ${tesis.registro} ✓")
                return@withContext completa
            }
        }

        // 5) No fallar — devolver la tesis con los metadatos que ya tenemos del
        //    resultado de búsqueda. El usuario verá rubro, época, instancia, tipo,
        //    materia, fecha y un mensaje explicando que el texto completo aún no
        //    está disponible por esta vía (típico de registros cuyo detalle no
        //    responde en ninguna edición). La hoja de detalle muestra el enlace
        //    "Ver Original" para consultar el sitio oficial.
        NetLog.log("byId · ius ${tesis.registro} · sin texto completo tras ${cola.size + 1} vías, devolviendo metadatos de búsqueda")
        tesis
    }

    // ── Votos y ejecutorias (documentos relacionados con una tesis) ──

    private const val VOTOS_BASE =
        "https://sjf2.scjn.gob.mx/services/sjfvotosmicroservice/api/public/votos/%IUS%"
    private const val EJECUTORIAS_BASE =
        "https://sjf2.scjn.gob.mx/services/sjfejecutoriamicroservice/api/public/ejecutorias/%IUS%"

    private val votoCache = ConcurrentHashMap<Long, Voto>()

    /** Ejecutorias recientes (LRU 12): sus textos pueden pasar de 150 000 caracteres. */
    private val ejecutoriaCache = object : LinkedHashMap<Long, Ejecutoria>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Ejecutoria>) = size > 12
    }

    private fun registrosDe(o: JSONObject, clave: String): List<Long> =
        o.optJSONArray(clave)?.let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it > 0 } }
            ?: emptyList()

    private fun JSONObject.texto(clave: String): String = if (isNull(clave)) "" else optString(clave)

    private fun esDeGaceta(tesis: Tesis) = tesis.fuente.contains("Gaceta", ignoreCase = true)

    /**
     * Votos publicados con una tesis, en el orden en que los lista la SCJN.
     * Se piden uno tras otro (no en ráfaga) para no provocar al firewall.
     */
    suspend fun votos(tesis: Tesis): List<Voto> = tesis.votos.map { id ->
        votoCache[id] ?: run {
            val json = documento(VOTOS_BASE, id, esDeGaceta(tesis), tesis.urlDetalle, "Voto")
            val texto = json.texto("texto")
            val (titulo, tipo) = tituloYTipoDeVoto(texto)
            Voto(
                registro = id,
                titulo = titulo,
                tipo = tipo,
                texto = texto,
                publicacion = limpiarHtml(json.texto("textoPublicacion")).trim()
            ).also { votoCache[id] = it }
        }
    }

    /**
     * Ejecutorias de las que deriva una tesis («Precedente(s) de la tesis» en
     * el Semanario), en el orden de la SCJN, con su texto completo.
     */
    suspend fun ejecutorias(tesis: Tesis): List<Ejecutoria> = tesis.ejecutorias.map { id ->
        synchronized(ejecutoriaCache) { ejecutoriaCache[id] } ?: run {
            val json = documento(EJECUTORIAS_BASE, id, esDeGaceta(tesis), tesis.urlDetalle, "Ejecutoria")
            Ejecutoria(
                registro = id,
                asunto = json.texto("tipoAsunto").trim().trimEnd('.').let(::tipoAsuntoLegible),
                rubro = limpiarHtml(json.texto("rubro")).trim(),
                epoca = json.texto("epoca").trim(),
                instancia = json.texto("instancia").trim(),
                fuente = json.texto("fuente").trim(),
                volumen = json.texto("volumen").trim(),
                tomo = json.texto("subVolumen").trim().ifBlank { json.texto("tomo").trim() },
                pagina = json.texto("pagina").trim().takeIf { it.isNotBlank() && it != "0" } ?: "",
                publicacion = limpiarHtml(json.texto("textoPublicacion")).trim(),
                texto = json.texto("texto")
            ).also { synchronized(ejecutoriaCache) { ejecutoriaCache[id] = it } }
        }
    }

    /**
     * «AMPARO DIRECTO EN REVISIÓN 6627/2025» → «Amparo directo en revisión
     * 6627/2025». La API entrega el tipo de asunto a veces en mayúsculas.
     */
    internal fun tipoAsuntoLegible(s: String): String {
        if (s.isBlank() || s.any { it.isLowerCase() }) return s
        return s.lowercase().replaceFirstChar { it.uppercase() }
            .replace(Regex("-([a-z]{1,4})\\b")) { "-" + it.groupValues[1].uppercase() }
    }

    /**
     * Un documento (voto o ejecutoria) por su registro. Igual que las tesis,
     * cada servicio solo responde con el parámetro de edición correcto
     * (medido: el voto 47724 da 404 sin «isSemanal=true» y la ejecutoria
     * 19501, de la Gaceta, da 500 con él); se prueba primero la edición más
     * probable según la fuente de la tesis.
     */
    private suspend fun documento(base: String, id: Long, gaceta: Boolean, referer: String, tipo: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureSession()
            val ediciones = if (gaceta) listOf(DetalleVariants.PLANA, DetalleVariants.SEMANAL)
                            else listOf(DetalleVariants.SEMANAL, DetalleVariants.PLANA)
            for (edicion in ediciones) {
                val json = obtenerJsonConEspera(DetalleVariants.url(base, id, edicion), referer) ?: continue
                if (json.texto("texto").isBlank()) continue
                NetLog.log("$tipo $id ✓ (${DetalleVariants.etiqueta(edicion)})")
                return@withContext json
            }
            throw IOException("$tipo $id no disponible")
        }

    /**
     * GET que devuelve el JSON, o null si el documento no está en esa edición
     * (la API responde 404 o 500 según el servicio). Ante la página de
     * verificación del firewall espera y repite la misma petición, como
     * [pedirDetalleConEspera].
     */
    private suspend fun obtenerJsonConEspera(url: String, referer: String): JSONObject? {
        var intento = 0
        while (true) {
            val resultado = executeWithRetry(apiHeaders(referer)) { h -> Request.Builder().url(url).headers(h).get().build() }
                .use { resp ->
                    fusionarCookies(resp)
                    val raw = resp.body?.string() ?: ""
                    when {
                        !resp.isSuccessful -> null
                        esDesafioWaf(raw) -> WafChallengeException("verificación del firewall (Incapsula)")
                        else -> runCatching { JSONObject(raw) }.getOrElse { throw IOException("sin JSON (len=${raw.length})") }
                    }
                }
            if (resultado !is WafChallengeException) return resultado as JSONObject?
            val espera = ESPERAS_WAF_MS.getOrNull(intento++) ?: throw resultado
            NetLog.log("Documento · el firewall pidió verificación · reintento en ${espera / 1000.0} s")
            delay(espera)
        }
    }

    private fun esDesafioWaf(raw: String): Boolean {
        val inicio = raw.trimStart()
        return inicio.startsWith("<") && (raw.contains("_Incapsula_Resource") || raw.contains("Incapsula", ignoreCase = true))
    }

    /** Esperas entre reintentos cuando el firewall pide verificación (~10 s en total). */
    private val ESPERAS_WAF_MS = listOf(1500L, 3000L, 6000L)

    /**
     * [pedirDetalle] tolerante al firewall: si llega la página de verificación,
     * espera y repite la MISMA petición. Probar otras variantes en ráfaga solo
     * alarga el bloqueo (medido: las ráfagas siguen bloqueadas; tras esperar,
     * la misma petición responde bien).
     */
    private suspend fun pedirDetalleConEspera(plantilla: String, tesis: Tesis): Tesis {
        var intento = 0
        while (true) {
            try {
                return pedirDetalle(plantilla, tesis)
            } catch (e: WafChallengeException) {
                val espera = ESPERAS_WAF_MS.getOrNull(intento++) ?: throw e
                NetLog.log("Detalle · el firewall pidió verificación · reintento en ${espera / 1000.0} s")
                delay(espera)
            }
        }
    }

    private suspend fun pedirDetalle(plantilla: String, tesis: Tesis): Tesis = withContext(Dispatchers.IO) {
        // La plantilla tiene hasta 3 segmentos:  METODO|URL(%IUS%)|VARIANTE
        // La variante (S/P/G/F, ver [DetalleVariants]) decide los parámetros de
        // edición que se agregan a la URL; si no viene, no se agrega ninguno.
        val partes = plantilla.split('|')
        val metodo = partes[0]
        val variante = partes.getOrNull(2) ?: DetalleVariants.PLANA
        val url = if (metodo == "GET")
            DetalleVariants.url(partes[1], tesis.registro, variante)
        else partes[1].replace("%IUS%", tesis.registro.toString())

        // Referer específico de detalle (el sitio oficial usa /detalle/tesis/{ius}).
        val referer = "https://sjf2.scjn.gob.mx/detalle/tesis/${tesis.registro}"
        val headers = apiHeaders(referer)

        val r = if (metodo == "GET")
            executeWithRetry(headers) { h -> Request.Builder().url(url).headers(h).get().build() }
        else
            executeWithRetry(headers) { h ->
                Request.Builder().url(url).headers(h)
                    .post(JSONObject().put("ius", JSONArray(listOf(tesis.registro.toString()))).toString().toRequestBody(JSON_MEDIA)).build()
            }
        r.use { resp ->
            fusionarCookies(resp)
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string() }.getOrNull()?.take(120) ?: ""
                throw IOException("HTTP ${resp.code}${if (body.isNotBlank()) " · $body" else ""}")
            }
            val raw = resp.body?.string() ?: ""
            if (esDesafioWaf(raw)) throw WafChallengeException("verificación del firewall (Incapsula)")
            val json = runCatching { JSONObject(raw) }.getOrElse {
                throw IOException("sin JSON (len=${raw.length}): ${raw.take(90)}")
            }
            val doc = json.optJSONObject("documento")
                ?: json.optJSONArray("documents")?.optJSONObject(0)
                ?: json.optJSONArray("tesis")?.optJSONObject(0)
                ?: json
            val t = parseTesis(doc)
            if (t.texto.isBlank() && t.rubro.isBlank()) {
                // Incluir las claves del JSON: si la respuesta usa otro esquema
                // (p. ej. campos renombrados), el registro de red lo delata de
                // inmediato sin otra ronda de depuración a ciegas.
                val claves = doc.keys().asSequence().take(12).joinToString(",")
                throw IOException("200 sin contenido (ius=${tesis.registro} · claves: $claves)")
            }
            t
        }
    }

    /**
     * Plan C para el texto completo: la búsqueda oficial admite un filtro por
     * «ius» — es el campo "ius" del cuerpo de búsqueda que siempre enviamos
     * vacío y que el sitio llena en su «Búsqueda por registro digital».
     *
     * Se usa como último recurso cuando ninguna variante del endpoint de
     * detalle respondió con texto. Solo se acepta la respuesta si el documento
     * devuelto corresponde al registro pedido (la API puede ignorar el filtro)
     * y trae texto completo.
     */
    private suspend fun buscarPorRegistro(registro: Long): Tesis? = withContext(Dispatchers.IO) {
        ensureSession()
        NetLog.log("POST /tesis · búsqueda por registro $registro…")
        val cuerpo = JSONObject(cuerpoOficial(emptyList(), null, null).toString())
            .put("ius", JSONArray(listOf(registro.toString())))
        val r = executeWithRetry(apiHeaders("https://sjf2.scjn.gob.mx/listado-resultado-tesis")) { h ->
            Request.Builder().url("$BASE/tesis?page=0&size=5").headers(h)
                .post(cuerpo.toString().toRequestBody(JSON_MEDIA)).build()
        }
        r.use { resp ->
            if (!resp.isSuccessful) {
                NetLog.log("   búsqueda por registro → HTTP ${resp.code}")
                return@withContext null
            }
            val docs = runCatching { JSONObject(resp.body?.string() ?: "").optJSONArray("documents") }.getOrNull()
            val d0 = docs?.optJSONObject(0) ?: run {
                NetLog.log("   búsqueda por registro → sin documentos")
                return@withContext null
            }
            val t = parseTesis(d0)
            when {
                t.registro != registro ->
                    NetLog.log("   búsqueda por registro → devolvió ius ${t.registro} ≠ $registro (filtro ignorado)")
                t.texto.isBlank() ->
                    NetLog.log("   búsqueda por registro → documento hallado pero sin texto completo")
                else -> return@withContext t
            }
            null
        }
    }

    /**
     * Diagnóstico específico para tesis de la Gaceta impresa, las que dejaron
     * de cargar en v2.2.0 (ius 190955 · P. CLXV/2000 · Novena Época). Prueba
     * la matriz completa de variantes y la búsqueda por registro contra un
     * registro conocido y deja el resultado en el registro de red, para poder
     * confirmar de inmediato qué vía responde desde el dispositivo real.
     */
    private suspend fun probarTesisHistorica() {
        val registro = 190955L
        val muestra = Tesis(registro = registro, rubro = "EXTRADICIÓN")
        NetLog.log("   ius $registro (P. CLXV/2000 · Gaceta Novena Época · Pleno):")
        for (v in DetalleVariants.ORDEN) {
            val plantilla = "$DETALLE_TESIS_BASE|$v"
            val intento = runCatching { pedirDetalle(plantilla, muestra) }
            val t = intento.getOrNull()
            NetLog.log("   variante ${DetalleVariants.etiqueta(v)} → " + when {
                t != null && t.texto.isNotBlank() -> "✓ ${t.texto.length} caracteres de texto"
                t != null -> "◇ solo rubro (${t.rubro.length} caracteres, sin texto)"
                else -> "✗ ${intento.exceptionOrNull()?.message?.take(70)}"
            })
        }
        val porBusqueda = runCatching { buscarPorRegistro(registro) }.getOrNull()
        NetLog.log("   búsqueda por registro → " +
            (porBusqueda?.let { "✓ ${it.texto.length} caracteres de texto" } ?: "✗ sin texto completo"))
    }

    private fun extraerTextoDePagina(html: String, rubro: String): Pair<String, String> {
        data class Bloque(val inicio: Int, val fin: Int, val interior: String)
        val re = Regex("""(?is)<p[^>]*>(.*?)</p>""")
        val bloques = re.findAll(html).mapNotNull { m ->
            val interior = m.groupValues[1].trim()
            if (interior.isBlank()) null else Bloque(m.range.first, m.range.last + 1, interior)
        }.toList()
        if (bloques.isEmpty()) return "" to ""
        fun plano(b: Bloque) = limpiarHtml(b.interior).trim()
        val prefijo = decodificarEntidades(rubro).trim().take(45)
        val idxRubro = if (prefijo.length < 15) -1 else bloques.indexOfFirst { b ->
            val p = plano(b); p.length > 20 && (p.startsWith(prefijo) || p.contains(prefijo))
        }
        val desde: Int; val saltarPrimero: Boolean
        if (idxRubro >= 0) { desde = idxRubro; saltarPrimero = true }
        else {
            var mejorInicio = 0; var mejorLong = 0; var i = 0
            while (i < bloques.size) {
                var j = i
                while (j + 1 < bloques.size && bloques[j + 1].inicio - bloques[j].fin <= 400) j++
                val longitud = (i..j).sumOf { plano(bloques[it]).length }
                if (longitud > mejorLong) { mejorLong = longitud; mejorInicio = i }
                i = j + 1
            }
            desde = mejorInicio; saltarPrimero = false
        }
        val partes = mutableListOf<String>(); var precedente = ""
        var i = desde; var primero = true
        while (i < bloques.size && partes.size < 60) {
            val b = bloques[i]
            if (i > desde && b.inicio - bloques[i - 1].fin > 400) break
            val p = plano(b)
            when {
                primero && saltarPrimero -> {}
                p.startsWith("Precedentes:", ignoreCase = true) -> precedente = p.substringAfter(":").trim()
                p.startsWith("Registro:", ignoreCase = true) || p.contains("Esta tesis se publicó", ignoreCase = true) -> if (i > desde) break
                else -> partes += b.interior.trim()
            }
            primero = false; i++
        }
        val planoTotal = partes.joinToString(" ") { limpiarHtml(it) }.trim()
        if (planoTotal.contains("outdated browser", ignoreCase = true) ||
            planoTotal.contains("upgrade your browser", ignoreCase = true) || planoTotal.length < 120) return "" to ""
        return partes.joinToString("\n\n") to precedente
    }

    suspend fun health(): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        ensureSession()
        NetLog.log("GET /health"); val ini = System.currentTimeMillis()
        executeWithRetry { headers -> Request.Builder().url("$BASE/health").headers(headers).get().build() }.use { r ->
            val ms = System.currentTimeMillis() - ini
            NetLog.log("← HTTP ${r.code} · ${ms}ms ${if (r.isSuccessful) "✓" else "✗"}")
            r.code to r.isSuccessful
        }
    }

    /**
     * Verificación rápida del endpoint de detalle por defecto.
     * A diferencia de discoverApi() que escanea bundles JS y prueba 8 endpoints,
     * este solo hace 1 GET al endpoint conocido y confirma que devuelve el ius
     * solicitado. ~1s en lugar de ~30s.
     */
    suspend fun quickHealthCheck(ius: Long = 2031950L): Boolean = withContext(Dispatchers.IO) {
        ensureSession()
        val ep = detalleEndpoint ?: DEFAULT_DETALLE_ENDPOINT
        NetLog.log("── Verificación rápida del endpoint de detalle ──")
        val ok = runCatching {
            val muestra = Tesis(registro = ius, rubro = "")
            val r = pedirDetalle(ep, muestra)
            val ok = r.registro == ius && (r.texto.isNotBlank() || r.rubro.isNotBlank())
            NetLog.log("   GET ${ep.substringAfter("|").substringAfter("gob.mx")} → HTTP 200 · ius=${r.registro} · texto=${r.texto.length} chars ${if (ok) "✓" else "✗"}")
            ok
        }.getOrDefault(false)
        NetLog.log("── Verificación rápida finalizada: ${if (ok) "OK" else "fallo"} ──")
        ok
    }

    suspend fun discoverApi() = withContext(Dispatchers.IO) {
        try {
            ensureSession()
            NetLog.log("── Verificación de API v6 iniciada ──")
            NetLog.log("A) Búsqueda «extradición» (todas las épocas):")
            val muestra = runCatching {
                val r = searchList(listOf("extradición"), null, null, 0, 1)
                NetLog.log("   ✓ total ${r.totalElements} · totalPage ${r.totalPages}")
                r.documents.firstOrNull()
            }.getOrNull() ?: Tesis(registro = 2032514L, rubro = "COMPETENCIA")
            val urlDetalle = "https://sjf2.scjn.gob.mx/detalle/tesis/${muestra.registro}"
            NetLog.log("   Muestra: ius=${muestra.registro}")
            NetLog.log("B) ¿Prerender para buscadores? (UA Googlebot):")
            runCatching {
                val html = descargarPagina(urlDetalle, bot = true)
                val prefijo = muestra.rubro.take(40)
                when {
                    html == null -> NetLog.log("   ✗ sin respuesta")
                    prefijo.isNotBlank() && html.contains(prefijo) -> {
                        prerenderOk = true
                        NetLog.log("   ✓ El HTML contiene el rubro · ¡prerender disponible!")
                    }
                    else -> { prerenderOk = false; NetLog.log("   ✗ Cascarón JavaScript · el HTML no contiene el rubro") }
                }
            }.onFailure { NetLog.log("   ✗ ${it.message}") }
            NetLog.log("C) Lote por «ius» (¿devuelve el documento pedido?):")
            runCatching {
                val cuerpo = JSONObject().put("classifiers", JSONArray()).put("searchTerms", JSONArray())
                    .put("bFacet", false).put("ius", JSONArray(listOf(muestra.registro.toString())))
                    .put("idApp", Constants.APP_ID).put("lbSearch", JSONArray()).put("filterExpression", "")
                executeWithRetry { h ->
                    Request.Builder().url("$BASE/tesis?page=0&size=5").headers(h)
                        .post(cuerpo.toString().toRequestBody(JSON_MEDIA)).build()
                }.use { r ->
                    val raw = r.body?.string() ?: ""
                    val json = runCatching { JSONObject(raw) }.getOrNull()
                    val docs = json?.optJSONArray("documents"); val d0 = docs?.optJSONObject(0)
                    NetLog.log("   HTTP ${r.code} · total=${json?.optInt("total", -1)} · docs=${docs?.length() ?: 0} · ius[0]=${d0?.opt("ius")}")
                    if (d0 != null) {
                        val tieneTexto = !d0.isNull("texto") && d0.optString("texto").length > 40
                        NetLog.log("   texto[0]: ${if (tieneTexto) "✓ ${d0.optString("texto").take(80)}" else "(nulo o corto)"}")
                        val iusResp = d0.opt("ius")?.toString()
                        if (iusResp == muestra.registro.toString() && tieneTexto) {
                            detalleEndpoint = "POST|$BASE/tesis?page=0&size=5"
                            NetLog.log("   ★ Lote por ius ADOPTADO como endpoint de detalle")
                        }
                    }
                }
            }.onFailure { NetLog.log("   ✗ ${it.message}") }
            NetLog.log("D) Caza del endpoint en el JavaScript oficial:")
            runCatching { cazarDetalle(urlDetalle, muestra) }.onFailure { NetLog.log("   ✗ ${it.message}") }
            NetLog.log("E) Extracción de texto de punta a punta (byId):")
            runCatching {
                val completa = byId(muestra)
                val plano = mx.sjf.tesis.data.util.textoPlano(completa.texto)
                NetLog.log("   ✓ ${plano.length} caracteres · precedente: ${completa.precedente.ifBlank { "(vacío)" }}")
                NetLog.log("   inicio: ${plano.take(260)}")
            }.onFailure { NetLog.log("   ✗ ${it.message}") }
            NetLog.log("F) Tesis histórica de la Gaceta (las que fallaban en 2.2.0):")
            runCatching { probarTesisHistorica() }.onFailure { NetLog.log("   ✗ ${it.message}") }
        } catch (e: Exception) { NetLog.log("Verificación ✗ ${e.message}") }
        NetLog.log("── Verificación finalizada ──")
    }

    private suspend fun cazarDetalle(urlDetalle: String, muestra: Tesis) {
        runCatching { descargarPagina("https://sjf2.scjn.gob.mx/") }
        val shell = descargarPagina(urlDetalle) ?: run { NetLog.log("   ✗ No se pudo descargar el cascarón"); return }
        val raiz = "https://sjf2.scjn.gob.mx/"
        val nombres = Regex("""(?:src|href)\s*=\s*["']([^"']+\.m?js[^"']*)["']""")
            .findAll(shell).map { it.groupValues[1] }.distinct().toList()
        val scripts = nombres.map { u ->
            when {
                u.startsWith("http") -> if (u.contains("scjn.gob.mx")) u else null
                u.startsWith("/") -> raiz + u.trimStart('/'); else -> raiz + u
            }
        }.filterNotNull().distinct()
        if (scripts.isEmpty()) { NetLog.log("   ✗ El cascarón no lista scripts"); return }
        NetLog.log("   Scripts: ${scripts.joinToString(", ") { it.substringAfterLast('/') }}")
        val cola = ArrayDeque(scripts); val vistos = scripts.toMutableSet()
        val emitidos = mutableSetOf<String>(); val candidatos = LinkedHashSet<String>(); var escaneados = 0
        val chunkJs = Regex("""[A-Za-z0-9_./-]{0,60}\.?[a-f0-9]{16}\.js""")
        val chunkPar = Regex("""(\d{1,4})\s*:\s*"([a-f0-9]{16})"""")
        while (cola.isNotEmpty() && escaneados < 25) {
            val url = cola.removeFirst(); val codigo = descargarScript(url) ?: continue; escaneados++
            scanJsDetalle(url.substringAfterLast('/'), codigo, emitidos, candidatos)
            chunkJs.findAll(codigo).map { it.value }.distinct().take(20).forEach { c ->
                val cand = if (c.startsWith("http")) c else raiz + c.trimStart('/')
                if (cand !in vistos) { vistos.add(cand); cola.addLast(cand) }
            }
            chunkPar.findAll(codigo).distinctBy { it.groupValues[2] }.take(30).forEach { m ->
                val cand = raiz + "${m.groupValues[1]}.${m.groupValues[2]}.js"
                if (cand !in vistos) { vistos.add(cand); cola.addLast(cand) }
            }
        }
        NetLog.log("   Scripts analizados: $escaneados · rutas candidatas: ${candidatos.size}")
        if (candidatos.isEmpty()) { NetLog.log("   Sin rutas candidatas (scripts bloqueados o sin referencias)."); return }
        // Filtrar candidatos: solo nos interesan rutas que contengan "/tesis"
        // (no "/ejecutorias", "/votos", "/acuerdos", "/indices", "/thesaurus",
        // "/historicalfile" — esos son microservicios distintos que no devuelven
        // el detalle de una tesis).
        val rutasTesis = candidatos.filter { it.contains("/tesis", true) }
        NetLog.log("   Candidatas /tesis: ${rutasTesis.joinToString(" · ")} (${rutasTesis.size}/${candidatos.size})")
        if (rutasTesis.isEmpty()) { NetLog.log("   ✗ Sin rutas /tesis candidatas."); return }
        // Probar endpoints en orden; detenerse apenas se adopte uno válido.
        for (c in rutasTesis.take(5)) {
            if (detalleEndpoint != null) {
                NetLog.log("   ✓ Endpoint ya adoptado · se omiten las demás candidatas")
                return
            }
            probarEndpointDetalle(c, muestra)
        }
    }

    private fun descargarScript(url: String): String? = runCatching {
        val b = Request.Builder().url(url).header("User-Agent", UA).header("Accept", "*/*")
            .header("Accept-Language", "es-MX,es;q=0.9")
            .header("Referer", "https://sjf2.scjn.gob.mx/busqueda-principal-tesis")
            .header("sec-fetch-dest", "script").header("sec-fetch-mode", "cors")
            .header("sec-fetch-site", "same-origin")
            .header("sec-ch-ua", "\"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
            .header("sec-ch-ua-mobile", "?1").header("sec-ch-ua-platform", "\"Android\"")
        if (cookies.isNotEmpty()) b.header("Cookie", cookies.joinToString("; "))
        client.newCall(b.get().build()).execute().use { r ->
            if (!r.isSuccessful) { NetLog.log("   GET ${url.substringAfterLast('/').take(40)} → HTTP ${r.code}"); return@use null }
            r.body?.string()
        }
    }.getOrNull()

    private fun scanJsDetalle(nombre: String, codigo: String, emitidos: MutableSet<String>, candidatos: MutableSet<String>) {
        for (p in listOf("api/public", "sjftesismicroservice", "detalleTesis", "obtenerTesis",
                         "consultaTesis", "getTesis", "textoTesis", "detalle/tesis", "tesis/detalle")) {
            var idx = codigo.indexOf(p); var n = 0
            while (idx >= 0 && n < 2) {
                val frag = codigo.substring((idx - 250).coerceAtLeast(0), (idx + 320).coerceAtMost(codigo.length))
                    .replace(Regex("\\s+"), " ")
                if (emitidos.add(frag)) {
                    NetLog.log("📦 $nombre · «$p» → ${frag.take(560)}")
                    Regex("""["']((?:https?://|/)[^"'\s]{3,90})["']""").findAll(frag).forEach { m ->
                        val v = m.groupValues[1]
                        if (v.contains("tesis", true) || v.contains("detalle", true) || v.contains("api", true)) candidatos += v
                    }
                }
                n++; idx = codigo.indexOf(p, idx + p.length)
            }
        }
    }

    private suspend fun probarEndpointDetalle(ruta: String, muestra: Tesis) {
        val base = if (ruta.startsWith("http")) ruta
                   else "https://sjf2.scjn.gob.mx" + (if (ruta.startsWith("/")) ruta else "/$ruta")
        val ius = muestra.registro.toString()
        for ((metodo, url) in listOf("GET" to base, "GET" to "$base/$ius", "POST" to base)) {
            val ok = runCatching {
                val r = if (metodo == "GET")
                    executeWithRetry { h -> Request.Builder().url(url).headers(h).get().build() }
                else
                    executeWithRetry { h ->
                        Request.Builder().url(url).headers(h)
                            .post(JSONObject().put("ius", JSONArray(listOf(ius))).toString().toRequestBody(JSON_MEDIA)).build()
                    }
                r.use { resp ->
                    val raw = runCatching { resp.body?.string() }.getOrNull() ?: ""
                    NetLog.log("   $metodo ${url.substringAfter("gob.mx").take(80)} → HTTP ${resp.code} · ${raw.replace(Regex("\\s+"), " ").take(80)}")
                    resp.isSuccessful && pareceDetalle(raw, muestra)
                }
            }.getOrDefault(false)
            if (ok && detalleEndpoint == null) {
                detalleEndpoint = if (metodo == "GET" && url.endsWith(ius)) "GET|$base/%IUS%" else "$metodo|$base"
                NetLog.log("   ★ Endpoint de detalle ADOPTADO: $detalleEndpoint")
                return
            }
        }
    }

    private fun pareceDetalle(raw: String, muestra: Tesis): Boolean {
        val t = raw.trim()
        if (!t.startsWith("{") && !t.startsWith("[")) return false
        // Verificación estricta: la respuesta debe contener el ius solicitado.
        // Sin esto, endpoints como /historicalfile devuelven 200 con cualquier documento
        // histórico (p. ej. ius=1 de 1898) y se adoptan erróneamente.
        val iusRegex = Regex("\"ius\"\\s*:\\s*${muestra.registro}(?:\\D|\$)")
        if (!iusRegex.containsMatchIn(raw)) {
            NetLog.log("   ✗ Respuesta no contiene el ius solicitado (${muestra.registro})")
            return false
        }
        val prefijo = muestra.rubro.take(30)
        val tieneTexto = Regex("\"texto\"\\s*:\\s*\".{40,}\"").containsMatchIn(raw)
        return (prefijo.length > 10 && raw.contains(prefijo)) || tieneTexto
    }

    private fun objetoATexto(e: JSONObject): String? {
        for (k in listOf("value", "valor", "nombre", "materia", "descripcion", "texto", "asunto", "clave")) {
            val s = e.optString(k)
            if (s.isNotBlank() && s != "null") return s.trim()
        }
        val claves = e.keys()
        while (claves.hasNext()) {
            val s = e.optString(claves.next())
            if (s.isNotBlank() && s != "null") return s.trim()
        }
        return null
    }

    private fun parseTesis(o: JSONObject): Tesis {
        fun str(k: String): String = if (o.isNull(k)) "" else o.optString(k)
        fun listaOTexto(clave: String): String {
            if (o.isNull(clave)) return ""
            return when (val v = o.opt(clave)) {
                is JSONArray -> (0 until v.length()).mapNotNull { i ->
                    when (val e = v.opt(i)) {
                        is String -> e.trim().ifBlank { null }
                        is JSONObject -> objetoATexto(e)
                        else -> null
                    }
                }.joinToString(", ")
                is JSONObject -> objetoATexto(v) ?: ""
                else -> o.optString(clave)
            }
        }
        val materia = listaOTexto("materias").ifBlank { listaOTexto("materia") }
        val precedentes = if (o.isNull("precedentes")) "" else when (val p = o.opt("precedentes")) {
            is JSONArray -> (0 until p.length()).mapNotNull { i ->
                when (val e = p.opt(i)) {
                    is JSONObject -> e.optString("asunto").ifBlank { e.optString("precedente") }.ifBlank { e.optString("texto") }.ifBlank { objetoATexto(e) ?: "" }.ifBlank { null }
                    is String -> e.trim().ifBlank { null }
                    else -> null
                }
            }.joinToString(" · ")
            is JSONObject -> objetoATexto(p) ?: ""
            else -> o.optString("precedentes")
        }
        val registro = when {
            !o.isNull("ius") && o.optLong("ius", 0L) != 0L -> o.optLong("ius")
            !o.isNull("registro") && o.optLong("registro", 0L) != 0L -> o.optLong("registro")
            else -> str("id").toLongOrNull() ?: 0L
        }
        val tipoTesis = str("tipoTesis")
        val ta = str("ta_tj").uppercase().replace(".", "").replace(" ", "")
        val tipoBase = when {
            tipoTesis.contains("Aislada", ignoreCase = true) -> "Tesis Aislada"
            tipoTesis.contains("Jurisprudencia", ignoreCase = true) -> "Jurisprudencia"
            ta == "TJ" -> "Jurisprudencia"
            ta == "TA" -> "Tesis Aislada"
            str("tipo") == "1" -> "Tesis Aislada"
            str("tipo") == "2" -> "Jurisprudencia"
            str("tipoJurisprudencia").isNotBlank() -> "Jurisprudencia"
            else -> str("tipo").ifBlank { str("tipoDocumento") }
        }
        // Limpiar HTML de rubro y precedente: la API SCJN los devuelve envueltos en
        // <p>…</p>, y sin esto los marcadores se filtran a citas, tarjetas y PDFs.
        val rubroLimpio = limpiarHtml(str("rubro")).trim()
        val precedenteLimpio = limpiarHtml(
            str("precedente").ifBlank { str("asunto") }.ifBlank { precedentes }
        ).trim()
        var t = Tesis(
            registro = registro, rubro = rubroLimpio, texto = str("texto"),
            epoca = str("epoca").ifBlank { str("epocaAbr") },
            instancia = str("instancia").ifBlank { str("instanciaAbr") },
            tipo = tipoBase, materia = materia,
            // La fecha se normaliza de inmediato al formato legible «d de mes del
            // año»: la API la entrega como texto humano, ISO o epoch según la
            // edición, y así todo lo que se guarda y muestra queda uniforme.
            fecha = str("fecha").let { parseFechaLegible(it)?.legible() ?: it },
            precedente = precedenteLimpio,
            // Datos de localización oficiales. La API rellena con espacios
            // («P. CLXV/2000        », « 1659») y usa «0» como página de las
            // tesis solo digitales.
            clave = str("claveTesis").trim(),
            fuente = str("fuente").trim(),
            volumen = str("volumen").trim(),
            tomo = str("subVolumen").trim().ifBlank { str("tomo").trim() },
            pagina = str("pagina").trim().takeIf { it.isNotBlank() && it != "0" } ?: "",
            publicacion = limpiarHtml(str("textoPublicacion")).trim(),
            votos = registrosDe(o, "votos"),
            ejecutorias = registrosDe(o, "ejecutorias")
        )
        val loc = str("localizacion").ifBlank { str("localizacionAbr") }
        if (loc.isNotBlank()) {
            val partes = loc.split(";").map { it.trim() }
            if (t.tipo.isBlank()) t = t.copy(tipo = when {
                loc.contains("[J]", true) -> "Jurisprudencia"
                loc.contains("[T]", true) -> "Tesis Aislada"
                else -> t.tipo
            })
            if (t.epoca.isBlank() && partes.size > 1) t = t.copy(epoca = partes[1])
            if (t.instancia.isBlank() && partes.size > 2) t = t.copy(instancia = partes[2])
        }
        // Fallback 1: «fechaPublicacion». Según la edición llega como epoch
        // (segundos o milisegundos) o como texto (ISO «2015-02-01T00:00:00Z»,
        // epoch en dígitos o fecha humana); se normaliza a texto legible.
        // Nota: se acepta el rango completo 1910–2036 — el filtro anterior
        // (ms > 1e12) descartaba de golpe todas las fechas anteriores a 2001.
        if (t.fecha.isBlank()) {
            when (val fp = o.opt("fechaPublicacion")) {
                is Long -> fechaLegibleDeEpoch(fp)?.let { t = t.copy(fecha = it) }
                is Int -> fechaLegibleDeEpoch(fp.toLong())?.let { t = t.copy(fecha = it) }
                is Double -> fechaLegibleDeEpoch(fp.toLong())?.let { t = t.copy(fecha = it) }
                is String -> if (fp.isNotBlank()) {
                    val legible = fp.toLongOrNull()?.let { fechaLegibleDeEpoch(it) }
                        ?: parseFechaLegible(fp)?.legible()
                    t = t.copy(fecha = legible ?: fp)
                }
                else -> {}
            }
        }
        // Fallback 2: la fecha vive DENTRO de la publicación o la localización.
        // La Gaceta impresa y las épocas antiguas no traen «fecha» ni
        // «fechaPublicacion»: lo único disponible es «Febrero de 2015» dentro
        // de «S.J.F. y su Gaceta, Libro 19, Febrero de 2015, Tomo I». Por eso
        // se busca primero una fecha completa («26 de enero del 2018») y luego
        // mes-año («Febrero de 2015»).
        if (t.fecha.isBlank()) {
            for (clave in listOf("textoPublicacion", "localizacion", "localizacionAbr")) {
                val fuente = str(clave)
                if (fuente.isBlank()) continue
                val f = extraerFecha(fuente) ?: continue
                t = t.copy(fecha = f.legible())
                break
            }
        }
        return t
    }
}
