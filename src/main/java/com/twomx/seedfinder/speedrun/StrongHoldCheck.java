package com.twomx.seedfinder.speedrun;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.util.pos.BPos;
import com.seedfinding.mccore.util.pos.CPos;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mcfeature.structure.Stronghold;
import com.seedfinding.mcfeature.structure.generator.piece.stronghold.PortalRoom;
import com.seedfinding.mcfeature.structure.generator.structure.StrongholdGenerator;
import com.seedfinding.mcseed.rand.JRand;

import java.util.ArrayList;
import java.util.List;

public class StrongHoldCheck {

    public static class StrongholdInfo {
        public final CPos chunkPos;
        public final int blockX;
        public final int blockZ;
        public final int eyeCount;
        public final BPos portalRoomPos; // null if not found

        public StrongholdInfo(CPos chunkPos, int eyeCount, BPos portalRoomPos) {
            this.chunkPos = chunkPos;
            this.blockX = (chunkPos.getX() << 4) + 8;
            this.blockZ = (chunkPos.getZ() << 4) + 8;
            this.eyeCount = eyeCount;
            this.portalRoomPos = portalRoomPos;
        }

        @Override
        public String toString() {
            String portalStr = portalRoomPos != null
                    ? String.format("(%d, %d, %d) or [%d, %d]", portalRoomPos.getX(), portalRoomPos.getY(), portalRoomPos.getZ(), portalRoomPos.getX() >> 3, portalRoomPos.getZ() >> 3)
                    : "-1";
            return String.format("SH info: {[%d, %d], %d Eyes, PR=%s}",
                    blockX, blockZ, eyeCount, portalStr);

        }
    }

    private final MCVersion version;
    private final Stronghold stronghold;

    public StrongHoldCheck(MCVersion version) {
        this.version = version;
        this.stronghold = new Stronghold(version);
    }

    /**
     * Returns info for the first-ring strongholds (3 strongholds in v1.9+).
     *
     * @param worldSeed the world seed
     * @return list of StrongholdInfo for the first ring
     */
    public List<StrongholdInfo> getFirstRingStrongholds(long worldSeed) {
        OverworldBiomeSource biomeSource = new OverworldBiomeSource(version, worldSeed);
        JRand jRand = new JRand(0L);

        CPos[] allStarts = stronghold.getStarts(biomeSource, 3, jRand);

        List<StrongholdInfo> result = new ArrayList<>();
        ChunkRand chunkRand = new ChunkRand();

        for (CPos start : allStarts) {
            result.add(getStrongholdInfo(worldSeed, start, chunkRand));
        }

        return result;
    }

    private StrongholdInfo getStrongholdInfo(long worldSeed, CPos start, ChunkRand chunkRand) {
        StrongholdGenerator generator = new StrongholdGenerator(version);
        generator.populateStructure(worldSeed, start.getX(), start.getZ(), chunkRand);

        // find portal room piece and grab its center
        BPos portalRoomPos = null;
        for (Stronghold.Piece piece : generator.pieceList) {
            if (piece instanceof PortalRoom) {
                portalRoomPos = new BPos(
                        piece.getBoundingBox().getCenter().getX(),
                        piece.getBoundingBox().minY,  // this is not precise idk waht to do
                        //piece.getBoundingBox().getCenter().getY(),
                        piece.getBoundingBox().getCenter().getZ()
                );
                break;
            }
        }

        int eyeCount = -1;
        boolean[] eyes = generator.getEyes();
        if (eyes != null) {
            eyeCount = 0;
            for (boolean eye : eyes) {
                if (eye) eyeCount++;
            }
        }

        return new StrongholdInfo(start, eyeCount, portalRoomPos);
    }
}