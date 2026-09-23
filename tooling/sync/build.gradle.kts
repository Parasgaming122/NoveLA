plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "my.noveldokusha.tooling.sync"
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    // Project modules
    implementation(projects.core)
    implementation(projects.coreui)
    implementation(projects.strings)
    implementation(projects.tooling.localDatabase)

    // AndroidX
    implementation(libs.androidx.workmanager)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.workmanager)
    ksp(libs.hilt.androidx.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Serialization — required by Supabase Kotlin SDK
    implementation(libs.kotlinx.serialization.json)

    // Supabase + Ktor engine (OkHttp shares the app's existing OkHttp stack)
    implementation(libs.supabase.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.ktor.client.okhttp)

    // Logging
    implementation(libs.timber)

    // Test
    testImplementation(libs.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
}
