package com.boydti.fawe.util;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.beta.IQueueChunk;
import com.boydti.fawe.beta.IQueueExtent;
import com.boydti.fawe.beta.implementation.lighting.NullRelighter;
import com.boydti.fawe.beta.implementation.lighting.RelightProcessor;
import com.boydti.fawe.beta.implementation.lighting.Relighter;
import com.boydti.fawe.beta.implementation.processors.HeightmapProcessor;
import com.boydti.fawe.beta.implementation.processors.LimitExtent;
import com.boydti.fawe.beta.implementation.queue.ParallelQueueExtent;
import com.boydti.fawe.config.Caption;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.logging.rollback.RollbackOptimizedHistory;
import com.boydti.fawe.object.FaweLimit;
import com.boydti.fawe.object.HistoryExtent;
import com.boydti.fawe.object.NullChangeSet;
import com.boydti.fawe.object.RegionWrapper;
import com.boydti.fawe.object.RelightMode;
import com.boydti.fawe.object.changeset.AbstractChangeSet;
import com.boydti.fawe.object.changeset.BlockBagChangeSet;
import com.boydti.fawe.object.changeset.DiskStorageHistory;
import com.boydti.fawe.object.changeset.MemoryOptimizedHistory;
import com.boydti.fawe.object.extent.FaweRegionExtent;
import com.boydti.fawe.object.extent.MultiRegionExtent;
import com.boydti.fawe.object.extent.NullExtent;
import com.boydti.fawe.object.extent.SingleRegionExtent;
import com.boydti.fawe.object.extent.SlowExtent;
import com.boydti.fawe.object.extent.StripNBTExtent;
import com.boydti.fawe.wrappers.WorldWrapper;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Identifiable;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.world.World;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;


//TODO Migrate all logic to com.sk89q.worldedit.EditSessionBuilder
@Deprecated(forRemoval = true)
public class EditSessionBuilder {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    private World world;
    private Actor actor;
    private FaweLimit limit;
    private AbstractChangeSet changeSet;
    private Region[] allowedRegions;
    private Boolean autoQueue;
    private Boolean fastmode;
    private Boolean checkMemory;
    private Boolean combineStages;
    @NotNull
    private EventBus eventBus = WorldEdit.getInstance().getEventBus();
    private BlockBag blockBag;
    private EditSessionEvent event;
    private String command;
    private RelightMode relightMode;
    private Relighter relighter;
    private Boolean wnaMode;
    private boolean tracing;

    /**
     * An EditSession builder<br>
     * - Unset values will revert to their default<br>
     * <br>
     * player: The player doing the edit (defaults to to null)<br>
     * limit: Block/Entity/Action limit (defaults to unlimited)<br>
     * changeSet: Stores changes (defaults to config.yml value)<br>
     * allowedRegions: Allowed editable regions (defaults to player's allowed regions, or everywhere)<br>
     * autoQueue: Changes can occur before flushQueue() (defaults true)<br>
     * fastmode: bypasses history (defaults to player fastmode or config.yml console history)<br>
     * checkMemory: If low memory checks are enabled (defaults to player's fastmode or true)<br>
     * combineStages: If history is combined with dispatching
     *
     * @param world A world must be provided for all EditSession(s)
     */
    public EditSessionBuilder(World world) {
        this.world = world;
    }

    /**
     * Set the actor who owns the {@link EditSession}.
     *
     * @param actor the actor
     * @return this builder
     */
    public EditSessionBuilder actor(@Nullable Actor actor) {
        this.actor = actor;
        return setDirty();
    }

    public EditSessionBuilder limit(@Nullable FaweLimit limit) {
        this.limit = limit;
        return setDirty();
    }

    public EditSessionBuilder limitUnlimited() {
        return limit(FaweLimit.MAX.copy());
    }

    public EditSessionBuilder limitUnprocessed(@NotNull Actor player) {
        limitUnlimited();
        FaweLimit tmp = player.getLimit();
        limit.INVENTORY_MODE = tmp.INVENTORY_MODE;
        return setDirty();
    }

    public EditSessionBuilder changeSet(@Nullable AbstractChangeSet changeSet) {
        this.changeSet = changeSet;
        return setDirty();
    }

    public EditSessionBuilder changeSetNull() {
        return changeSet(new NullChangeSet(world));
    }

    public EditSessionBuilder world( World world) {
        this.world = world;
        return setDirty();
    }

