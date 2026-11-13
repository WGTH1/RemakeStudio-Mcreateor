# Display Workflow Implementation Plan

Goal: deliver all six requested editor improvements without changing saved projects on open/cancel.
Architecture: reuse the Swing OBJ renderer and detached display session; add optional per-side worn settings,
bounded named presets and a dev-only game bridge. Do not claim the software preview is Minecraft-exact.

- [x] Baseline existing display/generator tests (19 passing).
- [x] Unified draft and mode switcher with deep-copy commit/cancel semantics.
- [x] Per-side leg/foot placement and visibility, legacy linked defaults, generator coverage.
- [x] Auto-fit from OBJ bounds and selectable reference poses.
- [x] Persistent named presets with corruption-safe atomic storage.
- [x] Live preview bridge implemented for six installed generator variants with dev-only guard and cleanup.
- [x] Integration, 1080/800px screenshots, 91 targeted tests and review fixes before final JAR build.
- [ ] Real Minecraft visual/reload tests; full Fabric and 26.1.2 loader compilation; other NeoForge patch endpoints.

Validation: state/persistence and legacy tests; generated Java/text contracts across loaders; screenshot
smokes at 1080x580 and 800x480. Verify actual game API and compilation for live helpers; report any runtime
or gameplay verification that is unavailable. Agents own only preset-store files and live-backend files.

Review fixes: left-hand auto-fit mirrors rotation/translation consistently; both worn sides share
one depth-sorted face list including attachment depth; the inspector tracks viewport width so Z
fields stay visible. Game helper + worn-layer API checks passed for Forge 47.4.20 and NeoForge
21.1.219. The Fabric 26 archive is staged because an existing Studio process holds the old ZIP.
