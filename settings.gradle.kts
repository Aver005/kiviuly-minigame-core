plugins {
    // авто-загрузка нужного JDK (Java 25) при отсутствии в системе
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kiviuly-mg"

include("mg-api", "mg-core")
