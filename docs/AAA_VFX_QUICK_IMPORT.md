# AAA VFX Quick Import

## Use

Start this source checkout with `RUN_REMAKE_STUDIO.bat` and open a workspace.

1. Drop `.efkefc` files or an effect folder on the mod element list. Alternatively select **+ > AAA Particle**, or use the particle import icon on the left.
2. Select one mode and confirm. No element name or transform setup is required.
3. Use the `/particle <modid>:<effect>` commands shown in the import result. Double-click an element to change its mode later.

- **World**: fixed world position and axes.
- **Facing**: captures the executing player's direction once, at spawn.
- **Follow**: follows the source's position and view until the effect finishes.

For command blocks, supply the source explicitly, for example:

```mcfunction
execute as @p at @s run particle example:skill_1
```

The full effect is already included in the file: use count `1`, not hundreds of copies.

## Imported Files

- Names are derived from filenames, with numeric suffixes for collisions. Underscores in registry IDs are preserved.
- INFO dependency tables are read structurally, including old and newer Effekseer versions and UTF-16 filenames.
- Referenced textures, models, materials, curves and sounds are copied automatically when present beside the effect, in its fallback folder, or under up to four parent folders.
- Unreferenced neighbouring asset libraries are not copied. Missing dependencies are reported with their filenames; no incomplete element is created.
- Each quick import gets a separate resource directory so identically named textures cannot overwrite another effect.
- Saved definitions point to `src/main/resources/assets/<modid>/effeks/...`, not external files. The original file can subsequently be moved or removed.
- Imports run off the Swing event thread, with progress. Folder/multiple-file imports support up to 128 effects per batch.
- Absolute and `..` dependency paths are rejected. Re-export those effects with assets inside their own folder.
- Existing elements keep their transform settings. These remain available on **Advanced**.

## Visibility And Performance

Quick imports enable **No distance limit (same dimension)**. Existing elements retain their previous distance settings; enable it on Advanced to opt them in.

This removes the generated server delivery cutoff, including the previous 32/512-block command ranges. It does **not** replace Minecraft's camera projection, change chunk loading, draw through terrain, or guarantee unlimited visible distance. In particular, AAA entity-bound Follow requires the source entity to exist on each viewer's client; outside entity tracking range that condition is not met. Cross-dimension effects are not broadcast.

The generators reuse one packet per emitter for all recipients, filter dimensions once, skip random spread work for zero offsets, and avoid setting native scale/speed when they equal the defaults. They do not respawn Follow emitters every tick.

The custom `/particle` aliases reject more than 64 effect instances per command or dimension/tick before spawning anything. This is a burst guard, **not** a cap on active native emitters and not a limit on arbitrary procedure or other-mod calls.

Effect geometry, overdraw, distortion, texture resolution and overlapping instances still determine GPU cost. This update does not decimate models or alter the supplied `.efkefc` artwork. No FPS/no-lag guarantee is implied.

## Implementation References

Dependency parsing follows the official [Effekseer EFKE reader](https://github.com/effekseer/Effekseer/blob/master/Dev/Cpp/Effekseer/Effekseer/IO/Effekseer.EfkEfcFactory.cpp).
Distance and binding use [AAA's API](https://github.com/ChloePrime/AAAParticles/blob/master/common/src/main/java/mod/chloeprime/aaaparticles/api/common/ParticleEmitterInfo.java).

Templates updated: Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1 (both bundled variants), NeoForge 26.1.2 and Fabric 26.1.2.

## Verification (2026-10-09)

- Real generated mod JAR builds passed for Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2, with all three movement modes and unlimited delivery enabled.
- A subsequent Minecraft 26.1.2 compile regression was fixed by using `Level.getRandom()` in all six command templates. The command fixture now models the protected `Level.random` field, and the fix was rebuilt against real NeoForge 26.1.2 and Forge 1.20.1 dependencies in separate verification workspaces.
- Command and transform contracts execute templates from all six bundled generator variants; those fixtures do not implement Minecraft's renderer.
- A real `seraphic_detonation.efkefc` from the local sundayzy workspace was read and staged with all 14 dependencies, without modifying the original.
- Swing editor screenshots were inspected at 960x640 and 1280x640. Tests cover preservation of advanced values and coexistence with the list's previous transfer handler.
- Actual in-game appearance, distant two-client Follow, FPS and simultaneous-effect load capacity have not been measured in this change. Fabric 26.1.2 was not rebuilt against its real dependencies in this run.
