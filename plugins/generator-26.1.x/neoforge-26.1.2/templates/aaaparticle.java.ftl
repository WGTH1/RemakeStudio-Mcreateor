<#-- @formatter:off -->
package ${package}.aaaparticle;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import mod.chloeprime.aaaparticles.common.util.Basis;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class ${name}AAAParticle {
	public static final Identifier EFFECT = Identifier.fromNamespaceAndPath("${modid}", "${data.getSafeEffectPath()}");
	private static final String COMMAND_NAME = "${registryname}";
	private static final float ROTATION_X = ${data.getRotationXRadians()}f;
	private static final float ROTATION_Y = ${data.getRotationYRadians()}f;
	private static final float ROTATION_Z = ${data.getRotationZRadians()}f;

	@SubscribeEvent public static void registerCommand(RegisterCommandsEvent event) {
		${JavaModName}AAAParticleCommands.register(event.getDispatcher(), "${modid}:${registryname}",
${data.force?c}, ${(data.getSpawnModeSafe() != "world")?c}, ${data.unlimitedDistance?c}, ${name}AAAParticle::create);
		event.getDispatcher().register(Commands.literal("aaaparticle")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal(COMMAND_NAME)
						.executes(${name}AAAParticle::spawnAtSource)
						.then(Commands.literal("at")
								.then(Commands.argument("pos", Vec3Argument.vec3())
										.executes(arguments -> spawnAt(arguments, Vec3Argument.getVec3(arguments, "pos")))))
						.then(Commands.literal("on")
								.then(Commands.argument("target", EntityArgument.entity())
										.executes(arguments -> spawnOnEntity(arguments, EntityArgument.getEntity(arguments, "target")))))));
	}

	private static ParticleEmitterInfo createBase(Level level) {
		ParticleEmitterInfo info = ParticleEmitterInfo.create(level, EFFECT);
<#if data.scaleX != 1 || data.scaleY != 1 || data.scaleZ != 1>
		info.scale(${data.scaleX}f, ${data.scaleY}f, ${data.scaleZ}f);
</#if>
<#if data.speed != 1>
		info.speed(${data.speed}f);
</#if>
		return info;
	}

	public static ParticleEmitterInfo create(Level level, double x, double y, double z) {
		return createWorld(level, x, y, z);
	}

	public static ParticleEmitterInfo create(Level level, double x, double y, double z, Entity source) {
		if (source == null)
			return createWorld(level, x, y, z);
<#assign spawnMode = data.getSpawnModeSafe()>
<#if spawnMode == "source_facing">
		return createFacingSource(level, x, y, z, source);
<#elseif spawnMode == "source_follow_view">
		return createFollowSourceView(level, x, y, z, source);
<#else>
		return createWorld(level, x, y, z);
</#if>
	}

	public static ParticleEmitterInfo createWorld(Level level, double x, double y, double z) {
		return createBase(level)
				.position(x + ${data.offsetX}d, y + ${data.offsetY}d, z + ${data.offsetZ}d)
				.rotation(ROTATION_X, ROTATION_Y, ROTATION_Z);
	}

	// Offsets are right/up/forward. Cancel AAA's roll on position, not on the effect.
	private static Vec3 headSpaceOffset() {
		double cos = Math.cos(ROTATION_Z);
		double sin = Math.sin(ROTATION_Z);
		return new Vec3(${data.offsetX}d * cos + ${data.offsetY}d * sin,
				-(${data.offsetX}d) * sin + ${data.offsetY}d * cos, -(${data.offsetZ}d));
	}

	public static ParticleEmitterInfo createFacingSource(Level level, double x, double y, double z, Entity source) {
		if (source == null)
			return createWorld(level, x, y, z);
		float pitch = (float) Math.toRadians(source.getViewXRot(1.0f));
		float yaw = (float) Math.toRadians(source.getViewYRot(1.0f));
		Vec3 offset = Basis.fromEuler(new Vec3(-pitch, (float) Math.PI - yaw, ROTATION_Z))
				.toGlobal(headSpaceOffset());
		return createBase(level)
				.position(x + offset.x, y + offset.y, z + offset.z)
				.rotation(ROTATION_X + pitch, ROTATION_Y - yaw, ROTATION_Z);
	}

	public static ParticleEmitterInfo createFollowSourceView(Level level, Entity source) {
		return createFollowSourceView(level, source.getX(), source.getY() + source.getEyeHeight(), source.getZ(), source);
	}

	public static ParticleEmitterInfo createFollowSourceView(Level level, double x, double y, double z, Entity source) {
		if (source == null)
			return createWorld(level, x, y, z);
		return createBase(level)
				.bindOnEntity(source)
				.useEntityHeadSpace()
				.position(x - source.getX(), y - source.getY() - source.getEyeHeight(), z - source.getZ())
				.entitySpaceRelativePosition(headSpaceOffset())
				.rotation(ROTATION_X, ROTATION_Y, ROTATION_Z);
	}

	public static void spawn(Level level, double x, double y, double z) {
		AAALevel.addParticle(level, <#if data.unlimitedDistance>Double.POSITIVE_INFINITY<#else>${data.force?c}</#if>, create(level, x, y, z));
	}

	public static void spawn(Level level, double x, double y, double z, Entity source) {
		AAALevel.addParticle(level, <#if data.unlimitedDistance>Double.POSITIVE_INFINITY<#else>${data.force?c}</#if>, create(level, x, y, z, source));
	}

	public static void spawnOnSource(Level level, Entity source) {
		if (source != null)
			spawn(level, source.getX(), source.getY() + source.getEyeHeight(), source.getZ(), source);
	}

	private static int spawnAtSource(CommandContext<CommandSourceStack> arguments) {
		CommandSourceStack source = arguments.getSource();
		return spawnAt(arguments, source.getPosition());
	}

	private static int spawnAt(CommandContext<CommandSourceStack> arguments, Vec3 pos) {
		CommandSourceStack source = arguments.getSource();
		spawn(source.getLevel(), pos.x, pos.y, pos.z, source.getEntity());
		source.sendSuccess(() -> Component.literal("Spawned AAA particle: ${modid}:${data.getSafeEffectPath()}"), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int spawnOnEntity(CommandContext<CommandSourceStack> arguments, Entity target) {
		spawnOnSource(arguments.getSource().getLevel(), target);
		arguments.getSource().sendSuccess(() -> Component.literal("Spawned AAA particle on " + target.getDisplayName().getString() + ": ${modid}:${data.getSafeEffectPath()}"), false);
		return Command.SINGLE_SUCCESS;
	}
}
<#-- @formatter:on -->
