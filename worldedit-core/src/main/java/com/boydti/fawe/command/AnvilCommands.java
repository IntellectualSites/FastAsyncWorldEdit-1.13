package com.boydti.fawe.command;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.command.util.CommandPermissions;
import com.sk89q.worldedit.command.util.CommandPermissionsConditionGenerator;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.biome.BiomeType;
import org.enginehub.piston.annotation.Command;
import org.enginehub.piston.annotation.CommandContainer;
import org.enginehub.piston.annotation.param.Arg;
import org.enginehub.piston.annotation.param.Switch;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 * @deprecated Anvil classes were used on versions prior to 1.13 to trim chunks.
 * The way how it's been done was unsafe and led to issues back the years, hence it
 * hasn't been implemented in any modern version. Therefore the current
 * implementation is deprecated for removal without replacement.
 */
@Deprecated
@CommandContainer(superTypes = CommandPermissionsConditionGenerator.Registration.class)
public class AnvilCommands {

    /**
     * Create a new instance.
     *
     * @param worldEdit reference to WorldEdit
     */
    public AnvilCommands(WorldEdit worldEdit) {
        checkNotNull(worldEdit);
    }

    //    /**
    //     * Run safely on an unloaded world (no selection)
    //     *
    //     * @param player
    //     * @param folder
    //     * @param filter
    //     * @param <G>
    //     * @param <T>
    //     * @return
    //     */
    //    @Deprecated
    //    public static <G, T extends MCAFilter<G>> T runWithWorld(Player player, String folder, T filter, boolean force) {
    //        return runWithWorld(player, folder, filter, force, false);
    //    }
    //
    //
    //    @Deprecated
    //    public static <G, T extends MCAFilter<G>> T runWithWorld(Player player, String folder, T filter, boolean force, boolean unsafe) {
    //        boolean copy = false;
    //        if (FaweAPI.getWorld(folder) != null) {
    //            if (!force) {
    //                player.print(TranslatableComponent.of("fawe.worldedit.anvil.world.is.loaded"))
    //                return null;
    //            }
    //            copy = true;
    //        }
    //        IQueueExtent defaultQueue = SetQueue.IMP.getNewQueue(folder, true, false);
    //        MCAQueue queue = new MCAQueue(defaultQueue);
    //        if (copy && !unsafe) {
    //            return queue.filterCopy(filter, RegionWrapper.GLOBAL());
    //        } else {
    //            return queue.filterWorld(filter);
    //        }
    //    }
    //
    //    /**
    //     * Run safely on an existing world within a selection
    //     *
    //     * @param player
    //     * @param editSession
    //     * @param selection
    //     * @param filter
    //     * @param <G>
    //     * @param <T>
    //     * @return
    //     */
    //    @Deprecated
    //    public static <G, T extends MCAFilter<G>> T runWithSelection(Player player, EditSession editSession, Region selection, T filter) {
    //        if (!(selection instanceof CuboidRegion)) {
    //            player.print(TranslatableComponent.of("fawe.error.no.region"))
    //            return null;
    //        }
    //        CuboidRegion cuboid = (CuboidRegion) selection;
    //        RegionWrapper wrappedRegion = new RegionWrapper(cuboid.getMinimumPoint(), cuboid.getMaximumPoint());
    //        String worldName = editSession.getWorld().getName();
    //        IQueueExtent tmp = SetQueue.IMP.getNewQueue(worldName, true, false);
    //        MCAQueue queue = new MCAQueue(tmp);
    //        FawePlayer<Object> fp = FawePlayer.wrap(player);
    //        fp.checkAllowedRegion(selection);
    //        recordHistory(fp, editSession.getWorld(), iAnvilHistory -> {
    //            queue.filterCopy(filter, wrappedRegion, iAnvilHistory);
    //        });
    //        return filter;
    //    }
    //
    //    public static void recordHistory(FawePlayer fp, World world, Consumer<IAnvilHistory> run) {
    //        LocalSession session = fp.getSession();
    //        if (session == null || session.hasFastMode()) {
    //            run.accept(new NullAnvilHistory());
    //        } else {
    //            AnvilHistory history = new AnvilHistory(world.getName(), fp.getUUID());
    //            run.accept(history);
    //            session.remember(fp.getActor(), world, history, fp.getLimit());
    //        }
    //    }