    public EditSessionBuilder command(String command) {
        this.command = command;
        return this;
    }

    public EditSessionBuilder changeSet(boolean disk, @Nullable UUID uuid, int compression) {
        if (disk) {
            if (Settings.IMP.HISTORY.USE_DATABASE) {
                this.changeSet = new RollbackOptimizedHistory(world, uuid);
            } else {
                this.changeSet = new DiskStorageHistory(world, uuid);
            }
        } else {
            this.changeSet = new MemoryOptimizedHistory(world);
        }
        return setDirty();
    }

    public EditSessionBuilder allowedRegions(@Nullable Region[] allowedRegions) {
        this.allowedRegions = allowedRegions;
        return setDirty();
    }

    @Deprecated
    public EditSessionBuilder allowedRegions(@Nullable RegionWrapper[] allowedRegions) {
        this.allowedRegions = allowedRegions;
        return setDirty();
    }

    public EditSessionBuilder allowedRegions(@Nullable RegionWrapper allowedRegion) {
        this.allowedRegions = allowedRegion == null ? null : allowedRegion.toArray();
        return setDirty();
    }

    public EditSessionBuilder allowedRegionsEverywhere() {
        return allowedRegions(new Region[]{RegionWrapper.GLOBAL()});
    }

    public EditSessionBuilder autoQueue(@Nullable Boolean autoQueue) {
        this.autoQueue = autoQueue;
        return setDirty();
    }

    public EditSessionBuilder fastmode(@Nullable Boolean fastmode) {
        this.fastmode = fastmode;
        return setDirty();
    }

    public EditSessionBuilder relightMode(@Nullable RelightMode relightMode) {
        this.relightMode = relightMode;
        return setDirty();
    }

    public EditSessionBuilder checkMemory(@Nullable Boolean checkMemory) {
        this.checkMemory = checkMemory;
        return setDirty();
    }

    public EditSessionBuilder combineStages(@Nullable Boolean combineStages) {
        this.combineStages = combineStages;
        return setDirty();
    }

    public EditSessionBuilder blockBag(@Nullable BlockBag blockBag) {
        this.blockBag = blockBag;
        return setDirty();
    }

    public EditSessionBuilder eventBus(@NotNull EventBus eventBus) {
        this.eventBus = eventBus;
        return setDirty();
    }

    public EditSessionBuilder event(@Nullable EditSessionEvent event) {
        this.event = event;
        return setDirty();
    }

    public EditSessionBuilder forceWNA() {
        this.wnaMode = true;
        return setDirty();
    }

    private EditSessionBuilder setDirty() {
        compiled = false;
        return this;
    }

    private Extent wrapExtent(final Extent extent, final EventBus eventBus, EditSessionEvent event, final EditSession.Stage stage) {
        event = event.clone(stage);
        event.setExtent(extent);
        eventBus.post(event);
        if (event.isCancelled()) {
            return new NullExtent(extent, FaweCache.MANUAL);
        }
        final Extent toReturn = event.getExtent();
        if (toReturn instanceof com.sk89q.worldedit.extent.NullExtent) {
            return new NullExtent(toReturn, FaweCache.MANUAL);
        }
        if (toReturn != extent) {
            String className = toReturn.getClass().getName().toLowerCase(Locale.ROOT);
            for (String allowed : Settings.IMP.EXTENT.ALLOWED_PLUGINS) {
                if (className.contains(allowed.toLowerCase(Locale.ROOT))) {
                    this.wrapped = true;
                    return toReturn;
                }
            }
            if (Settings.IMP.EXTENT.DEBUG) {
                if (event.getActor() != null) {
                    event.getActor().printDebug(TextComponent.of("Potentially unsafe extent blocked: " + toReturn.getClass().getName()));
                    event.getActor().printDebug(TextComponent.of(" - For area restrictions and block logging, it is recommended to use the FaweAPI"));
                    event.getActor().printDebug(TextComponent.of(" - To allow " + toReturn.getClass().getName() + ", add it to the FAWE `allowed-plugins` list in config.yml"));
                    event.getActor().printDebug(TextComponent.of(" - If you are unsure which plugin tries to use the extent, you can find some additional information below:"));
                    event.getActor().printDebug(TextComponent.of(" - " + toReturn.getClass().getClassLoader()));
                } else {
                    LOGGER.debug("Potentially unsafe extent blocked: " + toReturn.getClass().getName());
                    LOGGER.debug(" - For area restrictions and block logging, it is recommended to use the FaweAPI");
                    LOGGER.debug(" - To allow " + toReturn.getClass().getName() + ", add it to the FAWE `allowed-plugins` list in config.yml");
                    LOGGER.debug(" - If you are unsure which plugin tries to use the extent, you can find some additional information below:");
                    LOGGER.debug(" - " + toReturn.getClass().getClassLoader());
                }
            }
        }
        return extent;
    }

