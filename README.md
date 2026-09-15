# 💸 Expense Tracker

A modern, offline-first personal finance and expense tracking Android application built with **Jetpack Compose**, **Kotlin Coroutines**, **Room Database**, and **Material 3**.

Designed with an emerald-accented modern aesthetic, dynamic typography scaling, custom accent color theming, and an intuitive, fast transaction entry experience.

---

## ✨ Highlights & Features

### 🌟 Expressive Transaction Entry
- **Hero Amount Display**: Dynamic hero card featuring radial ambient lighting, currency pill selector, and smooth pulsing cursor.
- **Quick Increments**: Speed up manual logging with instant `+$5`, `+$10`, `+$50`, and `Round up` helper chips.
- **Categorized & Tagged**: Select categories with distinct visual icons and colors; add merchants and receipts easily.
- **Segmented Type Switcher**: Seamlessly toggle between **Expense** and **Income**.
- **Save & Add Another**: Quickly log multiple receipts in sequence without leaving the screen.

### 🎨 Theming & Typography
- **Curated Emerald Palette**: Modern dark & light modes inspired by nature and clean fintech interfaces.
- **Custom Accent Colors**: Pick your own accent color with real-time harmonic palette generation across primary, container, and surface tones.
- **Dynamic Text Sizing**: Adjust text scale inside Settings (**Small 0.85x**, **Default 1.0x**, **Large 1.15x**, **Extra Large 1.3x**) dynamically scaling all app typography tokens.
- **Edge-to-Edge Design**: System status and navigation bar contrast automatically adapts to current light/dark modes.

### 💳 Accounts & Balances
- Dedicated account management for **Cash**, **Credit Cards**, and digital payment methods.
- Real-time balance calculations with isolated account ledgers.

### 🔒 Privacy & Offline-First
- **100% Local**: All financial data stays strictly on your device.
- Powered by Android's **Room SQLite Database** and **Jetpack DataStore Preferences**.
- Instant loading and zero network latency.

---

## 🏗️ Architecture & Tech Stack

This project follows **Android Clean Architecture** principles separated into feature and core modules:

```
expense/
├── app/                  # Application initialization, navigation host, theme state
├── core/
│   ├── common/           # Shared utility extensions and base contracts
│   ├── data/             # Repositories & data source implementations
│   ├── database/         # Room DB, DAOs, and entities
│   ├── designsystem/     # Material 3 theme, emerald color schemes, scaled typography
│   ├── model/            # Immutable domain models (Transaction, Account, Category, Money)
│   ├── navigation/       # Type-safe navigation routes and tab definitions
│   └── preferences/      # DataStore preferences repository (Theme, Text Size, Currency)
└── feature/
    ├── accounts/         # Payment accounts & wallets management
    ├── addtransaction/   # Fast transaction creation screen with quick amount actions
    ├── home/             # Dashboard with financial overview and quick actions
    ├── settings/         # App preferences: appearance, theme mode, text sizing
    ├── statistics/       # Spending breakdowns and analytics
    └── transactions/     # Transaction history and filters
```

### Libraries & Tools
- **UI**: Jetpack Compose, Material 3, Compose Navigation
- **Architecture**: MVI / MVVM, Kotlin Flow, StateFlow, Coroutines
- **Dependency Injection**: Dagger Hilt
- **Storage**: Room Database (SQLite), AndroidX DataStore Preferences
- **Testing**: JUnit 4, Kotlinx Coroutines Test, Fake Repositories

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio Ladybug (or newer)**
- **JDK 17 or JDK 21**
- Android SDK 35/36 (Min SDK: Android 8.0, API 26)

### Build and Run
Clone the repository and build the debug APK:

```bash
git clone https://github.com/Pranjalbudaniya/expense-tracker.git
cd expense-tracker
./gradlew assembleDebug
```

To install directly to a connected Android device:

```bash
./gradlew installDebug
# Or via adb:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Running Tests
Execute unit tests across modules:

```bash
./gradlew testDebugUnitTest
```

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
