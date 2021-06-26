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

import com.boydti.fawe.Fawe;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.FaweInputStream;
import com.boydti.fawe.object.FaweLimit;
import com.boydti.fawe.object.FaweOutputStream;
import com.boydti.fawe.object.changeset.DiskStorageHistory;
import com.boydti.fawe.object.clipboard.MultiClipboardHolder;
import com.boydti.fawe.object.collection.SparseBitSet;
import com.boydti.fawe.object.extent.ResettableExtent;
import com.boydti.fawe.util.BrushCache;
import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.StringMan;
import com.boydti.fawe.util.TextureHolder;
import com.boydti.fawe.util.TextureUtil;
import com.boydti.fawe.wrappers.WorldWrapper;
import com.sk89q.jchronic.Chronic;
import com.sk89q.jchronic.Options;
import com.sk89q.jchronic.utils.Span;
import com.sk89q.jchronic.utils.Time;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.command.tool.BlockTool;
import com.sk89q.worldedit.command.tool.BrushTool;
import com.sk89q.worldedit.command.tool.InvalidToolBindException;
import com.sk89q.worldedit.command.tool.NavigationWand;
import com.sk89q.worldedit.command.tool.SelectionWand;
import com.sk89q.worldedit.command.tool.SinglePickaxe;
import com.sk89q.worldedit.command.tool.Tool;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extension.platform.Locatable;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.operation.ChangeSetExecutor;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.internal.cui.CUIEvent;
import com.sk89q.worldedit.internal.cui.CUIRegion;
import com.sk89q.worldedit.internal.cui.SelectionShapeEvent;
import com.sk89q.worldedit.internal.cui.ServerCUIHandler;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.RegionSelectorType;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.HandSide;
import com.sk89q.worldedit.util.Identifiable;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.item.ItemType;
import com.sk89q.worldedit.world.item.ItemTypes;
import com.sk89q.worldedit.world.snapshot.experimental.Snapshot;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores session information.
 */
public class LocalSession implements TextureHolder {

    private static final transient int CUI_VERSION_UNINITIALIZED = -1;
    public static transient int MAX_HISTORY_SIZE = 15;

    // Non-session related fields
    private transient LocalConfiguration config;
    private final transient AtomicBoolean dirty = new AtomicBoolean();

    // Single-connection lifetime fields
    private transient int failedCuiAttempts = 0;
    private transient boolean hasCUISupport = false;
    private transient int cuiVersion = CUI_VERSION_UNINITIALIZED;

    // Session related
    private transient RegionSelector selector = new CuboidRegionSelector();
    private transient boolean placeAtPos1 = false;
    private final transient List<Object> history = Collections.synchronizedList(new LinkedList<Object>() {
        @Override
        public Object get(int index) {
            Object value = super.get(index);
            if (value instanceof Integer) {
                value = getChangeSet(value);
                set(index, value);
            }
            return value;
        }

        @Override
        public Object remove(int index) {
            return getChangeSet(super.remove(index));
        }
    });
    private transient volatile Integer historyNegativeIndex;
    private transient ClipboardHolder clipboard;
    private transient final Object clipboardLock = new Object();
    private transient final Lock historyWriteLock = new ReentrantLock(true);
    private transient boolean superPickaxe = false;
    private transient BlockTool pickaxeMode = new SinglePickaxe();
    private final transient Int2ObjectOpenHashMap<Tool> tools = new Int2ObjectOpenHashMap<>(0);
    private transient int maxBlocksChanged = -1;
    private transient int maxTimeoutTime;
    private transient boolean useInventory;
    private transient com.sk89q.worldedit.world.snapshot.Snapshot snapshot;
    private transient Snapshot snapshotExperimental;
    private transient SideEffectSet sideEffectSet = SideEffectSet.defaults();
    private transient Mask mask;
    private transient Mask sourceMask;
    private transient TextureUtil texture;
    private transient ResettableExtent transform = null;
    private transient ZoneId timezone = ZoneId.systemDefault();
    private transient World currentWorld;
    private transient UUID uuid;
    private transient volatile long historySize = 0;

    private transient BlockVector3 cuiTemporaryBlock;
    @SuppressWarnings("unused")
    private transient EditSession.ReorderMode reorderMode = EditSession.ReorderMode.MULTI_STAGE;
    private transient List<Countable<BlockState>> lastDistribution;
    private transient World worldOverride;
    private transient boolean tickingWatchdog = false;
    private transient boolean hasBeenToldVersion;
    private transient boolean tracingActions;

    // Saved properties
    private String lastScript;
    private RegionSelectorType defaultSelector;
    private boolean useServerCUI = false; // Save this to not annoy players.
    private ItemType wandItem;
    private ItemType navWandItem;
    private Map<String, String> macros = new HashMap<>();

    /**
     * Construct the object.
     *
     * <p>{@link #setConfiguration(LocalConfiguration)} should be called
     * later with configuration.</p>
     */
    public LocalSession() {
    }

    /**
     * Construct the object.
     *
     * @param config the configuration
     */
    public LocalSession(@Nullable LocalConfiguration config) {
        this.config = config;
    }

    /**
     * Set the configuration.
     *
     * @param config the configuration
     */
    public void setConfiguration(LocalConfiguration config) {
        checkNotNull(config);
        this.config = config;
    }

    /**
     * Called on post load of the session from persistent storage.
     */
    public void postLoad() {
        if (defaultSelector != null) {
            this.selector = defaultSelector.createSelector();
        }
    }

    public boolean loadSessionHistoryFromDisk(UUID uuid, World world) {
        if (world == null || uuid == null) {
            return false;
        }
        if (Settings.IMP.HISTORY.USE_DISK) {
            MAX_HISTORY_SIZE = Integer.MAX_VALUE;
        }
        world = WorldWrapper.unwrap(world);
        if (!world.equals(currentWorld)) {
            this.uuid = uuid;
            // Save history
            saveHistoryNegativeIndex(uuid, currentWorld);
            history.clear();
            currentWorld = world;
            // Load history
            if (loadHistoryChangeSets(uuid, currentWorld)) {
                loadHistoryNegativeIndex(uuid, currentWorld);
                return true;
            }
            historyNegativeIndex = 0;
        }
        return false;
    }

