// mg-api — чистые контракты платформы (интерфейсы/DTO), против которых компилируются
// игры и хаб. Реализация — в mg-core/mg-hub. Содержит: Minigame (SPI), Match, Arena,
// MatchPlayer, MatchResult, GamePhase, MgCore, ArenaService, StatsService.

dependencies {
    // Контракты ссылаются на Bukkit-типы (Player, Location, Component) — только compile-time.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}
