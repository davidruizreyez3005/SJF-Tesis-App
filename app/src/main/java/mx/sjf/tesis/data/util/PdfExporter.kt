package mx.sjf.tesis.data.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import mx.sjf.tesis.core.Constants
import mx.sjf.tesis.data.model.Tesis
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generador de PDFs de tesis con formato de documento legal premium.
 *
 * Inspirado en un script de ReportLab que replica el diseño editorial del
 * Semanario Judicial de la Federación:
 *
 *   - Tipografía serif (Typeface.SERIF ≈ Noto Serif / Times)
 *   - Masthead centrado con "PODER JUDICIAL DE LA FEDERACIÓN"
 *   - Rubro en negrita, justificado
 *   - Precedente en cursiva gris
 *   - Tabla de metadatos con filetes horizontales
 *   - Secciones: HECHOS, CRITERIO JURÍDICO, JUSTIFICACIÓN
 *   - Cuerpo justificado con sangría de primera línea
 *   - Tribunal centrado al final
 *   - Caja de cita con borde y filete izquierdo grueso
 *   - Header/footer con filetes hairline en cada página
 *
 * Soporta tres modos de exportación:
 *   - PDF individual (una tesis)
 *   - PDF combinado (varias tesis con portada)
 *   - ZIP de PDFs individuales
 */
object PdfExporter {

    // Dimensiones Letter en puntos PDF (1pt = 1/72 inch).
    private const val PAGE_WIDTH = 612  // 8.5 inch × 72
    private const val PAGE_HEIGHT = 792 // 11 inch × 72
    private const val MARGIN = 72f       // 1 inch
    private const val MARGIN_TOP = 68f   // 0.95 inch (espacio para header)
    private const val MARGIN_BOTTOM = 65f // 0.9 inch (espacio para footer)
    private val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    // Paleta — tinta negra sobre blanco, grises para texto secundario.
    private val INK = Color.parseColor("#1A1A1A")
    private val GRAY = Color.parseColor("#4A4A4A")
    private val RULE = Color.parseColor("#1A1A1A")
    private val LIGHT_RULE = Color.parseColor("#BBBBBB")

    // Typefaces serif — equivalente a Times-Roman en Android.
    private val serifNormal = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val serifItalic = Typeface.create(Typeface.SERIF, Typeface.ITALIC)

    // Paints reutilizables — cada estilo del script ReportLab tiene su equivalente.
    private val kickerBoldPaint = Paint().apply {
        typeface = serifBold; textSize = 10.5f; color = INK; isAntiAlias = true
    }
    private val kickerPaint = Paint().apply {
        typeface = serifNormal; textSize = 9.5f; color = GRAY; isAntiAlias = true
    }
    private val registryPaint = Paint().apply {
        typeface = serifNormal; textSize = 9f; color = GRAY; isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        typeface = serifBold; textSize = 13f; color = INK; isAntiAlias = true
    }
    private val precedentPaint = Paint().apply {
        typeface = serifItalic; textSize = 9.5f; color = GRAY; isAntiAlias = true
    }
    private val metaLabelPaint = Paint().apply {
        typeface = serifBold; textSize = 9f; color = INK; isAntiAlias = true
    }
    private val metaValuePaint = Paint().apply {
        typeface = serifNormal; textSize = 9f; color = INK; isAntiAlias = true
    }
    private val headingPaint = Paint().apply {
        typeface = serifBold; textSize = 10.5f; color = INK; isAntiAlias = true
    }
    private val bodyPaint = Paint().apply {
        typeface = serifNormal; textSize = 10.5f; color = INK; isAntiAlias = true
    }
    private val tribunalPaint = Paint().apply {
        typeface = serifBold; textSize = 10f; color = INK; isAntiAlias = true
    }
    private val citeBodyPaint = Paint().apply {
        typeface = serifNormal; textSize = 8.5f; color = GRAY; isAntiAlias = true
    }
    private val footerPaint = Paint().apply {
        typeface = serifNormal; textSize = 8f; color = GRAY; isAntiAlias = true
    }
    private val tocEntryPaint = Paint().apply {
        typeface = serifNormal; textSize = 9.5f; color = INK; isAntiAlias = true
    }
    private val rulePaint = Paint().apply { color = RULE; strokeWidth = 1.2f }
    private val lightRulePaint = Paint().apply { color = LIGHT_RULE; strokeWidth = 0.5f }
    private val thickLeftRulePaint = Paint().apply { color = RULE; strokeWidth = 2.5f }

