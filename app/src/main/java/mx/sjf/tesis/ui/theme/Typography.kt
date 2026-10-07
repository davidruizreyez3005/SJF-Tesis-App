package mx.sjf.tesis.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import mx.sjf.tesis.R

/**
 * Tipografía de display con carácter editorial/judicial (Playfair Display).
 * Se usa en encabezados, títulos de pantalla y la marca — el cuerpo del texto
 * mantiene la sans del sistema para máxima legibilidad.
 */
val DisplayFont = FontFamily(
    Font(R.font.playfair_display_600, FontWeight.SemiBold),
    Font(R.font.playfair_display_700, FontWeight.Bold)
)

/**
 * Texto corrido en español: división silábica y cortes de línea pensados para
 * párrafos. Sin esto, el texto justificado de tesis y sentencias deja huecos
 * enormes entre palabras en la columna angosta de un teléfono.
 */
private val Lectura = TextStyle(
    localeList = LocaleList("es-MX"),
    hyphens = Hyphens.Auto,
    lineBreak = LineBreak.Paragraph
)

/** Tipografía escalable por el usuario (fontScale en Settings). */
fun appTypography(scale: Float): Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFont,
        fontSize = 28.sp * scale, lineHeight = 34.sp * scale,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.5f).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFont,
        fontSize = 22.sp * scale, lineHeight = 28.sp * scale,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.2f).sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFont,
        fontSize = 20.sp * scale, lineHeight = 26.sp * scale,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.1f.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp * scale, lineHeight = 22.sp * scale,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.2f.sp
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp * scale, lineHeight = 18.sp * scale,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.4f.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp * scale, lineHeight = 26.sp * scale
    ).merge(Lectura),
    bodyMedium = TextStyle(
        fontSize = 14.sp * scale, lineHeight = 21.sp * scale
    ).merge(Lectura),
    bodySmall = TextStyle(
        fontSize = 12.sp * scale, lineHeight = 17.sp * scale
    ).merge(Lectura),
    labelLarge = TextStyle(
        fontSize = 14.sp * scale, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4f.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp * scale, fontWeight = FontWeight.Medium,
        letterSpacing = 0.4f.sp
    ),
    labelSmall = TextStyle(
        fontSize = 10.5f.sp * scale, lineHeight = 15.sp * scale,
        fontWeight = FontWeight.Medium, letterSpacing = 0.5f.sp
    )
)

/** Tipografía monoespaciada para el registro de red. */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.5f.sp,
    lineHeight = 15.sp
)
