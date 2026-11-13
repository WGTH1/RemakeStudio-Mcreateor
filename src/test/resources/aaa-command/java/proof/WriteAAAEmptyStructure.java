package proof;

import net.minecraft.nbt.*;
import java.io.File;
import java.nio.file.Files;

public final class WriteAAAEmptyStructure {
    public static void main(String[] args) throws Exception {
        CompoundTag structure = new CompoundTag();
        structure.putInt("DataVersion", 3465);
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
        File output = new File(args[0]);
        Files.createDirectories(output.toPath().getParent());
        NbtIo.writeCompressed(structure, output);
    }
}
