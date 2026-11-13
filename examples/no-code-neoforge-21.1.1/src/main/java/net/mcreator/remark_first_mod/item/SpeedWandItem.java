package net.mcreator.remark_first_mod.item;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;

public class SpeedWandItem extends Item {
	public SpeedWandItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level world, Player entity, InteractionHand hand) {
		ItemStack stack = entity.getItemInHand(hand);
		if (entity.getCooldowns().isOnCooldown(this))
			return InteractionResultHolder.fail(stack);
		if (world instanceof ServerLevel serverLevel) {
			entity.getCooldowns().addCooldown(this, 200);
			entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, false, true));
			serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, entity.getX(), entity.getY() + 1, entity.getZ(), 8, 0.35, 0.45, 0.35, 0.01);
		}
		return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
	}
}