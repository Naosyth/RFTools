package mcjty.rftools.dimension.world;

import cpw.mods.fml.common.Optional;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ivorius.reccomplex.dimensions.DimensionDictionary;
import mcjty.lib.varia.Logging;
import mcjty.rftools.api.dimension.RFToolsWorldProvider;
import mcjty.rftools.blocks.dimlets.DimletConfiguration;
import mcjty.rftools.dimension.DimensionInformation;
import mcjty.rftools.dimension.DimensionStorage;
import mcjty.rftools.dimension.RfToolsDimensionManager;
import mcjty.rftools.dimension.description.WeatherDescriptor;
import mcjty.rftools.dimension.network.PacketGetDimensionEnergy;
import mcjty.rftools.dimension.world.types.ControllerType;
import mcjty.rftools.dimension.world.types.SkyType;
import mcjty.rftools.dimension.world.types.StructureType;
import mcjty.rftools.items.dimlets.types.Patreons;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.DimensionManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Optional.InterfaceList(@Optional.Interface(iface = "ivorius.reccomplex.dimensions.DimensionDictionary$Handler", modid = "reccomplex"))
public class GenericWorldProvider extends WorldProvider implements DimensionDictionary.Handler, RFToolsWorldProvider {

    public static final String RFTOOLS_DIMENSION = "rftools dimension";

    private DimensionInformation dimensionInformation;
    private DimensionStorage storage;
    private long seed;
    private Set<String> dimensionTypes = null;  // Used for Recurrent Complex support

    private long calculateSeed(long seed, int dim) {
        return dim * 13L + seed;
    }

    @Override
    public long getSeed() {
        if (dimensionInformation == null || dimensionInformation.getWorldVersion() < DimensionInformation.VERSION_CORRECTSEED) {
            return super.getSeed();
        } else {
            return seed;
        }
    }

    private DimensionInformation getDimensionInformation() {
        if (dimensionInformation == null) {
            int dim = worldObj.provider.dimensionId;
            dimensionInformation = RfToolsDimensionManager.getDimensionManager(worldObj).getDimensionInformation(dim);
            if (dimensionInformation == null) {
                Logging.log("Dimension information for dimension " + dim + " is missing!");
            } else {
                setSeed(dim);
                setupProviderInfo();
            }
        }
        return dimensionInformation;
    }

    @Override
    @Optional.Method(modid = "reccomplex")
    public Set<String> getDimensionTypes() {
        getDimensionInformation();
        if (dimensionInformation == null) {
            return Collections.EMPTY_SET;
        }
        if (dimensionTypes == null) {
            dimensionTypes = new HashSet<String>();
            dimensionTypes.add(DimensionDictionary.INFINITE);
            dimensionTypes.add("RFTOOLS_DIMENSION");
            // @todo temporary. This should probably be in the TerrainType enum.
            switch (dimensionInformation.getTerrainType()) {
                case TERRAIN_VOID:
                case TERRAIN_ISLAND:
                case TERRAIN_ISLANDS:
                case TERRAIN_CHAOTIC:
                case TERRAIN_PLATEAUS:
                case TERRAIN_GRID:
                    dimensionTypes.add(DimensionDictionary.NO_TOP_LIMIT);
                    dimensionTypes.add(DimensionDictionary.NO_BOTTOM_LIMIT);
                    break;
                case TERRAIN_FLAT:
                case TERRAIN_AMPLIFIED:
                case TERRAIN_NORMAL:
                case TERRAIN_NEARLANDS:
                    dimensionTypes.add(DimensionDictionary.NO_TOP_LIMIT);
                    dimensionTypes.add(DimensionDictionary.BOTTOM_LIMIT);
                    break;
                case TERRAIN_CAVERN_OLD:
                    dimensionTypes.add(DimensionDictionary.BOTTOM_LIMIT);
                    dimensionTypes.add(DimensionDictionary.TOP_LIMIT);
                    break;
                case TERRAIN_CAVERN:
                case TERRAIN_LOW_CAVERN:
                case TERRAIN_FLOODED_CAVERN:
                    dimensionTypes.add(DimensionDictionary.BOTTOM_LIMIT);
                    dimensionTypes.add(DimensionDictionary.NO_TOP_LIMIT);
                    break;
            }
            if (dimensionInformation.hasStructureType(StructureType.STRUCTURE_RECURRENTCOMPLEX)) {
                Collections.addAll(dimensionTypes, dimensionInformation.getDimensionTypes());
            }
        }
        return dimensionTypes;
    }

    private void setSeed(int dim) {
        if (dimensionInformation == null) {
            if (worldObj == null) {
                return;
            }
            dimensionInformation = RfToolsDimensionManager.getDimensionManager(worldObj).getDimensionInformation(dim);
            if (dimensionInformation == null) {
                Logging.log("Error: setSeed() called with null diminfo. Error ignored!");
                return;
            }
        }
        long forcedSeed = dimensionInformation.getForcedDimensionSeed();
        if (forcedSeed != 0) {
            Logging.log("Forced seed for dimension " + dim + ": " + forcedSeed);
            seed = forcedSeed;
        } else {
            long baseSeed = dimensionInformation.getBaseSeed();
            if (baseSeed != 0) {
                seed = calculateSeed(baseSeed, dim) ;
            } else {
                seed = calculateSeed(worldObj.getSeed(), dim) ;
            }
        }
    }

