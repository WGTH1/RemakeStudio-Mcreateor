package proof;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import io.netty.channel.embedded.EmbeddedChannel;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sundayzy")
@PrefixGameTestTemplate(false)
public class AAACommandGameTest {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void particleCommandUsesServerSource(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "aaa-test-player"));
        // Forge login hooks need a channel before placeNewPlayer, unlike the vanilla mock helper.
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        connection.setProtocol(ConnectionProtocol.PLAY);
        try {
            server.getPlayerList().placeNewPlayer(connection, player);
            player.setYRot(0);
            player.setXRot(0);
            var dispatcher = server.getCommands().getDispatcher();
            helper.assertTrue(dispatcher.getRoot().getChild("particle").getChild("sundayzy:skill_1") != null,
                    "The actual skill_1 alias must be registered during Forge startup");
            CommandSourceStack source = player.createCommandSourceStack().withPermission(2);
            helper.assertTrue(dispatcher.execute("particle sundayzy:skill_1", source) == 1,
                    "The actual effect must send to the mock player without a vanilla particle packet");

            Class<?> commands = Class.forName("net.mcreator.sundayzy.aaaparticle.SundayzyModAAAParticleCommands");
            Class<?> factory = Class.forName(commands.getName() + "$EmitterFactory");
            AtomicReference<Entity> observedEntity = new AtomicReference<>();
            AtomicReference<Vec3> observedPosition = new AtomicReference<>();
            Object probe = Proxy.newProxyInstance(commands.getClassLoader(), new Class<?>[]{factory}, (proxy, method, args) -> {
                observedEntity.set((Entity) args[4]);
                Vec3 position = new Vec3((double) args[1], (double) args[2], (double) args[3]);
                observedPosition.set(position);
                return ParticleEmitterInfo.create((Level) args[0], new ResourceLocation("sundayzy", "seraphic_detonation"))
                        .position(position.x, position.y, position.z);
            });
            commands.getMethod("register", CommandDispatcher.class, String.class, boolean.class, boolean.class, factory)
                    .invoke(null, dispatcher, "aaa_command_probe:face", true, true, probe);
            dispatcher.execute("execute as " + player.getUUID() + " at @s run particle aaa_command_probe:face ^ ^ ^1",
                    server.createCommandSourceStack());
            helper.assertTrue(observedEntity.get() == player, "execute as must select the executor, not a viewer");
            helper.assertTrue(observedPosition.get().distanceToSqr(player.position().add(0, 0, 1)) < 0.00001,
                    "Local coordinates must use the real Minecraft coordinate parser");
            try {
                dispatcher.execute("particle sundayzy:skill_1", server.createCommandSourceStack());
                helper.fail("Source-less Face must fail clearly");
            } catch (CommandSyntaxException expected) {
                helper.assertTrue(expected.getMessage().contains("execute as"), "Error must explain how to supply a source");
            }
            System.out.println("AAA_COMMAND_RUNTIME_PROOF=skill_1,execute_as,local_coordinates,server_packet,missing_source");
            helper.succeed();
        } catch (Exception failure) {
            failure.printStackTrace();
            throw failure;
        } finally {
            server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
