# Third-party components

DayFlow's original source code is licensed under MIT. Dependency licenses remain applicable to those dependencies.

Android 0.5.0 uses:

- Markwon ext-latex and inline-parser 4.6.2, Apache License 2.0, https://github.com/noties/Markwon — native Markdown formula integration.
- JLaTeXMath Android 0.2.0, upstream tag `v0.2.0`, commit `cc2a83dcf695028b5588f91a90734d94eb5bd02b`, https://github.com/noties/jlatexmath-android — GNU GPL v2 or later with the upstream special linking exception for independent modules. This component retains its own license; DayFlow's original independent source remains MIT. The unmodified corresponding upstream source, including build files, fonts and licenses, is distributed alongside the APK as `MathRenderer-source.zip`. GPL text, linking exception and font licenses are included in the APK under `licenses/jlatexmath/`. The fonts retain their original OFL, Knuth, free/public-domain or GPL terms as described in that directory and the corresponding source archive.

- MarkdownEditText 1.1.3, Copyright 2020 YahiaAngelo, Apache License 2.0. https://github.com/YahiaAngelo/MarkdownEditText — embedded native rich Markdown editor and styles bar. DayFlow adapts the styles-bar item layout for theme colors and larger touch targets. License bundled as `licenses/MarkdownEditText-Apache-2.0.txt`.

- Calendar View 2.6.2, Copyright (c) 2019 Kizito Nwose, MIT License. https://github.com/kizitonwose/Calendar — month paging, day binding and recycled calendar cells. License bundled as `licenses/Calendar-MIT.txt`.
- lunar-java 1.7.7, Copyright (c) 2018 6tail, MIT License. https://github.com/6tail/lunar-java — offline lunar dates, festivals and solar terms. License bundled as `licenses/lunar-java-MIT.txt`.
- Markwon 4.6.2, Copyright (c) 2017 Dimitry Ivanov, Apache License 2.0. https://github.com/noties/Markwon — native TextView Markdown rendering. It is not MIT licensed.
- commonmark-java 0.13.0 (transitive dependency of Markwon), Copyright (c) 2015-2016 Atlassian Pty Ltd, BSD 2-Clause License. https://github.com/commonmark/commonmark-java — Markdown parsing. License bundled as `licenses/commonmark-BSD.txt`.

- Material Components for Android, Copyright The Android Open Source Project, Apache License 2.0. Source and license: https://github.com/material-components/material-components-android
- AndroidX AppCompat, Activity, RecyclerView, Core and transitive AndroidX libraries, Copyright The Android Open Source Project, Apache License 2.0. Source and license: https://android.googlesource.com/platform/frameworks/support/
- Kotlin standard library where required by these libraries, Copyright JetBrains s.r.o. and Kotlin Programming Language contributors, Apache License 2.0. Source and license: https://github.com/JetBrains/kotlin

The Apache License 2.0 text is included in the Android application's assets at `licenses/Apache-2.0.txt`.

Weather is supplied by Open-Meteo and is attributed in the weather page. Users supply their own AI services and API credentials.
