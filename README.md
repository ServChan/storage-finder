# Storage Finder

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.2.2-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Client-side storage search: remembers the containers you open and items in frames, highlights every match, draws collision-aware walking routes to them, and shows LTS_Server Economy prices in its HUD.

**Руководство / User guide:** [Русский](docs/USER_GUIDE_RU.md) · [English](docs/USER_GUIDE_EN.md)

## Русский

### Что это

`Storage Finder` (mod id `storagefinder`) — клиентский Fabric-мод для Minecraft 26.3 для поиска предметов на складах и базах. Мод запоминает содержимое сундуков, бочек и шалкеров, которые вы открываете сами, учитывает предметы в рамках, подсвечивает хранилища с нужным предметом и строит к ним пешеходный маршрут с учётом препятствий. Мод пассивный: он ничего не открывает за вас.

### Возможности

- **Поиск по предмету в руке**: возьмите предмет и нажмите клавишу поиска — подсветятся все подходящие хранилища в радиусе до 8 чанков, повторное нажатие убирает этот поиск; одновременно можно искать несколько предметов, у каждого свой цвет;
- **Текстовый поиск**: с пустой рукой клавиша открывает экран поиска по названию или ID с прокруткой, количеством уже сохранённых предметов и кнопкой сброса всех поисков;
- **Память контейнеров**: сундуки (одинарные и двойные), бочки и шалкеры индексируются после синхронизации меню сервером; меню плагинов с другим числом слотов не записываются; у записей видна свежесть данных;
- **Маршруты A\***: двери и калитки (настраивается), ступени, плиты, плавание по горизонтали и вертикали, подъём на 1 блок и безопасный спуск до 3 блоков, обход лавы, костров и открытых люков; до 5 маршрутов на запрос, бюджет 200–2000 узлов за тик (по умолчанию 800);
- **HUD**: список активных поисков с длиной пешего маршрута, свежестью источника и причиной, если маршрута нет; компактный режим, прозрачность, число строк и размещение перетаскиванием с предпросмотром;
- **Статистика и менеджер индекса**: что и где хранится, фильтр текущей или всех областей, точечное удаление, подтверждаемая очистка и экспорт индекса в JSON;
- **Интеграция с LTS_Server Economy**: при зажатом левом Alt HUD показывает минимальную и среднюю цену за штуку и число магазинов;
- **Без лагов**: запись индекса идёт в фоновом потоке, а поиск пути ограничен бюджетом узлов за тик.

### Управление

| Действие | Клавиша по умолчанию | Переназначение |
|---|---|---|
| Найти предмет в руке / убрать его поиск | `Right Alt` | Настройки → Управление → Storage Finder |
| Открыть текстовый поиск | `Right Alt` с пустой рукой | та же клавиша |
| Цены Economy в HUD | зажать `Left Alt` | — |

### Настройки

Экран настроек открывается через **Mod Menu**; из него открываются отдельные экраны настроек HUD (с редактором размещения) и маршрутов. Параметры: включение мода, запоминание контейнеров, поиск в рамках и в сохранённом содержимом, подсветка, маршруты, обход опасностей, плавание, проход через двери, число маршрутов и бюджет узлов, радиус в чанках, уведомления, положение и вид HUD.

Файлы: `config/storage_finder.json` (настройки) и `config/storage_finder_index.json` (индекс склада, раздельно по серверу и миру одиночной игры). Запись атомарная через временный файл, с резервной копией `.bak`.

### Установка

1. **Fabric Loader** `0.19.3+` и **Fabric API**.
2. Скопируйте `storage-finder-1.2.2.jar` из `build/libs/` в папку `mods/`.
3. **LTS_Server Economy** (`>=1.1.0`) — опционально, для цен в HUD.
4. **Mod Menu** — опционально, открывает экран настроек.

