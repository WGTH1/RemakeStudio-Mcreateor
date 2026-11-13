# No-Code Minecraft Mod Studio

Audited 2026-09-13. This is the staged evolution of the existing Remake Studio desktop app.

## Product and first user journey

Audience: Windows users making Minecraft Java Edition mods without writing Java or editing Gradle.
The first journey is New workspace -> choose the pinned target -> Item -> texture and display name ->
Simple behavior -> save -> reopen -> Run client -> Export JAR.
Thai display names are independent from the generated registry identifier.

The existing application already has project management, resource imports, item/block/recipe editors,
Blockly procedures, generator plugins, Gradle execution, backup tools and export.
These features are not all newly validated by this milestone.

## Scope

MVP: project create/open/save/backup/export; item and basic block editing; visual recipes;
a bounded form-based behavior builder; validation; isolated test launches; real runtime JAR export.
First vertical slice: a speed wand that applies speed to its holder for five seconds, a small optional
particle burst and a ten-second cooldown.
Later: blocks and recipes under the same verified target, hardened imports/build cancellation/recovery,
then additional target adapters. Complex armor, VFX, RP, entities and drag-and-drop screens remain
existing or later subsystems with their own test matrices.
No cloud, account, marketplace, AI runtime or Bedrock requirement.

## Initial target

See target-neoforge-1.21.1.json. Choose Minecraft 1.21.1 + NeoForge 21.1.219 because the checkout has
a dedicated pinned generator and cached matching dependencies. The application needs Java 25;
generated mods use Java 21. The generated build uses Gradle 9.6.1 and ModDevGradle 2.0.141.
No additional runtime mod is needed for simple behavior.
Simple behavior remains experimental until client/manual multiplayer checks complete.
Existing loader entries do not imply this new behavior has been ported to them.

## Architecture

Keep the established Java/Swing/FlatLaf desktop, Gson storage, FreeMarker generator plugins and
Gradle Tooling API. Replacing the desktop stack would not address the generator correctness first.

UI -> persisted Item + SimpleItemBehavior -> shared validation -> immutable typed Plan ->
pinned NeoForge template -> Java/resources -> Gradle -> artifact verification.

The UI only reads and writes settings. It does not concatenate Java or shell commands.
The behavior compiler converts seconds and effect levels only after range and target checks.
Output uses Item.use, server-side effects and vanilla item cooldowns. There is no additional tick
scanner, scripting interpreter or bundled runtime library.

Legacy items without the optional behavior remain disabled. Behavior schema 1 is explicit;
unknown versions are rejected rather than rewritten. The existing element format and project
converters remain in use. A project-wide stable UUID reference migration is future work:
existing legacy name references must not be represented as already converted.

## UX direction

Keep navigation consistent: creation/assets left, editing/preview center, selection properties right,
save/test/export at the top, diagnostics on demand. Implement this incrementally around the current
editors, with actual connected controls rather than a replacement shell.
The first behavior form exposes trigger, recipient, action, duration, level, cooldown and particles.
Advanced Blockly remains available separately. Incompatible right-click handlers show a blocking
message. Reset restores defaults; target status includes text rather than color alone.

Next UX work: a single guided first-mod entry point, better progressive disclosure of advanced Item
pages, undo/redo across actions, and consistent property-panel sizing at high DPI.
The new form does not mean these app-wide changes are already done.

## Correctness and performance rules

- Gameplay executes on the logical server. Vanilla cooldown rejects repeated use.
- Cooldown is shared by stacks of the same item type and both hands for each player.
- Reconnection/respawn use vanilla cooldown lifecycle; this slice does not persist cooldowns.
- Optional particles are one eight-particle burst per accepted use; no periodic world/player query.
- Speed level is limited to 1-5 and time values are validated, not silently clamped.
- Food, charged use, ranged items, inventories and pre-existing air right-click procedures conflict.
- Other targets are blocked in generation and compatibility reports; configuration is preserved.
- Existing block interactions can consume a right-click before Item.use, following vanilla behavior.
- Generated code locking remains the explicit advanced manual-code policy.
- No measured frame-time, network-load or multiplayer capacity claims follow from these limits.

## Milestones and gates

1. Audit and targeted baseline. Establish the pinned target and current toolchain.
2. First behavior form with persisted schema, typed lowering and generator validation.
3. Save/reopen tests, generated source/JAR checks and Minecraft runtime tests.
4. Simplify the complete creation flow, add blocks and recipes with reference validation.
5. Harden atomic saves, recovery, malformed imports, filesystem containment, cancellation and exports.
6. Add each loader/version only after its adapter, generated build and runtime checks pass.

Release gates distinguish: UI rendered, schema verified, generator built, game loaded, behavior
verified, multiplayer verified and performance measured. A passing build does not close other gates.
The first three steps are the focus of this implementation round; consult DELIVERY.md for evidence.

## Sources

- https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- https://docs.neoforged.net/docs/1.21.1/items/
- https://docs.neoforged.net/docs/1.21.1/misc/gametest/
- https://docs.gradle.org/current/userguide/toolchains.html
- Local pinned generator workspacebase/build.gradle, generator.yaml and cached 21.1.219 sources.
- Forge and Fabric documentation are reference points for later adapters, not evidence of new support.

Retain existing licenses and notices. Shipping this modified GPL application must follow its license;
the generated mod and supplied assets require their own licensing review when distributed.
