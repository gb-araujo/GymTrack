package app.gabrielaraujo.gymtrack.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.health.connect.client.HealthConnectClient

/**
 * Atalhos para o Samsung Health. Não há integração direta: o Samsung Health sincroniza com o
 * Health Connect nos dois sentidos, e o RepStack lê e grava lá.
 */
object SamsungHealth {
    const val PACKAGE = "com.sec.android.app.shealth"

    /** App do Health Connect no Android 13 ou menor (no 14+ ele faz parte do sistema). */
    private const val HEALTH_CONNECT_APK = "com.google.android.apps.healthdata"

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    fun open(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { start(context, it) } ?: false

    /**
     * Abre no Health Connect as permissões do Samsung Health, onde se libera a leitura de "Exercício"
     * (sem ela o Samsung Health não importa os treinos do RepStack). Se o aparelho não abrir a tela
     * do app direto, cai nas configurações gerais do Health Connect.
     */
    fun openHealthConnectPermissions(context: Context): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                add(Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, PACKAGE))
            } else {
                add(
                    Intent("androidx.health.ACTION_MANAGE_HEALTH_PERMISSIONS")
                        .setPackage(HEALTH_CONNECT_APK)
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, PACKAGE),
                )
            }
            add(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        }
        return candidates.any { start(context, it) }
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
