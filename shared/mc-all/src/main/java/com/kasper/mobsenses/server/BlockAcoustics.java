package com.kasper.mobsenses.server;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.RayBundle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * Maps Minecraft {@link BlockState}s to {@link AcousticMaterial}s and traces 5-ray acoustic
 * occlusion bundles between a noise source and a listening mob's ears.
 */
public final class BlockAcoustics {

    private BlockAcoustics() {
    }

    public static String blockId(BlockState state) {
        if (state == null) {
            return "";
        }
        try {
            return String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        } catch (Throwable ignored) {
            return state.getBlock().toString().toLowerCase(Locale.ROOT);
        }
    }

    public static AcousticMaterial material(BlockState state) {
        return material(state, blockId(state));
    }

    public static AcousticMaterial material(BlockState state, String id) {
        if (state == null || state.isAir()) {
            return AcousticMaterial.THIN;
        }
        if (!state.getFluidState().isEmpty() && state.getBlock().defaultBlockState().getFluidState().isSource()) {
            return AcousticMaterial.LIQUID;
        }
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.FENCE_GATES)) {
            return AcousticMaterial.DOOR;
        }
        if (state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LEAVES)) {
            return AcousticMaterial.LEAVES;
        }
        String lower = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (state.is(BlockTags.WOOL)
                || lower.contains("wool")
                || lower.contains("carpet")
                || lower.contains("sponge")
                || lower.contains("sculk")) {
            return AcousticMaterial.WOOL;
        }
        if (state.is(BlockTags.ICE) || lower.contains("ice")) {
            return AcousticMaterial.ICE;
        }
        if (lower.contains("glass") || lower.contains("tinted_glass")) {
            return AcousticMaterial.GLASS;
        }
        if (state.is(BlockTags.BEDS)
                || state.is(BlockTags.BANNERS)
                || lower.contains("snow")
                || lower.contains("moss")
                || lower.contains("hay_block")
                || lower.contains("honeycomb")) {
            return AcousticMaterial.SOFT;
        }
        if (state.is(BlockTags.PLANKS)
                || state.is(BlockTags.LOGS)
                || lower.contains("planks")
                || lower.contains("log")
                || lower.contains("wood")
                || lower.contains("stem")
                || lower.contains("hyphae")
                || lower.contains("bamboo")
                || lower.contains("barrel")
                || lower.contains("chest")
                || lower.contains("bookshelf")) {
            return AcousticMaterial.WOOD;
        }
        if (state.is(BlockTags.ANVIL)
                || state.is(BlockTags.RAILS)
                || lower.contains("iron")
                || lower.contains("copper")
                || lower.contains("netherite")
                || lower.contains("gold_block")
                || lower.contains("chain")
                || lower.contains("anvil")
                || lower.contains("cauldron")
                || lower.contains("hopper")) {
            return AcousticMaterial.METAL;
        }
        if (state.is(BlockTags.SAND)
                || lower.contains("dirt")
                || lower.contains("grass_block")
                || lower.contains("podzol")
                || lower.contains("mycelium")
                || lower.contains("sand")
                || lower.contains("gravel")
                || lower.contains("clay")
                || lower.contains("mud")
                || lower.contains("soul_sand")
                || lower.contains("soul_soil")
                || lower.contains("farmland")) {
            return AcousticMaterial.EARTH;
        }

        SoundType sound = state.getSoundType();
        if (sound == SoundType.WOOL) {
            return AcousticMaterial.WOOL;
        }
        if (sound == SoundType.SNOW
                || sound == SoundType.SLIME_BLOCK
                || sound == SoundType.HONEY_BLOCK
                || sound == SoundType.WET_GRASS
                || sound == SoundType.LILY_PAD) {
            return AcousticMaterial.SOFT;
        }
        if (sound == SoundType.WOOD
                || sound == SoundType.NETHER_WOOD
                || sound == SoundType.STEM) {
            return AcousticMaterial.WOOD;
        }
        if (sound == SoundType.GLASS) {
            return AcousticMaterial.GLASS;
        }
        if (sound == SoundType.METAL
                || sound == SoundType.CHAIN
                || sound == SoundType.ANVIL
                || sound == SoundType.LANTERN
                || sound == SoundType.NETHERITE_BLOCK
                || sound == SoundType.LODESTONE) {
            return AcousticMaterial.METAL;
        }
        if (sound == SoundType.SAND
                || sound == SoundType.GRAVEL
                || sound == SoundType.SOUL_SAND
                || sound == SoundType.SOUL_SOIL
                || sound == SoundType.GRASS) {
            return AcousticMaterial.EARTH;
        }
        if (sound == SoundType.CROP
                || sound == SoundType.VINE
                || sound == SoundType.WEEPING_VINES
                || sound == SoundType.TWISTING_VINES) {
            return AcousticMaterial.THIN;
        }
        return AcousticMaterial.STONE;
    }

    public static boolean isOpenForSound(Level level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        if (state.getBlock() instanceof DoorBlock && Boolean.TRUE.equals(state.getValue(DoorBlock.OPEN))) {
            return true;
        }
        if (state.getBlock() instanceof TrapDoorBlock && Boolean.TRUE.equals(state.getValue(TrapDoorBlock.OPEN))) {
            return true;
        }
        if (state.getBlock() instanceof FenceGateBlock && Boolean.TRUE.equals(state.getValue(FenceGateBlock.OPEN))) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Traces the 5-ray acoustic bundle from the noise source {@code (sx, sy, sz)} to the
     * listener's ear {@code (ex, ey, ez)}, returning effective wall weight.
     */
    public static double traceBundle(Level level, double sx, double sy, double sz, double ex, double ey, double ez) {
        return RayBundle.trace(
                (ax, ay, az, bx, by, bz) -> traceSingleRay(level, new Vec3(ax, ay, az), new Vec3(bx, by, bz)),
                (x, y, z) -> isOpenForSound(level, x, y, z),
                sx, sy, sz, ex, ey, ez);
    }

    public static double traceSingleRay(Level level, Vec3 start, Vec3 end) {
        BlockPos startBlock = BlockPos.containing(start.x, start.y, start.z);
        BlockPos endBlock = BlockPos.containing(end.x, end.y, end.z);
        if (startBlock.equals(endBlock)) {
            return 0.0;
        }
        double[] total = {0.0};
        BlockGetter.traverseBlocks(start, end, new ClipBlockStateContext(start, end, s -> false), (ctx, pos) -> {
            if (pos.equals(startBlock) || pos.equals(endBlock)) {
                return null;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            AcousticMaterial mat = material(state);
            double weight = mat.wallWeight();
            if (weight <= 0.0) {
                return null;
            }
            if (mat == AcousticMaterial.DOOR) {
                boolean open = (state.getBlock() instanceof DoorBlock && Boolean.TRUE.equals(state.getValue(DoorBlock.OPEN)))
                        || (state.getBlock() instanceof TrapDoorBlock && Boolean.TRUE.equals(state.getValue(TrapDoorBlock.OPEN)))
                        || (state.getBlock() instanceof FenceGateBlock && Boolean.TRUE.equals(state.getValue(FenceGateBlock.OPEN)));
                VoxelShape shape = state.getCollisionShape(level, pos);
                boolean hitShape = !shape.isEmpty() && shape.clip(start, end, pos) != null;
                if (open && !hitShape) {
                    return null;
                }
                total[0] += weight * AcousticMaterial.panelShare(open);
            } else {
                VoxelShape shape = state.getCollisionShape(level, pos);
                if (shape.isEmpty()) {
                    return null;
                }
                double chord = chordInShape(shape, pos, start, end);
                if (chord <= 0.0) {
                    return null;
                }
                total[0] += weight * RayBundle.chordWeight(chord);
            }
            if (total[0] >= RayBundle.MAX_RAY_THICKNESS) {
                return Boolean.TRUE;
            }
            return null;
        }, ctx -> null);
        return Math.min(total[0], RayBundle.MAX_RAY_THICKNESS);
    }

    private static double chordInShape(VoxelShape shape, BlockPos pos, Vec3 start, Vec3 end) {
        BlockHitResult entry = shape.clip(start, end, pos);
        if (entry == null) {
            return 0.0;
        }
        BlockHitResult exit = shape.clip(end, start, pos);
        if (exit == null) {
            return 0.0;
        }
        return entry.getLocation().distanceTo(exit.getLocation());
    }

    public static BlockPos blockUnderEvent(Level level, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        if (!at.isAir()) {
            return pos;
        }
        BlockPos below = pos.relative(Direction.DOWN);
        return level.getBlockState(below).isAir() ? pos : below;
    }
}