    @Command(
        name = "replaceall",
        aliases = {"rea", "repall"},
        desc = "Replace all blocks in the selection with another"
    )
    @CommandPermissions("worldedit.anvil.replaceall")
    public void replaceAll(Player player, String folder,
        @Arg(name = "from", desc = "String", def = "")
            String fromPattern,
        String toPatternStr,
        @Switch(name = 'd', desc = "Disable wildcard data matching")
            boolean useData) throws WorldEditException {
        //        final FaweBlockMatcher matchFrom;  TODO NOT IMPLEMENTED
        //        if (from == null) {
        //            matchFrom = FaweBlockMatcher.NOT_AIR;
        //        } else {
        //            if (from.contains(":")) {
        //                useData = true; //override d flag, if they specified data they want it
        //            }
        //            matchFrom = FaweBlockMatcher.fromBlocks(worldEdit.getBlocks(player, from, true), useData);
        //        }
        //        final FaweBlockMatcher matchTo = FaweBlockMatcher.setBlocks(worldEdit.getBlocks(player, to, true));
        //        ReplaceSimpleFilter filter = new ReplaceSimpleFilter(matchFrom, matchTo);
        //        ReplaceSimpleFilter result = runWithWorld(player, folder, filter, true);
        //        if (result != null) player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
    }

