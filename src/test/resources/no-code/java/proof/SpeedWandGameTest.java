package proof;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.mcreator.remark_first_mod.init.RemarkFirstModModItems;

@GameTestHolder("remark_first_mod")
@PrefixGameTestTemplate(false)
public class SpeedWandGameTest {
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void appliesSpeedAndRejectsReuseUntilCooldownExpires(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        // Embedded test connections are not ticked by the real network listener.
        helper.onEachTick(player.connection::tick);
        var item = RemarkFirstModModItems.SPEED_WAND.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(item));
        var first = item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.getResult().consumesAction(), "First use must succeed");
        var effect = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(effect != null && effect.getDuration() == 100 && effect.getAmplifier() == 0,
                "The generated item must apply speed I for 100 ticks");
        helper.assertTrue(player.getCooldowns().isOnCooldown(item), "Cooldown must start");
        helper.assertTrue(item.use(helper.getLevel(), player, InteractionHand.OFF_HAND).getResult() == InteractionResult.FAIL,
                "Changing hands must not bypass the cooldown");
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(!player.hasEffect(MobEffects.MOVEMENT_SPEED), "Speed must expire");
            helper.assertTrue(player.getCooldowns().isOnCooldown(item), "Cooldown must outlast the effect");
            helper.assertTrue(item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult() == InteractionResult.FAIL,
                    "Repeated use while cooling down must fail");
        });
        helper.runAfterDelay(210, () -> {
            helper.assertTrue(!player.getCooldowns().isOnCooldown(item), "Cooldown must expire");
            helper.assertTrue(item.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                    "Item must be usable again");
            helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "Second accepted use must apply speed");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }
}
