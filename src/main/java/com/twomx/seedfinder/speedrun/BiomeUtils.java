package com.twomx.seedfinder.speedrun;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.util.pos.CPos;

import java.util.Set;

public class BiomeUtils {

    public static boolean hasTreeBiomeNear(OverworldBiomeSource source, CPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                CPos checkPos = new CPos(center.getX() + dx, center.getZ() + dz);

                Biome b = source.getBiome(checkPos.toBlockPos().getX(), 0, checkPos.toBlockPos().getZ());

                if (isTreeBiome(b)) return true;
            }
        }

        return false;
    }

    private static boolean isTreeBiome(Biome b) {
        return b == Biomes.FOREST
                || b == Biomes.BIRCH_FOREST
                || b == Biomes.FLOWER_FOREST
                || b == Biomes.DARK_FOREST
                || b == Biomes.MUTATED_FOREST
                //|| b == Biomes.SWAMP
                || b == Biomes.FOREST_HILLS
                || b == Biomes.MUTATED_BIRCH_FOREST
                || b == Biomes.MUTATED_ROOFED_FOREST
                || b == Biomes.ROOFED_FOREST
                || b == Biomes.TALL_BIRCH_FOREST
                || b == Biomes.BIRCH_FOREST_HILLS
                || b == Biomes.BIRCH_FOREST_HILLS_M
                || b == Biomes.BIRCH_FOREST_M
                || b == Biomes.DARK_FOREST_HILLS;
    }

    public static boolean hasAnyBiomeNear(
            OverworldBiomeSource source,
            CPos center,
            int radius,
            Set<Biome> targetBiomes
    ) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                CPos checkPos = new CPos(center.getX() + dx, center.getZ() + dz);

                Biome b = source.getBiome(
                        checkPos.toBlockPos().getX(),
                        0,
                        checkPos.toBlockPos().getZ()
                );

                if (targetBiomes.contains(b)) {
                    return true;
                }
            }
        }

        return false;
    }
}