package app.gabrielaraujo.gymtrack.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta RepStack (visual/repstack_brand_package/guidelines/brand-guide.md).
 * Primária verde-limão sobre fundo quase preto; neutros frios sem o azul do Slate antigo.
 * Os nomes Slate* foram mantidos para não tocar em todas as telas; os valores seguem a marca.
 */
object TC {
    // Marca
    val Lime = Color(0xFFA3FF12)       // primária: ações, indicadores, marca
    val LimeSoft = Color(0xFFC6FF66)   // texto e ícones em verde sobre fundo escuro
    val LimeDeep = Color(0xFF7FCC0A)   // estado pressionado / bordas
    val OnLime = Color(0xFF0B0D0F)     // texto sobre a primária

    // Fundo e superfícies
    val Background = Color(0xFF0B0D0F)
    val Slate950 = Color(0xFF0F1215)
    val Slate900 = Color(0xFF1A1F24)   // superfície
    val Slate800 = Color(0xFF242A30)
    val Slate700 = Color(0xFF30373E)
    val Slate600 = Color(0xFF4B5563)
    val Slate500 = Color(0xFF6B7280)   // texto secundário
    val Slate400 = Color(0xFF9CA3AF)
    val Slate300 = Color(0xFFD1D5DB)

    // Semânticas (mantidas)
    val Emerald700 = Color(0xFF047857)
    val Emerald600 = Color(0xFF059669)
    val Emerald500 = Color(0xFF10B981)
    val Emerald400 = Color(0xFF34D399)

    val Yellow500 = Color(0xFFEAB308)
    val Yellow400 = Color(0xFFFACC15)
    val Orange400 = Color(0xFFFB923C)

    val Red600 = Color(0xFFDC2626)
    val Red400 = Color(0xFFF87171)

    val White = Color(0xFFF3F4F6)      // texto principal da marca
}
