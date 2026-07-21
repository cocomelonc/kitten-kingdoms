# Kitten Kingdoms

![Kitten Kingdoms cover](art/kitten-kingdoms-cover.png)

**Author:** `cocomelonc`<br>
**Copyright:** © 2026 cocomelonc (Zhassulan Zhussupov)

Kitten Kingdoms is a tiny, calm Android game about growing a kitten
settlement one gentle turn at a time. Walk a little kitten across a
continuous 96x96-tile world, uncover the land as you go, and build a small
kingdom on the ground you've discovered: gather six resources, put up eleven
kinds of buildings, and research a ten-technology tree. There are no ads,
accounts, purchases, trackers, network calls, timers, lives, or game-over
screens - a kingdom can only grow, never fail.

The game starts in English and includes an in-game `EN / RU` language switch.
Both Latin and Cyrillic use the same bundled Nunito typeface, so typography is
consistent on every Android device.

### Screenshots

| English | Русский |
|---|---|
| ![English title screen](art/runtime-title.png) | ![Russian title screen](art/runtime-title-ru.png) |

The pannable, pinch-zoomable world viewport sits below a fixed resource bar;
fog of war reveals permanently as the kitten walks, and buildings can be
placed on any discovered tile once you can afford them.

![World view with fog of war and a building under construction](art/runtime-level.png)

Build Menu is a modal card over the world; Research and How to Play are their
own full screens (real Android Activities, so the system Back gesture returns
you to the kingdom exactly as it left it):

![Build menu with buildable, locked, and unaffordable buildings](art/runtime-build-menu.png)
![Research screen showing the ten-node technology DAG](art/runtime-tech-tree.png)

The pause card uses the same EN/RU switch as every other screen:

![Russian pause card](art/runtime-pause-ru.png)

### How a kingdom grows

- **Explore**: drag to pan the map, pinch to zoom, tap a tile to walk the
  kitten there. Fog of war reveals permanently in a radius around every tile
  the kitten visits - once seen, land stays known.
- **Build**: once a tile is discovered, open *Build* and place any building
  you can afford there instantly; no need to walk the kitten to the site
  itself. Some buildings need to be adjacent to specific terrain (a Fishing
  Dock needs nearby water, a Lumber Camp nearby forest, a Quarry a nearby
  stone outcrop).
- **Produce**: each building yields resources - Fish, Wood, Stone, Catnip,
  Yarn, or Crystals - every turn, and some consume a little upkeep. A building
  that can't afford its own upkeep simply idles for that turn; nothing is
  ever destroyed.
- **Grow**: population grows toward each building's housing capacity as long
  as there's enough Fish; if there isn't, growth just pauses - it never
  reverses.
- **Research**: tech points accumulate every turn from the Town Hall and any
  Scholar's Dens. Opening *Research* leaves the world view for its own screen
  showing the ten-node technology tree; pick a target node and once enough
  points have banked, it unlocks and any leftover points carry to the next
  choice. The system Back gesture/button returns you to the kingdom.
- **Notice**: a small banner announces when a building finishes construction
  or a technology is researched.
- **End Turn**: the whole economy - production, upkeep, population, and
  research - advances only when you tap *End Turn*. Exploration and building
  placement happen anytime in between, independent of the turn clock.

Rabbits, hedgehogs, ducklings, and bees wander the discovered land -
purely decorative background life with no AI and no interaction with the
economy, just so the kingdom doesn't feel empty.

### Why it is deliberately small

