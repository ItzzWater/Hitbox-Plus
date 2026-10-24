package itzglecni.customhitboxes.gui;

import net.minecraft.client.gui.screen.Screen;

public class ConfigScreenProvider {

    public static Screen buildScreen(Screen parent) {
        return new CustomConfigScreen(parent);
    }
}