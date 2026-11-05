package com.kasper.mobsenses;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VoxelRayTest {

    @Test
    void walkVisitsStraightLineBlocks() {
        List<int[]> visited = new ArrayList<>();
        VoxelRay.walk(0.5, 64.5, 0.5, 4.5, 64.5, 0.5, 32, (bx, by, bz) -> {
            visited.add(new int[]{bx, by, bz});
            return false;
        });
        assertEquals(5, visited.size());
        for (int i = 0; i < 5; i++) {
            assertArrayEquals(new int[]{i, 64, 0}, visited.get(i));
        }
    }

    @Test
    void chordMeasuresSegmentThroughUnitCube() {
        double c = VoxelRay.chord(0.5, 0.5, -1.0, 0.5, 0.5, 2.0, 0, 0, 0, 1, 1, 1);
        assertEquals(1.0, c, 1e-6);

        double miss = VoxelRay.chord(2.5, 0.5, -1.0, 2.5, 0.5, 2.0, 0, 0, 0, 1, 1, 1);
        assertEquals(0.0, miss, 1e-6);
    }

    @Test
    void rayBundleDistinguishesOpenAirFromSolidWallAndOpenDoor() {
        Set<Long> wallBlocks = new HashSet<>();
        for (int y = 63; y <= 66; y++) {
            for (int z = -2; z <= 2; z++) {
                wallBlocks.add(pack(2, y, z));
            }
        }

        RayBundle.RayCaster openTracer = (sx, sy, sz, ex, ey, ez) -> 0.0;
        RayBundle.OpenGrid allOpen = (x, y, z) -> true;
        assertEquals(0.0, RayBundle.trace(openTracer, allOpen, 0.5, 64.5, 0.5, 4.5, 64.5, 0.5), 1e-9);

        RayBundle.OpenGrid wallGrid = (x, y, z) -> !wallBlocks.contains(pack(x, y, z));
        RayBundle.RayCaster wallTracer = (sx, sy, sz, ex, ey, ez) -> {
            double[] sum = {0.0};
            VoxelRay.walk(sx, sy, sz, ex, ey, ez, 32, (bx, by, bz) -> {
                if (wallBlocks.contains(pack(bx, by, bz))) {
                    double ch = VoxelRay.chord(sx, sy, sz, ex, ey, ez, bx, by, bz, bx + 1, by + 1, bz + 1);
                    sum[0] += RayBundle.chordWeight(ch) * AcousticMaterial.STONE.wallWeight();
                }
                return false;
            });
            return sum[0];
        };

        double solidThickness = RayBundle.trace(wallTracer, wallGrid, 0.5, 64.5, 0.5, 4.5, 64.5, 0.5);
        assertEquals(AcousticMaterial.STONE.wallWeight(), solidThickness, 1e-6);

        // Open doorway at (2, 64, 0): center ray passes through air while side rays see adjacent frame
        wallBlocks.remove(pack(2, 64, 0));
        double doorwayThickness = RayBundle.trace(wallTracer, wallGrid, 0.5, 64.5, 0.5, 4.5, 64.5, 0.5);
        assertTrue(doorwayThickness < solidThickness * 0.85, "An open doorway lets more sound through: " + doorwayThickness);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}
