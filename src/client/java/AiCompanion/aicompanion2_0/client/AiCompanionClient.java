package AiCompanion.aicompanion2_0.client;

import AiCompanion.aicompanion2_0.AIEntity;
import AiCompanion.aicompanion2_0.Aicompanion2_0;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public class AiCompanionClient implements ClientModInitializer {

    // One session per world load, shared across all right-clicks
    private static AiChatSession currentSession = null;

    @Override
    public void onInitializeClient() {
        // Register renderer
        EntityRendererRegistry.register(Aicompanion2_0.AI_COMPANION, (context) ->
            new BipedEntityRenderer<AIEntity, PlayerEntityModel<AIEntity>>(
                context,
                new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false),
                0.5f
            ) {
                @Override
                public Identifier getTexture(AIEntity entity) {
                    return new Identifier("aicompanion2_0", "textures/entity/skin.png");
                }
            }
        );

        // Clear session when leaving a world
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            currentSession = null;
        });

        ClientPlayNetworking.registerGlobalReceiver(Aicompanion2_0.QUESTION_PACKET_ID, (client, handler, buf, responseSender) -> {
            String question = buf.readString(32767);
            client.execute(() -> ensureApiKeyAndRun(client, () -> askQuestion(client, question)));
        });

        ClientPlayNetworking.registerGlobalReceiver(Aicompanion2_0.DELETE_KEY_PACKET_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                currentSession = null;

                try {
                    boolean deleted = ClientConfig.deleteApiKey();
                    if (deleted) {
                        sendChatMessage(client, Text.literal("§6[AI] §fAPI key deleted."));
                    } else {
                        sendChatMessage(client, Text.literal("§6[AI] §fno api key deleted: none found"));
                    }
                } catch (Exception e) {
                    String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    sendChatMessage(client, Text.literal("§c[AI] Could not delete API key: " + error));
                }
            });
        });

        // Open chat GUI on right-click
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof AIEntity) {
                MinecraftClient client = MinecraftClient.getInstance();

                client.execute(() -> ensureApiKeyAndRun(client, () -> openChatScreen(client)));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    private static void ensureApiKeyAndRun(MinecraftClient client, Runnable action) {
        if (!ClientConfig.hasApiKey()) {
            currentSession = null;
            client.setScreen(new ApiKeyScreen(() -> {
                currentSession = null;
                action.run();
            }));
            return;
        }

        action.run();
    }

    private static void askQuestion(MinecraftClient client, String question) {
        sendChatMessage(client, Text.literal("§6[AI] §fThink about: " + question));

        AiChatSession session = new AiChatSession(
            Aicompanion2_0.getApiBaseUrl(),
            ClientConfig.getApiKey(),
            Aicompanion2_0.getModel()
        );

        session.sendMessage(question, response -> client.execute(() -> {
            if (response == null || response.isEmpty()) {
                return;
            }

            if (response.startsWith("§cError: ")) {
                String error = response.substring("§cError: ".length());
                sendChatMessage(client, Text.literal("§c[AI] Error with /ai question: " + error));
                return;
            }

            String answer = response;
            if (answer.length() > 2000) {
                answer = answer.substring(0, 2000) + "...";
            }

            sendChatMessage(client, Text.literal("§6[AI] §fAnswer: " + answer));
        }));
    }

    private static void sendChatMessage(MinecraftClient client, Text message) {
        if (client.player != null) {
            client.player.sendMessage(message, false);
        }
    }

    private static void openChatScreen(MinecraftClient client) {
        if (currentSession == null) {
            currentSession = new AiChatSession(
                Aicompanion2_0.getApiBaseUrl(),
                ClientConfig.getApiKey(),
                Aicompanion2_0.getModel()
            );
        }
        client.setScreen(new AiChatScreen(currentSession));
    }
}
