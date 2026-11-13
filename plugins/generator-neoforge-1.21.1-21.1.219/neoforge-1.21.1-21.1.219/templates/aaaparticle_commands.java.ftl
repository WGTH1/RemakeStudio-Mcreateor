<#-- @formatter:off -->
package ${package}.aaaparticle;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import dev.architectury.networking.NetworkManager;
import mod.chloeprime.aaaparticles.common.network.S2CAddParticle;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ${JavaModName}AAAParticleCommands {
    private static final SimpleCommandExceptionType MISSING_SOURCE = new SimpleCommandExceptionType(
            Component.literal("Face/Follow needs a source entity. Use execute as <player> at @s run particle <effect>."));
    private static final SimpleCommandExceptionType NO_VIEWERS = new SimpleCommandExceptionType(
            Component.translatable("commands.particle.failed"));
    private static final SimpleCommandExceptionType TOO_MANY = new SimpleCommandExceptionType(
            Component.literal("AAA VFX limit: at most 64 emitters per command and per dimension/tick. Use count 1 for a complete effect."));
    private static final Map<Level, SpawnBudget> BUDGETS = new WeakHashMap<>();

    private static class SpawnBudget {
        long tick = Long.MIN_VALUE;
        int emitters;
    }

    private ${JavaModName}AAAParticleCommands() {}

    @FunctionalInterface
    public interface EmitterFactory {
        ParticleEmitterInfo create(Level level, double x, double y, double z, Entity source);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, String id,
            boolean defaultForce, boolean needsSource, EmitterFactory factory) {
        register(dispatcher, id, defaultForce, needsSource, false, factory);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, String id,
            boolean defaultForce, boolean needsSource, boolean unlimitedDistance, EmitterFactory factory) {
        CommandNode<CommandSourceStack> particle = dispatcher.getRoot().getChild("particle");
        CommandNode<CommandSourceStack> name = particle == null ? null : particle.getChild("name");
        if (name == null)
            throw new IllegalStateException("Vanilla particle command is unavailable for " + id);
        Command<CommandSourceStack> command = context -> spawn(context, id, defaultForce, needsSource, unlimitedDistance, factory);
        var alias = Commands.literal(id).requires(name.getRequirement()).executes(command);
        // Reuse this Minecraft version's grammar, suggestions, coordinates, and permissions.
        for (var child : name.getChildren())
            alias.then(copyArguments(child, command));
        dispatcher.register(Commands.literal("particle").requires(particle.getRequirement()).then(alias));
    }

    private static CommandNode<CommandSourceStack> copyArguments(CommandNode<CommandSourceStack> node,
            Command<CommandSourceStack> command) {
        var builder = node.createBuilder();
        if (node.getCommand() != null)
            builder.executes(command);
        for (var child : node.getChildren())
            builder.then(copyArguments(child, command));
        return builder.build();
    }

    private static boolean has(CommandContext<CommandSourceStack> context, String name) {
        return context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals(name));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String id, boolean defaultForce,
            boolean needsSource, boolean unlimitedDistance, EmitterFactory factory) throws CommandSyntaxException {
        var source = context.getSource();
        Entity entity = source.getEntity();
        if (needsSource && entity == null)
            throw MISSING_SOURCE.create();
        Vec3 origin = has(context, "pos") ? Vec3Argument.getVec3(context, "pos") : source.getPosition();
        Vec3 delta = has(context, "delta") ? Vec3Argument.getVec3(context, "delta") : Vec3.ZERO;
        int count = has(context, "count") ? IntegerArgumentType.getInteger(context, "count") : 0;
        int emitterCount = Math.max(1, count);
        if (emitterCount > 64)
            throw TOO_MANY.create();
        boolean force = has(context, "force") || (!has(context, "normal") && defaultForce);
        Collection<ServerPlayer> viewers = has(context, "viewers") ? EntityArgument.getPlayers(context, "viewers")
                : source.getServer().getPlayerList().getPlayers();
        Set<ServerPlayer> recipients = new HashSet<>();
        var level = source.getLevel();
        List<ServerPlayer> sameDimension = viewers.stream().filter(viewer -> viewer.level() == level).toList();
        if (sameDimension.isEmpty())
            throw NO_VIEWERS.create();
        SpawnBudget budget = BUDGETS.computeIfAbsent(level, ignored -> new SpawnBudget());
        if (budget.tick != level.getGameTime()) {
            budget.tick = level.getGameTime();
            budget.emitters = 0;
        }
        if (budget.emitters + emitterCount > 64)
            throw TOO_MANY.create();
        budget.emitters += emitterCount;
        double distanceSquared = unlimitedDistance ? Double.POSITIVE_INFINITY : force ? 512d * 512d : 32d * 32d;
        for (int i = 0; i < emitterCount; i++) {
            // Vanilla count=0 is one particle; delta/speed describe velocity, not playback speed.
            Vec3 position = count == 0 || delta.x == 0 && delta.y == 0 && delta.z == 0 ? origin : origin.add(
                    level.getRandom().nextGaussian() * delta.x,
                    level.getRandom().nextGaussian() * delta.y,
                    level.getRandom().nextGaussian() * delta.z);
            S2CAddParticle packet = null;
            for (ServerPlayer viewer : sameDimension) {
                // Bound emitter packet positions are relative; range checks must use the world origin.
                if (!unlimitedDistance && viewer.position().distanceToSqr(position) > distanceSquared)
                    continue;
                if (packet == null)
                    packet = S2CAddParticle.of(factory.create(level, position.x, position.y, position.z, entity));
                NetworkManager.sendToPlayer(viewer, packet);
                recipients.add(viewer);
            }
        }
        if (recipients.isEmpty())
            throw NO_VIEWERS.create();
        source.sendSuccess(() -> Component.translatable("commands.particle.success", id), true);
        return recipients.size();
    }
}
<#-- @formatter:on -->
