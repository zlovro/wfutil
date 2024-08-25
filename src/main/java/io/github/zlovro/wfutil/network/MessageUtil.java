package io.github.zlovro.wfutil.network;

import net.minecraft.network.PacketBuffer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MessageUtil
{
    public static <T> void writeList(ArrayList<T> collection, PacketBuffer buf, Consumer<T> forEachAction)
    {
        buf.writeShort(collection.size());
        collection.forEach(forEachAction);
    }

    public static <T> ArrayList<T> readList(PacketBuffer buf, Supplier<T> adder)
    {
        ArrayList<T> list = new ArrayList<>();
        int count = buf.readShort();
        for (int i = 0; i < count; i++)
        {
            list.add(adder.get());
        }
        return list;
    }
}
