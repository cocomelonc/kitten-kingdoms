# Art

**Author:** cocomelonc<br>
**Copyright:** © 2026 cocomelonc (Zhassulan Zhussupov)<br>
**License:** MIT (original artwork) / CC0 1.0 (third-party tiles, see below)

Most of Kitten Kingdoms is still hand-drawn `Canvas` vector art generated at
runtime by `KittenKingdomsView.java` - the kitten, the HUD, every screen, and
most building icons. The one exception: terrain decoration (trees, rocks,
bushes, mushrooms, a water lily) and the shoreline tiles that give lakes a
rounded bank, plus three building badges (Lumber Camp, Quarry, Crystal Mine),
are third-party pixel art, loaded once as bitmaps by `TerrainSprites.java`.

## Third-party tiles

**Source:** Kenney's ["Roguelike/RPG pack"](https://kenney.nl/assets/roguelike-rpg-pack)
(kenney.nl), the same CC0 sheet already used by this author's
[crystal-trail](https://github.com/cocomelonc/crystal-trail) project.<br>
**License:** CC0 1.0 Universal - free for personal and commercial use, no
attribution required. Full text: [`third_party/kenney/KENNEY_LICENSE.txt`](third_party/kenney/KENNEY_LICENSE.txt).<br>
**Files used** (`app/src/main/res/drawable-nodpi/`): `tree_green`, `tree_dark`,
`tree_fruit`, `pine_green` (forest), `rock_grey_big`, `rock_grey_mid` (stone
outcrop), `bush_round`, `mushroom_red`, `mushroom_brown` (grass), `water_lily`
and the 13 water-edge/inner-corner/plain shoreline tiles (water), plus
`badge_lumber_camp`, `badge_quarry`, `badge_crystal_mine` (building icons) -
all baked once from the source sheet/sprites to their final on-screen size,
not resized at runtime.

Not used from that same pack: its graveyard props (crosses, tombstones), dead
sapling, cactus, and statue - not a fit for a calm, no-conflict kitten
kingdom.

## Original artwork

Everything else - the kitten, buildings without a badge above, the world
chrome, and every screenshot in `art/` - is original vector art, drawn at
runtime, no external image generator or bitmap involved. Available under the
repository's [MIT License](LICENSE).
