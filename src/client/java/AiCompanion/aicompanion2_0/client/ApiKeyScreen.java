package AiCompanion.aicompanion2_0.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ApiKeyScreen extends Screen {

    private TextFieldWidget keyField;
    private final Runnable onSuccess;

    public ApiKeyScreen(Runnable onSuccess) {
        super(Text.literal("Enter API Key"));
        this.onSuccess = onSuccess;
    }

    @Override
    protected void init() {
        keyField = new TextFieldWidget(
            textRenderer, width / 2 - 150, height / 2 - 10, 300, 20,
            Text.literal("API Key...")
        );
        keyField.setMaxLength(200);
        keyField.setFocused(true);
        addDrawableChild(keyField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), btn -> confirm())
            .dimensions(width / 2 - 75, height / 2 + 20, 150, 20)
            .build()
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        String key = keyField.getText().trim();
        if (key.isEmpty()) return;

        ClientConfig.setApiKey(key);
        MinecraftClient.getInstance().execute(() -> {
            close();
            onSuccess.run();
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer,
            "§6Please enter your Open WebUI API key:", width / 2, height / 2 - 30, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            "§7(Stored locally, only required once.)", width / 2, height / 2 - 20, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}