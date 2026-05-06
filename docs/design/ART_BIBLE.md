# Pulverfass — Art Bible

> Single source of truth für die visuelle Sprache von Pulverfass.
> Stand: Sprint 2 · Reverse-engineered aus dem Figma-Master.
> Studio: Gabumon Game Studio

---

## 1. Visual Identity

**Genre-Aufhänger:** Dark Fantasy × Vintage Seafarer × Strategiekarte

Pulverfass spielt in einer atmosphärischen Hafenwelt: Pergament, Messing, Kerzenlicht, Seekarten, Schiffe. Die Optik soll an alte Atlanten und Piratentavernen erinnern — düster, warm, taktil. **Nicht** clean-modern, **nicht** bunt-cartoon, **nicht** sci-fi.

### Kern-Prinzipien

1. **Pergament & Tinte statt Pixel** — Texturen, Grain und warme Töne dominieren.
2. **Gold als Heiligtum** — Goldene Akzente sind reserviert für wichtige UI-Anker (Titel, primäre CTAs, Spieler-Frames). Nie inflationär verwenden.
3. **Tiefe durch Schichten** — Backgrounds tragen Tiefe (Vignetten, Grain, atmosphärische Beleuchtung), nicht flache Volltöne.
4. **Lesbarkeit vor Stimmung** — Atmosphäre darf nie den Spielzustand verschleiern. Wichtige Zahlen und Labels bleiben kontraststark.

---

## 2. Color System

### 2.1 Surfaces (dunkle Untergründe)

| Token | Hex | Verwendung |
|---|---|---|
| `surface_void` | `#0F1419` | Tiefster Hintergrund, Vignetten-Ränder |
| `surface_dark` | `#1A1F2A` | Standard App-Background |
| `surface_wood` | `#2A1F18` | HUD-Bars, Holzelemente, Modal-Overlays |
| `surface_card` | `#1F1A14` | Card-Backgrounds, Buttons (Default) |
| `surface_overlay` | `#000000CC` | Modal-Backdrops (80% schwarz) |

### 2.2 Parchment (warme Mitten)

| Token | Hex | Verwendung |
|---|---|---|
| `parchment_light` | `#D4B896` | Helle Pergament-Bereiche, Input-Backgrounds |
| `parchment` | `#C8A876` | Standard-Pergament (Cards, Schilder) |
| `parchment_dark` | `#A8855A` | Pergament-Schatten, Borders |
| `parchment_edge` | `#6B4D2A` | Verschmolzene Kanten, alte Versiegelungen |

### 2.3 Gold (Akzent — sparsam!)

| Token | Hex | Verwendung |
|---|---|---|
| `gold_bright` | `#E8C268` | Glow, Hover-States, aktive Highlights |
| `gold` | `#D4B45A` | Primary CTA, Titel-Text, "WÜRFELN!" |
| `gold_muted` | `#C4A14B` | Borders, sekundäre Highlights |
| `gold_dark` | `#8B6F2A` | Tiefgehende Goldränder, Embossing |

### 2.4 Player Colors (semantisch)

Acht Spielerfarben für Karten-Regionen, Avatare, Schiffe. Alle so abgestimmt, dass sie auf dunklem Pergament-Untergrund klar trennbar sind.

| Token | Hex | Charakter |
|---|---|---|
| `player_red` | `#A6342B` | Spieler 1 / Angreifer-Default |
| `player_blue` | `#3A5BA0` | Spieler 2 / Verteidiger-Default |
| `player_orange` | `#D17030` | Spieler 3 |
| `player_olive` | `#8C8A2E` | Spieler 4 |
| `player_purple` | `#7A4A8C` | Spieler 5 |
| `player_teal` | `#4A8C8C` | Spieler 6 |
| `player_pink` | `#B8466C` | Spieler 7 |
| `player_green` | `#5A8C3A` | Spieler 8 |

