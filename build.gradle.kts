// Корневой проект — контейнер модулей. Общая конфигурация подмодулей задаётся здесь,
// специфика (зависимости, сборка jar, plugin.yml) — в build.gradle.kts каждого модуля.

subprojects {
    apply(plugin = "java")

    group = "ru.kiviuly.mg"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
    }
}
