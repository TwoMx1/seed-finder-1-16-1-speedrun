package org.example;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mcbiome.layer.end.EndBiomeLayer;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mcbiome.source.EndBiomeSource;
import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.block.Block;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.rand.seed.PillarSeed;
import com.seedfinding.mccore.rand.seed.WorldSeed;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.math.DistanceMetric;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcfeature.misc.SpawnPoint;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.DesertPyramid;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mcfeature.structure.Village;
import com.seedfinding.mcfeature.structure.generator.structure.DesertPyramidGenerator;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;
import com.seedfinding.mcfeature.structure.generator.structure.VillageGenerator;
import com.seedfinding.mcterrain.TerrainGenerator;
import com.seedfinding.mcterrain.terrain.EndTerrainGenerator;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.seedfinding.mccore.rand.seed.PillarSeed.getPillarHeights;

public class DesertTemple {

    static final long TOTAL_SEEDS = 100_000_000L;
    static final int THREADS = Math.max(Runtime.getRuntime().availableProcessors() - 2, 1);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("THREAD_COUNT: " + THREADS);
        AtomicLong progress = new AtomicLong(0);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        long chunkSize = TOTAL_SEEDS / THREADS;

        for (int t = 0; t < THREADS; t++) {
            long start = t * chunkSize;
            long end = (t == THREADS - 1) ? TOTAL_SEEDS : start + chunkSize;

            executor.submit(() -> searchRange(start, end, progress));
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        System.out.println();
        System.out.println("SCANNED ALL SEEDS.");
    }