### 2.5 Semantic Colors

| Token | Hex | Verwendung |
|---|---|---|
| `danger` | `#8B2828` | RÜCKZUG, destruktive Aktionen, "RUNDE ENDE" |
| `danger_bright` | `#C44030` | Hover/Pressed-States für danger |
| `success` | `#4A7A4A` | Bestätigungen, gewonnene Gebiete |
| `warning` | `#D17030` | Selektion-Glow, Warnings |
| `info` | `#3A5BA0` | Informational, neutrale Hinweise |

### 2.6 Text

| Token | Hex | Verwendung |
|---|---|---|
| `text_primary` | `#F0E6D2` | Standard-Text auf dunklem Hintergrund |
| `text_on_dark` | `#E8DCC8` | Body Copy auf surface_dark |
| `text_on_parchment` | `#2A1F18` | Text auf Pergament (Inputs etc.) |
| `text_muted` | `#8B7F66` | Sekundär-Text, Captions, Disabled |
| `text_gold` | `#D4B45A` | Akzent-Text, Buttons, Titles |

---

## 3. Typography

### 3.1 Font Stack

| Rolle | Font | Use Case |
|---|---|---|
| **Display** | Cinzel Decorative | Game-Titel, Screen-Headlines (SCHLACHT, GEWONNEN, LOBBY) |
| **Headline** | Cinzel Decorative | Sub-Titles, Phasen-Labels (PHASE: ANGRIFF) |
| **Body** | Cormorant Garamond | Fließtext, Player-Namen, Lobby-Beschreibungen |
| **UI Label** | Philosopher | Button-Labels, Captions, Stats, Mikro-Text |
| **Decorative** | Fleur De Leah | _Nur_ für Zier-Elemente (Lobby-Schild) — sparsam! |

> **Empfehlung:** Drop "Jim Nightshade" aus dem Figma-Style-Guide. Fleur De Leah deckt den Decorative-Bedarf vollständig ab — zwei Decorative-Fonts machen das System unruhig.

### 3.2 Type Scale

| Rolle | Font | Größe (sp) | Weight | Tracking | Use Case |
|---|---|---|---|---|---|
| `display_xl` | Cinzel Decorative | 56 | 700 | 0.02em | "GEWONNEN !!!!" |
| `display_lg` | Cinzel Decorative | 48 | 700 | 0.02em | "SCHLACHT", "LOBBY" |
| `display_md` | Cinzel Decorative | 36 | 600 | 0.01em | "Nächster ZUG" |
| `headline` | Cinzel Decorative | 28 | 600 | 0.05em | "PHASE: ANGRIFF", "RUNDE 3" |
| `title` | Cinzel Decorative | 20 | 600 | 0.05em | Section-Headers |
| `body_lg` | Cormorant Garamond | 18 | 400 | 0 | Primary Body |
| `body` | Cormorant Garamond | 16 | 400 | 0 | Standard Body |
| `body_sm` | Cormorant Garamond | 14 | 400 | 0 | Tertiary |
| `button` | Philosopher | 14 | 700 | 0.1em | Button-Labels (UPPERCASE) |
| `label` | Philosopher | 12 | 600 | 0.08em | UI-Labels, Tags |
| `caption` | Philosopher | 11 | 400 | 0.05em | Stats, Mikro-Text |

### 3.3 Regeln

- Buttons & Tags: **UPPERCASE** mit Letter-Spacing (passt zum Vintage-Look)
- Player-Namen: **Mixed Case** in Cormorant — niemals all caps
- Game-Titel: immer Cinzel mit `0.02em` Tracking — gibt das chiseled Gefühl
- Body-Text NIEMALS unter 14sp (Lesbarkeit auf Mobile)

---

## 4. Spacing & Layout

### 4.1 Spacing Scale (8pt-basiert)

