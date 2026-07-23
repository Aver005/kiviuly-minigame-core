# 03 — Как сделать игру

Своя игра — это один тонкий плагин, который зависит от `mg-core` в рантайме и
компилируется против `mg-api`. Инфраструктура (арены, лобби, отсчёты, таймер, откат
мира, снапшоты, HUD, статистика, меню) уже готова в core; твоё дело — правила в
наследнике `Minigame`. Package root игры — свой (напр. `ru.kiviuly.skywars`).

## Три шага

**1. Подключи зависимости.** В `build.gradle.kts` игры:

```kotlin
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
    compileOnly(project(":mg-api"))     // либо published-артефакт ru.kiviuly.mg:mg-api
}
```

В `plugin.yml` игры:

```yaml
name: SkyWars
main: ru.kiviuly.skywars.SkyWarsPlugin
depend: [MgCore]        # гарантирует порядок загрузки: core уже включён
api-version: '1.21'
```

**2. Наследуй `Minigame`.** Логика без состояния — хуки получают `Match` и работают
через его API. Состояние матча живёт в `Match`, не в полях класса (матчей может идти
несколько параллельно на разных аренах).

**3. Зарегистрируй игру в `onEnable`** через фасад `MgCore`:

```java
public final class SkyWarsPlugin extends JavaPlugin {
    @Override public void onEnable() {
        MgCore core = getServer().getServicesManager().load(MgCore.class);
        if (core == null) {
            getLogger().severe("MgCore не найден — плагин выключается");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        core.register(new SkyWarsGame(this, core));
    }
}
```

## Каркас класса

```java
package ru.kiviuly.skywars.game;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.game.Match;
import ru.kiviuly.mg.api.game.MatchPlayer;
import ru.kiviuly.mg.api.game.MatchResult;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.game.MinigameDescriptor;

/** SkyWars: острова, сундуки, последний живой побеждает. */
public final class SkyWarsGame extends Minigame {

    private final SkyWarsPlugin plugin;
    private final MgCore core;

    public SkyWarsGame(SkyWarsPlugin plugin, MgCore core) {
        this.plugin = plugin;
        this.core = core;
    }

    @Override
    public MinigameDescriptor descriptor() {
        return new MinigameDescriptor(
            "skywars",                          // стабильный id (по нему идёт статистика)
            Component.text("SkyWars"),
            new ItemStack(Material.ENDER_PEARL), // иконка в селекторе хаба
            2, 12, "arenas", false);
    }

    @Override
    public void giveLoadout(Match m, Player p) {
        p.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
    }

    @Override
    public void onPlayerEliminated(Match m, MatchPlayer mp) {
        core.messages().broadcast(m, "skywars.eliminated",
            "player", mp.name(), "n", m.aliveCount());
    }

    @Override
    public MatchResult checkResult(Match m) {
        return m.defaultResult();   // последний живой — победитель; таймаут — ничья
    }

    @Override
    public void onEnd(Match m, MatchResult result) {
        if (result.hasWinner()) {
            core.messages().broadcast(m, "skywars.win",
                "team", result.winners().get(0).displayName());
        }
    }

    @Override
    public List<Component> scoreboardLines(Match m, Player viewer) {
        return List.of(core.messages().line("skywars.sidebar-alive", "n", m.aliveCount()));
    }
}
```

> Тексты — только ключи `messages.yml`; ни одной строки для игрока в Java. Заведи
> ключи `skywars.*` в `messages.yml` плагина.

## Хуки `Minigame`

Все с разумным дефолтом (no-op, если не указано иное) — переопределяй нужные.

