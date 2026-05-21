package com.twomx.seedfinder.speedrun.seedtype;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mcbiome.biome.Biomes;
import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.rand.seed.PillarSeed;
import com.seedfinding.mccore.rand.seed.WorldSeed;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.math.DistanceMetric;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.ChestContent;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcfeature.misc.SpawnPoint;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;
import com.seedfinding.mcterrain.TerrainGenerator;
import com.twomx.seedfinder.speedrun.FastionPair;
import com.twomx.seedfinder.speedrun.StructureFinder;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.seedfinding.mccore.rand.seed.PillarSeed.getPillarHeights;

public class RuinedPortal {
    static final long TOTAL_SEEDS = 100_000_000L;
    static final int THREADS = Math.max(Runtime.getRuntime().availableProcessors() - 2, 1);
    static CPos zeroZero = new CPos(0, 0);

    public static void main(String[] args) throws InterruptedException {
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

    static int getTotalObby(String type) {
        if (Objects.equals(type, "portal_8")
                || Objects.equals(type, "portal_6")) {
            return 12;
        }
        if (Objects.equals(type, "giant_portal_1")
                || Objects.equals(type, "giant_portal_2")
                || Objects.equals(type, "giant_portal_3")) return -1; // only use side

        return 10;
    }

    static boolean isGoodRpChest(ChestContent chest, int lootingLevel, boolean needAxe) {
        // light
        int iron_ingot_count = 3;
        boolean HasEnoughLight = chest.containsAtLeast(Items.FLINT_AND_STEEL, 1) || chest.containsAtLeast(Items.FIRE_CHARGE, 6);
        boolean hasRealLight = HasEnoughLight || chest.containsAtLeast(Items.FIRE_CHARGE, 1);
        if (!hasRealLight) {
            if (!chest.containsAtLeast(Items.FLINT, 1)) return false;
            iron_ingot_count++;
        }

        // food
        if (!HasEnoughLight && (!(chest.containsAtLeast(Items.GOLDEN_CARROT, 4)
                || chest.containsAtLeast(Items.GOLDEN_APPLE, 1)
                || chest.containsAtLeast(Items.ENCHANTED_GOLDEN_APPLE, 1)))) return false;

        // iron
        if (!(chest.containsAtLeast(Items.IRON_NUGGET, iron_ingot_count * 9))) return false;

        // axe
        if (needAxe && !(chest.containsAtLeast(Items.GOLDEN_AXE, 1))) return false;

        // looting
        if (lootingLevel > 0 && lootingLevel < 4) {
            boolean hasLooting = false;
            for (ItemStack stack : chest.getItems()) {
                if (stack.getItem().getName().equals("golden_sword")) {
                    for (var enchant : stack.getItem().getEnchantments()) {
                        if (enchant.getFirst().equals("looting") && enchant.getSecond() >= 1) {
                            hasLooting = true;
                            break;
                        }
                    }
                }
                if (hasLooting) break;
            }
            if (!(chest.containsAtLeast(Items.GOLDEN_SWORD, 1))) return false;
            if (!hasLooting) return false;
        }

        return true; //passed all check
    }

    static void searchRange(long start, long end, AtomicLong progress, long totalSeeds) {
        MCVersion version = MCVersion.v1_16_1;
        Dimension ow = Dimension.OVERWORLD;
        Dimension nether = Dimension.NETHER;
        ChunkRand rand = new ChunkRand();

        com.seedfinding.mcfeature.structure.RuinedPortal rp = new com.seedfinding.mcfeature.structure.RuinedPortal(ow, version);
        RuinedPortalGenerator rpg = new RuinedPortalGenerator(version);

        BastionRemnant bastion = new BastionRemnant(version);
        Fortress fortress = new Fortress(version);

        for (long structureSeed = start; structureSeed < end; structureSeed++) {
            long done = progress.incrementAndGet();
            if (done % 1_000_000L == 0)
                System.out.printf("===== Progress: %.1f%% =====%n", (done * 100.0) / totalSeeds);

            // =========================
            // RP STRUCTURE CHECK
            // =========================
            CPos rpPos = rp.getInRegion(structureSeed, 0, 0, rand);
            if (rpPos == null) continue;

            // distance to spawn filter
            if (rpPos.distanceTo(CPos.ZERO, DistanceMetric.CHEBYSHEV) > 8) continue;

            var terrainGen = TerrainGenerator.of(BiomeSource.of(ow, version, structureSeed));
            rpg.generate(terrainGen, rpPos); // make rpg usable

            RuinedPortalGenerator.Location location = rpg.getLocation();
            if (location != RuinedPortalGenerator.Location.ON_LAND_SURFACE) continue; // check portal on surface

            var chestLoot = rp.getLoot(structureSeed, rpg, rand, false);

            if (chestLoot.isEmpty()) continue;

            boolean chestMeetsAllConditions = false;
            for (var chest : chestLoot) {
                // checks chest content
                chestMeetsAllConditions = isGoodRpChest(chest, 0, false);
            }
            if (!chestMeetsAllConditions) continue;

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
                if (!rp.canSpawn(rpPos, overworldSource)) return;

                // NETHER
                BiomeSource netherSource = BiomeSource.of(nether, version, worldSeed);
                if (!bastion.canSpawn(finalBastion, netherSource)) return;
                if (!fortress.canSpawn(finalFort, netherSource)) return;

                Biome bastionBiome = netherSource.getBiome(finalBastion.getX() << 4, 0, finalBastion.getZ() << 4);
                if (bastionBiome == Biomes.BASALT_DELTAS) return; // skip basalt bastion

                // portal distance from SPAWN
                CPos spawnPos = SpawnPoint.getApproximateSpawn((OverworldBiomeSource) overworldSource).toChunkPos();
                if (spawnPos.distanceTo(rpPos, DistanceMetric.CHEBYSHEV) > 5) return;

                // REAL TERRAIN GENERATION
                TerrainGenerator terrainGenerator = TerrainGenerator.of(overworldSource);
                RuinedPortalGenerator ruinedPortalGeneratorSeed = new RuinedPortalGenerator(version);
                ruinedPortalGeneratorSeed.generate(terrainGenerator, rpPos);

                if (ruinedPortalGeneratorSeed.getChestsPos().isEmpty() || chestLoot.isEmpty()) return; // chest didnt spawn

                // checking portal is completable
                var minimalPortal = ruinedPortalGeneratorSeed.getMinimalPortal();
                long obbyCount = minimalPortal.stream().filter(pair -> pair.getFirst() == Blocks.OBSIDIAN).count();
                String type = ruinedPortalGeneratorSeed.getType();
                int totalObbyForFrame = getTotalObby(type);
                int missingObby;
                if (totalObbyForFrame == -1) missingObby = 5; // complete side setup
                else missingObby = Math.toIntExact(totalObbyForFrame - obbyCount);

                var portal = ruinedPortalGeneratorSeed.getPortal();
                long cryingBlocks = portal.stream().filter(pair -> pair.getFirst() == Blocks.CRYING_OBSIDIAN).count();
                if (cryingBlocks > 0) return; // no crying

                // obi
                if (!chestLoot.stream().anyMatch(chest -> chest.containsAtLeast(Items.OBSIDIAN, missingObby))) return;

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
                                "RP:            [%4d, %4d] (%d)%n" +
                                "Bastion:       [%4d, %4d] (%d)%n" +
                                "Fort:          [%4d, %4d] (%d)%n" +
                                "%s, %s %d%n%n",

                        worldSeed,
                        finalStructureSeed,

                        (rpPos.getX() * 16) + 10,
                        (rpPos.getZ() * 16) + 10,
                        Math.toIntExact((long) spawnPos.distanceTo(new CPos((rpPos.getX() * 16),(rpPos.getZ() * 16)), DistanceMetric.EUCLIDEAN)),

                        finalBastion.getX() * 16,
                        finalBastion.getZ() * 16,
                        Math.toIntExact((long) spawnPos.distanceTo(new CPos((finalBastion.getX() * 16), (finalBastion.getZ() * 16)), DistanceMetric.EUCLIDEAN)),

                        finalFort.getX() * 16,
                        finalFort.getZ() * 16,
                        Math.toIntExact((long) (new CPos((finalBastion.getX() * 16), (finalBastion.getZ() * 16))).distanceTo(new CPos((finalFort.getX() * 16), (finalFort.getZ() * 16)), DistanceMetric.EUCLIDEAN)),

                        endSpawnStatus,
                        backOrFront,
                        zeroHeight
                );
            });
        }
    }
}