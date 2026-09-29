package app.gabrielaraujo.gymtrack.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Desembrulha o Context do Compose até achar a Activity (necessário p/ Credential Manager). */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
