// mg-core — самостоятельный Paper-плагин: движок арен/матчей платформы. Ставится на
// каждый игровой сервер. На Шаге 1 несёт заглушку TemplateGame и работает автономно.

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    implementation(project(":mg-api")) // связь модулей; активно используется с Шага 2
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("MgCore")
    // mg-api не отдельный плагин — вкладываем его классы в jar ядра, иначе рантайм
    // не найдёт ru.kiviuly.mg.api.*. Игровые плагины (depend: MgCore) видят их через
    // classloader ядра.
    dependsOn(":mg-api:jar")
    from({ zipTree(project(":mg-api").tasks.named<Jar>("jar").get().archiveFile) })
}

// Читает DEPLOY_DIR из корневого .env (KEY=VALUE, не в гите). null, если нет.
fun deployDirFromEnv(): String? {
    val env = rootProject.file(".env")
    if (!env.exists()) return null
    for (raw in env.readLines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue
        if (line.substringBefore("=").trim() == "DEPLOY_DIR") {
            return line.substringAfter("=").trim().trim('"', '\'')
        }
    }
    return null
}

// Сборка + копирование jar в тестовый сервер. Каталог по приоритету:
//   1) -PdeployDir=<путь>   2) DEPLOY_DIR из .env   3) build/deploy (дефолт)
tasks.register<Copy>("deploy") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    val target = (project.findProperty("deployDir") as String?) ?: deployDirFromEnv() ?: "build/deploy"
    into(target)
    doNotTrackState("deploy target may contain files locked by a running server")
}
