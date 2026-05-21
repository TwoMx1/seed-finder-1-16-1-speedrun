package org.example;

import com.seedfinding.mcbiome.source.BiomeSource;
import com.seedfinding.mcbiome.source.NetherBiomeSource;
import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.block.Blocks;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.rand.seed.WorldSeed;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.util.math.DistanceMetric;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import com.seedfinding.mcfeature.loot.item.Items;
import com.seedfinding.mcfeature.misc.SpawnPoint;
import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mcfeature.structure.RuinedPortal;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;
import com.seedfinding.mcterrain.TerrainGenerator;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class Main {
    static final long TOTAL_SEEDS = 100_000_000L;
    static final int THREADS = Math.max(Runtime.getRuntime().availableProcessors() - 2, 1);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Using " + THREADS + " threads");
        AtomicLong progress = new AtomicLong(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);

        long chunkSize = TOTAL_SEEDS / THREADS;

        for (int t = 0; t < THREADS; t++) {
            long start = t * chunkSize;
            long end = (t == THREADS - 1) ? TOTAL_SEEDS : start + chunkSize;

            executor.submit(() -> searchRange(start, end, progress));
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.MILLISECONDS);
        System.out.println("Done.");
    }

    static int getTotalObby(String type) {
        if (Objects.equals(type, "portal_8")
                || Objects.equals(type, "portal_6"))
        {
            return 12;
        }
        if (Objects.equals(type, "giant_portal_1")
                || Objects.equals(type, "giant_portal_2")
                || Objects.equals(type, "giant_portal_3")) return -1; // only use side

        return 10;
    }

    static void searchRange(long start, long end, AtomicLong progress) {
        MCVersion version = MCVersion.v1_16_1;
        Dimension ow = Dimension.OVERWORLD;
        Dimension nether = Dimension.NETHER;
        ChunkRand rand = new ChunkRand();
        ChunkRand randBast = new ChunkRand();
        RuinedPortal rp = new RuinedPortal(ow, version);
        RuinedPortalGenerator rpg = new RuinedPortalGenerator(version);
        CPos zeroZero = new CPos(0, 0);

        BastionRemnant bastion = new BastionRemnant(version);
        Fortress fortress = new Fortress(version);

        for (long structureSeed = start; structureSeed < end; structureSeed++) {
            long done = progress.incrementAndGet();
            if (done % 1_000_000L == 0) {
                System.out.printf("Progress: %.1f%%%n", (done * 100.0) / TOTAL_SEEDS);
            }

            // ====== STRUCTURE SEED ===========

            // Ruined portal
            {
                CPos rpPos = rp.getInRegion(structureSeed, 0, 0, rand);
                //if (rpPos.distanceTo(CPos.ZERO, DistanceMetric.CHEBYSHEV) > 5) continue;

                var terrainGen = TerrainGenerator.of(BiomeSource.of(ow, version, structureSeed));
                rpg.generate(terrainGen, rpPos); // make rpg usable

                RuinedPortalGenerator.Location location = rpg.getLocation();
                if (location != RuinedPortalGenerator.Location.ON_LAND_SURFACE) continue; // check portal on surface

                //if (rpg.isBuried()) continue; // PR is burried

                var chestLoot = rp.getLoot(structureSeed, rpg, rand, false);

                if (chestLoot.isEmpty()) continue;

                boolean chestMeetsAllConditions = false;
                for (var chest : chestLoot) {
                    // light
                    int iron_ingot_count = 3;
                    boolean HasEnoughLight = chest.containsAtLeast(Items.FLINT_AND_STEEL, 1) || chest.containsAtLeast(Items.FIRE_CHARGE, 6);
                    boolean hasRealLight = HasEnoughLight || chest.containsAtLeast(Items.FIRE_CHARGE, 1);
                    if (!hasRealLight) {
                        if (!chest.containsAtLeast(Items.FLINT, 1)) break;
                        iron_ingot_count++;
                    }

                    // food
                    if (!HasEnoughLight && (!(chest.containsAtLeast(Items.GOLDEN_CARROT, 4)
                            || chest.containsAtLeast(Items.GOLDEN_APPLE, 1)
                            || chest.containsAtLeast(Items.ENCHANTED_GOLDEN_APPLE, 1)))) break;

                    // iron
                    if (!(chest.containsAtLeast(Items.IRON_NUGGET, iron_ingot_count * 9))) break;

                    // axe
                    //if (!(chest.containsAtLeast(Items.GOLDEN_AXE, 1))) break;


                    // looting
                    boolean checkLooting = false; // LOOTING <=================
                    if (checkLooting) {
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
                        if (!(chest.containsAtLeast(Items.GOLDEN_SWORD, 1))) break;
                        if (!hasLooting) break;
                    }

                    chestMeetsAllConditions = true;
                }

                if (!chestMeetsAllConditions) continue;


                // check bastion within 12 chunk of 0 0, and fortress within 16 chunks of bastion
                CPos foundBastion = null;
                CPos foundFort = null;
                outer:
                for (int rx = -1; rx <= 1; rx++) {
                    for (int rz = -1; rz <= 1; rz++) {

                        CPos b = bastion.getInRegion(structureSeed, rx, rz, new ChunkRand());
                        if (b == null) continue;
                        if (b.distanceTo(zeroZero, DistanceMetric.CHEBYSHEV) > 12) continue;

                        for (int frx = rx - 1; frx <= rx + 1; frx++) {
                            for (int frz = rz - 1; frz <= rz + 1; frz++) {

                                CPos f = fortress.getInRegion(structureSeed, frx, frz, new ChunkRand());
                                if (f == null) continue;

                                if (f.distanceTo(b, DistanceMetric.CHEBYSHEV) <= 16) {
                                    foundBastion = b;
                                    foundFort = f;
                                    break outer;
                                }
                            }
                        }
                    }
                }

                if (foundBastion == null || foundFort == null) continue;

                final long finalStructureSeed = structureSeed;

                CPos finalBastion = foundBastion;
                CPos finalFort = foundFort;

                WorldSeed.getSisterSeeds(structureSeed).asStream().boxed().limit(1000)
                        .forEach(worldSeed -> {
                            BiomeSource overworldSource = BiomeSource.of(ow, version, worldSeed);
                            if (!rp.canSpawn(rpPos, overworldSource)) return;

                            BiomeSource netherSource = BiomeSource.of(nether, version, worldSeed);
                            if (!bastion.canSpawn(finalBastion, netherSource)) return;
                            if (!fortress.canSpawn(finalFort, netherSource)) return;

                            CPos spawnPos = SpawnPoint.getApproximateSpawn((OverworldBiomeSource) overworldSource).toChunkPos();
                            if (spawnPos.distanceTo(rpPos, DistanceMetric.CHEBYSHEV) > 5) return;

                            // =============

                            TerrainGenerator tg = TerrainGenerator.of(overworldSource);
                            RuinedPortalGenerator ruinedPortalGeneratorSeed = new RuinedPortalGenerator(version);
                            ruinedPortalGeneratorSeed.generate(tg, rpPos);

                            if (ruinedPortalGeneratorSeed.getChestsPos().isEmpty()) return; // chest didnt spawn

                            var minimalPortal = ruinedPortalGeneratorSeed.getMinimalPortal();
                            long obbyCount = minimalPortal.stream()
                                    .filter(pair -> pair.getFirst() == Blocks.OBSIDIAN)
                                    .count();

                            String type = ruinedPortalGeneratorSeed.getType();

                            int totalObbyForFrame = getTotalObby(type);

                            int missingObby;
                            if (totalObbyForFrame == -1) missingObby = 5; // complete side setup
                            else {
                                missingObby = Math.toIntExact(totalObbyForFrame - obbyCount);
                            }

                            var portal = ruinedPortalGeneratorSeed.getPortal();
                            long cryingBlocks = portal.stream()
                                    .filter(pair -> pair.getFirst() == Blocks.CRYING_OBSIDIAN)
                                    .count();

                            if (cryingBlocks > 0) return; // no crying

                            // obi
                            if (!chestLoot.stream().anyMatch(chest -> chest.containsAtLeast(Items.OBSIDIAN, missingObby))) {
                                return;
                            }

                            //if (zeroZero.distanceTo(bastionPos, DistanceMetric.CHEBYSHEV) > 8) return;

                            System.out.print(worldSeed + " (" + finalStructureSeed + "), [" + obbyCount + "/" + totalObbyForFrame + "]");
                            System.out.println();
                        });
            }


        }
    }
}