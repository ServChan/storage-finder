# Storage Finder — User Guide

Mod version: 1.2.2 · Minecraft 26.3 · Fabric · client-only

[Русская версия](USER_GUIDE_RU.md)

## Contents

1. [What the mod does](#what-the-mod-does)
2. [Installation](#installation)
3. [Getting started](#getting-started)
4. [How the mod learns what is where](#how-the-mod-learns-what-is-where)
5. [Searching](#searching)
6. [Highlights and routes](#highlights-and-routes)
7. [HUD](#hud)
8. [Statistics and index](#statistics-and-index)
9. [Prices from LTS_Server Economy](#prices-from-lts_server-economy)
10. [Settings](#settings)
11. [Macro edition](#macro-edition)
12. [Files](#files)
13. [FAQ](#faq)
14. [Building from source](#building-from-source)

## What the mod does

On a big base it is hard to remember which of a hundred chests holds the diamonds. Storage Finder remembers the contents of the storage you open and later shows where to look for an item:

- it highlights matching chests, barrels and shulker boxes;
- it plans a walking route to them around obstacles;
- it reads storage labels, meaning items in item frames on a chest;
- it shows a list of active searches on screen.

The normal edition is passive: it never opens anything for you and remembers only what you opened yourself. The server does not need it.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) `0.19.3` or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3 into the `mods` folder.
3. Put `storage-finder-1.2.2.jar` into `mods` (see [Building](#building-from-source)).
4. Optional: [Mod Menu](https://modrinth.com/mod/modmenu) for the settings screen, and LTS_Server Economy `1.1.0+` for prices in the HUD.

Java 25 is required.

## Getting started

1. Walk through your storage and open the chests you use. The mod remembers each one: chat says "Storage remembered: N item types".
2. Take the item you want to find in your hand and press right `Alt`.
3. Matching storage within 8 chunks lights up, routes lead to it, and a search line appears at the bottom right.
4. Take the item out and the search ends by itself. Or press right `Alt` again with the same item.

With an empty hand, right `Alt` opens the text search screen.

## How the mod learns what is where

- **Opened storage.** Chests (single and double), barrels and shulker boxes are recorded once the server sends their contents. Plugin menus with an unusual number of slots are not recorded.
- **Changes.** If you put something in or take something out, the record is updated when you close the storage.
- **Item frames.** An item in a frame on a storage block counts as a label of its contents.
- **Freshness.** Each record shows how long ago it was updated.
- The index is kept separately for every server and every singleplayer world.

## Searching

### By the item in your hand

Right `Alt` with an item in hand starts a search for it; pressing again with the same item removes it. Several items can be searched at once, each in its own colour.

### Text search

Right `Alt` with an empty hand opens "Find an item in storage".

- Search by the name in your current language, by ID (`oak_log`) or by tag (`#logs`).
- Items from storage you have opened come first, with how many you have ("×64 in storage").
- Click or `Enter` starts a search.
- Right click or `Shift+Enter` **pins** a search so it never ends by itself.
- An active search has a check mark; click it to remove it.
- Enchanted books are searched by enchantment.
- "Clear search" removes every search; "Statistics" and "Index" open their screens.

### When a search ends

The Finish a search setting:

| Value | What happens |
|---|---|
| when taken (default) | the search ends as soon as you take the item out of storage |
| when opened | as soon as you open storage that holds it |
| never | only by hand |

Pinned searches never end by themselves.

## Highlights and routes

- Storage holding the item is highlighted in the search colour.
- **Routes** are planned with A* over real blocks: stairs and slabs, one-block steps up, safe drops of up to 3 blocks, swimming, doors and gates. Lava, campfires and open trapdoors are avoided.
- Each search plans up to 3 routes to the nearest storage (up to 5 possible).
- Pathfinding has a node budget per tick (800 by default), so the game does not stall even on big bases.

## HUD

At the bottom right is the list of active searches:

- the item icon and name, how many were found and in how many storage blocks;
- the walking route length in metres;
- the data age ("old") and how many matches came from item frames;
- "routing…" while a route is being planned, and "no route" with the reason when it cannot be reached.

Change its look in "HUD settings…": position, offsets, compact mode, distance, data age, row count, background opacity. "Drag placement…" opens a preview where you drag the HUD.

## Statistics and index

- **Statistics** shows what is stored where, summed up by item.
- **Index** lists the remembered storage blocks:
  - a Current world / All worlds filter and a search by coordinates or world;
  - "Delete selected" forgets one storage block;
  - "Clear current world" forgets every storage block of this world (after confirmation);
  - "Export snapshot" saves the index as JSON.

## Prices from LTS_Server Economy

With the LTS_Server Economy mod `1.1.0` or newer installed, holding left `Alt` makes the HUD show the lowest and average price per item and the number of shops on the server for the searched items.

## Settings

Open **Mod Menu → Storage Finder → settings icon**. Changes apply with Save; leaving with unsaved changes asks whether to discard them. The reset button on a row restores its default, and Defaults resets everything.

### Main

| Setting | Default | What it does |
|---|---|---|
| Mod enabled | on | the main switch |
| Remember containers | on | record the contents of opened storage |
| Search item frames | on | an item in a frame on storage labels its contents |
| Search remembered contents | on | search the remembered contents |
| Storage highlights | on | highlight found storage |
| Build routes | on | plan paths to it |
| Avoid hazards | on | avoid lava, campfires and open trapdoors |
| Notifications | on | chat messages such as "Storage remembered" |
| Shulker preview on Shift | on | with Shift, a shulker box tooltip shows its slots like the inventory |
| Finish a search | when taken | see [When a search ends](#when-a-search-ends) |
| Radius | 8 chunks (128 blocks) | where to look for storage |

### HUD settings

| Setting | Default |
|---|---|
| HUD position | bottom right |
| Horizontal / vertical offset | 0 / 48 |
| Compact mode | off |
| Show distance | on |
| Show data age | on |
| Maximum visible rows | 6 |
| Background opacity | 72% |

### Route settings

| Setting | Default | Range |
|---|---|---|
| Allow swimming routes | on | |
| Route through doors and gates | on | |
| Routes per search | 3 | 1–5 |
| A* nodes per tick | 800 | 200–2000 |

## Macro edition

The build puts a second file next to the first: `storage-finder-1.2.2-macro.jar`. It is the same mod plus **auto-peek**:

- while you walk past, it opens visible chests, barrels and shulker boxes within reach one by one, reads their contents without a screen and closes the menu a few ticks later;
- it never turns the camera, and the opening sound is muted;
- a storage block is re-read at most once every 5 minutes; trapped chests and ender chests are left alone;
- auto-peek waits while you sneak, use the mouse buttons or have a menu open;
- `F8` turns auto-peek on and off; its parameters are in `config/storage_finder_macro.json`.

**Important:** the server sees ordinary chest openings. On public servers this may count as a macro and get you banned. Use the macro edition only where it is allowed, and install the normal edition everywhere else. Install only one of the two files.

## Files

| Path | Contents |
|---|---|
| `config/storage_finder.json` | settings |
| `config/storage_finder_index.json` | the storage index, per server and world |
| `config/storage_finder_macro.json` | auto-peek settings (macro edition only) |

Files are written atomically through a temporary file with a `.bak` backup.

## FAQ

**The search finds nothing.** The mod only knows storage you have opened. Open the chests once and they will be searchable.

**A chest on a plugin server is not remembered.** If a plugin shows a menu with an unusual number of slots, the mod does not record it, so contents are never mixed up.

**"No route".** The storage cannot be reached on foot over known blocks: it is behind a wall, high up or past lava. The highlight stays.

**The game stutters on a huge base.** Lower A* nodes per tick and Routes per search.

**The data is out of date.** Open the storage again and the record is updated. Freshness shows in the HUD and the index.

## Building from source

JDK 25 is required.

```powershell
git clone https://github.com/ServChan/storage-finder.git
cd storage-finder
.\gradlew.bat build
```

On Linux and macOS use `./gradlew`. `build/libs/` gets `storage-finder-1.2.2.jar` and `storage-finder-1.2.2-macro.jar`.
