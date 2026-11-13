# Remake Studio Quick Guide

## Start

Run `RUN_REMAKE_STUDIO.bat`.

Save and close other Studio windows before applying a pending plugin update.
The launcher allocates a free temporary drive and releases only that drive on exit.
Existing drive mappings are left alone. If no drive is free, close the application that owns
an unused mapping before retrying; do not remove mappings still in use.

## NeoForge 1.21.1 Targets

- `NeoForge for 1.21.1 (21.1.232)`: the regular bundled target.
- `NeoForge for 1.21.1 (21.1.219, pinned)`: the separate target pinned to NeoForge `21.1.219`.

The pinned target supports Remake Studio AAA Particle, Fashion, and Create UI elements. Restart Remake Studio after
adding or changing generator plugins so the target list is reloaded.

## AAA Particle

1. Create an `AAA Particle` element.
2. Drag a `.efkefc` file or the whole effect folder into the import panel.
3. Use `Scan assets` to check missing texture/material/model references.
4. Save the element.
5. In game, use the copied test command from the editor.

The importer copies the selected `.efkefc` plus nearby `texture`, `textures`, `material`, `materials`, `model`,
`models`, `sound`, and `sounds` folders when generating code.

Spawn modes:

- `World`: use the effect's original/world orientation.
- `Source facing`: face the direction of the entity/player at spawn time.
- `Source follow view`: keep following the source view direction while the effect runs.

Fabric 1.20.1 support uses AAA Particles `1.20.1-1.4.3-fabric` from Modrinth. Enable the `AAA Particles`
API in workspace settings after switching the generator to Fabric 1.20.1.

## Fashion

Fashion is cosmetic armor.

- `Use held/inventory item model when worn`: renders the item model only on the slot where it is equipped.
- `Create one item for all enabled parts`: one generated item can be equipped in any enabled slot.
- If only Head is enabled, the item equips only to Head.
- If Head, Body, Legs, and Feet are enabled, the same item can be equipped to any of those slots, but it only renders at the equipped slot.

For simple 3D wearable items, import/select the item model the same way as normal held item models. You do not need an armor
layer texture unless you switch to advanced armor-layer/model rendering that needs it.

Fabric 26.1.2 and Fabric 1.20.1 support simple Fashion too. Simple Fashion uses a client item render layer, so the
item model renders only at the slot where the item is equipped. Fabric 1.20.1 uses a no-op Fabric armor renderer to hide
the default armor layer for these simple Fashion items.

## Fabric 1.20.1 Notes

Fabric 1.20.1 in Remake Studio has extra patched support for `AAA Particle`, `Fashion`, `Attribute`, and `Armor trim`.
GUI `onTick` and Enchantment `Allowed on books` are enabled too.

Some warning markers are intentionally kept for fields that still need loader-specific code, such as advanced armor
Java model rendering, item swing/drop hooks, fluid-only block behaviors, potion HUD visibility, and tab search bars.

## Remake Tools

Open any element editor and use `Remake tools`.

- `Save preset`: saves the current element setup as `.rspreset`.
- `Load preset`: loads a preset into the current editor when the element type matches.
- `Preview / test commands`: shows useful `/give`, `/summon`, `/particle`, or `/aaaparticle` commands when available.
- `Find / copy assets`: re-copies AAA Particle imported assets from the source folder.
- `Compatibility`: shows known loader/version support for the current element.

## Build Check

Before Build, Run Client, Debug Client, or Run Server, Remake Studio warns about common setup problems such as missing
AAA Particle files, missing AAA dependency, empty Fashion slots, or empty Fashion textures. You can still build anyway
if you are intentionally testing something unfinished.

If Gradle fails, the console can show `Remake Studio hints` for common errors like missing Java 25, old generated
Fashion item names, missing generated item classes, armor layer naming problems, or AAA Particle missing assets.

## Export Profiles

Export mod now asks for a profile:

- `Dev build`: runs `assemble` and names the file with `-dev`.
- `Release build`: runs `build` and names the file with `-release`.
- `Modpack build`: runs `build` and names the file with `-modpack`.

## Current Element Regenerate

When editing an existing element, use `Regenerate this element` to save and regenerate just that element without closing
the editor tab.
