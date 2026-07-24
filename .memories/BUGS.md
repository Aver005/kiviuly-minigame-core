# BUGS
> Known defects, severity-ranked. Move fixed ones to RESOLVED or delete.
> Last updated: 2026-07-24

Текущая незавершённая работа (16 ошибок компиляции в Escape) — это **не баг**, а
запланированное состояние миграции; см. `STATE.md` и `PLANS.md`.

## [BUG] Высокая — проявятся при первом запуске

- **Старые арены Escape не загрузятся.** Формат сменился: папка `arenas/<ID>/` из 4
  файлов → `arenas/<ID>.yml` + `game/<ID>.yml`. Старые папки остаются на диске и
  игнорируются. Нужен переносчик либо пересоздание арен вручную.
  → `escape-reborn/.../arena/ArenaManager.java` (`loadAll`)
- **Спец-предметы и маркеры Escape перестанут распознаваться.** PDC-namespace общих
  ключей сменился `escape:` → `mgcore:` (ключи теперь инициализирует ядро). Предметы,
  выданные до миграции, невалидны — перевыдать.
  → `escape-reborn/.../util/EscapeKeys.java` (игровые ключи остались свои)

## [?] Средняя — под подозрением, рантайм не проверялся

- **Порядок `giveLoadout` и `onStart` у ядра обратный привычному Escape.** Ядро зовёт
  `giveLoadout` каждому игроку, и только потом `onStart` (генерация мира арены).
  У Escape исторически мир генерировался до раздачи. Конфликта по данным не видно
  (киты не зависят от сундуков), но проверить на первом прогоне.
  → `mg-core/.../game/GameSession.java` (`startMatch`)
- **Двойной откат блоков в Escape.** Ядро откатывает то, что сломали игроки, а Escape
  дополнительно ведёт свой `editedBlocks` с трёхпроходным восстановлением. **Порядок на
  уровне кода проверен и ВЕРЕН** (2026-07-24): `GameSession.cleanup()` сначала откатывает
  свои блоки 1-проходно `update(true,false)` (без физики), ПОТОМ зовёт `game.onCleanup` →
  escape-3-проход `fixConnectingNeighbors` идёт последним и чинит стыки решёток. Рантайм
  всё ещё не гонялся, но конфликта порядка нет.
- **`Team` — заглушка.** Движок отдаёт FFA-команды (по команде на игрока). Любой код,
  рассчитывающий на реальные команды, работать не будет.
- **`StatsService.top(gameId, …)` игнорирует `gameId`** — хранилище одно-игровое.
  Лидерборд по конкретной игре вернёт данные по всем.

## RESOLVED

- ~~Escape-выбывание не сообщалось ядру → матч не завершался~~ (2026-07-24) — `isPlaying`/`aliveCount`
  читают ядровый `MatchPlayer.isAlive()`, а обычная смерть (`finishElimination`) и оффлайн-таймаут
  (`eliminateOffline`) `match.eliminate` не звали → выбывшие «живы», `defaultResult` не срабатывает.
  Добавлен тихий `Match.eliminate(UUID)` (без broadcast движка, работает и для ОФФЛАЙН-игрока),
  escape-пути переведены на него; чат-переход делает хук `onEliminated` (дубль снят), спектейт на
  reconnect для не-живых. Оффлайн-выбывший остаётся в ростере спектатором — cleanup вернёт снапшот.
- ~~Смерть в Escape не роняла лут и не восстанавливала HP~~ (2026-07-24) — `dropInventory`+
  `setHealth(20)` жили в escape-`GameListener.onDamage`, который стал мёртв (ядро гасит
  летальный урон на NORMAL раньше escape-HIGH и ведёт смерть через хук `onLethalDamage`→
  `handleDeath`). Перенёс сброс/лечение в `handleDeath`, мёртвый `onDamage` удалён.
- ~~~60 escape-подкоманд не работали в рантайме~~ (2026-07-24) — `EscapeGame` не реализовывал
  `onCommand`/`tabComplete`; собиралось зелёным, но `core.commandFor` отдаёт игро-специфику в
  `owner.onCommand` (дефолт `return false`). Делегировал в `EscapeCommand.handle/tab`.
- ~~Операторы не проходили админ-гейт `/escape`~~ (2026-07-24) — `EscapeGame.adminPermission()`
  возвращал необъявленный `esc.admin`, а plugin.yml/код используют `escape.admin`. Выровнено на `escape.admin`.
- ~~Импорты, вставленные `sed`, ломали строку `package`~~ — починено awk-ом,
  30+ файлов Escape; приём занесён в `LEARNINGS.md`.
- ~~`AnvilInputMenu` в `mg-api` тянул `MgCorePlugin`~~ — переведён на `org.bukkit.plugin.Plugin`.
- ~~`DebugLog.Cat` не имел игровых категорий Escape~~ — Escape оставлен со своим `DebugLog`.
