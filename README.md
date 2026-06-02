# Dimensional Inventories Extension: Warp

An extension for [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories) that adds warp commands to teleport players between dimensions.

Inventory switching is handled automatically by Dimensional Inventories — the extension just does the teleportation.

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

## Installation

1. Install [Dimensional Inventories](https://modrinth.com/mod/dimensional-inventories).
2. Drop `dimensional-inventories-extension-warp-<version>.jar` into your `mods/` folder alongside it.

## License

MIT
