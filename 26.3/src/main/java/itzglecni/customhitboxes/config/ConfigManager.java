package itzglecni.customhitboxes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "hitboxesplus.json");
    // Config file used before the mod ID changed from "customhitboxes" to "hitboxesplus".
    private static final File LEGACY_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "customhitboxes.json");
    private static ModConfig instance = new ModConfig();

    public static void load() {
        File source = FILE.exists() ? FILE : legacyConfigIfOurs();
        if (source != null) {
            try (FileReader reader = new FileReader(source)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                instance = loaded == null ? new ModConfig() : loaded;
                instance.normalize();
                if (source != FILE) {
                    save();
                }
            } catch (IOException e) {
                System.err.println("[Hitbox Plus] Critical failure reading configuration JSON.");
                e.printStackTrace();
            }
        } else {
            instance = new ModConfig();
            instance.normalize();
            save();
        }
    }

    // "customhitboxes.json" may belong to another mod with that ID, so only migrate it when it has our fields.
    private static File legacyConfigIfOurs() {
        if (!LEGACY_FILE.exists()) {
            return null;
        }
        try {
            String text = java.nio.file.Files.readString(LEGACY_FILE.toPath());
            if (text.contains("\"hoverColorHitbox\"") && text.contains("\"rainbowSpecificPlayers\"")) {
                return LEGACY_FILE;
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            System.err.println("[Hitbox Plus] Critical failure writing configuration JSON.");
            e.printStackTrace();
        }
    }

    public static ModConfig getConfig() {
        return instance;
    }
}