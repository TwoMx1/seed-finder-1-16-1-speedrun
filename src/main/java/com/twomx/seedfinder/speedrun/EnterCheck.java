package com.twomx.seedfinder.speedrun;

import com.seedfinding.mcfeature.structure.generator.structure.RuinedPortalGenerator;

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
}
