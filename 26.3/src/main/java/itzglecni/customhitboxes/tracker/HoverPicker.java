package itzglecni.customhitboxes.tracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Asks Minecraft which entities a left click right now would attack, using the same calls as
// Minecraft.startAttack, so hover always agrees with the game (reach, spears, weapon attack ranges).
public final class HoverPicker {
    private HoverPicker() {
    }

    public static Set<Entity> findAttackTargets(Minecraft client) {
        LocalPlayer player = client.player;
        Entity camera = client.getCameraEntity();
        if (player == null || camera == null || client.level == null || player.isSpectator()) {
            return Set.of();
        }

        ItemStack stack = player.getMainHandItem();

        // Spears: startAttack calls piercingAttack, and the stab hits every entity
        // PiercingWeapon.attack finds along the spear's attack range (not the crosshair target).
        if (stack.get(DataComponents.PIERCING_WEAPON) != null) {
            AttackRange range = player.getAttackRangeWith(stack);
            Collection<EntityHitResult> hits = ProjectileUtil.getHitEntitiesAlong(
                    player, range, target -> PiercingWeapon.canHitEntity(player, target), ClipContext.Block.COLLIDER)
                .map(block -> List.<EntityHitResult>of(), entities -> entities);
            Set<Entity> targets = new HashSet<>();
            for (EntityHitResult hit : hits) {
                targets.add(hit.getEntity());
            }
            return targets;
        }

        // Everything else: startAttack attacks client.hitResult, which Minecraft.tick re-picks with
        // partialTick 1.0 right before handling the click.
        HitResult hit = player.raycastHitResult(1.0f, camera);
        if (!(hit instanceof EntityHitResult entityHit)) {
            return Set.of();
        }
        AttackRange attackRange = stack.get(DataComponents.ATTACK_RANGE);
        if (attackRange != null && !attackRange.isInRange(player, hit.getLocation())) {
            return Set.of();
        }
        return Set.of(entityHit.getEntity());
    }
}