    @Override
    public void registerWorldChunkManager() {
        getDimensionInformation();
        storage = DimensionStorage.getDimensionStorage(worldObj);

        setupProviderInfo();
    }

    private void setupProviderInfo() {
        if (dimensionInformation != null) {
            ControllerType type = dimensionInformation.getControllerType();
            if (type == ControllerType.CONTROLLER_SINGLE) {
                worldChunkMgr = new SingleBiomeWorldChunkManager(worldObj, worldObj.getSeed(), terrainType);
            } else if (type == ControllerType.CONTROLLER_DEFAULT) {
                worldChunkMgr = new WorldChunkManager(seed, worldObj.getWorldInfo().getTerrainType());
            } else {
                GenericWorldChunkManager.hackyDimensionInformation = dimensionInformation;      // Hack to get the dimension information in the superclass.
                worldChunkMgr = new GenericWorldChunkManager(seed, worldObj.getWorldInfo().getTerrainType(), dimensionInformation);
            }
        } else {
            worldChunkMgr = new WorldChunkManager(seed, worldObj.getWorldInfo().getTerrainType());
        }

        if (dimensionInformation != null) {
            hasNoSky = !dimensionInformation.getTerrainType().hasSky();

            if (worldObj.isRemote) {
                // Only on client!
                SkyType skyType = dimensionInformation.getSkyDescriptor().getSkyType();
                if (hasNoSky) {
                    SkyRenderer.registerNoSky(this);
                } else if (skyType == SkyType.SKY_ENDER) {
                    SkyRenderer.registerEnderSky(this);
                } else if (skyType == SkyType.SKY_INFERNO || skyType == SkyType.SKY_STARS1 || skyType == SkyType.SKY_STARS2 || skyType == SkyType.SKY_STARS3) {
                    SkyRenderer.registerSkybox(this, skyType);
                } else {
                    SkyRenderer.registerSky(this, dimensionInformation);
                }

                if (dimensionInformation.getSkyDescriptor().isCloudColorGiven() || dimensionInformation.isPatreonBitSet(Patreons.PATREON_KENNEY)) {
                    SkyRenderer.registerCloudRenderer(this, dimensionInformation);
                }
            }
        }
    }

    public static WorldProvider getProviderForDimension(int id) {
        return DimensionManager.createProviderFor(id);
    }

    @Override
    public double getHorizon() {
        getDimensionInformation();
        if (dimensionInformation != null && dimensionInformation.getTerrainType().hasNoHorizon()) {
            return 0;
        } else {
            return super.getHorizon();
        }
    }

    @Override
    public boolean isSurfaceWorld() {
        getDimensionInformation();
        if (dimensionInformation == null) {
            return super.isSurfaceWorld();
        }
        return dimensionInformation.getTerrainType().hasSky();
    }

    @Override
    public String getDimensionName() {
        return RFTOOLS_DIMENSION;
    }

    @Override
    public String getWelcomeMessage() {
        return "Entering the rftools dimension!";
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player) {
        getDimensionInformation();
        if (DimletConfiguration.respawnSameDim || (dimensionInformation != null && dimensionInformation.isRespawnHere())) {
            DimensionStorage dimensionStorage = DimensionStorage.getDimensionStorage(worldObj);
            int power = dimensionStorage.getEnergyLevel(dimensionId);
            if (power < 1000) {
                return DimletConfiguration.spawnDimension;
            } else {
                return dimensionId;
            }
        }
        return DimletConfiguration.spawnDimension;
    }

