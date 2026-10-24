package itzglecni.customhitboxes.mixin;

import itzglecni.customhitboxes.tracker.HitTracker;
import itzglecni.customhitboxes.tracker.HoverPicker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (target != null) {
            HitTracker.recordAttackAttempt(target.getId());
        }
    }

    // Spear stabs don't go through attackEntity(); the server hits every entity along the spear's range,
    // which HoverPicker computes with the same vanilla call.
    @Inject(method = "attackWithPiercingWeapon", at = @At("HEAD"))
    private void onPiercingAttack(CallbackInfo ci) {
        for (Entity target : HoverPicker.findAttackTargets(MinecraftClient.getInstance())) {
            HitTracker.recordAttackAttempt(target.getId());
        }
    }
}
