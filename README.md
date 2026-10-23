# Wrench Display

A wrench and a blueprint for building with block, item and text displays.

- **Wrench**: hold a block or item in your off hand and right-click a block to place it as a display. Look at a
  display and right-click / left-click to increase / decrease the selected property (move, rotate, scale, block
  state, brightness, glow, …). Sneak + scroll or sneak + right/left-click picks the property.
- **Blueprint**: paste a `/summon` command exported from [BDEngine](https://block-display.com) (or any
  block/item/text display summon command), then right-click a block to place the whole model. A preview shows
  where it goes; sneak + scroll rotates it.

Survival-friendly: placing uses up the items (refunded on removal), and unfair options are limited unless a player
has the matching permission.

## Installing on a Fabric server

1. Put `wrench-display-fabric-<version>.jar` and Fabric API in the server's `mods` folder. Players don't need the
   mod; vanilla clients can join.
2. Vanilla clients need the resource pack to see the wrench and blueprint models. Host
   `wrench-display-pack-<version>.zip` somewhere public and either
   - set `resource-pack` and `resource-pack-sha1` in `server.properties`, or
   - set `resourcePack.url` and `resourcePack.sha1` in `config/wrench-display.json`.

   The SHA-1 is in the `.zip.sha1` file next to the pack.
3. Players with the mod installed on their client don't need the pack, and get a bigger paste window for
   blueprints. Without it the vanilla paste window holds about 16,000 characters, the most the protocol can carry.

Give the tools with `/wrenchdisplay give <players> wrench|blueprint`, or craft them:
wrench = 3 iron ingots in a diagonal + 1 copper ingot, blueprint = 1 paper surrounded by 4 lapis lazuli.

## Permissions

Without a permission mod, everyone may use the tools and operators (level 2) get everything else.
With one (e.g. LuckPerms), the nodes are:

| Node | Allows |
|---|---|
| `wrenchdisplay:use.wrench`, `wrenchdisplay:use.blueprint` | using the tools (default: everyone) |
| `wrenchdisplay:bypass.offset` | moving displays more than `maxOffset` from where they were placed |
| `wrenchdisplay:bypass.scale` | scales outside `minScale`–`maxScale` |
| `wrenchdisplay:bypass.chunklimit` | more than `maxDisplaysPerChunk` displays per chunk |
| `wrenchdisplay:bypass.cost` | placing without using up items |
| `wrenchdisplay:bypass.blueprintsize` | blueprints larger than `maxModelSize` / `maxPartSize` |
| `wrenchdisplay:visual` | glow, glow colour, brightness, view range |
| `wrenchdisplay:blocks.restricted` | restricted blocks (barrier, light, portals, command blocks, …) |
| `wrenchdisplay:blockstate.free` | block-state keys outside the survival list |
| `wrenchdisplay:text` | text displays in blueprints |
| `wrenchdisplay:edit.others` | editing displays someone else placed |
| `wrenchdisplay:edit.foreign` | editing displays not placed with the wrench |
| `wrenchdisplay:admin` | `/wrenchdisplay` |

All limits are in `config/wrench-display.json`; `/wrenchdisplay reload` applies changes.

## Building

```
./gradlew build                   # mod jar, common library, resource pack zip
./gradlew :fabric:runGameTest     # in-game tests
./gradlew :fabric:runClient       # play-test
```

## License

Wrench Display is released under the [MIT License](LICENSE).
