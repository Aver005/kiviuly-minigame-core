# QUICKSTART
> Agent: read this first. ~15s. Last updated: 2026-07-24

## Что это

**Kiviuly minigame platform** — общее ядро для мини-игр Minecraft (Paper). Три
мини-игры держали три копии одного каркаса; каркас вынесен в платформу, игры
становятся тонкими плагинами.

## Четыре репозитория (работа идёт во всех)

| Путь | Что | Статус |
|---|---|---|
| `E:\Projects\Me\kiviuly-mg-core` | **платформа** (этот репо): `mg-api` + `mg-core` | рабочая |
| `E:\Projects\Me\skywars-reborn` | игра SkyWars | ✅ переехала |
| `E:\Projects\Me\skyblockwars-reborn` | игра SkyBlockWars | ✅ переехала |
| `E:\Projects\Me\escape-reborn` | игра Escape | ⏳ **переезд не закончен** |

## Как собрать

```bash
cd E:/Projects/Me/kiviuly-mg-core && ./gradlew build     # платформа
cd E:/Projects/Me/skywars-reborn  && ./gradlew build     # и так для каждой игры
```

Игры тянут `mg-api` из соседнего репо через composite build
(`includeBuild("../kiviuly-mg-core")` в их `settings.gradle.kts`) — публиковать
ничего не нужно. Java 25, Paper 26.1.2, Gradle 9.6.1 (wrapper в репо).
Деплой на тестовый сервер: `./gradlew deploy` (каталог из `.env`, ключ `DEPLOY_DIR`).

## Горячие файлы платформы

| Файл | Роль |
|---|---|
| `mg-api/.../api/MgCore.java` | фасад: `register(Minigame)`, `arenas()`, `stats()`, `commandFor(game)` |
| `mg-api/.../api/game/Minigame.java` | точка расширения: все хуки игры |
| `mg-api/.../api/game/Match.java` | контракт матча, который видит игра |
| `mg-api/.../api/arena/Arena.java` | конфиг арены + «карманы» настроек и точек |
| `mg-core/.../mg/game/GameSession.java` | движок матча (реализует `Match`) |
| `mg-core/.../mg/arena/ArenaManager.java` | реестр арен, раскладка по папкам игр |
| `mg-core/.../mg/command/MinigameCommand.java` | команда: платформенная и привязанная к игре |
| `mg-core/.../mg/MgCorePlugin.java` | bootstrap, реестр игр, публикация в ServicesManager |

## Как игра подключается

```java
MgCore core = getServer().getServicesManager().load(MgCore.class);  // plugin.yml: depend: [MgCore]
Msg.merge(this);                       // НЕ init — каталог общий
core.register(new MyGame(this, core));
var h = core.commandFor(game); cmd.setExecutor(h); cmd.setTabCompleter(h);
```

## Дальше

`STATE.md` — текущий фронт работ (переезд Escape). `PLANS.md` — что после.
Перед правкой кода обязательно `CONVENTIONS.md` и `LEARNINGS.md`: там инварианты и
грабли, на которые уже наступали.
