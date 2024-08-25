package io.github.zlovro.wfutil.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.zlovro.wfutil.WFMod;
import io.github.zlovro.wfutil.tileentities.mortar.MortarTileEntity;
import journeymap.client.waypoint.Waypoint;
import journeymap.client.waypoint.WaypointStore;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.ModList;
import org.apache.commons.lang3.math.NumberUtils;

public class MortarScreen extends WFScreen {
    protected static final ResourceLocation BG = new ResourceLocation("textures/gui/demo_background.png");

    protected final MortarTileEntity mortar;

    protected int paddingX, paddingY;
    protected int fieldHeight;

    protected TextInputFieldWidget txtX, txtY, txtZ;
    protected Button btnPredict;

    protected TextInputFieldWidget txtWaypointName;
    protected Button               btnLocateWaypoint;

    protected Waypoint waypoint = null;

    protected BlockPos.Mutable pos;
    protected BlockPos.Mutable lastPos;

    protected String predictionStatus;
    protected int    predictionTimer = 101;

    private void setPredictionStatus(String status) {
        predictionStatus = status;
        predictionTimer  = 0;
    }

    private static boolean jmapLoaded() {
        return ModList.get().isLoaded("journeymap");
    }

    public MortarScreen(MortarTileEntity mortar) {
        super(249, 166);
        this.mortar = mortar;

        pos = mortar.targetPos == null ? mortar.getPos().toMutable() : mortar.targetPos.toMutable();

        if (jmapLoaded()) {
            waypoint = WaypointStore.INSTANCE.getAll().stream().filter(w -> w.getId().equals(mortar.waypointId)).findFirst().orElse(null);
        }
    }

    private void checkTarget() {
        if (!mortar.targetInRange()) {
            setPredictionStatus(TextFormatting.DARK_RED + String.format("Target distance exceeds %d blocks", (int) WFMod.configServer.mortarRange));
        } else {
            mortar.sendToServer();
            setPredictionStatus(TextFormatting.DARK_GREEN + "Successfully calculated");
        }
    }


    @Override
    protected void onInit() {
        minecraft.keyboardListener.enableRepeatEvents(true);

        paddingX    = font.getStringWidth("X: ");
        paddingY    = 5;
        fieldHeight = font.FONT_HEIGHT + 2;

        int x = topLeftX + 10;
        int y = topLeftY + 20;

        children.add(txtX = new TextInputFieldWidget(p -> pos.setX(parseInt(p)), String.valueOf(pos.getX()), 10, font, x + paddingX, y + (paddingY + fieldHeight) * 0, 110 - paddingX, fieldHeight));
        children.add(txtY = new TextInputFieldWidget(p -> pos.setY(parseInt(p)), String.valueOf(pos.getY()), 10, font, x + paddingX, y + (paddingY + fieldHeight) * 1, 110 - paddingX, fieldHeight));
        children.add(txtZ = new TextInputFieldWidget(p -> pos.setZ(parseInt(p)), String.valueOf(pos.getZ()), 10, font, x + paddingX, y + (paddingY + fieldHeight) * 2, 110 - paddingX, fieldHeight));

        addButton(btnPredict = new Button(txtX.x - 1, txtZ.y + 20, txtX.getWidth() + 2, 20, new StringTextComponent("Calculate angle"), p -> checkTarget()));

        txtX.setValidator(this::validator);
        txtY.setValidator(this::validator);
        txtZ.setValidator(this::validator);

        // children.add(chkUseWaypoints = new CheckboxButton(txtX.x + txtX.getWidth() + 30, txtX.y, 16, 16, new StringTextComponent(""), false));
        children.add(txtWaypointName = new TextInputFieldWidget(p -> waypoint = WaypointStore.INSTANCE.getAll().stream().filter(w -> w.getName().equals(p)).findFirst().orElse(null), "", 50, font, (topLeftX + guiWidth) - (paddingX * 2) - txtX.getWidth(), txtX.y, txtX.getWidth(), txtX.getHeight()));

        addButton(btnLocateWaypoint = new Button(txtWaypointName.x, btnPredict.y, btnPredict.getWidth(), btnPredict.getHeight(), new StringTextComponent("Locate"), p -> {
            if (waypoint == null) {
                setPredictionStatus(TextFormatting.DARK_RED + "No waypoint found");
            } else {
                mortar.targetPos  = pos = waypoint.getBlockPos().toMutable();
                mortar.waypointId = waypoint.getId();

                checkTarget();
            }
        }));

        if (jmapLoaded() && waypoint != null) {
            txtWaypointName.setText(waypoint.getName());
        }
    }

