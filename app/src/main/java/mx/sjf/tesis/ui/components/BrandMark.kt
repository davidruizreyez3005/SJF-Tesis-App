package mx.sjf.tesis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mx.sjf.tesis.ui.theme.Gold
import mx.sjf.tesis.ui.theme.GoldLight
import mx.sjf.tesis.ui.theme.NavyBg

/**
 * Emblema de la app: balanza sobre placa dorada. Usa un ícono vectorial en
 * lugar del carácter «⚖», que cada fabricante dibuja distinto (o como emoji).
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = RoundedCornerShape(size * 0.3f)
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Gold, GoldLight))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Balance,
            contentDescription = null,
            tint = NavyBg,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}
