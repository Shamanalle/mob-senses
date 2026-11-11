package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.RayBundle;
import com.kasper.mobsenses.VoxelRay;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;

import java.util.Locale;

/**
 * Maps Bukkit {@link Block}s and {@link Material}s to {@link AcousticMaterial}s and traces
 * 5-ray occlusion bundles through a Bukkit {@link World}.
 */
public final class BukkitAcoustics {

    private BukkitAcoustics() {
    }

    public static String blockId(Block block) {
        if (block == null) {
            return "minecraft:air";
        }
        return materialId(block.getType());
    }

    public static String materialId(Material type) {
        if (type == null) {
            return "minecraft:air";
        }
        return "minecraft:" + type.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isLiquidMaterial(Material type) {
        if (type == null) {
            return false;
        }
        if (type == Material.WATER || type == Material.LAVA) {
            return true;
        }
        String n = type.name();
        return "BUBBLE_COLUMN".equals(n) || "KELP_PLANT".equals(n) || "SEAGRASS".equals(n) || "TALL_SEAGRASS".equals(n);
    }

    public static AcousticMaterial material(Block block) {
        if (block == null) {
            return AcousticMaterial.THIN;
        }
        Material type = block.getType();
        if (type == null || type.isAir()) {
            return AcousticMaterial.THIN;
        }
        if (isLiquidMaterial(type)) {
            return AcousticMaterial.LIQUID;
        }
        String name = type.name().toLowerCase(Locale.ROOT);
        if (name.contains("carpet") || name.contains("wool") || name.contains("sculk")) {
            return AcousticMaterial.WOOL;
        }
        if (!type.isSolid()) {
            return AcousticMaterial.THIN;
        }
        if (name.contains("ice")) {
            return AcousticMaterial.ICE;
        }
        if (name.contains("glass")) {
            return AcousticMaterial.GLASS;
        }
        if (name.contains("leaves")) {
            return AcousticMaterial.LEAVES;
        }
        if (name.contains("door") || name.contains("trapdoor") || name.contains("fence_gate")) {
            return AcousticMaterial.DOOR;
        }
        if (name.contains("sponge") || name.contains("moss") || name.contains("bed")
                || name.contains("hay") || name.contains("snow") || name.contains("wart_block")) {
            return AcousticMaterial.SOFT;
        }
        if (name.contains("dirt") || name.contains("grass_block") || name.contains("podzol")
                || name.contains("mycelium") || name.contains("sand") || name.contains("gravel")
                || name.contains("clay") || name.contains("mud") || name.contains("soul_")
                || name.contains("farmland")) {
            return AcousticMaterial.EARTH;
        }
        if (name.contains("wood") || name.contains("planks") || name.contains("log")
                || name.contains("stem") || name.contains("hyphae") || name.contains("bamboo")
                || name.contains("barrel") || name.contains("chest") || name.contains("bookshelf")
                || name.contains("fence")) {
            if (name.contains("iron") || name.contains("copper")) {
                return AcousticMaterial.METAL;
            }
            return AcousticMaterial.WOOD;
        }
        if (name.contains("iron") || name.contains("copper") || name.contains("gold")
                || name.contains("netherite") || name.contains("anvil") || name.contains("chain")
                || name.contains("lantern") || name.contains("bell") || name.contains("rail")
                || name.contains("hopper") || name.contains("cauldron")) {
            return AcousticMaterial.METAL;
        }
        return AcousticMaterial.STONE;
    }

    public static double wallWeightAt(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();
        if (type == null || type.isAir()) {
            return 0.0;
        }
        if (isLiquidMaterial(type)) {
            return AcousticMaterial.LIQUID.wallWeight();
        }
        if (!type.isSolid()) {
            return 0.0;
        }
        try {
            BlockData data = block.getBlockData();
            if (data instanceof Openable && ((Openable) data).isOpen()) {
                return AcousticMaterial.DOOR.wallWeight() * AcousticMaterial.OPEN_PANEL_SHARE;
            }
        } catch (Throwable ignored) {
        }
        String name = type.name().toLowerCase(Locale.ROOT);
        AcousticMaterial mat = material(block);
        double w = mat.wallWeight();
        if (name.contains("fence") || name.contains("bars") || name.contains("pane")
                || name.contains("wall") || name.contains("grate") || name.contains("slab")
                || name.contains("stairs") || name.contains("trapdoor") || name.contains("door")) {
            w *= 0.5;
        }
        return w;
    }

    public static boolean isOpenForDetour(World world, int x, int y, int z) {
        return wallWeightAt(world, x, y, z) < 0.15;
    }

    public static double traceSingleRay(
            World world, double ax, double ay, double az, double bx, double by, double bz) {
        int startX = (int) Math.floor(ax);
        int startY = (int) Math.floor(ay);
        int startZ = (int) Math.floor(az);
        int endX = (int) Math.floor(bx);
        int endY = (int) Math.floor(by);
        int endZ = (int) Math.floor(bz);
        if (startX == endX && startY == endY && startZ == endZ) {
            return 0.0;
        }
        double[] total = {0.0};
        VoxelRay.walk(ax, ay, az, bx, by, bz, 96, (x, y, z) -> {
            if ((x == startX && y == startY && z == startZ)
                    || (x == endX && y == endY && z == endZ)) {
                return false;
            }
            double w = wallWeightAt(world, x, y, z);
            if (w <= 0.0) {
                return false;
            }
            double chord = VoxelRay.chord(ax, ay, az, bx, by, bz, x, y, z, x + 1.0, y + 1.0, z + 1.0);
            if (chord > 0.0) {
                total[0] += w * RayBundle.chordWeight(chord);
            }
            return total[0] >= RayBundle.MAX_RAY_THICKNESS;
        });
        return Math.min(total[0], RayBundle.MAX_RAY_THICKNESS);
    }

    public static double traceBundle(
            World world, double sx, double sy, double sz, double tx, double ty, double tz) {
        return RayBundle.trace(
                (ax, ay, az, bx, by, bz) -> traceSingleRay(world, ax, ay, az, bx, by, bz),
                (x, y, z) -> isOpenForDetour(world, x, y, z),
                sx, sy, sz, tx, ty, tz);
    }
}
