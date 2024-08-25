package io.github.zlovro.wfutil.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.StringTextComponent;

import java.util.function.Consumer;

public class TextInputFieldWidget extends TextFieldWidget {
    public TextInputFieldWidget(Consumer<String> onType, String defaultText, int maxStringLength, FontRenderer fontRenderer, int x, int y, int width, int height) {
        super(fontRenderer, x, y, width, height, new StringTextComponent(""));

        setFocused2(false);
        setMaxStringLength(maxStringLength);
        setText(defaultText);
        setResponder(onType);
    }
}
