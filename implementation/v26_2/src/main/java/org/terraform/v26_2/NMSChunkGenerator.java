package org.terraform.v26_2;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.FeatureCountTracker;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jetbrains.annotations.NotNull;
import org.terraform.coregen.bukkit.TerraformGenerator;
import org.terraform.data.CoordPair;
import org.terraform.data.MegaChunk;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.structure.SingleMegaChunkStructurePopulator;
import org.terraform.structure.StructureLocator;
import org.terraform.structure.StructurePopulator;
import org.terraform.structure.StructureRegistry;
import org.terraform.structure.VanillaStructurePopulator;
import org.terraform.structure.monument.MonumentPopulator;
import org.terraform.structure.pillager.mansion.MansionPopulator;
import org.terraform.structure.small.buriedtreasure.BuriedTreasurePopulator;
import org.terraform.structure.stronghold.StrongholdPopulator;
import org.terraform.structure.trialchamber.TrialChamberPopulator;
import org.terraform.utils.version.TerraformFieldHandler;
import org.terraform.utils.version.TerraformMethodHandler;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class NMSChunkGenerator extends ChunkGenerator {
    private final @NotNull ChunkGenerator delegate;
    private final @NotNull TerraformWorld tw;
    private final @NotNull MapRenderWorldProviderBiome mapRendererBS;
    private final @NotNull TerraformWorldProviderBiome twBS;
    private final @NotNull TerraformMethodHandler tryGenerateStructure;
    private final ArrayList<Identifier> possibleStructureSets = new ArrayList<>();
    private final @NotNull TerraformMethodHandler getWriteableArea;
    private final @NotNull Supplier<?> featuresPerStep;

    public NMSChunkGenerator(String worldName, long seed, @NotNull ChunkGenerator delegate)
            throws NoSuchMethodException, SecurityException, NoSuchFieldException, IllegalAccessException {
        super(delegate.getBiomeSource(), delegate.generationSettingsGetter);
        tw = TerraformWorld.get(worldName, seed);
        this.delegate = delegate;

        mapRendererBS = new MapRenderWorldProviderBiome(tw, delegate.getBiomeSource());
        twBS = new TerraformWorldProviderBiome(tw, delegate.getBiomeSource());

        featuresPerStep = (Supplier<?>) new TerraformFieldHandler(ChunkGenerator.class, "featuresPerStep", "c")
                .field.get(delegate);
        getWriteableArea = new TerraformMethodHandler(
                ChunkGenerator.class,
                new String[]{"getWritableArea", "a"},
                ChunkAccess.class
        );

        for (StructurePopulator pop : StructureRegistry.getAllPopulators()) {
            if (pop instanceof VanillaStructurePopulator vsp) {
                possibleStructureSets.add(Identifier.parse(vsp.structureRegistryKey));
            }
        }

        tryGenerateStructure = new TerraformMethodHandler(
                ChunkGenerator.class,
                new String[]{"tryGenerateStructure", "a"},
                StructureSet.StructureSelectionEntry.class,
                StructureManager.class,
                RegistryAccess.class,
                RandomState.class,
                StructureTemplateManager.class,
                long.class,
                ChunkAccess.class,
                ChunkPos.class,
                SectionPos.class,
                ResourceKey.class
        );
    }

    @Override
    public @NotNull BiomeSource getBiomeSource() {
        return mapRendererBS;
    }

    public @NotNull TerraformWorld getTerraformWorld() {
        return tw;
    }

    @Override
    protected @NotNull MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(null);
    }

    @Override
    public @NotNull CompletableFuture<ChunkAccess> createBiomes(
            RandomState randomState,
            Blender blender,
            StructureManager structureManager,
            @NotNull ChunkAccess chunkAccess) {
        // Biomes are populated in applyCarvers. Avoid scheduling a no-op task for
        // every generated chunk while players move through new terrain.
        return CompletableFuture.completedFuture(chunkAccess);
    }

    @Override
    public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
            ServerLevel serverLevel,
            @NotNull HolderSet<Structure> holderSet,
            @NotNull BlockPos blockPos,
            int radius,
            boolean skipKnownStructures) {
        int pX = blockPos.getX();
        int pZ = blockPos.getZ();

        for (Holder<Structure> holder : holderSet) {
            Structure feature = holder.value();
            TerraformGeneratorPlugin.logger.info("Vanilla locate for " + feature.getClass().getName() + " invoked.");

            if (holder.value().getClass() == StrongholdStructure.class) {
                int[] coords = new StrongholdPopulator().getNearestFeature(tw, pX, pZ);
                return new Pair<>(new BlockPos(coords[0], 20, coords[1]), holder);
            }
            else if (!TConfig.c.DEVSTUFF_VANILLA_LOCATE_DISABLE) {
                if (holder.value().getClass() == OceanMonumentStructure.class) {
                    int[] coords = StructureLocator.locateSingleMegaChunkStructure(
                            tw,
                            pX,
                            pZ,
                            new MonumentPopulator(),
                            TConfig.c.DEVSTUFF_VANILLA_LOCATE_TIMEOUTMILLIS
                    );
                    return new Pair<>(new BlockPos(coords[0], 50, coords[1]), holder);
                }
                else if (holder.value().getClass() == WoodlandMansionStructure.class) {
                    int[] coords = StructureLocator.locateSingleMegaChunkStructure(
                            tw,
                            pX,
                            pZ,
                            new MansionPopulator(),
                            TConfig.c.DEVSTUFF_VANILLA_LOCATE_TIMEOUTMILLIS
                    );
                    return new Pair<>(new BlockPos(coords[0], 50, coords[1]), holder);
                }
                else if (holder.value() instanceof JigsawStructure
                         && MinecraftServer.getServer()
                                           .registryAccess()
                                           .lookup(Registries.STRUCTURE)
                                           .orElseThrow()
                                           .getValue(Identifier.parse("trial_chambers")) == holder.value()) {
                    int[] coords = StructureLocator.locateSingleMegaChunkStructure(
                            tw,
                            pX,
                            pZ,
                            new TrialChamberPopulator(),
                            TConfig.c.DEVSTUFF_VANILLA_LOCATE_TIMEOUTMILLIS
                    );
                    return new Pair<>(new BlockPos(coords[0], 50, coords[1]), holder);
                }
                else if (holder.value().getClass() == BuriedTreasureStructure.class) {
                    int[] coords = StructureLocator.locateMultiMegaChunkStructure(
                            tw,
                            new MegaChunk(pX, 0, pZ),
                            new BuriedTreasurePopulator(),
                            TConfig.c.DEVSTUFF_VANILLA_LOCATE_TIMEOUTMILLIS
                    );
                    if (coords == null) {
                        return null;
                    }
                    return new Pair<>(new BlockPos(coords[0], 50, coords[1]), holder);
                }
            }
        }
        return null;
    }

    @Override
    public void applyBiomeDecoration(
            WorldGenLevel worldGenLevel,
            ChunkAccess chunkAccess,
            StructureManager structureManager) {
        // Paper can execute world population on async chunk workers. Iterating the
        // live CraftWorld populator list can throw ConcurrentModificationException
        // if another plugin changes it concurrently, which Paper treats as a fatal
        // chunk-system failure. Run the same population path against a stable copy.
        applyBukkitPopulators(worldGenLevel, chunkAccess);

        // Required for Terraform's VanillaStructurePopulator integration.
        addVanillaDecorations(worldGenLevel, chunkAccess, structureManager);
    }

    private void applyBukkitPopulators(
            @NotNull WorldGenLevel worldGenLevel,
            @NotNull ChunkAccess chunkAccess) {
        org.bukkit.World world = worldGenLevel.getMinecraftWorld().getWorld();
        org.bukkit.generator.BlockPopulator[] populators =
                world.getPopulators().toArray(org.bukkit.generator.BlockPopulator[]::new);
        if (populators.length == 0) {
            return;
        }

        org.bukkit.craftbukkit.generator.CraftLimitedRegion limitedRegion =
                new org.bukkit.craftbukkit.generator.CraftLimitedRegion(worldGenLevel, chunkAccess.getPos());
        int chunkX = chunkAccess.getPos().x();
        int chunkZ = chunkAccess.getPos().z();

        try {
            for (org.bukkit.generator.BlockPopulator populator : populators) {
                if (populator == null) {
                    continue;
                }

                WorldgenRandom seededRandom = new WorldgenRandom(new LegacyRandomSource(worldGenLevel.getSeed()));
                seededRandom.setDecorationSeed(worldGenLevel.getSeed(), chunkX, chunkZ);
                populator.populate(
                        world,
                        new org.bukkit.craftbukkit.util.RandomSourceWrapper.RandomWrapper(seededRandom),
                        chunkX,
                        chunkZ,
                        limitedRegion
                );
            }
            limitedRegion.saveEntities();
        }
        finally {
            limitedRegion.breakLink();
        }
    }

    @Override
    public void addVanillaDecorations(
            WorldGenLevel worldGenLevel,
            ChunkAccess chunkAccess,
            StructureManager structureManager) {
        ChunkPos chunkPos = chunkAccess.getPos();
        if (SharedConstants.debugVoidTerrain(chunkPos)) {
            return;
        }

        SectionPos sectionPos = SectionPos.of(chunkPos, worldGenLevel.getMinSectionY());
        BlockPos blockPos = sectionPos.origin();
        Registry<Structure> structureRegistry =
                worldGenLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<Integer, List<Structure>> structuresByStep = structureRegistry.stream().collect(
                Collectors.groupingBy(structure -> structure.step().ordinal())
        );

        @SuppressWarnings("unchecked")
        List<FeatureSorter.StepFeatureData> featureSteps =
                (List<FeatureSorter.StepFeatureData>) featuresPerStep.get();
        WorldgenRandom seededRandom =
                new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = seededRandom.setDecorationSeed(
                worldGenLevel.getSeed(),
                blockPos.getX(),
                blockPos.getZ()
        );

        try {
            int stepCount = Math.max(GenerationStep.Decoration.values().length, featureSteps.size());
            for (int step = 0; step < stepCount; step++) {
                int featureIndex = 0;
                if (structureManager.shouldGenerateStructures()) {
                    for (Structure structure : structuresByStep.getOrDefault(step, Collections.emptyList())) {
                        seededRandom.setFeatureSeed(decorationSeed, featureIndex, step);
                        Supplier<String> supplier = () -> structureRegistry.getResourceKey(structure)
                                                                       .map(Object::toString)
                                                                       .orElseGet(structure::toString);

                        try {
                            worldGenLevel.setCurrentlyGenerating(supplier);
                            structureManager.startsForStructure(sectionPos, structure).forEach(structureStart -> {
                                try {
                                    structureStart.placeInChunk(
                                            worldGenLevel,
                                            structureManager,
                                            this,
                                            seededRandom,
                                            (BoundingBox) getWriteableArea.method.invoke(null, chunkAccess),
                                            chunkPos
                                    );
                                }
                                catch (IllegalAccessException | InvocationTargetException e) {
                                    CrashReport crashReport = CrashReport.forThrowable(e, "TerraformGenerator");
                                    throw new ReportedException(crashReport);
                                }
                            });
                        }
                        catch (Exception e) {
                            CrashReport crashReport = CrashReport.forThrowable(e, "Feature placement");
                            CrashReportCategory details = crashReport.addCategory("Feature");
                            details.setDetail("Description", supplier::get);
                            throw new ReportedException(crashReport);
                        }
                        featureIndex++;
                    }
                }
            }

            worldGenLevel.setCurrentlyGenerating(null);
            if (SharedConstants.DEBUG_FEATURE_COUNT) {
                FeatureCountTracker.chunkDecorated(worldGenLevel.getLevel());
            }
        }
        catch (Exception e) {
            CrashReport crashReport = CrashReport.forThrowable(e, "Biome decoration");
            crashReport.addCategory("Generation")
                       .setDetail("CenterX", chunkPos.x())
                       .setDetail("CenterZ", chunkPos.z())
                       .setDetail("Decoration Seed", decorationSeed);
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public void applyCarvers(
            WorldGenRegion worldGenRegion,
            long seed,
            RandomState randomState,
            BiomeManager biomeManager,
            StructureManager structureManager,
            @NotNull ChunkAccess chunkAccess) {
        // Terraform calculates the actual biome layout here.
        chunkAccess.fillBiomesFromNoise(this.twBS, null);
        delegate.applyCarvers(worldGenRegion, seed, randomState, biomeManager, structureManager, chunkAccess);
    }

    @Override
    public int getGenDepth() {
        return delegate.getGenDepth();
    }

    @Override
    public void createStructures(
            RegistryAccess registryAccess,
            @NotNull ChunkGeneratorStructureState structureState,
            StructureManager structureManager,
            @NotNull ChunkAccess chunkAccess,
            StructureTemplateManager structureTemplateManager,
            ResourceKey<Level> resourceKey) {
        ChunkPos chunkPos = chunkAccess.getPos();
        SectionPos sectionPos = SectionPos.bottomOf(chunkAccess);
        RandomState randomState = structureState.randomState();
        MegaChunk megaChunk = new MegaChunk(chunkPos.x(), chunkPos.z());
        SingleMegaChunkStructurePopulator[] populators =
                StructureRegistry.getLargeStructureForMegaChunk(tw, megaChunk);
        CoordPair centerCoords = megaChunk.getCenterBiomeSectionChunkCoords();
        if (populators == null) {
            return;
        }

        for (SingleMegaChunkStructurePopulator populator : populators) {
            if (!(populator instanceof VanillaStructurePopulator vanillaPopulator)) {
                continue;
            }

            possibleStructureSets.stream()
                    .filter(resourceLocation -> vanillaPopulator.structureRegistryKey.equals(resourceLocation.getPath()))
                    .map(resourceLocation -> MinecraftServer.getServer()
                                                    .registryAccess()
                                                    .lookup(Registries.STRUCTURE_SET)
                                                    .orElseThrow()
                                                    .getValue(resourceLocation))
                    .forEach(structureSet -> {
                        List<StructureSet.StructureSelectionEntry> structures = structureSet.structures();
                        if (centerCoords.x() == chunkPos.x() && centerCoords.z() == chunkPos.z()) {
                            try {
                                Object result = tryGenerateStructure.method.invoke(
                                        this,
                                        structures.getFirst(),
                                        structureManager,
                                        registryAccess,
                                        randomState,
                                        structureTemplateManager,
                                        structureState.getLevelSeed(),
                                        chunkAccess,
                                        chunkPos,
                                        sectionPos,
                                        resourceKey
                                );
                                TerraformGeneratorPlugin.logger.info(
                                        chunkPos.x() + "," + chunkPos.z()
                                        + " will spawn a vanilla structure, with tryGenerateStructure == " + result
                                );
                            }
                            catch (Throwable throwable) {
                                TerraformGeneratorPlugin.logger.info(
                                        chunkPos.x() + "," + chunkPos.z()
                                        + " Failed to generate a vanilla structure"
                                );
                                TerraformGeneratorPlugin.logger.stackTrace(throwable);
                            }
                        }
                    });
        }
    }

    @Override
    public void createReferences(WorldGenLevel worldGenLevel, StructureManager manager, ChunkAccess chunkAccess) {
        delegate.createReferences(worldGenLevel, manager, chunkAccess);
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor levelHeightAccessor) {
        return 64;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender,
            RandomState randomState,
            StructureManager structureManager,
            ChunkAccess chunkAccess) {
        return delegate.fillFromNoise(blender, randomState, structureManager, chunkAccess);
    }

    @Override
    public void buildSurface(
            WorldGenRegion worldGenRegion,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunkAccess) {
        delegate.buildSurface(worldGenRegion, structureManager, randomState, chunkAccess);
    }

    @Override
    public NoiseColumn getBaseColumn(
            int x,
            int z,
            LevelHeightAccessor levelHeightAccessor,
            RandomState randomState) {
        return delegate.getBaseColumn(x, z, levelHeightAccessor, randomState);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        delegate.spawnOriginalMobs(worldGenRegion);
    }

    @Override
    public int getSeaLevel() {
        return TerraformGenerator.seaLevel;
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public int getFirstFreeHeight(
            int x,
            int z,
            Heightmap.Types heightmapType,
            LevelHeightAccessor levelHeightAccessor,
            RandomState randomState) {
        return delegate.getFirstFreeHeight(x, z, heightmapType, levelHeightAccessor, randomState);
    }

    @Override
    public int getFirstOccupiedHeight(
            int x,
            int z,
            Heightmap.Types heightmapType,
            LevelHeightAccessor levelHeightAccessor,
            RandomState randomState) {
        return delegate.getFirstOccupiedHeight(x, z, heightmapType, levelHeightAccessor, randomState) - 1;
    }

    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.Types heightmapType,
            LevelHeightAccessor levelHeightAccessor,
            RandomState randomState) {
        // Preserve existing behavior for compatibility with Terraform's Bukkit generator.
        return 100;
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos blockPos) {
    }
}
