package AiCompanion.aicompanion2_0.client;

import AiCompanion.aicompanion2_0.AIEntity;
import AiCompanion.aicompanion2_0.Aicompanion2_0;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

public class AiCompanionClient implements ClientModInitializer {
    private static final Identifier DEFAULT_TEXTURE = new Identifier("aicompanion2_0", "textures/entity/skin.png");
    private static final Identifier TUX_TEXTURE = new Identifier("aicompanion2_0", "textures/entity/tux-mc.png");

    // One session per world load, shared across all right-clicks
    private static AiChatSession currentSession = null;
    private static final Deque<Consumer<String>> pendingArchResponses = new ArrayDeque<>();

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
                    return entity.isTuxMode() ? TUX_TEXTURE : DEFAULT_TEXTURE;
                }
            }
        );

        // Clear session when leaving a world
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            currentSession = null;
        });

        ClientPlayNetworking.registerGlobalReceiver(Aicompanion2_0.QUESTION_PACKET_ID, (client, handler, buf, responseSender) -> {
            String question = buf.readString(32767);
            client.execute(() -> ensureSetupAndRun(client, () -> askQuestion(client, question)));
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

        ClientPlayNetworking.registerGlobalReceiver(Aicompanion2_0.DELETE_CONFIG_PACKET_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                currentSession = null;
                try {
                    ClientConfig.deleteConfig();
                    sendChatMessage(client, Text.literal("§6[AI] §fConfig deleted. Run any AI command to set it up again."));
                } catch (Exception e) {
                    String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    sendChatMessage(client, Text.literal("§c[AI] Could not delete config: " + error));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Aicompanion2_0.MODEL_SELECT_PACKET_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                currentSession = null;
                client.setScreen(new ModelSelectScreen());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Aicompanion2_0.CHANGE_MODE_PACKET_ID, (client, handler, buf, responseSender) -> {
            client.execute(() -> client.setScreen(new ModeSelectScreen()));
        });

        ClientPlayNetworking.registerGlobalReceiver(Aicompanion2_0.ARCH_EASTER_EGG_RESPONSE_PACKET_ID, (client, handler, buf, responseSender) -> {
            String response = buf.readString(32767);
            client.execute(() -> finishArchEasterEgg(response));
        });

        // Open chat GUI on right-click
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && entity instanceof AIEntity) {
                MinecraftClient client = MinecraftClient.getInstance();

                client.execute(() -> ensureSetupAndRun(client, () -> openChatScreen(client)));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }

    private static void ensureSetupAndRun(MinecraftClient client, Runnable action) {
        if (!ClientConfig.isSetupDone()) {
            currentSession = null;
            client.setScreen(new ProviderSetupScreen(() -> {
                currentSession = null;
                action.run();
            }));
            return;
        }
        action.run();
    }

    public static boolean tryTriggerArchEasterEgg(String message, Consumer<String> onResponse) {
        if (!isArchEasterEggTrigger(message)) {
            return false;
        }

        if (!ClientPlayNetworking.canSend(Aicompanion2_0.ARCH_EASTER_EGG_PACKET_ID)) {
            onResponse.accept("§cError: The server does not support the Arch easter egg.");
            return true;
        }

        pendingArchResponses.addLast(onResponse);

        try {
            ClientPlayNetworking.send(Aicompanion2_0.ARCH_EASTER_EGG_PACKET_ID, PacketByteBufs.create());
        } catch (RuntimeException e) {
            pendingArchResponses.pollLast();
            String error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            onResponse.accept("§cError: " + error);
        }

        return true;
    }

    private static void askQuestion(MinecraftClient client, String question) {
        sendChatMessage(client, Text.literal("§6[AI] §fThink about: " + question));

        AiChatSession session = new AiChatSession(
            ClientConfig.getBaseUrl(),
            ClientConfig.getApiKey(),
            ClientConfig.getModel(),
            ClientConfig.getApiPath()
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

    private static boolean isArchEasterEggTrigger(String message) {
        String normalized = message.trim().replaceAll("\\s+", " ").toLowerCase();
        return "i use arch btw".equals(normalized)
            || "sudo".equals(normalized)
            || normalized.startsWith("sudo ");
    }

    private static void finishArchEasterEgg(String response) {
        Consumer<String> callback = pendingArchResponses.pollFirst();
        if (callback != null) {
            callback.accept(response);
        }
    }

    private static void openChatScreen(MinecraftClient client) {
        if (currentSession == null) {
            currentSession = new AiChatSession(
                ClientConfig.getBaseUrl(),
                ClientConfig.getApiKey(),
                ClientConfig.getModel(),
                ClientConfig.getApiPath()
            );
        }
        client.setScreen(new AiChatScreen(currentSession));
    }
}
