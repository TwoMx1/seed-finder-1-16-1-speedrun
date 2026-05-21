package com.twomx.seedfinder.speedrun;

import com.seedfinding.mccore.util.pos.CPos;

public class FastionPair {
    public final CPos bastion;
    public final CPos fortress;

    public FastionPair(CPos bastion, CPos fortress) {
        this.bastion = bastion;
        this.fortress = fortress;
    }
}