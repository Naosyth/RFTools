package com.mcjty.rftools.blocks.endergen;

import net.minecraftforge.common.config.Configuration;

public class EndergenicConfiguration {
    public static final String CATEGORY_ENDERGENIC = "Endergenic";
    // This value indicates the chance (with 0 being no chance and 100 being 100% chance) that an
    // endergenic pearl is lost while holding it.
    public static int chanceLost = 1;
    // This value indicates how much RF is being consumed every tick to try to keep the endergenic pearl.
    public static int rfToHoldPearl = 1000;
    // This value indicates how much RF/tick this block can send out to neighbours
    public static int rfOutput = 20000;
    public static int goodParticleCount = 10;
    public static int badParticleCount = 10;
    public static boolean logEndergenic = false;

    public static void init(Configuration cfg) {
        chanceLost = cfg.get(CATEGORY_ENDERGENIC, "endergenicChanceLost", chanceLost,
                "The chance (in percent) that an endergenic pearl is lost while trying to hold it").getInt();
        rfToHoldPearl = cfg.get(CATEGORY_ENDERGENIC, "endergenicRfHolding", rfToHoldPearl,
                "The amount of RF that is consumed every tick to hold the endergenic pearl").getInt();
        rfOutput = cfg.get(CATEGORY_ENDERGENIC, "endergenicRfOutput", rfOutput,
                "The amount of RF per tick that this generator can give from its internal buffer to adjacent blocks").getInt();
        goodParticleCount = cfg.get(CATEGORY_ENDERGENIC, "endergenicGoodParticles", goodParticleCount,
                "The amount of particles to spawn whenever energy is generated (use 0 to disable)").getInt();
        badParticleCount = cfg.get(CATEGORY_ENDERGENIC, "endergenicBadParticles", badParticleCount,
                "The amount of particles to spawn whenever a pearl is lost (use 0 to disable)").getInt();
        logEndergenic = cfg.get(CATEGORY_ENDERGENIC, "endergenicLogging", logEndergenic,
                "If true dump a lot of logging information about the generators. Useful for debugging.").getBoolean();
    }
}
