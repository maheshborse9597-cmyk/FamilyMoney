package com.familymoney.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

object Pal {
    val Brand = Color(0xFF4F46E5)
    val Brand2 = Color(0xFF7C3AED)
    val Income = Color(0xFF059669)
    val Expense = Color(0xFFE11D48)
    val Invest = Color(0xFF7C3AED)
    val Transfer = Color(0xFF0284C7)
    val Warn = Color(0xFFD97706)
    val Bad = Color(0xFFDC2626)
    val Ok = Color(0xFF059669)

    val IncomeDark = Color(0xFF34D399)
    val ExpenseDark = Color(0xFFFB7185)
    val InvestDark = Color(0xFFA78BFA)
    val TransferDark = Color(0xFF38BDF8)
    val WarnDark = Color(0xFFFBBF24)
    val BadDark = Color(0xFFF87171)
    val OkDark = Color(0xFF34D399)

    val Chart = listOf(
        Color(0xFF4F46E5), Color(0xFF06B6D4), Color(0xFF10B981), Color(0xFFF59E0B),
        Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF64748B)
    )
}

private val LightScheme = lightColorScheme(
    primary = Pal.Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF0FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Pal.Brand2,
    background = Color(0xFFF4F5FB),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F3F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE4E7F0),
    error = Pal.Bad
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF1E2447),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFA78BFA),
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE8ECF8),
    surface = Color(0xFF141A2E),
    onSurface = Color(0xFFE8ECF8),
    surfaceVariant = Color(0xFF1C2440),
    onSurfaceVariant = Color(0xFF94A3C4),
    outline = Color(0xFF3B4670),
    outlineVariant = Color(0xFF252E4D),
    error = Color(0xFFF87171)
)

@Composable
fun FamilyMoneyTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun typeColor(type: String): Color {
    val dark = isDarkTheme()
    return when (type) {
        "Income" -> if (dark) Pal.IncomeDark else Pal.Income
        "Investment" -> if (dark) Pal.InvestDark else Pal.Invest
        "Transfer" -> if (dark) Pal.TransferDark else Pal.Transfer
        else -> if (dark) Pal.ExpenseDark else Pal.Expense
    }
}

@Composable
fun okColor(): Color = if (isDarkTheme()) Pal.OkDark else Pal.Ok

@Composable
fun badColor(): Color = if (isDarkTheme()) Pal.BadDark else Pal.Bad

@Composable
fun warnColor(): Color = if (isDarkTheme()) Pal.WarnDark else Pal.Warn

/** Budget colour: green below the alert level, amber when close, red when over. */
@Composable
fun budgetColor(pct: Double, alertPct: Int): Color = when {
    pct > 100 -> badColor()
    pct >= alertPct -> warnColor()
    else -> okColor()
}

fun budgetLabel(pct: Double, alertPct: Int): String = when {
    pct > 100 -> "Over budget"
    pct >= alertPct -> "Near limit"
    else -> "Within budget"
}
