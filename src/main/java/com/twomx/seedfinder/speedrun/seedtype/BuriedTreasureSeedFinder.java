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
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.MCLootTables;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcfeature.misc.SpawnPoint;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.BuriedTreasure;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mcfeature.structure.RegionStructure;
import com.seedfinding.mcterrain.TerrainGenerator;
import com.twomx.seedfinder.speedrun.BiomeUtils;
import com.twomx.seedfinder.speedrun.FastionPair;
import com.twomx.seedfinder.speedrun.StructureFinder;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.seedfinding.mccore.rand.seed.PillarSeed.getPillarHeights;
import static com.twomx.seedfinder.speedrun.EnterCheck.getLavaLake;

public class BuriedTreasureSeedFinder {

    static final long TOTAL_SEEDS = 100_000_000L;
    static final int THREADS = Math.max(Runtime.getRuntime().availableProcessors() - 0, 1);
    static CPos zeroZero = new CPos(0, 0);

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

    public static boolean enoughLoot(int ironCount, int diamondCount, int goldCount, int tntCount, int fishCount, boolean wantAxeAndShovel) {
        int iron = ironCount;
        int diamond = diamondCount;
        int gold = goldCount;

        // TNT + Food + Bucket + FNS
        if (iron < 4 || tntCount < 1 || fishCount < 6) return false;
        iron -= 4;

        // pickaxe
        if (iron < 3 && diamond < 3) return false;
        if (diamond >= 3) diamond -= 3;
        else iron -= 3;

        if (wantAxeAndShovel) {
            // axe
            if (iron < 3 && diamond < 3 && gold < 3) return false;
            if (diamond >= 3) diamond -= 3;
            else if (iron >= 3) iron -= 3;
            else gold -= 3;

            // shovel
            if (iron < 1 && diamond < 1 && gold < 1) return false;
        }

        return true;
    }

