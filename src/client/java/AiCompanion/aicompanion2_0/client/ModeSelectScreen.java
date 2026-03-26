package AiCompanion.aicompanion2_0.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ModeSelectScreen extends Screen {

    public ModeSelectScreen() {
        super(Text.literal("Change AI Mode"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        boolean casualSelected = !"minecraft".equals(ClientConfig.getMode());
        boolean mcSelected     = "minecraft".equals(ClientConfig.getMode());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(casualSelected ? "§a▶ Casual" : "  Casual"),
            btn -> { ClientConfig.setMode("casual"); close(); }
        ).dimensions(cx - 75, cy - 20, 150, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(mcSelected ? "§a▶ Minecraft" : "  Minecraft"),
            btn -> { ClientConfig.setMode("minecraft"); close(); }
        ).dimensions(cx - 75, cy + 5, 150, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> close())
            .dimensions(cx - 55, cy + 32, 110, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int cx = width / 2;
        int cy = height / 2;

        context.drawCenteredTextWithShadow(textRenderer, "§6Change AI Mode", cx, cy - 48, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            "§8Current: §7" + ClientConfig.getMode(), cx, cy - 36, 0x888888);
        context.drawCenteredTextWithShadow(textRenderer,
            "§8Casual §7- general assistant, matches your language",
            cx, cy - 24, 0x666666);
        context.drawCenteredTextWithShadow(textRenderer,
            "§8Minecraft §7- only answers Minecraft questions",
            cx, cy - 14, 0x666666);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