    public void renderDefaultBG(MatrixStack matrixStack) {
        RenderSystem.color3f(1, 1, 1);

        mc.textureManager.bindTexture(BG);
        blit(matrixStack, topLeftX, topLeftY, 0, 0, guiWidth, guiHeight);

        RenderSystem.color3f(1.0F, 1.0F, 1.0F);
    }

    private int parseInt(String s) {
        if (s.equals("-") || s.isEmpty()) {
            return 0;
        }

        return Integer.parseInt(s);
    }

    private boolean validator(String text) {
        return text.length() < 10 && (text.isEmpty() || text.equals("-") || NumberUtils.isCreatable(text));
    }

    @Override
    public void render(MatrixStack mxStk, int mouseX, int mouseY, float partialTicks) {
        super.render(mxStk, mouseX, mouseY, partialTicks);

        renderDefaultBG(mxStk);

        txtX.render(mxStk, mouseX, mouseY, partialTicks);
        txtY.render(mxStk, mouseX, mouseY, partialTicks);
        txtZ.render(mxStk, mouseX, mouseY, partialTicks);

        font.drawStringWithShadow(mxStk, "X:", txtX.x - paddingX, txtX.y + 1, -1);
        font.drawStringWithShadow(mxStk, "Y:", txtY.x - paddingX, txtY.y + 1, -1);
        font.drawStringWithShadow(mxStk, "Z:", txtZ.x - paddingX, txtZ.y + 1, -1);

        renderCenteredString(mxStk, "Target:", txtX.x + txtX.getWidth() / 2, txtX.y - 12, -1);

        btnPredict.render(mxStk, mouseX, mouseY, partialTicks);

        if (predictionTimer < 99) {
            int alpha = 255;
            if (predictionTimer >= 90) {
                alpha = (int) ((100 - predictionTimer) * (255 / 10.0));
            }

            RenderSystem.enableAlphaTest();
            font.drawString(mxStk, predictionStatus, btnPredict.x, btnPredict.y + 30, (alpha << 24) | 0xFF_FF_FF);
            RenderSystem.disableAlphaTest();
        }

        boolean journeymap = jmapLoaded();

        txtWaypointName.active = journeymap;
        txtWaypointName.render(mxStk, mouseX, mouseY, partialTicks);

        renderCenteredString(mxStk, "Waypoint:", txtWaypointName.x + txtWaypointName.getWidth() / 2, txtWaypointName.y - 12, txtWaypointName.getFGColor());

        btnLocateWaypoint.active = journeymap;
        btnLocateWaypoint.render(mxStk, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        predictionTimer++;

        if (!txtX.isFocused()) {
            txtX.setText(String.valueOf(pos.getX()));
        }
        if (!txtY.isFocused()) {
            txtY.setText(String.valueOf(pos.getY()));
        }
        if (!txtZ.isFocused()) {
            txtZ.setText(String.valueOf(pos.getZ()));
        }

        if (txtWaypointName.active && !txtWaypointName.isFocused()) {
            if (waypoint != null) {
                txtWaypointName.setText(waypoint.getName());
            }
        }

        mortar.targetPos = pos;
        if (!pos.equals(lastPos)) {
            mortar.sendToServer();
        }
        lastPos = pos.toMutable();
    }
}
