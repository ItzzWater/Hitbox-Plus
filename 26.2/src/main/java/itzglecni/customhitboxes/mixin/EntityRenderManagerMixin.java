package itzglecni.customhitboxes.mixin;

import itzglecni.customhitboxes.config.ConfigManager;
import itzglecni.customhitboxes.config.ModConfig;
import itzglecni.customhitboxes.config.TeamNames;
import itzglecni.customhitboxes.tracker.HitTracker;
import itzglecni.customhitboxes.tracker.HoverPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.gizmos.ArrowGizmo;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.LineGizmo;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityRenderManagerMixin {

    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void onEmitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValueAccess, Frustum frustum, float tickDelta, CallbackInfo ci) {
        ModConfig config = ConfigManager.getConfig();

        if (!config.enabled) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Player localPlayer = client.player;
        boolean allowSelfHitbox = config.selfHitbox
            && client.options != null
            && !client.options.getCameraType().isFirstPerson();
        Set<String> rainbowSpecificPlayers = parseRainbowSpecificPlayers(config.rainbowSpecificPlayers);
        Set<String> teamPlayers = config.teamColorEnabled ? TeamNames.lowerCaseSet(config.teamPlayers) : Set.of();
        Set<Entity> hoveredEntities = HoverPicker.findAttackTargets(client);

        // We cancel the vanilla renderer entirely, then re-emit our own gizmos per entity.
        ci.cancel();

        boolean renderedLocalPlayer = false;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == localPlayer) {
                renderedLocalPlayer = true;
            }

            if (!shouldRenderEntity(entity, localPlayer, config, frustum, false, allowSelfHitbox)) {
                continue;
            }

            emitEntityGizmos(entity, localPlayer, config, hoveredEntities, rainbowSpecificPlayers, teamPlayers, tickDelta);
        }

        if (allowSelfHitbox && localPlayer != null && !renderedLocalPlayer && shouldRenderEntity(localPlayer, localPlayer, config, frustum, true, allowSelfHitbox)) {
            emitEntityGizmos(localPlayer, localPlayer, config, hoveredEntities, rainbowSpecificPlayers, teamPlayers, tickDelta);
        }
    }

    private static void emitEntityGizmos(Entity entity, Player localPlayer, ModConfig config, Set<Entity> hoveredEntities,
                                         Set<String> rainbowSpecificPlayers, Set<String> teamPlayers, float tickDelta) {
        HitTracker.confirmDamageState(entity);

        int resolvedColor = resolveColor(entity, localPlayer, config, hoveredEntities, rainbowSpecificPlayers, teamPlayers);
        float lineWidth = resolveLineWidth(entity, localPlayer, config);
        int argbColor = withFullAlpha(resolvedColor);
        AABB box = getLerpedBox(entity, tickDelta);

        GizmoStyle style = GizmoStyle.stroke(argbColor, lineWidth);
        // Render Hitbox Box toggle applies to players only; mobs and projectiles always keep their box.
        boolean isPlayer = entity instanceof Player;
        if (!isPlayer || config.renderHitboxBox) {
            Gizmos.addGizmo(new CuboidGizmo(box, style, false));
        }

        if (config.lookingDirection) {
            int dirColor = useRainbowForEntity(entity, config.rainbowLooking, rainbowSpecificPlayers)
                ? getRainbowColor()
                : config.lookingDirectionColor;
            int dirArgb = withFullAlpha(dirColor);
            Vec3 lookVec = entity.getViewVector(tickDelta);
            Vec3 basePos = entity.getPosition(tickDelta);
            float eyeHeight = entity.getEyeHeight();
            float length = 2.0f;
            Vec3 start = basePos.add(0.0, eyeHeight, 0.0);
            Vec3 end = start.add(lookVec.scale(length));

            if (!config.removeDirectionArrow) {
                Gizmos.addGizmo(new ArrowGizmo(start, end, dirArgb, lineWidth));
            } else {
                Gizmos.addGizmo(new LineGizmo(start, end, dirArgb, lineWidth));
            }
        }

        if (config.foreheadLine && entity instanceof LivingEntity) {
            int foreColor = useRainbowForEntity(entity, config.rainbowForehead, rainbowSpecificPlayers)
                ? getRainbowColor()
                : config.foreheadLineColor;
            int foreArgb = withFullAlpha(foreColor);
            float eyeHeight = entity.getEyeHeight();
            Vec3 basePos = entity.getPosition(tickDelta);
            double y = basePos.y + eyeHeight;
            AABB foreheadBox = new AABB(box.minX, y - 0.01f, box.minZ, box.maxX, y + 0.01f, box.maxZ);
            Gizmos.addGizmo(new CuboidGizmo(foreheadBox, GizmoStyle.stroke(foreArgb, lineWidth), false));
        }
    }

    private static boolean shouldRenderEntity(Entity entity, Player localPlayer, ModConfig config, Frustum frustum,
                                              boolean bypassFrustum, boolean allowSelfHitbox) {
        if (!bypassFrustum && entity != localPlayer && !frustum.isVisible(entity.getBoundingBox())) {
            return false;
        }

        if (entity == localPlayer && !allowSelfHitbox) {
            return false;
        }

        if (!config.renderProjectiles && entity instanceof Projectile) {
            return false;
        }

        if (config.renderEntities && !(entity instanceof Player) && !(entity instanceof Projectile)) {
            return false;
        }

        if (config.noFireworkHitbox && entity instanceof FireworkRocketEntity) {
            return false;
        }

        if (config.noStuckArrows && entity instanceof AbstractArrow arrow && arrow.getDeltaMovement().lengthSqr() == 0) {
            return false;
        }

        if (config.renderOnlyWhenElytraEquipped && entity instanceof Player targetPlayer && targetPlayer != localPlayer) {
            if (!targetPlayer.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                return false;
            }
        }

        if (localPlayer != null && entity != localPlayer) {
            double maxDistance = config.disableHitboxesAfterBlocks;
            if (localPlayer.distanceToSqr(entity) > (maxDistance * maxDistance)) {
                return false;
            }
        }

        return true;
    }

    private static int resolveColor(Entity entity, Player localPlayer, ModConfig config, Set<Entity> hoveredEntities,
                                    Set<String> rainbowSpecificPlayers, Set<String> teamPlayers) {
        int resolvedColor = useRainbowForEntity(entity, config.rainbowMain, rainbowSpecificPlayers)
            ? getRainbowColor()
            : config.hitboxMainColor;

        if (entity instanceof Projectile) {
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
            if (entity instanceof Player) {
                resolvedColor = useRainbowForEntity(entity, config.rainbowHover, rainbowSpecificPlayers)
                    ? getRainbowColor()
                    : config.hoverColor;
            } else if (entity instanceof LivingEntity) {
                resolvedColor = useRainbowForEntity(entity, config.rainbowHoverMobs, rainbowSpecificPlayers)
                    ? getRainbowColor()
                    : config.hoverColorMobs;
            }
        }

        if (config.hitColorHitbox && entity instanceof Player && HitTracker.wasRecentlyHit(entity.getId())) {
            resolvedColor = useRainbowForEntity(entity, config.rainbowHit, rainbowSpecificPlayers)
                ? getRainbowColor()
                : config.hitColor;
        }

        return resolvedColor;
    }

    private static boolean isTeammate(Entity entity, Set<String> teamPlayers) {
        if (teamPlayers.isEmpty() || !(entity instanceof Player player)) {
            return false;
        }
        String name = player.getGameProfile().name();
        return name != null && teamPlayers.contains(name.toLowerCase(Locale.ROOT));
    }

    private static float resolveLineWidth(Entity entity, Player localPlayer, ModConfig config) {
        if (!(entity instanceof Player) || entity == localPlayer || localPlayer == null) {
            return entity instanceof Player ? config.hitboxWidth : config.nonPlayerHitboxWidth;
        }

        double distanceThreshold = config.distanceHitboxBlocks;
        if (localPlayer.distanceToSqr(entity) > (distanceThreshold * distanceThreshold)) {
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

        if (!(entity instanceof Player player)) {
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

    private static AABB getLerpedBox(Entity entity, float tickDelta) {
        AABB base = entity.getBoundingBox();
        Vec3 lerpedPos = entity.getPosition(tickDelta);
        Vec3 posDelta = lerpedPos.subtract(entity.getX(), entity.getY(), entity.getZ());
        return base.move(posDelta.x, posDelta.y, posDelta.z);
    }

    private static int withFullAlpha(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static int getRainbowColor() {
        float hue = (System.currentTimeMillis() % 2000L) / 2000f;
        return java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
    }
}