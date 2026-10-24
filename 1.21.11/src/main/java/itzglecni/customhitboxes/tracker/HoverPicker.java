package itzglecni.customhitboxes.tracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttackRangeComponent;
import net.minecraft.component.type.PiercingWeaponComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Asks Minecraft which entities a left click right now would attack, using the same calls as
// MinecraftClient.doAttack, so hover always agrees with the game (reach, spears, weapon attack ranges).
public final class HoverPicker {
    private HoverPicker() {
    }

    public static Set<Entity> findAttackTargets(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        Entity camera = client.getCameraEntity();
        if (player == null || camera == null || client.world == null || player.isSpectator()) {
            return Set.of();
        }

        ItemStack stack = player.getMainHandStack();

        // Spears: doAttack calls attackWithPiercingWeapon, and the stab hits every entity
        // PiercingWeaponComponent.stab finds along the attack range (not the crosshair target).
        if (stack.get(DataComponentTypes.PIERCING_WEAPON) != null) {
            AttackRangeComponent range = player.getAttackRange();
            Collection<EntityHitResult> hits = ProjectileUtil.collectPiercingCollisions(
                    player, range, target -> PiercingWeaponComponent.canHit(player, target), RaycastContext.ShapeType.COLLIDER)
                .map(block -> List.<EntityHitResult>of(), entities -> entities);
            Set<Entity> targets = new HashSet<>();
            for (EntityHitResult hit : hits) {
                targets.add(hit.getEntity());
            }
            return targets;
        }

        // Everything else: doAttack attacks client.crosshairTarget, which MinecraftClient.tick re-picks
        // with tickDelta 1.0 right before handling the click.
        HitResult hit = player.getCrosshairTarget(1.0f, camera);
        if (!(hit instanceof EntityHitResult entityHit)) {
            return Set.of();
        }
        AttackRangeComponent attackRange = stack.get(DataComponentTypes.ATTACK_RANGE);
        if (attackRange != null && !attackRange.isWithinRange(player, hit.getPos())) {
            return Set.of();
        }
        return Set.of(entityHit.getEntity());
    }
}
