# AAA Particle stability update

## Transform behavior

- World keeps the supplied world position, world offsets, and X/Y/Z rotation.
- Face player snapshots the source view once. It keeps all three configured
  rotations, and uses the same coordinate convention as Follow player.
- Follow player binds one emitter to the selected entity. AAA's own interpolated
  head-space update follows that entity; the Studio does not spawn an emitter
  every tick. Explicit spawn coordinates are retained as a world-space offset
  from the attachment point.
- Face/Follow offsets are X right, Y up, Z forward. Effect roll does not swing
  the positional offset around the player.
- NeoForge 1.21.1 now applies the configured playback speed too.
- Fabric 1.20.1 now uses AAA Particles 2.2.3 and declares that minimum runtime
  dependency. Its previous dependency did not expose the playback-speed API.

Use `/particle <modid>:<element_registry_name>` to use the element's configured
World, Face, or Follow mode. The server handles these specific AAA IDs and sends
the command's source entity through AAA's emitter packet. Face snapshots the
source direction once; Follow remains attached until the effect finishes.
The copied vanilla argument tree retains coordinates (including local `^`),
spread, count, force/normal, viewer selectors, and permission requirements.
Count zero creates one emitter. Vanilla speed describes particle velocity, not
AAA playback speed; the effect's configured playback speed is retained.

For a command block use
`execute as @p at @s run particle <modid>:<element_registry_name>`.
Face/Follow without a source entity report an error instead of guessing a nearby
player. World mode still works without an entity. Explicit viewer selectors only
select recipients; they never change the source being followed.
`/aaaparticle <element_registry_name> on <player>` is also still supported.
Ordinary particles are untouched. Non-command vanilla particle packets still
have no source entity, so their AAA adapter uses World mode.

The Studio's animated preview is a transform diagram, not a native Effekseer
renderer. Native effect simulation, shaders, trails, and GPU performance still
need to be checked in Minecraft.

## Imported files

New imports point to the copied effect under
`src/main/resources/assets/<modid>/effeks/` immediately. The source effect and
its imported dependency paths are saved relative to the workspace.

Opening an older AAA element migrates it to the in-mod copy. When a mod copy
already exists, it wins over the old import copy. Save the element to persist
the migrated paths. Regeneration keeps these editable resources instead of
deleting and replacing them with stale imported files.

Explicit reimport is the operation that replaces an existing mod copy. Previous
contents are backed up under `.remarkstudio/backups/aaa-import/`. Source import
folders are retained. Replacing an individual resource uses a temporary file
and atomic replacement where supported.

Imported dependencies remain associated with the effect. Deleting an effect
does not delete an associated resource still used by another element.
Component-library export and quality checks use that effect's dependency list,
not the entire shared effeks directory.

This does not rewrite the binary format or any absolute paths embedded by an
external Effekseer editor. Export effects with portable relative resource paths
and import the folder containing their actual dependencies when necessary.

## Update and verification

Save work and close the old Studio window, then use `RUN_REMAKE_STUDIO.bat`.
The Fabric 26.1.2 plugin update is staged while its ZIP is held by the old
process. The launcher applies it after that process closes. Open and save the
AAA elements, regenerate code, and rebuild the mod. Existing installed Studio
EXE packages are not repackaged by this source update.

Tests cover portable imports, existing in-mod edits, explicit reimport backups,
dependency relocation, shared-resource deletion, six bundled transform
templates, negative offsets, pitch/yaw edge cases, and command-source handling.
The transform contract fixture models the upstream head-space API. It is not
an in-game or visual test.

### Full Regenerate Code correction

The full-workspace cleanup now honors the same retained-resource policy as
individual element generation, in both legacy and non-invasive cleanup modes.
Previously it could delete the canonical effect before finalization, leaving
partially generated Java without automatically resolved imports. AAA provider
templates now also declare their required imports explicitly.

The follow-up regression suite passes 14 tests, including two consecutive full
regenerations per cleanup mode with edited in-mod assets and no external copy.
The `sundayzy` recovery restored 13 assets from the local-history snapshot before
deletion; their contents were verified against the rebuilt mod JAR. Java symbol
errors no longer automatically produce a missing-effect-assets hint.

### Particle command verification (2026-09-13)

- The `sundayzy` Forge 1.20.1 mod builds successfully with the actual `skill_1`
  resource and its unchanged Face setting.
- A Forge GameTest invokes that registered alias on a dedicated test server.
  It also verifies `execute as`, vanilla local-coordinate parsing, and the
  helpful error for a source-less Face command. The player uses an embedded
  Netty channel; this does not verify a real client's visual rendering.
- The Brigadier command contract executes all six bundled helper templates,
  checking source identity, viewer selection, force/normal range, shared random
  spread, count-zero behavior, permissions, and unrelated vanilla particles.
- Fresh generated API builds pass for Fabric 1.20.1, NeoForge 1.21.1, and
  NeoForge 26.1.2. Fabric 26.1.2 is covered by the staged-template contract and
  API inspection, not a new full Fabric 26.1.2 build.
- Architectury is a compile-only dependency for the command transport. AAA
  already bundles it at runtime; this change does not require users to install
  another mod. Loader-specific import priorities avoid ambiguous class names.

The isolated Forge runtime fixture is under `src/test/resources/aaa-command/`
and writes its test world only under `sundayzy/build/aaa-command-game-test/`.
Its init script is opt-in and no proof classes are packaged in the mod.

API references:
- [ParticleEmitterInfo](https://github.com/ChloePrime/AAAParticles/blob/master/common/src/main/java/mod/chloeprime/aaaparticles/api/common/ParticleEmitterInfo.java)
- [Basis](https://github.com/ChloePrime/AAAParticles/blob/master/common/src/main/java/mod/chloeprime/aaaparticles/common/util/Basis.java)
- [Resource loader](https://github.com/ChloePrime/AAAParticles/blob/master/common/src/main/java/mod/chloeprime/aaaparticles/client/loader/EffekAssetLoader.java)
