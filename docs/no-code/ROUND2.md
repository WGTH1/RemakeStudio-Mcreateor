# No-Code Workflow Update

Date: 2026-09-13. Scope: requested items 1, 3, 4, 5 and 6, plus selectable NeoForge 21.1 builds.
This is an update to the existing Studio, not a new standalone application or installer.

## Try the update

Close the older Studio instance, then open `RUN_REMAKE_STUDIO.bat` in this checkout.
The executable installed in Program Files has NOT been replaced or repackaged.

1. Open an Item. The first page now combines name, texture, stack size, model, display transforms,
   creative tabs and simple behavior. The second page contains the existing advanced controls.
   The controls still read/write the same Item fields; switching pages does not convert the item.
2. When validation fails, select an error and choose **Go to setting**. The Doctor section of
   Remake Studio Center also has an actionable error list. It reuses an already-open editor
   without discarding unsaved changes. Compiler errors without a mapped form field open the
   relevant element but still require inspecting the build error.
3. **Test this item** on the Item toolbar snapshots the project and the current item's editor
   values into a temporary workspace, including unsaved changes to that item. It prepares and
   starts a separate Minecraft client. Create a new test world on first launch; the item is
   granted automatically once. Stop cancels the Gradle operation. Original worlds are excluded.
   Changes made afterward require a new test session; this is not live code/resource syncing.
   Other open editors' unsaved values are not included. Test copies and logs remain at the path
   shown in the dialog for diagnosis. They are not included in normal mod exports.
4. In the workspace context menu, **Rename safely** preserves the item's in-game registry ID.
   Rename/delete checks references and takes a persistent recovery snapshot before modification.
   Close editing tabs first. References from other elements, unmanaged tags, self references
   during rename, and known manual/code-locked elements stop the operation for review.
   This version does not rewrite references or arbitrary handwritten Java automatically.
   **Open pre-change backup** opens a separate recovered project, not an overwrite of the current one.
5. Remake Studio Center -> Assets shows duplicate content, large files, missing Item textures
   and potential unused files. Select individual eligible rows to quarantine, then use
   **Restore last cleanup** to recover them. Changing the search clears selections, including
   hidden rows. The operation rescans references/hashes before moving files. Files are not
   resized, re-encoded or permanently deleted. The Asset Doctor remains available for additional
   model/resource checks; Import and Undo import are retained.

## Minecraft 1.21.1 Versions

Minecraft **1.21.1** and NeoForge **21.1.219** are separate version numbers.

Remake Studio Center -> Home -> **Loader version...**:

1. Save and close element editors.
2. Fetch the official published version list and select the required stable `21.1.x` build.
3. Apply and rebuild. The entire project is first generated/built in a separate copy.
4. Only a successful build changes the original project setting, with a recovery backup.
   Cancel leaves the original version unchanged. A failed apply attempts to restore its previous setting.

Both existing NeoForge 1.21.1 adapters accept this setting. The legacy adapter name may still
contain `.219`; the selector, Home label and build console show the actual selected build.
The choice is saved per project and does not leak into another loader when switching targets.
There is no automatic silent update to the latest build.

The generated dependency range is `[selectedVersion,21.2)`. Choosing `21.1.219` therefore does
not make that JAR load on `21.1.1`. Build against the earliest version required and validate
the APIs/dependencies used by that particular project. A successful build is not runtime or
performance certification for every intervening version, dependency or feature combination.

Official catalog: https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge
The catalog query during this update found 243 stable 21.1 builds, from 21.1.1 to 21.1.250.

**Not complete:** Forge and Fabric adapters for Minecraft 1.21.1. The installed adapters are
currently for other Minecraft versions. Their existing behavior is left unchanged; this update
does not mark them compatible by changing metadata. Forge, NeoForge and Fabric do not share
one interchangeable output JAR. Porting/testing those two 1.21.1 adapters remains separate work.

## Verification

- Application: 30 tests passed, 0 failed, 1 opt-in example-build test skipped in the final run.
  Includes the two-page editor on the existing Forge 1.20.1, Fabric 1.20.1, NeoForge 26.1.2
  and generic/pinned NeoForge 1.21.1 adapters; this is form compatibility, not a mod runtime
  certification for every adapter. The app JAR task also passed.
- Generated mod builds passed on NeoForge 21.1.1 and 21.1.250.
- Dedicated Minecraft GameTest: 2/2 passed on each endpoint, process exit 0. Tests cover speed
  duration/amplifier, cooldown/reuse across hands and automatic one-time test-item granting.
