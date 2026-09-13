package app.gabrielaraujo.track.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlin.random.Random

/** Desembrulha o Context do Compose até achar a Activity (necessário p/ Credential Manager). */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-"

/** Gerador de id curto no espírito do nanoid usado no app web. */
fun nanoid(size: Int = 12): String =
    buildString(size) { repeat(size) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) } }
