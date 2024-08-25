package io.github.zlovro.wfutil.client;

import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public class LocationRenderer {
    public static String text = "";

    public static void drawLocationText(RenderGameOverlayEvent event) {
        MainWindow   mainWindow   = event.getWindow();
        FontRenderer fontRenderer = Minecraft.getInstance().fontRenderer;

        int width = fontRenderer.getStringWidth(text);

        // fontRenderer.drawStringWithShadow(event.getMatrixStack(), String.format("%s%s%s", TextFormatting.YELLOW, TextFormatting.BOLD, text), (mainWindow.getScaledWidth() / 2.0F) - (width / 2.0F), mainWindow.getScaledHeight() * 0.1F, -1);
    }
}
