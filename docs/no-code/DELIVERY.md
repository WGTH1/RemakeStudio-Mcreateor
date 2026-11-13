# First No-Code Milestone Delivery

Historical first-milestone snapshot. Current workflow and version-selection instructions are in
[ROUND2.md](ROUND2.md); the standalone Simple behavior page and exact-target-only restriction
described below have since been superseded.

Date: 2026-09-13. This is an implemented first slice, not a completed replacement application.
Product direction, MVP, UX, architecture and later gates: [ROADMAP.md](ROADMAP.md).
Exact initial profile: [target-neoforge-1.21.1.json](target-neoforge-1.21.1.json).

## Open and try without writing code

1. Close older running Remake Studio instances. Open `RUN_REMAKE_STUDIO.bat` in this checkout.
   The installed Program Files executable has not been repackaged by this milestone.
2. Open `examples/speed-wand-20260913/remark_first_mod.mcreator` from the workspace chooser.
3. Open the `SpeedWand` Item. Its display name is Thai and its texture is already included.
4. Select `Simple behavior` between the Item's Properties and Use properties pages.
   Enable speed, choose duration, level, cooldown and the optional particle burst. Save normally.
5. Close/reopen the workspace to continue editing. Existing Run client and Export controls use
   the normal generator/build pipeline; no Java or Gradle editing is required for this behavior.
6. In a separate creative test world with commands enabled, use
   `/give @s remark_first_mod:speed_wand`, hold it and right-click into the air.

To start from scratch: New workspace -> Minecraft 1.21.1 / NeoForge 21.1.219 (the pinned profile)
-> add Item -> set texture and display name -> Simple behavior -> enable -> save -> build/export.
Only this exact profile supports the new form's generated behavior in this milestone.
The internal identifier remains separate from the Thai display name.

Thai UI labels are included; with the English app language the page is `Simple behavior`.
The supplied sample has speed I for 5 seconds, a 10-second cooldown and an eight-particle burst.
It is obtained by the command above; it does not add a recipe or a creative-tab entry.

## Runtime artifact

Send/install `examples/speed-wand-20260913/dist/remark-first-mod-1.0.0-neoforge-1.21.1.jar`.
This is the normal runtime JAR, not a sources JAR or the Studio installer.

- Minecraft Java Edition: 1.21.1.
- Tested loader: NeoForge 21.1.219; Java 21.
- Install in the isolated client instance's `mods` folder and on the server for multiplayer.
- No AAA Particles, Figura, MCreator, or extra runtime library is required by this example.
- Size: 9,976 bytes.
- SHA-256: `937B17034626F485031AE354AC78E18C7DB479DC650D8FE333462F9BCB88DD2A`.

The existing generator metadata allows later NeoForge versions; that is not evidence that they
have been tested. This delivery's verified version is exactly 21.1.219.

## Implemented files

- `element/parts/SimpleItemBehavior.java`: optional versioned settings on Item, disabled for legacy items.
- `generator/behavior/SimpleItemBehaviorCompiler.java`: shared validation and immutable bounded plan.
- `ui/component/SimpleItemBehaviorPanel.java` and `ui/modgui/ItemGUI.java`: real persisted editor form.
- `element/types/Item.java` and `generator/Generator.java`: typed generation and validation gate.
- `ui/remark/RemarkCompatibilityService.java` and `ui/validation/RemarkStudioBuildValidator.java`: migration/build diagnostics.
- The pinned NeoForge generator's `templates/item/item.java.ftl`: server-side use and vanilla cooldown.
- `plugins/remark-localization/lang/texts*.properties`: base, English and Thai strings.
- Unit/integration tests and opt-in `src/test/resources/no-code` Minecraft GameTest harness.

Java paths above are relative to `src/main/java/net/mcreator/`.
Original edited files were backed up in `.remarkstudio-backups/no-code-milestone-20260913`.
No old effect projects, unrelated plugins, user worlds or installed executable were removed.

## Evidence

- Baseline before edits: pinned-target availability and Gson tests passed (2 tests).
- Final targeted app run: 9 tests passed, 0 failed, 0 skipped.
  Includes actual ItemGUI load/read/validation, rendered behavior panel, save/reopen with Thai name,
  invalid-number/range/schema checks, handler conflicts, deterministic lowering and target blocking.
- Opt-in example build passed and produced the runtime JAR. JAR contents include item registration,
  compiled item, metadata, model, texture and localization; no test harness classes are packaged.
