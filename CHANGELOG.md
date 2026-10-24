# Changelog

## 1.1

First public release.

**Wrench**
- Place the block or item from your off hand as a block or item display.
- Edit the display you look at: move, rotate (yaw, pitch, roll), scale, block state, item pose, billboard, shadow, light level, glow and glow colour, view range, with a choice of step sizes. Sneak + scroll or sneak + click picks the property, left/right click lowers or raises it.
- Displays placed from one blueprint form a group that moves together; switch to single parts in the "Mode" property.
- "Remove" takes a display down and gives its item back.
- Survival limits: how far a display can move from where it was placed, how big it can get, and how many displays fit in a chunk. Glow, brightness and view range are for operators by default.

**Blueprint**
- Paste `/summon` commands exported from display-model websites; the model is stored on the server.
- Ghost preview where you look, sneak + scroll to rotate, right-click to place.
- In survival, placing a model costs the blocks and items it is made of; missing materials are listed.
- Vanilla clients paste in a vanilla window (up to about 16 000 characters). With the mod installed on the client, a dedicated paste screen takes models of any size, on Fabric and Paper servers.

**Servers**
- Works server-side only: vanilla clients can join, the resource pack shows the wrench and blueprint models.
- Fabric for Minecraft 1.21.6 – 26.3, and a Paper plugin (also Purpur and Folia) for 1.21.6 – 26.3.
- Permissions for every feature (Fabric permission API or LuckPerms; Paper permissions).
- Respects claim and protection mods through Common Protection API on Fabric, and build-permission checks on Paper.
- Every message is translatable.

Crafting: the wrench is a netherite ingot, an iron ingot and a stick; the blueprint is lapis lazuli, a feather, paper, an ink sac and a diamond block. See the README for the grids.
