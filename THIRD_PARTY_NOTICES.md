# Third-party components

- RE2/J 1.8, https://github.com/google/re2j — linear-time notification regex filtering. Upstream Go/BSD license and additional notices are bundled verbatim as `licenses/RE2J-LICENSE.txt`. DayFlow original source remains MIT.

DayFlow's original source code is licensed under MIT. Dependency licenses remain applicable to those dependencies.

Android 1.2.0 core and optional plugins use the following components; Markdown/Markwon/JLaTeXMath are distributed in the optional Markdown APK, not the core APK:

- Markwon ext-latex and inline-parser 4.6.2, Apache License 2.0, https://github.com/noties/Markwon — native Markdown formula integration.
- JLaTeXMath Android 0.2.0, upstream tag `v0.2.0`, commit `cc2a83dcf695028b5588f91a90734d94eb5bd02b`, https://github.com/noties/jlatexmath-android — GNU GPL v2 or later with the upstream special linking exception for independent modules. This component retains its own license; DayFlow's original independent source remains MIT. The unmodified corresponding upstream source, including build files, fonts and licenses, is distributed alongside the APK as `MathRenderer-source.zip`. GPL text, linking exception and font licenses are included in the APK under `licenses/jlatexmath/`. The fonts retain their original OFL, Knuth, free/public-domain or GPL terms as described in that directory and the corresponding source archive.

- MarkdownEditText 1.1.3, Copyright 2020 YahiaAngelo, Apache License 2.0. https://github.com/YahiaAngelo/MarkdownEditText — embedded native rich Markdown editor and styles bar. DayFlow adapts the styles-bar item layout for theme colors and larger touch targets. License bundled as `licenses/MarkdownEditText-Apache-2.0.txt`.

- Calendar View was used by earlier versions and removed from Android 1.2.0. The historical MIT notice remains for older releases; the current month grid is original DayFlow code.
- lunar-java 1.7.7, Copyright (c) 2018 6tail, MIT License. https://github.com/6tail/lunar-java — offline lunar dates, festivals and solar terms. License bundled as `licenses/lunar-java-MIT.txt`.
- Markwon 4.6.2, Copyright (c) 2017 Dimitry Ivanov, Apache License 2.0. https://github.com/noties/Markwon — native TextView Markdown rendering. It is not MIT licensed.
- commonmark-java 0.13.0 (transitive dependency of Markwon), Copyright (c) 2015-2016 Atlassian Pty Ltd, BSD 2-Clause License. https://github.com/commonmark/commonmark-java — Markdown parsing. License bundled as `licenses/commonmark-BSD.txt`.

- Material Components for Android, Copyright The Android Open Source Project, Apache License 2.0. Source and license: https://github.com/material-components/material-components-android
- AndroidX AppCompat, Activity, RecyclerView, Core and transitive AndroidX libraries, Copyright The Android Open Source Project, Apache License 2.0. Source and license: https://android.googlesource.com/platform/frameworks/support/
- Kotlin standard library where required by these libraries, Copyright JetBrains s.r.o. and Kotlin Programming Language contributors, Apache License 2.0. Source and license: https://github.com/JetBrains/kotlin

The Apache License 2.0 text is included in the Android application's assets at `licenses/Apache-2.0.txt`.

Weather is supplied by Open-Meteo and is attributed in the weather page. Users supply their own AI services and API credentials.

## TaskFlow protocol adapter (Android 1.0.0)

The adapter and schema mapping are original DayFlow MIT code. It reads the public protocol of the user-supplied TaskFlow source; no TaskFlow code is redistributed.

- Yjs 13.6.33 — MIT, Copyright Kevin Jahns: https://github.com/yjs/yjs
- y-protocols 1.0.7 — MIT, Copyright Kevin Jahns: https://github.com/yjs/y-protocols
- lib0 0.2.119 — MIT, Copyright Kevin Jahns: https://github.com/dmonad/lib0
- AndroidX JavaScriptEngine 1.1.0 — Apache-2.0, Android Open Source Project.
- OkHttp 4.12.0 and Okio (transitive) — Apache-2.0, Square / their respective contributors.

Yjs library license texts are included under assets/licenses/yjs. Apache-2.0 text is included in the existing assets/licenses/Apache-2.0.txt. esbuild is a build-only MIT dependency. The sandbox evaluates the bundled local protocol implementation; no remotely supplied executable code is evaluated.


## Android 1.2.0 additions

- jsoup 1.21.2 — MIT, Jonathan Hedley and contributors, https://github.com/jhy/jsoup ; static HTML parsing, notice assets/licenses/jsoup-MIT.txt.
- biweekly 0.6.8 — BSD 2-Clause, Michael Angstadt and contributors, https://github.com/mangstadt/biweekly ; iCalendar parsing. Transitive vinnie (same author) is BSD 2-Clause; Jackson core is Apache-2.0. The BSD notice is bundled as licenses/biweekly-BSD.txt.
- Android Image Cropper 4.7.0 — Apache-2.0, CanHub contributors, based on ArthurHub / Edmodo Android Image Cropper, https://github.com/CanHub/Android-Image-Cropper . Bundled licenses/Image-Cropper-Apache.txt.
- holiday-cn yearly data — MIT, NateScarlet and contributors, https://github.com/NateScarlet/holiday-cn ; 2026 data bundled with its source URLs and licenses/holiday-cn-MIT.txt. Availability of a year does not imply future official holiday announcements.
- Markwon table, image, HTML and linkify modules 4.6.2 use the same Apache-2.0 license as Markwon. CommonMark extensions retain upstream BSD terms. Formula renderer and source obligations above continue to apply.

Optional OCR is a separate APK and process, not included in the core APK:

- RapidOcrAndroidOnnx / OcrLibrary 1.3.0 — Apache-2.0, RapidAI and contributors, https://github.com/RapidAI/RapidOcrAndroidOnnx . Official AAR pinned by tools/fetch_ocr.py, not checked into Git.
- RapidOCR / PaddleOCR model resources retain their Apache-2.0 upstream licenses, https://github.com/RapidAI/RapidOCR and https://github.com/PaddlePaddle/PaddleOCR .
- ONNX Runtime native library — MIT, Microsoft Corporation and contributors, https://github.com/microsoft/onnxruntime .
- OpenCV 4.6 native dependency — Apache-2.0, OpenCV contributors, https://github.com/opencv/opencv .
- Corresponding upstream license texts are bundled under android/ocrPlugin/src/main/assets/licenses/. AndroidX and Material dependencies retain Apache-2.0 as above.

Workflow ideas were informed by Super Productivity (MIT, https://github.com/johannesjo/super-productivity), notably focus sessions, task notes and project boards. DayFlow implements these independently; no Super Productivity source is redistributed.

The almanac APK independently uses lunar-java (MIT); the core retains lunar-java for basic lunar calendar display. The assistant API APK uses AndroidX/Material (Apache-2.0). The Markdown APK retains the full license/source obligations above; MathRenderer-source.zip must accompany its distribution. Plugin APKs may duplicate Android base libraries; users install only the capabilities they need.
