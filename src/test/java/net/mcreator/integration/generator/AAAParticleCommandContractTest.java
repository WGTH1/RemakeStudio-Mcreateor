package net.mcreator.integration.generator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.*;

/** Real Brigadier parsing; Minecraft entities and AAA packet transport are fixtures, not a renderer. */
public class AAAParticleCommandContractTest {
    @TempDir Path temporary;

    @Test void bundledCommandsUseThePublicLevelRandomAccessor() throws Exception {
        List<String> templates = AAAParticleTransformContractTest.templates("aaaparticle_commands.java.ftl");
        assertEquals(6, templates.size());
        for (String template : templates) {
            assertFalse(template.contains("level.random"), "Level.random is protected in Minecraft 26.1.2");
            assertTrue(template.contains("level.getRandom().nextGaussian()"));
        }
    }

    @Test void particleAliasesUseTheExecutingEntityAcrossAllSixGenerators() throws Exception {
        int index = 0;
        for (String template : AAAParticleTransformContractTest.templates("aaaparticle_commands.java.ftl")) {
            String name = "Generated" + index++ + "AAAParticleCommands";
            String code = template.replace("${package}", "proof").replace("${JavaModName}", name.replace("AAAParticleCommands", ""))
                    .lines().filter(line -> !line.startsWith("<#") && !line.startsWith("import net.minecraft.")
                            && !line.startsWith("import mod.chloeprime.") && !line.startsWith("import dev.architectury."))
                    .collect(Collectors.joining("\n"))
                    .replace("package proof.aaaparticle;", "package proof.aaaparticle;\nimport " + getClass().getName() + ".*;");
            Path file = temporary.resolve(name + ".java");
            Files.writeString(file, code);
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath",
                    System.getProperty("java.class.path"), "-d", temporary.toString(), file.toString()));
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, getClass().getClassLoader())) {
                Class<?> generated = loader.loadClass("proof.aaaparticle." + name);
                Class<?> factoryType = loader.loadClass(generated.getName() + "$EmitterFactory");
                var factory = Proxy.newProxyInstance(loader, new Class<?>[]{factoryType}, (proxy, method, args) ->
                        new ParticleEmitterInfo((Entity) args[4], new Vec3((double) args[1], (double) args[2], (double) args[3])));
                var dispatcher = vanillaTree();
                var register = generated.getMethod("register", CommandDispatcher.class, String.class, boolean.class, boolean.class, factoryType);
                register.invoke(null, dispatcher, "proof:face", true, true, factory);
                register.invoke(null, dispatcher, "proof:follow", true, true, factory);
                register.invoke(null, dispatcher, "proof:world", true, false, factory);
                generated.getMethod("register", CommandDispatcher.class, String.class, boolean.class, boolean.class,
                        boolean.class, factoryType).invoke(null, dispatcher, "proof:unlimited", true, false, true, factory);
                var alias = dispatcher.getRoot().getChild("particle").getChild("proof:face");
                var vanilla = dispatcher.getRoot().getChild("particle").getChild("name");
                assertEquals(vanilla.getChildren().stream().map(n -> n.getName()).toList(),
                        alias.getChildren().stream().map(n -> n.getName()).toList());

                Level level = new Level();
                Entity emitter = new Entity();
                Entity otherExecutor = new Entity();
                ServerPlayer near = new ServerPlayer(level, new Vec3(1000, 50, 1000));
                ServerPlayer far = new ServerPlayer(level, new Vec3(1100, 50, 1000));
                ServerPlayer otherDimension = new ServerPlayer(new Level(), near.position());
                CommandSourceStack source = new CommandSourceStack(level, emitter, near.position(), List.of(near, far, otherDimension), 2);
                ModNetwork.CHANNEL.sent.clear();
                assertEquals(2, dispatcher.execute("particle proof:face", source));
                assertEquals(2, ModNetwork.CHANNEL.sent.size());
                assertTrue(ModNetwork.CHANNEL.sent.stream().allMatch(sent -> sent.packet.info.source == emitter));
                assertSame(ModNetwork.CHANNEL.sent.get(0).packet, ModNetwork.CHANNEL.sent.get(1).packet);

                ModNetwork.CHANNEL.sent.clear();
                dispatcher.execute("particle proof:follow", source.withEntity(otherExecutor));
                assertTrue(ModNetwork.CHANNEL.sent.stream().allMatch(sent -> sent.packet.info.source == otherExecutor));
                assertEquals(2, ModNetwork.CHANNEL.sent.size(), "One emitter per viewer, not per client-side guess");

                ModNetwork.CHANNEL.sent.clear();
                assertEquals(1, dispatcher.execute("particle proof:face 1000 50 1000 0 0 0 0 1 normal", source));
                assertSame(near, ModNetwork.CHANNEL.sent.getFirst().viewer);
                ModNetwork.CHANNEL.sent.clear();
                assertEquals(1, dispatcher.execute("particle proof:face 1000 50 1000 0 0 0 0 0 force viewers", source));
                assertSame(far, ModNetwork.CHANNEL.sent.getFirst().viewer);
                assertSame(emitter, ModNetwork.CHANNEL.sent.getFirst().packet.info.source, "Viewer must not become source");

                ModNetwork.CHANNEL.sent.clear();
                dispatcher.execute("particle proof:face 1000 50 1000 1 2 3 0 3 force", source);
                assertEquals(6, ModNetwork.CHANNEL.sent.size());
                for (int i = 0; i < 6; i += 2)
                    assertSame(ModNetwork.CHANNEL.sent.get(i).packet, ModNetwork.CHANNEL.sent.get(i + 1).packet,
                            "Random spread must be identical for both viewers");

                ModNetwork.CHANNEL.sent.clear();
                var error = assertThrows(CommandSyntaxException.class,
                        () -> dispatcher.execute("particle proof:face", source.withEntity(null)));
                assertTrue(error.getMessage().contains("execute as"));
                assertTrue(ModNetwork.CHANNEL.sent.isEmpty());
                assertEquals(2, dispatcher.execute("particle proof:world", source.withEntity(null)));
                assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("particle proof:face",
                        new CommandSourceStack(level, emitter, near.position(), source.players, 0)));
                assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(
                        "particle proof:face 1000 50 1000 0 0 0", source), "Incomplete vanilla syntax must stay invalid");
                assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(
                        "particle proof:face 1000 50 1000 0 0 0 0 -1", source));

                ModNetwork.CHANNEL.sent.clear();
                assertEquals(77, dispatcher.execute("particle flame", source));
                assertTrue(ModNetwork.CHANNEL.sent.isEmpty(), "Unrelated vanilla particles must not be intercepted");

                ServerPlayer distant = new ServerPlayer(level, new Vec3(1000000, 50, 1000));
                var distantSource = new CommandSourceStack(level, emitter, near.position(),
                        List.of(near, distant, otherDimension), 2);
                ModNetwork.CHANNEL.sent.clear();
                assertEquals(2, dispatcher.execute("particle proof:unlimited", distantSource));
                assertEquals(2, ModNetwork.CHANNEL.sent.size(), "Unlimited delivery must not retain AAA's 512 block cap");
                assertSame(distant, ModNetwork.CHANNEL.sent.get(1).viewer);
                assertSame(ModNetwork.CHANNEL.sent.get(0).packet, ModNetwork.CHANNEL.sent.get(1).packet);
                ModNetwork.CHANNEL.sent.clear();
                assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(
                        "particle proof:unlimited 1000 50 1000 0 0 0 0 65", distantSource));
                assertTrue(ModNetwork.CHANNEL.sent.isEmpty(), "Over-budget requests must not partially spawn");
                level.time++;
                dispatcher.execute("particle proof:unlimited 1000 50 1000 0 0 0 0 64", distantSource);
                assertEquals(128, ModNetwork.CHANNEL.sent.size());
                ModNetwork.CHANNEL.sent.clear();
                assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("particle proof:unlimited", distantSource));
                assertTrue(ModNetwork.CHANNEL.sent.isEmpty());
                level.time++;
                assertEquals(2, dispatcher.execute("particle proof:unlimited", distantSource));
            }
        }
        assertEquals(6, index);
    }

    private CommandDispatcher<CommandSourceStack> vanillaTree() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        Command<CommandSourceStack> original = context -> 77;
        ArgumentType<Vec3> coordinates = reader -> {
            double x = reader.readDouble(); reader.skipWhitespace();
            double y = reader.readDouble(); reader.skipWhitespace();
            return new Vec3(x, y, reader.readDouble());
        };
        ArgumentType<List<ServerPlayer>> viewers = reader -> {
            reader.readUnquotedString();
            return selectedViewers;
        };
        var count = AAAParticleCommandContractTest.<Integer>arg("count", IntegerArgumentType.integer(0)).executes(original);
        for (String mode : List.of("force", "normal"))
            count.then(Commands.literal(mode).executes(original)
                    .then(AAAParticleCommandContractTest.<List<ServerPlayer>>arg("viewers", viewers).executes(original)));
        dispatcher.register(Commands.literal("particle").requires(source -> source.permission >= 2)
                .then(AAAParticleCommandContractTest.<String>arg("name", StringArgumentType.word()).executes(original)
                        .then(AAAParticleCommandContractTest.<Vec3>arg("pos", coordinates).executes(original)
                                .then(AAAParticleCommandContractTest.<Vec3>arg("delta", coordinates)
                                        .then(AAAParticleCommandContractTest.<Float>arg("speed", FloatArgumentType.floatArg(0)).then(count))))));
        return dispatcher;
    }

    private static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, T> arg(String name, ArgumentType<T> type) {
        return argument(name, type);
    }

    private static List<ServerPlayer> selectedViewers;
    public static class Entity {}
    public static class Level {
        protected final Random random = new Random(42);
        public long time;
        public Random getRandom() { return random; }
        public long getGameTime() { return time; }
    }
    public static class Vec3 {
        public static final Vec3 ZERO = new Vec3(0, 0, 0);
        public final double x, y, z;
        public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }
        public double distanceToSqr(Vec3 other) {
            return Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2);
        }
    }
    public record ServerPlayer(Level level, Vec3 position) {}
    public record PlayerList(List<ServerPlayer> getPlayers) {}
    public record Server(PlayerList getPlayerList) {}
    public record CommandSourceStack(Level level, Entity entity, Vec3 position, List<ServerPlayer> players, int permission) {
        public CommandSourceStack { selectedViewers = List.of(players.get(1)); }
        public Entity getEntity() { return entity; }
        public Level getLevel() { return level; }
        public Vec3 getPosition() { return position; }
        public Server getServer() { return new Server(new PlayerList(players)); }
        public void sendSuccess(Supplier<Component> message, boolean broadcast) { message.get(); }
        public CommandSourceStack withEntity(Entity source) { return new CommandSourceStack(level, source, position, players, permission); }
    }
    public static class Commands {
        public static LiteralArgumentBuilder<CommandSourceStack> literal(String name) { return LiteralArgumentBuilder.literal(name); }
    }
    public static class Vec3Argument {
        public static Vec3 getVec3(CommandContext<CommandSourceStack> context, String name) { return context.getArgument(name, Vec3.class); }
    }
    public static class EntityArgument {
        @SuppressWarnings("unchecked")
        public static Collection<ServerPlayer> getPlayers(CommandContext<CommandSourceStack> context, String name) {
            return context.getArgument(name, List.class);
        }
    }
    public record Component(String getString) implements Message {
        public static Component literal(String text) { return new Component(text); }
        public static Component translatable(String key, Object... args) { return new Component(key); }
    }
    public record ParticleEmitterInfo(Entity source, Vec3 origin) {}
    public record S2CAddParticle(ParticleEmitterInfo info) {
        public static S2CAddParticle of(ParticleEmitterInfo info) { return new S2CAddParticle(info); }
    }
    public record Sent(ServerPlayer viewer, S2CAddParticle packet) {}
    public static class Channel {
        public final List<Sent> sent = new ArrayList<>();
        public void sendToPlayer(ServerPlayer player, S2CAddParticle packet) { sent.add(new Sent(player, packet)); }
    }
    public static class ModNetwork { public static final Channel CHANNEL = new Channel(); }
    public static class NetworkManager {
        public static void sendToPlayer(ServerPlayer player, S2CAddParticle packet) { ModNetwork.CHANNEL.sendToPlayer(player, packet); }
    }
}
