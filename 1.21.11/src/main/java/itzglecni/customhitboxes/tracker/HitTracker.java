package itzglecni.customhitboxes.tracker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

public class HitTracker {
    private static final long ATTACK_CONFIRMATION_WINDOW_MS = 900;
    private static final long HIT_HIGHLIGHT_DURATION_MS = 1000;
    private static final long STALE_ENTRY_MS = 5000;

    // Keyed by entity id: a spear stab can hit several entities at once.
    private static final Map<Integer, Long> attackAttempts = new HashMap<>();
    private static final Map<Integer, Long> confirmedHits = new HashMap<>();

    public static void recordAttackAttempt(int entityId) {
        long now = System.currentTimeMillis();
        attackAttempts.values().removeIf(time -> now - time > STALE_ENTRY_MS);
        confirmedHits.values().removeIf(time -> now - time > STALE_ENTRY_MS);
        attackAttempts.put(entityId, now);
    }

    public static void confirmDamageState(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }

        Long attemptTime = attackAttempts.get(entity.getId());
        if (attemptTime == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - attemptTime) > ATTACK_CONFIRMATION_WINDOW_MS) {
            return;
        }

        if (living.hurtTime > 0) {
            confirmedHits.put(entity.getId(), now);
        }
    }

    public static boolean wasRecentlyHit(int entityId) {
        Long hitTime = confirmedHits.get(entityId);
        return hitTime != null && (System.currentTimeMillis() - hitTime) <= HIT_HIGHLIGHT_DURATION_MS;
    }
}
