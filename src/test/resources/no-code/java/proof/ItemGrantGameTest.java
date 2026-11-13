package proof;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.mcreator.remark_first_mod.init.RemarkFirstModModItems;

@GameTestHolder("remark_first_mod")
@PrefixGameTestTemplate(false)
public class ItemGrantGameTest {
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void grantsCurrentItemOnceOnFirstEntry(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        helper.onEachTick(player.connection::tick);
        var item = RemarkFirstModModItems.SPEED_WAND.get();
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(player.getInventory().countItem(item) == 1,
                    "The isolated-test advancement must grant exactly one current item");
            player.getInventory().clearContent();
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(player.getInventory().countItem(item) == 0,
                    "The test item must not be re-granted on later ticks");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }
}
