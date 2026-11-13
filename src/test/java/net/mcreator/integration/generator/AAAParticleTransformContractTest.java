package net.mcreator.integration.generator;

import freemarker.template.Template;
import net.mcreator.generator.template.base.DefaultFreemarkerConfiguration;
import net.mcreator.element.types.AAAParticle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Executes generated transform methods; the fixture models AAA's head-space API, not its renderer. */
public class AAAParticleTransformContractTest {
    @TempDir Path temporary;

    @Test void faceAndFollowAgreeAcrossAllBundledTemplates() throws Exception {
        int index = 0;
        for (String template : templates("aaaparticle.java.ftl")) {
            AAAParticle data = new AAAParticle(null);
            data.spawnMode = "source_follow_view";
            data.offsetX = index % 2 == 0 ? 1.25 : -1.25;
            data.offsetY = 0.6;
            data.offsetZ = index % 2 == 0 ? 2.75 : -2.75;
            data.rotationX = 20;
            data.rotationY = -35;
            data.rotationZ = 67;
            data.scaleX = data.scaleY = data.scaleZ = 1;
            data.speed = 1.25;
            String methods = template.substring(template.indexOf("\tprivate static ParticleEmitterInfo createBase"),
                    template.indexOf("\tpublic static void spawn("));
            StringWriter rendered = new StringWriter();
            new Template("aaa", methods, new DefaultFreemarkerConfiguration())
                    .process(Map.of("data", data), rendered);
            String name = "GeneratedAAA" + index++;
            String code = "import " + getClass().getName() + ".*;\npublic class " + name + "{\n"
                    + "static Object EFFECT = new Object();\n"
                    + "static float ROTATION_X=" + data.getRotationXRadians() + "f, ROTATION_Y="
                    + data.getRotationYRadians() + "f, ROTATION_Z=" + data.getRotationZRadians() + "f;\n"
                    + rendered + "\n}";
            Path file = temporary.resolve(name + ".java");
            Files.writeString(file, code);
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath",
                    System.getProperty("java.class.path"), "-d", temporary.toString(), file.toString()), name);
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader())) {
                Class<?> generated = loader.loadClass(name);
                for (float yaw : new float[]{0, 90, -90, 179.9f, -179.9f})
                    for (float pitch : new float[]{0, 45, -45, 89.9f, -89.9f}) {
                        Entity source = new Entity(yaw, pitch);
                        ParticleEmitterInfo face = invoke(generated, "createFacingSource", source);
                        ParticleEmitterInfo follow = invoke(generated, "createFollowSourceView", source);
                        assertNull(face.bound);
                        assertSame(source, follow.bound);
                        assertVector(face.position, follow.resolvedPosition(), name);
                        assertVector(face.rotation, follow.resolvedRotation(), name);
                        assertEquals(1.25f, follow.speed);
                        Vec3 snapshot = face.position;
                        source.x += 3;
                        source.yaw += 25;
                        assertEquals(snapshot, face.position);
                        assertNotEquals(snapshot, follow.resolvedPosition());
                    }
                Entity source = new Entity(0, 0);
                ParticleEmitterInfo face = invoke(generated, "createFacingSource", source);
                assertVector(new Vec3(100 - data.offsetX, 20 + data.offsetY, 50 + data.offsetZ), face.position, name);
                ParticleEmitterInfo fallback = invoke(generated, "createFollowSourceView", null);
                assertVector(new Vec3(100 + data.offsetX, 20 + data.offsetY, 50 + data.offsetZ), fallback.position, name);
            }
        }
        assertEquals(6, index, "Five unpacked generators and the staged/live Fabric 26.1.2 generator");
    }

    @Test void vanillaParticleProvidersNeverGuessWhoSpawnedTheEffect() throws Exception {
        for (String template : templates("aaaparticle_particle.java.ftl")) {
            assertFalse(template.contains("getNearestPlayer"));
            assertFalse(template.contains(", source)"));
        }
    }

    @Test void particleProvidersDeclareTheirImportsWithoutAnImportCache() throws Exception {
        for (String template : templates("aaaparticle_particle.java.ftl")) {
            for (String required : List.of("net.minecraft.client.multiplayer.ClientLevel",
                    "net.minecraft.client.particle.NoRenderParticle", "net.minecraft.client.particle.Particle",
                    "net.minecraft.client.particle.ParticleProvider", "net.minecraft.core.particles.SimpleParticleType"))
                assertTrue(template.contains("import " + required + ";"), "Missing explicit import: " + required);
            if (template.contains("@OnlyIn")) {
                assertTrue(template.contains("import net.minecraftforge.api.distmarker.OnlyIn;"));
                assertTrue(template.contains("import net.minecraftforge.api.distmarker.Dist;"));
            }
            if (template.contains("RandomSource random"))
                assertTrue(template.contains("import net.minecraft.util.RandomSource;"));
        }
    }

    private ParticleEmitterInfo invoke(Class<?> generated, String method, Entity source) throws Exception {
        return (ParticleEmitterInfo) generated.getMethod(method, Level.class, double.class, double.class, double.class, Entity.class)
                .invoke(null, new Level(), 100d, 20d, 50d, source);
    }

    private static void assertVector(Vec3 expected, Vec3 actual, String name) {
        assertEquals(expected.x, actual.x, 0.00001, name + " X");
        assertEquals(expected.y, actual.y, 0.00001, name + " Y");
        assertEquals(expected.z, actual.z, 0.00001, name + " Z");
    }

    static List<String> templates(String filename) throws Exception {
        List<String> result = new ArrayList<>();
        try (var files = Files.walk(Path.of("plugins"))) {
            for (Path path : files.filter(path -> path.getFileName().toString().equals(filename)).sorted().toList())
                result.add(Files.readString(path).replace("\r\n", "\n"));
        }
        Path zip = Path.of(".remarkstudio-pending/generator-fabric-26.1.2-2026.2.zip");
        if (!Files.isRegularFile(zip)) zip = Path.of("plugins/generator-fabric-26.1.2-2026.2.zip");
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            var entry = archive.stream().filter(item -> item.getName().replace('\\', '/').endsWith("/" + filename)).findFirst().orElseThrow();
            result.add(new String(archive.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
        return result;
    }

    public static class Level {}
    public static final class Vec3 {
        public final double x, y, z;
        public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }
    public static class Entity {
        double x = 10, y = 5, z = -20;
        float yaw, pitch;
        Entity(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
        public float getViewXRot(float partial) { return pitch; }
        public float getViewYRot(float partial) { return yaw; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getEyeHeight() { return 1.62f; }
    }
    public record Basis(Vec3 euler) {
        public static Basis fromEuler(Vec3 euler) { return new Basis(euler); }
        public Vec3 toGlobal(Vec3 local) {
            double x = local.x * Math.cos(euler.z) - local.y * Math.sin(euler.z);
            double y = local.x * Math.sin(euler.z) + local.y * Math.cos(euler.z);
            double z = y * Math.sin(euler.x) + local.z * Math.cos(euler.x);
            y = y * Math.cos(euler.x) - local.z * Math.sin(euler.x);
            return new Vec3(x * Math.cos(euler.y) + z * Math.sin(euler.y), y,
                    -x * Math.sin(euler.y) + z * Math.cos(euler.y));
        }
    }
    public static class ParticleEmitterInfo {
        Entity bound;
        Vec3 position = new Vec3(0, 0, 0), rotation = new Vec3(0, 0, 0), relative;
        float speed;
        public static ParticleEmitterInfo create(Level level, Object effect) { return new ParticleEmitterInfo(); }
        public ParticleEmitterInfo scale(float x, float y, float z) { return this; }
        public ParticleEmitterInfo speed(float value) { speed = value; return this; }
        public ParticleEmitterInfo position(double x, double y, double z) { position = new Vec3(x, y, z); return this; }
        public ParticleEmitterInfo rotation(float x, float y, float z) { rotation = new Vec3(x, y, z); return this; }
        public ParticleEmitterInfo bindOnEntity(Entity entity) { bound = entity; return this; }
        public ParticleEmitterInfo useEntityHeadSpace() { return this; }
        public ParticleEmitterInfo entitySpaceRelativePosition(Vec3 value) { relative = value; return this; }
        Vec3 resolvedPosition() {
            float pitch = (float) Math.toRadians(bound.pitch), yaw = (float) Math.toRadians(bound.yaw);
            Vec3 offset = Basis.fromEuler(new Vec3(-pitch, (float) Math.PI - yaw, rotation.z)).toGlobal(relative);
            return new Vec3(bound.x + position.x + offset.x, bound.y + bound.getEyeHeight() + position.y + offset.y,
                    bound.z + position.z + offset.z);
        }
        Vec3 resolvedRotation() {
            return new Vec3((float) rotation.x + (float) Math.toRadians(bound.pitch),
                    (float) rotation.y - (float) Math.toRadians(bound.yaw), rotation.z);
        }
    }
}