    /** Resultado de una exportación: URI pública + nombre legible. */
    data class ExportResult(val uri: Uri, val fileName: String, val mimeType: String)

    /** Genera un PDF individual para una tesis. */
    fun exportSingle(context: Context, tesis: Tesis): ExportResult {
        val name = "Tesis_${tesis.registro}_${sanitizeFileName(tesis.rubro)}.pdf"
        val result = PdfStorage.saveToPublicDocuments(context, name, "application/pdf") { os ->
            val doc = PdfDocument()
            renderTesis(doc, tesis, pageNumStart = 1)
            doc.writeTo(os)
            doc.close()
        }
        return ExportResult(result.getOrThrow(), name, "application/pdf")
    }

    /**
     * Genera un PDF combinado con varias tesis: portada, índice con números de
     * página y las tesis completas.
     *
     * El índice necesita saber en qué página arranca cada tesis, así que se
     * renderiza dos veces: una primera pasada sobre documentos de prueba para
     * contar páginas (el renderizado es determinista) y la pasada final que se
     * escribe al archivo.
     */
    fun exportCombined(context: Context, tesis: List<Tesis>, title: String = "Tesis seleccionadas"): ExportResult {
        val name = "SJF_Tesis_Combinado_${System.currentTimeMillis()}.pdf"
        val result = PdfStorage.saveToPublicDocuments(context, name, "application/pdf") { os ->
            val doc = PdfDocument()
            drawCoverPage(doc, title, tesis.size)
            // Primera pasada: medir cuántas páginas ocupa cada tesis.
            val pageCounts = tesis.map { t ->
                val scratch = PdfDocument()
                val pages = runCatching { renderTesis(scratch, t, pageNumStart = 1) }.getOrDefault(1)
                scratch.close()
                pages
            }
            // Segunda pasada: índice con páginas reales + tesis completas.
            val contentStart = drawTocPage(doc, tesis, pageCounts)
            var pageNum = contentStart
            tesis.forEach { t ->
                pageNum = renderTesis(doc, t, pageNumStart = pageNum)
            }
            doc.writeTo(os)
            doc.close()
        }
        return ExportResult(result.getOrThrow(), name, "application/pdf")
    }

    /** Genera PDFs individuales y los empaqueta en un ZIP. */
    fun exportZip(context: Context, tesis: List<Tesis>): ExportResult {
        val name = "SJF_Tesis_${System.currentTimeMillis()}.zip"
        val result = PdfStorage.saveToPublicDocuments(context, name, "application/zip") { os ->
            ZipOutputStream(os).use { zip ->
                tesis.forEach { t ->
                    val doc = PdfDocument()
                    renderTesis(doc, t, pageNumStart = 1)
                    val baos = ByteArrayOutputStream()
                    doc.writeTo(baos)
                    doc.close()
                    val entryName = "Tesis_${t.registro}_${sanitizeFileName(t.rubro)}.pdf"
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(baos.toByteArray())
                    zip.closeEntry()
                }
            }
        }
        return ExportResult(result.getOrThrow(), name, "application/zip")
    }

    // ───────────────────────────────────────────────────────────────────
    //   Renderizado de una tesis — formato de documento legal
    // ───────────────────────────────────────────────────────────────────

