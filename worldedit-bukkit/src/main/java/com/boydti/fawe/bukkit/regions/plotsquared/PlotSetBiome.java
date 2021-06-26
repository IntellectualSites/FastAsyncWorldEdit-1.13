package com.boydti.fawe.bukkit.regions.plotsquared;

import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.TaskManager;
import com.plotsquared.core.command.Command;
import com.plotsquared.core.command.CommandCategory;
import com.plotsquared.core.command.CommandDeclaration;
import com.plotsquared.core.command.MainCommand;
import com.plotsquared.core.command.RequiredType;
import com.plotsquared.core.configuration.Captions;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import com.plotsquared.core.util.MainUtil;
import com.plotsquared.core.util.Permissions;
import com.plotsquared.core.util.StringMan;
import com.plotsquared.core.util.task.RunnableVal2;
import com.plotsquared.core.util.task.RunnableVal3;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.biome.Biomes;
import com.sk89q.worldedit.world.registry.BiomeRegistry;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@CommandDeclaration(
        command = "generatebiome",
        permission = "plots.generatebiome",
        category = CommandCategory.APPEARANCE,
        requiredType = RequiredType.NONE,
        description = "Generate a biome in your plot",
        aliases = {"bg", "gb"},
        usage = "/plots generatebiome <biome>"
)
public class PlotSetBiome extends Command {
    public PlotSetBiome() {
        super(MainCommand.getInstance(), true);
    }

    @Override
    public CompletableFuture<Boolean> execute(final PlotPlayer<?> player, String[] args, RunnableVal3<Command, Runnable, Runnable> confirm, RunnableVal2<Command, CommandResult> whenDone) throws CommandException {
        final Plot plot = check(player.getCurrentPlot(), Captions.NOT_IN_PLOT);
        checkTrue(plot.isOwner(player.getUUID()) || Permissions
            .hasPermission(player, "plots.admin.command.generatebiome"), Captions.NO_PLOT_PERMS);
        if (plot.getRunning() != 0) {
            Captions.WAIT_FOR_TIMER.send(player);
            return null;
        }
        checkTrue(args.length == 1, Captions.COMMAND_SYNTAX, getUsage());
        final Set<CuboidRegion> regions = plot.getRegions();
        BiomeRegistry biomeRegistry = WorldEdit.getInstance().getPlatformManager().queryCapability(Capability.GAME_HOOKS).getRegistries().getBiomeRegistry();
        Collection<BiomeType> knownBiomes = BiomeTypes.values();
        final BiomeType biome = Biomes.findBiomeByName(knownBiomes, args[0], biomeRegistry);
        if (biome == null) {
            String biomes = StringMan
                    .join(BiomeType.REGISTRY.values(), Captions.BLOCK_LIST_SEPARATOR.getTranslated());
            Captions.NEED_BIOME.send(player);
            MainUtil.sendMessage(player, Captions.SUBCOMMAND_SET_OPTIONS_HEADER.toString() + biomes);
            return CompletableFuture.completedFuture(false);
        }
        confirm.run(this, () -> {
            if (plot.getRunning() != 0) {
                Captions.WAIT_FOR_TIMER.send(player);
                return;
            }
            plot.addRunning();
            TaskManager.IMP.async(() -> {
                EditSession session = new EditSessionBuilder(BukkitAdapter.adapt(Bukkit.getWorld(plot.getArea().getWorldName())))
                        .autoQueue(false)
                        .checkMemory(false)
                        .allowedRegionsEverywhere()
                        .actor(BukkitAdapter.adapt(Bukkit.getPlayer(player.getUUID())))
                        .limitUnlimited()
                        .build();
                long seed = ThreadLocalRandom.current().nextLong();
                for (CuboidRegion region : regions) {
                    session.regenerate(region, biome, seed);
                }
                session.flushQueue();
                plot.removeRunning();
            });
        }, null);

        return CompletableFuture.completedFuture(true);
    }
}