- One MVP scope: a single settlement, one continuous map, six resources,
  eleven buildings, and a ten-node tech tree - trade and diplomacy with other
  kingdoms, and an army, are intentionally left out (the latter permanently -
  a kingdom can only grow, never fail, and that doesn't mix with combat).
- No engine: a single hardware-accelerated Android `View` renders the world,
  camera, HUD, and every modal screen; only visible tiles are drawn each
  frame.
- Zero runtime dependencies, matching the rest of the series: the kingdom
  save is a small hand-written versioned binary format over plain
  `java.io` streams, not a database library. The format is versioned; a save
  written by an older, incompatible version of the app is rejected cleanly
  and replaced by a fresh kingdom rather than crashing.
- Terrain regenerates deterministically from a fixed seed and is never saved;
  only what the player has actually discovered or built is persisted.
- English and Russian resources bundled in every APK/AAB.
- Original procedural chimes and calm background music; no sampled audio
  files or codec dependency.

### Android configuration

| Setting | Value |
|---|---:|
| Application ID | `com.cocomelonc.kittenkingdoms` |
| Minimum SDK | 33 (Android 13) |
| Target SDK | 36 (Android 16) |
| Compile SDK | 36 |
| Java | 17 |
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |

Because the application contains no native ELF libraries, Android's 16 KB
memory-page compatibility requirement does not apply to project code. The
verification script also checks that no `.so` file enters the APK.

`minSdk` controls the oldest Android release that can install the app, while
`targetSdk` opts the app into the behavior rules of that Android generation.
Kitten Kingdoms declares `minSdk 33` (Android 13) rather than the rest of the
series' `minSdk 26` - every code path this project needs from `Build.VERSION.
TIRAMISU` and the splash-screen API is available unconditionally, so
`MainActivity` carries no legacy branches at all. See the official Android
[`<uses-sdk>` documentation](https://developer.android.com/guide/topics/manifest/uses-sdk-element).

The complete debug build was clean-installed and exercised on a Pixel 7
emulator running Android 16/API 36: English and Russian switching (including
across `TechTreeActivity` and `HelpActivity`), Cyrillic font rendering, camera
pan and zoom, tap-to-walk pathing, fog-of-war reveal, building placement
(including tech-gated, terrain-adjacency-gated, and unaffordable rejection
with an on-screen explanation), turn resolution and resource production,
opening Research as its own Activity and returning the chosen technology via
`onActivityResult`, the New Kingdom confirmation when a save already exists,
save-on-background and continue-on-relaunch, a save from an older format
version being rejected cleanly rather than crashing, building-complete and
technology-unlocked notification banners, the Crystal Mine's tech-and-terrain
gate, the eleven-building Build Menu's four-row layout, ambient wildlife
spawning and rendering only on discovered tiles, app backgrounding, and safe
resume-to-pause. Automated tests also prove full terrain connectivity from
the starting tile and that the technology tree is a valid, fully reachable,
acyclic graph.

### Build

Install JDK 17 and Android SDK Platform 36, then run:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME=/path/to/jdk-17
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a Play-ready Android App Bundle artifact:

```bash
./gradlew bundleRelease
```

The release AAB is unsigned. Configure your own upload key outside the
repository; never commit a keystore or its passwords.

### Verification

```bash
./scripts/verify_android.sh
```

It runs unit tests, strict lint, builds the APK, verifies its signature and ZIP
alignment, confirms `minSdk=33` / `targetSdk=36`, and rejects unexpected native
libraries.

The unit tests validate every content registry (terrain, resources,
buildings, technologies), prove the technology tree is acyclic and fully
reachable from its root, flood-fill the generated terrain for full
connectivity from the kitten's starting tile, drive the turn-based economy
through production, storage caps, population growth, tech-gated and
terrain-gated building placement, and upkeep shortfalls, and round-trip the
save format through a byte stream.

### Controls

- Main menu: *Continue* (once a kingdom exists), *New Kingdom* (confirms
  before replacing a saved kingdom), and *How to Play*.
- Drag: pan the map. Pinch: zoom, from 0.6x to 1.8x.
- Tap a tile with no building selected: the kitten walks there.
- Tap *Build*, choose a building, then tap any discovered, eligible tile to
  place it.
- Tap *Research* to open the technology screen, then an available
  (non-greyed) node to set it as the active research target; Back returns to
  the kingdom.
- Tap *End Turn* to resolve production, upkeep, population growth, and
  research for the whole kingdom.
- Top-right pause button or Android Back: pause (or cancel a build selection
  first, if one is open). Pause also offers a *Main Menu* button.
- `EN / RU`: switch language on the title or pause screen.

### Project layout

```text
app/src/main/java/com/cocomelonc/kittenkingdoms/
  MainActivity.java       edge-to-edge Android host, lifecycle, and activity-result glue
  KittenKingdomsView.java camera, tile culling, HUD, main menu, Build modal, and input
  TechTreeActivity.java   hosts the Research screen, returns the chosen tech via Intent
  TechTreeView.java       the nine-node technology DAG, rendered full screen
  HelpActivity.java       hosts the static "How to Play" screen
  HelpView.java           three short Explore/Build/Research sections, no scrolling
  KingdomWorld.java       turn-based rules, kitten pathing, save/load glue
  WorldMap.java           96x96 deterministic terrain, fog of war, occupancy
  TerrainType.java        data-driven terrain kinds (grass, forest, water, ...)
  ResourceType.java       data-driven stockpiled resources
  BuildingType.java       data-driven building costs, output, and gates
  TechNode.java           data-driven technology tree (a DAG, not a line)
  PlacedBuilding.java     mutable building-instance placement state
  WildlifeCritter.java    decorative background creature: no AI, just wanders
  TurnMath.java           stateless per-turn economy formulas
  KingdomSaveData.java    plain save/load transfer object
  KingdomSerializer.java  zero-dependency versioned binary save format
  AudioEngine.java        tiny procedural chime synthesizer
  MusicEngine.java        calm original procedural background music
app/src/test/             registry, reachability, economy, and save-format tests
art/                      open-source cover and its generation notes
third_party/nunito/       exact SIL OFL license for the bundled font
scripts/                  reproducible Android verification
```

### Privacy and children

The app is intentionally offline and does not collect or transmit data. See
[PRIVACY.md](PRIVACY.md). If you publish a modified build with analytics,
advertising, accounts, or network services, its privacy declarations and
Google Play Families answers must be updated.

### License

Project source and original project artwork are available under the MIT
License. The original sound effects and music are documented in
[AUDIO.md](AUDIO.md). Nunito remains under the SIL Open Font License 1.1; see
[`third_party/nunito/OFL.txt`](third_party/nunito/OFL.txt).

Kitten Kingdoms was created by **cocomelonc**. The author and copyright notices
must remain in copies and substantial portions of the project as required by
the MIT License. See [AUTHORS.md](AUTHORS.md) and [LICENSE](LICENSE).

Contributions and translations are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

---

### Русский

Kitten Kingdoms - маленькая спокойная Android-игра о том, как растить
кошачье поселение ход за ходом. Котёнок гуляет по цельной карте 96x96 клеток,
открывая землю по пути, а на открытых клетках можно строить: добывать шесть
видов ресурсов, возводить одиннадцать типов зданий и исследовать дерево из
десяти технологий. Здесь нет рекламы, регистрации, покупок, аналитики, сети,
таймеров, жизней и экрана проигрыша - королевство может только расти. По
открытой земле бродят кролики, ежи, утята и пчёлы - чисто декоративные, без
логики и влияния на экономику.

Игра запускается на английском; язык можно в любой момент переключить на
русский кнопкой `EN / RU`. Один и тот же встроенный шрифт Nunito используется
для латиницы и кириллицы. Сборка и проверка описаны выше; основной артефакт -
обычный Android-проект с `targetSdk 36` и `minSdk 33`.