    @Command(
        name = "remapall",
        descFooter = "Remap the world between MCPE/PC values",
        desc = "Remap the world between MCPE/PC values"
    )
    @CommandPermissions("worldedit.anvil.remapall")
    public void remapall(Player player, String folder) throws WorldEditException {
        //        ClipboardRemapper.RemapPlatform from; TODO NOT IMPLEMENTED
        //        ClipboardRemapper.RemapPlatform to;
        //        from = ClipboardRemapper.RemapPlatform.PE;
        //        to = ClipboardRemapper.RemapPlatform.PC;
        //        RemapFilter filter = new RemapFilter(from, to);
        //        RemapFilter result = runWithWorld(player, folder, filter, true);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }


    @Command(
        name = "deleteallunvisited",
        aliases = {"delunvisited" },
        desc = "Delete all chunks which haven't been occupied",
        descFooter = "occupied for `age-ticks` (20t = 1s) and \n"
            + "Have not been accessed since `file-duration` (ms) after creation and\n"
            + "Have not been used in the past `chunk-inactivity` (ms)"
            + "The auto-save interval is the recommended value for `file-duration` and `chunk-inactivity`"
    )
    @CommandPermissions("worldedit.anvil.deleteallunvisited")
    public void deleteAllUnvisited(Player player, String folder, int inhabitedTicks,
                                   @Arg(desc = "int", def = "60000")
                                       int fileDurationMillis) throws WorldEditException {
        //        DeleteUninhabitedFilter filter = new DeleteUninhabitedFilter(fileDurationMillis, inhabitedTicks, fileDurationMillis);  TODO NOT IMPLEMENTED
        //        DeleteUninhabitedFilter result = runWithWorld(player, folder, filter, true);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "deleteallunclaimed",
        aliases = {"delallunclaimed" },
        desc = "Delete all chunks which haven't been occupied",
        descFooter = "Supports: WorldGuard, PlotSquared, GriefPrevention:\n"
            + "Delete all chunks which aren't claimed AND haven't been occupied for `age-ticks` (20t = 1s) and \n"
            + "Have not been accessed since `file-duration` (ms) after creation and\n"
            + "Have not been used in the past `chunk-inactivity` (ms)"
            + "The auto-save interval is the recommended value for `file-duration` and `chunk-inactivity`"
    )
    @CommandPermissions("worldedit.anvil.deleteallunclaimed")
    public void deleteAllUnclaimed(Player player, int inhabitedTicks,
                                   @Arg(desc = "int", def = "60000")
                                       int fileDurationMillis,
                                   @Switch(name = 'd', desc = "The flag will debug the task")
                                       boolean debug) throws WorldEditException {
        //        String folder = player.getWorld().getName();  TODO NOT IMPLEMENTED
        //        DeleteUnclaimedFilter filter = new DeleteUnclaimedFilter(player.getWorld(), fileDurationMillis, inhabitedTicks, fileDurationMillis);
        //        if (debug) {
        //            filter.enableDebug();
        //        }
        //        DeleteUnclaimedFilter result = runWithWorld(player, folder, filter, true);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "deleteunclaimed",
        desc = "Delete all chunks which haven't been occupied",
        descFooter = "(Supports: WorldGuard, PlotSquared, GriefPrevention):\n"
            + "Is not claimed\n"
            + "Has not been occupied for `age-ticks` (20t = 1s) and \n"
            + "Have not been accessed since `file-duration` (ms) after creation and\n"
            + "Have not been used in the past `chunk-inactivity` (ms)"
            + "The auto-save interval is the recommended value for `file-duration` and `chunk-inactivity`"
    )
    @CommandPermissions("worldedit.anvil.deleteunclaimed")
    public void deleteUnclaimed(Player player, EditSession editSession, @Selection Region selection, int inhabitedTicks,
                                @Arg(desc = "int", def = "60000")
                                    int fileDurationMillis,
                                @Switch(name = 'd', desc = "The flag will debug the task")
                                    boolean debug) throws WorldEditException {
        //        DeleteUnclaimedFilter filter = new DeleteUnclaimedFilter(player.getWorld(), fileDurationMillis, inhabitedTicks, fileDurationMillis);  TODO NOT IMPLEMENTED
        //        if (debug) {
        //            filter.enableDebug();
        //        }
        //        DeleteUnclaimedFilter result = runWithSelection(player, editSession, selection, filter);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "deletealloldregions",
        aliases = {"deloldreg" },
        desc = "Delete regions which haven't been accessed in a certain amount of time",
        descFooter = "You can use seconds (s), minutes (m), hours (h), days (d), weeks (w), years (y)\n"
            + "(months are not a unit of time) e.g., 8h5m12s\n"
    )
    @CommandPermissions("worldedit.anvil.deletealloldregions")
    public void deleteAllOldRegions(Player player, String folder, String time) throws WorldEditException {
        //        long duration = MainUtil.timeToSec(time) * 1000L;  TODO NOT IMPLEMENTED
        //        DeleteOldFilter filter = new DeleteOldFilter(duration);
        //        DeleteOldFilter result = runWithWorld(player, folder, filter, true);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "trimallplots",
        desc = "Trim chunks in a Plot World",
        descFooter = "Unclaimed chunks will be deleted\nUnmodified chunks will be deleted\n"
    )
    @CommandPermissions("worldedit.anvil.trimallplots")
    public void trimAllPlots(Player player,
                             @Switch(name = 'v', desc = "Delete unvisited chunks")
                                 boolean deleteUnvisited) throws WorldEditException {
        //        String folder = player.getWorld().getName();  TODO NOT IMPLEMENTED
        //        int visitTime = deleteUnvisited ? 1 : -1;
        //        PlotTrimFilter filter = new PlotTrimFilter(player.getWorld(), 0, visitTime, 600000);
        ////        PlotTrimFilter result = runWithWorld(player, folder, filter, true);
        //        IQueueExtent defaultQueue = SetQueue.IMP.getNewQueue(folder, true, false);
        //        MCAQueue queue = new MCAQueue(defaultQueue);
        //        PlotTrimFilter result = queue.filterWorld(filter);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "deletebiomechunks",
        desc = "Delete chunks matching a specific biome"
    )
    @CommandPermissions("worldedit.anvil.trimallair")
    public void deleteBiome(Player player, String folder, BiomeType biome,
                            @Switch(name = 'u', desc = "The flag will run the task unsafe")
                                boolean unsafe) {
        //        DeleteBiomeFilterSimple filter = new DeleteBiomeFilterSimple(biome);  TODO NOT IMPLEMENTED
        //        DeleteBiomeFilterSimple result = runWithWorld(player, folder, filter, true, unsafe);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "trimallair",
        desc = "Trim all air in the world"
    )
    @CommandPermissions("worldedit.anvil.trimallair")
    public void trimAllAir(Player player, String folder,
                           @Switch(name = 'u', desc = "The flag will run the task unsafe")
                               boolean unsafe) throws WorldEditException {
        //        TrimAirFilter filter = new TrimAirFilter();  TODO NOT IMPLEMENTED
        //        TrimAirFilter result = runWithWorld(player, folder, filter, true, unsafe);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "debugfixroads",
        desc = "debug - do not use"
    )
    @CommandPermissions("worldedit.anvil.debugfixroads")
    public void debugfixroads(Player player, String folder) throws WorldEditException {
        //        DebugFixP2Roads filter = new DebugFixP2Roads();  TODO NOT IMPLEMENTED
        //        DebugFixP2Roads result = runWithWorld(player, folder, filter, true, true);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "replaceallpattern",
        aliases = {"reap", "repallpat"},
        desc = "Replace all blocks in the selection with another"
    )
    @CommandPermissions("worldedit.anvil.replaceall")
    public void replaceAllPattern(Player player, String folder,
                                  @Arg(desc = "String", def = "")
                                      String from, Pattern toPattern,
                                  @Switch(name = 'd', desc = "The flag specifies the data to use")
                                      boolean useData,
                                  @Switch(name = 'm', desc = "The flag specifies the map to use")
                                      boolean useMap) throws WorldEditException {
        //        MCAFilterCounter filter;  TODO NOT IMPLEMENTED
        //        if (useMap) {
        //            if (to instanceof RandomPattern) {
        //                List<String> split = StringMan.split(from, ',');
        //                filter = new MappedReplacePatternFilter(from, (RandomPattern) to, useData);
        //            } else {
        //                player.print("Must be a pattern list!");
        //                return;
        //            }
        //        } else {
        //            final FaweBlockMatcher matchFrom;
        //            if (from == null) {
        //                matchFrom = FaweBlockMatcher.NOT_AIR;
        //            } else {
        //                matchFrom = FaweBlockMatcher.fromBlocks(worldEdit.getBlocks(player, from, true), useData || from.contains(":"));
        //            }
        //            filter = new ReplacePatternFilter(matchFrom, to);
        //        }
        //        MCAFilterCounter result = runWithWorld(player, folder, filter, true);
        //        if (result != null) player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
    }

    //
    @Command(
        name = "countall",
        desc = "Count all blocks in a world"
    )
    @CommandPermissions("worldedit.anvil.countall")
    public void countAll(Player player, EditSession editSession, String folder, String argStr,
                         @Switch(name = 'd', desc = "The flag specifies the data to use")
                             boolean useData) throws WorldEditException {
        //        Set<BaseBlock> searchBlocks = worldEdit.getBlocks(player, arg, true);
        //        MCAFilterCounter filter;
        //        if (useData || arg.contains(":")) { // Optimize for both cases
        //            CountFilter counter = new CountFilter();
        //            searchBlocks.forEach(counter::addBlock);
        //            filter = counter;
        //        } else {
        //            CountIdFilter counter = new CountIdFilter();
        //            searchBlocks.forEach(counter::addBlock);
        //            filter = counter;
        //        }
        //        MCAFilterCounter result = runWithWorld(player, folder, filter, true);
        //        if (result != null) player.print(TranslatableComponent.of("fawe.worldedit.selection.selection.count", (result.getTotal())));
    }

    @Command(
        name = "clear",
        aliases = {"unset"},
        desc = "Clear the chunks in a selection (delete without defrag)"
    )
    @CommandPermissions("worldedit.anvil.clear")
    public void unset(Player player, EditSession editSession, @Selection Region selection) throws WorldEditException {
        //        BlockVector3 bot = selection.getMinimumPoint();  TODO NOT IMPLEMENTED
        //        BlockVector3 top = selection.getMaximumPoint();
        //        RegionWrapper region = new RegionWrapper(bot, top);
        //
        //        MCAFilterCounter filter = new MCAFilterCounter() {
        //            @Override
        //            public MCAFile applyFile(MCAFile file) {
        //                int X = file.getX();
        //                int Z = file.getZ();
        //                int bcx = X << 5;
        //                int bcz = Z << 5;
        //                int bx = X << 9;
        //                int bz = Z << 9;
        //                if (region.isIn(bx, bz) && region.isIn(bx + 511, bz + 511)) {
        //                    file.setDeleted(true);
        //                    get().add(512 * 512 * 256);
        //                } else if (region.isInMCA(X, Z)) {
        //                    file.init();
        //                    final byte[] empty = new byte[4];
        //                    RandomAccessFile raf = file.getRandomAccessFile();
        //                    file.forEachChunk(new RunnableVal4<Integer, Integer, Integer, Integer>() {
        //                        @Override
        //                        public void run(Integer cx, Integer cz, Integer offset, Integer size) {
        //                            if (region.isInChunk(bcx + cx, bcz + cz)) {
        //                                int index = ((cx & 31) << 2) + ((cz & 31) << 7);
        //                                try {
        //                                    raf.seek(index);
        //                                    raf.write(empty);
        //                                    get().add(16 * 16 * 256);
        //                                } catch (IOException e) {
        //                                    e.printStackTrace();
        //                                }
        //                            }
        //                        }
        //                    });
        //                    file.clear();
        //                }
        //                return null;
        //            }
        //        };
        //        MCAFilterCounter result = runWithSelection(player, editSession, selection, filter);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "count",
        desc = "Count blocks in a selection"
    )
    @CommandPermissions("worldedit.anvil.count")
    public void count(Player player, EditSession editSession, @Selection Region selection, String argStr,
                      @Switch(name = 'd', desc = "The flag specifies the data to use")
                          boolean useData) throws WorldEditException {
        //        Set<BaseBlock> searchBlocks = worldEdit.getBlocks(player, arg, true);  TODO NOT IMPLEMENTED
        //        MCAFilterCounter filter;
        //        if (useData || arg.contains(":")) { // Optimize for both cases
        //            CountFilter counter = new CountFilter();
        //            searchBlocks.forEach(counter::addBlock);
        //            filter = counter;
        //        } else {
        //            CountIdFilter counter = new CountIdFilter();
        //            searchBlocks.forEach(counter::addBlock);
        //            filter = counter;
        //        }
        //        MCAFilterCounter result = runWithSelection(player, editSession, selection, filter);
        //        if (result != null) player.print(TranslatableComponent.of("fawe.worldedit.selection.selection.count", (result.getTotal())));
    }

    //
    @Command(
        name = "distr",
        desc = "Replace all blocks in the selection with another"
    )
    @CommandPermissions("worldedit.anvil.distr")
    public void distr(Player player, EditSession editSession, @Selection Region selection,
                      @Switch(name = 'd', desc = "The flag specifies the data to use")
                          boolean useData) throws WorldEditException {
        //        long total = 0;  TODO NOT IMPLEMENTED
        //        long[] count;
        //        MCAFilter<long[]> counts;
        //        if (useData) {
        //            counts = runWithSelection(player, editSession, selection, new MCAFilter<long[]>() {
        //                @Override
        //                public void applyBlock(int x, int y, int z, BaseBlock block, long[] counts) {
        //                    counts[block.getCombined()]++;
        //                }
        //
        //                @Override
        //                public long[] init() {
        //                    return new long[Character.MAX_VALUE + 1];
        //                }
        //            });
        //            count = new long[Character.MAX_VALUE + 1];
        //        } else {
        //            counts = runWithSelection(player, editSession, selection, new MCAFilter<long[]>() {
        //                @Override
        //                public void applyBlock(int x, int y, int z, BaseBlock block, long[] counts) {
        //                    counts[block.getId()]++;
        //                }
        //
        //                @Override
        //                public long[] init() {
        //                    return new long[4096];
        //                }
        //            });
        //            count = new long[4096];
        //        }
        //        for (long[] value : counts) {
        //            for (int i = 0; i < value.length; i++) {
        //                count[i] += value[i];
        //                total += value[i];
        //            }
        //        }
        //        ArrayList<long[]> map = new ArrayList<>();
        //        for (int i = 0; i < count.length; i++) {
        //            if (count[i] != 0) map.add(new long[]{i, count[i]});
        //        }
        //        Collections.sort(map, new Comparator<long[]>() {
        //            @Override
        //            public int compare(long[] a, long[] b) {
        //                long vA = a[1];
        //                long vB = b[1];
        //                return (vA < vB) ? -1 : ((vA == vB) ? 0 : 1);
        //            }
        //        });
        //        if (useData) {
        //            for (long[] c : map) {
        //                BaseBlock block = FaweCache.IMP.CACHE_BLOCK[(int) c[0]];
        //                String name = BlockType.fromID(block.getId()).getName();
        //                String str = String.format("%-7s (%.3f%%) %s #%d:%d",
        //                        String.valueOf(c[1]),
        //                        ((c[1] * 10000) / total) / 100d,
        //                        name == null ? "Unknown" : name,
        //                        block.getType(), block.getData());
        //                player.print(str);
        //            }
        //        } else {
        //            for (long[] c : map) {
        //                BlockType block = BlockType.fromID((int) c[0]);
        //                String str = String.format("%-7s (%.3f%%) %s #%d",
        //                        String.valueOf(c[1]),
        //                        ((c[1] * 10000) / total) / 100d,
        //                        block == null ? "Unknown" : block.getName(), c[0]);
        //                player.print(str);
        //            }
        //        }
    }

    //
    @Command(
        name = "replace",
        aliases = {"r"},
        desc = "Replace all blocks in the selection with another"
    )
    @CommandPermissions("worldedit.anvil.replace")
    public void replace(Player player, EditSession editSession, @Selection Region selection,
                        @Arg(desc = "String", def = "")
                            String from, String toStr,
                        @Switch(name = 'd', desc = "The flag specifies the data to use")
                            boolean useData) throws WorldEditException {
        //        final FaweBlockMatcher matchFrom;  TODO NOT IMPLEMENTED
        //        if (from == null) {
        //            matchFrom = FaweBlockMatcher.NOT_AIR;
        //        } else {
        //            matchFrom = FaweBlockMatcher.fromBlocks(worldEdit.getBlocks(player, from, true), useData || from.contains(":"));
        //        }
        //        final FaweBlockMatcher matchTo = FaweBlockMatcher.setBlocks(worldEdit.getBlocks(player, to, true));
        //        ReplaceSimpleFilter filter = new ReplaceSimpleFilter(matchFrom, matchTo);
        //        MCAFilterCounter result = runWithSelection(player, editSession, selection, filter);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    //
    @Command(
        name = "replacepattern",
        aliases = {"preplace", "rp"},
        desc = "Replace all blocks in the selection with a pattern"
    )
    @CommandPermissions("worldedit.anvil.replace")
    public void replacePattern(Player player, EditSession editSession, @Selection Region selection,
                               @Arg(desc = "String", def = "")
                                   String from, final Pattern toPattern,
                               @Switch(name = 'd', desc = "The flag specifies the data to use")
                                   boolean useData,
                               @Switch(name = 'm', desc = "The flag specifies the map to use")
                                   boolean useMap) throws WorldEditException {
        //        MCAFilterCounter filter;  TODO NOT IMPLEMENTED
        //        if (useMap) {
        //            if (to instanceof RandomPattern) {
        //                List<String> split = StringMan.split(from, ',');
        //                filter = new MappedReplacePatternFilter(from, (RandomPattern) to, useData);
        //            } else {
        //                player.print("Must be a pattern list!");
        //                return;
        //            }
        //        } else {
        //            final FaweBlockMatcher matchFrom;
        //            if (from == null) {
        //                matchFrom = FaweBlockMatcher.NOT_AIR;
        //            } else {
        //                matchFrom = FaweBlockMatcher.fromBlocks(worldEdit.getBlocks(player, from, true), useData || from.contains(":"));
        //            }
        //            filter = new ReplacePatternFilter(matchFrom, to);
        //        }
        //        MCAFilterCounter result = runWithSelection(player, editSession, selection, filter);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "set",
        desc = "Set all blocks in the selection with a pattern"
    )
    @CommandPermissions("worldedit.anvil.set")
    // Player player, String folder, @Arg(name = "from", desc = "String", def = "") String from, final Pattern toPattern, @Switch(name = 'd', desc = "The flag specifies the data to use") boolean useData, @Switch(name = 'm', desc = "The flag specifies the map to use") boolean useMap
    public void set(Player player, EditSession editSession, @Selection Region selection, final Pattern toPattern) throws WorldEditException {
        //        MCAFilterCounter filter = new SetPatternFilter(to);  TODO NOT IMPLEMENTED
        //        MCAFilterCounter result = runWithSelection(player, editSession, selection, filter);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }

    @Command(
        name = "removelayers",
        desc = "Removes matching chunk layers",
        descFooter = "Only if a chunk matches the provided id"
    )
    @CommandPermissions("worldedit.anvil.removelayer")
    public void removeLayers(Player player, EditSession editSession, @Selection Region selection, int id) throws WorldEditException {
        //        BlockVector3 min = selection.getMinimumPoint();  TODO NOT IMPLEMENTED
        //        BlockVector3 max = selection.getMaximumPoint();
        //        int minY = min.getBlockY();
        //        int maxY = max.getBlockY();
        //        RemoveLayerFilter filter = new RemoveLayerFilter(minY, maxY, id);
        //        MCAFilterCounter result = runWithSelection(player, editSession, selection, filter);
        //        if (result != null) {
        //            player.print(TranslatableComponent.of("fawe.worldedit.visitor.visitor.block", (result.getTotal())));
        //        }
    }


    @Command(
        name = "copy",
        desc = "Lazily copy chunks to your anvil clipboard"
    )
    @CommandPermissions("worldedit.anvil.copychunks")
    public void copy(Player player, LocalSession session, EditSession editSession, @Selection Region selection) throws WorldEditException {
        //        if (!(selection instanceof CuboidRegion)) {  TODO NOT IMPLEMENTED
        //            player.print(TranslatableComponent.of("fawe.error.no.region"))
        //            return;
        //        }
        //        CuboidRegion cuboid = (CuboidRegion) selection;
        //        String worldName = editSession.getWorld().getName();
        //        IQueueExtent tmp = SetQueue.IMP.getNewQueue(worldName, true, false);
        //        MCAQueue queue = new MCAQueue(tmp);
        //        BlockVector3 origin = session.getPlacementPosition(player);
        //        MCAClipboard clipboard = new MCAClipboard(queue, cuboid, origin);
        //        FawePlayer fp = FawePlayer.wrap(player);
        //        fp.setMeta(FawePlayer.METADATA_KEYS.ANVIL_CLIPBOARD, clipboard);
        //        player.print(TranslatableComponent.of("fawe.worldedit.copy.command.copy", selection.getArea()));
    }

    @Command(
        name = "paste",
        desc = "Paste chunks from your anvil clipboard",
        descFooter = "Paste the chunks from your anvil clipboard."
    )
    @CommandPermissions("worldedit.anvil.pastechunks")
    public void paste(Player player, LocalSession session, EditSession editSession,
                      @Switch(name = 'c', desc = "Align to chunks")
                          boolean alignChunk) throws WorldEditException, IOException {
        //        FawePlayer fp = FawePlayer.wrap(player);  TODO NOT IMPLEMENTED
        //        MCAClipboard clipboard = fp.getMeta(FawePlayer.METADATA_KEYS.ANVIL_CLIPBOARD);
        //        if (clipboard == null) {
        //            fp.sendMessage("You must first use `//anvil copy`");
        //            return;
        //        }
        //        CuboidRegion cuboid = clipboard.getRegion();
        //        RegionWrapper copyRegion = new RegionWrapper(cuboid.getMinimumPoint(), cuboid.getMaximumPoint());
        //        final Vector offset = player.getPosition().subtract(clipboard.getOrigin());
        //        if (alignChunk) {
        //            offset.setComponents((offset.getBlockX() >> 4) << 4, offset.getBlockY(), (offset.getBlockZ() >> 4) << 4);
        //        }
        //        int oX = offset.getBlockX();
        //        int oZ = offset.getBlockZ();
        //        RegionWrapper pasteRegion = new RegionWrapper(copyRegion.minX + oX, copyRegion.maxX + oX, copyRegion.minZ + oZ, copyRegion.maxZ + oZ);
        //        String pasteWorldName = editSession.getWorld().getName();
        //        IQueueExtent tmpTo = SetQueue.IMP.getNewQueue(pasteWorldName, true, false);
        //        MCAQueue copyQueue = clipboard.getQueue();
        //        MCAQueue pasteQueue = new MCAQueue(tmpTo);
        //
        //        fp.checkAllowedRegion(pasteRegion);
        //        recordHistory(fp, editSession.getWorld(), iAnvilHistory -> {
        //            try {
        //                pasteQueue.pasteRegion(copyQueue, copyRegion, offset, iAnvilHistory);
        //            } catch (IOException e) { throw new RuntimeException(e); }
        //        });
        //        player.print(TranslatableComponent.of("fawe.worldedit.paste.command.paste", player.getPosition().toBlockVector()));
    }
}
