# InvSort

[![Build](https://github.com/YOUR_USERNAME/invsort/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/invsort/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A lightweight [Paper](https://papermc.io/) plugin that sorts your inventory or any chest with a **double-click** — no commands, no config, no fuss.

---

## Features

- **Double-click to sort** — works on the hotbar, main inventory, and external containers (chests, barrels, etc.).
- **Smart stack merging** — identical items are merged before sorting, then split back into legal max-stack sizes.
- **Category ordering** — items are grouped by category (ores, wood, stone, then everything else), then sorted A → Z by material name, with the largest stacks placed first within each group.
- **Meta-aware** — enchanted books, named items, and other items with unique metadata are never merged with plain items of the same type.
- **Safe interaction** — sorting is cancelled if the cursor is holding an item to prevent any duplication edge cases.
- **Zero configuration** — drop it in and it works.

---

## How to Use

| Action | Effect |
|---|---|
| Double-click a **hotbar** slot | Sorts the 9 hotbar slots only |
| Double-click a **main inventory** slot (rows 2–4) | Sorts the 27 main inventory slots only |
| Double-click inside a **chest / container** | Sorts the entire container |

> **Note:** Double-clicking while holding an item on your cursor does nothing — pick up the item first, then double-click an empty hand.

---

## Sorting Behaviour

1. All non-empty items in the target region are collected.
2. Identical stacks (same material + same item meta) are merged together.
3. Merged totals are split back into stacks no larger than the item's max stack size.
4. Stacks are sorted by **category** (ores → wood → stone → other), then **alphabetically by material name**, then by **amount descending** within each group.
5. Sorted items are placed back from slot 0 / the first slot of the region onward.

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | [Paper](https://papermc.io/downloads/paper) |
| Minecraft / Paper API | 26.1 (Minecraft 1.21.x) |
| Java | 17+ |

---

## Installation

1. Download the latest `invsort-*.jar` from the [Releases](https://github.com/YOUR_USERNAME/invsort/releases) page.
2. Place the JAR in your server's `plugins/` directory.
3. Restart (or `/reload confirm`) your server.
4. No configuration needed — the plugin is ready to use.

---

## Building from Source

```bash
git clone https://github.com/YOUR_USERNAME/invsort.git
cd invsort
mvn package
```

The built JAR will be at `target/invsort-1.0.0.jar`.

---

## License

This project is released under the [MIT License](LICENSE).
