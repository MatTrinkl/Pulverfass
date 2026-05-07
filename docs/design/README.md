# Pulverfass · Art Bible Package

> Sprint 2 Deliverable für Issues #356, #357 (teilweise), #358.
> Komplette Design-Foundation, vom Konzept bis zu Production-Ready Android Resources.

## 📁 Was ist drin

```
docs/design/
├── README.md                       ← du bist hier
├── ART_BIBLE.md                    ← Kern-Dokument (Issue #356)
├── DEV-HANDOFF.md                  ← Screen-by-Screen Token Mapping (Issue #357)
├── art-bible-preview.html          ← Visueller Showcase (öffne im Browser!)
└── android-tokens/                 ← Android Resource Files (Issue #358)
    ├── colors.xml
    ├── dimens.xml
    ├── type.xml
    └── themes.xml
```

## 🎯 Wer was lesen sollte

| Person | Lies das |
|---|---|
| **Du (Designer)** | `ART_BIBLE.md` — das ist deine Source of Truth |
| **Devs** | `DEV-HANDOFF.md` + `android-tokens/` — alles was sie brauchen |
| **Team / Stakeholder** | `art-bible-preview.html` — sieht aus wie was, in 2 Minuten |
| **Neue Mitglieder** | Alles, in der Reihenfolge oben |

## 🚀 Quick Start für die Devs

1. Kopiere `android-tokens/*.xml` nach `app/src/main/res/values/`
2. Lade die Fonts von Google Fonts und leg sie in `app/src/main/res/font/`:
   - Cinzel Decorative
   - Cormorant Garamond
   - Philosopher
   - Fleur De Leah
3. Setze in `AndroidManifest.xml`: `android:theme="@style/Theme.Pulverfass"`
4. Lese `DEV-HANDOFF.md` für Screen-by-Screen Mapping

## 📋 Issue-Status (Sprint 2)

- ✅ **#356 Art Bible** → Done (`ART_BIBLE.md`)
- 🟡 **#357 Figma → Android** → Specs & Tokens fertig (`DEV-HANDOFF.md`), Implementation liegt bei Devs
- ✅ **#358 Design Tokens** → Done (`android-tokens/`)

## 🎨 Visual Preview öffnen

```bash
# Lokal im Browser anschauen
open art-bible-preview.html
# oder
xdg-open art-bible-preview.html
```

Tipp: das HTML kannst du auch ins Wiki packen oder als GitHub Pages deployen — dann hat das ganze Team eine immer-aktuelle Live-Referenz.

## 🔄 Wartung

Wenn du im Figma-Master Änderungen machst die das Design-System betreffen:

1. Update `ART_BIBLE.md` (Source of Truth)
2. Sync Tokens in `android-tokens/*.xml`
3. Update `art-bible-preview.html` (das visuelle Pendant)
4. Bump Version in der Art Bible Version-Tabelle

## 💡 Open TODOs (für Sprint 3+)

Alle in der Art Bible §10 dokumentiert. Top 3:

- [ ] Onboarding/Tutorial-UI komplett designen
- [ ] Empty / Error / Loading States
- [ ] Vollständiges Icon-Set (ca. 20 Icons fehlen)

---

**Maintained by:** Martin · Sprint 2 · Mai 2026
