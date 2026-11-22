package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Editorial Scholarly Palette - Light
val IndigoPrimary = Color(0xFF1E3A6E)
val OnIndigoPrimary = Color(0xFFFFFFFF)
val IndigoContainer = Color(0xFFDCE5FA)
val OnIndigoContainer = Color(0xFF0E2148)

val TerracottaSecondary = Color(0xFFC85A3F)
val OnTerracottaSecondary = Color(0xFFFFFFFF)
val TerracottaContainer = Color(0xFFFFDFD6)
val OnTerracottaContainer = Color(0xFF4A180B)

val SageTertiary = Color(0xFF257A5B)
val OnSageTertiary = Color(0xFFFFFFFF)
val SageContainer = Color(0xFFCFF3E2)
val OnSageContainer = Color(0xFF063825)

val WarmIvoryBackground = Color(0xFFFBF8F2)
val CharcoalInk = Color(0xFF191C22)
val PureCreamSurface = Color(0xFFFFFFFF)
val SandSurfaceVariant = Color(0xFFEFE9DE)
val SlateMutedText = Color(0xFF5A616E)
val WarmOutline = Color(0xFFD5CCB9)
val ErrorCrimson = Color(0xFFB3261E)
val ErrorContainerLight = Color(0xFFF9DEDC)

// Editorial Scholarly Palette - Dark
val IndigoPrimaryDark = Color(0xFFACC6FF)
val OnIndigoPrimaryDark = Color(0xFF092350)
val IndigoContainerDark = Color(0xFF1E3666)
val OnIndigoContainerDark = Color(0xFFDCE5FA)

val TerracottaSecondaryDark = Color(0xFFFFB4A0)
val OnTerracottaSecondaryDark = Color(0xFF561E0F)
val TerracottaContainerDark = Color(0xFF7D3421)
val OnTerracottaContainerDark = Color(0xFFFFDFD6)

val SageTertiaryDark = Color(0xFF7ADBB3)
val OnSageTertiaryDark = Color(0xFF003824)
val SageContainerDark = Color(0xFF12523B)
val OnSageContainerDark = Color(0xFFCFF3E2)

val MidnightSlateBackground = Color(0xFF10141D)
val CreamInkDark = Color(0xFFF2EFE9)
val NavyElevatedSurface = Color(0xFF19202E)
val DeepSlateVariant = Color(0xFF232B3C)
val CoolMutedDark = Color(0xFF9EA7B8)
val DarkOutline = Color(0xFF394256)

// Deck Accent Tokens
val AmberAccent = Color(0xFFD4831D)
val AmberContainer = Color(0xFFFFE9C7)
val PlumAccent = Color(0xFF6B3E7E)
val PlumContainer = Color(0xFFF2DDFB)

data class DeckAccentPalette(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

fun deckAccentPalette(key: String, isDark: Boolean): DeckAccentPalette =
    when (key.uppercase()) {
        "TERRACOTTA" -> if (isDark) {
            DeckAccentPalette(Color(0xFFFF9B82), Color(0xFF5A2517), Color(0xFFFFDFD6))
        } else {
            DeckAccentPalette(TerracottaSecondary, TerracottaContainer, OnTerracottaContainer)
        }
        "SAGE" -> if (isDark) {
            DeckAccentPalette(Color(0xFF7ADBB3), Color(0xFF124632), Color(0xFFCFF3E2))
        } else {
            DeckAccentPalette(SageTertiary, SageContainer, OnSageContainer)
        }
        "AMBER" -> if (isDark) {
            DeckAccentPalette(Color(0xFFFFC26B), Color(0xFF52350A), Color(0xFFFFE9C7))
        } else {
            DeckAccentPalette(AmberAccent, AmberContainer, Color(0xFF422800))
        }
        "PLUM" -> if (isDark) {
            DeckAccentPalette(Color(0xFFDEB2F3), Color(0xFF472756), Color(0xFFF2DDFB))
        } else {
            DeckAccentPalette(PlumAccent, PlumContainer, Color(0xFF351445))
        }
        else -> if (isDark) {
            DeckAccentPalette(IndigoPrimaryDark, IndigoContainerDark, OnIndigoContainerDark)
        } else {
            DeckAccentPalette(IndigoPrimary, IndigoContainer, OnIndigoContainer)
        }
    }
