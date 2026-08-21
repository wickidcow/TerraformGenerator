package org.terraform.v26_2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.entity.EntityTypes;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.jetbrains.annotations.NotNull;
import org.terraform.biome.custombiomes.CustomBiomeType;
import org.terraform.coregen.NaturalSpawnType;
import org.terraform.coregen.TerraLootTable;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.coregen.populatordata.PopulatorDataICABiomeWriterAbstract;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.utils.version.TerraformFieldHandler;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

public class PopulatorDataICA extends PopulatorDataICABiomeWriterAbstract {
    private final PopulatorDataAbstract parent;
    private final ChunkAccess ica;
    private final int chunkX;
    private final int chunkZ;
    private final ServerLevel ws;
    private final TerraformWorld tw;

    public PopulatorDataICA(
            PopulatorDataAbstract parent,
            TerraformWorld tw,
            ServerLevel ws,
            ChunkAccess ica,
            int chunkX,
            int chunkZ) {
        this.ica = ica;
        this.parent = parent;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.ws = ws;
        this.tw = tw;
    }

    public @NotNull Material getType(int x, int y, int z) {
        BlockState blockState = ica.getBlockState(new BlockPos(x, y, z));
        return CraftBlockData.createData(blockState).getMaterial();
    }

    public BlockData getBlockData(int x, int y, int z) {
        BlockState blockState = ica.getBlockState(new BlockPos(x, y, z));
        return CraftBlockData.createData(blockState);
    }

    @Override
    public void setBiome(
            int rawX,
            int rawY,
            int rawZ,
            CustomBiomeType customBiomeType,
            org.bukkit.block.Biome fallback) {
        Registry<Biome> biomeRegistry = CustomBiomeHandler.getBiomeRegistry();
        Holder<Biome> targetBiome;
        if (customBiomeType == CustomBiomeType.NONE) {
            targetBiome = CraftBiome.bukkitToMinecraftHolder(fallback);
        }
        else {
            ResourceKey<Biome> resourceKey =
                    CustomBiomeHandler.terraformGenBiomeRegistry.get(customBiomeType);
            Optional<Holder.Reference<Biome>> holder = biomeRegistry.get(resourceKey);
            if (holder.isEmpty()) {
                TerraformGeneratorPlugin.logger.error("Custom biome was not found in the vanilla registry!");
                targetBiome = CraftBiome.bukkitToMinecraftHolder(fallback);
            }
            else {
                targetBiome = holder.get();
            }
        }

        ica.setNoiseBiome(rawX >> 2, rawY >> 2, rawZ >> 2, targetBiome);
    }

    @Override
    public void setBiome(int rawX, int rawY, int rawZ, org.bukkit.block.Biome biome) {
        ica.setNoiseBiome(
                rawX >> 2,
                rawY >> 2,
                rawZ >> 2,
                Objects.requireNonNull(CraftBiome.bukkitToMinecraftHolder(biome))
        );
    }

    @Override
    public void setType(int x, int y, int z, @NotNull Material type) {
        ica.setBlockState(
                new BlockPos(x, y, z),
                ((CraftBlockData) org.bukkit.Bukkit.createBlockData(type)).getState(),
                3
        );
    }

    @Override
    public void setBlockData(int x, int y, int z, @NotNull BlockData data) {
        ica.setBlockState(new BlockPos(x, y, z), ((CraftBlockData) data).getState(), 3);
    }

    public org.bukkit.block.Biome getBiome(int rawX, int rawZ) {
        return parent.getBiome(rawX, rawZ);
    }

    @Override
    public int getChunkX() {
        return chunkX;
    }

    @Override
    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    public void addEntity(int rawX, int rawY, int rawZ, org.bukkit.entity.EntityType type) {
        parent.addEntity(rawX, rawY, rawZ, type);
    }

    @Override
    public void setSpawner(int rawX, int rawY, int rawZ, org.bukkit.entity.EntityType type) {
        parent.setSpawner(rawX, rawY, rawZ, type);
    }

    @Override
    public void lootTableChest(int x, int y, int z, TerraLootTable table) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockEntity blockEntity = ica.getBlockEntity(pos);
        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(LootTableTranslator.translationMap.get(table));
        }
        else if (blockEntity instanceof BrushableBlockEntity brushableBlockEntity) {
            brushableBlockEntity.setLootTable(
                    LootTableTranslator.translationMap.get(table),
                    tw.getHashedRand(x, y, z).nextLong()
            );
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void registerNaturalSpawns(
            @NotNull NaturalSpawnType type,
            int x0,
            int y0,
            int z0,
            int x1,
            int y1,
            int z1) {
        ResourceKey<Structure> structureKey = switch (type) {
            case GUARDIAN -> BuiltinStructures.OCEAN_MONUMENT;
            case PILLAGER -> BuiltinStructures.PILLAGER_OUTPOST;
            case WITCH -> BuiltinStructures.SWAMP_HUT;
        };

        Registry<Structure> structureRegistry =
                MinecraftServer.getServer().registryAccess().lookup(Registries.STRUCTURE).orElseThrow();
        Structure structure = structureRegistry.getValue(structureKey);

        try {
            Class<OceanMonumentPieces.MonumentBuilding> monumentClass =
                    OceanMonumentPieces.MonumentBuilding.class;
            StructurePiece boundPiece = monumentClass.getConstructor(
                    RandomSource.class,
                    int.class,
                    int.class,
                    Direction.class
            ).newInstance(RandomSource.create(), x0, z0, Direction.DOWN);

            PiecesContainer pieces = new PiecesContainer(List.of(boundPiece));
            StructureStart start = new StructureStart(
                    structure,
                    new ChunkPos(chunkX, chunkZ),
                    0,
                    pieces
            );

            var cachedBoundingBox = new TerraformFieldHandler(
                    StructureStart.class,
                    "cachedBoundingBox",
                    "h"
            );
            cachedBoundingBox.field.set(start, new BoundingBox(x0, y0, z0, x1, y1, z1));

            ica.setStartForStructure(structure, start);
            ica.addReferenceForStructure(structure, new ChunkPos(chunkX, chunkZ).pack());
        }
        catch (NoSuchMethodException |
               InstantiationException |
               InvocationTargetException |
               NoSuchFieldException |
               IllegalArgumentException |
               IllegalAccessException e) {
            TerraformGeneratorPlugin.logger.stackTrace(e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void spawnMinecartWithChest(
            int x,
            int y,
            int z,
            TerraLootTable table,
            @NotNull Random random) {
        MinecartChest minecartChest = (MinecartChest) EntityTypes.CHEST_MINECART.create(
                ws.getMinecraftWorld(),
                EntitySpawnReason.CHUNK_GENERATION
        );

        if (minecartChest != null) {
            minecartChest.setPos((float) x + 0.5F, (float) y + 0.5F, (float) z + 0.5F);
            minecartChest.setLootTable(LootTableTranslator.translationMap.get(table), random.nextLong());
            ws.addFreshEntity(minecartChest);
        }
    }

    @Override
    public @NotNull TerraformWorld getTerraformWorld() {
        return tw;
    }

    @Override
    public boolean isInBound(int x, int y, int z) {
        // Coordinates passed to PopulatorData are absolute block coordinates.
        // Convert them to chunk coordinates before comparing with this ICA.
        return (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }
}
