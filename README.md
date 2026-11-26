# LightSkies Utils

This is a client-side Fabric mod for the LightSkies skymining server (`play.lightskies.gg`). It targets **Minecraft 26.3**, **Fabric Loader 0.20.1+** and **Fabric API 0.166.2+26.3**, and needs Java 25.

## Features

| Feature | How it works |
|---|---|
| **Mining HUD** | Counts the blocks you break (client break event). Shows blocks/min for the last 60 s, the session average, money/h and tokens/h. Money and tokens come from chat regexes. |
| **Quest tracker** | Reads quest progress from chat, the action bar, titles and the sidebar scoreboard. Shows a progress bar for each quest. You can also add quests by hand. |
| **Relic locator** | Finds relic blocks (set per dimension) and relic entities (custom-name regex) near you. The HUD shows distance, a direction arrow and ▲/▼ height. A box is drawn in the world. Also tracks "relic found 3/10" messages. |
| **Boss tracker** | Records boss spawns and deaths per dimension from chat or titles, plus "blocks until boss" progress. Shows bosses alive near you. If you set respawn timers, it shows an estimated time to the next spawn. Spawn history is saved between sessions. |
| **Treasure highlighting** | Draws translucent boxes on treasure blocks (chests and barrels by default) and on named treasure entities. Plays a ping when a new one comes into range. |
| **Rare-drop summary** | Picks up drop messages from chat, and also rare items appearing in your inventory (matched by item id or name keyword). Keeps a recent list on the HUD, plus session and all-time totals (`/lsu drops`, `/lsu drops all`). |

The mod does nothing unless you're connected to a server whose address contains `lightskies`. You can change this with `onlyOnLightSkies` / `serverAddressContains`.

## Keys (rebindable in Controls → LightSkies Utils)

- `H` toggles the HUD
- `J` toggles the treasure/relic highlights
- `K` cycles the HUD page: All → Mining → Quests → Bosses → Locator → Drops

## Commands

```
/lsu                          help
/lsu hud | highlights | page  toggles
/lsu mining | bosses | drops [all]
/lsu quest add <max> <name> | quest remove <name> | quest clear
/lsu reset <mining|quests|bosses|drops|relics|all>
/lsu scan                     force a rescan of nearby blocks
/lsu reload                   reload config/lightskies_utils.json
/lsu debug                    log every server message to latest.log as "[LSU msg] [channel] text"
/lsu test <message>           feed a fake server message through all trackers (works offline)
/lsu sidebar                  print the sidebar lines the mod can read
```

## Matching the real LightSkies messages (do this first)

The default regexes are **guesses**. Every trigger lives in `config/lightskies_utils.json` (the file is created on first launch). To tune them:

1. Join LightSkies and run `/lsu debug`.
2. Do the thing you want to match: mine, finish a quest step, spawn a boss, get a rare drop, find a relic.
3. Copy the exact lines from `logs/latest.log` (colour codes are already stripped).
4. Edit the regex in the config, run `/lsu reload`, then check it with `/lsu test <the line>`.

Named groups each tracker reads:

| Config list | Required groups | Optional groups |
|---|---|---|
| `mining.moneyPatterns` / `tokenPatterns` | `amount` | `multiplier` (k/m/b/t) |
| `quests.progressPatterns` | `name`, `current`, `max` | — |
| `quests.completePatterns` / `startPatterns` | `name` | `max` (start) |
| `bosses.spawnPatterns` / `deathPatterns` | `boss` | `dimension` (defaults to the dimension you're in) |
| `bosses.progressPatterns` | `current`, `max` | `boss`, `dimension` |
| `relics.foundPatterns` | `name` | `current`, `max` |
| `drops.chatPatterns` | `item` | `amount`, `rarity` |

Other settings to fill in:

- `relics.blockIdsByDimension`: for example `{"minecraft:the_nether": ["minecraft:respawn_anchor"], "*": ["minecraft:beacon"]}`. Use `/lsu debug` together with F3 to find dimension ids, since LightSkies may use custom ones like `lightskies:crystal_caves`.
- `relics.entityNamePatterns` / `treasure.entityNamePatterns` / `bosses.bossEntityNamePatterns`: regexes matched against entity custom names (armor-stand holograms, display entities, named mobs).
- `bosses.respawnMinutes`: for example `{"ancient golem": 45}` (lower-case boss name).
- `bosses.dimensionNames`: friendly names for custom dimension ids.
- `drops.rareItemIds` / `rareNameKeywords`.
- `treasure.color` / `relics.color`: ARGB ints (for example `0x6000E5FF` = 1610671615).

## Building

You need JDK 25. Run:

```
./gradlew build
```

The jar ends up in `build/libs/lightskies-utils-1.0.0.jar`. Put it in `.minecraft/mods` next to Fabric API. `./gradlew runClient` starts a dev client.

### Notes for whoever compiles it first

The code was written against Fabric API's 26.3 source (HUD `GuiGraphicsExtractor`, `LevelRenderEvents`, `KeyMappingHelper`, `ClientCommands`, Mojang names). It has **not yet been compiled against the 26.3 game jar**. If something doesn't match, these are the most likely places:

- `tracker/SidebarReader.java`: the scoreboard API (`listPlayerScores`, `PlayerScoreEntry.ownerName()`, `PlayerTeam.formatNameForTeam`). If it breaks, you can delete the class and the `readSidebar` call.
- `render/HighlightRenderer.java`: `RenderTypes.debugFilledBox()` and `submitCustomGeometry`. This copies Fabric's own `LevelRenderEventsTests`. Boxes are depth-tested, so they don't show through walls. The HUD arrow/distance list is the locator that works through walls.
- `scan/Locator.java`: `level.getMinY()`, `level.getHeight()`, `level.isLoaded(pos)`.

## Performance

The block scanner checks `scan.blocksPerTick` blocks each tick (25k by default) inside a 32×24 radius cube. It never touches unloaded chunks. The scan restarts around you after each full pass, about every 8 ticks with the defaults.