- UI screenshot: `build/no-code-proof/simple-item-round2.png`, inspected at 900 x 660.
- Normal runtime JAR inspection: no `proof/` classes, test-grant datapack or test structure
  included; metadata contains the selected minimum NeoForge build and upper bound 21.2.

Example JARs in each example's `build/libs/modid-1.0.jar`:

| Build | Bytes | SHA-256 |
| --- | ---: | --- |
| 21.1.1 | 9886 | A584818EDA8A0B7AE9DD4B35A0633EBB302B1A6177E95D907B14B3E81D227AA7 |
| 21.1.250 | 9890 | 8CA830AB7E71999CB129712E564B430650294582F50539C5704C76C93DA00DBC |

See `build/no-code-round2-tests.log`, the JUnit XML in `build/test-results/test`, and the isolated
examples `examples/no-code-neoforge-21.1.1` / `examples/no-code-neoforge-21.1.250`.
The first attempt at the earliest target produced a successful mod build, but the app's strict
log-based test guard failed on the upstream `Cannot inject duplicate file mcp/client/Start.class`
message. Subsequent direct build/runtime results are recorded separately, not treated as a pass
for that first test invocation.

The runtime harness uses actual Minecraft dedicated GameTest servers and mock players, not
two real clients. Runtime logs are `endpoint-runtime.log` in each isolated example.
Test-only classes/resources are kept out of the normal main JAR.

Not verified: the complete legacy suite, all 243 loader builds, the full AAA/fashion/UI matrix
on early loader APIs, actual client input/rendering for the new test button, two-client networking,
high-DPI layouts, packaged installer launch, or performance/FPS under load.

## Reproduce

Use this checkout's existing ASCII drive mapping; the commands here used R:.
Do not remap a drive in use or run the batch launcher while these tests use it.

```powershell
$env:JAVA_HOME = 'R:\jdk\jbr25_win_64'
.\gradlew.bat test --tests net.mcreator.integration.generator.RemarkWorkflowTest --tests net.mcreator.unit.generator.SimpleItemBehaviorCompilerTest --tests net.mcreator.integration.generator.RemarkSimpleItemBehaviorTest --tests net.mcreator.integration.generator.RemarkPinnedNeoForgeGeneratorTest.pinnedGeneratorAndRemarkFeaturesAreAvailable --tests net.mcreator.integration.workspace.ReferencesFinderTest jar --no-daemon --max-workers=1
```

The opt-in example-build test accepts `REMARK_FIRST_MOD_TARGET`, `REMARK_FIRST_MOD_LOADER_VERSION`,
`REMARK_FIRST_MOD_BUILD=true`, `REMARK_FIRST_MOD_ONLINE=true`, and `REMARK_FIRST_MOD_OUTPUT`.
The output must be an unused directory; the test refuses to overwrite an existing project.

Run the app tests first to generate the exact isolated-item grant resources used by runtime checks.
Then, from either isolated example with Java 21 and the existing Gradle cache:

```powershell
$env:JAVA_HOME = 'C:\Users\User\.mcreator\gradle\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:GRADLE_USER_HOME = 'C:\Users\User\.mcreator\gradle'
.\gradlew.bat build runtimeProofClasses prepareNoCodeGameTest '-PremarkRuntimeProof' -x writeProofStructure --no-daemon --max-workers=1
& 'R:\src\test\resources\no-code\RUN_RUNTIME_PROOF.ps1' -ExampleRoot $PWD.Path -JavaRuntime $env:JAVA_HOME -NeoForgeVersion '21.1.1'
```

Use `21.1.250` for the other example. The PowerShell runner prepares the test structure via Java
and argument files because Gradle JavaExec fails to spawn Java with error 5 on this machine.
It does not modify normal worlds. Regenerating an example's Gradle files may remove its opt-in
proof hook; the hook is not needed for normal mod build/export.

## Recovery And Scope

Source backups: `.remarkstudio-backups/no-code-round2-20260913`.
Per-project recovery snapshots: `.remarkstudio/operation-backups`.
The latest snapshot pointer is validated before opening. Known code-locked elements prevent
destructive operations, but arbitrary external edits are not a complete static reference graph.
Review resource quarantine selections and keep the recovery copy for important projects.

No user world, old effect asset, unrelated plugin or installed executable was deleted.
Unselected preset-library and release-packaging features were not added in this round.
