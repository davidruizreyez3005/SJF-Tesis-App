package mx.sjf.tesis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.sjf.tesis.core.Constants
import mx.sjf.tesis.ui.theme.Gold
import mx.sjf.tesis.ui.theme.GoldLight
import mx.sjf.tesis.ui.theme.NavyBg

/**
 * Intro de marca: báscula dorada sobre navy con el nombre en serif editorial.
 * Se desvanece sola (~1 s) para no estorbar; cero dependencias externas.
 */
@Composable
fun BrandSplash(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var emblemVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        emblemVisible = true
        delay(700)
        visible = false
        delay(350) // tiempo de la animación de salida antes de quitar del árbol
        onFinished()
    }
    val emblemScale by animateFloatAsState(
        targetValue = if (emblemVisible) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "emblemScale"
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(NavyBg, NavyBg.copy(alpha = 0.98f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandMark(
                    size = 84.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = emblemScale
                        scaleY = emblemScale
                    }
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    "SJF Tesis",
                    style = MaterialTheme.typography.displaySmall,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Semanario Judicial de la Federación",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldLight.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