    private boolean loadHistoryChangeSets(UUID uuid, World world) {
        SparseBitSet set = new SparseBitSet();
        final File folder = MainUtil.getFile(Fawe.imp().getDirectory(), Settings.IMP.PATHS.HISTORY + File.separator + world.getName() + File.separator + uuid);
        if (folder.isDirectory()) {
            folder.listFiles(pathname -> {
                String name = pathname.getName();
                Integer val = null;
                if (pathname.isDirectory()) {
                    val = StringMan.toInteger(name, 0, name.length());

                } else {
                    int i = name.lastIndexOf('.');
                    if (i != -1) {
                        val = StringMan.toInteger(name, 0, i);
                    }
                }
                if (val != null) {
                    set.set(val);
                }
                return false;
            });
        }
        if (!set.isEmpty()) {
            historySize = MainUtil.getTotalSize(folder.toPath());
            for (int index = set.nextSetBit(0); index != -1; index = set.nextSetBit(index + 1)) {
                history.add(index);
            }
        } else {
            historySize = 0;
        }
        return !set.isEmpty();
    }

    private void loadHistoryNegativeIndex(UUID uuid, World world) {
        if (!Settings.IMP.HISTORY.USE_DISK) {
            return;
        }
        File file = MainUtil.getFile(Fawe.imp().getDirectory(), Settings.IMP.PATHS.HISTORY + File.separator + world.getName() + File.separator + uuid + File.separator + "index");
        if (file.exists()) {
            try (FaweInputStream is = new FaweInputStream(new FileInputStream(file))) {
                historyNegativeIndex = Math.min(Math.max(0, is.readInt()), history.size());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            historyNegativeIndex = 0;
        }
    }

    private void saveHistoryNegativeIndex(UUID uuid, World world) {
        if (world == null || !Settings.IMP.HISTORY.USE_DISK) {
            return;
        }
        File file = MainUtil.getFile(Fawe.imp().getDirectory(), Settings.IMP.PATHS.HISTORY + File.separator + world.getName() + File.separator + uuid + File.separator + "index");
        if (getHistoryNegativeIndex() != 0) {
            try {
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                try (FaweOutputStream os = new FaweOutputStream(new FileOutputStream(file))) {
                    os.writeInt(getHistoryNegativeIndex());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (file.exists()) {
            file.delete();
        }
    }

    public Map<String, String> getMacros() {
        return Collections.unmodifiableMap(this.macros);
    }

    public void setMacro(String key, String value) {
        this.macros.put(key, value);
        setDirty();
    }

    public String getMacro(String key) {
        return this.macros.get(key);
    }

    /**
     * Get whether this session is "dirty" and has changes that needs to
     * be committed.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Set this session as dirty.
     */
    private void setDirty() {
        dirty.set(true);
    }

    public int getHistoryIndex() {
        return history.size() - 1 - (historyNegativeIndex == null ? 0 : historyNegativeIndex);
    }

    public int getHistoryNegativeIndex() {
        return (historyNegativeIndex == null ? historyNegativeIndex = 0 : historyNegativeIndex);
    }

    public List<ChangeSet> getHistory() {
        return history.stream().map(this::getChangeSet).collect(Collectors.toList());
    }

    public boolean save() {
        saveHistoryNegativeIndex(uuid, currentWorld);
        if (defaultSelector == RegionSelectorType.CUBOID) {
            defaultSelector = null;
        }
        if (lastScript != null || defaultSelector != null) {
            return true;
        }
        return false;
    }

    /**
     * Get whether this session is "dirty" and has changes that needs to
     * be committed, and reset it to {@code false}.
     *
     * @return true if the dirty value was {@code true}
     */
    public boolean compareAndResetDirty() {
        return dirty.compareAndSet(true, false);
    }

    /**
     * Get the session's timezone.
     *
     * @return the timezone
     */
    public ZoneId getTimeZone() {
        return timezone;
    }

    /**
     * Set the session's timezone.
     *
     * @param timezone the user's timezone
     */
    public void setTimezone(ZoneId timezone) {
        checkNotNull(timezone);
        this.timezone = timezone;
    }

    /**
     * Clear history.
     */
    public void clearHistory() {
        history.clear();
        historyNegativeIndex = 0;
        historySize = 0;
        currentWorld = null;
    }

    /**
     * Remember an edit session for the undo history. If the history maximum
     * size is reached, old edit sessions will be discarded.
     *
     * @param editSession the edit session
     */
    public void remember(EditSession editSession) {
        checkNotNull(editSession);

        // Don't store anything if no changes were made
        if (editSession.size() == 0) {
            return;
        }

        Actor actor = editSession.getActor();
        int limit = actor == null ? Integer.MAX_VALUE : actor.getLimit().MAX_HISTORY;
        remember(editSession, true, limit);
    }

    private ChangeSet getChangeSet(Object o) {
        if (o instanceof ChangeSet) {
            ChangeSet cs = (ChangeSet) o;
            try {
                cs.close();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            return cs;
        }
        if (o instanceof Integer) {
            File folder = MainUtil.getFile(Fawe.imp().getDirectory(), Settings.IMP.PATHS.HISTORY + File.separator + currentWorld.getName() + File.separator + uuid);
            File specific = new File(folder, o.toString());
            if (specific.isDirectory()) {
                // TODO NOT IMPLEMENTED
//                return new AnvilHistory(currentWorld.getName(), specific);
            } else {
                return new DiskStorageHistory(currentWorld, this.uuid, (Integer) o);
            }
        }
        return null;
    }

    public void remember(Identifiable player, World world, ChangeSet changeSet, FaweLimit limit) {
        historyWriteLock.lock();
        try {
            if (Settings.IMP.HISTORY.USE_DISK) {
                LocalSession.MAX_HISTORY_SIZE = Integer.MAX_VALUE;
            }
            if (changeSet.size() == 0) {
                return;
            }
            loadSessionHistoryFromDisk(player.getUniqueId(), world);
            if (changeSet instanceof ChangeSet) {
                ListIterator<Object> iter = history.listIterator();
                int i = 0;
                int cutoffIndex = history.size() - getHistoryNegativeIndex();
                while (iter.hasNext()) {
                    Object item = iter.next();
                    if (++i > cutoffIndex) {
                        ChangeSet oldChangeSet;
                        if (item instanceof ChangeSet) {
                            oldChangeSet = (ChangeSet) item;
                        } else {
                            oldChangeSet = getChangeSet(item);
                        }
                        historySize -= MainUtil.getSize(oldChangeSet);
                        iter.remove();
                    }
                }
            }
            historySize += MainUtil.getSize(changeSet);
            history.add(changeSet);
            if (getHistoryNegativeIndex() != 0) {
                setDirty();
                historyNegativeIndex = 0;
            }
            if (limit != null) {
                int limitMb = limit.MAX_HISTORY;
                while (((!Settings.IMP.HISTORY.USE_DISK && history.size() > MAX_HISTORY_SIZE) || (historySize >> 20) > limitMb) && history.size() > 1) {
                    ChangeSet item = (ChangeSet) history.remove(0);
                    item.delete();
                    long size = MainUtil.getSize(item);
                    historySize -= size;
                }
            }
        } finally {
            historyWriteLock.unlock();
        }
    }

    public void remember(EditSession editSession, boolean append, int limitMb) {
        historyWriteLock.lock();
        try {
            if (Settings.IMP.HISTORY.USE_DISK) {
                LocalSession.MAX_HISTORY_SIZE = Integer.MAX_VALUE;
            }
            // It should have already been flushed, but just in case!
            editSession.flushQueue();
            if (editSession.getChangeSet() == null || limitMb == 0 || historySize >> 20 > limitMb && !append) {
                return;
            }

            ChangeSet changeSet = editSession.getChangeSet();
            if (changeSet.isEmpty()) {
                return;
            }

            Actor actor = editSession.getActor();
            if (actor != null) {
                loadSessionHistoryFromDisk(actor.getUniqueId(), editSession.getWorld());
            }
            // Destroy any sessions after this undo point
            if (append) {
                ListIterator<Object> iter = history.listIterator();
                int i = 0;
                int cutoffIndex = history.size() - getHistoryNegativeIndex();
                while (iter.hasNext()) {
                    Object item = iter.next();
                    if (++i > cutoffIndex) {
                        ChangeSet oldChangeSet;
                        if (item instanceof ChangeSet) {
                            oldChangeSet = (ChangeSet) item;
                        } else {
                            oldChangeSet = getChangeSet(item);
                        }
                        historySize -= MainUtil.getSize(oldChangeSet);
                        iter.remove();
                    }
                }
            }

            historySize += MainUtil.getSize(changeSet);
            if (append) {
                history.add(changeSet);
                if (getHistoryNegativeIndex() != 0) {
                    setDirty();
                    historyNegativeIndex = 0;
                }
            } else {
                history.add(0, changeSet);
            }
            while (((!Settings.IMP.HISTORY.USE_DISK && history.size() > MAX_HISTORY_SIZE) || (historySize >> 20) > limitMb) && history.size() > 1) {
                ChangeSet item = (ChangeSet) history.remove(0);
                item.delete();
                long size = MainUtil.getSize(item);
                historySize -= size;
            }
        } finally {
            historyWriteLock.unlock();
        }
    }

    /**
     * Performs an undo.
     *
     * @param newBlockBag a new block bag
     * @param actor the actor
     * @return whether anything was undone
     */
    public EditSession undo(@Nullable BlockBag newBlockBag, Actor actor) {
        checkNotNull(actor);
        World world = ((Player) actor).getWorldForEditing();
        loadSessionHistoryFromDisk(actor.getUniqueId(), world);
        if (getHistoryNegativeIndex() < history.size()) {
            ChangeSet changeSet = getChangeSet(history.get(getHistoryIndex()));
            try (EditSession newEditSession = new EditSessionBuilder(world)
                    .allowedRegionsEverywhere()
                    .checkMemory(false)
                    .changeSetNull()
                    .fastmode(false)
                    .limitUnprocessed((Player)actor)
                    .actor((Player)actor)
                    .blockBag(getBlockBag((Player)actor))
                    .build()) {
                newEditSession.setBlocks(changeSet, ChangeSetExecutor.Type.UNDO);
                setDirty();
                historyNegativeIndex++;
                return newEditSession;
            }
        } else {
            int size = history.size();
            if (getHistoryNegativeIndex() != size) {
                historyNegativeIndex = history.size();
                setDirty();
            }
            return null;
        }
    }

    /**
     * Performs a redo
     *
     * @param newBlockBag a new block bag
     * @param actor the actor
     * @return whether anything was redone
     */
    public EditSession redo(@Nullable BlockBag newBlockBag, Actor actor) {
        checkNotNull(actor);
        World world = ((Player) actor).getWorldForEditing();
        loadSessionHistoryFromDisk(actor.getUniqueId(), world);
        if (getHistoryNegativeIndex() > 0) {
            setDirty();
            historyNegativeIndex--;
            ChangeSet changeSet = getChangeSet(history.get(getHistoryIndex()));
            try (EditSession newEditSession = new EditSessionBuilder(world)
                    .allowedRegionsEverywhere()
                    .checkMemory(false)
                    .changeSetNull()
                    .fastmode(false)
                    .limitUnprocessed((Player)actor)
                    .actor((Player)actor)
                    .blockBag(getBlockBag((Player)actor))
                    .build()) {
                newEditSession.setBlocks(changeSet, ChangeSetExecutor.Type.REDO);
                return newEditSession;
            }
        }

        return null;
    }

    public boolean hasWorldOverride() {
        return this.worldOverride != null;
    }

    @Nullable
    public World getWorldOverride() {
        return this.worldOverride;
    }

    public void setWorldOverride(@Nullable World worldOverride) {
        this.worldOverride = worldOverride;
    }

    public boolean isTickingWatchdog() {
        return tickingWatchdog;
    }

    public void setTickingWatchdog(boolean tickingWatchdog) {
        this.tickingWatchdog = tickingWatchdog;
    }

    public boolean isTracingActions() {
        return tracingActions;
    }

    public void setTracingActions(boolean tracingActions) {
        this.tracingActions = tracingActions;
    }

    /**
     * Get the default region selector.
     *
     * @return the default region selector
     */
    public RegionSelectorType getDefaultRegionSelector() {
        return defaultSelector;
    }

    /**
     * Set the default region selector.
     *
     * @param defaultSelector the default region selector
     */
    public void setDefaultRegionSelector(RegionSelectorType defaultSelector) {
        checkNotNull(defaultSelector);
        this.defaultSelector = defaultSelector;
        setDirty();
    }

    /**
     * Get the region selector for defining the selection. If the selection
     * was defined for a different world, the old selection will be discarded.
     *
     * @param world the world
     * @return position the position
     */
    public RegionSelector getRegionSelector(World world) {
        checkNotNull(world);
        if (selector.getWorld() == null || !selector.getWorld().equals(world)) {
            selector.setWorld(world);
            selector.clear();
            if (hasWorldOverride() && !world.equals(getWorldOverride())) {
                setWorldOverride(null);
            }
        }
        return selector;
    }

    /**
     * Set the region selector.
     *
     * @param world the world
     * @param selector the selector
     */
    public void setRegionSelector(World world, RegionSelector selector) {
        checkNotNull(world);
        checkNotNull(selector);
        selector.setWorld(world);
        this.selector = selector;
        if (hasWorldOverride() && !world.equals(getWorldOverride())) {
            setWorldOverride(null);
        }
    }

    /**
     * Returns true if the region is fully defined for the specified world.
     *
     * @param world the world
     * @return true if a region selection is defined
     */
    public boolean isSelectionDefined(World world) {
        checkNotNull(world);
        if (selector.getIncompleteRegion().getWorld() == null || !selector.getIncompleteRegion().getWorld().equals(world)) {
            return false;
        }
        return selector.isDefined();
    }

    /**
     * Get the selection region. If you change the region, you should
     * call learnRegionChanges(). If the selection is not fully defined,
     * the {@code IncompleteRegionException} exception will be thrown.
     *
     * <p>Note that this method will return a region in the current selection world,
     * which is not guaranteed to be the player's world or even the current world
     * override. If you require a specific world, use the
     * {@link LocalSession#getSelection(World)} overload instead.
     *
     * @return the selected region
     * @throws IncompleteRegionException if the region is not fully defined
     */
    public Region getSelection() throws IncompleteRegionException {
        return getSelection(getSelectionWorld());
    }

    /**
     * Get the selection region. If you change the region, you should
     * call learnRegionChanges(). If the selection is defined in
     * a different world, or the selection isn't fully defined,
     * the {@code IncompleteRegionException} exception will be thrown.
     *
     * @param world the world
     * @return a region
     * @throws IncompleteRegionException if no region is selected, or the provided world is null
     */
    public Region getSelection(@Nullable World world) throws IncompleteRegionException {
        if (world == null || selector.getIncompleteRegion().getWorld() == null
                || !selector.getIncompleteRegion().getWorld().equals(world)) {
            throw new IncompleteRegionException() {
                @Override
                public synchronized Throwable fillInStackTrace() {
                    return this;
                }
            };
        }
        return selector.getRegion();
    }

    /**
     * Get the selection world.
     *
     * @return the the world of the selection
     */
    @Nullable
    public World getSelectionWorld() {
        World world = selector.getIncompleteRegion().getWorld();
        if (world instanceof WorldWrapper) {
            return ((WorldWrapper) world).getParent();
        }
        return world;
    }

    /**
     * Gets the clipboard.
     *
     * @return clipboard
     * @throws EmptyClipboardException thrown if no clipboard is set
     */
    public ClipboardHolder getClipboard() throws EmptyClipboardException {
        synchronized (clipboardLock) {
            if (clipboard == null) {
                throw new EmptyClipboardException();
            }
            return clipboard;
        }
    }

    @Nullable
    public ClipboardHolder getExistingClipboard() {
        synchronized (clipboardLock) {
            if (clipboard == null) {
                return null;
            }
            return clipboard;
        }
    }

    public void addClipboard(@Nonnull MultiClipboardHolder toAppend) {
        checkNotNull(toAppend);
        ClipboardHolder existing = getExistingClipboard();
        MultiClipboardHolder multi;
        if (existing instanceof MultiClipboardHolder) {
            multi = (MultiClipboardHolder) existing;
            for (ClipboardHolder holder : toAppend.getHolders()) {
                multi.add(holder);
            }
        } else  {
            multi = toAppend;
            if (existing != null) {
                multi.add(existing);
            }
        }
        setClipboard(multi);
    }

    /**
     * Sets the clipboard.
     *
     * <p>Pass {@code null} to clear the clipboard.</p>
     *
     * @param clipboard the clipboard, or null if the clipboard is to be cleared
     */
    public void setClipboard(@Nullable ClipboardHolder clipboard) {
        synchronized (clipboardLock) {
            if (this.clipboard == clipboard) {
                return;
            }

            if (this.clipboard != null) {
                if (clipboard == null || !clipboard.contains(this.clipboard.getClipboard())) {
                    this.clipboard.close();
                }
            }
            this.clipboard = clipboard;
        }
    }

    /**
     * @return true always - see deprecation notice
     * @deprecated The wand is now a tool that can be bound/unbound.
     */
    @Deprecated
    public boolean isToolControlEnabled() {
        return true;
    }

    /**
     * @param toolControl unused - see deprecation notice
     * @deprecated The wand is now a tool that can be bound/unbound.
     */
    @Deprecated
    public void setToolControl(boolean toolControl) {
    }

    /**
     * Get the maximum number of blocks that can be changed in an edit session.
     *
     * @return block change limit
     */
    public int getBlockChangeLimit() {
        return maxBlocksChanged;
    }

    /**
     * Set the maximum number of blocks that can be changed.
     *
     * @param maxBlocksChanged the maximum number of blocks changed
     */
    public void setBlockChangeLimit(int maxBlocksChanged) {
        this.maxBlocksChanged = maxBlocksChanged;
    }

    /**
     * Get the maximum time allowed for certain executions to run before cancelling them, such as expressions.
     *
     * @return timeout time, in milliseconds
     */
    public int getTimeout() {
        return maxTimeoutTime;
    }

    /**
     * Set the maximum number of blocks that can be changed.
     *
     * @param timeout the time, in milliseconds, to limit certain executions to, or -1 to disable
     */
    public void setTimeout(int timeout) {
        this.maxTimeoutTime = timeout;
    }

    /**
     * Checks whether the super pick axe is enabled.
     *
     * @return status
     */
    public boolean hasSuperPickAxe() {
        return superPickaxe;
    }

    /**
     * Enable super pick axe.
     */
    public void enableSuperPickAxe() {
        superPickaxe = true;
    }

    /**
     * Disable super pick axe.
     */
    public void disableSuperPickAxe() {
        superPickaxe = false;
    }

    /**
     * Toggle the super pick axe.
     *
     * @return whether the super pick axe is now enabled
     */
    public boolean toggleSuperPickAxe() {
        superPickaxe = !superPickaxe;
        return superPickaxe;
    }

    /**
     * Get the position use for commands that take a center point
     * (i.e. //forestgen, etc.).
     *
     * @param actor the actor
     * @return the position to use
     * @throws IncompleteRegionException thrown if a region is not fully selected
     */
    public BlockVector3 getPlacementPosition(Actor actor) throws IncompleteRegionException {
        checkNotNull(actor);
        if (!placeAtPos1) {
            if (actor instanceof Locatable) {
                return ((Locatable) actor).getBlockLocation().toVector().toBlockPoint();
            } else {
                throw new IncompleteRegionException();
            }
        }

        return selector.getPrimaryPosition();
    }

    public void setPlaceAtPos1(boolean placeAtPos1) {
        this.placeAtPos1 = placeAtPos1;
    }

    public boolean isPlaceAtPos1() {
        return placeAtPos1;
    }

    /**
     * Toggle placement position.
     *
     * @return whether "place at position 1" is now enabled
     */
    public boolean togglePlacementPosition() {
        placeAtPos1 = !placeAtPos1;
        return placeAtPos1;
    }

    /**
     * Get a block bag for a player.
     *
     * @param player the player to get the block bag for
     * @return a block bag
     */
    @Nullable
    public BlockBag getBlockBag(Player player) {
        checkNotNull(player);
        if (!useInventory && player.getLimit().INVENTORY_MODE == 0) {
            return null;
        }
        return player.getInventoryBlockBag();
    }

    /**
     * Get the legacy snapshot that has been selected.
     *
     * @return the legacy snapshot
     */
    @Nullable
    public com.sk89q.worldedit.world.snapshot.Snapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Select a legacy snapshot.
     *
     * @param snapshot a legacy snapshot
     */
    public void setSnapshot(@Nullable com.sk89q.worldedit.world.snapshot.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Get the snapshot that has been selected.
     *
     * @return the snapshot
     */
    @Nullable
    public Snapshot getSnapshotExperimental() {
        return snapshotExperimental;
    }

    /**
     * Select a snapshot.
     *
     * @param snapshotExperimental a snapshot
     */
    public void setSnapshotExperimental(@Nullable Snapshot snapshotExperimental) {
        this.snapshotExperimental = snapshotExperimental;
    }

    /**
     * Get the assigned block tool.
     *
     * @return the super pickaxe tool mode
     */
    public BlockTool getSuperPickaxe() {
        return pickaxeMode;
    }

    /**
     * Set the super pick axe tool.
     *
     * @param tool the tool to set
     */
    public void setSuperPickaxe(BlockTool tool) {
        checkNotNull(tool);
        this.pickaxeMode = tool;
    }

    /**
     * Get the tool assigned to the item.
     *
     * @param item the item type
     * @return the tool, which may be {@code null}
     */
    @Nullable
    @Deprecated
    public Tool getTool(ItemType item) {
        synchronized (this.tools) {
            return tools.get(item.getInternalId());
        }
    }

    @Nullable
    public Tool getTool(Player player) {
        loadDefaults(player, false);
        if (!Settings.IMP.EXPERIMENTAL.PERSISTENT_BRUSHES && tools.isEmpty()) {
            return null;
        }
        BaseItem item = player.getItemInHand(HandSide.MAIN_HAND);
        return getTool(item, player);
    }

    private transient boolean loadDefaults = true;

    public Tool getTool(BaseItem item, Player player) {
        loadDefaults(player, false);
        if (Settings.IMP.EXPERIMENTAL.PERSISTENT_BRUSHES && item.getNativeItem() != null) {
            BrushTool tool = BrushCache.getTool(player, this, item);
            if (tool != null) {
                return tool;
            }
        }
        return getTool(item.getType());
    }

    public void loadDefaults(Actor actor, boolean force) {
        if (loadDefaults || force) {
            loadDefaults = false;
            LocalConfiguration config = WorldEdit.getInstance().getConfiguration();
            if (wandItem == null) {
                wandItem = ItemTypes.parse(config.wandItem);
            }
            if (navWandItem == null) {
                navWandItem = ItemTypes.parse(config.navigationWand);
            }
            synchronized (this.tools) {
                if (tools.get(navWandItem.getInternalId()) == null && NavigationWand.INSTANCE.canUse(actor)) {
                    tools.put(navWandItem.getInternalId(), NavigationWand.INSTANCE);
                }
                if (tools.get(wandItem.getInternalId()) == null && SelectionWand.INSTANCE.canUse(actor)) {
                    tools.put(wandItem.getInternalId(), SelectionWand.INSTANCE);
                }
            }
        }
    }

    /**
     * Get the brush tool assigned to the item. If there is no tool assigned
     * or the tool is not assigned, the slot will be replaced with the
     * brush tool.
     *
     * @deprecated FAWE binds to the item, not the type - this allows brushes to persist
     * @param item the item type
     * @return the tool, or {@code null}
     * @throws InvalidToolBindException if the item can't be bound to that item
     */
    @Deprecated
    public BrushTool getBrushTool(ItemType item) throws InvalidToolBindException {
        return getBrushTool(item.getDefaultState(), null, true);
    }

    public BrushTool getBrushTool(Player player) throws InvalidToolBindException {
        return getBrushTool(player, true);
    }

    public BrushTool getBrushTool(Player player, boolean create) throws InvalidToolBindException {
        BaseItem item = player.getItemInHand(HandSide.MAIN_HAND);
        return getBrushTool(item, player, create);
    }

    public BrushTool getBrushTool(BaseItem item, Player player, boolean create) throws InvalidToolBindException {
        if (item.getType().hasBlockType()) {
            throw new InvalidToolBindException(item.getType(), "Blocks can't be used");
        }
        Tool tool = getTool(item, player);
        if (!(tool instanceof BrushTool)) {
            if (create) {
                tool = new BrushTool();
                setTool(item, tool, player);
            } else {
                return null;
            }
        }

        return (BrushTool) tool;
    }

    /**
     * Set the tool.
     *
     * @param item the item type
     * @param tool the tool to set, which can be {@code null}
     * @throws InvalidToolBindException if the item can't be bound to that item
     */
    public void setTool(ItemType item, @Nullable Tool tool) throws InvalidToolBindException {
        if (item.hasBlockType()) {
            throw new InvalidToolBindException(item, "Blocks can't be used");
        }
        if (tool instanceof SelectionWand) {
            changeTool(this.wandItem, this.wandItem = item, tool);
            setDirty();
            return;
        } else if (tool instanceof NavigationWand) {
            changeTool(this.navWandItem, this.navWandItem = item, tool);
            setDirty();
            return;
        }
        setTool(item.getDefaultState(), tool, null);
    }

    public void setTool(Player player, @Nullable Tool tool) throws InvalidToolBindException {
        BaseItemStack item = player.getItemInHand(HandSide.MAIN_HAND);
        setTool(item, tool, player);
    }

    private void changeTool(ItemType oldType, ItemType newType, Tool newTool) {
        if (oldType != null) {
            synchronized (this.tools) {
                this.tools.remove(oldType.getInternalId());
            }
        }
        synchronized (this.tools) {
            if (newTool == null) {
                this.tools.remove(newType.getInternalId());
            } else {
                this.tools.put(newType.getInternalId(), newTool);
            }
        }
    }

    public void setTool(BaseItem item, @Nullable Tool tool, Player player) throws InvalidToolBindException {
        ItemType type = item.getType();
        if (type.hasBlockType() && type.getBlockType().getMaterial().isAir()) {
            throw new InvalidToolBindException(type, "Blocks can't be used");
        } else if (tool instanceof SelectionWand) {
            changeTool(this.wandItem, this.wandItem = item.getType(), tool);
            setDirty();
            return;
        } else if (tool instanceof NavigationWand) {
            changeTool(this.navWandItem, this.navWandItem = item.getType(), tool);
            setDirty();
            return;
        }

        Tool previous;
        if (player != null && (tool instanceof BrushTool || tool == null) && Settings.IMP.EXPERIMENTAL.PERSISTENT_BRUSHES && item.getNativeItem() != null) {
            previous = BrushCache.getCachedTool(item);
            BrushCache.setTool(item, (BrushTool) tool);
            if (tool != null) {
                ((BrushTool) tool).setHolder(item);
            } else {
                synchronized (this.tools) {
                    this.tools.remove(type.getInternalId());
                }
            }
        } else {
            synchronized (this.tools) {
                previous = this.tools.get(type.getInternalId());
                if (tool != null) {
                    this.tools.put(type.getInternalId(), tool);
                } else {
                    this.tools.remove(type.getInternalId());
                }
            }
        }
    }

    /**
     * Returns whether inventory usage is enabled for this session.
     *
     * @return if inventory is being used
     */
    public boolean isUsingInventory() {
        return useInventory;
    }

    /**
     * Set the state of inventory usage.
     *
     * @param useInventory if inventory is to be used
     */
    public void setUseInventory(boolean useInventory) {
        this.useInventory = useInventory;
    }

    /**
     * Get the last script used.
     *
     * @return the last script's name
     */
    @Nullable
    public String getLastScript() {
        return lastScript;
    }

    /**
     * Set the last script used.
     *
     * @param lastScript the last script's name
     */
    public void setLastScript(@Nullable String lastScript) {
        this.lastScript = lastScript;
        setDirty();
    }

    /**
     * Tell the player the WorldEdit version.
     *
     * @param actor the actor
     */
    public void tellVersion(Actor actor) {
        if (hasBeenToldVersion) {
            return;
        }
        hasBeenToldVersion = true;
        actor.sendAnnouncements();
    }

    public boolean shouldUseServerCUI() {
        return this.useServerCUI;
    }

    public void setUseServerCUI(boolean useServerCUI) {
        this.useServerCUI = useServerCUI;
        setDirty();
    }

    /**
     * Update server-side WorldEdit CUI.
     *
     * @param actor The player
     */
    public void updateServerCUI(Actor actor) {
        if (!actor.isPlayer()) {
            return; // This is for players only.
        }

        if (!config.serverSideCUI) {
            return; // Disabled in config.
        }

        Player player = (Player) actor;

        if (!useServerCUI || hasCUISupport) {
            if (cuiTemporaryBlock != null) {
                player.sendFakeBlock(cuiTemporaryBlock, null);
                cuiTemporaryBlock = null;
            }
            return; // If it's not enabled, ignore this.
        }

        BaseBlock block = ServerCUIHandler.createStructureBlock(player);
        if (block != null) {
            // If it's null, we don't need to do anything. The old was already removed.
            Map<String, Tag> tags = block.getNbtData().getValue();
            BlockVector3 tempCuiTemporaryBlock = BlockVector3.at(
                    ((IntTag) tags.get("x")).getValue(),
                    ((IntTag) tags.get("y")).getValue(),
                    ((IntTag) tags.get("z")).getValue()
            );
            if (cuiTemporaryBlock != null && !tempCuiTemporaryBlock.equals(cuiTemporaryBlock)) {
                // Update the existing block if it's the same location
                player.sendFakeBlock(cuiTemporaryBlock, null);
            }
            cuiTemporaryBlock = tempCuiTemporaryBlock;
            player.sendFakeBlock(cuiTemporaryBlock, block);
        } else if (cuiTemporaryBlock != null) {
            // Remove the old block
            player.sendFakeBlock(cuiTemporaryBlock, null);
            cuiTemporaryBlock = null;
        }
    }

    /**
     * Dispatch a CUI event but only if the actor has CUI support.
     *
     * @param actor the actor
     * @param event the event
     */
    public void dispatchCUIEvent(Actor actor, CUIEvent event) {
        checkNotNull(actor);
        checkNotNull(event);

        if (hasCUISupport) {
            actor.dispatchCUIEvent(event);
        } else if (useServerCUI) {
            updateServerCUI(actor);
        }
    }

    /**
     * Dispatch the initial setup CUI messages.
     *
     * @param actor the actor
     */
    public void dispatchCUISetup(Actor actor) {
        if (selector != null) {
            dispatchCUISelection(actor);
        }
    }

    /**
     * Send the selection information.
     *
     * @param actor the actor
     */
    public void dispatchCUISelection(Actor actor) {
        checkNotNull(actor);

        if (!hasCUISupport && useServerCUI) {
            updateServerCUI(actor);
            return;
        }

        if (selector instanceof CUIRegion) {
            CUIRegion tempSel = (CUIRegion) selector;

            if (tempSel.getProtocolVersion() > cuiVersion) {
                actor.dispatchCUIEvent(new SelectionShapeEvent(tempSel.getLegacyTypeID()));
                tempSel.describeLegacyCUI(this, actor);
            } else {
                actor.dispatchCUIEvent(new SelectionShapeEvent(tempSel.getTypeID()));
                tempSel.describeCUI(this, actor);
            }

        }
    }

    /**
     * Describe the selection to the CUI actor.
     *
     * @param actor the actor
     */
    public void describeCUI(Actor actor) {
        checkNotNull(actor);

        // TODO preload

        if (!hasCUISupport) {
            return;
        }

        if (selector instanceof CUIRegion) {
            CUIRegion tempSel = (CUIRegion) selector;

            if (tempSel.getProtocolVersion() > cuiVersion) {
                tempSel.describeLegacyCUI(this, actor);
            } else {
                tempSel.describeCUI(this, actor);
            }

        }
    }

    /**
     * Handle a CUI initialization message.
     *
     * @param text the message
     */
    public void handleCUIInitializationMessage(String text, Actor actor) {
        checkNotNull(text);
        if (this.hasCUISupport) {
            // WECUI is a bit aggressive about re-initializing itself
            // the last attempt to touch handshakes didn't go well, so this will do... for now
            dispatchCUISelection(actor);
            return;
        } else if (this.failedCuiAttempts > 3) {
            return;
        }

        String[] split = text.split("\\|", 2);
        if (split.length > 1 && split[0].equalsIgnoreCase("v")) { // enough fields and right message
            if (split[1].length() > 4) {
                this.failedCuiAttempts ++;
                return;
            }

            int version;
            try {
                version = Integer.parseInt(split[1]);
            } catch (NumberFormatException e) {
                WorldEdit.logger.warn("Error while reading CUI init message: " + e.getMessage());
                this.failedCuiAttempts ++;
                return;
            }
            setCUISupport(true);
            setCUIVersion(version);
            dispatchCUISelection(actor);
        }
    }

    /**
     * Gets the status of CUI support.
     *
     * @return true if CUI is enabled
     */
    public boolean hasCUISupport() {
        return hasCUISupport;
    }

    /**
     * Sets the status of CUI support.
     *
     * @param support true if CUI is enabled
     */
    public void setCUISupport(boolean support) {
        hasCUISupport = support;
    }

    /**
     * Gets the client's CUI protocol version
     *
     * @return the CUI version
     */
    public int getCUIVersion() {
        return cuiVersion;
    }

    /**
     * Sets the client's CUI protocol version
     *
     * @param cuiVersion the CUI version
     */
    public void setCUIVersion(int cuiVersion) {
        if (cuiVersion < 0) {
            throw new IllegalArgumentException("CUI protocol version must be non-negative, but '" + cuiVersion + "' was received.");
        }

        this.cuiVersion = cuiVersion;
    }

    /**
     * Detect date from a user's input.
     *
     * @param input the input to parse
     * @return a date
     */
    @Nullable
    public Calendar detectDate(String input) {
        checkNotNull(input);

        TimeZone tz = TimeZone.getTimeZone(getTimeZone());
        Time.setTimeZone(tz);
        Options opt = new com.sk89q.jchronic.Options();
        opt.setNow(Calendar.getInstance(tz));
        Span date = Chronic.parse(input, opt);
        if (date == null) {
            return null;
        } else {
            return date.getBeginCalendar();
        }
    }

    /**
     * Construct a new edit session.
     *
     * @param actor the actor
     * @return an edit session
     */
    public EditSession createEditSession(Actor actor) {
        return createEditSession(actor, null);
    }

    public EditSession createEditSession(Actor actor, String command) {
        checkNotNull(actor);

        World world = null;
        if (hasWorldOverride()) {
            world = getWorldOverride();
        } else if (actor instanceof Locatable && ((Locatable) actor).getExtent() instanceof World) {
            world = (World) ((Locatable) actor).getExtent();
        }

        // Create an edit session
        EditSession editSession;
        EditSessionBuilder builder = new EditSessionBuilder(world);
        if (actor.isPlayer() && actor instanceof Player) {
            BlockBag blockBag = getBlockBag((Player) actor);
            builder.actor((Player) actor);
            builder.blockBag(blockBag);
        }
        builder.command(command);
        builder.fastmode(!this.sideEffectSet.doesApplyAny());

        editSession = builder.build();

        if (mask != null) {
            editSession.setMask(mask);
        }
        if (sourceMask != null) {
            editSession.setSourceMask(sourceMask);
        }
        if (transform != null) {
            editSession.addTransform(transform);
        }
        editSession.setTickingWatchdog(tickingWatchdog);

        return editSession;
    }

    private void prepareEditingExtents(EditSession editSession, Actor actor) {
        editSession.setSideEffectApplier(sideEffectSet);
        editSession.setReorderMode(reorderMode);
        if (editSession.getSurvivalExtent() != null) {
            editSession.getSurvivalExtent().setStripNbt(!actor.hasPermission("worldedit.setnbt"));
        }
        editSession.setTickingWatchdog(tickingWatchdog);
    }

    /**
     * Gets the side effect applier of this session.
     *
     * @return the side effect applier
     */
    public SideEffectSet getSideEffectSet() {
        return this.sideEffectSet;
    }

    /**
     * Sets the side effect applier for this session
     *
     * @param sideEffectSet the side effect applier
     */
    public void setSideEffectSet(SideEffectSet sideEffectSet) {
        this.sideEffectSet = sideEffectSet;
    }

    /**
     * Checks if the session has fast mode enabled.
     *
     * @return true if fast mode is enabled
     */
    @Deprecated
    public boolean hasFastMode() {
        return !this.sideEffectSet.doesApplyAny();
    }

    /**
     * Set fast mode.
     *
     * @param fastMode true if fast mode is enabled
     */
    @Deprecated
    public void setFastMode(boolean fastMode) {
        this.sideEffectSet = fastMode ? SideEffectSet.none() : SideEffectSet.defaults();
    }

    /**
     * Gets the reorder mode of the session.
     *
     * @return The reorder mode
     */
    public EditSession.ReorderMode getReorderMode() {
        return EditSession.ReorderMode.FAST;
    }

    /**
     * Sets the reorder mode of the session.
     *
     * @param reorderMode The reorder mode
     */
    public void setReorderMode(EditSession.ReorderMode reorderMode) {
    }

    /**
     * Get the mask.
     *
     * @return mask, may be null
     */
    public Mask getMask() {
        return mask;
    }

    /**
     * Get the mask.
     *
     * @return mask, may be null
     */
    public Mask getSourceMask() {
        return sourceMask;
    }

    /**
     * Set a mask.
     *
     * @param mask mask or null
     */
    public void setMask(Mask mask) {
        this.mask = mask;
    }

    /**
     * Set a mask.
     *
     * @param mask mask or null
     */
    public void setSourceMask(Mask mask) {
        this.sourceMask = mask;
    }

    public synchronized void setTextureUtil(TextureUtil texture) {
        this.texture = texture;
    }

    /**
     * Get the TextureUtil currently being used
     */
    @Override
    public TextureUtil getTextureUtil() {
        TextureUtil tmp = texture;
        if (tmp == null) {
            synchronized (this) {
                tmp = Fawe.get().getCachedTextureUtil(true, 0, 100);
                this.texture = tmp;
            }
        }
        return tmp;
    }

    /**
     * Get the preferred wand item for this user, or {@code null} to use the default
     * @return item id of wand item, or {@code null}
     */
    public String getWandItem() {
        return wandItem.getId();
    }

    /**
     * Get the preferred navigation wand item for this user, or {@code null} to use the default
     * @return item id of nav wand item, or {@code null}
     */
    public String getNavWandItem() {
        return navWandItem.getId();
    }

    /**
     * Get the last block distribution stored in this session.
     *
     * @return block distribution or {@code null}
     */
    public List<Countable<BlockState>> getLastDistribution() {
        return lastDistribution == null ? null : Collections.unmodifiableList(lastDistribution);
    }

    /**
     * Store a block distribution in this session.
     */
    public void setLastDistribution(List<Countable<BlockState>> dist) {
        lastDistribution = dist;
    }

    public ResettableExtent getTransform() {
        return transform;
    }

    public void setTransform(ResettableExtent transform) {
        this.transform = transform;
    }


    /**
     * Call when this session has become inactive.
     *
     * <p>This is for internal use only.</p>
     */
    public void onIdle() {
        this.cuiVersion = CUI_VERSION_UNINITIALIZED;
        this.hasCUISupport = false;
        this.failedCuiAttempts = 0;
    }
}
