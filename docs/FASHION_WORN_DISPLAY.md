# Fashion Worn Position

Open Fashion, enable the equipment slots and item-based wearable mode, then open Model display.
Choose the equipment slot and Worn on player, Inventory, or a hand/ground/frame view in the same dialog.
Position, rotation and scale are stored independently per slot and display context.
Apply closes the dialog; saving the Fashion element persists and regenerates the renderer.
Cancel discards the dialog's changes. Hidden/disabled slots keep their previous saved values.

The preview supports OBJ models with standing, walking, crouching and head-turn reference poses.
The move/rotate/scale, camera, proportional scale, copy/paste, reset and grouped undo controls are reused.
Auto-fit centers the rotated OBJ bounds and proposes a uniform starting size for the reference.
Fit is a starting point, not a guarantee that a hat or accessory is correctly aligned to a custom skin.
Saved presets retain the current transform by name in the user folder's remake-display-presets.json.
They can be applied across items and contexts; loading a preset is undoable.
Non-OBJ items still accept numeric transforms, but do not have an OBJ preview.

## Runtime

- Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1 and pinned NeoForge 21.1.219 templates apply
  the additional transform after the existing slot attachment and before the item renderer.
- A single item equipped in different slots uses each actual equipment slot's settings.
- Legs/feet are linked by default. Uncheck Link left / right to edit the selected side independently.
  The Left/Right checkboxes control which attachment renders. Relinking retains the last separate
  right-side values so unlinking again can restore them.
- Defaults are identity; old projects keep their previous appearance.
- Switching views does not overwrite the other views. With a single-item set, held/inventory views
  share the primary item's settings while worn transforms remain specific to each equipment slot.
- Advanced Java armor models and 26.1.2 renderers are not connected to this feature;
  the Worn on player option is absent there; the same dialog still edits item display contexts.

## Live Preview In Game

1. Save/generate the Fashion element after updating Studio, then start its development client once.
   An already-running client compiled before this update does not contain the bridge.
2. Enter a local single-player world and hold/equip the generated item. Open Fashion > Model display
   in Studio and enable Live preview in game.
3. Edit transforms. Worn changes use runtime overrides; item-display changes reload a temporary
   resource pack and can briefly pause rendering. These are not zero-cost per-frame updates.
4. CONNECTED means the game acknowledged the revision and observed the worn renderer hook.
   PARTIAL means only item display was acknowledged. WAITING is not a successful connection;
   hover the status for details. After disconnecting, toggle live preview off/on to start a new session.
5. Apply then save the Fashion element to keep changes. Cancel or closing the dialog removes its
   temporary preview. Rebuild/restart to use newly generated Java worn settings permanently.

The bridge targets the installed Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1 (including pinned
21.1.219), NeoForge 26.1.2 and Fabric 26.1.2 generators. On 26.1.2 this is item display only;
the item-based worn renderer is not yet connected. No claim is made for arbitrary future versions.
It runs only in development and local single-player, never in a packaged production client or
multiplayer. Files stay under run/.remark-display-preview; original assets are not changed.
An expired lease stops applying overrides if Studio exits unexpectedly.

## Preview Boundaries

This is not a Minecraft viewport. Standing classic player proportions and existing rigid item
attachment offsets/scales are approximated. Inherited model HEAD/FIXED transforms, GUI texture
overrides, JSON models, slim skins and vanilla animations can differ from this reference. Pose
choices are static references, not the game's animation engine.
The preview uses explicit custom FIXED transforms for the selected actual item where available.
For production checks, save the element and inspect it in Minecraft. In-game visuals have not
been verified by the automated tests.

## Verification

WornDisplaySettingsTest checks legacy/null defaults, deep copies and JSON round trips.
RemarkElementGeneratorMatrixTest checks all four slot offsets for single and separate items
on the four connected generators. ItemDisplaySettingsEditorTest covers editing and undo.
RemarkLivePreviewAndQualityTest renders all four OBJ worn views to build/obj-display-smoke.

2026-09-17 verification: Studio JAR and 91 targeted tests passed. The normal generator matrix
covered the five directory-based generators (15 element cases); a separate template test rendered
and checked registration for all six bridge variants, including the staged Fabric 26 archive.
Forge 47.4.20 and NeoForge 21.1.219 API compilation covered the helper and worn layer. The shared
26.1.2 core compiled against the actual Minecraft JAR, but not its complete loader wiring.
Full Fabric loader compilation, other NeoForge patch endpoints and real client/reload behavior
are still unverified. NeoForge 26 fails closed if addPackFinder is unavailable.
An open Studio window locks the installed Fabric 26 ZIP. The update is staged under
.remarkstudio-pending and installed by RUN_REMAKE_STUDIO.bat after all Studio windows close.