    private fun renderTesis(doc: PdfDocument, tesis: Tesis, pageNumStart: Int): Int {
        val ctx = PageContext(doc, tesis, pageNumStart)

        // ── Masthead ──
        ctx.y = drawCenteredText(ctx, "PODER JUDICIAL DE LA FEDERACIÓN", kickerBoldPaint, 13f)
        val subtitle = buildString {
            append("Semanario Judicial de la Federación")
            append("   ·   ")
            append("Gaceta de Tesis y Jurisprudencia")
        }
        ctx.y = drawCenteredText(ctx, subtitle, kickerPaint, 12f)
        ctx.y += 4f
        val registry = buildString {
            if (tesis.epoca.isNotBlank()) append(tesis.epoca)
            if (tesis.instancia.isNotBlank()) {
                if (isNotEmpty()) append("   —   ")
                append(tesis.instancia)
            }
            if (tesis.tipo.isNotBlank()) {
                if (isNotEmpty()) append("   —   ")
                append(tesis.tipo)
            }
        }
        if (registry.isNotBlank()) {
            ctx.y = drawCenteredText(ctx, registry, registryPaint, 11f)
        }
        ctx.y += 14f
        // Thick horizontal rule.
        ctx.canvas.drawLine(MARGIN, ctx.y, PAGE_WIDTH - MARGIN, ctx.y, rulePaint)
        ctx.y += 14f

        // ── Metadata table (compact, before rubro) ──
        ctx.y = drawMetaTable(ctx, tesis)
        ctx.y += 6f
        // Light rule.
        ctx.canvas.drawLine(MARGIN, ctx.y, PAGE_WIDTH - MARGIN, ctx.y, lightRulePaint)
        ctx.y += 14f

        // ── Title (rubro) — centered, bold ──
        ctx.y = drawCenteredText(ctx, tesis.rubro.ifBlank { "Tesis sin rubro" }, titlePaint, 18f)
        ctx.y += 16f

        // ── Body text with inline section labels ──
        // The SCJN format uses "Hechos:", "Criterio jurídico:", "Justificación:"
        // as inline bold labels at the start of each paragraph, not as separate
        // section headings. The label and the text are on the same paragraph.
        val plainTexto = textoPlano(tesis.texto)
        if (plainTexto.isNotBlank()) {
            for (paragraph in plainTexto.split("\n\n")) {
                val trimmed = paragraph.trim()
                if (trimmed.isEmpty()) continue
                ctx.y = drawInlineBoldLabelParagraph(ctx, trimmed, bodyPaint, 16f, 10f)
            }
        }

        // ── Tribunal name (centered, bold) — appears at end of body ──
        // Extract the tribunal name from the text if present (all-caps line).
        val tribunalName = extractTribunalName(plainTexto)
        if (tribunalName.isNotBlank()) {
            ctx.y += 10f
            ctx.y = drawCenteredText(ctx, tribunalName, tribunalPaint, 14f)
            ctx.y += 16f
        }

        // ── Precedentes (after body, before citation) ──
        if (tesis.precedente.isNotBlank()) {
            val precText = textoPlano(tesis.precedente)
            // Light rule before precedentes.
            ctx.canvas.drawLine(MARGIN, ctx.y, PAGE_WIDTH - MARGIN, ctx.y, lightRulePaint)
            ctx.y += 14f
            for (paragraph in precText.split("\n\n")) {
                val trimmed = paragraph.trim()
                if (trimmed.isEmpty()) continue
                ctx.y = drawJustifiedText(ctx, trimmed, precedentPaint, 13.5f, 8f)
            }
            ctx.y += 10f
        }

        // ── Citation box ──
        ctx.y += 6f
        drawCitationBox(ctx, tesis)

        // Footer on last page.
        drawFooter(ctx.canvas, ctx.pageNum, tesis)
        doc.finishPage(ctx.currentPage)

        return ctx.pageNum
    }

    // ───────────────────────────────────────────────────────────────────
    //   Page context + pagination
    // ───────────────────────────────────────────────────────────────────

