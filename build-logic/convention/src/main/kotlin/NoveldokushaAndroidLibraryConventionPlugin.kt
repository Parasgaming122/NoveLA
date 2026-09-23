import com.android.build.api.dsl.LibraryExtension
import my.noveldokusha.convention.plugin.appConfig
import my.noveldokusha.convention.plugin.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class NoveldokushaAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                resourcePrefix = path
                    .split("""\W""".toRegex()).drop(1).distinct()
                    .joinToString(separator = "_")
                    .lowercase() + "_"

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }
            }
        }
    }
}
