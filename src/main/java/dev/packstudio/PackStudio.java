package dev.packstudio;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PackStudio implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("packstudio", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.packstudio.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, cat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            RoomBuilder.tick(client);
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new StudioScreen(null));
                }
            }
        });
    }

    public static java.nio.file.Path packsDir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("resourcepacks");
    }

    public static java.nio.file.Path exportDir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("packstudio").resolve("exports");
    }
}
