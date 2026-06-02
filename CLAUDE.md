# CLAUDE.md — Dimensional Inventories Extension: Warp

Full context for AI-assisted development of this project.

## What this project is

A Fabric mod extension for [Dimensional Inventories](https://github.com/Thomilist/dimensional-inventories/tree/26.1) targeting **Minecraft 26.1.2** (Java 25). It adds `/warp` and `/diminv warp` commands that teleport players between dimensions, returning them to their last known position in that dimension.

The companion extension to study for patterns is [dimensional-inventories-extension-trinkets](https://github.com/Thomilist/dimensional-inventories-extension-trinkets/tree/1.21.1) (targets MC 1.21.1, so some APIs differ).

## Key dependencies

| Dependency | Version | Notes |
|---|---|---|
| Minecraft | 26.1.2 | Uses Mojang official mappings (no `mappings` line in build.gradle) |
| Fabric Loader | 0.19.2 | |
| Fabric API | 0.148.2+26.1.2 | |
| Fabric Loom | 1.16.3 | SNAPSHOT resolves to 1.16.3 |
| Gradle | 9.5.1 | |
| Dimensional Inventories | 2.2.1+26.1 | Main mod; loaded from Modrinth or local `../dimensional-inventories/build/libs/` |
| Gson | — | Transitive from main mod; used for JSON persistence |

## Build quirks for MC 26.1 / Loom 1.16

- **No `mappings` line** in `build.gradle` — Loom 1.16 defaults to official Mojang mappings.
- **No `modImplementation`** for `fabric-loader` or `fabric-api` — use `implementation` (same as the main mod). `modImplementation` is only used for the main mod dependency.
- Build: `.\gradlew.bat build` — output in `build/libs/`.

## MC 26.x API changes vs older versions

These tripped up initial development; double-check if adding new features:

| What | Old (1.21.x) | New (26.x) |
|---|---|---|
| `TeleportTransition` package | `net.minecraft.world.entity` | `net.minecraft.world.level.portal` |
| Teleport method | `player.teleportTo(TeleportTransition)` | `player.teleport(TeleportTransition)` |
| Player's current level | `player.serverLevel()` | `(ServerLevel) player.level()` |
| World spawn position | `level.getSharedSpawnPos()` | `level.getRespawnData().pos()` |
| Dimension identifier | `dim.location()` | `dim.identifier()` |

`TeleportTransition` constructor (simplest overload):
```java
new TeleportTransition(ServerLevel, Vec3 pos, Vec3 deltaMovement, float yRot, float xRot, PostTeleportTransition)
```

## Project structure

```
src/
  main/java/net/thomilist/dimensionalinventories/extension/warp/
    DimensionalInventoriesExtensionWarp.java   — ModInitializer; registers all events and the command
    WarpPositionStore.java                     — per-player, per-dimension position storage (memory + JSON)
    command/
      WarpCommand.java                         — registers /warp and /diminv warp via Brigadier
  main/resources/
    fabric.mod.json
    dimensional-inventories-extension-warp.mixins.json   — empty (no mixins yet)
  client/java/.../extension/warp/client/
    DimensionalInventoriesExtensionWarpClient.java       — empty client init
  client/resources/
    dimensional-inventories-extension-warp.client.mixins.json
```

## How position memory works

1. `ServerTickEvents.END_SERVER_TICK` — snapshots every online player's current position+dimension into an in-memory cache each tick.
2. Before calling `player.teleport()` in `WarpCommand` — manually snapshots exact current position (overrides tick cache for precision).
3. `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` — fires after any dimension change (portal or `/warp`). Reads the cache for the origin dimension and writes to the persistent store.
4. `ServerPlayConnectionEvents.JOIN` — loads the player's JSON file into memory when they connect.
5. Data is saved to `<world>/dimensional-inventories-extension-warp/<uuid>.json` after every write.

On warp: if a stored position exists for the target dimension → use it. Otherwise → fall back to `level.getRespawnData().pos()` (world spawn) projected to surface height.

## Commands

Both trees share the same Brigadier node (no logic duplication):

```
/warp <dimension>                     — teleport self (permission level 4)
/warp player <player> <dimension>     — teleport another player
/diminv warp <dimension>              — same, merged into main mod's /diminv tree
/diminv warp player <player> <dimension>
```

## Main mod API surface used

```java
// DimensionalInventoriesExtension — base class for module-based extensions (not used here;
// this extension is command-only and implements ModInitializer directly)

// ModProperties — reads name/version/authors from fabric.mod.json at runtime
new ModProperties("dimensional-inventories-extension-warp")
```

The main mod's inventory swap logic is **not called directly** — it fires automatically through its own `AFTER_PLAYER_CHANGE_LEVEL` listener whenever a player changes dimension.

## What to do next (ideas)

- Permission node per-player (allow non-ops to warp to specific dimensions)
- `/warp back` — warp to the previous dimension
- Cooldown between warps
- Config file for allowed dimensions or permission overrides