| Token | dp | Use Case |
|---|---|---|
| `space_xs` | 4 | Icon-Padding, dichte Stacks |
| `space_sm` | 8 | Standard-Gap zwischen verwandten Elementen |
| `space_md` | 12 | Component-Padding (Buttons, Chips) |
| `space_base` | 16 | Standard-Padding für Cards, Sections |
| `space_lg` | 24 | Section-Trennung |
| `space_xl` | 32 | Major Section-Trennung |
| `space_2xl` | 48 | Screen-Padding, Hero-Bereiche |
| `space_3xl` | 64 | Top/Bottom Hero-Spacing |

### 4.2 Radius Scale

| Token | dp | Use Case |
|---|---|---|
| `radius_sm` | 4 | Tags, Chips, kleine Inputs |
| `radius_md` | 8 | Buttons, Cards, Inputs |
| `radius_lg` | 16 | Modals, große Cards |
| `radius_xl` | 24 | Hero-Cards, Feature-Container |
| `radius_circle` | 50% | Avatare, Truppen-Indikatoren |

### 4.3 Elevation / Shadows

Wir setzen Tiefe primär über **Vignetten und Inner-Glow**, nicht über harte Material-Shadows.

| Token | Werte | Use Case |
|---|---|---|
| `elevation_subtle` | `0 2dp 4dp #000000 40%` | Hover-State, leichte Cards |
| `elevation_card` | `0 4dp 12dp #000000 60%` | Standard-Card-Tiefe |
| `elevation_modal` | `0 8dp 24dp #000000 80%` | Modale Dialoge, Overlays |
| `glow_gold` | `0 0 16dp #D4B45A 60%` | Selected-State, aktive CTA |
| `glow_player` | `0 0 12dp <player_color> 50%` | Selektierte Region, aktiver Spieler |

---

## 5. Components

### 5.1 Button

Drei Varianten, alle mit Cinzel/Philosopher-Label uppercase:

#### Primary (Gold)
- BG: `gold` mit subtilem vertikalen Gradient zu `gold_muted`
- Border: 2dp `gold_dark`
- Text: `surface_void` (dunkel auf gold)
- States:
  - Default: Wie oben
  - Pressed: BG `gold_dark`, Translation Y +1dp
  - Disabled: Opacity 40%
- Use Case: WÜRFELN!, NEUES SPIEL, primärer CTA pro Screen

#### Secondary (Outlined)
- BG: `surface_card` mit 2dp Border in `gold_muted`
- Text: `gold`
- States:
  - Default: Wie oben
  - Pressed: BG `gold_dark` 20% overlay
  - Active/Selected: Border `gold_bright` mit `glow_gold`
- Use Case: VERSTÄRKEN, ANGRIFF, VERSCHIEBEN, HAUPTMENÜ

#### Danger
- BG: `danger`
- Text: `text_primary`
- States: Hover/Pressed → `danger_bright`
- Use Case: RÜCKZUG, RUNDE ENDE, Reset, Spiel beenden

**Gemeinsame Specs:**
- Höhe: 56dp (primary), 40dp (secondary kompakt)
- Padding horizontal: `space_lg` (24dp)
- Min-Width: 120dp
- Radius: `radius_md` (8dp)

### 5.2 Territory Card (Region auf der Karte)

| State | Visual |
|---|---|
| Eigene Region | Gefüllt mit `player_<color>` 70% Opacity, Border 2dp `player_<color>` |
| Feindliche Region | Gefüllt mit gegnerischem `player_<color>` 70%, Border 2dp gegnerisch |
| Selected | Eigene Farbe + 4dp `gold_bright` Border + `glow_gold` |
| Target (Angriffsziel) | Feindliche Farbe + 4dp `danger_bright` Border + Pulse-Animation |
| Disabled (außer Reichweite) | 30% Opacity, kein Border |

**Truppen-Indikator (eingebettet):**
- Kreis 28×28dp, weißer BG (`#FFFFFF`)
- Border 2dp `surface_void`
- Zahl: Philosopher Bold, 14sp, `text_on_parchment`

