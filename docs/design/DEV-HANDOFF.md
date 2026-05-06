# Pulverfass · Dev Handoff

> Schnell-Referenz für die Devs: welche Tokens für welchen Screen?
> Begleit-Doc zur [Art Bible](./ART_BIBLE.md).

## Setup-Checklist

Vor der Implementierung muss folgendes ins Repo:

- [ ] `app/src/main/res/values/colors.xml` ← aus `android-tokens/`
- [ ] `app/src/main/res/values/dimens.xml` ← aus `android-tokens/`
- [ ] `app/src/main/res/values/type.xml` ← aus `android-tokens/`
- [ ] `app/src/main/res/values/themes.xml` ← aus `android-tokens/`
- [ ] Fonts in `app/src/main/res/font/`:
  - `cinzel_decorative.ttf` ([Google Fonts](https://fonts.google.com/specimen/Cinzel+Decorative))
  - `cormorant_garamond.ttf` ([Google Fonts](https://fonts.google.com/specimen/Cormorant+Garamond))
  - `philosopher.ttf` ([Google Fonts](https://fonts.google.com/specimen/Philosopher))
  - `fleur_de_leah.ttf` ([Google Fonts](https://fonts.google.com/specimen/Fleur+De+Leah))
- [ ] Theme in `AndroidManifest.xml` setzen: `android:theme="@style/Theme.Pulverfass"`

## Compose Token Bridge

Wenn ihr Compose statt XML nutzt, hier ein `Theme.kt` Skelett:

```kotlin
// ui/theme/Color.kt
object PulverfassColors {
    val SurfaceVoid = Color(0xFF0F1419)
    val SurfaceDark = Color(0xFF1A1F2A)
    val SurfaceWood = Color(0xFF2A1F18)
    val SurfaceCard = Color(0xFF1F1A14)

    val Parchment = Color(0xFFC8A876)
    val ParchmentLight = Color(0xFFD4B896)
    val ParchmentDark = Color(0xFFA8855A)
    val ParchmentEdge = Color(0xFF6B4D2A)

    val GoldBright = Color(0xFFE8C268)
    val Gold = Color(0xFFD4B45A)
    val GoldMuted = Color(0xFFC4A14B)
    val GoldDark = Color(0xFF8B6F2A)

    val PlayerRed = Color(0xFFA6342B)
    val PlayerBlue = Color(0xFF3A5BA0)
    val PlayerOrange = Color(0xFFD17030)
    val PlayerOlive = Color(0xFF8C8A2E)
    val PlayerPurple = Color(0xFF7A4A8C)
    val PlayerTeal = Color(0xFF4A8C8C)
    val PlayerPink = Color(0xFFB8466C)
    val PlayerGreen = Color(0xFF5A8C3A)

    val Danger = Color(0xFF8B2828)
    val DangerBright = Color(0xFFC44030)
    val Success = Color(0xFF4A7A4A)
    val Warning = Color(0xFFD17030)

    val TextPrimary = Color(0xFFF0E6D2)
    val TextOnParchment = Color(0xFF2A1F18)
    val TextMuted = Color(0xFF8B7F66)
    val TextGold = Color(0xFFD4B45A)
}

// ui/theme/Type.kt
val PulverfassTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = 0.96.sp,
        color = PulverfassColors.Gold
    ),
    headlineMedium = TextStyle(
        fontFamily = CinzelDecorative,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.4.sp,
        color = PulverfassColors.Gold
    ),
    bodyLarge = TextStyle(
        fontFamily = CormorantGaramond,
        fontSize = 16.sp,
        color = PulverfassColors.TextPrimary
    ),
    labelLarge = TextStyle(
        fontFamily = Philosopher,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.4.sp,
        color = PulverfassColors.Gold
    )
    // ... weitere Styles aus type.xml übernehmen
)

// ui/theme/Theme.kt
@Composable
fun PulverfassTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = PulverfassColors.Gold,
        onPrimary = PulverfassColors.SurfaceVoid,
        secondary = PulverfassColors.Parchment,
        onSecondary = PulverfassColors.TextOnParchment,
        background = PulverfassColors.SurfaceDark,
        onBackground = PulverfassColors.TextPrimary,
        surface = PulverfassColors.SurfaceCard,
        onSurface = PulverfassColors.TextPrimary,
        error = PulverfassColors.Danger,
        outline = PulverfassColors.GoldMuted
    )
    MaterialTheme(
        colorScheme = colors,
        typography = PulverfassTypography,
        content = content
    )
}
```

## Screen-by-Screen Token Mapping

### Lobby Screen (`28:55` im Figma)
| Element | Token |
|---|---|
| Background | Hintergrund-Image + `surface_overlay` 30% |
| "LOBBY" Schild-Text | `Pulverfass.Display.Large`, color `gold` |
| Input Fields ("Spielername", "ws://...") | Style `Pulverfass.Input` |
| "LOBBY ERSTELLEN" / "LOBBY BEITRETEN" | Style `Pulverfass.Button.Secondary` mit Gold-Text |
| "STATUS: NICHT VERBUNDEN" | `Pulverfass.Caption`, `text_muted` |

### Spielfeld (`62:3` / `106:46` im Figma)
| Element | Token |
|---|---|
| Top HUD Bar | BG `surface_wood`, Höhe `hud_top_height` (56dp) |
| "PHASE: ANGRIFF" / "RUNDE 3" | `Pulverfass.Headline`, color `gold` |
| Player Avatar Top-Left | `avatar_sm` (40dp), Border `gold_muted` |
| Bottom HUD Bar | BG `surface_wood`, Höhe `hud_bottom_height` (60dp) |
| Aktions-Buttons (VERSTÄRKEN/ANGRIFF/VERSCHIEBEN) | `Pulverfass.Button.Secondary` |
| Aktiver Button (state: active) | + `gold_bright` Border + `glow_gold` |
| RUNDE ENDE | `Pulverfass.Button.Danger` |
| KARTEN | `Pulverfass.Button.Secondary` |
| Spieler-Avatare rechts (4 Stück) | `avatar_md` (56dp) mit ornamenter Border |
| Truppen-Indikator auf Karte | weißer Kreis 28dp, Zahl `Pulverfass.Label` |
| Selected Territory Glow | 4dp `gold_bright` Border + `glow_gold` |

### Kampfscreen (`63:3` im Figma)
| Element | Token |
|---|---|
| Background | Schiffs-Image + `surface_overlay` 30% |
| "SCHLACHT" Title | `Pulverfass.Display.Large`, `gold` |
| "VS" Text | `Pulverfass.Display.Medium` |
| Spieler 1 Circle (Angreifer) | `avatar_lg`, BG `player_red` |
| Spieler 2 Circle (Verteidiger) | `avatar_lg`, BG `player_blue` |
| "ANGREIFER" / "VERTEIDIGER" Tags | `Pulverfass.Label` auf rotem/blauem BG, Radius `radius_md` |
| Truppen-Anzahl ("8" / "4") | `Pulverfass.Display.Large`, `text_primary` |
| Würfel Angreifer | `dice_size`, BG `player_red`, Border `danger_bright` |
| Würfel Verteidiger | `dice_size`, BG `player_blue`, Border heller blau |
| Würfel-Zahlen | Cinzel 28sp, `text_primary` |
| WÜRFELN! Button | `Pulverfass.Button.Primary` (gold) |
| RÜCKZUG Button | `Pulverfass.Button.Danger` |

### Karten-Screen (`64:3` im Figma)
| Element | Token |
|---|---|
| "Kriegskarten" Title | `Pulverfass.Display.Medium` |
| Schließen-Button (X) | `icon_lg` (32dp), `text_primary` |
| Trennlinie | 2dp `gold_dark` |
| Kriegskarten | `parchment` BG, `radius_lg` Radius, Border 2dp `parchment_edge` |
| Karten-Selection-Häkchen | `gold_bright` Kreis, weißes ✓ |
| EINLÖSEN Button | `Pulverfass.Button.Primary` |

### Rundenübergang (`65:3` im Figma)
| Element | Token |
|---|---|
| Background | Stuhl-Image + `surface_overlay` 50% |
| "Nächster ZUG" | `Pulverfass.Display.Medium` |
| Banner Strip | `surface_overlay` 70%, full-width |
| Player Circle | `avatar_xl` (150dp), BG = current player color |
| Glow Ring | `glow_player` mit current player color |
| Spieler-Name | `Pulverfass.Title` |
| Stats ("Gebiete: 8 • Truppen: 24 • Karten: 2") | `Pulverfass.Body`, `text_muted` |
| "Tippen zum Fortfahren..." | `Pulverfass.Caption`, italic, pulse-animation |

### Sieg / Verloren (`66:3` / `74:157` im Figma)
| Element | Token |
|---|---|
| Background | Sunset/Storm-Image + Vignette |
| "GEWONNEN !!!!" / "Verloren" | `Pulverfass.Display.XLarge`, `gold` |
| Player Avatar | `avatar_xl` (150dp), Border 4dp `gold_bright` |
| Subtitle ("hat die welt erobert") | `Pulverfass.Body`, `gold_muted` |
| Stats (Runden/Eroberungen/Truppen) | `Pulverfass.Body.Small` |
| Trenner-Bullets | `gold_muted` Punkte |
| NEUES SPIEL Button | `Pulverfass.Button.Primary` |
| HAUPTMENÜ Button | `Pulverfass.Button.Secondary` |
| Particle-Effekte | gold sparkles, 0.5-1s loop |

### Einstellungen (`67:3` im Figma)
| Element | Token |
|---|---|
| Background | Library-Image + parchment overlay |
| "Einstellungen" Title | `Pulverfass.Display.Medium` |
| "← Zurück" | `Pulverfass.Body`, `text_primary` |
| Setting-Labels (Musik, Soundeffekte, etc.) | `Pulverfass.Body.Large` |
| Slider Track | `surface_card`, Höhe 6dp |
| Slider Fill | `gold` |
| Slider Thumb | 20dp Kreis, `gold_bright` |
| Toggle Switch | track `surface_card`, thumb `gold` (on) / `text_muted` (off) |
| Sprache-Dropdown | Style `Pulverfass.Input` aber kompakter (Höhe 37dp) |

### Loading Screen (`28:16` / `28:22` im Figma)
| Element | Token |
|---|---|
| Background | Candle-Image + `surface_overlay` 40% |
| Loading Bar Track | `surface_card`, `loading_bar_height` (16dp), Radius `radius_sm` |
| Loading Bar Fill | `gold` mit shimmer-Animation |
| Loading Text ("8% - Schartnerbomb building...") | `Pulverfass.Body`, `text_gold` |

### Confirm Modal (`73:58` im Figma)
| Element | Token |
|---|---|
| Backdrop | `surface_overlay` (80% schwarz) |
| Modal Container | BG `parchment` mit Pergament-Textur, Radius `radius_lg`, Padding `modal_padding` |
| "Achtung" Title | `Pulverfass.Display.Medium`, `text_on_parchment` |
| Body-Text | `Pulverfass.Body.Large`, `text_on_parchment` |
| "JA !!" Button | `Pulverfass.Button.Danger` |
| "NEIN !!" Button | `Pulverfass.Button.Secondary` |

### Menu (`28:30` im Figma)
| Element | Token |
|---|---|
| Background | Game-Promo-Image + Vignette |
| "Menü" Title | `Pulverfass.Display.XLarge` |
| play / options / EXIT | `Pulverfass.Button.Primary` (large variant, full-width auf mobile) |

## Asset-Pipeline Notes

Die AI-generated Backgrounds (Gemini, Leonardo, Flux) sind sehr großformatig.
Empfehlung pro Screen:

- Reference (orig): 1820×1000 oder 1920×1080
- Export für `drawable-xxxhdpi`: 1080×600 (4:3 oder 16:9)
- Export für `drawable-xhdpi`: 720×400
- Format: `.webp` lossy 85% Qualität (massive Speicher-Ersparnis ggü. PNG)

Backgrounds **immer** mit dezenter Vignette + 30% Dunkel-Overlay rendern für UI-Lesbarkeit.

## Open Questions für die Devs

1. Compose vs. XML-Layouts? → Tokens sind kompatibel mit beiden, aber wir empfehlen Compose komplett.
2. Welche `minSdk` haben wir? → Beeinflusst ob wir `Color(...)` Long-Hex-Format brauchen.
3. Localization: Strings sind bisher hardcoded im Figma — sollten ALLE in `strings.xml` rein.
