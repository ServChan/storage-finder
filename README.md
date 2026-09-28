# Storage Finder

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.2.0-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

High-performance client-side storage search engine with 3D collision-aware A* navigation, container memory, a unified route/HUD config, a container index browser, and LTS_Server Economy integration.

## Русский

### Что это

`Storage Finder` (mod id `storagefinder`) — клиентский Fabric-мод для Minecraft 26.3 для поиска предметов на складах и базах. Мод индексирует предметы в рамках, запоминает содержимое открываемых контейнеров (сундуки, бочки, шалкеры, воронки, дропперы) и прокладывает честный трёхмерный пешеходный маршрут к целевому хранилищу с учётом препятствий.

### Возможности

- **Поиск по предмету в руке**: возьмите предмет и нажмите клавишу поиска — мод подсветит все контейнеры с ним в радиусе склада и проложит путь;
- **Постоянная панель поиска**: текстовый поиск с автодополнением по названию предметов во время игры;
- **Запоминание контейнеров**: автоматическое сканирование содержимого открываемых контейнеров с координатами, типом, количеством и метаданными;
- **Навигация A* с обходом препятствий**: маршрут строится по реальной траектории движения игрока с учётом дверей, калиток, лестниц, ступеней, плит и перепадов высот; прыжок на 1 блок вверх, безопасный спуск до 3 блоков вниз, обход лавы, костров и открытых люков; бюджет поиска — до 1200 узлов за тик;
- **Браузер индекса контейнеров** и единая конфигурация маршрута/HUD;
- **Интеграция с LTS_Server Economy**: над найденным контейнером отображается минимальная цена за штуку и сводка магазина;
- **0-лаг**: сканирование чанков, индексирование рамок и расчёт путей выполняются в фоновом потоке-демоне; поток рендеринга только читает готовые координаты и кэшированные линии.

### Управление

| Действие | Клавиша по умолчанию | Переназначение |
|---|---|---|
| Найти предмет / убрать поиск | `Right Alt` | Options → Controls → Storage Finder |

Экран настроек — через **Mod Menu**.

### Настройки

Экран настроек открывается через **Mod Menu** (цвета подсветки и маршрута, радиус поиска, бюджет поиска пути, стрелки направления, мост экономики, запоминание содержимого контейнеров). Файлы: `config/storage_finder.json` (настройки) и `config/storage_finder_index.json` (индекс склада; изолируется по серверу и миру одиночной игры). Запись атомарная через временные файлы, с резервной копией `.bak`.

### Установка

1. **Fabric Loader** `0.19.3+` и **Fabric API**.
2. Скопируйте `storage-finder-1.2.0.jar` из `build/libs/` в папку `mods/`.
3. **LTS_Server Economy** (`>=1.1.0`) — опционально, для цен над контейнерами.
4. **Mod Menu** — опционально, открывает экран настроек.

**Требования:** Minecraft `26.3` · Java `25` · только клиент.

### Сборка

```powershell
.\gradlew.bat clean build
```

Готовый JAR: `build/libs/storage-finder-1.2.0.jar`.

---

## English

### What It Is

`Storage Finder` (mod id `storagefinder`) is a client-side Fabric mod for Minecraft 26.3 for finding items in warehouses and bases. It indexes items in item frames, memorizes opened container contents (chests, barrels, shulkers, hoppers, droppers), and renders a true 3D collision-aware walking path to the target storage.

### Features

- **Held-item search**: hold an item and press the search key — the mod highlights every matching container within the storage radius and draws a path;
- **Permanent search panel**: in-game text search with item-name autocompletion;
- **Container memory**: automatic scanning of opened containers with coordinates, type, counts, and metadata;
- **Collision-aware A\* navigation**: routes follow the player's actual movement path, respecting doors, gates, ladders, stairs, slabs, and height changes; 1-block step-ups, safe drops up to 3 blocks, avoidance of lava, campfires, and open trapdoors; a search budget of up to 1200 nodes per tick;
- **Container index browser** and a unified route/HUD configuration;
- **LTS_Server Economy integration**: the lowest per-unit price and a shop summary are shown over a found container;
- **0-lag**: chunk scanning, frame indexing, and pathfinding run on a background daemon thread; the render thread only reads prepared coordinates and cached lines.

### Controls

| Action | Default key | Rebind |
|---|---|---|
| Find item / clear search | `Right Alt` | Options → Controls → Storage Finder |

The settings screen opens through **Mod Menu**.

### Configuration

The settings screen opens through **Mod Menu** (highlight and route colors, search radius, pathfinding budget, direction arrows, economy bridge, container-memory toggle). Files: `config/storage_finder.json` (settings) and `config/storage_finder_index.json` (storage index; scoped per server and per singleplayer world). Writes are atomic via temporary files with a `.bak` backup.

### Installation

1. **Fabric Loader** `0.19.3+` and **Fabric API**.
2. Copy `storage-finder-1.2.0.jar` from `build/libs/` into `mods/`.
3. **LTS_Server Economy** (`>=1.1.0`) — optional, for prices over containers.
4. **Mod Menu** — optional, opens the settings screen.

**Requirements:** Minecraft `26.3` · Java `25` · client-only.

### Building

```powershell
.\gradlew.bat clean build
```

Output JAR: `build/libs/storage-finder-1.2.0.jar`.

## Лицензия / License

[MIT](LICENSE). Developed by LTS_Server.
