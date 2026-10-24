package itzglecni.customhitboxes.gui;

import itzglecni.customhitboxes.config.ConfigManager;
import itzglecni.customhitboxes.config.ModConfig;
import itzglecni.customhitboxes.config.TeamNames;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CustomConfigScreen extends Screen {
    private enum Tab {
        TOGGLES("Toggles", "Turn features on or off"),
        SIZING("Sizing", "Line widths and distance rules"),
        COLORS("Colors", "Hitbox colors and rainbow effects"),
        PLAYERS("Players", "Teammates and player highlights");

        final String label;
        final String subtitle;

        Tab(String label, String subtitle) {
            this.label = label;
            this.subtitle = subtitle;
        }
    }

    // ===== Theme =====
    private static final int BG_TOP = 0xD8070A10;
    private static final int BG_BOTTOM = 0xE805070C;
    private static final int PANEL_BG = 0xFF10151E;
    private static final int PANEL_SHADOW = 0x66000000;
    private static final int SIDEBAR_BG = 0xFF0B0F16;
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
    private static final int TRACK_OFF = 0xFF2A3547;
    private static final int KNOB = 0xFFF2F7FD;
    private static final int SCROLL_THUMB = 0xFF333F52;
    private static final int SCROLL_THUMB_ACTIVE = 0xFF4A5A73;

    private static final int FOOTER_H = 30;
    private static final int ROW_GAP = 4;
    private static final String VERSION_LABEL = "v1.3.0";
    private static final int TEAM_RED = 0xFFFF6B6B;
    private static final int TEAM_BLUE = 0xFF6BA8FF;
    private static final int ONLINE = 0xFF4ADE80;
    private static final String TEAM_COLOR_INFO = "Team Color gives your teammates their own hitbox color. "
        + "Add players by name here, or use /hbteam in chat.";

    private final Screen parent;
    private final ModConfig working;

    private Tab tab = Tab.TOGGLES;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();

    private float scroll;
    private float scrollTarget;
    private int maxScroll;
    private long lastFrameNanos;
    private float frameDt;
    private long tabSwitchMillis;

    private Row draggedRow;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private String tooltipText;
    private List<FormattedCharSequence> tooltipLines;

    // Players tab: Team Color page and its "Add Players" popup.
    private boolean teamPage;
    private boolean addPopupOpen;
    private EditBox addField;
    private String addPopupError;
    private String lastCompletionPrefix = "";
    private int completionIndex = -1;
    private final Map<String, ResolvableProfile> skinProfiles = new HashMap<>();

    public CustomConfigScreen(Screen parent) {
        super(Component.literal("Hitbox Plus"));
        this.parent = parent;
        this.working = ConfigManager.getConfig().copy();
        this.tabSwitchMillis = System.currentTimeMillis();
        rebuildRows();
    }

    // ===== Layout =====

    private int panelW() {
        return Mth.clamp(this.width - 32, 300, 540);
    }

    private int panelH() {
        return Mth.clamp(this.height - 24, 196, 330);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    private int sidebarW() {
        return Mth.clamp(panelW() / 4, 92, 118);
    }

    private int contentX() {
        return panelX() + sidebarW() + 12;
    }

    private int contentRight() {
        return panelX() + panelW() - 16;
    }

    private int contentW() {
        return contentRight() - contentX();
    }

    private int viewTop() {
        return panelY() + 38;
    }

    private int viewBottom() {
        return panelY() + panelH() - FOOTER_H - 6;
    }

    private int footerTop() {
        return panelY() + panelH() - FOOTER_H;
    }

    private int[] navItemBounds(int index) {
        int x = panelX() + 6;
        int y = panelY() + 44 + index * 25;
        return new int[]{x, y, sidebarW() - 12, 22};
    }

    private int[] saveBounds() {
        int w = 64;
        return new int[]{panelX() + panelW() - 12 - w, footerTop() + 6, w, 18};
    }

    private int[] cancelBounds() {
        int[] save = saveBounds();
        int w = 56;
        return new int[]{save[0] - 6 - w, footerTop() + 6, w, 18};
    }

    private int[] resetBounds() {
        int w = this.font.width("Reset Tab") + 14;
        int available = cancelBounds()[0] - contentX() - 4;
        return new int[]{contentX(), footerTop() + 6, Math.min(w, Math.max(20, available)), 18};
    }

    // ===== Rows =====

    private void rebuildRows() {
        rows.clear();
        switch (tab) {
            case TOGGLES -> buildToggleRows();
            case SIZING -> buildSizingRows();
            case COLORS -> buildColorRows();
            case PLAYERS -> {
                if (teamPage) {
                    buildTeamRows();
                } else {
                    rows.add(new TileRow());
                }
            }
        }
    }

    private void buildToggleRows() {
        rows.add(new ToggleRow("Mod Enabled", "Master toggle for the whole mod.",
            () -> working.enabled, v -> working.enabled = v));
        rows.add(new ToggleRow("Config Hotkey", "Press O in-game to open this screen instantly.",
            () -> working.openConfigHotkeyEnabled, v -> working.openConfigHotkeyEnabled = v));
        rows.add(new ToggleRow("Elytra Players Only", "Only show player hitboxes when that player has an Elytra equipped.",
            () -> working.renderOnlyWhenElytraEquipped, v -> working.renderOnlyWhenElytraEquipped = v));
        rows.add(new ToggleRow("Render Projectiles", "Show hitboxes for arrows, pearls, wind charges and more.",
            () -> working.renderProjectiles, v -> working.renderProjectiles = v));
        rows.add(new ToggleRow("Players + Projectiles Only", "Hide mob and animal hitboxes, keep players and projectiles.",
            () -> working.renderEntities, v -> working.renderEntities = v));
        rows.add(new ToggleRow("Looking Direction Line", "Draw a line showing where each entity is facing.",
            () -> working.lookingDirection, v -> working.lookingDirection = v));
        rows.add(new ToggleRow("Render Hitbox Box", "Show the hitbox box (players only).",
            () -> working.renderHitboxBox, v -> working.renderHitboxBox = v));
        rows.add(new ToggleRow("Remove Direction Arrow", "Hide the arrow tip and keep only the direction line.",
            () -> working.removeDirectionArrow, v -> working.removeDirectionArrow = v));
        rows.add(new ToggleRow("Forehead Line", "Show the small eye-level line on hitboxes.",
            () -> working.foreheadLine, v -> working.foreheadLine = v));
        rows.add(new ToggleRow("Self Hitbox", "Render your own hitbox.",
            () -> working.selfHitbox, v -> working.selfHitbox = v));
        rows.add(new ToggleRow("Hover Color", "Recolor hitboxes when your crosshair is over a hittable target.",
            () -> working.hoverColorHitbox, v -> working.hoverColorHitbox = v));
        rows.add(new ToggleRow("Hurt Color", "Flash the Hit Color (Colors tab) after you damage an entity.",
            () -> working.hitColorHitbox, v -> working.hitColorHitbox = v));
        rows.add(new ToggleRow("Hide Firework Hitbox", "Do not render hitboxes for fireworks.",
            () -> working.noFireworkHitbox, v -> working.noFireworkHitbox = v));
        rows.add(new ToggleRow("Hide Stuck Arrows", "Skip hitboxes for arrows that landed and are no longer flying.",
            () -> working.noStuckArrows, v -> working.noStuckArrows = v));
    }

    private void buildSizingRows() {
        rows.add(new SliderRow("Player Hitbox Width", "Line width for player hitboxes (default 2.50).",
            0.1f, 20.0f, 0.05f, false,
            () -> working.hitboxWidth, v -> working.hitboxWidth = v));
        rows.add(new SliderRow("Non-Player Hitbox Width", "Line width for mobs, animals and other entities.",
            0.1f, 20.0f, 0.05f, false,
            () -> working.nonPlayerHitboxWidth, v -> working.nonPlayerHitboxWidth = v));
        rows.add(new DualSliderRow("Far Player Hitbox Width", "Players more than this many blocks away use this width instead.",
            () -> working.distanceHitboxBlocks, v -> working.distanceHitboxBlocks = v,
            () -> working.distanceHitboxWidth, v -> working.distanceHitboxWidth = v));
        rows.add(new SliderRow("Hide Far Hitboxes", "Hitboxes farther than this many blocks are hidden entirely.",
            1, 300, 1, true,
            () -> (float) working.disableHitboxesAfterBlocks, v -> working.disableHitboxesAfterBlocks = Math.round(v)));
    }

    private void buildColorRows() {
        rows.add(new ColorRow("Hitbox Main Color", "Main color for regular hitboxes.",
            () -> working.hitboxMainColor, v -> working.hitboxMainColor = v,
            () -> working.rainbowMain, v -> working.rainbowMain = v));
        rows.add(new ColorRow("Hover Color", "Shown when your crosshair is over a valid target.",
            () -> working.hoverColor, v -> working.hoverColor = v,
            () -> working.rainbowHover, v -> working.rainbowHover = v));
        rows.add(new ColorRow("Hit Color", "Flashes briefly after you deal real damage.",
            () -> working.hitColor, v -> working.hitColor = v,
            () -> working.rainbowHit, v -> working.rainbowHit = v));
        rows.add(new ColorRow("Hover Color Mobs", "Shown when your crosshair is over a mob or hostile.",
            () -> working.hoverColorMobs, v -> working.hoverColorMobs = v,
            () -> working.rainbowHoverMobs, v -> working.rainbowHoverMobs = v));
        rows.add(new ColorRow("Looking Direction Color", "Color of the looking direction line.",
            () -> working.lookingDirectionColor, v -> working.lookingDirectionColor = v,
            () -> working.rainbowLooking, v -> working.rainbowLooking = v));
        rows.add(new ColorRow("Forehead Line Color", "Color of the eye-level guide line.",
            () -> working.foreheadLineColor, v -> working.foreheadLineColor = v,
            () -> working.rainbowForehead, v -> working.rainbowForehead = v));
        rows.add(new ColorRow("Projectiles Color", "Color used for projectile hitboxes only.",
            () -> working.projectilesColor, v -> working.projectilesColor = v,
            () -> working.rainbowProjectiles, v -> working.rainbowProjectiles = v));
        rows.add(new ActionRow("Rainbow Player List", "Limit rainbow mode to specific usernames.", "EDIT",
            () -> this.minecraft.gui.setScreen(new RainbowPlayersScreen(this, working.rainbowSpecificPlayers,
                savedCsv -> working.rainbowSpecificPlayers = savedCsv))));
    }

    private void buildTeamRows() {
        rows.add(new SectionRow("Teammate Color", false));
        rows.add(new ToggleRow("Show Team Color", "Give the players on your team their own hitbox color.",
            () -> working.teamColorEnabled, v -> working.teamColorEnabled = v));
        rows.add(new ColorRow("Teammate Color", "The hitbox color used for your teammates.",
            () -> working.teamColor, v -> working.teamColor = v,
            () -> working.rainbowTeam, v -> working.rainbowTeam = v));

        rows.add(new SectionRow("Player Names", true));
        if (working.teamPlayers.isEmpty()) {
            rows.add(new InfoRow("No players yet. Click + to add someone."));
        } else {
            for (String name : working.teamPlayers) {
                rows.add(new NameRow(name));
            }
        }

        rows.add(new SectionRow("Commands", false));
        rows.add(new CommandRow("Add a player", "/hbteam add <player>"));
        rows.add(new CommandRow("Remove a player", "/hbteam remove <player>"));
        rows.add(new CommandRow("Show your team", "/hbteam list"));
        rows.add(new CommandRow("Remove everyone", "/hbteam clear"));
        rows.add(new InfoRow("Type these in chat. Click one to copy it."));
    }

    private int contentHeight() {
        int h = 0;
        for (Row row : rows) {
            h += row.height() + ROW_GAP;
        }
        return Math.max(0, h - ROW_GAP);
    }

    // ===== Render =====

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        frameDt = lastFrameNanos == 0 ? 0.016f : Mth.clamp((now - lastFrameNanos) / 1_000_000_000f, 0.0f, 0.05f);
        lastFrameNanos = now;
        tooltipText = null;
        tooltipLines = null;

        updateScroll();

        g.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
        super.extractRenderState(g, mouseX, mouseY, delta);

        int px = panelX();
        int py = panelY();
        int pw = panelW();
        int ph = panelH();

        g.fill(px + 3, py + 3, px + pw + 3, py + ph + 3, PANEL_SHADOW);
        g.fill(px, py, px + pw, py + ph, PANEL_BG);
        g.fill(px, py, px + sidebarW(), py + ph, SIDEBAR_BG);
        g.fill(px + sidebarW(), py, px + sidebarW() + 1, py + ph, HAIRLINE);
        g.outline(px, py, pw, ph, HAIRLINE);

        // While the popup is open, nothing behind it reacts to the mouse.
        int mx = addPopupOpen ? -10000 : mouseX;
        int my = addPopupOpen ? -10000 : mouseY;
        renderSidebar(g, mx, my);
        renderHeader(g, mx, my);
        renderRows(g, mx, my);
        renderScrollbar(g, mx, my);
        renderFooter(g, mx, my);

        if (addPopupOpen) {
            renderAddPopup(g, mouseX, mouseY, delta);
        }

        if (tooltipLines != null) {
            g.setTooltipForNextFrame(this.font, tooltipLines, mouseX, mouseY);
        } else if (tooltipText != null) {
            g.setTooltipForNextFrame(this.font, Component.literal(tooltipText), mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int bx = panelX() + 12;
        int by = panelY() + 12;
        g.text(this.font, "HITBOX", bx, by, TEXT_MAIN);
        g.text(this.font, "+", bx + this.font.width("HITBOX"), by, ACCENT);
        g.text(this.font, VERSION_LABEL, bx, by + 11, TEXT_FAINT);

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int[] b = navItemBounds(i);
            boolean active = tab == tabs[i];
            boolean hovered = contains(b, mouseX, mouseY);
            float hover = animate("nav:" + i, hovered && !active);

            if (active) {
                pillFill(g, b[0], b[1], b[2], b[3], CARD_HOVER);
                g.fill(b[0], b[1] + 5, b[0] + 2, b[1] + b[3] - 5, ACCENT);
            } else if (hover > 0.02f) {
                pillFill(g, b[0], b[1], b[2], b[3], blend(SIDEBAR_BG, CARD, hover));
            }

            int iconColor = active ? ACCENT : blend(TEXT_FAINT, TEXT_DIM, hover);
            renderNavIcon(g, tabs[i], b[0] + 8, b[1] + 7, iconColor);
            int textColor = active ? TEXT_MAIN : blend(TEXT_DIM, TEXT_MAIN, hover);
            g.text(this.font, tabs[i].label, b[0] + 22, b[1] + 7, textColor);
        }
    }

    private void renderNavIcon(GuiGraphicsExtractor g, Tab icon, int x, int y, int color) {
        switch (icon) {
            case TOGGLES -> {
                pillFill(g, x, y + 1, 9, 6, color);
                g.fill(x + 5, y + 2, x + 8, y + 6, SIDEBAR_BG);
            }
            case SIZING -> {
                g.fill(x, y, x + 9, y + 1, color);
                g.fill(x + 5, y - 1, x + 7, y + 2, color);
                g.fill(x, y + 4, x + 9, y + 5, color);
                g.fill(x + 1, y + 3, x + 3, y + 6, color);
                g.fill(x, y + 8, x + 9, y + 9, color);
                g.fill(x + 6, y + 7, x + 8, y + 10, color);
            }
            case COLORS -> {
                boolean lit = color == ACCENT;
                g.fill(x, y, x + 4, y + 4, lit ? 0xFFFF6B6B : color);
                g.fill(x + 5, y, x + 9, y + 4, lit ? 0xFF6BFF8A : color);
                g.fill(x, y + 5, x + 4, y + 9, lit ? 0xFF6BA8FF : color);
                g.fill(x + 5, y + 5, x + 9, y + 9, lit ? 0xFFFFD96B : color);
            }
            case PLAYERS -> {
                // Two players side by side: red behind, blue in front. Grey like the other icons until the tab is open.
                boolean lit = color == ACCENT;
                int red = lit ? TEAM_RED : color;
                int blue = lit ? TEAM_BLUE : blend(color, 0xFF000000, 0.25f);
                g.fill(x + 1, y - 1, x + 4, y + 2, red);
                g.fill(x, y + 3, x + 5, y + 8, red);
                g.fill(x + 6, y, x + 9, y + 3, blue);
                g.fill(x + 5, y + 4, x + 10, y + 9, blue);
            }
        }
    }

    private int[] backBounds() {
        return new int[]{contentX() - 3, panelY() + 7, this.font.width("Team Color") + 18, 14};
    }

    private void renderHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = contentX();
        int y = panelY() + 10;
        if (tab == Tab.PLAYERS && teamPage) {
            int[] back = backBounds();
            boolean hovered = contains(back, mouseX, mouseY);
            float hover = animate("btn:back", hovered);
            if (hover > 0.02f) {
                pillFill(g, back[0], back[1], back[2], back[3], blend(PANEL_BG, CARD, hover));
            }
            int arrow = blend(TEXT_DIM, ACCENT, hover);
            // "<" chevron
            g.fill(x + 3, y, x + 4, y + 1, arrow);
            g.fill(x + 2, y + 1, x + 3, y + 2, arrow);
            g.fill(x + 1, y + 2, x + 2, y + 5, arrow);
            g.fill(x + 2, y + 5, x + 3, y + 6, arrow);
            g.fill(x + 3, y + 6, x + 4, y + 7, arrow);
            g.text(this.font, "Team Color", x + 10, y, TEXT_MAIN);
            g.text(this.font, "Teammates get their own hitbox color", x, y + 12, TEXT_FAINT);
            if (hovered) {
                tooltipText = "Back to Players";
            }
        } else {
            g.text(this.font, tab.label, x, y, TEXT_MAIN);
            g.text(this.font, tab.subtitle, x, y + 12, TEXT_FAINT);
        }
        g.fill(x, viewTop() - 5, contentRight() + 4, viewTop() - 4, HAIRLINE);
    }

    private void updateScroll() {
        maxScroll = Math.max(0, contentHeight() - (viewBottom() - viewTop()));
        scrollTarget = Mth.clamp(scrollTarget, 0.0f, maxScroll);
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        float diff = scrollTarget - scroll;
        if (Math.abs(diff) < 0.1f) {
            scroll = scrollTarget;
        } else {
            scroll += diff * (1.0f - (float) Math.exp(-14.0 * frameDt));
        }
    }

    private int slideOffset() {
        float a = Mth.clamp((System.currentTimeMillis() - tabSwitchMillis) / 160.0f, 0.0f, 1.0f);
        float ease = 1.0f - (1.0f - a) * (1.0f - a) * (1.0f - a);
        return Math.round((1.0f - ease) * 10.0f);
    }

    private void renderRows(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = contentX();
        int w = contentW();
        int top = viewTop();
        int bottom = viewBottom();

        g.enableScissor(x - 2, top, contentRight() + 2, bottom);

        int y = top + slideOffset() - Math.round(scroll);
        boolean blockHover = draggedRow != null || draggingScrollbar;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int h = row.height();
            if (y + h >= top && y <= bottom) {
                boolean hovered = !blockHover
                    && mouseX >= x && mouseX <= x + w
                    && mouseY >= y && mouseY <= y + h
                    && mouseY >= top && mouseY <= bottom;
                float hover = animate("row:" + tab + (teamPage ? ":team:" : ":") + i, hovered);
                row.render(g, x, y, w, mouseX, mouseY, hovered, hover);
            }
            y += h + ROW_GAP;
        }

        g.disableScissor();

        if (scroll > 0.5f) {
            g.fillGradient(x - 2, top, contentRight() + 2, top + 8, PANEL_BG, PANEL_BG & 0x00FFFFFF);
        }
        if (scroll < maxScroll - 0.5f) {
            g.fillGradient(x - 2, bottom - 8, contentRight() + 2, bottom, PANEL_BG & 0x00FFFFFF, PANEL_BG);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (maxScroll <= 0) {
            return;
        }
        int[] thumb = scrollThumbBounds();
        boolean hovered = contains(thumb, mouseX, mouseY);
        int color = draggingScrollbar ? ACCENT : (hovered ? SCROLL_THUMB_ACTIVE : SCROLL_THUMB);
        pillFill(g, thumb[0], thumb[1], thumb[2], thumb[3], color);
    }

    private int[] scrollThumbBounds() {
        int viewH = viewBottom() - viewTop();
        int thumbH = Math.max(20, viewH * viewH / Math.max(1, contentHeight()));
        int range = Math.max(1, viewH - thumbH);
        int thumbY = viewTop() + Math.round(scroll / Math.max(1, maxScroll) * range);
        return new int[]{panelX() + panelW() - 10, thumbY, 3, thumbH};
    }

    private void renderFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int left = panelX() + sidebarW() + 1;
        // Opaque footer band: mask any scrolled row content below the viewport so it can never
        // bleed into the Reset/Cancel/Save controls (keeps the fixed footer cleanly separated).
        g.fill(left, viewBottom(), panelX() + panelW(), panelY() + panelH(), PANEL_BG);
        g.fill(left, footerTop(), panelX() + panelW(), footerTop() + 1, HAIRLINE);

        int[] save = saveBounds();
        boolean saveHovered = contains(save, mouseX, mouseY);
        float saveHover = animate("btn:save", saveHovered);
        pillFill(g, save[0], save[1], save[2], save[3], blend(ACCENT, ACCENT_HOVER, saveHover));
        centeredNS(g, "Save", save[0] + save[2] / 2, save[1] + 5, ON_ACCENT);

        int[] cancel = cancelBounds();
        boolean cancelHovered = contains(cancel, mouseX, mouseY);
        float cancelHover = animate("btn:cancel", cancelHovered);
        pillFill(g, cancel[0], cancel[1], cancel[2], cancel[3], blend(CARD, CARD_HOVER, cancelHover));
        centeredNS(g, "Cancel", cancel[0] + cancel[2] / 2, cancel[1] + 5, blend(TEXT_DIM, TEXT_MAIN, cancelHover));

        int[] reset = resetBounds();
        boolean resetHovered = contains(reset, mouseX, mouseY);
        float resetHover = animate("btn:reset", resetHovered);
        if (resetHover > 0.02f) {
            pillFill(g, reset[0], reset[1], reset[2], reset[3], blend(PANEL_BG, CARD, resetHover));
        }
        centeredNS(g, "Reset Tab", reset[0] + reset[2] / 2, reset[1] + 5, blend(TEXT_FAINT, DANGER_HOVER, resetHover));
        if (resetHovered) {
            tooltipText = tab == Tab.PLAYERS
                ? "Restore the Team Color settings. Your player names stay."
                : "Restore this tab's default settings";
        }
    }

    // ===== Input =====

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (addPopupOpen) {
            if (event.button() == 0) {
                if (contains(popupAddBounds(), event.x(), event.y())) {
                    submitAddPopup();
                    return true;
                }
                if (contains(popupCancelBounds(), event.x(), event.y())) {
                    playClick();
                    closeAddPopup();
                    return true;
                }
            }
            if (contains(popupFieldBounds(), event.x(), event.y())) {
                return super.mouseClicked(event, doubled);
            }
            return true;
        }
        if (event.button() == 0 && handleLeftClick(event.x(), event.y())) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (addPopupOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeAddPopup();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitAddPopup();
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                applyAddCompletion();
                return true;
            }
            return super.keyPressed(event);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && tab == Tab.PLAYERS && teamPage) {
            closeTeamPage();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void init() {
        // Screen.init runs again on resize and when coming back from the color picker; it clears widgets.
        if (addPopupOpen) {
            createAddField(addField != null ? addField.getValue() : "");
        }
    }

    private boolean handleLeftClick(double mx, double my) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (contains(navItemBounds(i), mx, my)) {
                if (tab != tabs[i]) {
                    switchTab(tabs[i]);
                } else if (teamPage) {
                    closeTeamPage();
                }
                return true;
            }
        }

        if (tab == Tab.PLAYERS && teamPage && contains(backBounds(), mx, my)) {
            closeTeamPage();
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
        if (contains(resetBounds(), mx, my)) {
            playClick();
            resetCurrentTab();
            return true;
        }

        if (maxScroll > 0) {
            int[] thumb = scrollThumbBounds();
            int trackX = thumb[0];
            if (mx >= trackX - 3 && mx <= trackX + thumb[2] + 3 && my >= viewTop() && my <= viewBottom()) {
                if (my >= thumb[1] && my <= thumb[1] + thumb[3]) {
                    draggingScrollbar = true;
                    scrollbarGrabOffset = my - thumb[1];
                } else {
                    float page = (viewBottom() - viewTop()) * 0.85f;
                    scrollTarget = Mth.clamp(scrollTarget + (my < thumb[1] ? -page : page), 0.0f, maxScroll);
                }
                return true;
            }
        }

        int x = contentX();
        int w = contentW();
        if (mx >= x && mx <= x + w && my >= viewTop() && my <= viewBottom()) {
            int y = viewTop() - Math.round(scroll);
            for (Row row : rows) {
                int h = row.height();
                if (my >= y && my <= y + h) {
                    return row.mouseClicked(mx, my, x, y, w);
                }
                y += h + ROW_GAP;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (addPopupOpen) {
            return super.mouseDragged(event, dragX, dragY);
        }
        if (draggedRow != null) {
            draggedRow.applyDrag(event.x(), contentX(), contentW());
            return true;
        }
        if (draggingScrollbar && maxScroll > 0) {
            int viewH = viewBottom() - viewTop();
            int thumbH = Math.max(20, viewH * viewH / Math.max(1, contentHeight()));
            int range = Math.max(1, viewH - thumbH);
            float value = (float) (event.y() - scrollbarGrabOffset - viewTop()) / range * maxScroll;
            scroll = Mth.clamp(value, 0.0f, maxScroll);
            scrollTarget = scroll;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggedRow != null || draggingScrollbar) {
            draggedRow = null;
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (addPopupOpen) {
            return true;
        }
        if (mouseX >= panelX() + sidebarW() && mouseX <= panelX() + panelW()
            && mouseY >= panelY() && mouseY <= panelY() + panelH() && maxScroll > 0) {
            scrollTarget = Mth.clamp(scrollTarget - (float) (verticalAmount * 36.0), 0.0f, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void switchTab(Tab target) {
        playClick();
        tab = target;
        teamPage = false;
        if (addPopupOpen) {
            closeAddPopup();
        }
        scroll = 0.0f;
        scrollTarget = 0.0f;
        draggedRow = null;
        draggingScrollbar = false;
        tabSwitchMillis = System.currentTimeMillis();
        rebuildRows();
    }

    private void resetCurrentTab() {
        ModConfig defaults = new ModConfig();
        switch (tab) {
            case TOGGLES -> {
                working.enabled = defaults.enabled;
                working.openConfigHotkeyEnabled = defaults.openConfigHotkeyEnabled;
                working.renderOnlyWhenElytraEquipped = defaults.renderOnlyWhenElytraEquipped;
                working.renderProjectiles = defaults.renderProjectiles;
                working.renderEntities = defaults.renderEntities;
                working.lookingDirection = defaults.lookingDirection;
                working.renderHitboxBox = defaults.renderHitboxBox;
                working.removeDirectionArrow = defaults.removeDirectionArrow;
                working.foreheadLine = defaults.foreheadLine;
                working.selfHitbox = defaults.selfHitbox;
                working.hoverColorHitbox = defaults.hoverColorHitbox;
                working.hitColorHitbox = defaults.hitColorHitbox;
                working.noFireworkHitbox = defaults.noFireworkHitbox;
                working.noStuckArrows = defaults.noStuckArrows;
            }
            case SIZING -> {
                working.hitboxWidth = defaults.hitboxWidth;
                working.nonPlayerHitboxWidth = defaults.nonPlayerHitboxWidth;
                working.distanceHitboxBlocks = defaults.distanceHitboxBlocks;
                working.distanceHitboxWidth = defaults.distanceHitboxWidth;
                working.disableHitboxesAfterBlocks = defaults.disableHitboxesAfterBlocks;
            }
            case COLORS -> {
                working.hitboxMainColor = defaults.hitboxMainColor;
                working.rainbowMain = defaults.rainbowMain;
                working.hoverColor = defaults.hoverColor;
                working.rainbowHover = defaults.rainbowHover;
                working.hitColor = defaults.hitColor;
                working.rainbowHit = defaults.rainbowHit;
                working.hoverColorMobs = defaults.hoverColorMobs;
                working.rainbowHoverMobs = defaults.rainbowHoverMobs;
                working.lookingDirectionColor = defaults.lookingDirectionColor;
                working.rainbowLooking = defaults.rainbowLooking;
                working.foreheadLineColor = defaults.foreheadLineColor;
                working.rainbowForehead = defaults.rainbowForehead;
                working.projectilesColor = defaults.projectilesColor;
                working.rainbowProjectiles = defaults.rainbowProjectiles;
            }
            case PLAYERS -> {
                // Player names are kept on purpose; use the x buttons or /hbteam clear to remove them.
                working.teamColorEnabled = defaults.teamColorEnabled;
                working.teamColor = defaults.teamColor;
                working.rainbowTeam = defaults.rainbowTeam;
            }
        }
    }

    private void saveAndClose() {
        if (working.rainbowSpecificPlayers == null) {
            working.rainbowSpecificPlayers = "";
        }
        ModConfig live = ConfigManager.getConfig();
        live.copyFrom(working);
        live.normalize();
        ConfigManager.save();
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private void playClick() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ===== Team Color page =====

    private void openTeamPage() {
        teamPage = true;
        scroll = 0.0f;
        scrollTarget = 0.0f;
        tabSwitchMillis = System.currentTimeMillis();
        rebuildRows();
    }

    private void closeTeamPage() {
        playClick();
        if (addPopupOpen) {
            closeAddPopup();
        }
        teamPage = false;
        scroll = 0.0f;
        scrollTarget = 0.0f;
        tabSwitchMillis = System.currentTimeMillis();
        rebuildRows();
    }

    private int[] popupBounds() {
        int w = Math.min(280, panelW() - 40);
        int h = 108;
        return new int[]{(this.width - w) / 2, (this.height - h) / 2, w, h};
    }

    private int[] popupFieldBounds() {
        int[] p = popupBounds();
        return new int[]{p[0] + 12, p[1] + 30, p[2] - 24, 18};
    }

    private int[] popupAddBounds() {
        int[] p = popupBounds();
        return new int[]{p[0] + p[2] - 12 - 56, p[1] + p[3] - 26, 56, 18};
    }

    private int[] popupCancelBounds() {
        int[] add = popupAddBounds();
        return new int[]{add[0] - 6 - 56, add[1], 56, 18};
    }

    private void openAddPopup() {
        addPopupOpen = true;
        addPopupError = null;
        createAddField("");
    }

    private void createAddField(String value) {
        if (addField != null) {
            removeWidget(addField);
        }
        int[] box = popupFieldBounds();
        addField = new EditBox(this.font, box[0] + 6, box[1] + 5, box[2] - 12, 10, Component.literal("Player names"));
        addField.setBordered(false);
        addField.setMaxLength(256);
        addField.setTextColor(TEXT_MAIN);
        addField.setTextShadow(false);
        addField.setHint(Component.literal("Steve, Alex"));
        addField.setValue(value);
        addField.setResponder(text -> {
            completionIndex = -1;
            updateAddSuggestion();
        });
        // addWidget (not addRenderableWidget): the popup draws the field itself, on top of everything.
        addWidget(addField);
        setFocused(addField);
        updateAddSuggestion();
    }

    private void closeAddPopup() {
        if (addField != null) {
            removeWidget(addField);
        }
        addField = null;
        addPopupOpen = false;
        addPopupError = null;
        setFocused(null);
    }

    private void submitAddPopup() {
        if (addField == null) {
            return;
        }
        List<String> typed = TeamNames.split(addField.getValue());
        if (typed.isEmpty()) {
            addPopupError = "Type at least one player name.";
            return;
        }
        List<String> invalid = new ArrayList<>();
        boolean triedSelf = false;
        for (String name : typed) {
            if (name.equalsIgnoreCase(ownName())) {
                triedSelf = true;
            } else if (!TeamNames.isValid(name)) {
                invalid.add(name);
            } else {
                TeamNames.add(working.teamPlayers, name);
            }
        }
        playClick();
        rebuildRows();
        if (!invalid.isEmpty()) {
            // Keep only the names that need fixing in the box.
            addPopupError = "Not a valid name: " + String.join(", ", invalid);
            addField.setValue(String.join(", ", invalid));
            return;
        }
        if (triedSelf) {
            addPopupError = "You can't add yourself to your team.";
            addField.setValue("");
            return;
        }
        closeAddPopup();
    }

    private String ownName() {
        return this.minecraft.getUser().getName();
    }

    private void renderAddPopup(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0x99000000);
        int[] p = popupBounds();
        g.fill(p[0] + 3, p[1] + 3, p[0] + p[2] + 3, p[1] + p[3] + 3, PANEL_SHADOW);
        roundedFill(g, p[0], p[1], p[2], p[3], 4, PANEL_BG);
        g.outline(p[0], p[1], p[2], p[3], HAIRLINE);

        g.text(this.font, "Add Players", p[0] + 12, p[1] + 10, TEXT_MAIN);
        g.fill(p[0] + 12, p[1] + 21, p[0] + 30, p[1] + 23, ACCENT);

        int[] box = popupFieldBounds();
        g.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], CARD);
        g.outline(box[0], box[1], box[2], box[3], addField != null && addField.isFocused() ? ACCENT : HAIRLINE);
        if (addField != null) {
            addField.extractRenderState(g, mouseX, mouseY, delta);
        }

        textNS(g, "Split names with commas. Press Tab to autocomplete.", p[0] + 12, box[1] + 24, TEXT_FAINT);
        if (addPopupError != null) {
            textNS(g, trimmedDesc(addPopupError, p[2] - 24), p[0] + 12, box[1] + 36, DANGER);
        }

        int[] add = popupAddBounds();
        boolean addHovered = contains(add, mouseX, mouseY);
        float addHover = animate("popup:add", addHovered);
        pillFill(g, add[0], add[1], add[2], add[3], blend(ACCENT, ACCENT_HOVER, addHover));
        centeredNS(g, "Add", add[0] + add[2] / 2, add[1] + 5, ON_ACCENT);

        int[] cancel = popupCancelBounds();
        boolean cancelHovered = contains(cancel, mouseX, mouseY);
        float cancelHover = animate("popup:cancel", cancelHovered);
        pillFill(g, cancel[0], cancel[1], cancel[2], cancel[3], blend(CARD, CARD_HOVER, cancelHover));
        centeredNS(g, "Cancel", cancel[0] + cancel[2] / 2, cancel[1] + 5, blend(TEXT_DIM, TEXT_MAIN, cancelHover));
    }

    // Start of the name the cursor is in: after the last comma before the cursor, skipping spaces.
    private static int segmentStart(String value, int cursor) {
        int start = value.lastIndexOf(',', cursor - 1) + 1;
        while (start < cursor && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        return start;
    }

    private List<String> completionMatches(String value, int cursor) {
        int start = segmentStart(value, cursor);
        String prefix = value.substring(start, cursor).toLowerCase(Locale.ROOT);
        List<String> typedElsewhere = new ArrayList<>(TeamNames.split(value.substring(0, start)));
        typedElsewhere.addAll(TeamNames.split(value.substring(cursor)));

        List<String> result = new ArrayList<>();
        for (String name : knownPlayerNames()) {
            if (name.equalsIgnoreCase(ownName()) || TeamNames.contains(working.teamPlayers, name) || TeamNames.contains(typedElsewhere, name)
                || TeamNames.contains(result, name)) {
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(name);
            }
        }
        result.sort(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)));
        return result;
    }

    // Everyone in the tab list (whole server), plus players loaded around you.
    private List<String> knownPlayerNames() {
        List<String> names = new ArrayList<>();
        if (this.minecraft == null) {
            return names;
        }
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        if (this.minecraft.level != null) {
            for (Player player : this.minecraft.level.players()) {
                String name = player.getGameProfile().name();
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private void applyAddCompletion() {
        if (addField == null) {
            return;
        }
        String value = addField.getValue();
        int cursor = addField.getCursorPosition();
        int start = segmentStart(value, cursor);
        String prefix = value.substring(start, cursor).toLowerCase(Locale.ROOT);
        List<String> matches = completionMatches(value, cursor);
        if (matches.isEmpty()) {
            return;
        }
        if (!prefix.equals(lastCompletionPrefix) || completionIndex < 0) {
            completionIndex = 0;
            lastCompletionPrefix = prefix;
        } else {
            completionIndex = (completionIndex + 1) % matches.size();
        }
        String completion = matches.get(completionIndex);
        String updated = value.substring(0, start) + completion + value.substring(cursor);
        int keptIndex = completionIndex;
        String keptPrefix = lastCompletionPrefix;
        addField.setValue(updated);
        // setValue fires the responder, which resets the cycle; keep it so the next Tab picks the next name.
        completionIndex = keptIndex;
        lastCompletionPrefix = keptPrefix;
        int newCursor = start + completion.length();
        addField.setCursorPosition(newCursor);
        addField.setHighlightPos(newCursor);
        addField.setSuggestion("");
    }

    private void updateAddSuggestion() {
        if (addField == null) {
            return;
        }
        String value = addField.getValue();
        int cursor = addField.getCursorPosition();
        if (cursor != value.length()) {
            addField.setSuggestion("");
            return;
        }
        int start = segmentStart(value, cursor);
        String prefix = value.substring(start, cursor);
        List<String> matches = prefix.isEmpty() ? List.of() : completionMatches(value, cursor);
        if (matches.isEmpty() || matches.get(0).length() <= prefix.length()) {
            addField.setSuggestion("");
        } else {
            addField.setSuggestion(matches.get(0).substring(prefix.length()));
        }
    }

    private boolean isOnline(String name) {
        ClientPacketListener connection = this.minecraft.getConnection();
        return connection != null && connection.getPlayerInfoIgnoreCase(name) != null;
    }

    // Skin for the avatar: the server's skin if the player is online, else Minecraft's own player-head
    // skin cache (loads in the background and shows the default skin until it is ready).
    private PlayerSkin skinFor(String name) {
        ClientPacketListener connection = this.minecraft.getConnection();
        if (connection != null) {
            PlayerInfo info = connection.getPlayerInfoIgnoreCase(name);
            if (info != null) {
                return info.getSkin();
            }
        }
        try {
            ResolvableProfile profile = skinProfiles.computeIfAbsent(name.toLowerCase(Locale.ROOT),
                key -> ResolvableProfile.createUnresolved(name));
            return this.minecraft.playerSkinRenderCache().getOrDefault(profile).playerSkin();
        } catch (RuntimeException e) {
            return DefaultPlayerSkin.getDefaultSkin();
        }
    }

    // ===== Helpers =====

    private float animate(String key, boolean active) {
        float current = anim.getOrDefault(key, active ? 1.0f : 0.0f);
        float target = active ? 1.0f : 0.0f;
        float next = current + (target - current) * (1.0f - (float) Math.exp(-22.0 * frameDt));
        if (Math.abs(target - next) < 0.003f) {
            next = target;
        }
        anim.put(key, next);
        return next;
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

    private void pillFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    // Rounded rectangle with a real corner radius (pillFill only trims one pixel).
    private void roundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int dy = 0; dy < h; dy++) {
            int fromEdge = Math.min(dy, h - 1 - dy);
            int inset = 0;
            if (fromEdge < r) {
                double d = r - fromEdge - 0.5;
                inset = (int) Math.round(r - Math.sqrt(r * r - d * d));
            }
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
        }
    }

    private static int rainbowColor() {
        float hue = (System.currentTimeMillis() % 3000L) / 3000.0f;
        return 0xFF000000 | Mth.hsvToRgb(hue, 0.75f, 1.0f);
    }

    private static int blend(int from, int to, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a = (int) (((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
        int r = (int) (((from >>> 16) & 0xFF) + ((((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t));
        int gr = (int) (((from >>> 8) & 0xFF) + ((((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    private String trimmedDesc(String desc, int maxWidth) {
        if (this.font.width(desc) <= maxWidth) {
            return desc;
        }
        return this.font.plainSubstrByWidth(desc, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    // ===== Row types =====

    private abstract class Row {
        final String label;
        final String desc;

        Row(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }

        int height() {
            return 30;
        }

        int descMaxWidth(int w) {
            return w - 20;
        }

        int labelRestY() {
            return (height() - 8) / 2;
        }

        void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered, float hover) {
            pillFill(g, x, y, w, height(), blend(CARD, CARD_HOVER, hover));

            float labelShift = (6.0f - labelRestY()) * hover;
            String labelShown = trimmedDesc(label, descMaxWidth(w));
            g.pose().pushMatrix();
            g.pose().translate(0.0f, labelShift);
            g.text(font, labelShown, x + 10, y + labelRestY(), TEXT_MAIN);
            g.pose().popMatrix();

            if (hover > 0.05f) {
                String shown = trimmedDesc(desc, descMaxWidth(w));
                int alpha = Math.min(255, Math.round(hover * 255.0f));
                int descColor = (alpha << 24) | (TEXT_DIM & 0x00FFFFFF);
                g.pose().pushMatrix();
                g.pose().translate(0.0f, (1.0f - hover) * 3.0f);
                g.text(font, shown, x + 10, y + 17, descColor, false);
                g.pose().popMatrix();
                if (hovered && (!shown.equals(desc) || !labelShown.equals(label))) {
                    tooltipText = label + ": " + desc;
                }
            }
            renderControls(g, x, y, w, mouseX, mouseY, hovered);
        }

        abstract void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered);

        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            return false;
        }

        void applyDrag(double mx, int contentX, int contentW) {
        }
    }

    private class ToggleRow extends Row {
        final Supplier<Boolean> get;
        final Consumer<Boolean> set;

        ToggleRow(String label, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {
            super(label, desc);
            this.get = get;
            this.set = set;
        }

        @Override
        int descMaxWidth(int w) {
            return w - 52;
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
            boolean on = get.get();
            float p = animate("toggle:" + label, on);
            int px = x + w - 32;
            int py = y + 9;
            pillFill(g, px, py, 22, 12, blend(TRACK_OFF, ACCENT, p));
            g.pose().pushMatrix();
            g.pose().translate(p * 10.0f, 0.0f);
            pillFill(g, px + 2, py + 2, 8, 8, KNOB);
            g.pose().popMatrix();
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            playClick();
            set.accept(!get.get());
            return true;
        }
    }

    private class SliderRow extends Row {
        final float min;
        final float max;
        final float step;
        final boolean integer;
        final Supplier<Float> get;
        final Consumer<Float> set;

        SliderRow(String label, String desc, float min, float max, float step, boolean integer,
                  Supplier<Float> get, Consumer<Float> set) {
            super(label, desc);
            this.min = min;
            this.max = max;
            this.step = step;
            this.integer = integer;
            this.get = get;
            this.set = set;
        }

        @Override
        int height() {
            return 38;
        }

        @Override
        int descMaxWidth(int w) {
            return w - 64;
        }

        int trackX(int x) {
            return x + 10;
        }

        int trackW(int w) {
            return w - 20 - 44;
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
            boolean dragging = draggedRow == this;
            int tx = trackX(x);
            int tw = trackW(w);
            int ty = y + 27;
            float value = Mth.clamp(get.get(), min, max);
            float t = (value - min) / (max - min);

            pillFill(g, tx, ty, tw, 4, TRACK_OFF);
            int fillW = Math.round(t * tw);
            if (fillW > 1) {
                pillFill(g, tx, ty, fillW, 4, dragging ? ACCENT_HOVER : ACCENT);
            }
            int kx = tx + Math.round(t * (tw - 8));
            pillFill(g, kx - 1, ty - 3, 10, 10, blend(PANEL_BG, CARD, 1.0f));
            pillFill(g, kx, ty - 2, 8, 8, dragging ? ACCENT_HOVER : KNOB);

            String text = integer
                ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.2f", value);
            int textX = x + w - 10 - font.width(text);
            textNS(g, text, textX, ty - 2, dragging ? TEXT_MAIN : TEXT_DIM);
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            int tx = trackX(x);
            int tw = trackW(w);
            if (my >= y + 19 && mx >= tx - 5 && mx <= tx + tw + 5) {
                draggedRow = this;
                applyDrag(mx, x, w);
                return true;
            }
            return false;
        }

        @Override
        void applyDrag(double mx, int contentX, int contentW) {
            int tx = trackX(contentX);
            int tw = trackW(contentW);
            float t = Mth.clamp((float) ((mx - tx - 4) / (tw - 8)), 0.0f, 1.0f);
            float value = min + t * (max - min);
            value = Math.round(value / step) * step;
            set.accept(Mth.clamp(value, min, max));
        }
    }

    private class DualSliderRow extends Row {
        final Supplier<Integer> blocksGet;
        final Consumer<Integer> blocksSet;
        final Supplier<Float> widthGet;
        final Consumer<Float> widthSet;
        int dragChannel;

        DualSliderRow(String label, String desc,
                      Supplier<Integer> blocksGet, Consumer<Integer> blocksSet,
                      Supplier<Float> widthGet, Consumer<Float> widthSet) {
            super(label, desc);
            this.blocksGet = blocksGet;
            this.blocksSet = blocksSet;
            this.widthGet = widthGet;
            this.widthSet = widthSet;
        }

        @Override
        int height() {
            return 56;
        }

        @Override
        int labelRestY() {
            return 13;
        }

        int halfW(int w) {
            return (w - 20 - 10) / 2;
        }

        int trackX(int x, int w, int channel) {
            return channel == 0 ? x + 10 : x + 10 + halfW(w) + 10;
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
            for (int channel = 0; channel < 2; channel++) {
                boolean dragging = draggedRow == this && dragChannel == channel;
                int tx = trackX(x, w, channel);
                int tw = halfW(w);
                int ty = y + 44;
                float t = channel == 0
                    ? (Mth.clamp(blocksGet.get(), 1, 300) - 1) / 299.0f
                    : (Mth.clamp(widthGet.get(), 0.1f, 20.0f) - 0.1f) / 19.9f;

                String caption = channel == 0 ? "Blocks" : "Width";
                textNS(g, caption, tx, y + 31, TEXT_FAINT);
                String valueText = channel == 0
                    ? Integer.toString(blocksGet.get())
                    : String.format(Locale.ROOT, "%.2f", widthGet.get());
                textNS(g, valueText, tx + tw - font.width(valueText), y + 31, dragging ? TEXT_MAIN : TEXT_DIM);

                pillFill(g, tx, ty, tw, 4, TRACK_OFF);
                int fillW = Math.round(t * tw);
                if (fillW > 1) {
                    pillFill(g, tx, ty, fillW, 4, dragging ? ACCENT_HOVER : ACCENT);
                }
                int kx = tx + Math.round(t * (tw - 8));
                pillFill(g, kx - 1, ty - 3, 10, 10, blend(PANEL_BG, CARD, 1.0f));
                pillFill(g, kx, ty - 2, 8, 8, dragging ? ACCENT_HOVER : KNOB);
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            if (my < y + 36) {
                return false;
            }
            for (int channel = 0; channel < 2; channel++) {
                int tx = trackX(x, w, channel);
                int tw = halfW(w);
                if (mx >= tx - 5 && mx <= tx + tw + 5) {
                    draggedRow = this;
                    dragChannel = channel;
                    applyDrag(mx, x, w);
                    return true;
                }
            }
            return false;
        }

        @Override
        void applyDrag(double mx, int contentX, int contentW) {
            int tx = trackX(contentX, contentW, dragChannel);
            int tw = halfW(contentW);
            float t = Mth.clamp((float) ((mx - tx - 4) / (tw - 8)), 0.0f, 1.0f);
            if (dragChannel == 0) {
                blocksSet.accept(Mth.clamp(Math.round(1 + t * 299.0f), 1, 300));
            } else {
                float value = 0.1f + t * 19.9f;
                value = Math.round(value / 0.05f) * 0.05f;
                widthSet.accept(Mth.clamp(value, 0.1f, 20.0f));
            }
        }
    }

    private class ColorRow extends Row {
        final Supplier<Integer> colorGet;
        final Consumer<Integer> colorSet;
        final Supplier<Boolean> rainbowGet;
        final Consumer<Boolean> rainbowSet;

        ColorRow(String label, String desc,
                 Supplier<Integer> colorGet, Consumer<Integer> colorSet,
                 Supplier<Boolean> rainbowGet, Consumer<Boolean> rainbowSet) {
            super(label, desc);
            this.colorGet = colorGet;
            this.colorSet = colorSet;
            this.rainbowGet = rainbowGet;
            this.rainbowSet = rainbowSet;
        }

        @Override
        int descMaxWidth(int w) {
            return w - 142;
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
            boolean rainbow = rainbowGet.get();
            int swX = x + w - 26;
            int swY = y + 7;
            int swatchColor = rainbow ? rainbowColor() : (0xFF000000 | (colorGet.get() & 0xFFFFFF));
            pillFill(g, swX, swY, 16, 16, swatchColor);
            g.outline(swX, swY, 16, 16, 0x38FFFFFF);

            float p = animate("rainbow:" + label, rainbow);
            int pillX = swX - 32;
            int pillY = y + 9;
            pillFill(g, pillX, pillY, 22, 12, blend(TRACK_OFF, rainbowColor(), p));
            g.pose().pushMatrix();
            g.pose().translate(p * 10.0f, 0.0f);
            pillFill(g, pillX + 2, pillY + 2, 8, 8, KNOB);
            g.pose().popMatrix();

            String hex = rainbow ? "RAINBOW" : String.format(Locale.ROOT, "#%06X", colorGet.get() & 0xFFFFFF);
            int hexColor = rainbow ? rainbowColor() : TEXT_DIM;
            textNS(g, hex, pillX - 8 - font.width(hex), y + 11, hexColor);

            if (mouseX >= pillX - 2 && mouseX <= pillX + 24 && mouseY >= y + 6 && mouseY <= y + 24 && hovered) {
                tooltipText = "Rainbow cycle for this color";
            }
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            int swX = x + w - 26;
            int pillX = swX - 32;
            if (mx >= pillX - 2 && mx <= pillX + 24) {
                playClick();
                rainbowSet.accept(!rainbowGet.get());
                return true;
            }
            playClick();
            minecraft.gui.setScreen(new ColorPickerScreen(CustomConfigScreen.this, Component.literal(label),
                colorGet.get(), colorSet::accept));
            return true;
        }
    }

    private class ActionRow extends Row {
        final String buttonLabel;
        final Runnable action;

        ActionRow(String label, String desc, String buttonLabel, Runnable action) {
            super(label, desc);
            this.buttonLabel = buttonLabel;
            this.action = action;
        }

        @Override
        int descMaxWidth(int w) {
            return w - 70;
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
            int bw = font.width(buttonLabel) + 16;
            int bx = x + w - bw - 8;
            int by = y + 7;
            float hover = animate("action:" + label, hovered);
            pillFill(g, bx, by, bw, 16, blend(TRACK_OFF, ACCENT, hover));
            centeredNS(g, buttonLabel, bx + bw / 2, by + 4, blend(TEXT_MAIN, ON_ACCENT, hover));
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            playClick();
            action.run();
            return true;
        }
    }

    // ===== Players tab rows =====

    // The Players tab home: one rounded tile per feature. Only Team Color for now.
    private class TileRow extends Row {
        static final int TILE = 78;

        TileRow() {
            super("Team Color", TEAM_COLOR_INFO);
        }

        @Override
        int height() {
            return TILE + 4;
        }

        int[] tileBounds(int x, int y) {
            return new int[]{x + 1, y + 2, TILE, TILE};
        }

        int[] infoBounds(int x, int y) {
            int[] t = tileBounds(x, y);
            return new int[]{t[0] + TILE - 15, t[1] + 5, 10, 10};
        }

        @Override
        void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered, float hover) {
            int[] t = tileBounds(x, y);
            boolean overTile = contains(t, mouseX, mouseY);
            float h = animate("tile:team", overTile);
            roundedFill(g, t[0] - 1, t[1] - 1, TILE + 2, TILE + 2, 7, blend(HAIRLINE, ACCENT, h));
            roundedFill(g, t[0], t[1], TILE, TILE, 6, blend(CARD, CARD_HOVER, h));

            int iconColor = working.rainbowTeam ? rainbowColor() : (0xFF000000 | (working.teamColor & 0xFFFFFF));
            renderShieldIcon(g, t[0] + TILE / 2 - 9, t[1] + 17, iconColor);
            centeredNS(g, "Team Color", t[0] + TILE / 2, t[1] + TILE - 19, TEXT_MAIN);

            int[] info = infoBounds(x, y);
            boolean overInfo = contains(info, mouseX, mouseY);
            float ih = animate("tile:team:info", overInfo);
            roundedFill(g, info[0], info[1], info[2], info[3], 5, blend(TRACK_OFF, ACCENT, ih));
            int glyph = blend(TEXT_MAIN, ON_ACCENT, ih);
            g.fill(info[0] + 4, info[1] + 2, info[0] + 6, info[1] + 3, glyph);
            g.fill(info[0] + 4, info[1] + 4, info[0] + 6, info[1] + 8, glyph);

            if (overInfo) {
                tooltipLines = font.split(Component.literal(TEAM_COLOR_INFO), 220);
            } else if (overTile) {
                tooltipText = "Open Team Color settings";
            }
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            if (contains(tileBounds(x, y), mx, my)) {
                playClick();
                openTeamPage();
                return true;
            }
            return false;
        }
    }

    // 18x20 shield in the team color with a player cut out of it.
    private void renderShieldIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        int[][] shape = {
            {2, 16}, {1, 17}, {0, 18}, {0, 18}, {0, 18}, {0, 18}, {0, 18}, {0, 18}, {0, 18}, {0, 18},
            {0, 18}, {1, 17}, {1, 17}, {2, 16}, {3, 15}, {4, 14}, {5, 13}, {6, 12}, {7, 11}, {8, 10}
        };
        for (int i = 0; i < shape.length; i++) {
            g.fill(x + shape[i][0], y + i, x + shape[i][1], y + i + 1, color);
        }
        int cut = 0xCC0B0F16;
        g.fill(x + 7, y + 3, x + 11, y + 7, cut);
        g.fill(x + 5, y + 8, x + 13, y + 13, cut);
        g.fill(x + 6, y + 13, x + 12, y + 14, cut);
    }

    private class SectionRow extends Row {
        final boolean addButton;

        SectionRow(String title, boolean addButton) {
            super(title, "");
            this.addButton = addButton;
        }

        @Override
        int height() {
            return 22;
        }

        int[] plusBounds(int x, int y, int w) {
            return new int[]{x + w - 20, y + 5, 18, 15};
        }

        @Override
        void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered, float hover) {
            g.text(font, label, x + 2, y + 9, TEXT_MAIN);
            int lineX = x + 8 + font.width(label);
            int lineRight = addButton ? x + w - 26 : x + w;
            if (lineRight > lineX) {
                g.fill(lineX, y + 13, lineRight, y + 14, HAIRLINE);
            }
            if (addButton) {
                int[] b = plusBounds(x, y, w);
                boolean over = contains(b, mouseX, mouseY);
                float h = animate("plus:" + label, over);
                pillFill(g, b[0], b[1], b[2], b[3], blend(TRACK_OFF, ACCENT, h));
                int c = blend(TEXT_MAIN, ON_ACCENT, h);
                int cx = b[0] + b[2] / 2;
                int cy = b[1] + b[3] / 2;
                g.fill(cx - 3, cy, cx + 4, cy + 1, c);
                g.fill(cx, cy - 3, cx + 1, cy + 4, c);
                if (over) {
                    tooltipText = "Add players";
                }
            }
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            if (addButton && contains(plusBounds(x, y, w), mx, my)) {
                playClick();
                openAddPopup();
                return true;
            }
            return false;
        }
    }

    private class NameRow extends Row {
        NameRow(String name) {
            super(name, "");
        }

        @Override
        int height() {
            return 24;
        }

        int[] removeBounds(int x, int y, int w) {
            return new int[]{x + w - 22, y + 4, 16, 16};
        }

        @Override
        void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered, float hover) {
            pillFill(g, x, y, w, height(), blend(CARD, CARD_HOVER, hover));
            PlayerFaceExtractor.extractRenderState(g, skinFor(label), x + 6, y + 4, 16);
            g.text(font, label, x + 28, y + 8, TEXT_MAIN);

            int[] b = removeBounds(x, y, w);
            if (isOnline(label)) {
                String online = "Online";
                int ox = b[0] - 8 - font.width(online);
                g.fill(ox - 7, y + 10, ox - 3, y + 14, ONLINE);
                textNS(g, online, ox, y + 8, TEXT_DIM);
            }

            boolean over = contains(b, mouseX, mouseY);
            float h = animate("remove:" + label, over);
            if (h > 0.02f) {
                pillFill(g, b[0], b[1], b[2], b[3], blend(CARD_HOVER, 0xFF3A2228, h));
            }
            int c = over ? DANGER_HOVER : (hovered ? DANGER : TEXT_FAINT);
            for (int i = 0; i < 7; i++) {
                g.fill(b[0] + 4 + i, b[1] + 4 + i, b[0] + 5 + i, b[1] + 5 + i, c);
                g.fill(b[0] + 10 - i, b[1] + 4 + i, b[0] + 11 - i, b[1] + 5 + i, c);
            }
            if (over) {
                tooltipText = "Remove " + label;
            }
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            if (contains(removeBounds(x, y, w), mx, my)) {
                playClick();
                TeamNames.remove(working.teamPlayers, label);
                rebuildRows();
                return true;
            }
            return false;
        }
    }

    private class CommandRow extends Row {
        final String command;

        CommandRow(String meaning, String command) {
            super(meaning, "");
            this.command = command;
        }

        @Override
        int height() {
            return 20;
        }

        @Override
        void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered, float hover) {
            pillFill(g, x, y, w, height(), blend(PANEL_BG, CARD, 0.5f + hover * 0.5f));
            textNS(g, label + ":", x + 10, y + 6, TEXT_DIM);
            // Commands line up in one column, right after the widest label.
            int labelColumn = 0;
            for (String meaning : new String[]{"Add a player:", "Remove a player:", "Show your team:", "Remove everyone:"}) {
                labelColumn = Math.max(labelColumn, font.width(meaning));
            }
            int commandX = x + 10 + labelColumn + 10;
            textNS(g, trimmedDesc(command, x + w - 8 - commandX), commandX, y + 6, blend(ACCENT, ACCENT_HOVER, hover));
            if (hovered) {
                tooltipText = "Click to copy";
            }
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
        }

        @Override
        boolean mouseClicked(double mx, double my, int x, int y, int w) {
            playClick();
            // "/hbteam add <player>" copies as "/hbteam add " so you can type the name right after pasting.
            minecraft.keyboardHandler.setClipboard(command.replace("<player>", ""));
            return true;
        }
    }

    private class InfoRow extends Row {
        InfoRow(String text) {
            super(text, "");
        }

        @Override
        int height() {
            return 16;
        }

        @Override
        void render(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered, float hover) {
            textNS(g, trimmedDesc(label, w - 8), x + 4, y + 4, TEXT_FAINT);
        }

        @Override
        void renderControls(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
        }
    }
}