### 5.3 Player Avatar

| Variante | Spec |
|---|---|
| Standard | 40×40dp Kreis, 2dp Gold-Border |
| Aktiv (am Zug) | 56×56dp Kreis, 4dp `gold_bright` Border + `glow_gold` |
| Im HUD | 80×80dp Kreis mit ornamentem Gold-Frame |
| Sieg-Screen | 150×150dp Kreis mit doppeltem Frame, full glow |

Player-Initiale (wenn kein Avatar) auf `player_<color>` Hintergrund, Cinzel 24sp `text_primary`.

### 5.4 Input Field

- BG: `parchment` mit subtiler Pergament-Textur
- Border: 2dp `parchment_edge`
- Padding: `space_base` (16dp)
- Höhe: 56dp
- Text: Philosopher 16sp `text_on_parchment`
- Placeholder: 60% Opacity
- Focus: Border `gold` + `glow_gold` subtle

### 5.5 Dice Tile (Kampfwürfel)

- 56×56dp Quadrat
- Radius: `radius_md` (8dp)
- BG: `player_<color>` (rot für Angreifer, blau für Verteidiger)
- Border: 2dp etwas hellere Variante derselben Farbe
- Zahl: Cinzel Bold 32sp `text_primary`, zentriert

### 5.6 Modal / Confirm Dialog

- Background-Overlay: `surface_overlay` (80% schwarz)
- Modal-Container:
  - BG: `parchment` mit Pergament-Textur
  - Border: 2dp `parchment_edge`
  - Radius: `radius_lg` (16dp)
  - `elevation_modal` Shadow
  - Padding: `space_xl` (32dp)
- Title: Cinzel 28sp `text_on_parchment`
- Body: Cormorant 16sp `text_on_parchment`
- Buttons: Primary + Secondary nebeneinander

### 5.7 HUD Bar (Top & Bottom)

- BG: `surface_wood` mit Holz-Textur-Overlay
- Höhe: 56dp (top), 60dp (bottom mit Aktions-Buttons)
- Subtle Inner-Shadow oben für Tiefe
- Bottom-Border: 1dp `gold_dark` (oben angesetzt)

### 5.8 Loading Bar

- Track: `surface_card`, Radius `radius_sm`, Höhe 16dp
- Fill: `gold` mit shimmer-Animation
- Begleittext: Philosopher 14sp `text_gold`, humorvoll ("Schartnerbomb building...")

---

## 6. Iconography

### 6.1 Style

- Strichstärke: konsistent 2dp
- Style: outlined mit subtilen Akzenten — _kein_ flat material
- Größen-Grid: 16 / 20 / 24 / 32 / 48dp
- Farbe: `text_primary` default, `gold` für aktive Icons

### 6.2 Erforderliches Icon-Set (Sprint 3 TODO)

- [ ] Action Icons: Verstärken, Angriff, Verschieben, Karten, Runde Ende
- [ ] Resource Icons: Truppen, Karten, Gebiete
- [ ] Navigation: Zurück, Settings, Lobby verlassen, Schließen (X)
- [ ] Status: Online/Offline, Verbunden/Nicht verbunden, Loading
- [ ] Decorative: Schwerter, Anker, Kompass, Pulverfass-Logo

---

## 7. Motion & Animation

### 7.1 Timing

| Token | Duration | Easing | Use Case |
|---|---|---|---|
| `motion_fast` | 150ms | ease-out | Buttons, Toggles, Tap-Feedback |
| `motion_default` | 250ms | ease-in-out | Standard-Transitions |
| `motion_slow` | 400ms | ease-in-out | Panel-Slides, große State-Wechsel |
| `motion_dramatic` | 800ms | ease-out + bounce | Sieg/Niederlage-Reveals |

