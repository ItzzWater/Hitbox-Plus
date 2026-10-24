package itzglecni.customhitboxes.gui;

import net.minecraft.client.gui.screens.Screen;

public class ConfigScreenProvider {

    public static Screen buildScreen(Screen parent) {
        return new CustomConfigScreen(parent);
    }
}