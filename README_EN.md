<div align="center">

# legadoInk

**An Open-Source Android Reader Built for E-Ink**

Focused on an immersive reading experience on e-paper devices: customizable book sources,
multi-format local reading, deep typography control, and read-aloud support.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2021%2B-brightgreen.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple.svg)](https://kotlinlang.org)

[简体中文](README.md) | **English**

</div>

---

## ⚠️ Read This First: This Repository Contains No Content

**This is a pure reader client source repository.**

| **Not included** | Notes |
|---|---|
| Book sources / feeds | No built-in, recommended, or supplied rules pointing to third-party sites |
| Book content | No novels, comics, or any copyrighted works |
| Third-party online service configs | No built-in TTS endpoints, dictionary rules, or direct-upload configs |
| Font files | Not bundled for licensing reasons — see [Fonts](#4-fonts) |
| Personal data | No reading history, bookshelf, accounts, or keys |

The software provides no content of its own; all content must be added by the user.
Users are solely responsible for complying with applicable laws when obtaining or
distributing copyrighted material. Please respect the rights of content creators.

---

## 1. Highlights

### 🖥️ Deep E-Ink Optimization

Designed systematically around how e-paper displays render. All switches live in app settings:

- **Dedicated E-Ink UI theme**: `EInk` is a fifth theme mode alongside light and dark, enabled by default.
  The interface uses flat, solid colors, avoiding gradients and shadows that e-paper renders poorly
- **Zero animations**: in E-Ink mode all UI animation durations are set to 0, eliminating ghosting and smearing
- **Independent reading background**: the reader has its own E-Ink background config (pure white `#FFFFFF`
  by default), separate from day/night backgrounds, maximizing contrast and refresh speed when page-turning
- **Auto-paging adaptation**: auto-scroll switches to a full-page refresh strategy in E-Ink mode — no gradient scrolling
- **Manga binarization**: the built-in manga reader offers E-Ink-specific processing with an adjustable
  binarization threshold, converting grayscale images into high-contrast output suited to e-paper
- **Focused on reading**: capabilities meaningless on e-paper (e.g. video playback) are omitted,
  reducing size and power consumption

### 📖 Powerful Book Source Rule Engine

Content acquisition is entirely user-defined — rules describe *how to search, how to fetch the TOC, how to extract chapter text*:

- Supports **JSONPath / CSS selectors / XPath / JS** syntax, mixable within one rule
- Embedded **Rhino JavaScript engine**, so rules can run JS for decryption, API calls, and other complex logic
- Sources can be imported, exported, shared, and subscribed — fully under user control

### 📚 Multi-Format Local Reading

Open local files without any network connection:
**TXT · EPUB · MOBI · AZW3 · AZW · UMD · PDF** (automatic encoding detection and TOC generation)

### 🔊 Flexible Read-Aloud

- System TTS engines speak directly
- Custom **HTTP TTS engines**: connect any online speech-synthesis service via rules,
  with different engines configurable per language

### 🎨 Deep Typography & Theme Customization

Font, size, weight, line spacing, paragraph spacing, indentation, margins, page-turn mode, brightness,
tap zones, Simplified/Traditional conversion, and content cleanup (import third-party replacement rules);
bookshelf, theme color, and reading background are all configurable.

### ☁️ Backup & Companion Tools

- **WebDAV cloud backup**: one-tap backup/restore of bookshelf, reading progress, and settings;
  compatible with standard WebDAV services
- **Built-in web service**: manage your bookshelf, push books, and edit sources from a browser
  when the phone and computer share a network

### 📡 Feeds & Manga

RSS feed aggregation, plus a built-in manga reader (including the E-Ink optimizations above).

---

## 2. Getting Started

### Requirements

| Item | Version |
|---|---|
| JDK | 17 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.10 |
| Gradle | 8.14.4 (bundled wrapper — no manual install) |
| compileSdk | 36 (minSdk 21) |

### Build

```bash
# 1. Set your local SDK path (this file is git-ignored)
echo "sdk.dir=/path/to/your/android-sdk" > local.properties

# 2. (Optional) Fetch open-source fonts — see "4. Fonts"
./scripts/download-fonts.sh

# 3. Build the release APK
./gradlew assembleAppRelease
```

Output: `app/build/outputs/apk/app/release/`.

> **Versioning**: `versionName` is defined in `app/build.gradle` (currently `1.0.0`);
> `versionCode = 10000 + git commit count`. Bump the version manually when releasing.

### Signing

This repository **contains no signing keys**. Release builds read signing config from Gradle properties —
generate your own keystore:

```bash
keytool -genkey -v -keystore your-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-alias
```

Configure it in `~/.gradle/gradle.properties` (names match the properties in `app/build.gradle`):

```properties
RELEASE_STORE_FILE=/absolute/path/to/your-key.jks
RELEASE_KEY_ALIAS=your-alias
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_PASSWORD=your_key_password
```

> ⚠️ The signing key determines whether users can upgrade in place. **It cannot be changed after release** —
> keep an offsite backup and never hardcode or commit passwords.

## 3. Project Structure

```
.
├── app/                     Main application module
│   └── src/main/
│       ├── assets/
│       │   ├── defaultData/ Default app config (themes, typography, base rules)
│       │   ├── fonts/       Reading fonts (empty — supply your own)
│       │   └── web/         Built-in web service and help docs
│       └── java/            Kotlin sources
├── modules/
│   ├── book/                Book parsing module (EPUB, etc.)
│   ├── rhino/               JavaScript engine module
│   └── web/                 Companion web frontend (standalone Vue project, not part of the Gradle build)
├── scripts/
│   └── download-fonts.sh    Open-source font download script
├── NOTICE                   Third-party copyright and license notices
└── LICENSE                  GPL-3.0 full text
```

## 4. Fonts

For licensing reasons, **no font files are bundled**:

- **LXGW WenKai (霞鹜文楷)**: licensed under SIL OFL 1.1 and can be fetched by script for building:
  ```bash
  ./scripts/download-fonts.sh
  ```
- **Proprietary fonts** (e.g. commercial Song/Ming typefaces with all rights reserved): may not be
  redistributed with an open-source project. Obtain them yourself and use "Import font" inside the app

You may also drop a licensed `.ttf` into `app/src/main/assets/fonts/` to bundle it.
See [`app/src/main/assets/fonts/README.md`](app/src/main/assets/fonts/README.md).

## 5. License

This project is licensed under the **GNU General Public License v3.0** — full terms in [LICENSE](LICENSE).

- Third-party library and asset attributions are listed in [NOTICE](NOTICE)
- Font licensing is the user's own responsibility; this project makes no guarantees about any font's license status

## 6. Acknowledgements

This project has learned from and benefited from the following excellent open-source projects:

- [gedoor/legado](https://github.com/gedoor/legado)
- [阅读 Sigma (legado-E)](https://gitee.com/lyc486/legado)

Thanks to all upstream contributors.

legadoInk is a **reader tool** — it does not provide, bundle, or recommend any book source or content site.
