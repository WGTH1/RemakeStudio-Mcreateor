# Create UI: Pause Menu

Create UI can replace either the title screen or the in-game Esc menu. Existing elements keep their Main menu target.

## Editing

1. Create a separate **Create UI** element for the pause menu to keep your existing title menu.
2. In the preview's **Screen & actions** tab, select **Pause menu (Esc)** as the target screen.
3. Apply the **Pause menu (Esc)** preset for an editable starting layout, or retain your own layout.
4. Drag, resize, recolor, and edit images, text and buttons in the preview as usual.
5. Right-click the preview to add Back to Game, Advancements, Statistics, Disconnect, or Original menu buttons. Existing buttons can be assigned those actions from their context menu.
6. Enable replacement, save, regenerate code and build. Keep only one enabled replacement per target; one Main menu and one Pause menu can coexist.

Applying a preset replaces the current element layout. The editor's undo history can restore the preceding layout.

## Background And Game Behavior

- With no imported background images, the game world remains visible behind the menu and the configured overlay. The editor shows transparency as a checkerboard, not a live view of the game world.
- Imported backgrounds retain the existing crop, slideshow and fade controls.
- Esc and Back to Game return to the world and restore mouse capture.
- The custom pause screen uses Minecraft's normal pause-screen flag. This pauses a private singleplayer session; it does not pause a multiplayer server or a singleplayer world published to LAN.
- Disconnect follows the target game's normal draft-report confirmation, world save/cleanup and destination-screen flow. It is distinct from Quit Game, which closes the application.
- Original menu opens the native pause screen without immediately replacing it again, preserving access to mod-list buttons, reporting, LAN controls, server links and other mods' additions.
- Only the visible `PauseScreen(true)` menu is replaced. The hidden/loading `PauseScreen(false)` is left untouched.

## Generator Coverage

Templates are updated for Forge 1.20.1, Fabric 1.20.1, both bundled NeoForge 1.21.1 variants, NeoForge 26.1.2 and the Fabric 26.1.2 plugin archive.

## Verification (2026-10-13)

- The final combined Create UI and AAA regression run passed 49 tests; 2 other opt-in build suites were skipped.
- Real generated JAR builds passed for Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2, including separate title, custom pause and empty-layout pause elements.
- NeoForge 26.1.2 was additionally rebuilt with two background images, fading and a foreground image. The packaged textures and generated classes were checked.
- The Fabric 26.1.2 ZIP template/helper were checked for consistency and preservation of unrelated archive entries, but a real Fabric 26.1.2 build was not run.
- Editor tests cover preset actions, fixed button widths, reopening a draft without losing components and preview rendering at 960, 1280 and 1920 pixels wide.

Generated build proofs and editor screenshots are kept under `build/createui-pause-proof-20261013-v2` and `build/reports/createui-pause`. Compilation and fixture tests do not establish in-game visual correctness or compatibility with every modpack; live singleplayer, multiplayer, draft-report cancellation and third-party native-menu additions still require in-game QA.