- Dedicated Minecraft GameTest on 1.21.1 / NeoForge 21.1.219: 1 required test passed, process exit 0.
  It exercised initial use, effect duration/amplifier, rejection from the other hand, effect expiry,
  cooldown remaining after effect expiry, cooldown expiry and successful reuse.
- The GameTest uses a mock server player and explicitly ticks its embedded connection through the
  normal Minecraft connection lifecycle. It runs generated main classes in a development instance,
  not two real clients or an independently installed packaged-JAR profile.
- Visual inspection: `build/no-code-proof/behavior-panel.png`, 680 x 440, English labels and values.
- Runtime evidence: `examples/speed-wand-20260913/build/runtime-proof-console.log` and
  `examples/speed-wand-20260913/run-proof/logs/latest.log`.
- App test evidence: `build/test-results/test/TEST-*.xml` and `build/reports/tests/test/index.html`.

## Reproduce engineering checks

Use an ASCII drive mapping to this checkout (the tests used `R:`) and the bundled Java 25 for the app.
Do not map over an occupied drive. Do not launch the batch launcher while tests use the same mapping.

```powershell
$env:JAVA_HOME = 'R:\jdk\jbr25_win_64'
.\gradlew.bat jar test --tests net.mcreator.unit.generator.SimpleItemBehaviorCompilerTest --tests net.mcreator.integration.generator.RemarkSimpleItemBehaviorTest.formRoundTripPersistsAndUnsupportedMigrationIsBlocked --tests net.mcreator.integration.generator.RemarkSimpleItemBehaviorTest.itemEditorRetainsBehaviorAndReportsConflicts --tests net.mcreator.integration.generator.RemarkSimpleItemBehaviorTest.formRejectsInvalidTextAndPreservesUnknownSettings --tests net.mcreator.integration.generator.RemarkPinnedNeoForgeGeneratorTest.pinnedGeneratorAndRemarkFeaturesAreAvailable --no-daemon --max-workers=1
```

The opt-in `buildsPlayableExampleWithPinnedTarget` integration test creates a new example only when
`REMARK_FIRST_MOD_BUILD=true` and `REMARK_FIRST_MOD_OUTPUT` points at an unused directory.
It intentionally refuses to overwrite an existing workspace.

The delivered example also has an opt-in `-PremarkRuntimeProof` Gradle hook. From that example,
with `JAVA_HOME` set to Java 21 and `GRADLE_USER_HOME` set to the existing `.mcreator/gradle` cache,
run `gradlew.bat runNoCodeGameTest -PremarkRuntimeProof --no-daemon --no-configuration-cache --max-workers=1`.
On this machine Gradle's JavaExec launch failed with Windows CreateProcess error 5 after preparing
the game. The supplied `RUN_RUNTIME_PROOF.ps1` launched the same prepared target successfully:

```powershell
# Run from the Studio checkout after the Gradle preparation above.
.\src\test\resources\no-code\RUN_RUNTIME_PROOF.ps1 -ExampleRoot 'R:\examples\speed-wand-20260913' -JavaRuntime 'C:\Users\User\.mcreator\gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
```

The proof uses only its isolated `run-proof` world. Its hook is test-only and may be removed by
normal workspace build-file regeneration; it is not needed for ordinary editing/build/export.

## Remaining gates and limits

- Not yet verified: real client rendering/input, particles as seen by clients, packaged-JAR launch,
  two-client networking, reconnect/respawn/dimension scenarios, high DPI and sustained performance.
- No FPS, tick-time, memory or network benchmark was performed. No zero-lag guarantee.
- Full legacy test suite was not rerun; the 9-test result is deliberately scoped.
- The new behavior is a single speed preset, not the complete general behavior/graph editor.
- Food, charged/ranged use, item inventory and existing air-right-click procedures are incompatible.
  Errors block save/build rather than silently changing or deleting those settings.
- Cooldowns use vanilla per-player/per-item-type storage, shared across stacks and hands.
  They are not persisted across reconnect/respawn. Block interaction can consume right-click first.
- Existing general project import, atomic-save/recovery and tool-download UX need separate audits.
- Cached builds were used, but the asset preparation fetched an index even with `--offline`.
  Do not claim first-build or asset preparation is universally offline.
- Next priority: manual isolated-client acceptance, then guided first-mod UX and block/recipe tests.
  Port to another target only after that adapter's own compile/runtime gates pass.
