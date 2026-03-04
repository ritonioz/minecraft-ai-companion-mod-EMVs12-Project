package AiCompanion.aicompanion2_0.client;

import AiCompanion.aicompanion2_0.AIEntity;
import AiCompanion.aicompanion2_0.Aicompanion2_0;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
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

        // Open chat GUI on right-click
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof AIEntity) {
                MinecraftClient client = MinecraftClient.getInstance();

                client.execute(() -> {
                    if (!ClientConfig.hasApiKey()) {
                        // First time: ask for API key, then open chat
                        client.setScreen(new ApiKeyScreen(() -> {
                            currentSession = null; // reset so session uses new key
                            openChatScreen(client);
                        }));
                    } else {
                        openChatScreen(client);
                    }
                });
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
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
