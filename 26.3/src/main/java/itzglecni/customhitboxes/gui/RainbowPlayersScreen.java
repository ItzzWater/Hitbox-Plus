package itzglecni.customhitboxes.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class RainbowPlayersScreen extends Screen {
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
    private static final int DANGER = 0xFFE57373;
    private static final int DANGER_HOVER = 0xFFFF9B8F;

    private static final int CHIP_H = 18;
    private static final int CHIP_GAP = 3;

    private final Screen parent;
    private final Consumer<String> onSave;
    private final List<String> names = new ArrayList<>();

    private EditBox inputField;
    private String lastCompletionPrefix = "";
    private int completionIndex = -1;
    private float chipScroll;

    public RainbowPlayersScreen(Screen parent, String initialCsv, Consumer<String> onSave) {
        super(Component.literal("Rainbow Player List"));
        this.parent = parent;
        this.onSave = onSave;
        if (initialCsv != null) {
            for (String part : initialCsv.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty() && !containsName(trimmed)) {
                    names.add(trimmed);
                }
            }
        }
    }

    private boolean containsName(String name) {
        for (String existing : names) {
            if (existing.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // ===== Layout =====

    private int panelW() {
        return Mth.clamp(this.width - 40, 300, 400);
    }

    private int panelH() {
        return 212;
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    private int fieldBoxY() {
        return panelY() + 32;
    }

    private int fieldBoxW() {
        return panelW() - 28 - 50;
    }

    private int[] addBounds() {
        return new int[]{panelX() + panelW() - 14 - 44, fieldBoxY(), 44, 18};
    }

    private int listTop() {
        return panelY() + 66;
    }

    private int listBottom() {
        return panelY() + panelH() - 34;
    }

    private int[] saveBounds() {
        int w = 64;
        return new int[]{panelX() + panelW() - 14 - w, panelY() + panelH() - 26, w, 18};
    }

    private int[] cancelBounds() {
        int[] save = saveBounds();
        int w = 56;
        return new int[]{save[0] - 6 - w, save[1], w, 18};
    }

    private int maxChipScroll() {
        int contentH = names.size() * (CHIP_H + CHIP_GAP) - CHIP_GAP;
        return Math.max(0, contentH - (listBottom() - listTop()));
    }

    @Override
    protected void init() {
        String existing = inputField != null ? inputField.getValue() : "";

        inputField = new EditBox(this.font, panelX() + 18, fieldBoxY() + 5, fieldBoxW() - 8, 10, Component.literal("Username"));
        inputField.setBordered(false);
        inputField.setMaxLength(64);
        inputField.setTextColor(TEXT_MAIN);
        inputField.setTextShadow(false);
        inputField.setValue(existing);
        inputField.setFocused(true);
        inputField.setResponder(value -> {
            completionIndex = -1;
            updateSuggestion();
        });
        addRenderableWidget(inputField);
        setFocused(inputField);

        updateSuggestion();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_TAB && inputField != null && inputField.isFocused()) {
            applyTabCompletion();
            return true;
        }
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && inputField != null) {
            addFromField();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && handleLeftClick(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private boolean handleLeftClick(double mx, double my) {
        if (contains(addBounds(), mx, my)) {
            playClick();
            addFromField();
            return true;
        }
        if (contains(saveBounds(), mx, my)) {
            playClick();
            saveAndClose();
            return true;
        }
        if (contains(cancelBounds(), mx, my)) {
            playClick();
            onClose();
            return true;
        }

        int chipX = panelX() + 14;
        int chipW = panelW() - 28;
        if (mx >= chipX && mx <= chipX + chipW && my >= listTop() && my <= listBottom()) {
            int y = listTop() - Math.round(chipScroll);
            for (int i = 0; i < names.size(); i++) {
                if (my >= y && my <= y + CHIP_H) {
                    int removeX = chipX + chipW - 18;
                    if (mx >= removeX - 3) {
                        playClick();
                        names.remove(i);
                        chipScroll = Mth.clamp(chipScroll, 0.0f, maxChipScroll());
                        return true;
                    }
                    return false;
                }
                y += CHIP_H + CHIP_GAP;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX() && mouseX <= panelX() + panelW() && mouseY >= listTop() && mouseY <= listBottom()) {
            chipScroll = Mth.clamp(chipScroll - (float) (verticalAmount * (CHIP_H + CHIP_GAP)), 0.0f, maxChipScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void addFromField() {
        if (inputField == null) {
            return;
        }
        boolean added = false;
        for (String part : inputField.getValue().split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !containsName(trimmed)) {
                names.add(trimmed);
                added = true;
            }
        }
        if (added) {
            playClick();
            inputField.setValue("");
            chipScroll = maxChipScroll();
        }
    }

    private void saveAndClose() {
        onSave.accept(String.join(", ", names));
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

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

        g.fill(px + 14, fieldBoxY(), px + 14 + fieldBoxW(), fieldBoxY() + 18, CARD);
        g.outline(px + 14, fieldBoxY(), fieldBoxW(), 18, HAIRLINE);

        super.extractRenderState(g, mouseX, mouseY, delta);

        int[] add = addBounds();
        boolean addHovered = contains(add, mouseX, mouseY);
        fillRounded(g, add[0], add[1], add[2], add[3], addHovered ? ACCENT_HOVER : ACCENT);
        centeredNS(g, "Add", add[0] + add[2] / 2, add[1] + 5, ON_ACCENT);

        textNS(g, "Type a name, press Enter or Add. Tab autocompletes.", px + 14, fieldBoxY() + 24, TEXT_FAINT);

        renderChips(g, mouseX, mouseY);

        // Opaque footer band: mask any scrolled chips below the list so they can't overlap the buttons.
        g.fill(panelX() + 1, listBottom(), panelX() + panelW() - 1, panelY() + panelH() - 1, PANEL_BG);

        int[] save = saveBounds();
        boolean saveHovered = contains(save, mouseX, mouseY);
        fillRounded(g, save[0], save[1], save[2], save[3], saveHovered ? ACCENT_HOVER : ACCENT);
        centeredNS(g, "Save", save[0] + save[2] / 2, save[1] + 5, ON_ACCENT);

        int[] cancel = cancelBounds();
        boolean cancelHovered = contains(cancel, mouseX, mouseY);
        fillRounded(g, cancel[0], cancel[1], cancel[2], cancel[3], cancelHovered ? CARD_HOVER : CARD);
        centeredNS(g, "Cancel", cancel[0] + cancel[2] / 2, cancel[1] + 5, cancelHovered ? TEXT_MAIN : TEXT_DIM);
    }

    private void renderChips(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int chipX = panelX() + 14;
        int chipW = panelW() - 28;
        int top = listTop();
        int bottom = listBottom();

        if (names.isEmpty()) {
            textNS(g, "No players added yet.", chipX, top + 6, TEXT_FAINT);
            return;
        }

        g.enableScissor(chipX, top, chipX + chipW, bottom);

        int y = top - Math.round(chipScroll);
        for (String name : names) {
            if (y + CHIP_H >= top && y <= bottom) {
                boolean rowHovered = mouseX >= chipX && mouseX <= chipX + chipW && mouseY >= y && mouseY <= y + CHIP_H
                    && mouseY >= top && mouseY <= bottom;
                fillRounded(g, chipX, y, chipW, CHIP_H, rowHovered ? CARD_HOVER : CARD);
                textNS(g, name, chipX + 8, y + 5, TEXT_MAIN);

                int removeX = chipX + chipW - 18;
                boolean removeHovered = rowHovered && mouseX >= removeX - 3;
                centeredNS(g, "x", removeX + 6, y + 5, removeHovered ? DANGER_HOVER : (rowHovered ? DANGER : TEXT_FAINT));
            }
            y += CHIP_H + CHIP_GAP;
        }

        g.disableScissor();

        int max = maxChipScroll();
        if (max > 0) {
            int viewH = bottom - top;
            int contentH = names.size() * (CHIP_H + CHIP_GAP) - CHIP_GAP;
            int thumbH = Math.max(12, viewH * viewH / contentH);
            int thumbY = top + Math.round(chipScroll / max * (viewH - thumbH));
            g.fill(panelX() + panelW() - 8, thumbY, panelX() + panelW() - 5, thumbY + thumbH, 0xFF333F52);
        }
    }

    private void playClick() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static boolean contains(int[] bounds, double mx, double my) {
        return mx >= bounds[0] && mx <= bounds[0] + bounds[2] && my >= bounds[1] && my <= bounds[1] + bounds[3];
    }

    private void textNS(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.text(this.font, text, x, y, color, false);
    }

    private void centeredNS(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
        g.text(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
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

    private void applyTabCompletion() {
        if (inputField == null) {
            return;
        }

        int cursor = inputField.getCursorPosition();
        String value = inputField.getValue();

        int trimmedStart = 0;
        while (trimmedStart < cursor && Character.isWhitespace(value.charAt(trimmedStart))) {
            trimmedStart++;
        }

        String prefix = value.substring(trimmedStart, cursor).toLowerCase(Locale.ROOT);
        List<String> matches = collectMatches(prefix);
        if (matches.isEmpty()) {
            return;
        }

        if (!prefix.equals(lastCompletionPrefix)) {
            completionIndex = 0;
            lastCompletionPrefix = prefix;
        } else {
            completionIndex = (completionIndex + 1) % matches.size();
        }

        String completion = matches.get(completionIndex);
        String updated = value.substring(0, trimmedStart) + completion + value.substring(cursor);
        inputField.setValue(updated);

        int newCursor = trimmedStart + completion.length();
        inputField.setCursorPosition(newCursor);
        inputField.setHighlightPos(newCursor);
        updateSuggestion();
    }

    private void updateSuggestion() {
        if (inputField == null) {
            return;
        }

        int cursor = inputField.getCursorPosition();
        String value = inputField.getValue();

        int trimmedStart = 0;
        while (trimmedStart < cursor && Character.isWhitespace(value.charAt(trimmedStart))) {
            trimmedStart++;
        }

        String prefix = value.substring(trimmedStart, cursor).toLowerCase(Locale.ROOT);
        List<String> matches = collectMatches(prefix);

        if (matches.isEmpty()) {
            inputField.setSuggestion("");
            return;
        }

        String best = matches.get(0);
        if (best.length() > prefix.length()) {
            inputField.setSuggestion(best.substring(prefix.length()));
        } else {
            inputField.setSuggestion("");
        }
    }

    private List<String> collectMatches(String prefix) {
        List<String> result = new ArrayList<>();
        if (this.minecraft == null || this.minecraft.level == null) {
            return result;
        }

        for (Player player : this.minecraft.level.players()) {
            String name = player.getGameProfile().name();
            if (name == null || name.isBlank() || containsName(name)) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (prefix.isBlank() || lower.startsWith(prefix)) {
                result.add(name);
            }
        }

        result.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));
        return result;
    }
}