    private class PageContext(
        val doc: PdfDocument,
        val tesis: Tesis,
        startPageNum: Int
    ) {
        var pageNum = startPageNum
        var currentPage: PdfDocument.Page = startPage(doc, startPageNum)
        var canvas: Canvas = currentPage.canvas
        var y: Float = MARGIN_TOP

        fun newPage(tesis: Tesis) {
            drawHeader(canvas, tesis)
            drawFooter(canvas, pageNum, tesis)
            doc.finishPage(currentPage)
            pageNum++
            currentPage = startPage(doc, pageNum)
            canvas = currentPage.canvas
            y = MARGIN_TOP
        }
    }

    private fun startPage(doc: PdfDocument, pageNum: Int): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        return doc.startPage(pageInfo)
    }

    // ───────────────────────────────────────────────────────────────────
    //   Drawing helpers
    // ───────────────────────────────────────────────────────────────────

    /** Texto centrado — para masthead y tribunal. */
    private fun drawCenteredText(ctx: PageContext, text: String, paint: Paint, lineHeight: Float): Float {
        val lines = wrapText(text, paint, CONTENT_WIDTH)
        for (line in lines) {
            if (ctx.y > PAGE_HEIGHT - MARGIN_BOTTOM - lineHeight) ctx.newPage(ctx.tesis)
            val textWidth = paint.measureText(line)
            val x = (PAGE_WIDTH - textWidth) / 2f
            ctx.canvas.drawText(line, x, ctx.y + paint.textSize, paint)
            ctx.y += lineHeight
        }
        return ctx.y
    }

    /** Texto justificado — para rubro y cuerpo. */
    private fun drawJustifiedText(
        ctx: PageContext,
        text: String,
        paint: Paint,
        lineHeight: Float,
        spaceAfter: Float,
        firstLineIndent: Float = 0f
    ): Float {
        val plain = textoPlano(text)
        if (plain.isBlank()) return ctx.y
        val paragraphs = plain.split("\n\n")
        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) continue
            val lines = wrapText(trimmed, paint, CONTENT_WIDTH - firstLineIndent)
            for ((idx, line) in lines.withIndex()) {
                if (ctx.y > PAGE_HEIGHT - MARGIN_BOTTOM - lineHeight) ctx.newPage(ctx.tesis)
                val indent = if (idx == 0) firstLineIndent else 0f
                // Justify all lines except the last in each paragraph.
                val isLastLine = (idx == lines.size - 1)
                if (isLastLine || lines.size == 1) {
                    ctx.canvas.drawText(line, MARGIN + indent, ctx.y + paint.textSize, paint)
                } else {
                    drawJustifiedLine(ctx.canvas, line, MARGIN + indent, ctx.y + paint.textSize,
                                      PAGE_WIDTH - MARGIN - indent, paint)
                }
                ctx.y += lineHeight
            }
            ctx.y += spaceAfter
        }
        return ctx.y
    }

    /**
     * Dibuja un párrafo con etiqueta en negrita al inicio (inline).
     * Detecta si el párrafo empieza con "Hechos:", "Criterio jurídico:",
     * "Justificación:" y dibuja esa parte en negrita, el resto en normal.
     */
    private fun drawInlineBoldLabelParagraph(
        ctx: PageContext,
        text: String,
        paint: Paint,
        lineHeight: Float,
        spaceAfter: Float
    ): Float {
        val sectionLabels = listOf("Hechos:", "Criterio jurídico:", "Justificación:")
        val matchedLabel = sectionLabels.find { label ->
            text.startsWith(label, ignoreCase = true)
        }

        if (matchedLabel == null) {
            // Sin etiqueta — dibujar como texto justificado normal.
            return drawJustifiedText(ctx, text, paint, lineHeight, spaceAfter)
        }

        // Dividir en etiqueta (bold) + resto (normal).
        val label = matchedLabel
        val rest = text.substring(label.length).trim()

        // Crear un paint bold para la etiqueta.
        val boldPaint = Paint(paint).apply { typeface = serifBold }

        // Medir ancho de la etiqueta + espacio.
        val labelWidth = boldPaint.measureText(label + " ")
        val availableWidth = CONTENT_WIDTH - labelWidth

        // Envolver el resto del texto considerando que la primera línea
        // empieza después de la etiqueta.
        val restLines = wrapText(rest, paint, if (availableWidth > 50) availableWidth else CONTENT_WIDTH)

        for ((idx, line) in restLines.withIndex()) {
            if (ctx.y > PAGE_HEIGHT - MARGIN_BOTTOM - lineHeight) ctx.newPage(ctx.tesis)
            if (idx == 0 && labelWidth + paint.measureText(line) <= CONTENT_WIDTH) {
                // Primera línea: etiqueta bold + texto normal en la misma línea.
                ctx.canvas.drawText(label + " ", MARGIN, ctx.y + boldPaint.textSize, boldPaint)
                ctx.canvas.drawText(line, MARGIN + labelWidth, ctx.y + paint.textSize, paint)
            } else {
                // Líneas siguientes: solo texto normal.
                val isLastLine = (idx == restLines.size - 1)
                if (isLastLine || restLines.size == 1) {
                    ctx.canvas.drawText(line, MARGIN, ctx.y + paint.textSize, paint)
                } else {
                    drawJustifiedLine(ctx.canvas, line, MARGIN, ctx.y + paint.textSize,
                                      PAGE_WIDTH - MARGIN, paint)
                }
            }
            ctx.y += lineHeight
        }
        ctx.y += spaceAfter
        return ctx.y
    }

    /**
     * Extrae el nombre del tribunal del texto de la tesis.
     * Busca una línea en mayúsculas (SCJN style) que contiene palabras como
     * "TRIBUNAL", "PLENO", "SALA", "CIRCUITO" seguida de "CON RESIDENCIA" o
     * "DEL PRIMER CIRCUITO".
     */
    private fun extractTribunalName(texto: String): String {
        for (line in texto.split("\n")) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            // Debe estar en mayúsculas y contener palabras clave de tribunal.
            val isUppercase = trimmed == trimmed.uppercase() && trimmed.length > 15
            val hasTribunalKeyword = listOf("TRIBUNAL", "PLENO", "SALA", "CIRCUITO").any {
                trimmed.contains(it, ignoreCase = true)
            }
            if (isUppercase && hasTribunalKeyword) {
                return trimmed
            }
        }
        return ""
    }

    /** Distribuye el texto de una línea para llenar el ancho (justificación).
     *  Solo justifica si el espacio entre palabras no excede 1.8× el espacio normal
     *  — evita los huecos enormes que se ven cuando una línea tiene pocas palabras. */
    private fun drawJustifiedLine(canvas: Canvas, text: String, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        val words = text.split(" ")
        if (words.size <= 1) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val naturalWidth = paint.measureText(text)
        val extraSpace = maxWidth - naturalWidth
        if (extraSpace <= 0) {
            canvas.drawText(text, x, y, paint)
            return
        }
        // Calcular el espacio normal entre palabras y comparar con el justificado.
        val normalSpaceWidth = paint.measureText(" ")
        val numGaps = words.size - 1
        val justifiedSpaceWidth = normalSpaceWidth + extraSpace / numGaps
        // Si el espacio justificado es más de 1.8× el normal, no justificar —
        // se ve mejor alineado a la izquierda que con huecos enormes.
        if (justifiedSpaceWidth > normalSpaceWidth * 1.8f) {
            canvas.drawText(text, x, y, paint)
            return
        }
        // Distribuir palabras con el espacio justificado.
        var cx = x
        for ((idx, word) in words.withIndex()) {
            canvas.drawText(word, cx, y, paint)
            cx += paint.measureText(word)
            if (idx < words.size - 1) cx += justifiedSpaceWidth
        }
    }


    /** Tabla de metadatos con filetes horizontales entre filas. */
    private fun drawMetaTable(ctx: PageContext, tesis: Tesis): Float {
        val entries = buildList {
            if (tesis.clave.isNotBlank()) add("TESIS" to tesis.clave)
            if (tesis.epoca.isNotBlank()) add("ÉPOCA" to tesis.epoca)
            if (tesis.instancia.isNotBlank()) add("INSTANCIA" to tesis.instancia)
            if (tesis.tipo.isNotBlank()) add("TIPO" to tesis.tipo)
            if (tesis.materia.isNotBlank()) add("MATERIA" to tesis.materia)
            if (tesis.fecha.isNotBlank()) add("FECHA" to formatDateLong(tesis.fecha))
            add("REGISTRO" to tesis.registro.toString())
            if (tesis.fuente.isNotBlank()) add("FUENTE" to tesis.fuente)
        }

        val labelWidth = 130f // ~1.3 inch
        val valueWidth = CONTENT_WIDTH - labelWidth
        val rowHeight = 18f // 4pt top + 4pt bottom padding + ~10pt text

        entries.forEachIndexed { idx, (label, value) ->
            if (ctx.y > PAGE_HEIGHT - MARGIN_BOTTOM - rowHeight * 2) ctx.newPage(ctx.tesis)
            val rowTop = ctx.y

            // Label (bold, left-aligned).
            ctx.canvas.drawText(label, MARGIN, rowTop + 12f, metaLabelPaint)
            // Value (may wrap to multiple lines).
            val valueLines = wrapText(value, metaValuePaint, valueWidth)
            for ((lineIdx, line) in valueLines.withIndex()) {
                ctx.canvas.drawText(line, MARGIN + labelWidth, rowTop + 12f + lineIdx * 12f, metaValuePaint)
            }

            ctx.y = rowTop + rowHeight

            // Hairline below each row (except the last).
            if (idx < entries.size - 1) {
                ctx.canvas.drawLine(MARGIN, ctx.y, PAGE_WIDTH - MARGIN, ctx.y, lightRulePaint)
            }
        }
        return ctx.y
    }

    /** Caja de cita con borde y filete izquierdo grueso. */
    private fun drawCitationBox(ctx: PageContext, tesis: Tesis) {
        val citation = CitationBuilder.referencia(tesis)
        val citeText = "CITA\n\n$citation"
        val lines = wrapText(citeText, citeBodyPaint, CONTENT_WIDTH - 28f) // 14pt padding each side

        // Calculate box height.
        val boxHeight = lines.size * 12.5f + 24f // 12pt top + 12pt bottom padding
        val boxTop = ctx.y + 10f
        val boxBottom = boxTop + boxHeight

        // If box doesn't fit, start new page.
        if (boxBottom > PAGE_HEIGHT - MARGIN_BOTTOM) {
            ctx.newPage(ctx.tesis)
            return drawCitationBox(ctx, tesis)
        }

        // Outer box (thin border).
        ctx.canvas.drawRect(MARGIN, boxTop, PAGE_WIDTH - MARGIN, boxBottom,
            Paint().apply { color = RULE; strokeWidth = 0.75f; style = Paint.Style.STROKE })
        // Left thick rule.
        ctx.canvas.drawRect(MARGIN, boxTop, MARGIN + 2.5f, boxBottom,
            Paint().apply { color = RULE; style = Paint.Style.FILL })

        // Text inside the box.
        var cy = boxTop + 12f + citeBodyPaint.textSize
        for (line in lines) {
            ctx.canvas.drawText(line, MARGIN + 14f, cy, citeBodyPaint)
            cy += 12.5f
        }

        ctx.y = boxBottom + 10f
    }

    // ───────────────────────────────────────────────────────────────────
    //   Page chrome — header + footer on every page
    // ───────────────────────────────────────────────────────────────────

    private fun drawHeader(canvas: Canvas, tesis: Tesis) {
        // Header hairline.
        canvas.drawLine(MARGIN, PAGE_HEIGHT - 44f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 44f, rulePaint)
        // Running head.
        canvas.drawText("SEMANARIO JUDICIAL DE LA FEDERACIÓN", MARGIN, PAGE_HEIGHT - 36f, footerPaint)
        val regText = "REGISTRO NO. ${tesis.registro}"
        val regWidth = footerPaint.measureText(regText)
        canvas.drawText(regText, PAGE_WIDTH - MARGIN - regWidth, PAGE_HEIGHT - 36f, footerPaint)
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, tesis: Tesis?) {
        // Footer hairline.
        canvas.drawLine(MARGIN, 49f, PAGE_WIDTH - MARGIN, 49f, lightRulePaint)
        // Center: page number.
        val pageText = "Página $pageNum"
        val pageWidth = footerPaint.measureText(pageText)
        canvas.drawText(pageText, (PAGE_WIDTH - pageWidth) / 2f, 36f, footerPaint)
        // Left: época.
        val epoca = tesis?.epoca?.ifBlank { "" } ?: ""
        if (epoca.isNotBlank()) {
            canvas.drawText(epoca, MARGIN, 36f, footerPaint)
        }
        // Right: tipo.
        val tipo = tesis?.tipo?.ifBlank { "" } ?: ""
        if (tipo.isNotBlank()) {
            val tipoWidth = footerPaint.measureText(tipo)
            canvas.drawText(tipo, PAGE_WIDTH - MARGIN - tipoWidth, 36f, footerPaint)
        }
    }

    // ───────────────────────────────────────────────────────────────────
    //   Índice (TOC) para el PDF combinado
    // ───────────────────────────────────────────────────────────────────

    /**
     * Dibuja la(s) página(s) de índice después de la portada. Cada entrada
     * lista el rubro de la tesis con puntos de guía y su página de inicio.
     * Devuelve el número de página donde debe arrancar la primera tesis.
     */
    private fun drawTocPage(doc: PdfDocument, tesis: List<Tesis>, pageCounts: List<Int>): Int {
        val lineH = 13f
        val entryGap = 4f
        val titleBlock = 84f

        // Entradas: rubro truncado a 2 líneas (la numeración y página se dibujan aparte).
        val entryLines: List<List<String>> = tesis.mapIndexed { idx, t ->
            val label = "${idx + 1}. ${t.rubro.ifBlank { "Tesis sin rubro" }}"
            wrapText(truncateWords(label, 110), tocEntryPaint, CONTENT_WIDTH - 60f).take(2)
        }

        // Paginación del propio índice: replicar la condición de corte del dibujo.
        var pages = 1
        var used = MARGIN_TOP + titleBlock
        for (lines in entryLines) {
            val blockH = lines.size * lineH + entryGap
            if (used + blockH > PAGE_HEIGHT - MARGIN_BOTTOM) {
                pages++
                used = MARGIN_TOP
            }
            used += blockH
        }
        val contentStart = 2 + pages

        // Página de inicio de cada tesis dentro del documento combinado.
        val starts = IntArray(tesis.size)
        var acc = contentStart
        for (i in tesis.indices) {
            starts[i] = acc
            acc += pageCounts[i]
        }

        // Dibujar el índice.
        var pageNum = 2
        var page = startPage(doc, pageNum)
        var canvas = page.canvas
        var y = MARGIN_TOP
        canvas.drawText("ÍNDICE", (PAGE_WIDTH - kickerBoldPaint.measureText("ÍNDICE")) / 2f,
            y + 26f, kickerBoldPaint)
        canvas.drawText("${tesis.size} tesis recopiladas",
            (PAGE_WIDTH - kickerPaint.measureText("${tesis.size} tesis recopiladas")) / 2f,
            y + 42f, kickerPaint)
        y += titleBlock
        canvas.drawLine(MARGIN, y - 12f, PAGE_WIDTH - MARGIN, y - 12f, rulePaint)

        for ((idx, lines) in entryLines.withIndex()) {
            val blockH = lines.size * lineH + entryGap
            if (y + blockH > PAGE_HEIGHT - MARGIN_BOTTOM) {
                drawFooter(canvas, pageNum, null)
                doc.finishPage(page)
                pageNum++
                page = startPage(doc, pageNum)
                canvas = page.canvas
                y = MARGIN_TOP
            }
            // Texto de la entrada (hasta 2 líneas).
            lines.forEachIndexed { li, line ->
                canvas.drawText(line, MARGIN, y + (li + 1) * lineH - 3f, tocEntryPaint)
            }
            // Página de inicio, alineada a la derecha sobre la última línea,
            // con puntos de guía cuando hay espacio.
            val lastLine = lines.last()
            val lastLineWidth = tocEntryPaint.measureText(lastLine)
            val pageText = "pág. ${starts[idx]}"
            val pageTextWidth = precedentPaint.measureText(pageText)
            val textBaseline = y + lines.size * lineH - 3f
            canvas.drawText(pageText, PAGE_WIDTH - MARGIN - pageTextWidth, textBaseline, precedentPaint)
            val dotStart = MARGIN + lastLineWidth + 6f
            val dotEnd = PAGE_WIDTH - MARGIN - pageTextWidth - 6f
            if (dotEnd > dotStart + 8f) {
                val dotWidth = tocEntryPaint.measureText(".")
                val dots = ((dotEnd - dotStart) / dotWidth).toInt()
                if (dots > 0) {
                    canvas.drawText(".".repeat(dots), dotStart, textBaseline, tocEntryPaint)
                }
            }
            y += blockH
        }
        drawFooter(canvas, pageNum, null)
        doc.finishPage(page)
        return contentStart
    }

    // ───────────────────────────────────────────────────────────────────
    //   Cover page for combined PDF
    // ───────────────────────────────────────────────────────────────────

    private fun drawCoverPage(doc: PdfDocument, title: String, count: Int) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        // Masthead.
        canvas.drawText("PODER JUDICIAL DE LA FEDERACIÓN",
            (PAGE_WIDTH - kickerBoldPaint.measureText("PODER JUDICIAL DE LA FEDERACIÓN")) / 2f,
            200f, kickerBoldPaint)
        canvas.drawLine(MARGIN, 230f, PAGE_WIDTH - MARGIN, 230f, rulePaint)

        // Title centered.
        val titleLines = wrapText(title, titlePaint, CONTENT_WIDTH)
        var ty = 320f
        for (line in titleLines) {
            val tw = titlePaint.measureText(line)
            canvas.drawText(line, (PAGE_WIDTH - tw) / 2f, ty, titlePaint)
            ty += 20f
        }

        // Count.
        val countText = "$count tesis recopiladas"
        val cw = registryPaint.measureText(countText)
        canvas.drawText(countText, (PAGE_WIDTH - cw) / 2f, ty + 40f, registryPaint)

        doc.finishPage(page)
    }

    // ───────────────────────────────────────────────────────────────────
    //   Text wrapping
    // ───────────────────────────────────────────────────────────────────

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) { result.add(""); continue }
            val words = paragraph.split(" ")
            val line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    line.setLength(0)
                    line.append(candidate)
                } else {
                    if (line.isNotEmpty()) {
                        result.add(line.toString())
                        line.setLength(0)
                    }
                    if (paint.measureText(word) > maxWidth) {
                        splitLongWord(word, paint, maxWidth).forEach { result.add(it) }
                    } else {
                        line.append(word)
                    }
                }
            }
            if (line.isNotEmpty()) result.add(line.toString())
        }
        return result.ifEmpty { listOf("") }
    }

    private fun splitLongWord(word: String, paint: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (char in word) {
            current.append(char)
            if (paint.measureText(current.toString()) > maxWidth) {
                if (current.length > 1) {
                    current.deleteAt(current.length - 1)
                    result.add(current.toString())
                    current.setLength(0)
                    current.append(char)
                }
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