### 7.2 Wichtige Animationen (Sprint 3 Specs)

- **Würfel werfen:** 3D-Flip 600ms, Endposition mit subtle bounce
- **Truppen-Verschieben:** Particle-Trail vom Start- zum Zielgebiet, 400ms
- **Region-Selektion:** Glow-Pulse, Loop 1.5s
- **Karten ziehen:** Slide-in von rechts, staggered 100ms zwischen Karten
- **Spielerwechsel-Banner:** Slide-down + Glow, 600ms total
- **Sieg-Reveal:** Staggered Fade-In aller Elemente, Gold-Particles, 1200ms

---

## 8. Backgrounds & Atmosphere

Pulverfass lebt von atmosphärischen Backgrounds. Jeder Major-Screen hat einen eigenen Mood:

| Screen | Mood | Visual |
|---|---|---|
| Menu | Ruhig, einladend | Pirate Tavern, Kerzenlicht, Bücher |
| Lobby | Vorfreude | Pirate Tavern Detail, Schiffsruder |
| Loading | Build-up | Kerzen, dunkle Schatten, langsame Bewegung |
| Spielfeld | Strategisch | Seekarte mit Vignette, Kraken-Silhouetten |
| Kampf | Konflikt | Schiffe, Cannons, dramatische Beleuchtung |
| Rundenübergang | Übergang | Dunkler ornamenter Stuhl, blaues Glow |
| Sieg | Triumph | Goldener Sonnenuntergang, Schiff |
| Verloren | Niederlage | Sturmsee, gedämpft |

### Background-Regeln

- Alle Backgrounds **leicht abgedunkelt** mit `surface_void` 30% Overlay für Lesbarkeit
- **Vignette** an den Rändern (radial gradient zu `surface_void`)
- **Subtiler Grain-Overlay** über alles (5-8% Opacity)
- Backgrounds dürfen niemals so stark sein, dass UI darüber leidet

---

## 9. Do's & Don'ts

### ✅ Do
- Gold sparsam einsetzen — wenn alles glänzt, glänzt nichts
- Konsistente Token-Werte verwenden (keine Magic-Numbers im Code)
- Spielerfarben **immer** über `player_<color>` Tokens
- Texturen und Grain für Tiefe
- Cinzel für Titles, Philosopher für UI-Labels
- Mindestens 4.5:1 Kontrast für Body-Text (WCAG AA)

### ❌ Don't
- Keine reinen Volltöne ohne Textur (außer Buttons)
- Kein Material Shadow Stack (zu modern für unsere Optik)
- Keine Inter / Roboto / SF Pro
- Keine bunte LED-Optik bei Player-Farben
- Keine Cartoon-Charaktere oder helle Farben über 70% Sättigung
- Buttons nie ohne Border (würde clean-modern aussehen)

---

## 10. Open Questions / TODO

- [ ] Onboarding-Tutorial UI fehlt komplett im Figma
- [ ] Empty States (leere Lobby, kein Internet) noch nicht designed
- [ ] Notification-Komponente für Push-Reminders ("Du bist am Zug")
- [ ] Vollständiges Icon-Set finalisieren (siehe §6.2)
- [ ] Skeleton-Loading für Card-Listen
- [ ] Accessibility-Audit: Kontrast aller Player-Farben gegen Karten-Untergrund
- [ ] Dynamic Type Support: wie skaliert Cinzel bei größeren System-Fonts?
- [ ] Landscape-Verhalten auf Phones (aktuell sind Designs 1784×800 = Tablet/Landscape-Phone)

---

## 11. Versioning

| Version | Datum | Änderung |
|---|---|---|
| 1.0 | Sprint 2 | Initiale Art Bible, extrahiert aus Figma-Master |

**Maintained by:** Martin (UI Design)
**Figma Master:** [Pulverfass Figma](https://www.figma.com/design/9RgbU6CQnd1NIbjo8NPp7q/)
