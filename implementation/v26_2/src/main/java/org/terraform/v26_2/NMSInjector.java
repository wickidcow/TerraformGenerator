package org.terraform.v26_2;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Beehive;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlockEntityState;
import org.bukkit.craftbukkit.generator.CraftLimitedRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.terraform.coregen.BlockDataFixerAbstract;
import org.terraform.coregen.NMSInjectorAbstract;
import org.terraform.coregen.populatordata.PopulatorDataAbstract;
import org.terraform.coregen.populatordata.PopulatorDataICAAbstract;
import org.terraform.coregen.populatordata.PopulatorDataPostGen;
import org.terraform.coregen.populatordata.PopulatorDataSpigotAPI;
import org.terraform.data.TerraformWorld;
import org.terraform.main.TerraformGeneratorPlugin;
import org.terraform.main.config.TConfig;
import org.terraform.utils.GenUtils;
import org.terraform.utils.version.TerraformFieldHandler;
import org.terraform.utils.version.TerraformMethodHandler;

import java.lang.reflect.InvocationTargetException;

public class NMSInjector extends NMSInjectorAbstract {

    private static @Nullable TerraformMethodHandler getTileEntity = null;

    @Override
    public void startupTasks() {
        CustomBiomeHandler.init();
    }

    @Override
    public @NotNull BlockDataFixerAbstract getBlockDataFixer() {
        return new BlockDataFixer();
    }

    @Override
    public boolean attemptInject(@NotNull World world) {
        try {
            CraftWorld craftWorld = (CraftWorld) world;
            ServerLevel serverLevel = craftWorld.getHandle();

            TerraformWorld.get(world).minY = getMinY();
            TerraformWorld.get(world).maxY = getMaxY();

            ChunkGenerator delegate = serverLevel.getChunkSource().getGenerator();
            TerraformGeneratorPlugin.logger.info(
                    "NMSChunkGenerator Delegate is of type " + delegate.getClass().getSimpleName()
            );

            // Preserve the full 64-bit world seed. Truncating this to int causes
            // Terraform's NMS-side biome/structure calculations to disagree with
            // the Bukkit generator for most world seeds.
            NMSChunkGenerator generator = new NMSChunkGenerator(world.getName(), world.getSeed(), delegate);

            ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;
            var worldGenContextField = new TerraformFieldHandler(
                    chunkMap.getClass(),
                    "worldGenContext",
                    "N"
            );
            WorldGenContext worldGenContext = (WorldGenContext) worldGenContextField.field.get(chunkMap);
            worldGenContextField.field.set(
                    chunkMap,
                    new WorldGenContext(
                            worldGenContext.level(),
                            generator,
                            worldGenContext.structureManager(),
                            worldGenContext.lightEngine(),
                            worldGenContext.mainThreadExecutor(),
                            worldGenContext.unsavedListener()
                    )
            );

            TerraformGeneratorPlugin.logger.info(
                    "Post injection: getChunkSource().getChunkGenerator() is of type "
                    + serverLevel.getChunkSource().getGenerator().getClass().getSimpleName()
            );
        }
        catch (Throwable throwable) {
            TerraformGeneratorPlugin.logger.stackTrace(throwable);
            return false;
        }

        return true;
    }

    @Override
    public @NotNull PopulatorDataICAAbstract getICAData(@NotNull Chunk chunk) {
        ChunkAccess chunkAccess = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        CraftWorld craftWorld = (CraftWorld) chunk.getWorld();
        ServerLevel serverLevel = craftWorld.getHandle();
        TerraformWorld terraformWorld = TerraformWorld.get(chunk.getWorld());
        return new PopulatorDataICA(
                new PopulatorDataPostGen(chunk),
                terraformWorld,
                serverLevel,
                chunkAccess,
                chunk.getX(),
                chunk.getZ()
        );
    }

    @Override
    public PopulatorDataICAAbstract getICAData(PopulatorDataAbstract data) {
        if (data instanceof PopulatorDataSpigotAPI populatorData) {
            WorldGenLevel worldGenLevel = ((CraftLimitedRegion) populatorData.lr).getHandle();
            ServerLevel serverLevel = worldGenLevel.getMinecraftWorld();
            TerraformWorld terraformWorld = TerraformWorld.get(
                    serverLevel.getWorld().getName(),
                    serverLevel.getSeed()
            );
            return new PopulatorDataICA(
                    data,
                    terraformWorld,
                    serverLevel,
                    worldGenLevel.getChunk(data.getChunkX(), data.getChunkZ()),
                    data.getChunkX(),
                    data.getChunkZ()
            );
        }
        if (data instanceof PopulatorDataPostGen postGenData) {
            return getICAData(postGenData.getChunk());
        }

        return null;
    }

    @Override
    public void storeBee(Beehive hive) {
        try {
            if (getTileEntity == null) {
                getTileEntity = new TerraformMethodHandler(
                        CraftBlockEntityState.class,
                        new String[]{"getTileEntity", "getBlockEntity"}
                );
            }
            BeehiveBlockEntity blockEntity =
                    (BeehiveBlockEntity) getTileEntity.method.invoke(hive);
            blockEntity.storeBee(
                    BeehiveBlockEntity.Occupant.create(GenUtils.RANDOMIZER.nextInt(599))
            );
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getMinY() {
        return TConfig.c.DEVSTUFF_OVERRIDE_MINHEIGHT;
    }

    @Override
    public int getMaxY() {
        return TConfig.c.DEVSTUFF_OVERRIDE_MAXHEIGHT;
    }
}