    private AbstractChangeSet changeTask;
    private Extent bypassHistory;
    private Extent bypassAll;
    private Extent extent;
    private boolean compiled;
    private boolean wrapped;

    public EditSessionBuilder compile() {
        if (compiled) {
            return this;
        }

        compiled = true;
        wrapped = false;
        if (event == null) {
            event = new EditSessionEvent(world, actor, -1, null);
        }
        if (actor == null && event.getActor() != null) {
            actor = event.getActor();
        }
        if (limit == null) {
            if (actor == null) {
                limit = FaweLimit.MAX;
            } else {
                limit = actor.getLimit();
            }
        }
        if (autoQueue == null) {
            autoQueue = true;
        }
        if (fastmode == null) {
            if (actor == null) {
                fastmode = !Settings.IMP.HISTORY.ENABLE_FOR_CONSOLE;
            } else {
                fastmode = actor.getSession().hasFastMode();
            }
        }
        if (checkMemory == null) {
            checkMemory = actor != null && !this.fastmode;
        }
        if (checkMemory) {
            if (MemUtil.isMemoryLimitedSlow()) {
                if (Permission.hasPermission(actor, "worldedit.fast")) {
                    actor.print(Caption.of("fawe.info.worldedit.oom.admin"));
                }
                throw FaweCache.LOW_MEMORY;
            }
        }
//        this.originalLimit = limit;
        this.blockBag = limit.INVENTORY_MODE != 0 ? blockBag : null;
        this.limit = limit.copy();

        if (extent == null) {
            IQueueExtent<IQueueChunk> queue = null;
            World unwrapped = WorldWrapper.unwrap(world);
            boolean placeChunks = this.fastmode || this.limit.FAST_PLACEMENT;

            if (placeChunks && (wnaMode == null || !wnaMode)) {
                wnaMode = false;
                if (unwrapped instanceof IQueueExtent) {
                    extent = queue = (IQueueExtent) unwrapped;
                } else if (Settings.IMP.QUEUE.PARALLEL_THREADS > 1 && !Fawe.isMainThread()) {
                    ParallelQueueExtent parallel = new ParallelQueueExtent(Fawe.get().getQueueHandler(), world, fastmode);
                    queue = parallel.getExtent();
                    extent = parallel;
                } else {
                    extent = queue = Fawe.get().getQueueHandler().getQueue(world);
                }
            } else {
                wnaMode = true;
                extent = world;
            }
            if (combineStages == null) {
                combineStages =
                        // If it's enabled in the settings
                        Settings.IMP.HISTORY.COMBINE_STAGES
                                // If fast placement is disabled, it's slower to perform a copy on each chunk
                                && this.limit.FAST_PLACEMENT
                                // If the edit uses items from the inventory we can't use a delayed task
                                && this.blockBag == null;
            }
            extent = this.bypassAll = wrapExtent(extent, eventBus, event, EditSession.Stage.BEFORE_CHANGE);
            this.bypassHistory = this.extent = wrapExtent(bypassAll, eventBus, event, EditSession.Stage.BEFORE_REORDER);
            if (!this.fastmode || changeSet != null) {
                if (changeSet == null) {
                    if (Settings.IMP.HISTORY.USE_DISK) {
                        UUID uuid = actor == null ? Identifiable.CONSOLE : actor.getUniqueId();
                        if (Settings.IMP.HISTORY.USE_DATABASE) {
                            changeSet = new RollbackOptimizedHistory(world, uuid);
                        } else {
                            changeSet = new DiskStorageHistory(world, uuid);
                        }
                    } else {
                        if (combineStages && Settings.IMP.HISTORY.COMPRESSION_LEVEL == 0) {
                            //TODO add CPUOptimizedChangeSet
                        }
                        changeSet = new MemoryOptimizedHistory(world);
                    }
                }
                if (this.limit.SPEED_REDUCTION > 0) {
                    this.extent = this.bypassHistory = new SlowExtent(this.bypassHistory, this.limit.SPEED_REDUCTION);
                }
                if (command != null && changeSet instanceof RollbackOptimizedHistory) {
                    ((RollbackOptimizedHistory) changeSet).setCommand(this.command);
                }
                if (!(changeSet instanceof NullChangeSet)) {
                    if (this.blockBag != null) {
                        //TODO implement block bag as IBatchProcessor
                        changeSet = new BlockBagChangeSet(changeSet, blockBag, limit.INVENTORY_MODE == 1);
                    }
                    if (combineStages) {
                        changeTask = changeSet;
                        this.extent = extent.enableHistory(changeSet);
                    } else {
                        this.extent = new HistoryExtent(extent, changeSet);
                    }
                }
            }
            if (allowedRegions == null) {
                if (actor != null && !actor.hasPermission("fawe.bypass") && !actor.hasPermission("fawe.bypass.regions")) {
                    if (actor instanceof Player) {
                        Player player = (Player) actor;
                        allowedRegions = player.getCurrentRegions();
                    }
                }
            }
            FaweRegionExtent regionExtent = null;
            if (allowedRegions != null) {
                if (allowedRegions.length == 0) {
                    regionExtent = new NullExtent(this.extent, FaweCache.NO_REGION);
                } else {
                    if (allowedRegions.length == 1) {
                        regionExtent = new SingleRegionExtent(this.extent, this.limit, allowedRegions[0]);
                    } else {
                        regionExtent = new MultiRegionExtent(this.extent, this.limit, allowedRegions);
                    }
                }
            } else {
                allowedRegions = new Region[]{RegionWrapper.GLOBAL()};
//                this.extent = new HeightBoundExtent(this.extent, this.limit, 0, world.getMaxY());
            }
            // There's no need to do lighting (and it'll also just be a pain to implement) if we're not placing chunks
            if (placeChunks && ((relightMode != null && relightMode != RelightMode.NONE) || (relightMode == null && Settings.IMP.LIGHTING.MODE > 0))) {
                relighter = WorldEdit.getInstance().getPlatformManager()
                        .queryCapability(Capability.WORLD_EDITING)
                        .getRelighterFactory().createRelighter(relightMode, world, queue);
                extent.addProcessor(new RelightProcessor(relighter));
            } else {
                relighter = NullRelighter.INSTANCE;
            }
            extent.addProcessor(new HeightmapProcessor(world));
            if (limit != null && !limit.isUnlimited() && regionExtent != null) {
                this.extent = new LimitExtent(regionExtent, limit);
            } else if (limit != null && !limit.isUnlimited()) {
                this.extent = new LimitExtent(this.extent, limit);
            } else if (regionExtent != null) {
                this.extent = regionExtent;
            }
            if (this.limit != null && this.limit.STRIP_NBT != null && !this.limit.STRIP_NBT.isEmpty()) {
                //TODO add batch processor for strip nbt
                this.extent = new StripNBTExtent(this.extent, this.limit.STRIP_NBT);
            }
            this.extent = wrapExtent(this.extent, eventBus, event, EditSession.Stage.BEFORE_HISTORY);
        }
        return this;
    }

    public EditSession build() {
        return new EditSession(this);
    }

    @Nullable
    public World getWorld() {
        return world;
    }

    public Extent getExtent() {
        return extent != null ? extent : world;
    }

    public boolean isWrapped() {
        return wrapped;
    }

    public Extent getBypassHistory() {
        return bypassHistory;
    }

    public Extent getBypassAll() {
        return bypassAll;
    }

    @NotNull
    public FaweLimit getLimit() {
        return limit;
    }

    @Nullable
    public Actor getActor() {
        return actor;
    }

    public AbstractChangeSet getChangeTask() {
        return changeTask;
    }

    public BlockBag getBlockBag() {
        return blockBag;
    }

    public Relighter getRelighter() {
        return relighter;
    }

    public boolean isWNAMode() {
        return wnaMode;
    }
    public boolean isTracing() {
        return tracing;
    }

    @Nullable
    public Region[] getAllowedRegions() {
        return allowedRegions;
    }

    public EditSessionBuilder tracing(boolean tracing) {
        this.tracing = tracing;
        return this;
    }
}
