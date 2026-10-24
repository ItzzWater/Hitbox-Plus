package itzglecni.customhitboxes.mixin;

import itzglecni.customhitboxes.config.ConfigManager;
import itzglecni.customhitboxes.config.ModConfig;
import itzglecni.customhitboxes.config.TeamNames;
import itzglecni.customhitboxes.tracker.HitTracker;
import itzglecni.customhitboxes.tracker.HoverPicker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityRenderManagerMixin {

    // drawHitbox runs once per entity, so compute the attack targets once per frame here.
    private static Set<Entity> hoveredEntities = Set.of();
    private static Set<String> teamPlayers = Set.of();

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {
        hoveredEntities = HoverPicker.findAttackTargets(MinecraftClient.getInstance());
        ModConfig config = ConfigManager.getConfig();
        teamPlayers = config.teamColorEnabled ? TeamNames.lowerCaseSet(config.teamPlayers) : Set.of();
    }

    @Inject(method = "drawHitbox", at = @At("HEAD"), cancellable = true)
    private void onDrawHitbox(Entity entity, float tickDelta, boolean localServerEntity, CallbackInfo ci) {
        ModConfig config = ConfigManager.getConfig();

        if (!config.enabled) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity localPlayer = client.player;
        boolean allowSelfHitbox = config.selfHitbox
            && client.options != null
            && !client.options.getPerspective().isFirstPerson();
        Set<String> rainbowSpecificPlayers = parseRainbowSpecificPlayers(config.rainbowSpecificPlayers);

        if (!shouldRenderEntity(entity, localPlayer, config, allowSelfHitbox)) {
            ci.cancel();
            return;
        }

        // Confirm a hit only after the target is actually in hurt state.
        HitTracker.confirmDamageState(entity);

        int resolvedColor = resolveColor(entity, localPlayer, config, hoveredEntities, rainbowSpecificPlayers, teamPlayers);
        float lineWidth = resolveLineWidth(entity, localPlayer, config);
        
        int argbColor = withFullAlpha(resolvedColor);
        Box box = getLerpedBox(entity, tickDelta);

        // Render Hitbox Box toggle applies to players only; mobs and projectiles always keep their box.
        boolean isPlayer = entity instanceof PlayerEntity;
        if (!isPlayer || config.renderHitboxBox) {
            GizmoDrawing.box(box, DrawStyle.stroked(argbColor, lineWidth));
        }

        if (config.lookingDirection) {
            int dirColor = useRainbowForEntity(entity, config.rainbowLooking, rainbowSpecificPlayers)
                ? getRainbowColor()
                : config.lookingDirectionColor;
            int dirArgb = withFullAlpha(dirColor);
            Vec3d lookVec = entity.getRotationVec(tickDelta);
            Vec3d basePos = entity.getLerpedPos(tickDelta);
            float eyeHeight = entity.getStandingEyeHeight();
            float length = 2.0f;
            Vec3d start = basePos.add(0.0, eyeHeight, 0.0);
            Vec3d end = start.add(lookVec.multiply(length));

            if (!config.removeDirectionArrow) {
                GizmoDrawing.arrow(start, end, dirArgb, lineWidth);
            } else {
                GizmoDrawing.line(start, end, dirArgb, lineWidth);
            }
        }

        if (config.foreheadLine && entity instanceof LivingEntity) {
            int foreColor = useRainbowForEntity(entity, config.rainbowForehead, rainbowSpecificPlayers)
                ? getRainbowColor()
                : config.foreheadLineColor;
            int foreArgb = withFullAlpha(foreColor);
            float eyeHeight = entity.getStandingEyeHeight();
            Vec3d basePos = entity.getLerpedPos(tickDelta);
            double y = basePos.y + eyeHeight;
            Box foreheadBox = new Box(box.minX, y - 0.01f, box.minZ, box.maxX, y + 0.01f, box.maxZ);
            GizmoDrawing.box(foreheadBox, DrawStyle.stroked(foreArgb, lineWidth));
        }

        ci.cancel();
    }

    private static boolean shouldRenderEntity(Entity entity, PlayerEntity localPlayer, ModConfig config, boolean allowSelfHitbox) {
        if (entity == localPlayer && !allowSelfHitbox) {
            return false;
        }

        if (!config.renderProjectiles && entity instanceof ProjectileEntity) {
            return false;
        }

        if (config.renderEntities && !(entity instanceof PlayerEntity) && !(entity instanceof ProjectileEntity)) {
            return false;
        }

        if (config.noFireworkHitbox && entity instanceof FireworkRocketEntity) {
            return false;
        }

        if (config.noStuckArrows && entity instanceof PersistentProjectileEntity ppe && ppe.getVelocity().lengthSquared() == 0) {
            return false;
        }

        if (config.renderOnlyWhenElytraEquipped && entity instanceof PlayerEntity targetPlayer && targetPlayer != localPlayer) {
            if (!targetPlayer.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
                return false;
            }
        }

        if (localPlayer != null && entity != localPlayer) {
            double maxDistance = config.disableHitboxesAfterBlocks;
            if (localPlayer.squaredDistanceTo(entity) > (maxDistance * maxDistance)) {
                return false;
            }
        }

        return true;
    }

    private static int resolveColor(Entity entity, PlayerEntity localPlayer, ModConfig config, Set<Entity> hoveredEntities,
                                    Set<String> rainbowSpecificPlayers, Set<String> teamPlayers) {
        int resolvedColor = useRainbowForEntity(entity, config.rainbowMain, rainbowSpecificPlayers)
            ? getRainbowColor()
            : config.hitboxMainColor;

        if (entity instanceof ProjectileEntity) {
            resolvedColor = useRainbowForEntity(entity, config.rainbowProjectiles, rainbowSpecificPlayers)
                ? getRainbowColor()
                : config.projectilesColor;
        }

        // Team Color replaces the main color for players on your team list.
        boolean teammate = isTeammate(entity, teamPlayers);
        int teamColor = config.rainbowTeam ? getRainbowColor() : config.teamColor;
        if (teammate) {
            resolvedColor = teamColor;
        }

        // hoveredEntities = what a left click right now would hit, straight from Minecraft's attack logic.
        if (config.hoverColorHitbox && hoveredEntities.contains(entity)) {
            if (entity instanceof PlayerEntity) {
                resolvedColor = useRainbowForEntity(entity, config.rainbowHover, rainbowSpecificPlayers)
                    ? getRainbowColor()
                    : config.hoverColor;
            } else if (entity instanceof LivingEntity) {
                resolvedColor = useRainbowForEntity(entity, config.rainbowHoverMobs, rainbowSpecificPlayers)
                    ? getRainbowColor()
                    : config.hoverColorMobs;
            }
        }

        if (config.hitColorHitbox && entity instanceof PlayerEntity && HitTracker.wasRecentlyHit(entity.getId())) {
            resolvedColor = useRainbowForEntity(entity, config.rainbowHit, rainbowSpecificPlayers)
                ? getRainbowColor()
                : config.hitColor;
        }

        return resolvedColor;
    }

    private static boolean isTeammate(Entity entity, Set<String> teamPlayers) {
        if (teamPlayers.isEmpty() || !(entity instanceof PlayerEntity player)) {
            return false;
        }
        String name = player.getGameProfile().name();
        return name != null && teamPlayers.contains(name.toLowerCase(Locale.ROOT));
    }

    private static float resolveLineWidth(Entity entity, PlayerEntity localPlayer, ModConfig config) {
        if (!(entity instanceof PlayerEntity) || entity == localPlayer || localPlayer == null) {
            return entity instanceof PlayerEntity ? config.hitboxWidth : config.nonPlayerHitboxWidth;
        }

        double distanceThreshold = config.distanceHitboxBlocks;
        if (localPlayer.squaredDistanceTo(entity) > (distanceThreshold * distanceThreshold)) {
            return config.distanceHitboxWidth;
        }

        return config.hitboxWidth;
    }

    private static boolean useRainbowForEntity(Entity entity, boolean rainbowEnabled, Set<String> rainbowSpecificPlayers) {
        if (!rainbowEnabled) {
            return false;
        }

        if (rainbowSpecificPlayers.isEmpty()) {
            return true;
        }

        if (!(entity instanceof PlayerEntity player)) {
            return false;
        }

        String name = player.getGameProfile().name();
        if (name == null || name.isBlank()) {
            return false;
        }

        return rainbowSpecificPlayers.contains(name.toLowerCase(Locale.ROOT));
    }

    private static Set<String> parseRainbowSpecificPlayers(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }

        Set<String> out = new HashSet<>();
        String[] parts = csv.split(",");
        for (String part : parts) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private static Box getLerpedBox(Entity entity, float tickDelta) {
        Box base = entity.getBoundingBox();
        Vec3d lerpedPos = entity.getLerpedPos(tickDelta);
        Vec3d posDelta = lerpedPos.subtract(entity.getX(), entity.getY(), entity.getZ());
        return base.offset(posDelta.x, posDelta.y, posDelta.z);
    }

    private static int withFullAlpha(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static int getRainbowColor() {
        float hue = (System.currentTimeMillis() % 2000L) / 2000f;
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }
}