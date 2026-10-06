package mx.sjf.tesis.data.util

import android.content.Context
import android.net.Uri
import mx.sjf.tesis.data.model.Tesis
import mx.sjf.tesis.pdf.DocumentoPdf
import mx.sjf.tesis.pdf.DocumentosPdf
import java.io.FilterOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Guarda en Documents/SJF_Tesis/ los documentos que arma [DocumentosPdf].
 *
 * El motor PDF ([mx.sjf.tesis.pdf]) escribe en flujo y con las fuentes
 * estándar del PDF, así que una ejecutoria de 40 páginas pesa ~100 KB y se
 * genera en milisegundos, sin pasar por un Canvas de Android.
 */
object PdfExporter {

    /** Resultado de una exportación: URI pública + nombre legible. */
    data class ExportResult(val uri: Uri, val fileName: String, val mimeType: String)

    private const val PDF = "application/pdf"
    private const val ZIP = "application/zip"

    /** Escribe un documento (tesis, ejecutoria, voto o expediente). */
    fun guardar(context: Context, doc: DocumentoPdf): ExportResult {
        val uri = PdfStorage.saveToPublicDocuments(context, doc.nombreArchivo, PDF) { os -> doc.escribir(os) }
        return ExportResult(uri.getOrThrow(), doc.nombreArchivo, PDF)
    }

    /** Varias tesis en un solo PDF, con índice vinculado y marcadores. */
    fun exportCombined(context: Context, tesis: List<Tesis>): ExportResult =
        guardar(context, DocumentosPdf.compilacion(tesis, "SJF_Tesis_Compilacion_${marcaDeTiempo()}.pdf"))

    /** Un PDF por documento, empaquetados en un ZIP (cada uno se escribe directo al ZIP). */
    fun exportZip(context: Context, docs: List<DocumentoPdf>): ExportResult {
        val name = "SJF_Tesis_${marcaDeTiempo()}.zip"
        val uri = PdfStorage.saveToPublicDocuments(context, name, ZIP) { os ->
            val zip = ZipOutputStream(os)
            val usados = mutableSetOf<String>()
            docs.forEach { doc ->
                zip.putNextEntry(ZipEntry(nombreUnico(doc.nombreArchivo, usados)))
                doc.escribir(SinCerrar(zip))
                zip.closeEntry()
            }
            zip.finish()
        }
        return ExportResult(uri.getOrThrow(), name, ZIP)
    }

    private fun marcaDeTiempo(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** Evita entradas repetidas en el ZIP («Tesis_X.pdf», «Tesis_X (2).pdf»). */
    private fun nombreUnico(nombre: String, usados: MutableSet<String>): String {
        if (usados.add(nombre)) return nombre
        val base = nombre.substringBeforeLast('.')
        val ext = nombre.substringAfterLast('.', "")
        var n = 2
        while (!usados.add("$base ($n).$ext")) n++
        return "$base ($n).$ext"
    }

    /** El escritor no debe cerrar el ZIP al terminar cada entrada. */
    private class SinCerrar(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = flush()
    }
}