**Требования:** Minecraft `26.3` · Java `25` · только клиент.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/storage-finder-1.2.2.jar`.

### Macro-версия

Та же сборка кладёт рядом `storage-finder-1.2.2-macro.jar` — обычный мод плюс автопросмотр: пока вы идёте мимо,
мод по одному открывает видимые сундуки, бочки и шалкеры в зоне досягаемости, читает содержимое без экрана, не
поворачивает камеру и закрывает меню через несколько тиков; звук открытия этих хранилищ приглушается. Хранилище
перечитывается не чаще раза в 5 минут, ловушечные сундуки и эндер-сундук не трогаются, во время приседания, ЛКМ/ПКМ
и при открытом меню автопросмотр ждёт. **F8** включает и выключает его, параметры — в
`config/storage_finder_macro.json`. Ставьте только один из двух jar. Сервер видит обычные открытия сундуков, поэтому
на публичных серверах это может считаться макросом — используйте обычную версию.

---

## English

### What It Is

`Storage Finder` (mod id `storagefinder`) is a client-side Fabric mod for Minecraft 26.3 for finding items in warehouses and bases. It remembers the contents of chests, barrels, and shulker boxes you open yourself, takes items in frames into account, highlights containers holding the item you want, and builds a collision-aware walking route to them. The mod is passive: it never opens anything for you.

### Features

- **Held-item search**: hold an item and press the search key to highlight every matching container within up to 8 chunks; press again to drop that search; several items can be searched at once, each with its own color;
- **Text search**: with an empty hand the key opens a search screen by name or ID with scrolling, stored-item counts, and a button that clears all searches;
- **Container memory**: chests (single and double), barrels, and shulker boxes are indexed once the server has synced the menu; plugin menus with a different slot count are not recorded; entries show how fresh their data is;
- **A\* routes**: doors and gates (configurable), stairs, slabs, horizontal and vertical swimming, 1-block step-ups and safe drops of up to 3 blocks, avoiding lava, campfires, and open trapdoors; up to 5 routes per query and a 200–2000 node budget per tick (800 by default);
- **HUD**: active searches with walking-route length, source freshness, and the reason when no route exists; compact mode, opacity, row count, and drag-to-place layout with a live preview;
- **Statistics and index manager**: what is stored where, current-area or all-area filters, per-entry removal, confirmed clearing, and JSON export of the index;
- **LTS_Server Economy integration**: hold left Alt and the HUD shows the minimum and average unit price and the shop count;
- **Lag-free**: index writes run on a background thread and pathfinding is capped by a per-tick node budget.

### Controls

| Action | Default key | Rebind |
|---|---|---|
| Search the held item / drop its search | `Right Alt` | Options → Controls → Storage Finder |
| Open text search | `Right Alt` with an empty hand | same key |
| Economy prices in the HUD | hold `Left Alt` | — |

### Configuration

The settings screen opens through **Mod Menu** and links to separate HUD settings (with the layout editor) and route settings. Options: mod enabled, remember containers, search frames and remembered contents, highlights, routes, hazard avoidance, swimming, routing through doors, route count and node budget, radius in chunks, notifications, HUD position and style.

Files: `config/storage_finder.json` (settings) and `config/storage_finder_index.json` (storage index, kept separate per server and per singleplayer world). Writes are atomic via a temporary file with a `.bak` backup.

### Installation

1. **Fabric Loader** `0.19.3+` and **Fabric API**.
2. Copy `storage-finder-1.2.2.jar` from `build/libs/` into `mods/`.
3. **LTS_Server Economy** (`>=1.1.0`) — optional, for prices in the HUD.
4. **Mod Menu** — optional, opens the settings screen.

**Requirements:** Minecraft `26.3` · Java `25` · client-only.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/storage-finder-1.2.2.jar`.

### Macro edition

The same build also writes `storage-finder-1.2.2-macro.jar`: the regular mod plus auto-peek. While you walk past,
it opens visible chests, barrels and shulker boxes in reach one at a time, reads them without a screen or camera
movement, and closes the menu a few ticks later; their open and close sounds are muted. Each storage is re-read at
most once every 5 minutes, trapped chests and ender chests are skipped, and it waits while you sneak, hold
attack/use or have a screen open. **F8** toggles it; settings live in `config/storage_finder_macro.json`. Install
only one of the two jars. The server sees ordinary container opens, so public servers may treat this as a macro —
use the standard edition there.

## Лицензия / License

[MIT](LICENSE). Developed by LTS_Server.
