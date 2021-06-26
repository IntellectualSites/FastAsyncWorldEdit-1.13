/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit;

import com.boydti.fawe.FaweCache;
import com.boydti.fawe.beta.implementation.lighting.NullRelighter;
import com.boydti.fawe.beta.implementation.lighting.Relighter;
import com.boydti.fawe.config.Caption;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.FaweLimit;
import com.boydti.fawe.object.RegionWrapper;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.object.changeset.AbstractChangeSet;
import com.boydti.fawe.object.changeset.BlockBagChangeSet;
import com.boydti.fawe.object.collection.LocalBlockVectorSet;
import com.boydti.fawe.object.extent.FaweRegionExtent;
import com.boydti.fawe.object.extent.ProcessedWEExtent;
import com.boydti.fawe.object.extent.ResettableExtent;
import com.boydti.fawe.object.extent.SourceMaskExtent;
import com.boydti.fawe.object.function.SurfaceRegionFunction;
import com.boydti.fawe.object.mask.ResettableMask;
import com.boydti.fawe.object.pattern.ExistingPattern;
import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.ExtentTraverser;
import com.boydti.fawe.util.MaskTraverser;
import com.boydti.fawe.util.MathMan;
import com.boydti.fawe.util.TaskManager;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.ChangeSetExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.MaskingExtent;
import com.sk89q.worldedit.extent.PassthroughExtent;
import com.sk89q.worldedit.extent.TracingExtent;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.extent.inventory.BlockBagExtent;
import com.sk89q.worldedit.extent.world.SurvivalModeExtent;
import com.sk89q.worldedit.extent.world.WatchdogTickingExtent;
import com.sk89q.worldedit.function.GroundFunction;
import com.sk89q.worldedit.function.RegionFunction;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.block.Naturalizer;
import com.sk89q.worldedit.function.block.SnowSimulator;
import com.sk89q.worldedit.function.generator.ForestGenerator;
import com.sk89q.worldedit.function.generator.GardenPatchGenerator;
import com.sk89q.worldedit.function.mask.BlockStateMask;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.function.mask.BoundedHeightMask;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.MaskIntersection;
import com.sk89q.worldedit.function.mask.MaskUnion;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.mask.NoiseFilter2D;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.mask.SingleBlockTypeMask;
import com.sk89q.worldedit.function.mask.SolidBlockMask;
import com.sk89q.worldedit.function.mask.WallMakeMask;
import com.sk89q.worldedit.function.operation.ChangeSetExecutor;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.pattern.WaterloggedRemover;
import com.sk89q.worldedit.function.util.RegionOffset;
import com.sk89q.worldedit.function.visitor.DirectionalVisitor;
import com.sk89q.worldedit.function.visitor.DownwardVisitor;
import com.sk89q.worldedit.function.visitor.FlatRegionVisitor;
import com.sk89q.worldedit.function.visitor.LayerVisitor;
import com.sk89q.worldedit.function.visitor.NonRisingVisitor;
import com.sk89q.worldedit.function.visitor.RecursiveVisitor;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.internal.expression.EvaluationException;
import com.sk89q.worldedit.internal.expression.Expression;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.internal.expression.ExpressionTimeoutException;
import com.sk89q.worldedit.internal.expression.LocalSlot.Variable;
import com.sk89q.worldedit.internal.util.LogManagerCompat;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.MathUtils;
import com.sk89q.worldedit.math.MutableBlockVector2;
import com.sk89q.worldedit.math.MutableBlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.interpolation.Interpolation;
import com.sk89q.worldedit.math.interpolation.KochanekBartelsInterpolation;
import com.sk89q.worldedit.math.interpolation.Node;
import com.sk89q.worldedit.math.noise.RandomNoise;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.FlatRegion;
import com.sk89q.worldedit.regions.NullRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionIntersection;
import com.sk89q.worldedit.regions.Regions;
import com.sk89q.worldedit.regions.shape.ArbitraryBiomeShape;
import com.sk89q.worldedit.regions.shape.ArbitraryShape;
import com.sk89q.worldedit.regions.shape.RegionShape;
import com.sk89q.worldedit.regions.shape.WorldEditExpressionEnvironment;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.TreeGenerator;
import com.sk89q.worldedit.util.collection.BlockMap;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.registry.LegacyMapper;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.function.block.SnowSimulator.snowy;
import static com.sk89q.worldedit.regions.Regions.asFlatRegion;
import static com.sk89q.worldedit.regions.Regions.maximumBlockY;
import static com.sk89q.worldedit.regions.Regions.minimumBlockY;

/**
 * An {@link Extent} that handles history, {@link BlockBag}s, change limits,
 * block re-ordering, and much more. Most operations in WorldEdit use this class.
 *
 * <p>Most of the actual functionality is implemented with a number of other
 * {@link Extent}s that are chained together. For example, history is logged
 * using the {@link ChangeSetExtent}.</p>
 */
@SuppressWarnings({"FieldCanBeLocal"})
public class EditSession extends PassthroughExtent implements AutoCloseable {

    private static final Logger LOGGER = LogManagerCompat.getLogger();

    /**
     * Used by {@link EditSession#setBlock(BlockVector3, BlockStateHolder, Stage)} to
     * determine which {@link Extent}s should be bypassed.
     */
    public enum Stage {
        BEFORE_HISTORY,
        BEFORE_REORDER,
        BEFORE_CHANGE
    }

    /**
     * Reorder mode for {@link EditSession#setReorderMode(ReorderMode)}. NOT FUNCTIONAL IN FAWE
     *
     * <p>
     * MULTI_STAGE = Multi stage reorder, may not be great with mods.
     * FAST = Use the fast mode. Good for mods.
     * NONE = Place blocks without worrying about placement order.
     * </p>
     */
    public enum ReorderMode {
        MULTI_STAGE("multi"),
        FAST("fast"),
        NONE("none");

        private final String displayName;

        ReorderMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    @SuppressWarnings("ProtectedField")
    protected final World world;
    private final @Nullable Actor actor;
    private AbstractChangeSet changeSet;
    private final FaweLimit originalLimit;
    private final FaweLimit limit;
    private boolean history;

    private final MutableBlockVector3 mutablebv = new MutableBlockVector3();

    private int changes = 0;
    private final BlockBag blockBag;

    private final Extent bypassHistory;
    private Extent bypassAll;

    private final int minY;
    private final int maxY;
    private final List<WatchdogTickingExtent> watchdogExtents = new ArrayList<>(2);

    private final Relighter relighter;
    private final boolean wnaMode;
    @Nullable
    private final Region[] allowedRegions;
    private @Nullable List<TracingExtent> tracingExtents = null;

    @Deprecated
    public EditSession(@NotNull EventBus bus, World world, @Nullable Player actor,
        @Nullable FaweLimit limit, @Nullable AbstractChangeSet changeSet,
        @Nullable RegionWrapper[] allowedRegions, @Nullable Boolean autoQueue,
        @Nullable Boolean fastmode, @Nullable Boolean checkMemory, @Nullable Boolean combineStages,
        @Nullable BlockBag blockBag, @Nullable EditSessionEvent event) {
        this(new EditSessionBuilder(world).actor(actor).limit(limit).changeSet(changeSet).allowedRegions(allowedRegions).autoQueue(autoQueue).fastmode(fastmode).checkMemory(checkMemory).combineStages(combineStages).blockBag(blockBag).eventBus(bus).event(event));
    }

    /**
     * Construct the object with a maximum number of blocks and a block bag.
     *
     * @param eventBus the event bus
     * @param world the world
     * @param maxBlocks the maximum number of blocks that can be changed, or -1 to use no limit
     * @param blockBag an optional {@link BlockBag} to use, otherwise null
     * @param actor the actor that owns the session
     * @param tracing if tracing is enabled. An actor is required if this is {@code true}
     */
    public EditSession(EventBus eventBus, World world, int maxBlocks, @Nullable BlockBag blockBag, EditSessionEvent event) {
        this(eventBus, world, null, null, null, null, true, null, null, null, blockBag, event);
    }

    public EditSession(EditSessionBuilder builder) {
        super(builder.compile().getExtent());
        this.world = builder.getWorld();
        this.bypassHistory = builder.getBypassHistory();
        this.bypassAll = builder.getBypassAll();
        this.originalLimit = builder.getLimit();
        this.limit = builder.getLimit().copy();
        this.actor = builder.getActor();
        this.changeSet = builder.getChangeTask();
        this.minY = world.getMinY();
        this.maxY = world.getMaxY();
        this.blockBag = builder.getBlockBag();
        this.history = changeSet != null;
        this.relighter = builder.getRelighter();
        this.wnaMode = builder.isWNAMode();
        if (builder.isTracing()) {
            this.tracingExtents = new ArrayList<>();
            checkNotNull(actor, "A player is required while tracing");
        } else {
            this.tracingExtents = null;
        }

        this.allowedRegions = builder.getAllowedRegions() != null ? builder.getAllowedRegions().clone() : null;
    }

    /**
     * The limit for this specific edit (blocks etc).
     *
     * @return The limit
     */
    public FaweLimit getLimit() {
        return originalLimit;
    }

    public void resetLimit() {
        this.limit.set(this.originalLimit);
        ExtentTraverser<ProcessedWEExtent> find = new ExtentTraverser<>(getExtent()).find(ProcessedWEExtent.class);
        if (find != null && find.get() != null) {
            find.get().setLimit(this.limit);
        }
    }

    /**
     * Returns a new limit representing how much of this edit's limit has been used so far.
     *
     * @return Limit remaining
     */
    public FaweLimit getLimitUsed() {
        FaweLimit newLimit = new FaweLimit();
        newLimit.MAX_ACTIONS = originalLimit.MAX_ACTIONS - limit.MAX_ACTIONS;
        newLimit.MAX_CHANGES = originalLimit.MAX_CHANGES - limit.MAX_CHANGES;
        newLimit.MAX_FAILS = originalLimit.MAX_FAILS - limit.MAX_FAILS;
        newLimit.MAX_CHECKS = originalLimit.MAX_CHECKS - limit.MAX_CHECKS;
        newLimit.MAX_ITERATIONS = originalLimit.MAX_ITERATIONS - limit.MAX_ITERATIONS;
        newLimit.MAX_BLOCKSTATES = originalLimit.MAX_BLOCKSTATES - limit.MAX_BLOCKSTATES;
        newLimit.MAX_ENTITIES = originalLimit.MAX_ENTITIES - limit.MAX_ENTITIES;
        newLimit.MAX_HISTORY = limit.MAX_HISTORY;
        return newLimit;
    }

    /**
     * Returns the remaining limits.
     *
     * @return remaining limits
     */
    public FaweLimit getLimitLeft() {
        return limit;
    }

    /**
     * Returns the RegionExtent that will restrict an edit, or null.
     *
     * @return FaweRegionExtent (may be null)
     */
    public FaweRegionExtent getRegionExtent() {
        ExtentTraverser<FaweRegionExtent> traverser = new ExtentTraverser<>(getExtent()).find(FaweRegionExtent.class);
        return traverser == null ? null : traverser.get();
    }

    public Extent getBypassAll() {
        return bypassAll;
    }

    public Extent getBypassHistory() {
        return bypassHistory;
    }

    public void setExtent(AbstractDelegateExtent extent) {
        new ExtentTraverser(this).setNext(extent);
    }

    /**
     * Get the Player or null.
     *
     * @return the player
     */
    public Actor getActor() {
        return actor;
    }

    private Extent traceIfNeeded(Extent input) {
        Extent output = input;
        if (tracingExtents != null) {
            TracingExtent newExtent = new TracingExtent(input);
            output = newExtent;
            tracingExtents.add(newExtent);
        }
        return output;
    }

    private Extent wrapExtent(Extent extent, EventBus eventBus, EditSessionEvent event, Stage stage) {
        // NB: the event does its own tracing
        event = event.clone(stage);
        event.setExtent(extent);
        boolean tracing = tracingExtents != null;
        event.setTracing(tracing);
        eventBus.post(event);
        if (tracing) {
            tracingExtents.addAll(event.getTracingExtents());
        }
        return event.getExtent();
    }

    // pkg private for TracedEditSession only, may later become public API
    boolean commitRequired() {
        return false;
    }

    /**
     * Get the current list of active tracing extents.
     */
    private List<TracingExtent> getActiveTracingExtents() {
        if (tracingExtents == null) {
            return List.of();
        }
        return tracingExtents.stream()
            .filter(TracingExtent::isActive)
            .collect(Collectors.toList());
    }

    /**
     * Turns on specific features for a normal WorldEdit session, such as
     * {@link #setBatchingChunks(boolean)
     * chunk batching}.
     */
    public void enableStandardMode() {
        setBatchingChunks(true);
    }

    /**
     * Sets the {@link ReorderMode} of this EditSession, and flushes the session.
     *
     * @param reorderMode The reorder mode
     */
    public void setReorderMode(ReorderMode reorderMode) {
        switch (reorderMode) {
            case MULTI_STAGE:
                enableQueue();
                break;
            case NONE: // Functionally the same, since FAWE doesn't perform physics
            case FAST:
                disableQueue();
                break;
            default:
                throw new UnsupportedOperationException("Not implemented: " + reorderMode);
        }
    }

    /**
     * Get the reorder mode.
     *
     * @return the reorder mode
     */
    public ReorderMode getReorderMode() {
        if (isQueueEnabled()) {
            return ReorderMode.MULTI_STAGE;
        }
        return ReorderMode.FAST;
    }

