import org.gradle.kotlin.dsl.from

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.gradle.ktlint) version(libs.versions.ktlint) apply false
}

tasks.register<Copy>("installGitHook") {
    from(File(rootProject.rootDir, "git-hooks/pre-commit"))
    from(File(rootProject.rootDir, "git-hooks/pre-push"))
    into(File(rootProject.rootDir, ".git/hooks"))
    doFirst {
        filePermissions {
            unix(0b111_111_111)
        }
    }
}

project(":app") {
    tasks.matching { task -> task.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("installGitHook"))
    }
}
