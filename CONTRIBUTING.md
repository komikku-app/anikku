Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/komikku-app/anikku#issues-feature-requests-and-contributing).

---

Thanks for your interest in contributing to Anikku!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/komikku-app/anikku/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.

## Getting help

- Join [the Discord server](https://discord.gg/85jB7V5AJR) for online help and to ask questions while developing.

# Translations

Translations are done externally via [Weblate](https://hosted.weblate.org/projects/komikku-app/anikku/). See [our website](https://anikku-app.github.io/docs/contribute#translation) for more details.


# Forks

Forks are allowed so long as they abide by [the project's LICENSE](https://github.com/tachiyomiorg/tachiyomi/blob/master/LICENSE).

When creating a fork, remember to:

- To avoid confusion with the main app:
    - Change the app name
    - Change the app icon
    - Change or disable the [app update checker](https://github.com/komikku-app/anikku/blob/master/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
- To avoid installation conflicts:
    - Change the `applicationId` in [`build.gradle.kts`](https://github.com/komikku-app/anikku/blob/master/app/build.gradle.kts)
- To avoid having your data polluting the main app's analytics and crash report services:
    - If you want to use ACRA crash reporting, replace the `ACRA_URI` endpoint in [`build.gradle.kts`](https://github.com/komikku-app/anikku/blob/master/app/build.gradle.kts) with your own