    /**
     * Get the world.
     *
     * @return the world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Get the underlying {@link ChangeSet}.
     *
     * @return the change set
     */
    public ChangeSet getChangeSet() {
        return changeSet;
    }

    /**
     * Set the ChangeSet without hooking into any recording mechanism or triggering any actions.<br/>
     * Used internally to set the ChangeSet during completion to record custom changes which aren't normally recorded
     * @param set The ChangeSet to set
     */
    public void setRawChangeSet(@Nullable AbstractChangeSet set) {
        changeSet = set;
        changes++;
    }

    /**
     * Get the maximum number of blocks that can be changed. -1 will be returned
     * if it the limit disabled.
     *
     * @return the limit (&gt;= 0) or -1 for no limit
     * @see #getLimit()
     */
    @Deprecated
    public int getBlockChangeLimit() {
        return originalLimit.MAX_CHANGES;
    }

    /**
     * Set the maximum number of blocks that can be changed.
     *
     * @param limit the limit (&gt;= 0) or -1 for no limit
     */
    public void setBlockChangeLimit(int limit) {
        this.limit.MAX_CHANGES = limit;
    }

    /**
     * Returns queue status.
     *
     * @return whether the queue is enabled
     * @deprecated Use {@link EditSession#getReorderMode()} with MULTI_STAGE instead.
     */
    @Deprecated
    public boolean isQueueEnabled() {
        return true;
    }

    /**
     * Queue certain types of block for better reproduction of those blocks. Uses
     * {@link ReorderMode#MULTI_STAGE}.
     *
     * @deprecated Use {@link EditSession#setReorderMode(ReorderMode)} with MULTI_STAGE instead.
     */
    @Deprecated
    public void enableQueue() {
        super.enableQueue();
    }

    /**
     * Disable the queue. This will close the queue.
     */
    @Deprecated
    public void disableQueue() {
        super.disableQueue();
    }

    /**
     * Get the mask.
     *
     * @return mask, may be null
     */
    public Mask getMask() {
        ExtentTraverser<MaskingExtent> maskingExtent = new ExtentTraverser<>(getExtent()).find(MaskingExtent.class);
        return maskingExtent != null ? maskingExtent.get().getMask() : null;
    }

    /**
     * Get the mask.
     *
     * @return mask, may be null
     */
    public Mask getSourceMask() {
        ExtentTraverser<SourceMaskExtent> maskingExtent = new ExtentTraverser<>(getExtent()).find(SourceMaskExtent.class);
        return maskingExtent != null ? maskingExtent.get().getMask() : null;
    }

    @Nullable
    public Region[] getAllowedRegions() {
        return allowedRegions;
    }

    public void addTransform(ResettableExtent transform) {
        checkNotNull(transform);
        transform.setExtent(getExtent());
        new ExtentTraverser(this).setNext(transform);
    }

    @Nullable
    public ResettableExtent getTransform() {
        ExtentTraverser<ResettableExtent> traverser = new ExtentTraverser<>(getExtent()).find(ResettableExtent.class);
        if (traverser != null) {
            return traverser.get();
        }
        return null;
    }

    /**
     * Set a mask.
     *
     * @param mask mask or null
     */
    public void setSourceMask(Mask mask) {
        if (mask == null) {
            mask = Masks.alwaysTrue();
        } else {
            new MaskTraverser(mask).reset(this);
        }
        ExtentTraverser<SourceMaskExtent> maskingExtent = new ExtentTraverser<>(getExtent()).find(SourceMaskExtent.class);
        if (maskingExtent != null && maskingExtent.get() != null) {
            Mask oldMask = maskingExtent.get().getMask();
            if (oldMask instanceof ResettableMask) {
                ((ResettableMask) oldMask).reset();
            }
            maskingExtent.get().setMask(mask);
        } else if (mask != Masks.alwaysTrue()) {
            SourceMaskExtent next = new SourceMaskExtent(getExtent(), mask);
            new ExtentTraverser(this).setNext(next);
        }
    }

    public void addSourceMask(Mask mask) {
        checkNotNull(mask);
        Mask existing = getSourceMask();
        if (existing != null) {
            if (existing instanceof MaskIntersection) {
                Collection<Mask> masks = new HashSet<>(((MaskIntersection) existing).getMasks());
                masks.add(mask);
                mask = new MaskIntersection(masks);
            } else {
                mask = new MaskIntersection(existing, mask);
            }
            mask = mask.optimize();
        }
        setSourceMask(mask);
    }

    /**
     * Set a mask.
     *
     * @param mask mask or null
     */
    public void setMask(Mask mask) {
        if (mask == null) {
            mask = Masks.alwaysTrue();
        } else {
            new MaskTraverser(mask).reset(this);
        }
        ExtentTraverser<MaskingExtent> maskingExtent = new ExtentTraverser<>(getExtent()).find(MaskingExtent.class);
        if (maskingExtent != null && maskingExtent.get() != null) {
            Mask oldMask = maskingExtent.get().getMask();
            if (oldMask instanceof ResettableMask) {
                ((ResettableMask) oldMask).reset();
            }
            maskingExtent.get().setMask(mask);
        } else if (mask != Masks.alwaysTrue()) {
            addProcessor(new MaskingExtent(getExtent(), mask));
        }
    }

    /**
     * Get the {@link SurvivalModeExtent}.
     *
     * @return the survival simulation extent
     */
    public SurvivalModeExtent getSurvivalExtent() {
        ExtentTraverser<SurvivalModeExtent> survivalExtent = new ExtentTraverser<>(getExtent()).find(SurvivalModeExtent.class);
        if (survivalExtent != null) {
            return survivalExtent.get();
        } else { // Kind of a bad way of doing it, but equally I (dords) hate the way upstream does it by just adding ALL possible extents to an edit and only "enabling" when required
            SurvivalModeExtent survival = new SurvivalModeExtent(getExtent(), getWorld());
            setExtent(survival);
            return survival;
        }
    }

    /**
     * Set whether fast mode is enabled.
     *
     * <p>Fast mode may skip lighting checks or adjacent block
     * notification.</p>
     *
     * @param enabled true to enable
     */
    @Deprecated
    public void setFastMode(boolean enabled) {
        disableHistory(enabled);
    }

    /**
     * Set which block updates should occur.
     *
     * @param sideEffectSet side effects to enable
     */
    public void setSideEffectApplier(SideEffectSet sideEffectSet) {
        //Do nothing; TODO: SideEffects currently not fully implemented in FAWE.
    }

    public SideEffectSet getSideEffectApplier() {
        //Do nothing; TODO: SideEffects currently not fully implemented in FAWE.
        return SideEffectSet.defaults();
    }

    /**
     * Disable history (or re-enable)
     *
     * @param disableHistory whether to  enable or disable.
     */
    @Deprecated
    public void disableHistory(boolean disableHistory) {
        if (disableHistory) {
            if (this.history) {
                disableHistory();
                this.history = false;
            }
        } else {
            if (this.history) {
                if (this.changeSet == null) {
                    throw new IllegalArgumentException("History was never provided, cannot enable");
                }
                enableHistory(this.changeSet);
            }
        }
    }

    /**
     * Return fast mode status.
     *
     * <p>Fast mode may skip lighting checks or adjacent block
     * notification.</p>
     *
     * @return true if enabled
     */
    @Deprecated
    public boolean hasFastMode() {
        return getChangeSet() == null;
    }

    /**
     * Get the {@link BlockBag} is used.
     *
     * @return a block bag or null
     */
    public BlockBag getBlockBag() {
        return this.blockBag;
    }

    /**
     * Set a {@link BlockBag} to use.
     *
     * @param blockBag the block bag to set, or null to use none
     */
    public void setBlockBag(BlockBag blockBag) {
        //Not Supported in FAWE
        throw new UnsupportedOperationException("TODO - this is never called anyway");
    }

    @Override
    public String toString() {
        return super.toString() + ":" + getExtent();
    }

