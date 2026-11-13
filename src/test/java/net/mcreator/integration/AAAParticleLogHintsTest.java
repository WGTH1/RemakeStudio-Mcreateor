package net.mcreator.integration;

import net.mcreator.ui.init.L10N;
import net.mcreator.ui.remark.RemarkStudioElementTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
class AAAParticleLogHintsTest {
    @Test void missingJavaImportsAreNotReportedAsMissingAssets() {
        var hints = RemarkStudioElementTools.explainGradleLog(
                "Skill1AAAParticleProvider.java:6: error: cannot find symbol\n"
                        + "symbol: class ParticleProvider\n9 errors\nBUILD FAILED");
        assertTrue(hints.contains(L10N.t("remark.error.aaa_compile")));
        assertFalse(hints.contains(L10N.t("remark.error.aaa_assets")));
    }

    @Test void actualAssetLoadFailuresStillExplainAssetRecovery() {
        var hints = RemarkStudioElementTools.explainGradleLog(
                "[EffekAssetLoader] Failed to load effect skill.efkefc: file not found");
        assertTrue(hints.contains(L10N.t("remark.error.aaa_assets")));
        assertFalse(hints.contains(L10N.t("remark.error.aaa_compile")));
    }

    @Test void successfulEffectReferencesDoNotImplyMissingAssets() {
        assertTrue(RemarkStudioElementTools.explainGradleLog(
                "Loaded APIs: aaa_particles\nLoaded effect effeks/skill.efkefc\nBUILD SUCCESSFUL").isEmpty());
    }
}
