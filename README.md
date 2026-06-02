# Dimensional Inventories Extension: Warp

An extension for [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories) that adds warp commands to teleport players between dimensions.

When a player warps to a dimension they've visited before, they are returned to the exact coordinates (and camera angle) where they last left it. Inventory switching is handled automatically by Dimensional Inventories.

## Requirements

- Minecraft 26.1.x
- [Fabric Loader](https://fabricmc.net/) 0.19.2+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories) 2.2.1+26.1

## Commands

All commands require permission level 4 (operator).

| Command | Description |
|---|---|
| `/warp <dimension>` | Teleport yourself to a dimension |
| `/warp player <player> <dimension>` | Teleport another player to a dimension |
| `/diminv warp <dimension>` | Same as above, under the main mod's command tree |
| `/diminv warp player <player> <dimension>` | Same as above, under the main mod's command tree |

### Examples

```
/warp minecraft:the_nether
/warp minecraft:the_end
/warp player Steve minecraft:overworld
/diminv warp minecraft:the_nether
```

The `<dimension>` argument accepts any dimension ID registered on the server, including dimensions from datapacks.

## Position memory

Each player's last known position in every dimension is remembered and persisted to disk:

- **Tracking:** positions are recorded whenever a player leaves a dimension — whether through a portal, the `/warp` command, or any other means.
- **On warp:** if the player has previously visited the target dimension, they are returned to their last coordinates and camera angle there. On a first visit the world spawn is used as a fallback.
- **Storage:** one JSON file per player at `<world>/dimensional-inventories-extension-warp/<uuid>.json`. Data survives server restarts.

Example file:
```json
{
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
}
```

## Installation

1. Install [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories).
2. Drop `dimensional-inventories-extension-warp-<version>.jar` into your `mods/` folder alongside it.

## License

MIT
