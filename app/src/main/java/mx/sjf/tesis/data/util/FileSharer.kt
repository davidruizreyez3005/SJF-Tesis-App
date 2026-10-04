package mx.sjf.tesis.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Helper para compartir y abrir archivos generados localmente (PDFs y ZIPs)
 * que se guardaron en Documents/SJF_Tesis/ vía PdfStorage.
 *
 * En Android 10+ los archivos se crean con MediaStore y la URI es del tipo
 * content://media/external/files/... — se puede compartir directamente sin
 * FileProvider. En Android 9- la URI ya viene del FileProvider.
 */
object FileSharer {

    /** Construye un Intent ACTION_SEND para compartir un archivo generado. */
    fun buildShareIntent(uri: Uri, mimeType: String = "application/pdf"): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Construye un Intent ACTION_VIEW para abrir el PDF en una app externa. */
    fun buildViewIntent(uri: Uri, mimeType: String = "application/pdf"): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Construye un chooser para mostrar las apps que pueden recibir el archivo. */
    fun buildChooser(shareIntent: Intent, title: String): Intent =
        Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