    static int locateDistance(int x1, int z1, int x2, int z2) {
        int dx = x2 - x1;
        int dz = z2 - z1;
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    static boolean isEnoughLoot(int notchCount, int appleCount, int fleshCount, int ironCount, int diamondCount, int reqScore, boolean extraIron) {
        if (reqScore == -1) return true;

        int score = 0;

        if (extraIron) ironCount = ironCount - 4;
        if (ironCount < 7 && (diamondCount < 3 || ironCount < 4)) return false; // not enough iron

        score += fleshCount * 1;
        score += appleCount * 8;
        score += notchCount * 20;

        return (score >= reqScore);
    }

    static void searchRange(long start, long end, AtomicLong progress) {
        MCVersion version = MCVersion.v1_16_1;
        Dimension ow = Dimension.OVERWORLD;
        Dimension nether = Dimension.NETHER;
        ChunkRand rand = new ChunkRand();

        DesertPyramid pyramid = new DesertPyramid(version);
        DesertPyramidGenerator dpg = new DesertPyramidGenerator(version);

        BastionRemnant bastion = new BastionRemnant(version);
        Fortress fortress = new Fortress(version);

        CPos zeroZero = new CPos(0, 0);

        for (long structureSeed = start; structureSeed < end; structureSeed++) {
            long done = progress.incrementAndGet();
            if (done % 1_000_000L == 0)
                System.out.printf("===== Progress: %.1f%% =====%n", (done * 100.0) / TOTAL_SEEDS);

            // =========================
            // TEMPLE STRUCTURE CHECK
            // =========================
            CPos pyramidPos = pyramid.getInRegion(structureSeed, 0, 0, rand);
            if (pyramidPos == null) continue;

            // distance to spawn filter
            if (pyramidPos.distanceTo(CPos.ZERO, DistanceMetric.CHEBYSHEV) > 6) continue;

            var terrainGen = TerrainGenerator.of(BiomeSource.of(ow, version, structureSeed));
            dpg.generate(terrainGen, pyramidPos); // make dpg usable

            var chestLoot = pyramid.getLoot(structureSeed, dpg, rand, false);

            int reqScore = 30; // set to wanted score of items: notch = 20, apple = 8, flesh = 1
            int notchCount = 0, appleCount = 0, fleshCount = 0, ironCount = 0, diamondCount = 0;

            // chest check
            if (reqScore > 0) {
                if (chestLoot.isEmpty()) continue;
                for (var chest : chestLoot) {
                    notchCount += chest.getCountExact(Items.ENCHANTED_GOLDEN_APPLE);
                    appleCount += chest.getCountExact(Items.GOLDEN_APPLE);
                    ironCount += chest.getCountExact(Items.IRON_INGOT);
                    fleshCount += chest.getCountExact(Items.ROTTEN_FLESH);
                    diamondCount += chest.getCountExact(Items.DIAMOND);
                }
                // check items
                if (!isEnoughLoot(notchCount, appleCount, fleshCount, ironCount, diamondCount, reqScore, true))
                    continue;
            }

            // =========================
            // BASTION + FORTRESS
            // =========================
            CPos foundBastion = null;
            CPos foundFort = null;
            outer:
            for (int rx = -1; rx <= 1; rx++) {
                for (int rz = -1; rz <= 1; rz++) {
                    CPos b = bastion.getInRegion(structureSeed, rx, rz, new ChunkRand());
                    if (b == null) continue;
                    if (b.distanceTo(zeroZero, DistanceMetric.CHEBYSHEV) > 8) continue; // 8 chunk bastion

                    for (int frx = rx - 1; frx <= rx + 1; frx++) {
                        for (int frz = rz - 1; frz <= rz + 1; frz++) {
                            CPos f = fortress.getInRegion(structureSeed, frx, frz, new ChunkRand());
                            if (f == null) continue;

                            if (f.distanceTo(b, DistanceMetric.CHEBYSHEV) <= 12) { // 12 chunk fort
                                foundBastion = b;
                                foundFort = f;
                                break outer;
                            }
                        }
                    }
                }
            }

            if (foundBastion == null || foundFort == null) continue;

            final CPos finalBastion = foundBastion;
            final CPos finalFort = foundFort;
            final long finalStructureSeed = structureSeed;

            // pillars for 0 cycle
            long pillarSeed = PillarSeed.fromStructureSeed(structureSeed);
            int[] pillarHeights = getPillarHeights(pillarSeed);
            String backOrFront;
            int zeroHeight;
            if (pillarHeights[0] < pillarHeights[5]) {
                backOrFront = "Back";
                zeroHeight = pillarHeights[4];
            } else {
                zeroHeight = pillarHeights[9];
                backOrFront = "Front";
            }

            // =========================
            // WORLD SEED CHECKS
            // =========================
            WorldSeed.getSisterSeeds(structureSeed).asStream().boxed().limit(1000).forEach(worldSeed -> {
                // OVERWORLD
                BiomeSource overworldSource = BiomeSource.of(ow, version, worldSeed);
                if (!pyramid.canSpawn(pyramidPos, overworldSource)) {
                    return;
                }

                // check for forest biome within 3 chunks (for trees)
                boolean hasTreeBiome = false;
                OverworldBiomeSource owSource = (OverworldBiomeSource) overworldSource;
                outer2:
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        CPos checkPos = new CPos(pyramidPos.getX() + dx, pyramidPos.getZ() + dz);
                        Biome b = owSource.getBiome(checkPos.toBlockPos().getX(), 0, checkPos.toBlockPos().getZ());
                        if (
                                b == Biomes.FOREST
                                        || b == Biomes.BIRCH_FOREST
                                        || b == Biomes.FLOWER_FOREST
                                        || b == Biomes.DARK_FOREST
                                        || b == Biomes.MUTATED_FOREST
                                        || b == Biomes.SWAMP
                                        || b == Biomes.FOREST_HILLS
                                        || b == Biomes.MUTATED_BIRCH_FOREST
                                        || b == Biomes.MUTATED_ROOFED_FOREST
                                        || b == Biomes.ROOFED_FOREST
                                        || b == Biomes.TALL_BIRCH_FOREST
                                        || b == Biomes.BIRCH_FOREST_HILLS
                                        || b == Biomes.BIRCH_FOREST_HILLS_M
                                        || b == Biomes.BIRCH_FOREST_M
                                        || b == Biomes.DARK_FOREST_HILLS
                        ) {
                            hasTreeBiome = true;
                            break outer2;
                        }
                    }
                }
                if (!hasTreeBiome) return;

                // check for river biome within 4 chunks (for gravel)
                boolean hasRiverBiome = false;
                outer2:
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        CPos checkPos = new CPos(pyramidPos.getX() + dx, pyramidPos.getZ() + dz);
                        Biome b = owSource.getBiome(checkPos.toBlockPos().getX(), 0, checkPos.toBlockPos().getZ());
                        if (
                                b == Biomes.RIVER
                                        || b == Biomes.FROZEN_RIVER
                                        || b == Biomes.SWAMP
                        ) {
                            hasRiverBiome = true;
                            break outer2;
                        }
                    }
                }
                if (!hasRiverBiome) return;

                // NETHER
                BiomeSource netherSource = BiomeSource.of(nether, version, worldSeed);
                if (!bastion.canSpawn(finalBastion, netherSource)) return;
                if (!fortress.canSpawn(finalFort, netherSource)) return;

                Biome bastionBiome = netherSource.getBiome(finalBastion.getX() << 4, 0, finalBastion.getZ() << 4);
                if (bastionBiome == Biomes.BASALT_DELTAS) return; // skip basalt bastion

                // pyramid distance from SPAWN
                CPos spawnPos = SpawnPoint.getApproximateSpawn((OverworldBiomeSource) overworldSource).toChunkPos();
                if (spawnPos.distanceTo(pyramidPos, DistanceMetric.CHEBYSHEV) > 3) return;

                TerrainGenerator terrainGenerator = TerrainGenerator.of(overworldSource);

                // LAVA CHECK (last — most expensive)
                /*
                boolean hasLava = false;
                int pyramidBlockX = pyramidPos.getX() << 4;
                int pyramidBlockZ = pyramidPos.getZ() << 4;
                lavaSearch:
                for (int dx = -80; dx <= 80; dx += 2) {
                    for (int dz = -80; dz <= 80; dz += 2) {
                        int bx = pyramidBlockX + dx;
                        int bz = pyramidBlockZ + dz;
                        int surfaceY = terrainGenerator.getFirstHeightInColumn(bx, bz, TerrainGenerator.WORLD_SURFACE_WG);
                        for (int dy = -3; dy <= 1; dy++) {
                            var block = terrainGenerator.getBlockAt(bx, surfaceY + dy, bz);
                            if (block.isPresent() && block.get().getId() == Blocks.LAVA.getId()) {
                                hasLava = true;
                                break lavaSearch;
                            }
                        }
                    }
                }
                if (!hasLava) return;
                 */

                // REAL TERRAIN GENERATION
                DesertPyramidGenerator desertPyramidGenerator = new DesertPyramidGenerator(version);
                if (!desertPyramidGenerator.generate(terrainGenerator, pyramidPos)) return;

                if (chestLoot.isEmpty()) {
                    return;
                }

                // END spawn
                BiomeSource endSource = BiomeSource.of(Dimension.END, version, worldSeed);
                TerrainGenerator endGen = TerrainGenerator.of(endSource);

                boolean caged = false;
                int height = -1;

                for (int i = -2; i <= 2; i++) {
                    height = endGen.getHeightInGround(102, i);
                    if (height >= 52) {
                        caged = true;
                        break;
                    }
                }

                String endSpawnStatus = "Open";
                if (caged) endSpawnStatus = "Caged [" + height + "]";

                System.out.printf(
                        "%d (%d)%n" +
                                "Desert Temple: [%4d, %4d] (%d)%n" +
                                "Bastion:       [%4d, %4d] (%d)%n" +
                                "Fort:          [%4d, %4d] (%d)%n" +
                                "%s, %s %d%n%n",

                        worldSeed,
                        finalStructureSeed,

                        (pyramidPos.getX() * 16) + 10,
                        (pyramidPos.getZ() * 16) + 10,
                        locateDistance(spawnPos.getX(), spawnPos.getZ(), (pyramidPos.getX() * 16) + 10, (pyramidPos.getZ() * 16) + 10),

                        finalBastion.getX() * 16,
                        finalBastion.getZ() * 16,
                        locateDistance(0, 0, finalBastion.getX() * 16, finalBastion.getZ() * 16),

                        finalFort.getX() * 16,
                        finalFort.getZ() * 16,
                        locateDistance(finalBastion.getX() * 16, finalBastion.getZ() * 16, finalFort.getX() * 16, finalFort.getZ() * 16),

                        endSpawnStatus,
                        backOrFront,
                        zeroHeight
                );
            });
        }
    }
}