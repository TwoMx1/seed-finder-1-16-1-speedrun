package org.example;

import com.seedfinding.mcfeature.structure.BastionRemnant;
import com.seedfinding.mcfeature.structure.Fortress;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.math.DistanceMetric;
import com.seedfinding.mccore.util.pos.CPos;

public class StructureFinder {

    public static FastionPair findBastionFortress(
            long structureSeed,
            BastionRemnant bastion,
            Fortress fortress,
            CPos zeroZero,
            int bastionMaxDist,
            int fortMaxDist,
            ChunkRand rand
    ) {

        for (int rx = -1; rx <= 1; rx++) {
            for (int rz = -1; rz <= 1; rz++) {

                CPos b = bastion.getInRegion(structureSeed, rx, rz, rand);
                if (b == null) continue;

                if (b.distanceTo(zeroZero, DistanceMetric.CHEBYSHEV) > bastionMaxDist)
                    continue;

                for (int frx = rx - 1; frx <= rx + 1; frx++) {
                    for (int frz = rz - 1; frz <= rz + 1; frz++) {

                        CPos f = fortress.getInRegion(structureSeed, frx, frz, rand);
                        if (f == null) continue;

                        if (f.distanceTo(b, DistanceMetric.CHEBYSHEV) <= fortMaxDist) {
                            return new FastionPair(b, f);
                        }
                    }
                }
            }
        }

        return null;
    }
}