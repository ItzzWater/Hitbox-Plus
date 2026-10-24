package itzglecni.customhitboxes.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.IntConsumer;

public class ColorPickerScreen extends Screen {
    private static final int BG_DIM = 0xC8070A10;
    private static final int PANEL_BG = 0xFF10151E;
    private static final int PANEL_SHADOW = 0x66000000;
    private static final int CARD = 0xFF161D29;
    private static final int CARD_HOVER = 0xFF1F2937;
    private static final int HAIRLINE = 0xFF232E3F;
    private static final int ACCENT = 0xFF4ADE9C;
    private static final int ACCENT_HOVER = 0xFF7BF0BC;
    private static final int ON_ACCENT = 0xFF07291B;
    private static final int TEXT_MAIN = 0xFFEAF2FB;
    private static final int TEXT_DIM = 0xFF93A0B4;
    private static final int TEXT_FAINT = 0xFF5A6678;
    private static final int TRACK_OFF = 0xFF2A3547;
    private static final int KNOB = 0xFFF2F7FD;

    private static final int[] CHANNEL_COLORS = {0xFFFF5C5C, 0xFF58E07C, 0xFF5C9CFF};
    private static final String[] CHANNEL_NAMES = {"R", "G", "B"};
    private static final int[] PRESETS = {
        0xFFFFFF, 0xFF4D4D, 0xFFA94D, 0xFFE14D, 0x4DFF6E,
        0x4DFFE1, 0x4D9EFF, 0x9B4DFF, 0xFF4DD8, 0x111111
    };

    private final Screen parent;
    private final IntConsumer onSave;
    private final int[] channels = new int[3];

    private EditBox hexField;
    private boolean syncingHex;
    private int draggedChannel = -1;

    public ColorPickerScreen(Screen parent, Component title, int startColor, IntConsumer onSave) {
        super(title);
        this.parent = parent;
        this.onSave = onSave;
        this.channels[0] = (startColor >> 16) & 0xFF;
        this.channels[1] = (startColor >> 8) & 0xFF;
        this.channels[2] = startColor & 0xFF;
    }

    // ===== Layout =====

    private int panelW() {
        return Mth.clamp(this.width - 40, 252, 300);
    }

    private int panelH() {
        return 192;
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    private int sliderRowY(int channel) {
        return panelY() + 52 + channel * 22;
    }

    private int trackX() {
        return panelX() + 30;
    }

    private int trackW() {
        return panelW() - 30 - 50;
    }

    private int presetsY() {
        return panelY() + 124;
    }

    private int presetsX() {
        return panelX() + (panelW() - (PRESETS.length * 12 + (PRESETS.length - 1) * 4)) / 2;
    }

    private int hexRowY() {
        return panelY() + 142;
    }

    private int[] applyBounds() {
        int w = 64;
        return new int[]{panelX() + panelW() - 14 - w, panelY() + panelH() - 26, w, 18};
    }

    private int[] cancelBounds() {
        int[] apply = applyBounds();
        int w = 56;
        return new int[]{apply[0] - 6 - w, apply[1], w, 18};
    }

    @Override
    protected void init() {
        int fieldX = panelX() + 44;
        hexField = new EditBox(this.font, fieldX, hexRowY() + 4, 54, 10, Component.literal("Hex"));
        hexField.setBordered(false);
        hexField.setMaxLength(7);
        hexField.setTextColor(TEXT_MAIN);
        hexField.setTextShadow(false);
        hexField.setValue(formatHex());
        hexField.setResponder(value -> {
            if (syncingHex) {
                return;
            }
            String raw = value.startsWith("#") ? value.substring(1) : value;
            if (raw.length() == 6) {
                try {
                    int parsed = Integer.parseInt(raw, 16);
                    channels[0] = (parsed >> 16) & 0xFF;
                    channels[1] = (parsed >> 8) & 0xFF;
                    channels[2] = parsed & 0xFF;
                } catch (NumberFormatException ignored) {
                }
            }
        });
        addRenderableWidget(hexField);
    }

    private String formatHex() {
        return String.format(Locale.ROOT, "#%06X", getColor());
    }

    private void syncHexField() {
        if (hexField != null) {
            syncingHex = true;
            hexField.setValue(formatHex());
            syncingHex = false;
        }
    }

    private int getColor() {
        return (channels[0] << 16) | (channels[1] << 8) | channels[2];
    }

    // ===== Render =====

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, BG_DIM);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, PANEL_SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        g.outline(px, py, pw, ph, HAIRLINE);

        g.text(this.font, this.title, px + 14, py + 10, TEXT_MAIN);
        g.fill(px + 14, py + 21, px + 14 + 18, py + 23, ACCENT);

