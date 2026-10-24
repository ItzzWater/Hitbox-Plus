package itzglecni.customhitboxes.mixin;

import itzglecni.customhitboxes.tracker.HitTracker;
import itzglecni.customhitboxes.tracker.HoverPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttackEntity(Player player, Entity target, CallbackInfo ci) {
        if (target != null) {
            HitTracker.recordAttackAttempt(target.getId());
        }
    }

    // Spear stabs don't go through attack(); the server hits every entity along the spear's range,
    // which HoverPicker computes with the same vanilla call.
    @Inject(method = "piercingAttack", at = @At("HEAD"))
    private void onPiercingAttack(CallbackInfo ci) {
        for (Entity target : HoverPicker.findAttackTargets(Minecraft.getInstance())) {
            HitTracker.recordAttackAttempt(target.getId());
        }
    }
}