| Хук | Когда зовётся | Дефолт |
|---|---|---|
| `descriptor()` | при регистрации | **абстрактный, обязателен** |
| `onLobbyJoin(m, p)` | игрок вошёл в лобби | no-op |
| `allowLobbyPvp(m)` | можно ли бить в лобби | `false` |
| `assignTeams(m)` | распределение по командам перед стартом | FFA (команда на игрока) |
| `onStart(m)` | матч стартовал (игроки на спавнах, `SURVIVAL`, очищены) | no-op |
| `giveLoadout(m, p)` | на старте, каждому (кит/предметы) | no-op |
| `onTick(m)` | каждую секунду матча | no-op |
| `onLethalDamage(m, p)` | летальный урон; вернуть `true` = выбыл | `true` |
| `onPlayerEliminated(m, mp)` | игрок выбыл (уже спектатор) | no-op |
| `checkResult(m)` | ядром на тике; не-null завершает матч | `m.defaultResult()` |
| `onEnd(m, result)` | матч завершается (объявить/наградить) | no-op |
| `onCleanup(m)` | уборка после матча | no-op |
| `scoreboardLines(m, viewer)` | доп. строки сайдбара | пустой список |
| `onCommand / tabComplete / onReload` | делегаты от команды ядра | null / пусто / no-op |

## API `Match`, которым пользуется игра

```java
m.arena()               // Arena — конфиг площадки (мир, лобби, спавны, setting)
m.phase()               // GamePhase: LOBBY / COUNTDOWN / RUNNING / ENDING
m.elapsedSeconds()      // сколько идёт матч
m.remainingSeconds()    // сколько осталось (при match-duration > 0)
m.players()             // Collection<MatchPlayer> — все участники
m.alivePlayers()        // List<Player> — живые сейчас
m.aliveCount()          // сколько живых
m.teams() / m.teamOf()  // команды матча
m.rememberBlock(block)  // ЗАПОМНИ блок ПЕРЕД изменением — иначе не откатится
m.trackEntity(entity)   // зарегистрируй заспавненную сущность — удалится после матча
m.get(key) / m.set(...) // типизированное состояние игры (DataKey)
m.defaultResult()       // стандартный итог: последняя живая команда / ничья
m.core()                // MgCore — доступ к stats(), messages(), transport()
```

## Где хранить состояние

Никогда — в полях `Minigame` (инстанция одна, матчей много). Два места:

**1. `Match.get/set` с `DataKey`** — состояние матча:

```java
static final DataKey<Integer> ROUND = DataKey.of("round", Integer.class);

m.set(ROUND, 1);                       // onStart
int round = m.get(ROUND, 1);           // onTick
m.set(ROUND, round + 1);
```

**2. `MatchPlayer`** — состояние на игрока (`uuid`, `name`, `alive`, `kills`, `team`).
Свои per-player счётчики — в `Match.set(...)` под ключом с UUID.

## Настройки под конкретную игру (per-arena)

Числа игры не хардкодь — держи в настройках арены, у каждой арены своё значение:

```java
static final DataKey<Integer> KILL_Y = DataKey.of("kill-y", Integer.class);

int killY = m.arena().setting(KILL_Y, 60);
```

Админ задаёт их командой ядра (`/mg set <arena> kill-y 60`) или `±`-редактором в GUI.
Ключи — пространство имён твоей игры.

## HUD, статистика, тексты

- **HUD.** Сайдбар и босс-бар (фаза + таймер) рисует ядро (если включены в конфиге).
  Свои строки — через `scoreboardLines(m, viewer)`, лягут поверх стандартных.
- **Статистика.** Пиши итоги через `core.stats().record(uuid, "skywars", delta)`.
  `played` ядро инкрементит само; победы/убийства начисляй из `onEnd`/`onPlayerEliminated`.
  Прямых SQL вне `StatsService` быть не должно. Бэкенд (SQLite/MySQL) прозрачен.
- **Тексты.** Всё игрокам — через `core.messages()` (фасад `Msg`), ключи из
  `messages.yml`. Ни строки в Java.

## Чек-лист новой игры

- [ ] `descriptor().id()` уникален и стабилен (по нему идёт статистика).
- [ ] `plugin.yml`: `depend: [MgCore]`.
- [ ] Все тексты игрокам — ключи в `messages.yml`, ни одной строки в Java.
- [ ] Числа игры — через `arena.setting(DataKey, def)`, не хардкод.
- [ ] Меняешь блок в матче → сначала `m.rememberBlock(block)`.
- [ ] Спавнишь сущность → `m.trackEntity(entity)`.
- [ ] Состояние — в `Match`/`MatchPlayer`, не в полях `Minigame`.
- [ ] Логика — main thread; предметы/сущности метишь через PDC.

Дальше: сеть и хаб — [04-cross-server.md](04-cross-server.md).
