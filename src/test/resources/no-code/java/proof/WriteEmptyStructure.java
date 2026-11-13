package proof;

import net.minecraft.nbt.*;
import java.nio.file.*;

public final class WriteEmptyStructure {
    public static void main(String[] args) throws Exception {
        CompoundTag structure = new CompoundTag();
        structure.putInt("DataVersion", 3955);
        ListTag size = new ListTag();
        for (int value : new int[] {5, 3, 5}) size.add(IntTag.valueOf(value));
        structure.put("size", size);
        ListTag palette = new ListTag();
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        structure.put("palette", palette);
        structure.put("blocks", new ListTag());
        structure.put("entities", new ListTag());
        Path output = Path.of(args[0]);
        Files.createDirectories(output.getParent());
        NbtIo.writeCompressed(structure, output);
    }
}
