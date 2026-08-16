# Storage Finder

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.1.2%20%7C%2026.2-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.0.3-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

High-performance client-side storage search engine for Minecraft 26.1.2-26.2 with 3D collision-aware A* navigation, container memory, and LTS_Server Economy integration.

## Русский

### Что это

`Storage Finder` — флагманский клиентский Fabric-мод для Minecraft 26.1.2–26.2, предоставляющий систему интеллектуального поиска предметов на складах и базах. Мод индексирует предметы в рамках, запоминает содержимое открываемых контейнеров (сундуки, бочки, шалкеры, воронки, дропперы) и прокладывает честный трёхмерный пешеходный маршрут к целевому хранилищу с учётом всех препятствий.

### Что дает мод

- **Мгновенный поиск по предмету в руке**: Наведите прицел или возьмите предмет в руку и нажмите `V` — мод подсветит все сундуки с этим предметом в радиусе склада и проложит кратчайший путь.
- **Постоянная панель поиска (Permanent Search Panel)**: Текстовый поиск с автодополнением по названию предметов прямо во время игры.
- **Интеллектуальное запоминание контейнеров**: Мод автоматически сканирует содержимое любых открываемых вами сундуков, сохраняя точные координаты, тип хранилища, количество и метаданные предметов.
- **3D-навигация A* с обходом препятствий**: Маршрут строится не по прямой сквозь стены, а по реальной физической траектории движения игрока с учётом дверей, калиток, лестниц, ступеней, плит и перепадов высот.
- **Интеграция с LTS_Server Economy**: При наличии мода экономики прямо над найденным сундуком отображается минимальная цена за штуку и сводка цен магазина.

### Особенности и архитектура

1. **Неблокирующий фоновый воркер (0-lag architecture)**:
   - Все операции сканирования чанков, индексирования рамок и расчёта путей A* выполняются в отдельном фоновом потоке-демоне (`Executors.newSingleThreadExecutor`).
   - Основной поток рендеринга (UI Thread) только считывает готовые координаты и кэшированные линии, обеспечивая стабильные 60+ FPS без задержек.
2. **Алгоритм поиска пути A***:
   - Лимит бюджета поиска: до 1200 узлов за тик для исключения просадок.
   - Поддержка прыжков на 1 блок вверх, безопасного спуска до 3 блоков вниз, обход лавы, кактусов и открытых люков.
3. **Безопасность данных и сохранение**:
   - База данных склада изолируется по серверам (`config/storagefinder/servers/<server_ip>.json`) и мирам одиночной игры.
   - Запись осуществляется атомарно через временные файлы с созданием `.bak` резервной копии.

### Горячие клавиши и управление

| Действие | Клавиша по умолчанию | Описание |
|---|---|---|
| **Поиск предмета в руке** | `V` | Запускает поиск хранилища для предмета в основной руке |
| **Открыть панель поиска** | `Ctrl + V` | Открывает окно текстового поиска по базе склада |
| **Очистить текущий маршрут** | `Escape` / `V` (повтор) | Снимает подсветку сундуков и удаляет навигационную линию |
| **Открыть настройки мода** | `Mod Menu` | Конфигурация цветов, дистанции и радиуса поиска |

### Настройки конфигурации (`config/storagefinder.json`)

```json
{
  "searchRadius": 64,
  "pathfindingMaxNodes": 2048,
  "highlightColor": "0xFF55FF55",
  "routeColor": "0xFF00AAFF",
  "showDirectionalArrows": true,
  "enableEconomyBridge": true,
  "rememberChestContents": true
}
```

### Установка и требования

- **Minecraft:** 26.1.2 или 26.2 (единый JAR);
- **Fabric Loader:** 0.19.3+;
- **Java:** 25 (`E:/Coding/Minecraft/tools/jdk-25.0.3+9`);
- **Зависимости:** Fabric API (`0.153.0+26.1.2` / `0.153.0+26.2`), Mod Menu (опционально).
- Поместите `storagefinder-1.0.3.jar` в папку `.minecraft/mods`.