        int preview = 0xFF000000 | getColor();
        g.fill(px + 14, py + 26, px + pw - 14, py + 44, preview);
        g.outline(px + 14, py + 26, pw - 28, 18, 0x38FFFFFF);
        int luminance = (channels[0] * 299 + channels[1] * 587 + channels[2] * 114) / 1000;
        int hexTextColor = luminance > 140 ? 0xFF10151E : 0xFFF2F7FD;
        centeredNS(g, formatHex(), px + pw / 2, py + 31, hexTextColor);

        for (int i = 0; i < 3; i++) {
            renderChannelSlider(g, i);
        }

        for (int i = 0; i < PRESETS.length; i++) {
            int sx = presetsX() + i * 16;
            int sy = presetsY();
            boolean hovered = mouseX >= sx && mouseX <= sx + 12 && mouseY >= sy && mouseY <= sy + 12;
            g.fill(sx, sy, sx + 12, sy + 12, 0xFF000000 | PRESETS[i]);
            g.outline(sx, sy, 12, 12, hovered ? 0xB0FFFFFF : 0x38FFFFFF);
        }

        textNS(g, "HEX", px + 14, hexRowY() + 4, TEXT_FAINT);
        g.fill(px + 40, hexRowY(), px + 40 + 62, hexRowY() + 16, CARD);
        g.outline(px + 40, hexRowY(), 62, 16, HAIRLINE);

        super.extractRenderState(g, mouseX, mouseY, delta);

        int[] apply = applyBounds();
        boolean applyHovered = contains(apply, mouseX, mouseY);
        fillRounded(g, apply[0], apply[1], apply[2], apply[3], applyHovered ? ACCENT_HOVER : ACCENT);
        centeredNS(g, "Apply", apply[0] + apply[2] / 2, apply[1] + 5, ON_ACCENT);

        int[] cancel = cancelBounds();
        boolean cancelHovered = contains(cancel, mouseX, mouseY);
        fillRounded(g, cancel[0], cancel[1], cancel[2], cancel[3], cancelHovered ? CARD_HOVER : CARD);
        centeredNS(g, "Cancel", cancel[0] + cancel[2] / 2, cancel[1] + 5, cancelHovered ? TEXT_MAIN : TEXT_DIM);
    }

    private void textNS(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(this.font, text, x, y, color, false);
    }

    private void centeredNS(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
        g.text(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void renderChannelSlider(GuiGraphicsExtractor g, int channel) {
        int rowY = sliderRowY(channel);
        int tx = trackX();
        int tw = trackW();
        int ty = rowY + 4;
        float t = channels[channel] / 255.0f;

        textNS(g, CHANNEL_NAMES[channel], panelX() + 16, rowY + 1, CHANNEL_COLORS[channel]);

        fillRounded(g, tx, ty, tw, 4, TRACK_OFF);
        int fillW = Math.round(t * tw);
        if (fillW > 1) {
            fillRounded(g, tx, ty, fillW, 4, CHANNEL_COLORS[channel]);
        }
        int kx = tx + Math.round(t * (tw - 8));
        fillRounded(g, kx, ty - 2, 8, 8, draggedChannel == channel ? CHANNEL_COLORS[channel] : KNOB);

        String value = Integer.toString(channels[channel]);
        textNS(g, value, panelX() + panelW() - 14 - this.font.width(value), rowY + 1,
            draggedChannel == channel ? TEXT_MAIN : TEXT_DIM);
    }

    // ===== Input =====

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && handleLeftClick(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private boolean handleLeftClick(double mx, double my) {
        for (int i = 0; i < 3; i++) {
            int rowY = sliderRowY(i);
            if (my >= rowY - 3 && my <= rowY + 13 && mx >= trackX() - 5 && mx <= trackX() + trackW() + 5) {
                draggedChannel = i;
                applyChannelMouse(mx);
                return true;
            }
        }

        for (int i = 0; i < PRESETS.length; i++) {
            int sx = presetsX() + i * 16;
            int sy = presetsY();
            if (mx >= sx && mx <= sx + 12 && my >= sy && my <= sy + 12) {
                playClick();
                channels[0] = (PRESETS[i] >> 16) & 0xFF;
                channels[1] = (PRESETS[i] >> 8) & 0xFF;
                channels[2] = PRESETS[i] & 0xFF;
                syncHexField();
                return true;
            }
        }

        if (contains(applyBounds(), mx, my)) {
            playClick();
            onSave.accept(getColor());
            onClose();
            return true;
        }
        if (contains(cancelBounds(), mx, my)) {
            playClick();
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggedChannel >= 0) {
            applyChannelMouse(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggedChannel >= 0) {
            draggedChannel = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void applyChannelMouse(double mx) {
        float t = Mth.clamp((float) ((mx - trackX() - 4) / (trackW() - 8)), 0.0f, 1.0f);
        channels[draggedChannel] = Math.round(t * 255.0f);
        syncHexField();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private void playClick() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static boolean contains(int[] bounds, double mx, double my) {
        return mx >= bounds[0] && mx <= bounds[0] + bounds[2] && my >= bounds[1] && my <= bounds[1] + bounds[3];
    }

    private void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
