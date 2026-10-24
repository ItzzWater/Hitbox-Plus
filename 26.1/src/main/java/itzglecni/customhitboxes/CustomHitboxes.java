package itzglecni.customhitboxes;

import itzglecni.customhitboxes.command.TeamCommands;
import itzglecni.customhitboxes.config.ConfigManager;
import itzglecni.customhitboxes.gui.CustomConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class CustomHitboxes implements ClientModInitializer {
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hitboxesplus", "main"));
    private static final String OPEN_GUI_KEY = "key.hitboxesplus.open_config";

    private static KeyMapping openConfigKey;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            OPEN_GUI_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> TeamCommands.register(dispatcher));
        System.out.println("[Hitbox Plus] Initialized ItzGlecni's Client-Side Hitbox Rendering Engine v1.3.0.");
    }

    private void onClientTick(Minecraft client) {
        while (openConfigKey.consumeClick()) {
            if (!ConfigManager.getConfig().openConfigHotkeyEnabled) {
                continue;
            }

            client.setScreen(new CustomConfigScreen(client.screen));
        }
    }
}