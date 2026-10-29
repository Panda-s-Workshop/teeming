# Teeming

[![Build](https://github.com/Panda-s-Workshop/teeming/actions/workflows/build.yml/badge.svg)](https://github.com/Panda-s-Workshop/teeming/actions/workflows/build.yml)

**Store page:** *(not published yet)* · **Website:** *(not deployed yet)*

A Minecraft 26.3 mod for NeoForge (26.3.0.89) and Fabric (Fabric Loader 0.19.5, Fabric API 0.161.0), Java 25.

**90 new mobs across the Overworld, Nether and End, each with its own trick, a tell to read and a counter to learn.**
20 Overworld, 30 Nether and 40 End mobs, a Field Guide that fills in as you meet them, rare trophies, signature materials
with real uses and twelve counter charms. It adds no biomes, structures or dimensions, so it sits beside any pack.

> Status: in development.

## Requirements

| Loader | Needs |
|---|---|
| NeoForge | NeoForge 26.3.0.89 or newer for Minecraft 26.3 |
| Fabric | Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3 or newer |

## Configuration

The server's settings apply to everyone; clients only show the Field Guide's kill count. NeoForge keeps them in
`config/teeming-synced.toml` (one file for every world, sent to joining clients). Fabric keeps them in
`config/teeming.properties`, read once at startup: restart after a change. Both files are written with the defaults
on first start, and a newer version adds its new keys to an existing file. On Fabric a start that changes the file
(a new key, or a newer header) writes the mod's keys in the order below under the current header, and keeps every
value, the comments above a key (with that key), keys the mod doesn't read (after its own) and comments after the last
key; comments at the very top of the file are taken for the old header and replaced. A value out of range is brought
into it, with a warning in the log.

| Key | Default | What it does |
|---|---|---|
| `spawningDensity` | 1.0 | 0 to 4. Below 1 it thins natural spawns: each one that passed every other rule goes ahead with this chance (0 turns them off). Above 1 it only raises the local caps (`spawning.local_cap` times it, apex mobs and giants included); how often a Teeming mob is picked, and its share of each biome's spawns, stay the same |
| `endMainIslandAfterDragon` | true | The main island's End mobs appear only after the dragon has been killed |
| `fieldguide.killsToReveal` | 3 | Kills of a mob that reveal its tell, counter, drops and looks in the Field Guide (1 to 100) |
| `griefing.temporary_blocks` | true | False stops every mob placing even temporary blocks (the Gloomweaver raises no walls) |
| `spawning.local_cap` | 3 | At most this many of one Teeming mob type within 48 blocks (1 to 16); apex mobs and giants at 1 in 128. Both are multiplied by `spawningDensity` when it is above 1 |
| `trophies.chance` | 0.025 | The chance a player kill of an ordinary Teeming mob drops its trophy (apex mobs four times it; Looting raises it in proportion) |
| `mobs.<id>.enabled` | true | False stops that mob's natural spawns; spawn eggs and commands still work |
| `mobs.<id>.spawnChance` | 1.0 | 0 to 1, the chance a natural spawn that passed every other rule goes ahead |

## Commands

All need operator permission (level 2).

- `/teeming guide reveal <players> <mob|all>` fills in Field Guide entries; `/teeming guide reset <players> <mob|all>` clears them.
  A reveal also awards the Field Guide advancements the entries now earn (the first sighting, each dimension's
  Naturalist once all its creatures are in, and with `all` the one for revealing every creature), announced in chat
  like any advancement; a reset doesn't take them back.
- `/teeming look <entity> <look>` gives a Teeming mob one of its looks.
- `/teeming spawns <mob>` tells where and how often a mob spawns in the dimension you're in, and whether it can where you stand.
  For a mob of Teeming's own spawner whose rule needs a particular spot, it answers by that spot: a fortress or bastion
  mob outside its structure gets "no, only inside ..."; a lava-sea, fungus-cap, island-edge, underside or void mob gets
  "where its rule fits" and the spot, not a yes. A mob both on the biome lists and placed by a rule (the Aurovore, the
  Astral Augur, the Nullcantor and the Resonant Triad) gets a line naming where its rule places it, beyond the biomes
  listed.

## Data packs

Nearly everything a pack would change is data under `data/teeming/`. The exceptions are code on both loaders: the
spawn weights on Fabric, the weights and rules of the mobs Teeming's own spawner places, and the items some mechanics
drop on top of a loot table (listed under drops below).

- **Where each mob spawns:** biome tags `#teeming:spawns/<mob>`. Add a biome (or a tag such as `#c:is_forest`) to
  give a mob that biome. Mobs on the biome lists stop spawning when their tag is emptied; mobs that only Teeming's own
  spawner places (fortress, lava sea, End islands and the like) spawn wherever their rule fits when their tag is empty.
  Four mobs are both on biome lists and placed by a spawner rule: the Aurovore (bastions), the Astral Augur and the
  Nullcantor (the main island, after the dragon) and the Resonant Triad (the End's open sky). Emptying their tag stops
  them both ways; while it holds anything, their rule places them wherever it fits, whatever biomes the tag names (a
  bastion stands in four Nether biomes). To switch a mob off either way, use `mobs.<id>.enabled`. The biome lists
  read the tags once, when the server starts; Teeming's own spawner and `/teeming spawns` read them live. So after a
  `/reload` a changed tag changes the spawner's placements and the command's answers at once, and the lists only
  after a restart.
  `#teeming:share_capped` holds Nether biomes whose vanilla monster list is too small to share, where Teeming's own
  spawner places their monsters instead (by default the crimson and warped forests, and biome mods' biomes in
  `#c:is_nether_forest`, `#c:primary_wood_type/crimson` or `#c:primary_wood_type/warped`). A biome in the tag gets no
  Teeming monster from the biome lists, tuned vanilla biomes included, on both loaders: a Nether biome added to it gets
  the spawner's placements of the monsters whose tags hold it, kept to about 1 in 5 of the monsters round the player.
  That spawner doesn't run in the Overworld, so an Overworld biome added to the tag gets no Teeming monsters at all
  (the deep dark and the mushroom fields are in it by default, where no monster spawns anyway). Creatures keep their
  lists in either.
- **Biome mods' End biomes:** `#teeming:modded_end` (by default `#c:is_end` and `#minecraft:is_end`) is how they get
  the outer islands' mobs. Each outer-island list mob's tag holds it, so emptying the mob's tag takes the mob out of
  them too. The tag holds vanilla's five End biomes as well; Teeming leaves those five out wherever it reads the tag, so
  a mob still spawns only in its own vanilla End biomes. Don't add the tag (or `#c:is_end`) to another mob's tag: that
  mob would get every modded End biome, and vanilla's five End biomes too, at its rest weight (only the outer-island
  mobs whose tags hold it by default leave vanilla's five out). No End list mob spawns naturally within 1024 blocks of
  the origin (the main island's area, whatever its biome) except the main island's own mobs, once the dragon is dead. In
  a biome mod's End biome one more Teeming monster spawns only while Teeming's don't outnumber the other monsters within
  128 blocks, since the lists assume endermen in groups of four: a biome with few monsters of its own gets few of
  Teeming's, and monsters a pack adds there raise Teeming's number with them.
- **Biome mods' Nether biomes:** the open-ground Nether monsters' homes name vanilla biomes only, so a biome mod's
  Nether biome tagged only `#c:is_nether` gets 7 of the 30 Nether mobs: the Lavaleaper and the Magma Tortoise from the
  biome lists, and the Soot Manta, the three fortress mobs and the Aurovore from Teeming's spawner. The fortress mobs
  and the Aurovore come only where a nether fortress or a bastion generates in that biome, which the structures' own
  biome tags decide. One in `#c:is_nether_forest` without a wood-type tag also gets the Gleamtrap, under the share cap,
  on fungus caps with wart blocks under them; one in `#c:primary_wood_type/crimson` or `/warped` gets that forest's
  mobs, under the share cap. Add the biome to a mob's tag to give it more.
- **Spawn costs:** vanilla's soul sand valley charges each of its monsters a spawn cost (0.7, energy budget 0.15),
  which keeps them from crowding together. Teeming gives its 7 valley mobs the same: on NeoForge through
  `data/teeming/neoforge/biome_modifier/spawn_costs/soul_sand_valley.json` (replace it by id to change the numbers or
  the mobs), on Fabric in code. A mob a pack adds to the valley through its tag gets no cost, so it out-spawns the
  rest there; add it to that file's `entity_types` on NeoForge.
- **How often and in what groups (NeoForge):** each biome-list mob's spawn weight and group size are NeoForge biome
  modifiers. `data/teeming/neoforge/biome_modifier/spawns/<mob>/<biome>.json` gives it a weight tuned to one vanilla
  biome's monster list. `spawns/<mob>.json` gives it the weight it carries in the biomes its tag reaches that are not
  tuned (a biome mod's biomes), and `spawns/<mob>/beside.json` a smaller one where another mob already carries that
  biome's share. `spawns/<mob>/modded_end.json` gives an outer-island End mob its weight in biome mods' End biomes. All
  stay inside the mob's tag. Replace a file by id to change its `weight` or `count`. On Fabric these
  numbers are built into the mod, so a pack can't change them; the server can only lower a mob with
  `mobs.<id>.spawnChance`.
- **Looks:** `data/teeming/teeming/looks/<mob>/<look>.json` with `weight` (0 switches a look off), and optionally
  `scale`, `attributes` and `biomes`. Where no allowed look has weight, the mob takes its base look, even a base set to
  0: so a base switched off still appears wherever the mob's other look isn't allowed (the Rattlecoil anywhere outside
  `#c:is_desert`, a Nether forest mob outside the forest of its other look). The Field Guide counts the looks the mod
  ships, so a look switched off still counts toward "Looks seen", and a pack's new look isn't counted. A new look id
  also needs its texture in a resource pack at `assets/teeming/textures/entity/<mob>/<look>.png`, and `<look>_weak.png`
  beside it for a mob with a weak point. Without them the look draws as the missing texture.
- **Drops, recipes, advancements:** ordinary loot tables (`loot_table/entities/<mob>.json`), recipes and advancements,
  each replaceable by id. In a loot table the trophy pool rolls on the condition `teeming:trophy_chance`, and the loot
  function `teeming:trophy_look` gives the trophy the look of the mob that dropped it. Keep both, and the pool's
  `teeming:trophy_mob` component, when you replace a table. Some mechanics drop items from code, on top of the loot
  table, and a replaced table doesn't remove them: the Echo Fencer's time sand when its hourglass cracks, the Astral
  Augur's star mote for each broken channel, the Swellshade's extra void ichor for the swells waited out, the gilded
  Blackstone Scuttler's gold nuggets, the Chorus Husk's spared chorus fruit, fleece shorn from the grazers, the
  Pearlshell Snail's pearls, and what a mob carried or stole (the Ash Jackal, the Aurovore, the Dustwhirl and the
  Pearlgulp give it back). Eleven of the advancements are awarded from code (every one whose criterion is
  `minecraft:impossible`), by the criterion name `done`: a replacement must keep that criterion name, or it is never
  awarded.
- **Entity tags:** `#teeming:overworld`, `#teeming:nether`, `#teeming:end`, `#teeming:apex`, `#teeming:passive`;
  `#teeming:reflectable` (what the Prismback turns back; by default `#minecraft:arrows`, tridents, snowballs, eggs,
  the blaze's small fireballs and wind charges, not the ghast's or the dragon's fireballs),
  `#teeming:pyre_herald_allies`, `#teeming:mending_crystalling_patients`. The mod also adds its mobs to
  `#minecraft:undead` (Soulward Revenant, Ossuary Wyrm), `#minecraft:arthropod` (six mobs), `#minecraft:aquatic`
  (Stormeel) and `#c:capturing_not_supported` (the apex mobs and the linked groups: Gemini Watchers, Resonant Triad and
  Recurrent).
- **Item tags:** `#teeming:signature_materials`, `#teeming:charms`, `#teeming:aurovore_loot` (what the Aurovore
  steals), `#teeming:cold_items` (thrown items that cool a Slagheart Golem as a snowball does; default: snowball).
- **Damage types:** `#teeming:mote_swarm_scatters` holds the damage types that hit a Mote Swarm as a spreading blow,
  for triple damage (default: explosions, dragon breath, lightning; splash and lingering potions and sweeps count
  whatever the tag holds). `teeming:orrery_mote` is the part of a Grand Orrery mote's damage that goes past armor,
  because it is in `#minecraft:bypasses_armor`. `teeming:void_stain`, the Void Bloom's stain, is in
  `#minecraft:bypasses_armor`, `#minecraft:bypasses_enchantments` and `#minecraft:bypasses_shield`, so armor,
  Protection and a raised shield don't cut it; a pack that takes it out of those tags turns the stain back into a
  scratch that natural regeneration outheals.

## Build and run

JDK 25 is pinned in `gradle.properties` (`org.gradle.java.home`); the Gradle wrapper itself also needs `JAVA_HOME`
pointing at a JDK.

```bash
./gradlew build
```

```bash
./gradlew :neoforge:runGameTestServer
```

- `build` writes `neoforge/build/libs/teeming-<version>-neoforge.jar` and `fabric/build/libs/teeming-<version>-fabric.jar`.
- JUnit tests live in `common/src/test`, GameTests in `neoforge/src/main/java/com/panda/teeming/gametest`.

CI runs the build, the unit tests and the GameTests on every push.

## License

All Rights Reserved.
