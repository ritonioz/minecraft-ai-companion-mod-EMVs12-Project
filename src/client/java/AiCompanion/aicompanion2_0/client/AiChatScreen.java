package AiCompanion.aicompanion2_0.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class AiChatScreen extends Screen {

    private final AiChatSession session;
    private TextFieldWidget inputField;
    private int scrollOffset = 0;

    public AiChatScreen(AiChatSession session) {
        super(Text.literal("AI Companion"));
        this.session = session;
    }

    private List<String> displayLines() {
        return session.getDisplayLines();
    }

    @Override
    protected void init() {
        inputField = new TextFieldWidget(
            textRenderer, width / 2 - 150, height - 35, 270, 20,
            Text.literal("Nachricht...")
        );
        inputField.setMaxLength(500);
        inputField.setFocused(true);
        addDrawableChild(inputField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Senden"), btn -> sendMessage())
            .dimensions(width / 2 + 125, height - 35, 60, 20)
            .build()
        );

        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), btn -> close())
            .dimensions(width / 2 - 30, height - 10, 60, 15)
            .build()
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendMessage() {
        String msg = inputField.getText().trim();
        if (msg.isEmpty()) return;
        inputField.setText("");

        displayLines().add("§eIch: §f" + msg);
        displayLines().add("§7[AI denkt nach...]");
        session.sendMessage(msg, response -> {
            displayLines().remove("§7[AI denkt nach...]");
            displayLines().add("§6AI: §f" + response);
            // auto-scroll to bottom
            scrollOffset = 0;
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // Chat box background
        context.fill(width / 2 - 155, 10, width / 2 + 155, height - 45, 0xAA000000);

        // Title
        context.drawCenteredTextWithShadow(textRenderer, "§6AI Companion", width / 2, 15, 0xFFFFFF);

        // Pre-wrap all lines first so we know total height
        int lineHeight = 11;
        List<OrderedText> allWrapped = new ArrayList<>();
        for (String dl : displayLines()) {
            allWrapped.addAll(textRenderer.wrapLines(Text.literal(dl), 290));
        }

        int chatAreaHeight = height - 65;
        int maxLines = chatAreaHeight / lineHeight;
        int totalLines = allWrapped.size();

        int startIdx = Math.max(0, totalLines - maxLines - scrollOffset);
        int endIdx = Math.min(totalLines, startIdx + maxLines);

        int y = 25;
        for (int i = startIdx; i < endIdx; i++) {
            context.drawTextWithShadow(textRenderer, allWrapped.get(i), width / 2 - 148, y, 0xFFFFFF);
            y += lineHeight;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollOffset = Math.max(0, scrollOffset - (int) amount);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}