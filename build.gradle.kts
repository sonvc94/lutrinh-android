plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * Build output lives outside the checkout, on the external disk (captain 2026-11-10):
 * `<buildRoot>/<checkout dir name>/<module>` (root project: `.../root`), so parallel worktrees never
 * share a build dir. `buildRoot` = `-Plutrinh.buildRoot=…`, else env `LUTRINH_BUILD_ROOT`, else
 * `/Volumes/wdblack/tmp/lutrinh-build`. If the volume is not mounted (its parent dir is missing)
 * or the value is empty, Gradle's default `build/` inside the checkout is kept.
 * `./gradlew -q printBuildDir` prints the resolved dir.
 */
val buildRootSetting: String = (findProperty("lutrinh.buildRoot") as String?)
    ?: System.getenv("LUTRINH_BUILD_ROOT")
    ?: "/Volumes/wdblack/tmp/lutrinh-build"
val checkoutBuildDir: File? = buildRootSetting.takeIf { it.isNotBlank() }
    ?.let { File(it) }
    ?.takeIf { it.parentFile?.isDirectory == true }
    ?.resolve(rootDir.name)

if (checkoutBuildDir != null) {
    allprojects {
        val moduleDir = if (this == rootProject) "root" else path.removePrefix(":").replace(':', '/')
        layout.buildDirectory.set(checkoutBuildDir.resolve(moduleDir))
    }
}

tasks.register("printBuildDir") {
    val dirs = allprojects.map { "${it.path} -> ${it.layout.buildDirectory.get().asFile.path}" }
    doLast { dirs.forEach(::println) }
}
