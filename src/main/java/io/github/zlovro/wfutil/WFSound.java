package io.github.zlovro.wfutil;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;

public class WFSound {
    public static final float EPSILON = 0.05F;

    public static class SoundInfo {
        public float volume, pitch;
        public Vector3d position;

        public SoundInfo(float volume, float pitch, Vector3d position) {
            this.volume   = volume;
            this.pitch    = pitch;
            this.position = position;
        }
    }

    @Nullable
    public static SoundInfo getSoundInfo(Vector3d origin, Vector3d destination, float range, float pitch) {
        float distance = (float) origin.distanceTo(destination);
        // if (distance < 16) {
        //     return null;
        // }

        float volume = 1 - (EPSILON + (distance / range) * (1 - EPSILON));

        return new SoundInfo(volume, pitch, destination.add(new Vector3d(origin.x, 0, origin.z).subtract(new Vector3d(destination.x, 0, destination.z)).normalize()));
    }

    public static void playSound(ServerWorld world, SoundEvent sound, Vector3d pos, float range, float pitch) {
        playSound(world, sound, SoundCategory.MASTER, pos, range, pitch);
    }

    /**
     * @param range distance at which the sound will be heard at {@value EPSILON} * max volume
     */
    public static void playSound(ServerWorld world, SoundEvent sound, SoundCategory category, Vector3d pos, float range, float pitch) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            // if (sound == SoundEvents.ENTITY_GENERIC_EXPLODE) {
            //     continue;
            // }

            SoundInfo info = getSoundInfo(pos, player.getPositionVec(), range, pitch);
            if (info == null) {
                continue;
            }

            world.playSound(null, info.position.x, info.position.y, info.position.z, sound, category, info.volume, pitch);
        }
    }
}