    /**
     * Gets the list of missing blocks and clears the list for the next
     * operation.
     *
     * @return a map of missing blocks
     */
    public Map<BlockType, Integer> popMissingBlocks() {
        BlockBag bag = getBlockBag();
        if (bag != null) {
            bag.flushChanges();

            Map<BlockType, Integer> missingBlocks;
            ChangeSet changeSet = getChangeSet();


            if (changeSet instanceof BlockBagChangeSet) {
                missingBlocks = ((BlockBagChangeSet) changeSet).popMissing();
            } else {
                ExtentTraverser<BlockBagExtent> find = new ExtentTraverser<>(getExtent()).find(BlockBagExtent.class);
                if (find != null && find.get() != null) {
                    missingBlocks = find.get().popMissing();
                } else {
                    missingBlocks = null;
                }
            }

            if (missingBlocks != null && !missingBlocks.isEmpty()) {
                StringBuilder str = new StringBuilder();
                int size = missingBlocks.size();
                int i = 0;

                for (Map.Entry<BlockType, Integer> entry : missingBlocks.entrySet()) {
                    BlockType type = entry.getKey();
                    int amount = entry.getValue();
                    str.append((type.getName())).append((amount != 1 ? "x" + amount : ""));
                    ++i;
                    if (i != size) {
                        str.append(", ");
                    }
                }

                actor.print(Caption.of("fawe.error.worldedit.some.fails.blockbag", str.toString()));
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Returns chunk batching status.
     *
     * @return whether chunk batching is enabled
     */
    public boolean isBatchingChunks() {
        return false;
    }

    /**
     * Enable or disable chunk batching. Disabling will flush the session.
     *
     * @param batchingChunks {@code true} to enable, {@code false} to disable
     */
    public void setBatchingChunks(boolean batchingChunks) {
        if (batchingChunks) {
            enableQueue();
        } else {
            disableQueue();
        }
    }

    /**
     * Disable all buffering extents.
     *
     * @see #setReorderMode(ReorderMode)
     * @see #setBatchingChunks(boolean)
     */
    public void disableBuffering() {
        disableQueue();
    }

    /**
     * Check if this session will tick the watchdog.
     *
     * @return {@code true} if any watchdog extent is enabled
     */
    public boolean isTickingWatchdog() {
        return watchdogExtents.stream().anyMatch(WatchdogTickingExtent::isEnabled);
    }

    /**
     * Set all watchdog extents to the given mode.
     */
    public void setTickingWatchdog(boolean active) {
        for (WatchdogTickingExtent extent : watchdogExtents) {
            extent.setEnabled(active);
        }
    }

    /**
     * Get the number of blocks changed, including repeated block changes.
     *
     * <p>This number may not be accurate.</p>
     *
     * @return the number of block changes
     */
    public int getBlockChangeCount() {
        return this.changes;
    }

    @Override
    public boolean fullySupports3DBiomes() {
        return this.getExtent().fullySupports3DBiomes();
    }

    @Override
    public BiomeType getBiome(BlockVector3 position) {
        return this.getExtent().getBiome(position);
    }

    @Override
    public boolean setBiome(BlockVector3 position, BiomeType biome) {
        if (position.getY() < this.minY || position.getY() > this.maxY) {
            return false;
        }
        this.changes++;
        return this.getExtent().setBiome(position, biome);
    }

    @Override
    public boolean setBiome(int x, int y, int z, BiomeType biome) {
        if (y < this.minY || y > this.maxY) {
            return false;
        }
        this.changes++;
        return this.getExtent().setBiome(x, y, z, biome);
    }

    /**
     * Returns the highest solid 'terrain' block.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @param minY minimal height
     * @param maxY maximal height
     * @return height of highest block found or 'minY'
     */
    public int getHighestTerrainBlock(int x, int z, int minY, int maxY) {
        for (int y = maxY; y >= minY; --y) {
            if (getBlock(x, y, z).getBlockType().getMaterial().isMovementBlocker()) {
                return y;
            }
        }
        return minY;
    }

    /**
     * Returns the highest solid 'terrain' block.
     *
     * @param x the X coordinate
     * @param z the Z coordinate
     * @param minY minimal height
     * @param maxY maximal height
     * @param filter a mask of blocks to consider, or null to consider any solid (movement-blocking) block
     * @return height of highest block found or 'minY'
     */
    public int getHighestTerrainBlock(int x, int z, int minY, int maxY, Mask filter) {
        for (int y = maxY; y >= minY; --y) {
            if (filter.test(mutablebv.setComponents(x, y, z))) {
                return y;
            }
        }

        return minY;
    }

    public BlockType getBlockType(int x, int y, int z) {
        return getBlock(x, y, z).getBlockType();
    }

    /**
     * Set a block, bypassing both history and block re-ordering.
     *
     * @param position the position to set the block at
     * @param block the block
     * @param stage the level
     * @return whether the block changed
     * @throws WorldEditException thrown on a set error
     */
    @Deprecated
    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 position, B block, Stage stage) throws WorldEditException {
        if (position.getBlockY() < this.minY || position.getBlockY() > this.maxY) {
            return false;
        }

        this.changes++;
        switch (stage) {
            case BEFORE_HISTORY:
                return this.getExtent().setBlock(position, block);
            case BEFORE_CHANGE:
                return bypassHistory.setBlock(position, block);
            case BEFORE_REORDER:
                return bypassAll.setBlock(position, block);
        }

        throw new RuntimeException("New enum entry added that is unhandled here");
    }

    /**
     * Set a block, bypassing both history and block re-ordering.
     *
     * @param position the position to set the block at
     * @param block the block
     * @return whether the block changed
     */
    @Deprecated
    public <B extends BlockStateHolder<B>> boolean rawSetBlock(BlockVector3 position, B block) {
        if (position.getBlockY() < this.minY || position.getBlockY() > this.maxY) {
            return false;
        }

        this.changes++;
        try {
            return bypassAll.setBlock(position, block);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    /**
     * Set a block, bypassing history but still utilizing block re-ordering.
     *
     * @param position the position to set the block at
     * @param block the block
     * @return whether the block changed
     */
    public <B extends BlockStateHolder<B>> boolean smartSetBlock(BlockVector3 position, B block) {
        if (position.getBlockY() < this.minY || position.getBlockY() > this.maxY) {
            return false;
        }

        this.changes++;
        try {
            return setBlock(position, block, Stage.BEFORE_REORDER);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    @Override
    @Deprecated
    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 position, B block) throws MaxChangedBlocksException {
        if (position.getBlockY() < this.minY || position.getBlockY() > this.maxY) {
            return false;
        }

        this.changes++;
        try {
            return this.getExtent().setBlock(position, block);
        } catch (MaxChangedBlocksException e) {
            throw e;
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }


    @Override
    public <B extends BlockStateHolder<B>> boolean setBlock(int x, int y, int z, B block) {
        if (y < this.minY || y > this.maxY) {
            return false;
        }

        this.changes++;
        try {
            return this.getExtent().setBlock(x, y, z, block);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    /**
     * Sets the block at a position, subject to both history and block re-ordering.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param pattern a pattern to use
     * @return Whether the block changed -- not entirely dependable
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public boolean setBlock(int x, int y, int z, Pattern pattern) {
        if (y < this.minY || y > this.maxY) {
            return false;
        }

        this.changes++;
        try {
            BlockVector3 bv = mutablebv.setComponents(x, y, z);
            return pattern.apply(getExtent(), bv, bv);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    /**
     * Sets the block at a position, subject to both history and block re-ordering.
     *
     * @param position the position
     * @param pattern a pattern to use
     * @return Whether the block changed -- not entirely dependable
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public boolean setBlock(BlockVector3 position, Pattern pattern) throws MaxChangedBlocksException {
        if (position.getBlockY() < this.minY || position.getBlockY() > this.maxY) {
            return false;
        }

        this.changes++;
        try {
            return pattern.apply(this.getExtent(), position, position);
        } catch (WorldEditException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <B extends BlockStateHolder<B>> int setBlocks(Region region, B block) throws MaxChangedBlocksException {
        return this.changes = super.setBlocks(region, block);
    }

    @Override
    public int setBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        return this.changes = super.setBlocks(region, pattern);
    }

    /**
     * Restores all blocks to their initial state.
     *
     * @param editSession a new {@link EditSession} to perform the undo in
     */
    public void undo(EditSession editSession) {
        UndoContext context = new UndoContext();
        context.setExtent(editSession.bypassAll);
        ChangeSet changeSet = getChangeSet();
        setChangeSet(null);
        Operations.completeBlindly(ChangeSetExecutor.create(changeSet, context, ChangeSetExecutor.Type.UNDO, editSession.getBlockBag(), editSession.getLimit().INVENTORY_MODE));
        flushQueue();
        editSession.changes = 1;
    }

    public void setBlocks(ChangeSet changeSet, ChangeSetExecutor.Type type) {
        final UndoContext context = new UndoContext();
        context.setExtent(bypassAll);
        Operations.completeBlindly(ChangeSetExecutor.create(changeSet, context, type, getBlockBag(), getLimit().INVENTORY_MODE));
        flushQueue();
        changes = 1;
    }

    /**
     * Sets to new state.
     *
     * @param editSession a new {@link EditSession} to perform the redo in
     */
    public void redo(EditSession editSession) {
        UndoContext context = new UndoContext();
        context.setExtent(editSession.bypassAll);
        ChangeSet changeSet = getChangeSet();
        setChangeSet(null);
        Operations.completeBlindly(ChangeSetExecutor.create(changeSet, context, ChangeSetExecutor.Type.REDO, editSession.getBlockBag(), editSession.getLimit().INVENTORY_MODE));
        flushQueue();
        editSession.changes = 1;
    }

    /**
     * Get the number of changed blocks.
     *
     * @return the number of changes
     */
    public int size() {
        return getBlockChangeCount();
    }

    public void setSize(int size) {
        this.changes = size;
    }

    /**
     * Closing an EditSession flushes its buffers to the world, and performs other
     * cleanup tasks.
     */
    @Override
    public void close() {
        internalFlushSession();
        dumpTracingInformation();
    }

    private void dumpTracingInformation() {
        if (this.tracingExtents == null) {
            return;
        }
        List<TracingExtent> tracingExtents = getActiveTracingExtents();
        assert actor != null;
        if (tracingExtents.isEmpty()) {
            actor.printError(TranslatableComponent.of("worldedit.trace.no-tracing-extents"));
            return;
        }
        // find the common stacks
        Set<List<TracingExtent>> stacks = new LinkedHashSet<>();
        Map<List<TracingExtent>, BlockVector3> stackToPosition = new HashMap<>();
        Set<BlockVector3> touchedLocations = Collections.newSetFromMap(BlockMap.create());
        for (TracingExtent tracingExtent : tracingExtents) {
            touchedLocations.addAll(tracingExtent.getTouchedLocations());
        }
        for (BlockVector3 loc : touchedLocations) {
            List<TracingExtent> stack = tracingExtents.stream()
                    .filter(it -> it.getTouchedLocations().contains(loc))
                    .collect(Collectors.toList());
            boolean anyFailed = stack.stream()
                .anyMatch(it -> it.getFailedActions().containsKey(loc));
            if (anyFailed && stacks.add(stack)) {
                stackToPosition.put(stack, loc);
            }
        }
        stackToPosition.forEach((stack, position) -> {
            // stack can never be empty, something has to have touched the position
            TracingExtent failure = stack.get(0);
            actor.printDebug(TranslatableComponent.builder("worldedit.trace.action-failed")
                                                  .args(
                    TextComponent.of(failure.getFailedActions().get(position).toString()),
                    TextComponent.of(position.toString()),
                    TextComponent.of(failure.getExtent().getClass().getName())
                )
                                                  .build());
        });
    }

    /**
     * Communicate to the EditSession that all block changes are complete,
     * and that it should apply them to the world.
     *
     * @deprecated Replace with {@link #close()} for proper cleanup behavior.
     */
    @Deprecated
    public void flushSession() {
        flushQueue();
    }

    /**
     * Finish off the queue.
     */
    public void flushQueue() {
        internalFlushSession();
    }

    private void internalFlushSession() {
        Operations.completeBlindly(commit());
        // Check fails
        FaweLimit used = getLimitUsed();
        if (used.MAX_FAILS > 0) {
            if (used.MAX_CHANGES > 0 || used.MAX_ENTITIES > 0) {
                actor.print(Caption.of("fawe.error.worldedit.some.fails", used.MAX_FAILS));
            } else if (new ExtentTraverser<>(getExtent()).findAndGet(FaweRegionExtent.class) != null) {
                actor.print(Caption.of("fawe.cancel.worldedit.cancel.reason.outside.region"));
            } else {
                actor.print(Caption.of("fawe.cancel.worldedit.cancel.reason.outside.level"));
            }
        }
        if (wnaMode) {
            getWorld().flush();
        }
        // Reset limit
        limit.set(originalLimit);
        try {
            if (relighter != null && !(relighter instanceof NullRelighter)) {
                // Don't relight twice!
                if (!relighter.isFinished() && relighter.getLock().tryLock()) {
                    try {
                        if (Settings.IMP.LIGHTING.REMOVE_FIRST) {
                            relighter.removeAndRelight(true);
                        } else {
                            relighter.fixLightingSafe(true);
                        }
                    } finally {
                        relighter.getLock().unlock();
                    }
                }
            }
        } catch (Throwable e) {
            actor.print(Caption.of("fawe.error.lighting"));
            e.printStackTrace();
        }
        // Enqueue it
        if (getChangeSet() != null) {
            if (Settings.IMP.HISTORY.COMBINE_STAGES) {
                ((AbstractChangeSet) getChangeSet()).closeAsync();
            } else {
                try {
                    getChangeSet().close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public <B extends BlockStateHolder<B>> int fall(final Region region, boolean fullHeight, final B replace) {
        FlatRegion flat = asFlatRegion(region);
        final int startPerformY = region.getMinimumPoint().getBlockY();
        final int startCheckY = fullHeight ? 0 : startPerformY;
        final int endY = region.getMaximumPoint().getBlockY();
        RegionVisitor visitor = new RegionVisitor(flat, pos -> {
            int x = pos.getX();
            int z = pos.getZ();
            int freeSpot = startCheckY;
            for (int y = startCheckY; y <= endY; y++) {
                if (y < startPerformY) {
                    if (!getBlockType(x, y, z).getMaterial().isAir()) {
                        freeSpot = y + 1;
                    }
                    continue;
                }
                BlockType block = getBlockType(x, y, z);
                if (!block.getMaterial().isAir()) {
                    if (freeSpot != y) {
                        setBlock(x, freeSpot, z, block);
                        setBlock(x, y, z, replace);
                    }
                    freeSpot++;
                }
            }
            return true;
        });
        Operations.completeBlindly(visitor);
        return this.changes;
    }

    @Override
    public <B extends BlockStateHolder<B>> int replaceBlocks(Region region, Set<BaseBlock> filter, B replacement) throws MaxChangedBlocksException {
        return this.changes = super.replaceBlocks(region, filter, replacement);
    }

    @Override
    public int replaceBlocks(Region region, Set<BaseBlock> filter, Pattern pattern) throws MaxChangedBlocksException {
        return this.changes = super.replaceBlocks(region, filter, pattern);
    }

    @Override
    public int replaceBlocks(Region region, Mask mask, Pattern pattern) throws MaxChangedBlocksException {
        return this.changes = super.replaceBlocks(region, mask, pattern);
    }

    /**
     * Fills an area recursively in the X/Z directions.
     *
     * @param origin the location to start from
     * @param pattern the block to fill with
     * @param radius the radius of the spherical area to fill
     * @param depth the maximum depth, starting from the origin
     * @param direction the direction to fill
     * @return the number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int fillDirection(final BlockVector3 origin, final Pattern pattern, final double radius, final int depth, BlockVector3 direction) throws MaxChangedBlocksException {
        checkNotNull(origin);
        checkNotNull(pattern);
        checkArgument(radius >= 0, "radius >= 0");
        checkArgument(depth >= 1, "depth >= 1");
        if (direction.equals(BlockVector3.at(0, -1, 0))) {
            return fillXZ(origin, pattern, radius, depth, false);
        }
        Mask mask = new MaskIntersection(new RegionMask(new EllipsoidRegion(null, origin, Vector3.at(radius, radius, radius))), Masks.negate(new ExistingBlockMask(EditSession.this)));
        // Want to replace blocks
        final BlockReplace replace = new BlockReplace(EditSession.this, pattern);

        // Pick how we're going to visit blocks
        RecursiveVisitor visitor = new DirectionalVisitor(mask, replace, origin, direction, (int) (radius * 2 + 1));

        // Start at the origin
        visitor.visit(origin);

        // Execute
        Operations.completeBlindly(visitor);
        return this.changes = visitor.getAffected();
    }

    /**
     * Fills an area recursively in the X/Z directions.
     *
     * @param origin the location to start from
     * @param block the block to fill with
     * @param radius the radius of the spherical area to fill
     * @param depth the maximum depth, starting from the origin
     * @param recursive whether a breadth-first search should be performed
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public <B extends BlockStateHolder<B>> int fillXZ(BlockVector3 origin, B block, double radius, int depth, boolean recursive) throws MaxChangedBlocksException {
        return fillXZ(origin, (Pattern) block, radius, depth, recursive);
    }

    /**
     * Fills an area recursively in the X/Z directions.
     *
     * @param origin the origin to start the fill from
     * @param pattern the pattern to fill with
     * @param radius the radius of the spherical area to fill, with 0 as the smallest radius
     * @param depth the maximum depth, starting from the origin, with 1 as the smallest depth
     * @param recursive whether a breadth-first search should be performed
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int fillXZ(BlockVector3 origin, Pattern pattern, double radius, int depth, boolean recursive) throws MaxChangedBlocksException {
        checkNotNull(origin);
        checkNotNull(pattern);
        checkArgument(radius >= 0, "radius >= 0");
        checkArgument(depth >= 1, "depth >= 1");

        Mask mask = new MaskIntersection(
                new RegionMask(new EllipsoidRegion(null, origin, Vector3.at(radius, radius, radius))),
                new BoundedHeightMask(
                        Math.max(origin.getBlockY() - depth + 1, getMinimumPoint().getBlockY()),
                        Math.min(getMaxY(), origin.getBlockY())),
                Masks.negate(new ExistingBlockMask(this)));

        // Want to replace blocks
        BlockReplace replace = new BlockReplace(this, pattern);

        // Pick how we're going to visit blocks
        RecursiveVisitor visitor;
        if (recursive) {
            visitor = new RecursiveVisitor(mask, replace, (int) (radius * 2 + 1));
        } else {
            visitor = new DownwardVisitor(mask, replace, origin.getBlockY(), (int) (radius * 2 + 1));
        }

        // Start at the origin
        visitor.visit(origin);

        // Execute
        Operations.completeLegacy(visitor);

        return this.changes = visitor.getAffected();
    }

    /**
     * Remove a cuboid above the given position with a given apothem and a given height.
     *
     * @param position base position
     * @param apothem an apothem of the cuboid (on the XZ plane), where the minimum is 1
     * @param height the height of the cuboid, where the minimum is 1
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int removeAbove(BlockVector3 position, int apothem, int height) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");
        checkArgument(height >= 1, "height >= 1");

        Region region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(-apothem + 1, 0, -apothem + 1),
                position.add(apothem - 1, height - 1, apothem - 1));
        return setBlocks(region, BlockTypes.AIR.getDefaultState());
    }

    /**
     * Remove a cuboid below the given position with a given apothem and a given height.
     *
     * @param position base position
     * @param apothem an apothem of the cuboid (on the XZ plane), where the minimum is 1
     * @param height the height of the cuboid, where the minimum is 1
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int removeBelow(BlockVector3 position, int apothem, int height) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");
        checkArgument(height >= 1, "height >= 1");

        Region region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(-apothem + 1, 0, -apothem + 1),
                position.add(apothem - 1, -height + 1, apothem - 1));
        return setBlocks(region, BlockTypes.AIR.getDefaultState());
    }

    /**
     * Remove blocks of a certain type nearby a given position.
     *
     * @param position center position of cuboid
     * @param mask the mask to match
     * @param apothem an apothem of the cuboid, where the minimum is 1
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int removeNear(BlockVector3 position, Mask mask, int apothem) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");

        BlockVector3 adjustment = BlockVector3.ONE.multiply(apothem - 1);
        Region region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(adjustment.multiply(-1)),
                position.add(adjustment));
        return replaceBlocks(region, mask, BlockTypes.AIR.getDefaultState());
    }

    /**
     * Sets the blocks at the center of the given region to the given pattern.
     * If the center sits between two blocks on a certain axis, then two blocks
     * will be placed to mark the center.
     *
     * @param region the region to find the center of
     * @param pattern the replacement pattern
     * @return the number of blocks placed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int center(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        Vector3 center = region.getCenter();
        Region centerRegion = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                BlockVector3.at(((int) center.getX()), ((int) center.getY()), ((int) center.getZ())),
                BlockVector3.at(
                        MathUtils.roundHalfUp(center.getX()),
                        MathUtils.roundHalfUp(center.getY()),
                        MathUtils.roundHalfUp(center.getZ())));
        return setBlocks(centerRegion, pattern);
    }

    /**
     * Make the faces of the given region as if it was a {@link CuboidRegion}.
     *
     * @param region the region
     * @param block the block to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @deprecated Use {@link EditSession#makeCuboidFaces(Region, Pattern)}.
     */
    @Deprecated
    public <B extends BlockStateHolder<B>> int makeCuboidFaces(Region region, B block) throws MaxChangedBlocksException {
        return makeCuboidFaces(region, (Pattern) block);
    }

    /**
     * Make the faces of the given region as if it was a {@link CuboidRegion}.
     *
     * @param region the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeCuboidFaces(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        CuboidRegion cuboid = CuboidRegion.makeCuboid(region);
        Region faces = cuboid.getFaces();
        return setBlocks(faces, pattern);
    }

    /**
     * Make the faces of the given region. The method by which the faces are found
     * may be inefficient, because there may not be an efficient implementation supported
     * for that specific shape.
     *
     * @param region the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeFaces(final Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        if (region instanceof CuboidRegion) {
            return makeCuboidFaces(region, pattern);
        } else {
            return new RegionShape(region).generate(this, pattern, true);
        }
    }


    /**
     * Make the walls (all faces but those parallel to the X-Z plane) of the given region
     * as if it was a {@link CuboidRegion}.
     *
     * @param region the region
     * @param block the block to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public <B extends BlockStateHolder<B>> int makeCuboidWalls(Region region, B block) throws MaxChangedBlocksException {
        return makeCuboidWalls(region, (Pattern) block);
    }

    /**
     * Make the walls (all faces but those parallel to the X-Z plane) of the given region
     * as if it was a {@link CuboidRegion}.
     *
     * @param region the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeCuboidWalls(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        int blocksChanged = 0;
        for (Region wall : CuboidRegion.makeCuboid(region).getWalls().getRegions()) {
            blocksChanged += setBlocks(wall, pattern);
        }
        return blocksChanged;
    }

    /**
     * Make the walls of the given region. The method by which the walls are found
     * may be inefficient, because there may not be an efficient implementation supported
     * for that specific shape.
     *
     * @param region the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeWalls(final Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        if (region instanceof CuboidRegion) {
            return makeCuboidWalls(region, pattern);
        } else {
            replaceBlocks(region, new WallMakeMask(region), pattern);
        }
        return changes;
    }

    /**
     * Places a layer of blocks on top of ground blocks in the given region
     * (as if it were a cuboid).
     *
     * @param region the region
     * @param block the placed block
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @deprecated Use {@link EditSession#overlayCuboidBlocks(Region, Pattern)}.
     */
    @Deprecated
    public <B extends BlockStateHolder<B>> int overlayCuboidBlocks(Region region, B block) throws MaxChangedBlocksException {
        checkNotNull(block);

        return overlayCuboidBlocks(region, (Pattern) block);
    }

    /**
     * Places a layer of blocks on top of ground blocks in the given region
     * (as if it were a cuboid).
     *
     * @param region the region
     * @param pattern the placed block pattern
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int overlayCuboidBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        BlockReplace replace = new BlockReplace(this, pattern);
        RegionOffset offset = new RegionOffset(BlockVector3.UNIT_Y, replace);
        int minY = region.getMinimumPoint().getBlockY();
        int maxY = Math.min(getMaximumPoint().getBlockY(), region.getMaximumPoint().getBlockY() + 1);
        SurfaceRegionFunction surface = new SurfaceRegionFunction(this, offset, minY, maxY);
        FlatRegionVisitor visitor = new FlatRegionVisitor(asFlatRegion(region), surface);
        Operations.completeBlindly(visitor);
        return this.changes = visitor.getAffected();
    }

    /**
     * Turns the first 3 layers into dirt/grass and the bottom layers
     * into rock, like a natural Minecraft mountain.
     *
     * @param region the region to affect
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int naturalizeCuboidBlocks(Region region) throws MaxChangedBlocksException {
        checkNotNull(region);

        Naturalizer naturalizer = new Naturalizer(this);
        FlatRegion flatRegion = Regions.asFlatRegion(region);
        LayerVisitor visitor = new LayerVisitor(flatRegion, minimumBlockY(region), maximumBlockY(region), naturalizer);
        Operations.completeBlindly(visitor);
        return this.changes = naturalizer.getAffected();
    }

    /**
     * Stack a cuboid region. For compatibility, entities are copied by biomes are not.
     * Use {@link #stackCuboidRegion(Region, BlockVector3, int, boolean, boolean, Mask)} to fine tune.
     *
     * @param region the region to stack
     * @param dir the direction to stack
     * @param count the number of times to stack
     * @param copyAir true to also copy air blocks
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int stackCuboidRegion(Region region, BlockVector3 dir, int count, boolean copyAir) throws MaxChangedBlocksException {
        return stackCuboidRegion(region, dir, count, true, false, copyAir ? null : new ExistingBlockMask(this));
    }

    /**
     * Stack a cuboid region.
     *
     * @param region the region to stack
     * @param dir the direction to stack
     * @param count the number of times to stack
     * @param copyEntities true to copy entities
     * @param copyBiomes true to copy biomes
     * @param mask source mask for the operation (only matching blocks are copied)
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int stackCuboidRegion(Region region, BlockVector3 dir, int count,
                                 boolean copyEntities, boolean copyBiomes, Mask mask) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(dir);
        checkArgument(count >= 1, "count >= 1 required");

        BlockVector3 size = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);
        BlockVector3 to = region.getMinimumPoint();
        ForwardExtentCopy copy = new ForwardExtentCopy(this, region, this, to);
        copy.setRepetitions(count);
        copy.setTransform(new AffineTransform().translate(dir.multiply(size)));
        copy.setCopyingEntities(copyEntities);
        copy.setCopyingBiomes(copyBiomes);
        final Region allowedRegion;
        if (allowedRegions == null || allowedRegions.length == 0) {
            allowedRegion = new NullRegion();
        } else {
            allowedRegion = new RegionIntersection(allowedRegions);
        }
        mask = MaskIntersection.of(getSourceMask(), mask, new RegionMask(allowedRegion)).optimize();
        if (mask != Masks.alwaysTrue()) {
            setSourceMask(null);
            copy.setSourceMask(mask);
        }
        Operations.completeLegacy(copy);
        return this.changes = copy.getAffected();
    }

    /**
     * Move the blocks in a region a certain direction.
     *
     * @param region the region to move
     * @param dir the direction
     * @param distance the distance to move
     * @param copyAir true to copy air blocks
     * @param moveEntities true to move entities
     * @param copyBiomes true to copy biomes
     * @param replacement the replacement pattern to fill in after moving, or null to use air
     * @return number of blocks moved
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int moveRegion(Region region, BlockVector3 dir, int distance, boolean copyAir, boolean moveEntities, boolean copyBiomes, Pattern replacement) throws MaxChangedBlocksException {
        Mask mask = null;
        if (!copyAir) {
            mask = new ExistingBlockMask(this);
        }
        return moveRegion(region, dir, distance, moveEntities, copyBiomes, mask, replacement);
    }

    /**
     * Move the blocks in a region a certain direction.
     *
     * @param region the region to move
     * @param dir the direction
     * @param distance the distance to move
     * @param moveEntities true to move entities
     * @param copyBiomes true to copy biomes (source biome is unchanged)
     * @param mask source mask for the operation (only matching blocks are moved)
     * @param replacement the replacement pattern to fill in after moving, or null to use air
     * @return number of blocks moved
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @throws IllegalArgumentException thrown if the region is not a flat region, but copyBiomes is true
     */
    public int moveRegion(Region region, BlockVector3 dir, int distance,
                          boolean moveEntities, boolean copyBiomes, Mask mask, Pattern replacement) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(dir);
        checkArgument(distance >= 1, "distance >= 1 required");
        checkArgument(!copyBiomes || region instanceof FlatRegion, "can't copy biomes from non-flat region");

        BlockVector3 to = region.getMinimumPoint().add(dir.multiply(distance));

        final BlockVector3 displace = dir.multiply(distance);
        final BlockVector3 size = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);

        BlockVector3 disAbs = displace.abs();

        if (disAbs.getBlockX() < size.getBlockX() && disAbs.getBlockY() < size.getBlockY() && disAbs.getBlockZ() < size.getBlockZ()) {
            // Buffer if overlapping
            enableQueue();
        }

        ForwardExtentCopy copy = new ForwardExtentCopy(this, region, this, to);

        if (replacement == null) {
            replacement = BlockTypes.AIR.getDefaultState();
        }
        BlockReplace remove = replacement instanceof ExistingPattern ? null : new BlockReplace(this, replacement);
        copy.setSourceFunction(remove); // Remove

        copy.setCopyingEntities(moveEntities);
        copy.setRemovingEntities(moveEntities);
        copy.setCopyingBiomes(copyBiomes);
        copy.setRepetitions(1);
        final Region allowedRegion;
        if (allowedRegions == null || allowedRegions.length == 0) {
            allowedRegion = new NullRegion();
        } else {
            allowedRegion = new RegionIntersection(allowedRegions);
        }
        Mask sourceMask = this.getSourceMask();
        mask = MaskIntersection.of(sourceMask, mask, new RegionMask(allowedRegion)).optimize();
        if (mask != Masks.alwaysTrue()) {
            copy.setSourceMask(mask);
            if (sourceMask != null && sourceMask.equals(mask)) {
                setSourceMask(null);
            }
        }
        Operations.completeBlindly(copy);
        return this.changes = copy.getAffected();
    }

    /**
     * Move the blocks in a region a certain direction.
     *
     * @param region the region to move
     * @param dir the direction
     * @param distance the distance to move
     * @param copyAir true to copy air blocks
     * @param replacement the replacement pattern to fill in after moving, or null to use air
     * @return number of blocks moved
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int moveCuboidRegion(Region region, BlockVector3 dir, int distance, boolean copyAir, Pattern replacement) throws MaxChangedBlocksException {
        return moveRegion(region, dir, distance, copyAir, true, false, replacement);
    }

    /**
     * Drain nearby pools of water or lava.
     *
     * @param origin the origin to drain from, which will search a 3x3 area
     * @param radius the radius of the removal, where a value should be 0 or greater
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int drainArea(BlockVector3 origin, double radius) throws MaxChangedBlocksException {
        return drainArea(origin, radius, false);
    }

    /**
     * Drain nearby pools of water or lava, optionally removed waterlogged states from blocks.
     *
     * @param origin the origin to drain from, which will search a 3x3 area
     * @param radius the radius of the removal, where a value should be 0 or greater
     * @param waterlogged true to make waterlogged blocks non-waterlogged as well
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int drainArea(BlockVector3 origin, double radius, boolean waterlogged) throws MaxChangedBlocksException {
        return drainArea(origin, radius, waterlogged, false);
    }

    /**
     * Drain nearby pools of water or lava, optionally removed waterlogged states from blocks.
     *
     * @param origin the origin to drain from, which will search a 3x3 area
     * @param radius the radius of the removal, where a value should be 0 or greater
     * @param waterlogged true to make waterlogged blocks non-waterlogged as well
     * @param plants true to remove underwater plants
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int drainArea(BlockVector3 origin, double radius, boolean waterlogged, boolean plants) throws MaxChangedBlocksException {
        checkNotNull(origin);
        checkArgument(radius >= 0, "radius >= 0 required");

        Mask liquidMask;
        if (plants) {
            liquidMask = new BlockTypeMask(this, BlockTypes.LAVA, BlockTypes.WATER,
                                           BlockTypes.KELP_PLANT, BlockTypes.KELP, BlockTypes.SEAGRASS, BlockTypes.TALL_SEAGRASS);
        } else {
            liquidMask = new BlockTypeMask(this, BlockTypes.LAVA, BlockTypes.WATER);
        }
        if (waterlogged) {
            Map<String, String> stateMap = new HashMap<>();
            stateMap.put("waterlogged", "true");
            liquidMask = new MaskUnion(liquidMask, new BlockStateMask(this, stateMap, true));
        }
        Mask mask = new MaskIntersection(
            new BoundedHeightMask(0, getWorld().getMaxY()),
            new RegionMask(new EllipsoidRegion(null, origin, Vector3.at(radius, radius, radius))),
            liquidMask);
        BlockReplace replace;
        if (waterlogged) {
            replace = new BlockReplace(this, new WaterloggedRemover(this));
        } else {
            replace = new BlockReplace(this, BlockTypes.AIR.getDefaultState());
        }
        RecursiveVisitor visitor = new RecursiveVisitor(mask, replace, (int) (radius * 2 + 1));

        // Around the origin in a 3x3 block
        for (BlockVector3 position : CuboidRegion.fromCenter(origin, 1)) {
            if (mask.test(position)) {
                visitor.visit(position);
            }
        }

        Operations.completeLegacy(visitor);

        return this.changes = visitor.getAffected();
    }

    /**
     * Fix liquids so that they turn into stationary blocks and extend outward.
     *
     * @param origin the original position
     * @param radius the radius to fix
     * @param fluid the type of the fluid
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int fixLiquid(BlockVector3 origin, double radius, BlockType fluid) throws MaxChangedBlocksException {
        checkNotNull(origin);
        checkArgument(radius >= 0, "radius >= 0 required");

        // Our origins can only be liquids
        Mask liquidMask = new SingleBlockTypeMask(this, fluid);

        // But we will also visit air blocks
        MaskIntersection blockMask = new MaskUnion(liquidMask, Masks.negate(new ExistingBlockMask(this)));

        // There are boundaries that the routine needs to stay in
        Mask mask = new MaskIntersection(
                new BoundedHeightMask(0, Math.min(origin.getBlockY(), getWorld().getMaxY())),
                new RegionMask(new EllipsoidRegion(null, origin, Vector3.at(radius, radius, radius))),
                blockMask
        );

        BlockReplace replace = new BlockReplace(this, fluid.getDefaultState());
        NonRisingVisitor visitor = new NonRisingVisitor(mask, replace);

        // Around the origin in a 3x3 block
        for (BlockVector3 position : CuboidRegion.fromCenter(origin, 1)) {
            if (liquidMask.test(position)) {
                visitor.visit(position);
            }
        }

        Operations.completeLegacy(visitor);

        return visitor.getAffected();
    }

    /**
     * Makes a cylinder.
     *
     * @param pos Center of the cylinder
     * @param block The block pattern to use
     * @param radius The cylinder's radius
     * @param height The cylinder's up/down extent. If negative, extend downward.
     * @param filled If false, only a shell will be generated.
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeCylinder(BlockVector3 pos, Pattern block, double radius, int height, boolean filled) throws MaxChangedBlocksException {
        return makeCylinder(pos, block, radius, radius, height, filled);
    }

    /**
     * Makes a cylinder.
     *
     * @param pos Center of the cylinder
     * @param block The block pattern to use
     * @param radiusX The cylinder's largest north/south extent
     * @param radiusZ The cylinder's largest east/west extent
     * @param height The cylinder's up/down extent. If negative, extend downward.
     * @param filled If false, only a shell will be generated.
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeCylinder(BlockVector3 pos, Pattern block, double radiusX, double radiusZ, int height, boolean filled) throws MaxChangedBlocksException {
        return makeCylinder(pos, block, radiusX, radiusZ, height, 0, filled);
    }

    public int makeHollowCylinder(BlockVector3 pos, final Pattern block, double radiusX, double radiusZ, int height, double thickness) throws MaxChangedBlocksException {
        return makeCylinder(pos, block, radiusX, radiusZ, height, thickness, false);
    }

    private int makeCylinder(BlockVector3 pos, Pattern block, double radiusX, double radiusZ, int height, double thickness, boolean filled) throws MaxChangedBlocksException {
        radiusX += 0.5;
        radiusZ += 0.5;

        MutableBlockVector3 posv = new MutableBlockVector3(pos);
        if (height == 0) {
            return 0;
        } else if (height < 0) {
            height = -height;
            posv.mutY(posv.getY() - height);
        }

        if (posv.getBlockY() < 0) {
            posv.mutY(0);
        } else if (posv.getBlockY() + height - 1 > maxY) {
            height = maxY - posv.getBlockY() + 1;
        }

        final double invRadiusX = 1 / radiusX;
        final double invRadiusZ = 1 / radiusZ;

        int px = posv.getBlockX();
        int py = posv.getBlockY();
        int pz = posv.getBlockZ();
        MutableBlockVector3 mutable = new MutableBlockVector3();

        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);

        double xSqr;
        double zSqr;
        double distanceSq;
        double nextXn = 0;

        if (thickness != 0) {
            double nextMinXn = 0;
            final double minInvRadiusX = 1 / (radiusX - thickness);
            final double minInvRadiusZ = 1 / (radiusZ - thickness);
            forX: for (int x = 0; x <= ceilRadiusX; ++x) {
                final double xn = nextXn;
                double dx2 = nextMinXn * nextMinXn;
                nextXn = (x + 1) * invRadiusX;
                nextMinXn = (x + 1) * minInvRadiusX;
                double nextZn = 0;
                double nextMinZn = 0;
                xSqr = xn * xn;
                forZ: for (int z = 0; z <= ceilRadiusZ; ++z) {
                    final double zn = nextZn;
                    double dz2 = nextMinZn * nextMinZn;
                    nextZn = (z + 1) * invRadiusZ;
                    nextMinZn = (z + 1) * minInvRadiusZ;
                    zSqr = zn * zn;
                    distanceSq = xSqr + zSqr;
                    if (distanceSq > 1) {
                        if (z == 0) {
                            break forX;
                        }
                        break forZ;
                    }

                    if ((dz2 + nextMinXn * nextMinXn <= 1) && (nextMinZn * nextMinZn + dx2 <= 1)) {
                        continue;
                    }

                    for (int y = 0; y < height; ++y) {
                        this.setBlock(mutable.setComponents(px + x, py + y, pz + z), block);
                        this.setBlock(mutable.setComponents(px - x, py + y, pz + z), block);
                        this.setBlock(mutable.setComponents(px + x, py + y, pz - z), block);
                        this.setBlock(mutable.setComponents(px - x, py + y, pz - z), block);
                    }
                }
            }
        } else {
            forX: for (int x = 0; x <= ceilRadiusX; ++x) {
                final double xn = nextXn;
                nextXn = (x + 1) * invRadiusX;
                double nextZn = 0;
                xSqr = xn * xn;
                forZ: for (int z = 0; z <= ceilRadiusZ; ++z) {
                    final double zn = nextZn;
                    nextZn = (z + 1) * invRadiusZ;
                    zSqr = zn * zn;
                    distanceSq = xSqr + zSqr;
                    if (distanceSq > 1) {
                        if (z == 0) {
                            break forX;
                        }
                        break forZ;
                    }

                    if (!filled) {
                        if ((zSqr + nextXn * nextXn <= 1) && (nextZn * nextZn + xSqr <= 1)) {
                            continue;
                        }
                    }

                    for (int y = 0; y < height; ++y) {
                        this.setBlock(mutable.setComponents(px + x, py + y, pz + z), block);
                        this.setBlock(mutable.setComponents(px - x, py + y, pz + z), block);
                        this.setBlock(mutable.setComponents(px + x, py + y, pz - z), block);
                        this.setBlock(mutable.setComponents(px - x, py + y, pz - z), block);
                    }
                }
            }
        }

        return this.changes;
    }

    /**
     * Move the blocks in a region a certain direction.
     *
     * @param region the region to move
     * @param dir the direction
     * @param distance the distance to move
     * @param copyAir true to copy air blocks
     * @param replacement the replacement pattern to fill in after moving, or null to use air
     * @return number of blocks moved
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int moveRegion(Region region, BlockVector3 dir, int distance, boolean copyAir, Pattern replacement) throws MaxChangedBlocksException {
        return moveRegion(region, dir, distance, true, false, copyAir ? new ExistingBlockMask(this) : null, replacement);
    }

    public int makeCircle(BlockVector3 pos, final Pattern block, double radiusX, double radiusY, double radiusZ, boolean filled, Vector3 normal) throws MaxChangedBlocksException {
        radiusX += 0.5;
        radiusY += 0.5;
        radiusZ += 0.5;

        normal = normal.normalize();
        double nx = normal.getX();
        double ny = normal.getY();
        double nz = normal.getZ();


        final double invRadiusX = 1 / radiusX;
        final double invRadiusY = 1 / radiusY;
        final double invRadiusZ = 1 / radiusZ;

        int px = pos.getBlockX();
        int py = pos.getBlockY();
        int pz = pos.getBlockZ();
        MutableBlockVector3 mutable = new MutableBlockVector3();

        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusY = (int) Math.ceil(radiusY);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);

        double threshold = 0.5;

        double nextXn = 0;
        double dx;
        double dy;
        double dz;
        double dxy;
        double dxyz;
        forX:
        for (int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            dx = xn * xn;
            nextXn = (x + 1) * invRadiusX;
            double nextYn = 0;
            forY:
            for (int y = 0; y <= ceilRadiusY; ++y) {
                final double yn = nextYn;
                dy = yn * yn;
                dxy = dx + dy;
                nextYn = (y + 1) * invRadiusY;
                double nextZn = 0;
                forZ:
                for (int z = 0; z <= ceilRadiusZ; ++z) {
                    final double zn = nextZn;
                    dz = zn * zn;
                    dxyz = dxy + dz;
                    nextZn = (z + 1) * invRadiusZ;
                    if (dxyz > 1) {
                        if (z == 0) {
                            if (y == 0) {
                                break forX;
                            }
                            break forY;
                        }
                        break forZ;
                    }
                    if (!filled) {
                        if (nextXn * nextXn + dy + dz <= 1 && nextYn * nextYn + dx + dz <= 1 && nextZn * nextZn + dx + dy <= 1) {
                            continue;
                        }
                    }

                    if (Math.abs((x) * nx + (y) * ny + (z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px + x, py + y, pz + z), block);
                    }
                    if (Math.abs((-x) * nx + (y) * ny + (z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px - x, py + y, pz + z), block);
                    }
                    if (Math.abs((x) * nx + (-y) * ny + (z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px + x, py - y, pz + z), block);
                    }
                    if (Math.abs((x) * nx + (y) * ny + (-z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px + x, py + y, pz - z), block);
                    }
                    if (Math.abs((-x) * nx + (-y) * ny + (z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px - x, py - y, pz + z), block);
                    }
                    if (Math.abs((x) * nx + (-y) * ny + (-z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px + x, py - y, pz - z), block);
                    }
                    if (Math.abs((-x) * nx + (y) * ny + (-z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px - x, py + y, pz - z), block);
                    }
                    if (Math.abs((-x) * nx + (-y) * ny + (-z) * nz) < threshold) {
                        setBlock(mutable.setComponents(px - x, py - y, pz - z), block);
                    }
                }
            }
        }

        return changes;
    }

    /**
    * Makes a sphere.
    *
    * @param pos Center of the sphere or ellipsoid
    * @param block The block pattern to use
    * @param radius The sphere's radius
    * @param filled If false, only a shell will be generated.
    * @return number of blocks changed
    * @throws MaxChangedBlocksException thrown if too many blocks are changed
    */
    public int makeSphere(BlockVector3 pos, Pattern block, double radius, boolean filled) throws MaxChangedBlocksException {
        return makeSphere(pos, block, radius, radius, radius, filled);
    }

    /**
     * Makes a sphere or ellipsoid.
     *
     * @param pos Center of the sphere or ellipsoid
     * @param block The block pattern to use
     * @param radiusX The sphere/ellipsoid's largest north/south extent
     * @param radiusY The sphere/ellipsoid's largest up/down extent
     * @param radiusZ The sphere/ellipsoid's largest east/west extent
     * @param filled If false, only a shell will be generated.
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeSphere(BlockVector3 pos, Pattern block, double radiusX, double radiusY, double radiusZ, boolean filled) throws MaxChangedBlocksException {
        radiusX += 0.5;
        radiusY += 0.5;
        radiusZ += 0.5;

        final double invRadiusX = 1 / radiusX;
        final double invRadiusY = 1 / radiusY;
        final double invRadiusZ = 1 / radiusZ;

        int px = pos.getBlockX();
        int py = pos.getBlockY();
        int pz = pos.getBlockZ();

        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusY = (int) Math.ceil(radiusY);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);

        int yy;

        double nextXn = 0;
        forX: for (int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            double dx = xn * xn;
            nextXn = (x + 1) * invRadiusX;
            double nextZn = 0;
            forZ: for (int z = 0; z <= ceilRadiusZ; ++z) {
                final double zn = nextZn;
                double dz = zn * zn;
                double dxz = dx + dz;
                nextZn = (z + 1) * invRadiusZ;
                double nextYn = 0;

                forY: for (int y = 0; y <= ceilRadiusY; ++y) {
                    final double yn = nextYn;
                    double dy = yn * yn;
                    double dxyz = dxz + dy;
                    nextYn = (y + 1) * invRadiusY;

                    if (dxyz > 1) {
                        if (y == 0) {
                            if (z == 0) {
                                break forX;
                            }
                            break forZ;
                        }
                        break forY;
                    }

                    if (!filled) {
                        if (nextXn * nextXn + dy + dz <= 1 && nextYn * nextYn + dx + dz <= 1 && nextZn * nextZn + dx + dy <= 1) {
                            continue;
                        }
                    }
                    yy = py + y;
                    if (yy <= maxY) {
                        this.setBlock(px + x, py + y, pz + z, block);
                        if (x != 0) {
                            this.setBlock(px - x, py + y, pz + z, block);
                        }
                        if (z != 0) {
                            this.setBlock(px + x, py + y, pz - z, block);
                            if (x != 0) {
                                this.setBlock(px - x, py + y, pz - z, block);
                            }
                        }
                    }
                    if (y != 0 && (yy = py - y) >= 0) {
                        this.setBlock(px + x, yy, pz + z, block);
                        if (x != 0) {
                            this.setBlock(px - x, yy, pz + z, block);
                        }
                        if (z != 0) {
                            this.setBlock(px + x, yy, pz - z, block);
                            if (x != 0) {
                                this.setBlock(px - x, yy, pz - z, block);
                            }
                        }
                    }
                }
            }
        }

        return changes;
    }

    /**
     * Makes a pyramid.
     *
     * @param position a position
     * @param block a block
     * @param size size of pyramid
     * @param filled true if filled
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makePyramid(BlockVector3 position, Pattern block, int size, boolean filled) throws MaxChangedBlocksException {
        int bx = position.getX();
        int by = position.getY();
        int bz = position.getZ();

        int height = size;

        for (int y = 0; y <= height; ++y) {
            size--;
            for (int x = 0; x <= size; ++x) {
                for (int z = 0; z <= size; ++z) {

                    if ((filled && z <= size && x <= size) || z == size || x == size) {
                        setBlock(x + bx, y + by, z + bz, block);
                        setBlock(-x + bx, y + by, z + bz, block);
                        setBlock(x + bx, y + by, -z + bz, block);
                        setBlock(-x + bx, y + by, -z + bz, block);
                    }
                }
            }
        }

        return changes;
    }

    /**
     * Thaw blocks in a radius.
     *
     * @param position the position
     * @param radius the radius
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @deprecated Use {@link #thaw(BlockVector3, double, int)}.
     */
    @Deprecated
    public int thaw(BlockVector3 position, double radius)
        throws MaxChangedBlocksException {
        return thaw(position, radius,
            WorldEdit.getInstance().getConfiguration().defaultVerticalHeight);
    }

    /**
     * Thaw blocks in a cylinder.
     *
     * @param position the position
     * @param radius the radius
     * @param height the height (upwards and downwards)
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int thaw(BlockVector3 position, double radius, int height)
        throws MaxChangedBlocksException {
        int affected = 0;
        double radiusSq = radius * radius;

        int ox = position.getBlockX();
        int oy = position.getBlockY();
        int oz = position.getBlockZ();

        BlockState air = BlockTypes.AIR.getDefaultState();
        BlockState water = BlockTypes.WATER.getDefaultState();

        int centerY = Math.max(getWorld().getMinY(), Math.min(getWorld().getMaxY(), oy));
        int minY = Math.max(getWorld().getMinY(), centerY - height);
        int maxY = Math.min(getWorld().getMaxY(), centerY + height);

        int ceilRadius = (int) Math.ceil(radius);
        for (int x = ox - ceilRadius; x <= ox + ceilRadius; ++x) {
            for (int z = oz - ceilRadius; z <= oz + ceilRadius; ++z) {
                if ((BlockVector3.at(x, oy, z)).distanceSq(position) > radiusSq) {
                    continue;
                }

                for (int y = maxY; y > minY; --y) {
                    BlockVector3 pt = BlockVector3.at(x, y, z);
                    BlockVector3 below = BlockVector3.at(x, y - 1, z);
                    BlockType id = getBlock(pt).getBlockType();

                    if (id == BlockTypes.ICE) {
                        if (setBlock(pt, water)) {
                            ++affected;
                        }
                    } else if (id == BlockTypes.SNOW) {
                        if (setBlock(pt, air)) {
                            if (y > 0 ) {
                                BlockState block = getBlock(below);
                                if (block.getStates().containsKey(snowy)) {
                                    if (setBlock(below, block.with(snowy, false))) {
                                        affected++;
                                    }
                                }
                            }
                            ++affected;
                        }
                    } else if (id.getMaterial().isAir()) {
                        continue;
                    }

                    break;
                }
            }
        }

        return affected;
    }

    /**
     * Make snow in a radius.
     *
     * @param position a position
     * @param radius a radius
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @deprecated Use {@link #simulateSnow(BlockVector3, double, int)}.
     */
    @Deprecated
    public int simulateSnow(BlockVector3 position, double radius) throws MaxChangedBlocksException {
        return simulateSnow(position, radius,
            WorldEdit.getInstance().getConfiguration().defaultVerticalHeight);
    }

    /**
     * Make snow in a cylinder.
     *
     * @param position a position
     * @param radius a radius
     * @param height the height (upwards and downwards)
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int simulateSnow(BlockVector3 position, double radius, int height)
        throws MaxChangedBlocksException {

        return simulateSnow(new CylinderRegion(position, Vector2.at(radius, radius), position.getBlockY(), height), false);
    }

    /**
     * Make snow in a region.
     *
     * @param region the region to simulate snow in
     * @param stack whether it should stack existing snow
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int simulateSnow(FlatRegion region, boolean stack)
        throws MaxChangedBlocksException {
        checkNotNull(region);

        SnowSimulator snowSimulator = new SnowSimulator(this, stack);
        LayerVisitor layerVisitor = new LayerVisitor(region, region.getMinimumY(), region.getMaximumY(), snowSimulator);
        Operations.completeLegacy(layerVisitor);
        return snowSimulator.getAffected();
    }
    /**
     * Make dirt green.
     *
     * @param position a position
     * @param radius a radius
     * @param onlyNormalDirt only affect normal dirt (all default properties)
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @deprecated Use {@link #green(BlockVector3, double, int, boolean)}.
     */
    @Deprecated
    public int green(BlockVector3 position, double radius, boolean onlyNormalDirt)
        throws MaxChangedBlocksException {
        return green(position, radius,
            WorldEdit.getInstance().getConfiguration().defaultVerticalHeight, onlyNormalDirt);
    }

    /**
     * Make dirt green in a cylinder.
     *
     * @param position the position
     * @param radius the radius
     * @param height the height
     * @param onlyNormalDirt only affect normal dirt (all default properties)
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int green(BlockVector3 position, double radius, int height, boolean onlyNormalDirt)
        throws MaxChangedBlocksException {
        int affected = 0;
        final double radiusSq = radius * radius;

        final int ox = position.getBlockX();
        final int oy = position.getBlockY();
        final int oz = position.getBlockZ();

        final BlockState grass = BlockTypes.GRASS_BLOCK.getDefaultState();

        final int centerY = Math.max(getWorld().getMinY(), Math.min(getWorld().getMaxY(), oy));
        final int minY = Math.max(getWorld().getMinY(), centerY - height);
        final int maxY = Math.min(getWorld().getMaxY(), centerY + height);

        final int ceilRadius = (int) Math.ceil(radius);
        for (int x = ox - ceilRadius; x <= ox + ceilRadius; ++x) {
            for (int z = oz - ceilRadius; z <= oz + ceilRadius; ++z) {
                if ((BlockVector3.at(x, oy, z)).distanceSq(position) > radiusSq) {
                    continue;
                }

                for (int y = maxY; y > minY; --y) {
                    final BlockVector3 pt = BlockVector3.at(x, y, z);
                    final BlockState block = getBlock(pt);

                    if (block.getBlockType() == BlockTypes.DIRT
                        || (!onlyNormalDirt && block.getBlockType() == BlockTypes.COARSE_DIRT)) {
                        if (setBlock(pt, grass)) {
                            ++affected;
                        }
                        break;
                    } else if (block.getBlockType() == BlockTypes.WATER || block.getBlockType() == BlockTypes.LAVA) {
                        break;
                    } else if (block.getBlockType().getMaterial().isMovementBlocker()) {
                        break;
                    }
                }
            }
        }

        return affected;
    }

    /**
     * Makes pumpkin patches randomly in an area around the given position.
     *
     * @param position the base position
     * @param apothem the apothem of the (square) area
     * @return number of patches created
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makePumpkinPatches(BlockVector3 position, int apothem) throws MaxChangedBlocksException {
        return makePumpkinPatches(position, apothem, 0.02);
    }

    public int makePumpkinPatches(BlockVector3 position, int apothem, double density) throws MaxChangedBlocksException {
        // We want to generate pumpkins
        GardenPatchGenerator generator = new GardenPatchGenerator(this);
        generator.setPlant(GardenPatchGenerator.getPumpkinPattern());

        // In a region of the given radius
        FlatRegion region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(-apothem, -5, -apothem),
                position.add(apothem, 10, apothem));

        GroundFunction ground = new GroundFunction(new ExistingBlockMask(this), generator);
        LayerVisitor visitor = new LayerVisitor(region, minimumBlockY(region), maximumBlockY(region), ground);
        visitor.setMask(new NoiseFilter2D(new RandomNoise(), density));
        Operations.completeLegacy(visitor);
        return this.changes = ground.getAffected();
    }

    /**
     * Makes a forest.
     *
     * @param basePosition a position
     * @param size a size
     * @param density between 0 and 1, inclusive
     * @param treeType the tree type
     * @return number of trees created
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeForest(BlockVector3 basePosition, int size, double density, TreeGenerator.TreeType treeType) throws MaxChangedBlocksException {
        return makeForest(CuboidRegion.fromCenter(basePosition, size), density, treeType);
    }

    /**
     * Makes a forest.
     *
     * @param region the region to generate trees in
     * @param density between 0 and 1, inclusive
     * @param treeType the tree type
     * @return number of trees created
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeForest(Region region, double density, TreeGenerator.TreeType treeType) throws MaxChangedBlocksException {
        ForestGenerator generator = new ForestGenerator(this, treeType);
        GroundFunction ground = new GroundFunction(new ExistingBlockMask(this), generator);
        LayerVisitor visitor = new LayerVisitor(asFlatRegion(region), minimumBlockY(region), maximumBlockY(region), ground);
        visitor.setMask(new NoiseFilter2D(new RandomNoise(), density));
        Operations.completeLegacy(visitor);
        return ground.getAffected();
    }

    /**
     * Get the block distribution inside a region.
     *
     * @param region a region
     * @return the results
     */
    public List<Countable<BlockState>> getBlockDistribution(Region region, boolean separateStates) {
        if (separateStates) {
            return getBlockDistributionWithData(region);
        }
        List<Countable<BlockType>> normalDistr = getBlockDistribution(region);
        List<Countable<BlockState>> distribution = new ArrayList<>();
        for (Countable<BlockType> count : normalDistr) {
            distribution.add(new Countable<>(count.getID().getDefaultState(), count.getAmount()));
        }
        return distribution;
    }

    /**
     * Generate a shape for the given expression.
     *
     * @param region the region to generate the shape in
     * @param zero the coordinate origin for x/y/z variables
     * @param unit the scale of the x/y/z/ variables
     * @param pattern the default material to make the shape from
     * @param expressionString the expression defining the shape
     * @param hollow whether the shape should be hollow
     * @return number of blocks changed
     * @throws ExpressionException if there is a problem with the expression
     * @throws MaxChangedBlocksException if the maximum block change limit is exceeded
     */
    public int makeShape(final Region region, final Vector3 zero, final Vector3 unit,
                         final Pattern pattern, final String expressionString, final boolean hollow)
            throws ExpressionException, MaxChangedBlocksException {
        return makeShape(region, zero, unit, pattern, expressionString, hollow, WorldEdit.getInstance().getConfiguration().calculationTimeout);
    }

    /**
     * Generate a shape for the given expression.
     *
     * @param region the region to generate the shape in
     * @param zero the coordinate origin for x/y/z variables
     * @param unit the scale of the x/y/z/ variables
     * @param pattern the default material to make the shape from
     * @param expressionString the expression defining the shape
     * @param hollow whether the shape should be hollow
     * @param timeout the time, in milliseconds, to wait for each expression evaluation before halting it. -1 to disable
     * @return number of blocks changed
     * @throws ExpressionException if there is a problem with the expression
     * @throws MaxChangedBlocksException if the maximum block change limit is exceeded
     */
    public int makeShape(final Region region, final Vector3 zero, final Vector3 unit,
                         final Pattern pattern, final String expressionString, final boolean hollow, final int timeout)
            throws ExpressionException, MaxChangedBlocksException {
        final Expression expression = Expression.compile(expressionString, "x", "y", "z", "type", "data");
        expression.optimize();

        final Variable typeVariable = expression.getSlots().getVariable("type")
            .orElseThrow(IllegalStateException::new);
        final Variable dataVariable = expression.getSlots().getVariable("data")
            .orElseThrow(IllegalStateException::new);

        final WorldEditExpressionEnvironment environment = new WorldEditExpressionEnvironment(this, unit, zero);
        expression.setEnvironment(environment);

        final int[] timedOut = {0};
        final ArbitraryShape shape = new ArbitraryShape(region) {
            @Override
            protected BaseBlock getMaterial(int x, int y, int z, BaseBlock defaultMaterial) {
                final Vector3 current = Vector3.at(x, y, z);
                environment.setCurrentBlock(current);
                final Vector3 scaled = current.subtract(zero).divide(unit);

                try {
                    int[] legacy = LegacyMapper.getInstance().getLegacyFromBlock(defaultMaterial.toImmutableState());
                    int typeVar = 0;
                    int dataVar = 0;
                    if (legacy != null) {
                        typeVar = legacy[0];
                        if (legacy.length > 1) {
                            dataVar = legacy[1];
                        }
                    }
                    if (expression.evaluate(new double[]{scaled.getX(), scaled.getY(), scaled.getZ(), typeVar, dataVar}, timeout) <= 0) {
                        return null;
                    }
                    int newType = (int) typeVariable.getValue();
                    int newData = (int) dataVariable.getValue();
                    if (newType != typeVar || newData != dataVar) {
                        BlockState state = LegacyMapper.getInstance().getBlockFromLegacy(newType, newData);
                        return state == null ? defaultMaterial : state.toBaseBlock();
                    } else {
                        return defaultMaterial;
                    }
                } catch (ExpressionTimeoutException e) {
                    timedOut[0] = timedOut[0] + 1;
                    return null;
                } catch (Exception e) {
                    LOGGER.warn("Failed to create shape", e);
                    return null;
                }
            }
        };
        int changed = shape.generate(this, pattern, hollow);
        if (timedOut[0] > 0) {
            throw new ExpressionTimeoutException(
                    String.format("%d blocks changed. %d blocks took too long to evaluate (increase with //timeout).",
                            changed, timedOut[0]));
        }
        return changed;
    }

    /**
     * Deforms the region by a given expression. A deform provides a block's x, y, and z coordinates (possibly scaled)
     * to an expression, and then sets the block to the block given by the resulting values of the variables, if they
     * have changed.
     *
     * @param region the region to deform
     * @param zero the origin of the coordinate system
     * @param unit the scale of the coordinate system
     * @param expressionString the expression to evaluate for each block
     *
     * @return number of blocks changed
     *
     * @throws ExpressionException thrown on invalid expression input
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int deformRegion(final Region region, final Vector3 zero, final Vector3 unit, final String expressionString)
            throws ExpressionException, MaxChangedBlocksException {
        return deformRegion(region, zero, unit, expressionString, WorldEdit.getInstance().getConfiguration().calculationTimeout);
    }

    /**
     * Deforms the region by a given expression. A deform provides a block's x, y, and z coordinates (possibly scaled)
     * to an expression, and then sets the block to the block given by the resulting values of the variables, if they
     * have changed.
     *
     * @param region the region to deform
     * @param zero the origin of the coordinate system
     * @param unit the scale of the coordinate system
     * @param expressionString the expression to evaluate for each block
     * @param timeout maximum time for the expression to evaluate for each block. -1 for unlimited.
     *
     * @return number of blocks changed
     *
     * @throws ExpressionException thrown on invalid expression input
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int deformRegion(final Region region, final Vector3 zero, final Vector3 unit, final String expressionString,
                            final int timeout) throws ExpressionException, MaxChangedBlocksException {
        final Expression expression = Expression.compile(expressionString, "x", "y", "z");
        expression.optimize();

        final Variable x = expression.getSlots().getVariable("x")
            .orElseThrow(IllegalStateException::new);
        final Variable y = expression.getSlots().getVariable("y")
            .orElseThrow(IllegalStateException::new);
        final Variable z = expression.getSlots().getVariable("z")
            .orElseThrow(IllegalStateException::new);

        final WorldEditExpressionEnvironment environment = new WorldEditExpressionEnvironment(this, unit, zero);
        expression.setEnvironment(environment);
        final Vector3 zero2 = zero.add(0.5, 0.5, 0.5);

        RegionVisitor visitor = new RegionVisitor(region, new RegionFunction() {

            @Override
            public boolean apply(BlockVector3 position) throws WorldEditException {
                try {
                    // offset, scale
                    final Vector3 scaled = position.toVector3().subtract(zero).divide(unit);

                    // transform
                    expression.evaluate(new double[]{scaled.getX(), scaled.getY(), scaled.getZ()}, timeout);
                    int xv = (int) (x.getValue() * unit.getX() + zero2.getX());
                    int yv = (int) (y.getValue() * unit.getY() + zero2.getY());
                    int zv = (int) (z.getValue() * unit.getZ() + zero2.getZ());

                    BlockState get;
                    if (yv >= 0 && yv < 256) {
                        get = getBlock(xv, yv, zv);
                    } else {
                        get = BlockTypes.AIR.getDefaultState();
                    }

                    // read block from world
                    return setBlock(position, get);
                } catch (EvaluationException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        Operations.completeBlindly(visitor);
        changes += visitor.getAffected();
        return changes;
    }

    /**
     * Hollows out the region (Semi-well-defined for non-cuboid selections).
     *
     * @param region the region to hollow out.
     * @param thickness the thickness of the shell to leave (manhattan distance)
     * @param pattern The block pattern to use
     *
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int hollowOutRegion(Region region, int thickness, Pattern pattern) throws MaxChangedBlocksException {
        return hollowOutRegion(region, thickness, pattern, new SolidBlockMask(this));
    }

    public int hollowOutRegion(Region region, int thickness, Pattern pattern, Mask mask) {
        try {
            final Set<BlockVector3> outside = new LocalBlockVectorSet();

            final BlockVector3 min = region.getMinimumPoint();
            final BlockVector3 max = region.getMaximumPoint();

            final int minX = min.getBlockX();
            final int minY = min.getBlockY();
            final int minZ = min.getBlockZ();
            final int maxX = max.getBlockX();
            final int maxY = max.getBlockY();
            final int maxZ = max.getBlockZ();

            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    recurseHollow(region, BlockVector3.at(x, y, minZ), outside, mask);
                    recurseHollow(region, BlockVector3.at(x, y, maxZ), outside, mask);
                }
            }

            for (int y = minY; y <= maxY; ++y) {
                for (int z = minZ; z <= maxZ; ++z) {
                    recurseHollow(region, BlockVector3.at(minX, y, z), outside, mask);
                    recurseHollow(region, BlockVector3.at(maxX, y, z), outside, mask);
                }
            }

            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    recurseHollow(region, BlockVector3.at(x, minY, z), outside, mask);
                    recurseHollow(region, BlockVector3.at(x, maxY, z), outside, mask);
                }
            }

            for (int i = 1; i < thickness; ++i) {
                final Set<BlockVector3> newOutside = new LocalBlockVectorSet();
                outer: for (BlockVector3 position : region) {
                    for (BlockVector3 recurseDirection : recurseDirections) {
                        BlockVector3 neighbor = position.add(recurseDirection);

                        if (outside.contains(neighbor)) {
                            newOutside.add(position);
                            continue outer;
                        }
                    }
                }

                outside.addAll(newOutside);
            }

            outer: for (BlockVector3 position : region) {
                for (BlockVector3 recurseDirection : recurseDirections) {
                    BlockVector3 neighbor = position.add(recurseDirection);

                    if (outside.contains(neighbor)) {
                        continue outer;
                    }
                }
                this.changes++;
                pattern.apply(getExtent(), position, position);
            }
        } catch (WorldEditException e) {
            throw new RuntimeException(e);
        }
        return changes;
    }

    public int drawLine(Pattern pattern, BlockVector3 pos1, BlockVector3 pos2, double radius, boolean filled) throws MaxChangedBlocksException {
        return drawLine(pattern, pos1, pos2, radius, filled, false);
    }

    /**
     * Draws a line (out of blocks) between two vectors.
     *
     * @param pattern The block pattern used to draw the line.
     * @param pos1 One of the points that define the line.
     * @param pos2 The other point that defines the line.
     * @param radius The radius (thickness) of the line.
     * @param filled If false, only a shell will be generated.
     *
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     *
     * @see #drawLine(Pattern, List, double, boolean)
     */
    public int drawLine(Pattern pattern, BlockVector3 pos1, BlockVector3 pos2, double radius, boolean filled, boolean flat)
        throws MaxChangedBlocksException {

        LocalBlockVectorSet vset = new LocalBlockVectorSet();
        boolean notdrawn = true;

        int x1 = pos1.getBlockX();
        int y1 = pos1.getBlockY();
        int z1 = pos1.getBlockZ();
        int x2 = pos2.getBlockX();
        int y2 = pos2.getBlockY();
        int z2 = pos2.getBlockZ();
        int tipx = x1;
        int tipy = y1;
        int tipz = z1;
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        if (dx + dy + dz == 0) {
            vset.add(BlockVector3.at(tipx, tipy, tipz));
            notdrawn = false;
        }

        int dMax = Math.max(Math.max(dx, dy), dz);
        if (dMax == dx && notdrawn) {
            for (int domstep = 0; domstep <= dx; domstep++) {
                tipx = x1 + domstep * (x2 - x1 > 0 ? 1 : -1);
                tipy = (int) Math.round(y1 + domstep * (double) dy / (double) dx * (y2 - y1 > 0 ? 1 : -1));
                tipz = (int) Math.round(z1 + domstep * (double) dz / (double) dx * (z2 - z1 > 0 ? 1 : -1));

                vset.add(BlockVector3.at(tipx, tipy, tipz));
            }
        } else if (dMax == dy && notdrawn) {
            for (int domstep = 0; domstep <= dy; domstep++) {
                tipy = y1 + domstep * (y2 - y1 > 0 ? 1 : -1);
                tipx = (int) Math.round(x1 + domstep * (double) dx / (double) dy * (x2 - x1 > 0 ? 1 : -1));
                tipz = (int) Math.round(z1 + domstep * (double) dz / (double) dy * (z2 - z1 > 0 ? 1 : -1));

                vset.add(BlockVector3.at(tipx, tipy, tipz));
            }
        } else if (dMax == dz && notdrawn) {
            for (int domstep = 0; domstep <= dz; domstep++) {
                tipz = z1 + domstep * (z2 - z1 > 0 ? 1 : -1);
                tipy = (int) Math.round(y1 + domstep * (double) dy / (double) dz * (y2 - y1 > 0 ? 1 : -1));
                tipx = (int) Math.round(x1 + domstep * (double) dx / (double) dz * (x2 - x1 > 0 ? 1 : -1));

                vset.add(BlockVector3.at(tipx, tipy, tipz));
            }
        }
        Set<BlockVector3> newVset;
        if (flat) {
            newVset = getStretched(vset, radius);
            if (!filled) {
                newVset = this.getOutline(newVset);
            }
        } else {
            newVset = getBallooned(vset, radius);
            if (!filled) {
                newVset = this.getHollowed(newVset);
            }
        }
        return this.changes += setBlocks(newVset, pattern);
    }

    /**
     * Draws a line (out of blocks) between two or more vectors.
     *
     * @param pattern The block pattern used to draw the line.
     * @param vectors the list of vectors to draw the line between
     * @param radius The radius (thickness) of the line.
     * @param filled If false, only a shell will be generated.
     *
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int drawLine(Pattern pattern, List<BlockVector3> vectors, double radius, boolean filled)
            throws MaxChangedBlocksException {

        Set<BlockVector3> vset = new HashSet<>();

        for (int i = 0; vectors.size() != 0 && i < vectors.size() - 1; i++) {
            BlockVector3 pos1 = vectors.get(i);
            BlockVector3 pos2 = vectors.get(i + 1);

            int x1 = pos1.getBlockX();
            int y1 = pos1.getBlockY();
            int z1 = pos1.getBlockZ();
            int x2 = pos2.getBlockX();
            int y2 = pos2.getBlockY();
            int z2 = pos2.getBlockZ();
            int tipx = x1;
            int tipy = y1;
            int tipz = z1;
            int dx = Math.abs(x2 - x1);
            int dy = Math.abs(y2 - y1);
            int dz = Math.abs(z2 - z1);

            if (dx + dy + dz == 0) {
                vset.add(BlockVector3.at(tipx, tipy, tipz));
                continue;
            }

            int dMax = Math.max(Math.max(dx, dy), dz);
            if (dMax == dx) {
                for (int domstep = 0; domstep <= dx; domstep++) {
                    tipx = x1 + domstep * (x2 - x1 > 0 ? 1 : -1);
                    tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dx) * (y2 - y1 > 0 ? 1 : -1));
                    tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dx) * (z2 - z1 > 0 ? 1 : -1));

                    vset.add(BlockVector3.at(tipx, tipy, tipz));
                }
            } else if (dMax == dy) {
                for (int domstep = 0; domstep <= dy; domstep++) {
                    tipy = y1 + domstep * (y2 - y1 > 0 ? 1 : -1);
                    tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dy) * (x2 - x1 > 0 ? 1 : -1));
                    tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dy) * (z2 - z1 > 0 ? 1 : -1));

                    vset.add(BlockVector3.at(tipx, tipy, tipz));
                }
            } else /* if (dMax == dz) */ {
                for (int domstep = 0; domstep <= dz; domstep++) {
                    tipz = z1 + domstep * (z2 - z1 > 0 ? 1 : -1);
                    tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dz) * (y2 - y1 > 0 ? 1 : -1));
                    tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dz) * (x2 - x1 > 0 ? 1 : -1));

                    vset.add(BlockVector3.at(tipx, tipy, tipz));
                }
            }
        }

        vset = getBallooned(vset, radius);
        if (!filled) {
            vset = getHollowed(vset);
        }
        return this.changes += setBlocks(vset, pattern);
    }

    /**
     * Draws a spline (out of blocks) between specified vectors.
     *
     * @param pattern The block pattern used to draw the spline.
     * @param nodevectors The list of vectors to draw through.
     * @param tension The tension of every node.
     * @param bias The bias of every node.
     * @param continuity The continuity of every node.
     * @param quality The quality of the spline. Must be greater than 0.
     * @param radius The radius (thickness) of the spline.
     * @param filled If false, only a shell will be generated.
     *
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int drawSpline(Pattern pattern, List<BlockVector3> nodevectors, double tension, double bias,
                          double continuity, double quality, double radius, boolean filled)
            throws MaxChangedBlocksException {

        LocalBlockVectorSet vset = new LocalBlockVectorSet();
        List<Node> nodes = new ArrayList<>(nodevectors.size());

        Interpolation interpol = new KochanekBartelsInterpolation();

        for (BlockVector3 nodevector : nodevectors) {
            Node n = new Node(nodevector.toVector3());
            n.setTension(tension);
            n.setBias(bias);
            n.setContinuity(continuity);
            nodes.add(n);
        }

        interpol.setNodes(nodes);
        double splinelength = interpol.arcLength(0, 1);
        for (double loop = 0; loop <= 1; loop += 1D / splinelength / quality) {
            BlockVector3 tipv = interpol.getPosition(loop).toBlockPoint();
            if (radius == 0) {
                pattern.apply(this, tipv, tipv);
                changes++;
            } else {
                vset.add(tipv);
            }
        }
        Set<BlockVector3> newVset;
        if (radius != 0) {
            newVset = getBallooned(vset, radius);
            if (!filled) {
                newVset = this.getHollowed(newVset);
            }
            return this.changes += setBlocks(newVset, pattern);
        }
        return changes;
    }

    private static Set<BlockVector3> getBallooned(Set<BlockVector3> vset, double radius) {
        if (radius < 1) {
            return vset;
        }
        LocalBlockVectorSet returnset = new LocalBlockVectorSet();
        int ceilrad = (int) Math.ceil(radius);

        for (BlockVector3 v : vset) {
            int tipx = v.getBlockX();
            int tipy = v.getBlockY();
            int tipz = v.getBlockZ();

            for (int loopx = tipx - ceilrad; loopx <= tipx + ceilrad; loopx++) {
                for (int loopy = tipy - ceilrad; loopy <= tipy + ceilrad; loopy++) {
                    for (int loopz = tipz - ceilrad; loopz <= tipz + ceilrad; loopz++) {
                        if (MathMan.hypot(loopx - tipx, loopy - tipy, loopz - tipz) <= radius) {
                            returnset.add(loopx, loopy, loopz);
                        }
                    }
                }
            }
        }
        return returnset;
    }

    public static Set<BlockVector3> getStretched(Set<BlockVector3> vset, double radius) {
        if (radius < 1) {
            return vset;
        }
        final LocalBlockVectorSet returnset = new LocalBlockVectorSet();
        final int ceilrad = (int) Math.ceil(radius);
        for (BlockVector3 v : vset) {
            final int tipx = v.getBlockX();
            final int tipy = v.getBlockY();
            final int tipz = v.getBlockZ();
            for (int loopx = tipx - ceilrad; loopx <= tipx + ceilrad; loopx++) {
                for (int loopz = tipz - ceilrad; loopz <= tipz + ceilrad; loopz++) {
                    if (MathMan.hypot(loopx - tipx, 0, loopz - tipz) <= radius) {
                        returnset.add(loopx, v.getBlockY(), loopz);
                    }
                }
            }
        }
        return returnset;
    }

    public Set<BlockVector3> getOutline(Set<BlockVector3> vset) {
        final LocalBlockVectorSet returnset = new LocalBlockVectorSet();
        final LocalBlockVectorSet newset = new LocalBlockVectorSet();
        newset.addAll(vset);
        for (BlockVector3 v : newset) {
            final int x = v.getX();
            final int y = v.getY();
            final int z = v.getZ();
            if (!(newset.contains(x + 1, y, z)
                && newset.contains(x - 1, y, z)
                && newset.contains(x, y, z + 1)
                && newset.contains(x, y, z - 1))) {
                returnset.add(v);
            }
        }
        return returnset;
    }

    public Set<BlockVector3> getHollowed(Set<BlockVector3> vset) {
        final Set<BlockVector3> returnset = new LocalBlockVectorSet();
        final LocalBlockVectorSet newset = new LocalBlockVectorSet();
        newset.addAll(vset);
        for (BlockVector3 v : newset) {
            final int x = v.getX();
            final int y = v.getY();
            final int z = v.getZ();
            if (!(newset.contains(x + 1, y, z)
                && newset.contains(x - 1, y, z)
                && newset.contains(x, y + 1, z)
                && newset.contains(x, y - 1, z)
                && newset.contains(x, y, z + 1)
                && newset.contains(x, y, z - 1))) {
                returnset.add(v);
            }
        }
        return returnset;
    }

    private void recurseHollow(Region region, BlockVector3 origin, Set<BlockVector3> outside, Mask mask) {
        final LocalBlockVectorSet queue = new LocalBlockVectorSet();
        queue.add(origin);

        while (!queue.isEmpty()) {
            Iterator<BlockVector3> iter = queue.iterator();
            while (iter.hasNext()) {
                final BlockVector3 current = iter.next();
                iter.remove();
                if (mask.test(current)) {
                    continue;
                }
                if (!outside.add(current)) {
                    continue;
                }
                if (!region.contains(current)) {
                    continue;
                }

                for (BlockVector3 recurseDirection : recurseDirections) {
                    queue.add(current.add(recurseDirection));
                }
            }
        }
    }

    public int makeBiomeShape(final Region region, final Vector3 zero, final Vector3 unit, final BiomeType biomeType,
                              final String expressionString, final boolean hollow) throws ExpressionException {
        return makeBiomeShape(region, zero, unit, biomeType, expressionString, hollow, WorldEdit.getInstance().getConfiguration().calculationTimeout);
    }

    public int makeBiomeShape(final Region region, final Vector3 zero, final Vector3 unit, final BiomeType biomeType,
                              final String expressionString, final boolean hollow, final int timeout) throws ExpressionException {

        final Expression expression = Expression.compile(expressionString, "x", "y", "z");
        expression.optimize();

        final EditSession editSession = this;
        final WorldEditExpressionEnvironment environment = new WorldEditExpressionEnvironment(editSession, unit, zero);
        expression.setEnvironment(environment);

        AtomicInteger timedOut = new AtomicInteger();
        final ArbitraryBiomeShape shape = new ArbitraryBiomeShape(region) {
            @Override
            protected BiomeType getBiome(int x, int y, int z, BiomeType defaultBiomeType) {
                environment.setCurrentBlock(x, y, z);
                double scaledX = (x - zero.getX()) / unit.getX();
                double scaledY = (y - zero.getY()) / unit.getY();
                double scaledZ = (z - zero.getZ()) / unit.getZ();

                try {
                    if (expression.evaluate(timeout, scaledX, scaledY, scaledZ) <= 0) {
                        return null;
                    }

                    // TODO: Allow biome setting via a script variable (needs BiomeType<->int mapping)
                    return defaultBiomeType;
                } catch (ExpressionTimeoutException e) {
                    timedOut.getAndIncrement();
                    return null;
                } catch (Exception e) {
                    LOGGER.warn("Failed to create shape", e);
                    return null;
                }
            }
        };
        int changed = shape.generate(this, biomeType, hollow);
        if (timedOut.get() > 0) {
            throw new ExpressionTimeoutException(
                    String.format("%d biomes changed. %d biomes took too long to evaluate (increase time with //timeout)",
                            changed, timedOut.get()));
        }
        return changed;
    }

    private static final BlockVector3[] recurseDirections = {
            Direction.NORTH.toBlockVector(),
            Direction.EAST.toBlockVector(),
            Direction.SOUTH.toBlockVector(),
            Direction.WEST.toBlockVector(),
            Direction.UP.toBlockVector(),
            Direction.DOWN.toBlockVector(),
    };

    public boolean regenerate(Region region) {
        return regenerate(region, this);
    }

    public boolean regenerate(Region region, EditSession session) {
        return session.regenerate(region, null, null);
    }

    private void setExistingBlocks(BlockVector3 pos1, BlockVector3 pos2) {
        for (int x = pos1.getX(); x <= pos2.getX(); x++) {
            for (int z = pos1.getBlockZ(); z <= pos2.getBlockZ(); z++) {
                for (int y = pos1.getY(); y <= pos2.getY(); y++) {
                    setBlock(x, y, z, getFullBlock(x, y, z));
                }
            }
        }
    }

    public boolean regenerate(Region region, BiomeType biome, Long seed) {
        //TODO Optimize - avoid Vector2D creation (make mutable)
        final AbstractChangeSet fcs = (AbstractChangeSet) this.getChangeSet();
        this.setChangeSet(null);
        final FaweRegionExtent fe = this.getRegionExtent();
        final boolean cuboid = region instanceof CuboidRegion;
        if (fe != null && cuboid) {
            BlockVector3 max = region.getMaximumPoint();
            BlockVector3 min = region.getMinimumPoint();
            if (!fe.contains(max.getBlockX(), max.getBlockY(), max.getBlockZ()) && !fe.contains(min.getBlockX(), min.getBlockY(), min.getBlockZ())) {
                throw FaweCache.OUTSIDE_REGION;
            }
        }
        final Set<BlockVector2> chunks = region.getChunks();
        MutableBlockVector3 mutable = new MutableBlockVector3();
        MutableBlockVector2 mutable2D = new MutableBlockVector2();
        for (BlockVector2 chunk : chunks) {
            final int cx = chunk.getBlockX();
            final int cz = chunk.getBlockZ();
            final int bx = cx << 4;
            final int bz = cz << 4;
            final BlockVector3 cmin = BlockVector3.at(bx, 0, bz);
            final BlockVector3 cmax = cmin.add(15, maxY, 15);
            final boolean containsBot1 =
                fe == null || fe.contains(cmin.getBlockX(), cmin.getBlockY(), cmin.getBlockZ());
            final boolean containsBot2 = region.contains(cmin);
            final boolean containsTop1 =
                fe == null || fe.contains(cmax.getBlockX(), cmax.getBlockY(), cmax.getBlockZ());
            final boolean containsTop2 = region.contains(cmax);
            if (containsBot2 && containsTop2 && !containsBot1 && !containsTop1) {
                continue;
            }
            boolean conNextX = chunks.contains(mutable2D.setComponents(cx + 1, cz));
            boolean conNextZ = chunks.contains(mutable2D.setComponents(cx, cz + 1));
            boolean containsAny = false;
            if (cuboid && containsBot1 && containsBot2 && containsTop1 && containsTop2 && conNextX && conNextZ) {
                containsAny = true;
                if (fcs != null) {
                    for (int x = 0; x < 16; x++) {
                        int xx = x + bx;
                        for (int z = 0; z < 16; z++) {
                            int zz = z + bz;
                            for (int y = 0; y < maxY + 1; y++) {
                                BaseBlock block = getFullBlock(mutable.setComponents(xx, y, zz));
                                fcs.add(mutable, block, BlockTypes.AIR.getDefaultState().toBaseBlock());
                            }
                        }
                    }
                }
            } else {
                if (!conNextX) {
                    setExistingBlocks(BlockVector3.at(bx + 16, 0, bz), BlockVector3.at(bx + 31, maxY, bz + 15));
                }
                if (!conNextZ) {
                    setExistingBlocks(BlockVector3.at(bx, 0, bz + 16), BlockVector3.at(bx + 15, maxY, bz + 31));
                }
                if (!chunks.contains(mutable2D.setComponents(cx + 1, cz + 1)) && !conNextX && !conNextZ) {
                    setExistingBlocks(BlockVector3.at(bx + 16, 0, bz + 16), BlockVector3.at(bx + 31, maxY, bz + 31));
                }
                for (int x = 0; x < 16; x++) {
                    int xx = x + bx;
                    mutable.mutX(xx);
                    for (int z = 0; z < 16; z++) {
                        int zz = z + bz;
                        mutable.mutZ(zz);
                        for (int y = 0; y < maxY + 1; y++) {
                            mutable.mutY(y);
                            boolean contains = (fe == null || fe.contains(xx, y, zz)) && region.contains(mutable);
                            if (contains) {
                                containsAny = true;
                                if (fcs != null) {
                                    BaseBlock block = getFullBlock(mutable);
                                    fcs.add(mutable, block, BlockTypes.AIR.getDefaultState().toBaseBlock());
                                }
                            } else {
                                BaseBlock block = getFullBlock(mutable);
                                try {
                                    setBlock(mutable, block);
                                } catch (MaxChangedBlocksException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }
            }
            if (containsAny) {
                changes++;
                TaskManager.IMP.sync(new RunnableVal<Object>() {
                    @Override
                    public void run(Object value) {
                        regenerateChunk(cx, cz, biome, seed);
                    }
                });
            }
        }
        if (changes != 0) {
            flushQueue();
            return true;
        }
        return false;
    }

    @Override
    public List<? extends Entity> getEntities() {
        return world.getEntities();
    }

    @Override
    public List<? extends Entity> getEntities(Region region) {
        return world.getEntities(region);
    }

    @Override
    public Entity createEntity(Location location, BaseEntity entity) {
        try {
            return this.getExtent().createEntity(location, entity);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    @Override
    public void removeEntity(int x, int y, int z, UUID uuid) {
        try {
            this.getExtent().removeEntity(x, y, z, uuid);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }
}