### Сборка

```powershell
.\gradlew.bat clean build --warning-mode all
.\gradlew.bat clean build '-Pminecraft_version=26.2' --warning-mode all
.\gradlew.bat clean build --warning-mode all
```

Итоговый бинарный файл: `build/libs/storagefinder-1.0.3.jar`.

---

## English

### What It Is

`Storage Finder` is a flagship client-side Fabric mod for Minecraft 26.1.2–26.2 offering an intelligent in-game warehouse search engine. It indexes item frames, memorizes opened container contents (chests, barrels, shulkers, hoppers, droppers), and renders 3D collision-aware pedestrian navigation paths to target storage containers.

### Key Features

- **Hand Item Quick Search**: Hold any item and press `V` to instantly locate and navigate to the nearest chest containing matching items.
- **Permanent Search Panel**: In-game HUD text search bar with real-time autocompletion across the entire indexed storage catalog.
- **Container Memory Indexer**: Automatically records coordinates, container types, stack counts, and NBT metadata whenever you interact with storage blocks.
- **3D Collision-Aware A* Pathfinding**: Navigates through actual walkable paths, respecting doors, gates, stairs, slabs, ladders, and height drops instead of drawing clipping straight lines.
- **LTS_Server Economy Bridge**: Dynamically superimposes lowest unit prices and shop statistics directly over matching container coordinates.

### Technical Architecture & Performance

1. **Zero-Lag Background Worker**:
   - Chunk traversal, item frame spatial indexing, and A* computations run exclusively on a background daemon executor thread (`Executors.newSingleThreadExecutor`).
   - The main rendering thread only consumes pre-computed line coordinates and ARGB vertex buffers (0.0 ms render overhead).
2. **Robust Pathfinding Engine**:
   - Bounded calculation budget (up to 1200 nodes per tick) preventing freeze spikes.
   - Handles 1-block step jumps, up to 3-block safe drops, obstacle avoidance for hazard blocks (lava, campfires, open trapdoors).
3. **Atomic Persistence & Server Scoping**:
   - Storage databases are scoped per server IP (`config/storagefinder/servers/<server_ip>.json`) and singleplayer world.
   - File writes utilize atomic temporary swap files with automatic `.bak` backup retention.

### Keybindings & Controls

| Action | Default Hotkey | Description |
|---|---|---|
| **Search Held Item** | `V` | Scans for storage containing the item in main hand |
| **Open Search GUI** | `Ctrl + V` | Opens full-text search browser |
| **Clear Navigation Path** | `Escape` / `V` (toggle) | Clears container highlights and resets path rendering |
| **Open Mod Settings** | `Mod Menu` | Customize scan radius, colors, and line widths |

### Configuration Parameters (`config/storagefinder.json`)

```json
{
  "searchRadius": 64,
  "pathfindingMaxNodes": 2048,
  "highlightColor": "0xFF55FF55",
  "routeColor": "0xFF00AAFF",
  "showDirectionalArrows": true,
  "enableEconomyBridge": true,
  "rememberChestContents": true
}
```

### Installation & Compatibility

- **Minecraft:** 26.1.2 – 26.2 (single universal JAR);
- **Fabric Loader:** 0.19.3+;
- **Java:** 25;
- **Required:** Fabric API, Mod Menu (optional).
- Place `storagefinder-1.0.3.jar` into your `.minecraft/mods` directory.

### Build

```powershell
.\gradlew.bat clean build --warning-mode all
.\gradlew.bat clean build '-Pminecraft_version=26.2' --warning-mode all
.\gradlew.bat clean build --warning-mode all
```

Output JAR: `build/libs/storagefinder-1.0.3.jar`.

## Credits

Developed by LTS_Server. Licensed under the MIT License.
