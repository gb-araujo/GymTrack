package app.gabrielaraujo.gymtrack.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gabrielaraujo.gymtrack.ui.theme.TC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Ilustração de um exercício nativo: os quadros de início e fim do movimento, em
 * `assets/exercises/<exerciseId>/0.webp` e `1.webp` (fotos do free-exercise-db, domínio público;
 * geradas por scripts/fetch_exercise_images.py). Ficam em assets, e não em res/drawable, porque o
 * build de release remove recursos que só seriam encontrados pelo nome.
 *
 * O tamanho vem do [modifier]. Com [animate], alterna os quadros como uma animação simples.
 * Exercício sem ilustração (personalizado ou id desconhecido) mostra um ícone genérico.
 */
@Composable
fun ExerciseIllustration(
    exerciseId: String,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    cornerRadius: Dp = 10.dp,
    contentDescription: String? = null,
) {
    val context = LocalContext.current.applicationContext
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(cornerRadius)).background(TC.Slate800),
        contentAlignment = Alignment.Center,
    ) {
        val targetPx = if (constraints.hasBoundedWidth && constraints.maxWidth > 0) constraints.maxWidth else DEFAULT_TARGET_PX
        val frames by produceState<List<ImageBitmap>?>(null, exerciseId, targetPx) {
            value = ExerciseImages.load(context, exerciseId, targetPx)
        }
        val loaded = frames
        when {
            loaded == null -> Unit // carregando: só o fundo
            loaded.isEmpty() -> Icon(
                Icons.Default.FitnessCenter, contentDescription, tint = TC.Slate500,
                modifier = Modifier.fillMaxSize(0.5f),
            )
            else -> {
                var frame by remember(loaded) { mutableIntStateOf(0) }
                if (animate && loaded.size > 1) {
                    LaunchedEffect(loaded) {
                        while (true) {
                            delay(FRAME_MS)
                            frame = (frame + 1) % loaded.size
                        }
                    }
                }
                Image(loaded[frame], contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

private const val FRAME_MS = 1_000L
private const val DEFAULT_TARGET_PX = 480

/** Leitura das ilustrações em assets, com cache em memória dos quadros já decodificados. */
private object ExerciseImages {
    private const val DIR = "exercises"
    private const val FRAMES = 2

    @Volatile private var available: Set<String>? = null

    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Quadros do exercício reduzidos para cerca de [targetPx] de largura; lista vazia se não houver ilustração. */
    suspend fun load(context: Context, exerciseId: String, targetPx: Int): List<ImageBitmap> = withContext(Dispatchers.IO) {
        val ids = available ?: runCatching { context.assets.list(DIR)?.toSet() }.getOrNull().orEmpty().also { available = it }
        if (exerciseId !in ids) return@withContext emptyList()
        (0 until FRAMES).mapNotNull { frame -> decode(context, "$DIR/$exerciseId/$frame.webp", targetPx) }
    }

    private fun decode(context: Context, path: String, targetPx: Int): ImageBitmap? {
        val key = "$path@$targetPx"
        cache.get(key)?.let { return it }
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }
}