    static void searchRange(long start, long end, AtomicLong progress) {
        MCVersion version = MCVersion.v1_16_1;
        Dimension ow = Dimension.OVERWORLD;
        Dimension nether = Dimension.NETHER;
        ChunkRand rand = new ChunkRand();

        BuriedTreasure bt = new BuriedTreasure(version);
        BastionRemnant bastion = new BastionRemnant(version);
        Fortress fortress = new Fortress(version);

        for (long structureSeed = start; structureSeed < end; structureSeed++) {
            if (Thread.currentThread().isInterrupted()) return;

            long done = progress.incrementAndGet();
            if (done % 1_000_000L == 0)
                System.out.printf("===== Progress: %.1f%% =====%n", (done * 100.0) / TOTAL_SEEDS);

            // =========================
            // BURIED TREASURE POSITION
            // =========================
            // BuriedTreasure always spawns at chunk (9,9) within its region,
            // so we check nearby regions around 0,0
            CPos btPos = null;
            for (int rx = -4; rx <= 4; rx++) {
                for (int rz = -4; rz <= 4; rz++) {
                    CPos candidate = bt.getInRegion(structureSeed, rx, rz, rand);
                    if (candidate == null) continue;
                    if (candidate.distanceTo(zeroZero, DistanceMetric.CHEBYSHEV) <= 6) {
                        btPos = candidate;
                        break;
                    }
                }
                if (btPos != null) break;
            }
            if (btPos == null) continue;

            // =========================
            // BASTION + FORTRESS
            // =========================
            FastionPair fastionPair = StructureFinder.findBastionFortress(structureSeed, bastion, fortress, zeroZero, 6 /* max bast dist */, 10 /* max fort dist */, rand);
            if (fastionPair == null) continue;

            final CPos finalBastion = fastionPair.bastion;
            final CPos finalFort = fastionPair.fortress;
            final CPos finalBtPos = btPos; // BT
            final long finalStructureSeed = structureSeed;

            // pillars for 0 cycle
            long pillarSeed = PillarSeed.fromStructureSeed(structureSeed);
            int[] pillarHeights = getPillarHeights(pillarSeed);
            String backOrFront;
            int zeroHeight;
            if (pillarHeights[0] < pillarHeights[5]) {
                backOrFront = "Back";
                zeroHeight = pillarHeights[4];
            }
            else {
                zeroHeight = pillarHeights[9];
                backOrFront = "Front";
            }

            // =========================
            // WORLD SEED CHECKS
            // =========================
            WorldSeed.getSisterSeeds(structureSeed).asStream().boxed().limit(1000).forEach(worldSeed -> {

                // BIOME CHECK — must be beach/snowy beach for buried treasure
                BiomeSource overworldSource = BiomeSource.of(ow, version, worldSeed);
                if (!bt.canSpawn(finalBtPos, overworldSource)) return;

                // check for forest biome within 3 chunks (for trees)
                OverworldBiomeSource owSource = (OverworldBiomeSource) overworldSource;
                if (!BiomeUtils.hasTreeBiomeNear(owSource, finalBtPos, 3 /* MAX DISTANCE */)) return;
                if (!BiomeUtils.hasAnyBiomeNear(owSource, finalBtPos, 5 /* MAX DISTANCE */, Set.of(Biomes.DEEP_OCEAN))) return;
                if (!BiomeUtils.hasAnyBiomeNear(owSource, finalBtPos, 5 /* MAX DISTANCE */, Set.of(Biomes.PLAINS))) return;

                // NETHER
                BiomeSource netherSource = BiomeSource.of(nether, version, worldSeed);
                if (!bastion.canSpawn(finalBastion, netherSource)) return;
                if (!fortress.canSpawn(finalFort, netherSource)) return;

                Biome bastionBiome = netherSource.getBiome(finalBastion.getX() << 4, 0, finalBastion.getZ() << 4);
                if (bastionBiome == Biomes.BASALT_DELTAS) return;

                // SPAWN CHECK
                CPos spawnPos = SpawnPoint.getApproximateSpawn(owSource).toChunkPos();
                if (spawnPos.distanceTo(finalBtPos, DistanceMetric.CHEBYSHEV) > 3) return;

                // LOOT CHECK
                RegionStructure.Data<BuriedTreasure> treasureData = bt.at(finalBtPos.getX(), finalBtPos.getZ());
                if (!treasureData.testStart(WorldSeed.toStructureSeed(worldSeed), rand)) return;

                long decoratorSeed = rand.setPopulationSeed(worldSeed, finalBtPos.getX() << 4, finalBtPos.getZ() << 4, version);
                rand.setDecoratorSeed(decoratorSeed, 1, 3, version);
                long lootTableSeed = rand.nextLong();

                LootContext context = new LootContext(lootTableSeed, version);
                List<ItemStack> loot = MCLootTables.BURIED_TREASURE_CHEST.get().generate(context);

                int ironCount = 0, tntCount = 0, diamondCount = 0, goldCount = 0, fishCounter = 0;
                for (ItemStack stack : loot) {
                    String name = stack.getItem().getName();
                    if (name.equals("iron_ingot")) ironCount += stack.getCount();
                    if (name.equals("tnt")) tntCount += stack.getCount();
                    if (name.equals("diamond")) diamondCount += stack.getCount();
                    if (name.equals("gold_ingot")) goldCount += stack.getCount();
                    if (name.equals("cooked_cod") || name.equals("cooked_salmon")) fishCounter += stack.getCount();
                }

                // LOOT FILTER — adjust as needed
                //if (!enoughLoot(ironCount, diamondCount, goldCount, tntCount, fishCounter, true)) return; // <========

                // scan chunks near pyramid for surface LAVA LAKE
                CPos lavaLakeCords = new CPos(-999, -999);
                final int DESERT_LAVA_LAKE_SALT_1_16 = 10001; //10000 = desert, desert hills, desert lakes biomes, 10001 = all other
                boolean hasLava = false;
                int lavaDist = 5;
                for (int cx = spawnPos.getX() - lavaDist; cx <= spawnPos.getX() + lavaDist; cx++) {
                    for (int cz = spawnPos.getZ() - lavaDist; cz <= spawnPos.getZ() + lavaDist; cz++) {
                        // checking only for desert
                        if (owSource.getBiome(cx << 4, 0, cz << 4) != Biomes.PLAINS) continue;

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

                System.out.printf(
                        "%d (%d)%n" +
                                "/tp %d ~ %d%n" +
                                "BuriedTreasure: [%4d, %4d] (dist from spawn: %d)%n" +
                                "Bastion:        [%4d, %4d]%n" +
                                "Fort:           [%4d, %4d]%n" +
                                "Loot: iron=%d diamond=%d gold=%d tnt=%d fish=%d%n" +
                                "Lava Lake:     [%4d, %4d]%n" +
                                "%s, %s %d%n%n",

                        // seeds
                        worldSeed,
                        finalStructureSeed,

                        // bt cords + tp command
                        finalBtPos.getX() << 4 + 9, finalBtPos.getZ() << 4 + 9,

                        finalBtPos.getX() << 4 + 9, finalBtPos.getZ() << 4 + 9,
                        Math.toIntExact((long) spawnPos.distanceTo(finalBtPos, DistanceMetric.CHEBYSHEV)),

                        // bastion and fort
                        finalBastion.getX() << 4, finalBastion.getZ() << 4,
                        finalFort.getX() << 4, finalFort.getZ() << 4,

                        ironCount, diamondCount, goldCount, tntCount, fishCounter, // loot info

                        // lava lake
                        lavaLakeCords.getX(),
                        lavaLakeCords.getZ(),

                        // end info
                        endSpawnStatus,
                        backOrFront,
                        zeroHeight
                );
            });
        }
    }
}