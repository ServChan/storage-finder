# Storage Finder

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.1.2%20%7C%2026.2-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Fabric-blue?style=flat-square&logo=fabric)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Mod Version](https://img.shields.io/badge/Version-1.0.3-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Finds nearby storage from item frames and remembered container contents, highlighting matches and drawing 3D collision-aware navigation routes.

## Русский

### Что это

`Storage Finder` — клиентский Fabric-мод для Minecraft 26.1.2–26.2, позволяющий быстро находить нужные предметы в сундуках, бочках, шалкерах и рамках склада с построением трёхмерного маршрута движения к целевому контейнеру.

### Что дает мод

- поиск по предмету в руке по нажатию горячей клавиши `V`;
- текстовый поиск по названию предметов в постоянной панели склада;
- запоминание содержимого открытых контейнеров с привязкой к координатам;
- трёхмерную подсветку сундуков и построение навигационной линии со стрелками направления (A* с учётом препятствий).

### Особенности

- фоновое индексирование сундуков без задержек основного потока рендеринга;
- безопасное сохранение индекса базы склада с защитой от повреждений (`.bak`);
- разделение баз хранения по серверам и мирам.

### Настройки

Файлы конфигурации и индексов: `config/storagefinder/`. Клавиша быстрого поиска по умолчанию — `V` (настраивается в управлении).

### Установка

1. Установите **Fabric Loader** 0.19.3+ и **Java 25**.
2. Поместите `storagefinder-1.0.3.jar` из `build/libs/` в папку `mods/`.

### Совместимость

- **Minecraft:** 26.1.2 – 26.2;
- **Fabric Loader:** 0.19.3+;
- **Java:** 25;
- **Сторона:** Клиент.

### Сборка

```powershell
.\gradlew.bat clean build --warning-mode all
.\gradlew.bat clean build '-Pminecraft_version=26.2' --warning-mode all
.\gradlew.bat clean build --warning-mode all
```

Итоговый файл: `build/libs/storagefinder-1.0.3.jar`.

---

## English

### What It Is

`Storage Finder` is a client-side Fabric mod for Minecraft 26.1.2–26.2 that locates items across storage containers and item frames, highlights matching chests, and renders 3D collision-aware navigation paths.

### What It Adds

- quick search for the item held in hand via configurable hotkey (`V`);
- interactive text-based search bar for warehouse management;
- automatic index memory of opened container contents tied to coordinates;
- 3D glowing container outlines and animated particle pathfinding route (A* with obstacle avoidance).

### Features

- background daemon thread indexer ensuring 0 frame drops;
- atomic file writes with `.bak` rollback safety;
- multi-server isolation scoping storage databases per server address.

### Settings

Data indices and config files are stored in `config/storagefinder/`. Hotkey `V` is rebindable via Minecraft Controls menu.

### Installation

1. Install **Fabric Loader** 0.19.3+ with **Java 25**.
2. Place `storagefinder-1.0.3.jar` from `build/libs/` into `.minecraft/mods`.

### Compatibility

- **Minecraft:** 26.1.2 – 26.2 (single JAR);
- **Fabric Loader:** 0.19.3+;
- **Java:** 25;
- **Environment:** Client-only.

### Build

```powershell
.\gradlew.bat clean build --warning-mode all
.\gradlew.bat clean build '-Pminecraft_version=26.2' --warning-mode all
.\gradlew.bat clean build --warning-mode all
```

Output: `build/libs/storagefinder-1.0.3.jar`.

## Credits

Developed by LTS_Server. Licensed under the MIT License.