    @Override
    public IChunkProvider createChunkGenerator() {
        int dim = worldObj.provider.dimensionId;
        setSeed(dim);
        return new GenericChunkProvider(worldObj, seed);
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(int x, int z) {
        return super.getBiomeGenForCoords(x, z);
    }

    @Override
    public int getActualHeight() {
        return 256;
    }

    private static long lastFogTime = 0;

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getFogColor(float angle, float dt) {
        int dim = worldObj.provider.dimensionId;
        if (System.currentTimeMillis() - lastFogTime > 1000) {
            lastFogTime = System.currentTimeMillis();
            RFToolsMessages.INSTANCE.sendToServer(new PacketGetDimensionEnergy(dim));
        }

        float factor = calculatePowerBlackout(dim);
        getDimensionInformation();

        float r;
        float g;
        float b;
        if (dimensionInformation == null) {
            r = g = b = 1.0f;
        } else {
            r = dimensionInformation.getSkyDescriptor().getFogColorFactorR() * factor;
            g = dimensionInformation.getSkyDescriptor().getFogColorFactorG() * factor;
            b = dimensionInformation.getSkyDescriptor().getFogColorFactorB() * factor;
        }

        Vec3 color = super.getFogColor(angle, dt);
        return Vec3.createVectorHelper(color.xCoord * r, color.yCoord * g, color.zCoord * b);
    }

    private static long lastTime = 0;

    @Override
    @SideOnly(Side.CLIENT)
    public Vec3 getSkyColor(Entity cameraEntity, float partialTicks) {
        int dim = worldObj.provider.dimensionId;
        if (System.currentTimeMillis() - lastTime > 1000) {
            lastTime = System.currentTimeMillis();
            RFToolsMessages.INSTANCE.sendToServer(new PacketGetDimensionEnergy(dim));
        }

        float factor = calculatePowerBlackout(dim);
        getDimensionInformation();

        float r;
        float g;
        float b;
        if (dimensionInformation == null) {
            r = g = b = 1.0f;
        } else {
            r = dimensionInformation.getSkyDescriptor().getSkyColorFactorR() * factor;
            g = dimensionInformation.getSkyDescriptor().getSkyColorFactorG() * factor;
            b = dimensionInformation.getSkyDescriptor().getSkyColorFactorB() * factor;
        }

        Vec3 skyColor = super.getSkyColor(cameraEntity, partialTicks);
        return Vec3.createVectorHelper(skyColor.xCoord * r, skyColor.yCoord * g, skyColor.zCoord * b);
    }

    private float calculatePowerBlackout(int dim) {
        float factor = 1.0f;
        int power = storage.getEnergyLevel(dim);
        if (power < DimletConfiguration.DIMPOWER_WARN3) {
            factor = ((float) power) / DimletConfiguration.DIMPOWER_WARN3 * 0.2f;
        } else  if (power < DimletConfiguration.DIMPOWER_WARN2) {
            factor = (float) (power - DimletConfiguration.DIMPOWER_WARN3) / (DimletConfiguration.DIMPOWER_WARN2 - DimletConfiguration.DIMPOWER_WARN3) * 0.3f + 0.2f;
        } else if (power < DimletConfiguration.DIMPOWER_WARN1) {
            factor = (float) (power - DimletConfiguration.DIMPOWER_WARN2) / (DimletConfiguration.DIMPOWER_WARN1 - DimletConfiguration.DIMPOWER_WARN2) * 0.3f + 0.5f;
        } else if (power < DimletConfiguration.DIMPOWER_WARN0) {
            factor = (float) (power - DimletConfiguration.DIMPOWER_WARN1) / (DimletConfiguration.DIMPOWER_WARN0 - DimletConfiguration.DIMPOWER_WARN1) * 0.2f + 0.8f;
        }
        return factor;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getSunBrightness(float par1) {
        getDimensionInformation();
        if (dimensionInformation == null) {
            return super.getSunBrightness(par1);
        }
        int dim = worldObj.provider.dimensionId;
        float factor = calculatePowerBlackout(dim);
        return super.getSunBrightness(par1) * dimensionInformation.getSkyDescriptor().getSunBrightnessFactor() * factor;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public float getStarBrightness(float par1) {
        getDimensionInformation();
        if (dimensionInformation == null) {
            return super.getStarBrightness(par1);
        }
        return super.getStarBrightness(par1) * dimensionInformation.getSkyDescriptor().getStarBrightnessFactor();
    }

    @Override
    public void updateWeather() {
        super.updateWeather();
        if (!worldObj.isRemote) {
            getDimensionInformation();
            if (dimensionInformation != null) {
                WeatherDescriptor descriptor = dimensionInformation.getWeatherDescriptor();
                float rs = descriptor.getRainStrength();
                if (rs > -0.5f) {
                    worldObj.rainingStrength = rs;
                    if (Math.abs(worldObj.rainingStrength) < 0.001) {
                        worldObj.prevRainingStrength = 0;
                        worldObj.rainingStrength = 0;
                        worldObj.getWorldInfo().setRaining(false);
                    }
                }

                float ts = descriptor.getThunderStrength();
                if (ts > -0.5f) {
                    worldObj.thunderingStrength = ts;
                    if (Math.abs(worldObj.thunderingStrength) < 0.001) {
                        worldObj.prevThunderingStrength = 0;
                        worldObj.thunderingStrength = 0;
                        worldObj.getWorldInfo().setThundering(false);
                    }
                }
            }
        }
    }

    @Override
    public float calculateCelestialAngle(long time, float dt) {
        getDimensionInformation();
        if (dimensionInformation == null) {
            return super.calculateCelestialAngle(time, dt);
        }

        if (!dimensionInformation.getTerrainType().hasSky()) {
            return 0.5F;
        }

        if (dimensionInformation.getCelestialAngle() == null) {
            if (dimensionInformation.getTimeSpeed() == null) {
                return super.calculateCelestialAngle(time, dt);
            } else {
                return super.calculateCelestialAngle((long) (time * dimensionInformation.getTimeSpeed()), dt);
            }
        } else {
            return dimensionInformation.getCelestialAngle();
        }
    }

    //------------------------ RFToolsWorldProvider


    @Override
    public int getCurrentRF() {
        DimensionStorage dimensionStorage = DimensionStorage.getDimensionStorage(worldObj);
        return dimensionStorage.getEnergyLevel(dimensionId);
    }
}
