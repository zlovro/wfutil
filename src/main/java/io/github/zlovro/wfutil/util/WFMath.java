package io.github.zlovro.wfutil.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector2f;
import net.minecraft.util.math.vector.Vector3d;

public class WFMath {
    public static double horizontalDistanceSq(BlockPos pos1, BlockPos pos2) {
        double deltaX = pos2.getX() - pos1.getX();
        double deltaZ = pos2.getZ() - pos1.getZ();

        return (deltaX * deltaX) + (deltaZ * deltaZ);
    }

    public static double horizontalDistanceSq(Vector3d pos1, Vector3d pos2) {
        double deltaX = pos2.getX() - pos1.getX();
        double deltaZ = pos2.getZ() - pos1.getZ();

        return (deltaX * deltaX) + (deltaZ * deltaZ);
    }

    public static double horizontalDistance(Vector3d pos1, Vector3d pos2) {
        return Math.sqrt(horizontalDistanceSq(pos1, pos2));
    }

    public static double horizontalDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(horizontalDistanceSq(pos1, pos2));
    }

    public static float clampYaw(float yaw) {
        yaw %= 360;
        yaw = (yaw + 360) % 360;

        if (yaw > 180) {
            yaw -= 360;
        }

        return yaw;
    }

    public static float clampPitch(float pitch) {
        pitch %= 180;
        pitch = (pitch + 180) % 180;

        if (pitch > 90) {
            pitch -= 180;
        }

        return pitch;
    }

    public static Vector2f angleBetweenTwoVectors(Vector3d pos1, Vector3d pos2) {
        float yaw   = (float) Math.toDegrees(Math.atan2(pos2.z - pos1.z, pos2.x - pos1.x) - Math.PI / 2);
        float pitch = (float) -Math.toDegrees(Math.atan((pos2.y - pos1.y) / horizontalDistance(pos1, pos2)));

        return new Vector2f(clampPitch(pitch), clampYaw(yaw));
    }
}
