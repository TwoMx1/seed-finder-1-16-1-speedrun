package com.twomx.seedfinder.speedrun.seedtype;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mcbiome.source.OverworldBiomeSource;
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
import com.seedfinding.mcfeature.structure.generator.structure.DesertPyramidGenerator;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;
import com.seedfinding.mcterrain.TerrainGenerator;
import com.twomx.seedfinder.speedrun.BiomeUtils;
import com.twomx.seedfinder.speedrun.EnterCheck;
import com.twomx.seedfinder.speedrun.FastionPair;
import com.twomx.seedfinder.speedrun.StructureFinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;

import static com.seedfinding.mccore.rand.seed.ChunkSeeds.getDecoratorSeed;
import static com.seedfinding.mccore.rand.seed.ChunkSeeds.getPopulationSeed;
import static com.seedfinding.mccore.rand.seed.PillarSeed.getPillarHeights;
import static com.twomx.seedfinder.speedrun.EnterCheck.*;

public class DesertTemple {
    static final long TOTAL_SEEDS = 100_000_000L;
    static final int THREADS = Math.max(Runtime.getRuntime().availableProcessors() - 0, 1);
    static CPos zeroZero = new CPos(0, 0);

    public static void main(String[] args) throws InterruptedException {
        // 33333742
        long singleStructureSeedToTest = -1; // -1 for no test
        if (singleStructureSeedToTest != -1) {
            testSeed(singleStructureSeedToTest);
            return;
        }

        System.out.println("THREAD_COUNT: " + THREADS);
        AtomicLong progress = new AtomicLong(0);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        long chunkSize = TOTAL_SEEDS / THREADS;

        for (int t = 0; t < THREADS; t++) {
            long start = t * chunkSize;
            long end = (t == THREADS - 1) ? TOTAL_SEEDS : start + chunkSize;

            executor.submit(() -> searchRange(start, end, progress, TOTAL_SEEDS));
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

        System.out.println();
        System.out.println("SCANNED ALL SEEDS.");
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

    /*
    static long getDecoratorSeedOld(long worldSeed, int blockX, int blockZ, int salt) {
        // matches cubiomes getDecoratorSeed
        long r = worldSeed;
        r ^= (long) blockX * 0x9e3779b97f4a7c15L;
        r ^= (long) blockZ * 0x6c62272e07bb0142L;
        r ^= (long) salt;
        r ^= 0x9e3779b97f4a7c15L;
        // mix
        r = (r ^ (r >>> 30)) * 0xbf58476d1ce4e5b9L;
        r = (r ^ (r >>> 27)) * 0x94d049bb133111ebL;
        return r ^ (r >>> 31);
    }

    static long getDecoratorSeed(long worldSeed, int blockX, int blockZ, int salt) {
        long populationSeed = getPopulationSeed(worldSeed, blockX >> 4, blockZ >> 4, MCVersion.v1_16_1);
        return (populationSeed + salt) & 0xFFFFFFFFFFFFL;
    }
    */

    /*
    // returns null if no lake, or [x, y, z] if lake found
    static int[] getLavaLake(long worldSeed, int blockX, int blockZ, int salt) {
        int step = salt / 10000;
        int index = salt % 10000;
        long seed = getDecoratorSeed(worldSeed, blockX, blockZ, index, step, MCVersion.v1_16_1);

        // use Java Random, but i think thats where i break it... v
        // both way are off when testing somehow
        //java.util.Random r = new java.util.Random(seed);
        ChunkRand r = new ChunkRand();
        r.setSeed(seed);
        // ^^^
        if (r.nextInt(8) != 0) return null;
        int lx = r.nextInt(16);
        int lz = r.nextInt(16);
        int y = r.nextInt(r.nextInt(248) + 8);
        if (y < 63 || r.nextInt(10) == 0) {
            return new int[]{lx + blockX, y, lz + blockZ};
        }
        return null;
    }

     */

    static void testSeed(long structureSeed) {
        searchRange(structureSeed, structureSeed + 1, new AtomicLong(0), 1);
    }

    static void searchRange(long start, long end, AtomicLong progress, long totalSeeds) {
        MCVersion version = MCVersion.v1_16_1;
        Dimension ow = Dimension.OVERWORLD;
        Dimension nether = Dimension.NETHER;
        ChunkRand rand = new ChunkRand();

        // PYRAMID
        DesertPyramid pyramid = new DesertPyramid(version);
        DesertPyramidGenerator dpg = new DesertPyramidGenerator(version);

        /*
        // RP
        com.seedfinding.mcfeature.structure.RuinedPortal rp = new com.seedfinding.mcfeature.structure.RuinedPortal(ow, version);
        RuinedPortalGenerator rpg = new RuinedPortalGenerator(version);
         */

        BastionRemnant bastion = new BastionRemnant(version);
        Fortress fortress = new Fortress(version);

        for (long structureSeed = start; structureSeed < end; structureSeed++) {
            long done = progress.incrementAndGet();
            if (done % 1_000_000L == 0)
                System.out.printf("===== Progress: %.1f%% =====%n", (done * 100.0) / totalSeeds);

            // =========================
            // TEMPLE STRUCTURE CHECK
            // =========================
            CPos pyramidPos = pyramid.getInRegion(structureSeed, 0, 0, rand);
            if (pyramidPos == null) continue;

            // distance to spawn filter
            if (pyramidPos.distanceTo(CPos.ZERO, DistanceMetric.CHEBYSHEV) > 6) continue;

            var terrainGen = TerrainGenerator.of(BiomeSource.of(ow, version, structureSeed));
            dpg.generate(terrainGen, pyramidPos); // make dpg usable

            /*
            // RP
            CPos rpPos = rp.getInRegion(structureSeed, 0, 0, rand);
            if (rpPos == null) continue;
            if (rpPos.distanceTo(CPos.ZERO, DistanceMetric.CHEBYSHEV) > 10) continue; // distance to spawn filter
            rpg.generate(terrainGen, rpPos); // make rpg usable
            RuinedPortalGenerator.Location location = rpg.getLocation();
            if (!portalIsOnSurface(location)) continue; // check portal on surface
             */


            // LOOT CHECK V
            var chestLoot = pyramid.getLoot(structureSeed, dpg, rand, false);

            int reqScore = -1; // set to wanted score of items: notch = 20, apple = 8, flesh = 1 /FIXME @@@@@@@@@@@@@@@@@@@
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
                if (!isEnoughLoot(notchCount, appleCount, fleshCount, ironCount, diamondCount, reqScore, false))
                    continue;
            }

            // =========================
            // BASTION + FORTRESS
            // =========================
            FastionPair fastionPair = StructureFinder.findBastionFortress(structureSeed, bastion, fortress, zeroZero, 12 /* max bast dist */, 16 /* max fort dist */, rand);
            if (fastionPair == null) continue;

            final CPos finalBastion = fastionPair.bastion;
            final CPos finalFort = fastionPair.fortress;
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
                BiomeSource overworldSource = BiomeSource.of(ow, version, worldSeed);
                if (!pyramid.canSpawn(pyramidPos, overworldSource)) return;
                //if (!rp.canSpawn(rpPos, overworldSource)) return;

                // check for forest biome within 3 chunks (for trees)
                OverworldBiomeSource owSource = (OverworldBiomeSource) overworldSource;
                if (!BiomeUtils.hasTreeBiomeNear(owSource, pyramidPos, 3 /* MAX DISTANCE */)) return;

                // check for river biome within 4 chunks (for gravel)
                if (!BiomeUtils.hasAnyBiomeNear(owSource, pyramidPos, 4 /* MAX DISTANCE */, Set.of(Biomes.RIVER, Biomes.FROZEN_RIVER)))
                    return;

                // NETHER
                BiomeSource netherSource = BiomeSource.of(nether, version, worldSeed);
                if (!bastion.canSpawn(finalBastion, netherSource)) return;
                if (!fortress.canSpawn(finalFort, netherSource)) return;

                Biome bastionBiome = netherSource.getBiome(finalBastion.getX() << 4, 0, finalBastion.getZ() << 4);
                if (bastionBiome == Biomes.BASALT_DELTAS) return; // skip basalt bastion

                // pyramid and RP distance from SPAWN
                CPos spawnPos = SpawnPoint.getApproximateSpawn((OverworldBiomeSource) overworldSource).toChunkPos();
                if (spawnPos.distanceTo(pyramidPos, DistanceMetric.CHEBYSHEV) > 3) return;
                //if (pyramidPos.distanceTo(rpPos, DistanceMetric.CHEBYSHEV) > 5) return;

                // REAL TERRAIN GENERATION
                TerrainGenerator terrainGenerator = TerrainGenerator.of(overworldSource);
                DesertPyramidGenerator desertPyramidGenerator = new DesertPyramidGenerator(version);
                if (!desertPyramidGenerator.generate(terrainGenerator, pyramidPos)) return;
                /*
                RuinedPortalGenerator ruinedPortalGeneratorSeed = new RuinedPortalGenerator(version);
                if (!ruinedPortalGeneratorSeed.generate(terrainGenerator, rpPos)) return;

                String type = ruinedPortalGeneratorSeed.getType();
                if (!rpHasEnoughLava(type)) return; // check portal has lava
                 */

                if (chestLoot.isEmpty()) return;

                // scan chunks near pyramid for surface LAVA LAKE
                CPos lavaLakeCords = new CPos(-999, -999);
                final int DESERT_LAVA_LAKE_SALT_1_16 = 10000; //10000 = desert, desert hills, desert lakes biomes, 10001 = all other
                boolean hasLava = false;
                int lavaDist = 5;
                for (int cx = pyramidPos.getX() - lavaDist; cx <= pyramidPos.getX() + lavaDist; cx++) {
                    for (int cz = pyramidPos.getZ() - lavaDist; cz <= pyramidPos.getZ() + lavaDist; cz++) {
                        // checking only for desert
                        if (owSource.getBiome(cx << 4, 0, cz << 4) != Biomes.DESERT) continue;

                        int[] lake = getLavaLake(worldSeed, cx << 4, cz << 4, DESERT_LAVA_LAKE_SALT_1_16);
                        if (lake != null && lake[1] >= 60) { // surface-ish
                            hasLava = true;
                            lavaLakeCords = new CPos(lake[0], lake[2]);
                            break;
                        }
                    }
                    if (hasLava) break;
                }

                if (!hasLava) return;

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

                // final seed print
                System.out.printf(
                        "%d (%d)%n" +
                                "Desert Temple: [%4d, %4d] (%d)%n" +
                                /*
                                "RP:            [%4d, %4d] (%d)%n" +
                                 */
                                "Bastion:       [%4d, %4d] (%d)%n" +
                                "Fort:          [%4d, %4d] (%d)%n" +
                                "Lava Lake:     [%4d, %4d]%n" +
                                "%s, %s %d%n%n",

                        worldSeed,
                        finalStructureSeed,

                        (pyramidPos.getX() * 16) + 10,
                        (pyramidPos.getZ() * 16) + 10,
                        Math.toIntExact((long) spawnPos.distanceTo(new CPos((pyramidPos.getX() * 16) + 10, (pyramidPos.getZ() * 16) + 10), DistanceMetric.EUCLIDEAN)),

                        /*
                        (rpPos.getX() * 16) + 10,
                        (rpPos.getZ() * 16) + 10,
                        Math.toIntExact((long) spawnPos.distanceTo(new CPos((rpPos.getX() * 16),(rpPos.getZ() * 16)), DistanceMetric.EUCLIDEAN)),
                         */

                        finalBastion.getX() * 16,
                        finalBastion.getZ() * 16,
                        Math.toIntExact((long) spawnPos.distanceTo(new CPos((finalBastion.getX() * 16), (finalBastion.getZ() * 16)), DistanceMetric.EUCLIDEAN)),

                        finalFort.getX() * 16,
                        finalFort.getZ() * 16,
                        Math.toIntExact((long) (new CPos((finalBastion.getX() * 16), (finalBastion.getZ() * 16))).distanceTo(new CPos((finalFort.getX() * 16), (finalFort.getZ() * 16)), DistanceMetric.EUCLIDEAN)),

                        lavaLakeCords.getX(),
                        lavaLakeCords.getZ(),

                        endSpawnStatus,
                        backOrFront,
                        zeroHeight
                );
            });
        }
    }
}