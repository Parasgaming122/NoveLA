# =============================================================================
# БАЗОВЫЕ ПРАВИЛА ANDROID & LIFECYCLE
# =============================================================================

-keep public class * extends androidx.lifecycle.ViewModel { *; }

# Минимальный набор: нужен для @Serializable и деобфускации крашей.
# SourceFile / EnclosingMethod убраны — на работу не влияют, только вес.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, Signature, LineNumberTable

# =============================================================================
# ИСПРАВЛЕНИЕ ОШИБОК СБОРКИ (R8 / MISSING CLASSES)
# =============================================================================

-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.apache.bcel.**
-dontwarn org.jspecify.**
-dontwarn org.yaml.snakeyaml.**

# =============================================================================
# LUA И СИСТЕМА ПЛАГИНОВ (LuaJ) — грузится динамически, держим целиком
# =============================================================================

-keep class org.luaj.vm2.** { *; }

-keep class my.noveldokusha.scraper.LuaEngine$* { *; }
-keep class my.noveldokusha.scraper.LuaSourceAdapter { *; }
-keep class my.noveldokusha.scraper.LuaSourceAdapterConfigurable { *; }
-keep interface my.noveldokusha.scraper.SourceInterface** { *; }

# =============================================================================
# МОДЕЛИ ДАННЫХ — цели Gson/SnakeYAML-рефлексии (имена полей должны сохраниться)
# =============================================================================

-keep class my.noveldokusha.scraper.domain.** { *; }
-keep class my.noveldokusha.scraper.configs.** { *; }

# core сериализуется через kotlinx.serialization (покрыто правилами ниже),
# тотальный keep всего пакета убран — иначе R8 не сожмёт/не обфусцирует core.

# =============================================================================
# СТОРОННИЕ БИБЛИОТЕКИ
# =============================================================================

# Jsoup — статический API, рефлексии нет: держать весь jsoup не нужно.
-dontwarn org.jsoup.**

# Gson / SnakeYAML — рефлективны, оставлены целиком (консервативно).
# Кандидаты на сужение после smoke-теста релизной сборки.
-keep class com.google.gson.** { *; }
-keep class org.yaml.snakeyaml.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# =============================================================================
# KOTLIN SERIALIZATION
# =============================================================================

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# =============================================================================
# ЛОГИРОВАНИЕ — вырезаем debug/verbose/info из релизного dex
# =============================================================================

-dontwarn org.slf4j.impl.StaticLoggerBinder

-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}

-assumenosideeffects class timber.log.Timber* {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-keep class my.noveldokusha.logging.FileTree { *; }

# =============================================================================
# TRANSLATION MANAGERS — регистрируются по имени/рефлексией
# =============================================================================

-keep class my.noveldokusha.text_translator.TranslationManagerComposite { *; }
-keep class my.noveldokusha.text_translator.TranslationManagerGemini { *; }
-keep class my.noveldokusha.text_translator.TranslationManagerGoogleFree { *; }