# Storage Finder

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-62b47a)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-client--side-d7c59a)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-e76f00)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Русский

Клиентский Fabric-мод для Minecraft **26.1.2**, который ищет ближайшие хранилища по выбранным предметам, подсвечивает все совпадения и строит безопасные наземные маршруты максимум к трём ближайшим достижимым целям.

Мод работает полностью на клиенте: устанавливать его на сервер не требуется. Для поиска используются предметные рамки и локальный индекс контейнеров, которые игрок уже открывал.

### Содержание

- [Возможности](#возможности)
- [Управление](#управление)
- [Запоминание контейнеров](#запоминание-контейнеров)
- [Данные и разделение миров](#данные-и-разделение-миров)
- [Маршрут](#маршрут)
- [Настройки Mod Menu](#настройки-mod-menu)
- [Установка](#установка)
- [Сборка](#сборка)
- [Диагностика](#диагностика)
- [Совместимость и ограничения](#совместимость-и-ограничения)

### Возможности

- Поиск и запоминание сундуков, сундуков-ловушек, бочек и поставленных шалкеровых ящиков.
- Эндер-сундуки не индексируются и не участвуют в результатах.
- Источники данных объединяются: предметные рамки и локально запомненное содержимое контейнеров.
- Обычные предметы сравниваются по registry-типу. Название, прочность, обычные зачарования и прочие компоненты игнорируются.
- Зачарованные книги индексируются отдельно по каждому виду зачарования. Уровень и пользовательское имя книги не влияют.
- Несколько запросов из руки получают разные цвета.
- При активном поиске снизу слева постоянно показывается компактная HUD-панель с иконкой, названием, цветом, числом совпадений и состоянием маршрута.
- Все совпавшие хранилища подсвечиваются; стрелки и A*-маршруты строятся максимум к трём ближайшим достижимым хранилищам каждого цвета.
- Маршрут учитывает стены, полный AABB игрока, ковры, плиты, ступени, снег и другие реальные формы коллизий.
- Маршрут избегает жидкостей, огня, кактусов, магмы, порошкового снега и других опасностей, если это включено в настройках.
- Строгий максимальный радиус — восемь чанков, то есть 128 блоков по горизонтальной евклидовой дистанции.
- Мод не загружает чанки ради поиска.

### Управление

Клавиша поиска доступна в стандартном меню **Настройки → Управление → Назначение клавиш → Поиск хранилищ**. По умолчанию назначен **правый Alt**; её можно заменить на любую другую клавишу.

#### Поиск предметом в руке

1. Возьмите предмет в основную руку.
2. Нажмите назначенную клавишу поиска (**правый Alt** по умолчанию).
3. Повторное нажатие с тем же предметом убирает его из поиска.

Повторное нажатие назначенной клавиши с тем же предметом удаляет только этот запрос. Если предмета уже нет в руке, откройте поиск пустой рукой: активные запросы показаны первыми с отметкой `✓`, и щелчок по активному запросу удаляет его. Кнопка **«Очистить поиск»** отключает все запросы сразу.

Последовательными нажатиями с разными предметами можно включить несколько цветных запросов.

#### Постоянная панель поиска

Панель появляется снизу слева только в игровом HUD и только при наличии активных запросов. Для каждого запроса она показывает:

- настоящий значок предмета;
- локализованное имя (для книги — конкретные чары);
- цвет подсветки;
- число подходящих хранилищ;
- состояние `расчёт`, число готовых маршрутов либо `нет пути`.

Панель не перехватывает управление мышью. Для удаления запроса откройте каталог пустой рукой: активные строки находятся сверху и удаляются щелчком.

#### Текстовый поиск

1. Выберите пустой слот основной руки.
2. Нажмите назначенную клавишу поиска (**правый Alt** по умолчанию).
3. Начните вводить локализованное название предмета.
4. Выберите подсказку мышью либо стрелками и Enter.

Каталог использует текущий язык Minecraft. Для зачарованных книг создаётся настоящий `ItemStack` с выбранным зачарованием и применяется тот же код определения типа, что и для предмета в руке. Текстовый выбор переключает только выбранный запрос, поэтому добавление и удаление нескольких предметов доступно и через окно поиска.

Если несколько registry-предметов имеют одинаковый перевод, рядом отображается внутренний ID только для различения вариантов.

#### Завершение поиска

После открытия совпавшего хранилища запросы, соответствующие этому контейнеру, автоматически отключаются. Остальные цветные запросы сохраняются. Полностью очистить поиск можно кнопкой в окне текстового ввода.

### Запоминание контейнеров

Мод привязывает открытое меню к физическому блоку, по которому кликнул игрок, и сохраняет только содержимое контейнера — инвентарь игрока не индексируется. Двойные сундуки канонизируются в одну запись, но подсвечиваются обе физические половины.

Снимок обновляется:

- при первом открытии;
- при изменении содержимого открытого контейнера;
- перед закрытием экрана;
- при смене мира;
- при остановке клиента.

При сбое записи индекс остаётся «грязным», и мод повторяет сохранение на следующем наблюдении контейнера. Уведомление не заявляет об успешном сохранении, если запись на диск действительно не удалась.

### Данные и разделение миров

Основной индекс:

```text
config/storage_finder_index.json
```

Резервная копия предыдущего корректного состояния:

```text
config/storage_finder_index.json.bak
```

Долговременная копия с максимальным известным числом записей:

```text
config/storage_finder_index.json.recovery
```

Запись выполняется через соседний временный файл с атомарной заменой, когда файловая система это поддерживает. Если основной JSON отсутствует, повреждён или неожиданно пуст, мод выбирает наиболее полную копию между `.bak` и `.recovery`. Старый формат ключей зачарованных книг мигрируется после закрытия reader, что важно для Windows.

Область данных включает:

- адрес сервера либо имя одиночного мира;
- dimension ID;
- seed из `CommonPlayerSpawnInfo`, полученный на login и respawn.

Legacy-записи без seed переносятся только если находятся в текущем радиусе игрока. Это не позволяет одной близкой записи ошибочно перетащить весь старый индекс в другой мир сервера.

Ограничение протокола: если сервер скрывает идентичность миров и два мира имеют одновременно одинаковый адрес, dimension ID и присланный seed, клиент не получает дополнительного стабильного ID и не может гарантированно различить такие миры.

### Маршрут

Маршрут строится пошаговым multi-goal A* только по уже загруженной клиентом геометрии. На один игровой тик выделяется общий бюджет до 800 A*-узлов, поэтому сложный поиск продолжается несколько кадров и показывает в HUD состояние `расчёт`, не выполняя все 40 000 узлов одним рывком. Конечная точка находится на достижимой высоте сбоку от хранилища, поэтому верхний сундук в вертикальном ряду не отправляет игрока на этаж над ним.

Для бочек, утопленных заподлицо в пол и направленных крышкой вверх, разрешён подход с соседнего пола и стояние непосредственно над бочкой. Горизонтально направленные бочки используют обычные боковые цели, поэтому маршрут не отправляет игрока на этаж над настенной бочкой.

Кэш проверяет все узлы и периодически обновляется. Когда игрок движется по уже рассчитанному пути, мод переиспользует оставшийся хвост маршрута вместо полного пересчёта. Неудачная тяжёлая попытка A* для неизменных позиции и набора целей повторяется не чаще одного раза в пять секунд; движение игрока запускает новую попытку сразу.

Маршрут является наземным: мод не планирует открывание дверей, плавание, полёт, лазание по лестницам или сложные паркур-прыжки. Поиск ограничен защитным лимитом посещённых A*-узлов, поэтому крайне сложная цель может подсвечиваться без построенного маршрута.

### Настройки Mod Menu

Через кнопку настроек Storage Finder доступны:

- включение мода;
- запоминание контейнеров;
- поиск по рамкам;
- поиск по сохранённому содержимому;
- подсветка;
- маршруты;
- обход опасностей;
- уведомления;
- радиус от 1 до 8 чанков.

Конфигурация хранится в `config/storage_finder.json`.

При ошибке записи экран остаётся открытым и показывает уведомление об ошибке вместо ложного сообщения об успехе. Закрытие или отмена возвращает на родительский экран Mod Menu.

### Установка

Требования:

- Minecraft 26.1.2;
- Fabric Loader 0.19.3 или новее в ветке, совместимой с 26.1.2;
- Java 25;
- Fabric Lifecycle Events, Fabric Rendering API и Fabric Key Mapping API (обычно входят в Fabric API);
- Mod Menu необязателен, но нужен для графических настроек.

Скопируйте обычный JAR без `-sources` в папку `mods` профиля. Мод устанавливается только на клиент; серверная установка не требуется.

Не заменяйте JAR при запущенном Minecraft: JVM может лениво читать ещё не загруженные классы из архива, и запись поверх активного файла способна вызвать `ZipFile invalid LOC header`. Закройте клиент перед обновлением либо используйте отложенную замену после завершения процесса.

### Сборка

```powershell
.\gradlew.bat clean build --warning-mode all
```

Готовый файл:

```text
build/libs/storage-finder-1.0.0.jar
```

Проект использует Java 25, Loom 1.17.12 и Gradle Wrapper 9.5.0. Minecraft 26.1.x необфусцирован, поэтому Mojang mappings не подключаются.

### Диагностика

Полезные файлы профиля:

```text
logs/latest.log
crash-reports/
config/storage_finder.json
config/storage_finder_index.json
config/storage_finder_index.json.bak
config/storage_finder_index.json.recovery
```

При выборе через руку или текстовый каталог мод пишет в INFO локализованное название и фактический набор внутренних ключей. Это позволяет сравнить два способа поиска без включения подробного debug-лога.

### Совместимость и ограничения

- Проверена компиляция против Minecraft 26.1.2 и текущих Fabric API-модулей проекта.
- Рендер использует `LevelRenderEvents` и Gizmos, не заменяя рендер чанков Sodium/Iris/Voxy.
- Поиск видит только уже загруженные клиентом чанки и сущности.
- Закрытый контейнер нельзя прочитать с клиента до его открытия; рамка при этом доступна как независимый источник.
- После ручного удаления физического контейнера его запись остаётся в JSON, но не показывается. Это сделано намеренно, чтобы временно неполный чанк не стирал пользовательский индекс.
- Последняя аудитная сборка скомпилирована, но окончательная проверка новых изменений в запущенной игре должна выполняться после перезапуска клиента.

Дополнительная документация:

- [результаты технического аудита](AUDIT.md);
- [история изменений](CHANGELOG.md);
- [планы развития](ROADMAP.md);
- [готовые тексты для публикации](PUBLISHING.md).

### Лицензия

MIT, автор `LTS_Server`.

## English

Storage Finder is a client-side Fabric mod for Minecraft **26.1.2** that finds nearby storage blocks containing selected items, highlights every match, and builds safe ground routes to up to three of the nearest reachable targets.

The mod runs entirely on the client and does not need to be installed on the server. It combines visible item frames with a local index of containers that the player has previously opened.

### Contents

- [Features](#features)
- [Controls](#controls)
- [Remembering containers](#remembering-containers)
- [Data and world separation](#data-and-world-separation)
- [Routing](#routing)
- [Mod Menu settings](#mod-menu-settings)
- [Installation](#installation)
- [Building](#building)
- [Diagnostics](#diagnostics)
- [Compatibility and limitations](#compatibility-and-limitations)

### Features

- Finds and remembers chests, trapped chests, barrels, and placed shulker boxes.
- Ender Chests are not indexed and never appear in search results.
- Combines two data sources: item frames and locally remembered container contents.
- Regular items are matched by registry item type. Custom names, durability, normal enchantments, and other components are ignored.
- Enchanted books are indexed separately for every stored enchantment type. Enchantment level and custom book name are ignored.
- Multiple held-item searches receive different colors.
- A compact HUD panel remains visible in the bottom-left corner while searches are active, showing the item icon, name, color, match count, and route status.
- Every matching storage block is highlighted. Arrows and A* routes are built to up to three of the nearest reachable storage blocks for each color.
- Routes account for walls, the player's full AABB, carpets, slabs, stairs, snow, and other real collision shapes.
- Routes avoid fluids, fire, cactus, magma, powder snow, and other hazards when hazard avoidance is enabled.
- The strict maximum radius is eight chunks, or 128 blocks by horizontal Euclidean distance.
- The mod never loads chunks just to search them.

### Controls

The search key is available in **Options → Controls → Key Binds → Storage Finder**. It is bound to **Right Alt** by default and can be reassigned to any other key.

#### Searching with a held item

1. Hold an item in your main hand.
2. Press the configured search key (**Right Alt** by default).
3. Press the key again with the same item to remove that search.

Repeating the key press removes only the matching query. If the item is no longer in your hand, open the catalog with an empty main hand: active searches appear first with a `✓` mark and can be removed by clicking them. The **Clear search** button removes all queries at once.

Pressing the key with different held items enables several color-coded searches at the same time.

#### Persistent search panel

The panel appears in the bottom-left corner of the in-game HUD only while at least one search is active. For every query it displays:

- the actual item icon;
- the localized name, or the specific enchantment for a book;
- the highlight color;
- the number of matching storage blocks;
- the `calculating` state, number of ready routes, or `no route` state.

The panel does not capture mouse input. To remove a query, open the catalog with an empty main hand. Active entries are listed first and can be removed by clicking them.

#### Text search

1. Select an empty main-hand slot.
2. Press the configured search key (**Right Alt** by default).
3. Start typing the localized item name.
4. Select a suggestion with the mouse, or use the arrow keys and Enter.

The catalog follows the current Minecraft language. For enchanted books, it creates a real `ItemStack` with the selected enchantment and passes it through the same identity code used for held items. Text selection toggles only the selected query, so multiple searches can also be added or removed through the search screen.

If several registry items have the same translated name, their internal IDs are shown only to distinguish those entries.

#### Completing a search

Opening a matching storage block automatically disables only the queries that match that container. Other color-coded queries remain active. All searches can be cleared with the button in the text search screen.

### Remembering containers

The mod associates an opened menu with the physical block the player clicked and saves only the container inventory. The player's inventory is never indexed. Double chests are canonicalized into one entry while both physical halves remain highlighted.

A snapshot is updated:

- when the container is first opened;
- when the contents of an open container change;
- before the screen closes;
- when the world changes;
- when the client stops.

If writing fails, the index remains dirty and the mod retries saving after the next container observation. It does not report a successful save when the data was not actually written to disk.

### Data and world separation

Primary index:

```text
config/storage_finder_index.json
```

Backup of the previous valid state:

```text
config/storage_finder_index.json.bak
```

Long-lived recovery copy containing the greatest known number of entries:

```text
config/storage_finder_index.json.recovery
```

Data is written to a sibling temporary file and atomically replaced when supported by the file system. If the primary JSON is missing, damaged, or unexpectedly empty, the mod selects the most complete copy between `.bak` and `.recovery`. The old enchanted-book key format is migrated after closing the reader, which is important on Windows.

The data scope includes:

- server address or singleplayer world name;
- dimension ID;
- the seed from `CommonPlayerSpawnInfo`, captured on login and respawn.

Legacy entries without a seed are migrated only when they are within the player's current search radius. This prevents one nearby record from incorrectly moving an entire old index into another server world.

Protocol limitation: if a server hides world identity and two worlds share the same address, dimension ID, and transmitted seed, the client receives no additional stable identifier and cannot reliably distinguish those worlds.

### Routing

Routes are calculated incrementally with multi-goal A* using only geometry already loaded by the client. A shared budget of up to 800 A* nodes is processed per game tick, so a complex search continues across several frames and displays `calculating` in the HUD instead of processing all 40,000 nodes in one burst. The endpoint is placed at a reachable height beside the storage block, preventing an upper chest in a vertical stack from routing the player to the floor above it.

For upward-facing barrels embedded flush with the floor, the route may approach from the neighboring floor or finish directly above the barrel. Horizontally facing wall barrels use normal side goals, so they do not route the player to the floor above them.

The cache validates every node and refreshes periodically. As the player follows a calculated path, the mod reuses the remaining route tail instead of rebuilding the entire path. A failed expensive A* attempt for an unchanged player position and target set is repeated no more than once every five seconds; moving the player starts a new attempt immediately.

Routing is ground-based. The mod does not plan door opening, swimming, flying, ladder climbing, or complex parkour jumps. A safety limit caps the number of visited A* nodes, so an extremely difficult target may remain highlighted without a route.

### Mod Menu settings

The Storage Finder settings screen provides controls for:

- enabling the mod;
- remembering containers;
- searching item frames;
- searching saved contents;
- highlights;
- routes;
- hazard avoidance;
- notifications;
- a radius from 1 to 8 chunks.

Configuration is stored in `config/storage_finder.json`.

If saving fails, the settings screen stays open and displays an error instead of a false success message. Closing or cancelling returns to the parent Mod Menu screen.

### Installation

Requirements:

- Minecraft 26.1.2;
- Fabric Loader 0.19.3 or a newer 26.1.2-compatible release;
- Java 25;
- Fabric Lifecycle Events, Fabric Rendering API, and Fabric Key Mapping API, normally supplied by Fabric API;
- Mod Menu is optional but required for the graphical settings screen.

Copy the normal JAR without the `-sources` suffix into the profile's `mods` directory. This is a client-only mod; no server-side installation is required.

Do not replace the JAR while Minecraft is running. The JVM may lazily read classes that have not yet been loaded, and overwriting an active archive can cause a `ZipFile invalid LOC header` error. Close the client before updating or arrange a delayed replacement after the process exits.

### Building

```powershell
.\gradlew.bat clean build --warning-mode all
```

Output:

```text
build/libs/storage-finder-1.0.0.jar
```

The project uses Java 25, Loom 1.17.12, and Gradle Wrapper 9.5.0. Minecraft 26.1.x is unobfuscated, so Mojang mappings are not configured.

### Diagnostics

Useful profile files:

```text
logs/latest.log
crash-reports/
config/storage_finder.json
config/storage_finder_index.json
config/storage_finder_index.json.bak
config/storage_finder_index.json.recovery
```

When a query is selected through a held item or the text catalog, the mod logs the localized name and actual internal key set at INFO level. This allows both search methods to be compared without enabling detailed debug logging.

### Compatibility and limitations

- Compilation has been verified against Minecraft 26.1.2 and the project's current Fabric API modules.
- Rendering uses `LevelRenderEvents` and Gizmos without replacing the Sodium, Iris, or Voxy chunk renderer.
- Search can see only chunks and entities already loaded by the client.
- The client cannot read a closed container before it has been opened; an item frame remains available as an independent source.
- Manually removing a physical container does not immediately delete its JSON entry, but the missing block is not displayed. This intentionally prevents a temporarily incomplete chunk from erasing the user's index.
- The latest audited build compiles successfully, but final verification of recent changes in a running game must be performed after restarting the client.

Additional documentation:

- [technical audit results](AUDIT.md);
- [change history](CHANGELOG.md);
- [development roadmap](ROADMAP.md);
- [ready-to-use publication text](PUBLISHING.md).

### License

MIT, authored by `LTS_Server`.
