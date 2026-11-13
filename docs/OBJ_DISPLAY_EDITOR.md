# OBJ Display Editor

The item/fashion Display dialog has a resizable preview and a context selector for inventory, ground,
item frame, and first/third-person left/right hands. Each context retains its own transforms.

- Copy/paste transfers the current context only.
- Undo/redo applies to the current context; one mouse drag is one undo operation.
- Reset transform restores identity for the current context. Reset camera does not change the model.
- Link scale fields preserves existing X/Y/Z ratios when editing a scale field.
- Right drag orbits, middle drag pans, wheel zooms. Ctrl+wheel edits scale.
- Empty-space left drag does not edit the model. Move arrows follow the projected display axes.
- Mirror right to left copies display values: Minecraft itself mirrors X position and Y/Z rotation.
- Auto-fit centers the rotated OBJ bounds, including left-hand mirroring, at a uniform starting size.
- Named presets save/load the active transform. Delete only removes the selected preset; invalid or
  corrupt preset files are reported without replacing their contents.
- Fashion uses one Model display dialog for item contexts and supported worn attachments. See
  FASHION_WORN_DISPLAY.md for the local development Live Preview workflow and version boundaries.

## Validation Boundaries

The third-person standing-arm reference uses the 4x12x4-pixel vanilla arm and ItemInHandLayer's
X(-90), Y(180) attachment rotation, verified against the cached Forge 1.20.1 source.
It is an orthographic reference, not an embedded Minecraft renderer. First-person views remain
approximate; FOV, perspective, swing/equip animations, slim arms, shaders and custom renderers are
not simulated. GUI texture overrides can intentionally use a 2D icon rather than the OBJ.

Tests cover numeric live commit, proportional scaling, grouped undo, independent context copies,
OBJ texture rendering/size, empty-space drag and left-hand X dragging. A third-person image smoke
test is written to build/obj-display-smoke. These tests do not prove real in-game visual parity.
