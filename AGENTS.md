# AGENTS.md — bridge into the knowledge base

**Kiviuly minigame platform** — общее ядро для мини-игр Minecraft (Paper) и три игры
в соседних репозиториях, которые на него переезжают.

## Read first

**`.memories/INDEX.md`** — точка входа. Затем `.memories/STATE.md` (текущий фронт
работ) и `.memories/PLANS.md` (следующий шаг). Перед правкой кода —
`.memories/CONVENTIONS.md` и `.memories/LEARNINGS.md`.

## Репозитории

| Путь | Что | Статус |
|---|---|---|
| `E:\Projects\Me\kiviuly-mg-core` | платформа (этот репо) | рабочая |
| `E:\Projects\Me\skywars-reborn` | SkyWars | ✅ переехала |
| `E:\Projects\Me\skyblockwars-reborn` | SkyBlockWars | ✅ переехала |
| `E:\Projects\Me\escape-reborn` | Escape | ⏳ переезд не закончен |

## Build

```bash
./gradlew build          # в любом из четырёх репозиториев
./gradlew deploy         # копия jar на тестовый сервер (каталог из .env: DEPLOY_DIR)
```
Java 25, Paper 26.1.2, Gradle 9.6.1. Игры тянут `mg-api` через composite build
(`includeBuild("../kiviuly-mg-core")`) — публиковать ничего не надо.

## ⚠️ Важно знать до первой команды

- **`escape-reborn` сейчас НЕ СОБИРАЕТСЯ — и это нормально.** Миграция идёт «в один
  заход»: владелец сознательно отказался от промежуточных зелёных сборок, тест — в
  конце. Не «чини» это откатом; читай `.memories/PLANS.md`.
- **Рантайм не проверялся ни разу.** Всё подтверждено только компиляцией. Не пиши
  «работает», если только собралось.

## Инварианты (нарушение = баг)

1. Меняешь блок в матче — сначала `match.rememberBlock`; спавнишь сущность — `trackEntity`.
2. Ни одной строки игроку в Java — только ключи `messages.yml` через `Msg`.
3. Данные предметов — только PDC (`Keys`), никогда не парсить имена/лор.
4. Вся игровая логика — main thread; async только у статистики.
5. Игра в `onEnable` зовёт `Msg.merge(this)`, **не `Msg.init`** (сотрёт каталог ядра).
6. Игра **не регистрирует свой `MenuListener`** — иначе двойная обработка кликов.

Полный список и обоснования — `.memories/CONVENTIONS.md`.
