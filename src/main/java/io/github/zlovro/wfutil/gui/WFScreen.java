package io.github.zlovro.wfutil.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import info.journeymap.shaded.org.jetbrains.annotations.NotNull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.HashMap;

public class WFScreen extends Screen {
    protected HashMap<String, ArrayList<Widget>> widgets = new HashMap<>();

    protected int centerX, centerY;
    protected int topLeftX, topLeftY;
    protected int guiWidth, guiHeight;

    @NotNull
    protected Minecraft mc;

    protected WFScreen(int width, int height) {
        super(new StringTextComponent(""));

        guiWidth = width;
        guiHeight = height;

        mc = minecraft = Minecraft.getInstance();
    }

    protected void onInit()
    {

    }

    /**
     * do not override, use {@link #onInit} instead
     */

    @Override
    public void init(Minecraft minecraft, int width, int height) {
        super.init(minecraft, width, height);

        centerX = width / 2;
        centerY = height / 2;

        topLeftX = centerX - guiWidth / 2;
        topLeftY = centerY - guiHeight / 2;

        onInit();
    }

    public void renderCenteredString(MatrixStack matrixStack, String text, int x, int y, int color) {
        font.drawStringWithShadow(matrixStack, text, x - font.getStringWidth(text) / 2.0F, y, color);
    }
}
