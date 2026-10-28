# Teeming

[![Build](https://github.com/Panda-s-Workshop/teeming/actions/workflows/build.yml/badge.svg)](https://github.com/Panda-s-Workshop/teeming/actions/workflows/build.yml)

**Store page:** *(not published yet)* · **Website:** *(not deployed yet)*

A Minecraft 26.3 mod for NeoForge (26.3.0.89) and Fabric (Fabric Loader 0.19.5, Fabric API 0.161.0), Java 25.

**90 new mobs across the Overworld, Nether and End, each with its own trick, a tell to read and a counter to learn.**
20 Overworld, 30 Nether and 40 End mobs, a Field Guide that fills in as you meet them, rare trophies, signature drops
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
on first start, and a newer version adds its new keys to an existing file.

| Key | Default | What it does |
|---|---|---|
| `spawningDensity` | 1.0 | Multiplies how often every Teeming mob spawns (0 to 4; 0 turns natural spawning off, above 1 raises the local caps) |
| `endMainIslandAfterDragon` | true | The main island's End mobs appear only after the dragon has been killed |
| `spawning.local_cap` | 3 | At most this many of one Teeming mob type within 48 blocks (1 to 16); apex mobs and giants stay at 1 in 128 |
| `trophies.chance` | 0.025 | The chance a player kill of an ordinary Teeming mob drops its trophy (apex mobs four times it; Looting raises it in proportion) |
| `fieldguide.killsToReveal` | 3 | Kills of a mob that reveal its tell, counter, drops and looks in the Field Guide (1 to 100) |
| `griefing.temporary_blocks` | true | False stops every mob placing even temporary blocks (the Gloomweaver raises no walls) |
| `mobs.<id>.enabled` | true | False stops that mob's natural spawns; spawn eggs and commands still work |
| `mobs.<id>.spawnChance` | 1.0 | 0 to 1, the chance a natural spawn that passed every other rule goes ahead |

## Commands

All need operator permission (level 2).

- `/teeming guide reveal <players> <mob|all>` fills in Field Guide entries; `/teeming guide reset <players> <mob|all>` clears them.
- `/teeming look <entity> <look>` gives a Teeming mob one of its looks.
- `/teeming spawns <mob>` tells where and how often a mob spawns in the dimension you're in, and whether it can where you stand.

## Data packs

Everything a pack would change is data under `data/teeming/`:

- **Where each mob spawns:** biome tags `#teeming:spawns/<mob>`. Add a biome (or a tag such as `#c:is_forest`) to
  give a mob that biome. Mobs on the biome lists stop spawning when their tag is emptied; mobs that Teeming's own
  spawner places (fortress, lava sea, End islands and the like) spawn wherever their rule fits when their tag is empty.
  To switch a mob off either way, use `mobs.<id>.enabled`. `#teeming:share_capped` holds
  biomes whose vanilla monster list is too small to share, where Teeming's own spawner places their monsters instead.
- **Looks:** `data/teeming/teeming/looks/<mob>/<look>.json` with `weight` (0 switches a look off), and optionally
  `scale`, `attributes` and `biomes`.
- **Drops, recipes, advancements:** ordinary loot tables (`loot_table/entities/<mob>.json`; the trophy roll is the
  condition `teeming:trophy_chance`), recipes and advancements, each replaceable by id.
- **Entity tags:** `#teeming:overworld`, `#teeming:nether`, `#teeming:end`, `#teeming:apex`, `#teeming:passive`;
  `#teeming:reflectable` (what the Prismback turns back; it includes `#minecraft:arrows`),
  `#teeming:pyre_herald_allies`, `#teeming:mending_crystalling_patients`.
- **Item tags:** `#teeming:signature_materials`, `#teeming:charms`, `#teeming:aurovore_loot` (what the Aurovore
  steals).

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
