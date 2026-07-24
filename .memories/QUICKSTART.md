# QUICKSTART
> Agent: read this first. ~15s. Last updated: 2026-07-24

## Что это

**Kiviuly minigame platform** — общее ядро для мини-игр Minecraft (Paper). Три
мини-игры держали три копии одного каркаса; каркас вынесен в платформу, игры
становятся тонкими плагинами.

## Четыре репозитория (работа идёт во всех)

| Путь | Что | Статус |
|---|---|---|
| `c:\Work\Minecraft\kiviuly-mg-core` | **платформа** (этот репо): `mg-api` + `mg-core`, ветка `develop` | рабочая |
| `c:\Work\Minecraft\skywars-reborn` | игра SkyWars | ✅ переехала |
| `c:\Work\Minecraft\skyblockwars-reborn` | игра SkyBlockWars | ✅ переехала |
| `c:\Work\Minecraft\escape-reborn` | игра Escape | ⏳ **кусок 4 в осн. закрыт** |

> **Ветки:** миграция каждой ИГРЫ — на ветке `refactor/Migrate-to-MG-core` (не в `main`).
> Платформа — на `develop` (своей `refactor/*` нет). Тестовый сервер: `C:\Servers\escape-server`.

## Как собрать

```bash
cd /c/Work/Minecraft/kiviuly-mg-core && ./gradlew build   # платформа
cd /c/Work/Minecraft/escape-reborn   && ./gradlew build   # игра (на ветке refactor/Migrate-to-MG-core)
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
