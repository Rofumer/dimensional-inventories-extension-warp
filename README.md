# Dimensional Inventories Extension: Warp

An extension for [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories) that adds warp commands to teleport players between **dimension pools**.

When a player warps to a pool, they are returned to the exact dimension, coordinates, and camera angle where they last left that pool. Inventory switching is handled automatically by Dimensional Inventories.

## Requirements

- Minecraft 26.1.x
- [Fabric Loader](https://fabricmc.net/) 0.19.2+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories) 2.2.1+26.1

## Commands

| Command | Permission node | Default |
|---|---|---|
| `/warp <pool>` | `diminv.warp.self` | OP level 4 |
| `/warp player <player> <pool>` | `diminv.warp.others` | OP level 4 |
| `/diminv warp <pool>` | `diminv.warp.self` | OP level 4 |
| `/diminv warp player <player> <pool>` | `diminv.warp.others` | OP level 4 |

Permission nodes are checked via [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api), which is bundled in the mod jar. With [LuckPerms](https://luckperms.net/) installed, you can grant nodes to non-operators:

```
/lp group vip permission set diminv.warp.self true
/lp group admin permission set diminv.warp.others true
```

Without a permissions mod, both nodes fall back to operator level 4.

The `<pool>` argument accepts any pool ID configured in Dimensional Inventories. It is autocompleted from the live pool list.

### Examples

```
/warp default
/warp survival
/warp player Steve creative
/diminv warp default
```

## Position memory

Each player's last known state in every pool is remembered and persisted to disk:

- **Pool dimension tracking:** when a player leaves a dimension, the mod records which dimension within that pool they were in. On the next warp to that pool, they are returned to that same dimension.
- **Coordinates:** positions are recorded whenever a player leaves a dimension — whether through a portal, the `/warp` command, or any other means. On warp, the player is returned to their last coordinates and camera angle in the target dimension.
- **Fallback:** if the player has never visited a pool before, they are teleported to the first dimension in that pool at world spawn.
- **Storage:** one JSON file per player at `<world>/dimensional-inventories-extension-warp/<uuid>.json`. Data survives server restarts.

Example file:
```json
{
  "positions": {
    "minecraft:overworld": {
      "x": 128.5,
      "y": 64.0,
      "z": -200.5,
      "yRot": 90.0,
      "xRot": 0.0
    },
    "minecraft:the_nether": {
      "x": 16.5,
      "y": 32.0,
      "z": -25.5,
      "yRot": 180.0,
      "xRot": 0.0
    }
  },
  "lastDimensionInPool": {
    "default": "minecraft:overworld"
  }
}
```

## Installation

1. Install [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories).
2. Drop `dimensional-inventories-extension-warp-<version>.jar` into your `mods/` folder alongside it.

## License

MIT
