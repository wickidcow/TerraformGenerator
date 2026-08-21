package org.terraform.coregen.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.terraform.main.TerraformGeneratorPlugin;

public class PaperScheduler implements AbstractScheduler {

    @Override
    public void execAsyncRegion(@NotNull World world, int chunkX, int chunkZ, @NotNull Runnable run) {
        Bukkit.getScheduler().runTask(TerraformGeneratorPlugin.get(), run);
    }

    @Override
    public void execSyncGlobal(@NotNull Runnable run) {
        Bukkit.getScheduler().runTask(TerraformGeneratorPlugin.get(), run);
    }

    @Override
    public void execAsync(@NotNull Runnable run) {
        Bukkit.getScheduler().runTaskAsynchronously(TerraformGeneratorPlugin.get(), run);
    }
}
