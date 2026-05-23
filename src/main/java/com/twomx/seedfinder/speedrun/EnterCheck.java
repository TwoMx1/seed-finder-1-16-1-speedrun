package com.twomx.seedfinder.speedrun;

import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;

import static com.seedfinding.mccore.rand.seed.ChunkSeeds.getDecoratorSeed;

public class EnterCheck {
    // returns only lava enterable portals based on name
    public static boolean rpHasEnoughLava(String portalName) {
        return switch (portalName) {
            case "giant_portal_1", "giant_portal_2", "giant_portal_3" -> true; //giant
            case "portal_2", "portal_7", "portal_8", "portal_10" -> true; // 3x2
            default -> false; // not enough lava
        };
    }

    // check portal on surface
    public static boolean portalIsOnSurface(RuinedPortalGenerator.Location rpLocation) {
        return rpLocation == RuinedPortalGenerator.Location.ON_LAND_SURFACE;
    }

    /*
    public static long getDecoratorSeedOld(long worldSeed, int blockX, int blockZ, int salt) {
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

    public static long getDecoratorSeed(long worldSeed, int blockX, int blockZ, int salt) {
        long populationSeed = getPopulationSeed(worldSeed, blockX >> 4, blockZ >> 4, MCVersion.v1_16_1);
        return (populationSeed + salt) & 0xFFFFFFFFFFFFL;
    }
    */

    // returns null if no lake, or [x, y, z] if lake found
    public static int[] getLavaLake(long worldSeed, int blockX, int blockZ, int salt) {
        int step = salt / 10000;
        int index = salt % 10000;
        //long seed = getDecoratorSeed(worldSeed, blockX, blockZ, index, step, MCVersion.v1_16_1);

        // use Java Random, but i think thats where i break it... v
        // both way are off when testing somehow
        //java.util.Random r = new java.util.Random(seed);
        //ChunkRand r = new ChunkRand();
        //r.setSeed(seed);
        ChunkRand r = new ChunkRand();
        r.setDecoratorSeed(worldSeed, blockX, blockZ, index, step, MCVersion.v1_16_1);
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
}
