package itzglecni.customhitboxes.config;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final float MIN_WIDTH = 0.1f;
    private static final float MAX_WIDTH = 20.0f;
    private static final int MIN_BLOCKS = 1;
    private static final int MAX_BLOCKS = 300;

    public boolean enabled = true;
    public boolean renderEntities = false;
    public boolean renderOnlyWhenElytraEquipped = false;
    public boolean renderProjectiles = true;
    public boolean lookingDirection = false;
    public boolean removeDirectionArrow = true;
    public boolean foreheadLine = false;
    public boolean selfHitbox = false;
    public boolean hoverColorHitbox = true;
    public boolean hitColorHitbox = true;
    public boolean renderHitboxBox = true;
    public boolean noFireworkHitbox = true;
    public boolean noStuckArrows = true;
    public boolean openConfigHotkeyEnabled = true;

    public float hitboxWidth = 2.5f;
    public float nonPlayerHitboxWidth = 2.3f;
    public int distanceHitboxBlocks = 30;
    public float distanceHitboxWidth = 1.0f;
    public int disableHitboxesAfterBlocks = 300;

    public int hitboxMainColor = 0xFFFFFF;
    public boolean rainbowMain = false;
    
    public int hoverColor = 0x00FF00;
    public boolean rainbowHover = false;
    
    public int hitColor = 0xFF0000;
    public boolean rainbowHit = false;

    public int hoverColorMobs = 0xFFA500;
    public boolean rainbowHoverMobs = false;
    
    public int lookingDirectionColor = 0x0000FF;
    public boolean rainbowLooking = false;
    
    public int foreheadLineColor = 0x00FFFF;
    public boolean rainbowForehead = false;
    
    public int projectilesColor = 0x0000FF;
    public boolean rainbowProjectiles = false;

    public String rainbowSpecificPlayers = "";

    // Team Color (Players tab)
    public boolean teamColorEnabled = true;
    public int teamColor = 0x3B82F6;
    public boolean rainbowTeam = false;
    public List<String> teamPlayers = new ArrayList<>();

    public ModConfig copy() {
        ModConfig out = new ModConfig();
        out.copyFrom(this);
        return out;
    }

    public void copyFrom(ModConfig other) {
        this.enabled = other.enabled;
        this.renderEntities = other.renderEntities;
        this.renderOnlyWhenElytraEquipped = other.renderOnlyWhenElytraEquipped;
        this.renderProjectiles = other.renderProjectiles;
        this.lookingDirection = other.lookingDirection;
        this.removeDirectionArrow = other.removeDirectionArrow;
        this.foreheadLine = other.foreheadLine;
        this.selfHitbox = other.selfHitbox;
        this.hoverColorHitbox = other.hoverColorHitbox;
        this.hitColorHitbox = other.hitColorHitbox;
        this.renderHitboxBox = other.renderHitboxBox;
        this.noFireworkHitbox = other.noFireworkHitbox;
        this.noStuckArrows = other.noStuckArrows;
        this.openConfigHotkeyEnabled = other.openConfigHotkeyEnabled;
        this.hitboxWidth = other.hitboxWidth;
        this.nonPlayerHitboxWidth = other.nonPlayerHitboxWidth;
        this.distanceHitboxBlocks = other.distanceHitboxBlocks;
        this.distanceHitboxWidth = other.distanceHitboxWidth;
        this.disableHitboxesAfterBlocks = other.disableHitboxesAfterBlocks;
        this.hitboxMainColor = other.hitboxMainColor;
        this.rainbowMain = other.rainbowMain;
        this.hoverColor = other.hoverColor;
        this.rainbowHover = other.rainbowHover;
        this.hitColor = other.hitColor;
        this.rainbowHit = other.rainbowHit;
        this.hoverColorMobs = other.hoverColorMobs;
        this.rainbowHoverMobs = other.rainbowHoverMobs;
        this.lookingDirectionColor = other.lookingDirectionColor;
        this.rainbowLooking = other.rainbowLooking;
        this.foreheadLineColor = other.foreheadLineColor;
        this.rainbowForehead = other.rainbowForehead;
        this.projectilesColor = other.projectilesColor;
        this.rainbowProjectiles = other.rainbowProjectiles;
        this.rainbowSpecificPlayers = other.rainbowSpecificPlayers;
        this.teamColorEnabled = other.teamColorEnabled;
        this.teamColor = other.teamColor;
        this.rainbowTeam = other.rainbowTeam;
        this.teamPlayers = other.teamPlayers == null ? new ArrayList<>() : new ArrayList<>(other.teamPlayers);
    }

    public void normalize() {
        this.hitboxWidth = clampWidth(this.hitboxWidth);
        this.nonPlayerHitboxWidth = clampWidth(this.nonPlayerHitboxWidth);
        this.distanceHitboxBlocks = clampBlocks(this.distanceHitboxBlocks);
        this.distanceHitboxWidth = clampWidth(this.distanceHitboxWidth);
        this.disableHitboxesAfterBlocks = clampBlocks(this.disableHitboxesAfterBlocks);

        if (this.rainbowSpecificPlayers == null) {
            this.rainbowSpecificPlayers = "";
        }

        // Drop blanks, invalid names and case-insensitive duplicates (keeps the first spelling).
        List<String> cleaned = new ArrayList<>();
        if (this.teamPlayers != null) {
            for (String name : this.teamPlayers) {
                TeamNames.add(cleaned, name);
            }
        }
        this.teamPlayers = cleaned;
    }

    private static float clampWidth(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 2.5f;
        }
        return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, value));
    }

    private static int clampBlocks(int value) {
        return Math.max(MIN_BLOCKS, Math.min(MAX_BLOCKS, value));
    }
}