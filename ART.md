# Art

**Author:** cocomelonc<br>
**Copyright:** © 2026 cocomelonc (Zhassulan Zhussupov)<br>
**License:** MIT (original artwork) / CC0 1.0 (Kenney tileset)

Kitten Kingdoms uses one visual language for its entire playable world: the
same 16px Kenney Roguelike/RPG sheet used by `crystal-trail`. Ground, forest
floor, hills, stone, every shoreline angle, vegetation, rocks, water
decoration, and world-map settlement markers are sliced from that one
sheet by `TerrainSprites.java` and enlarged 4x with nearest-neighbour scaling.
There are no flat Canvas terrain cells or procedurally drawn shore strips.

## Kenney terrain and props

**Source:** Kenney's [Roguelike/RPG pack](https://kenney.nl/assets/roguelike-rpg-pack)<br>
**License:** CC0 1.0 Universal — free for personal and commercial use, with
no attribution requirement.<br>
**License text:** [`third_party/kenney/KENNEY_LICENSE.txt`](third_party/kenney/KENNEY_LICENSE.txt)<br>
**Runtime sheet:** `app/src/main/res/drawable-nodpi/roguelike_16.png`

The old individually extracted runtime PNGs were removed, so the packaged app
cannot accidentally mix them with a different terrain renderer.

## Original pixel art

The four-direction kitten walk cycle, two-frame rabbit, hedgehog, duckling and
bee animations, and eleven building icons are original project artwork:

- editable sources: `art/source/kitten_walk.svg`, `art/source/wildlife.svg`,
  `art/source/building_icons.svg`
- runtime sheets: `drawable-nodpi/kitten_walk.png`, `drawable-nodpi/wildlife.png`,
  `drawable-nodpi/building_icons.png`
- license: MIT, the same as the project code

Every building has its own economic silhouette: logs and an axe for the
Lumber Camp, a pier and boat for the Fishing Dock, rock and pickaxe for the
Quarry, crop rows for the Catnip Farm, yarn and spindle for the Weaver's
Cottage, a barn and crate for storage, an open book for scholarship, and a
cave with crystals for mining. They intentionally share the terrain palette
and 2x pixel scale without reusing ambiguous terrain props as building art.

The adaptive launcher icon uses the same kitten palette and pixel proportions.
Interface cards, typography, relationship routes, progress bars, shadows, and
other UI chrome are drawn at runtime because they must scale cleanly and are
interface elements rather than world tiles.
