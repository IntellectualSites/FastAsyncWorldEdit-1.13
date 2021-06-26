//package com.boydti.fawe.object.schematic.visualizer;
//
//import com.boydti.fawe.FaweCache;
//import com.boydti.fawe.beta.IBlocks;
//import com.boydti.fawe.beta.IChunk;
//import com.boydti.fawe.beta.IQueueExtent;
//import com.boydti.fawe.config.BBC;
//import com.boydti.fawe.object.*;
//import com.boydti.fawe.object.brush.visualization.ImmutableVirtualWorld;
//import com.boydti.fawe.object.clipboard.LazyClipboardHolder;
//import com.boydti.fawe.object.clipboard.MultiClipboardHolder;
//import com.boydti.fawe.object.clipboard.URIClipboardHolder;
//import com.boydti.fawe.object.io.NonCloseableInputStream;
//import com.boydti.fawe.object.schematic.Schematic;
//import com.boydti.fawe.util.*;
//import com.google.common.io.ByteSource;
//import com.google.common.io.Files;
//import com.sk89q.jnbt.NBTInputStream;
//import com.sk89q.jnbt.NBTOutputStream;
//import com.sk89q.worldedit.*;
//import com.sk89q.worldedit.entity.Player;
//import com.sk89q.worldedit.event.platform.InputType;
//import com.sk89q.worldedit.event.platform.PlayerInputEvent;
//import com.sk89q.worldedit.extent.clipboard.Clipboard;
//import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
//import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
//import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
//import com.sk89q.worldedit.math.BlockVector2;
//import com.sk89q.worldedit.math.BlockVector3;
//import com.sk89q.worldedit.math.MutableBlockVector2;
//import com.sk89q.worldedit.math.Vector3;
//import com.sk89q.worldedit.session.ClipboardHolder;
//import com.sk89q.worldedit.util.Location;
//import com.sk89q.worldedit.util.TargetBlock;
//import com.sk89q.worldedit.world.World;
//import com.sk89q.worldedit.world.biome.BiomeType;
//import com.sk89q.worldedit.world.biome.BiomeTypes;
//import com.sk89q.worldedit.world.block.BlockState;
//import com.sk89q.worldedit.world.block.BlockTypes;
//import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
//import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
//import it.unimi.dsi.fastutil.objects.ObjectIterator;
//
//import java.io.*;
//import java.net.URI;
//import java.nio.file.LinkOption;
//import java.nio.file.Path;
//import java.util.*;
//
//import static com.google.common.base.Preconditions.checkNotNull;
//
///**
// * An Immutable virtual world used to display & select schematics
// */
//public class SchemVis extends ImmutableVirtualWorld {
//    private static final WeakHashMap<File, Integer> DIMENSION_CACHE = new WeakHashMap<>();
//
//    private final Long2ObjectOpenHashMap<Map.Entry<File, Long>> files;
//    private final Long2ObjectOpenHashMap<IChunk> chunks; // TODO use soft references OR clear chunks outside view distance
//
//    private final MutableBlockVector2 lastPos = new MutableBlockVector2();
//    private final Player player;
//    private final Location origin;
//    private final BlockVector2 chunkOffset;
//    private BlockVector2 lastPosition;
//
//    public static SchemVis create(Player player, Collection<File> files) throws IOException {
//        checkNotNull(player);
//        checkNotNull(files);
//        SchemVis visExtent = new SchemVis(player);
//        for (File file : files) {
//            visExtent.add(file);
//        }
//        visExtent.bind();
//        visExtent.update();
//        return visExtent;
//    }
//
//    public SchemVis(Player player) {
//        this.files = new Long2ObjectOpenHashMap<>();
//        this.chunks = new Long2ObjectOpenHashMap<>();
//        this.player = player;
//
//        // Set the origin to somewhere around where the player currently is
//        Location pos = player.getLocation();
//        this.origin = player.getLocation();
//        this.chunkOffset = BlockVector2.at(pos.getBlockX() >> 4,pos.getBlockZ() >> 4);
//    }
//
//    private Set<File> getFiles(BlockVector2 chunkPosA, BlockVector2 chunkPosB) {
//        BlockVector2 pos1 = BlockVector2.at(Math.min(chunkPosA.getBlockX(), chunkPosB.getBlockX()), Math.min(chunkPosA.getBlockZ(), chunkPosB.getBlockZ()));
//        BlockVector2 pos2 = BlockVector2.at(Math.max(chunkPosA.getBlockX(), chunkPosB.getBlockX()), Math.max(chunkPosA.getBlockZ(), chunkPosB.getBlockZ()));
//        Set<File> contained = new HashSet<>();
//        for (Long2ObjectMap.Entry<Map.Entry<File, Long>> entry : files.long2ObjectEntrySet()) {
//            long key = entry.getLongKey();
//            int chunkX = MathMan.unpairIntX(key);
//            if (chunkX < pos1.getBlockX() || chunkX > pos2.getBlockX()) continue;
//            int chunkZ = MathMan.unpairIntY(key);
//            if (chunkZ < pos1.getBlockZ() || chunkZ > pos2.getBlockZ()) continue;
//            contained.add(entry.getValue().getKey());
//
//        }
//        return contained;
//    }
//
//    @Override
//    public void handlePlayerInput(Player player, PlayerInputEvent event) {
//        int range = 240;
//        Location target = new TargetBlock(player, range, 0.2).getAnyTargetBlock();
//        if (target != null) {
//            int chunkX = target.getBlockX() >> 4;
//            int chunkZ = target.getBlockZ() >> 4;
//            long pos = MathMan.pairInt(chunkX, chunkZ);
//            Map.Entry<File, Long> entry = files.get(pos);
//            if (entry != null) {
//                File cachedFile = entry.getKey();
//                String filename = cachedFile.getName();
//
//                LocalSession session = this.player.getSession();
//                synchronized (this) {
//                    try {
//                        BlockVector2 tmpLastPosition = lastPosition;
//                        lastPosition = BlockVector2.at(chunkX, chunkZ);
//
//                        boolean sneaking = this.player.isSneaking();
//                        if (event.getInputType() == InputType.PRIMARY && !sneaking) {
//
//                            File file = new File(cachedFile.getParentFile(), filename.substring(1, filename.length() - 7));
//                            URI uri = file.toURI();
//                            ClipboardFormat format = ClipboardFormats.findByFile(file);
//                            format.hold(player, uri, new FileInputStream(file));
//                            player.print(TranslatableComponent.of("fawe.worldedit.schematic.schematic.loaded", filename));
//                            session.setVirtualWorld(null);
//                            return;
//                        }
//                        Set<File> toSelect;
//                        if (sneaking && tmpLastPosition != null) toSelect = getFiles(tmpLastPosition, lastPosition);
//                        else toSelect = Collections.singleton(cachedFile);
//
//                        Map<File, Boolean> select = new HashMap<>();
//                        for (File clicked : toSelect) {
//                            ClipboardHolder existing = session.getExistingClipboard();
//
//                            File file = new File(clicked.getParentFile(), filename.substring(1, filename.length() - 7));
//                            URI uri = file.toURI();
//                            ClipboardFormat format = ClipboardFormats.findByFile(file);
//
//                            boolean contains = existing instanceof URIClipboardHolder && ((URIClipboardHolder) existing).contains(uri);
//                            if (contains) {
//                                if (!sneaking) {
//                                    // Remove it
//                                    if (existing instanceof MultiClipboardHolder) {
//                                        MultiClipboardHolder multi = ((MultiClipboardHolder) existing);
//                                        multi.remove(uri);
//                                        if (multi.getClipboards().isEmpty()) session.setClipboard(null);
//                                    } else {
//                                        session.setClipboard(null);
//                                    }
//                                    select.put(clicked, false);
//                                    player.print(TranslatableComponent.of("fawe.worldedit.clipboard.clipboard.cleared"))
//                                }
//                            } else {
//                                // Add it
//                                ByteSource source = Files.asByteSource(file);
//                                MultiClipboardHolder multi = new MultiClipboardHolder(URI.create(""), new LazyClipboardHolder(uri, source, format, null));
//                                session.addClipboard(multi);
//                                select.put(clicked, true);
//                                player.print(TranslatableComponent.of("fawe.worldedit.schematic.schematic.loaded", file.getName()));
//                            }
//                        }
//                        // Resend relevant chunks
//                        World world = this.player.getWorld();
//
//                        ArrayDeque<Long> toSend = new ArrayDeque<>();
//                        ObjectIterator<Long2ObjectMap.Entry<IBlocks>> iter = chunks.long2ObjectEntrySet().fastIterator();
//                            while (iter.hasNext()) {
//                                Long2ObjectMap.Entry<IBlocks> mcaChunkEntry = iter.next();
//                                long curChunkPos = mcaChunkEntry.getLongKey();
//                                Map.Entry<File, Long> curFileEntry = files.get(curChunkPos);
//                                if (curFileEntry != null) {
//                                    Boolean selected = select.get(curFileEntry.getKey());
//                                    if (selected != null) {
//                                        if (!selected) {
//                                            iter.remove();
//                                        } else {
//                                            select(mcaChunkEntry.getValue());
//                                        }
//                                        toSend.add(curChunkPos);
//                                    }
//                                }
//                            }
//                            for (long curChunkPos : toSend) send(packetQueue, MathMan.unpairIntX(curChunkPos), MathMan.unpairIntY(curChunkPos));
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    }
//                }
//            }
//        }
//    }
//
//    /**
//     * Discard chunks outside FOV
//     */
//    private void clean() {
//        if (chunks.size() > 225) {
//            TaskManager.IMP.sync(() -> {
//                if (chunks.size() > 225) {
//                    synchronized (SchemVis.this) {
//                        Location pos = player.toWorldEditPlayer().getLocation();
//                        int centerX = pos.getBlockX() >> 4;
//                        int centerZ = pos.getBlockZ() >> 4;
//                        ObjectIterator<Long2ObjectMap.Entry<MCAChunk>> iter = chunks.long2ObjectEntrySet().fastIterator();
//                        while (iter.hasNext()) {
//                            Long2ObjectMap.Entry<MCAChunk> entry = iter.next();
//                            long pair = entry.getLongKey();
//                            int chunkX = MathMan.unpairIntX(pair);
//                            int chunkZ = MathMan.unpairIntY(pair);
//                            if (Math.abs(centerX - chunkX) > 15 || Math.abs(centerZ - chunkZ) > 15) {
//                                iter.remove();
//                            }
//                        }
//                    }
//                }
//                return null;
//            });
//        }
//    }
//
//    /**
//     * Send a chunk
//     * @param packetQueue
//     * @param chunkX
//     * @param chunkZ
//     */
//    private void send(IQueueExtent packetQueue, int chunkX, int chunkZ) {
//        TaskManager.IMP.getPublicForkJoinPool().submit(() -> {
//            try {
//                int OX = chunkOffset.getBlockX();
//                int OZ = chunkOffset.getBlockZ();
//                FaweChunk toSend = getSnapshot(chunkX, chunkZ);
//                toSend.setLoc(SchemVis.this, chunkX + OX, chunkZ + OZ);
//                packetQueue.sendChunkUpdate(toSend, SchemVis.this.player);
//            } catch (Throwable e) {
//                e.printStackTrace();
//            }
//        });
//    }
//
//    /**
//     * The offset for this virtual world
//     * @return offset vector
//     */
//    @Override
//    public Vector3 getOrigin() {
//        return Vector3.at(chunkOffset.getBlockX() << 4, 0, chunkOffset.getBlockZ() << 4);
//    }
//
//    private Map.Entry<File, Long> getEntry(File file, long position) {
//        return new AbstractMap.SimpleEntry<>(file, position);
//    }
//
//    /**
//     * Replace the blocks with glass, to indicate it's been selected
//     * @param chunk
//     */
//    private void select(IBlocks chunk) {
//        for (int layer = 0; layer < 16; layer++) {
//            if (!chunk.hasSection(layer)) continue;
//
//            char[] ids = chunk.ids[layer];
//            if (ids != null) {
//                for (int i = 0; i < ids.length; i++) {
//                    // TODO FIXME update to 1.13
//                    if (ids[i] != 0) ids[i] = (byte) BlockTypes.WHITE_STAINED_GLASS.getInternalId();
//                }
//            }
//        }
//    }
//
//    /**
//     * Cache a chunk
//     * @param chunk
//     */
//    private void cacheChunk(IChunk chunk, boolean selected) {
//        if (selected) {
//            select(chunk);
//        }
//        synchronized (this) {
//            chunks.put(pair, chunk);
//        }
//    }
//
//    /**
//     * Get the next free position for a schematic of the provided dimensions
//     * @param schemDimensions
//     * @return
//     */
//    private BlockVector2 registerAndGetChunkOffset(BlockVector2 schemDimensions, File file) {
//        int chunkX = schemDimensions.getBlockX() >> 4;
//        int chunkZ = schemDimensions.getBlockZ() >> 4;
//        MutableBlockVector2 pos2 = new MutableBlockVector2();
//        MutableBlockVector2 curPos = lastPos;
//        // Find next free position
//        while (!isAreaFree(curPos, pos2.setComponents(curPos.getBlockX() + chunkX, curPos.getBlockZ() + chunkZ))) {
////            if (curPos == lastPos && !files.containsKey(MathMan.pairInt(curPos.getBlockX(), curPos.getBlockZ()))) {
////                curPos = new MutableBlockVector2();
////                curPos.setComponents(lastPos.getBlockX(), lastPos.getBlockZ());
////            }
//            curPos.nextPosition();
//        }
//        // Register the chunks
//        Map.Entry<File, Long> originValue = getEntry(file, MathMan.pairInt(curPos.getBlockX(), curPos.getBlockZ()));
//        long pairX, pos;
//        for (int x = 0; x <= chunkX; x++) {
//            int xx = curPos.getBlockX() + x;
//            pairX = ((long) xx) << 32;
//            for (int z = 0; z <= chunkZ; z++) {
//                int zz = curPos.getBlockZ() + z;
//                pos = pairX + (zz & 0xffffffffL);
//                files.put(pos, originValue);
//            }
//        }
//        for (int i = 0; i < Math.min(chunkX, chunkZ); i++) curPos.nextPosition();
//        return curPos;
//    }
//
//    private boolean isAreaFree(BlockVector2 chunkPos1, BlockVector2 chunkPos2 /* inclusive */) {
//        for (int x = chunkPos1.getBlockX(); x <= chunkPos2.getBlockX(); x++) {
//            for (int z = chunkPos1.getBlockZ(); z <= chunkPos2.getBlockZ(); z++) {
//                if (files.containsKey(MathMan.pairInt(x, z)) || (x == 0 && z == 0)) return false;
//            }
//        }
//        return true;
//    }
//
//    private boolean isSelected(File file) {
//        ClipboardHolder clipboard = player.getSession().getExistingClipboard();
//        if (clipboard != null) {
//            if (clipboard instanceof URIClipboardHolder) {
//                return ((URIClipboardHolder) clipboard).contains(file.toURI());
//            }
//        }
//        return false;
//    }
//
//    public void add(File file) throws IOException {
//        File cached = new File(file.getParentFile(), "." + file.getName() + ".cached");
//        Integer dimensionPair = DIMENSION_CACHE.get(file);
//        if (dimensionPair != null) {
//            int width = (char) MathMan.unpairX(dimensionPair);
//            int length = (char) MathMan.unpairY(dimensionPair);
//            BlockVector2 dimensions = BlockVector2.at(width, length);
//            BlockVector2 offset = registerAndGetChunkOffset(dimensions, cached);
//            return;
//        }
//        if (cached.exists() && file.lastModified() <= cached.lastModified()) {
//            try (InputStream fis = new BufferedInputStream(new FileInputStream(cached), 4)) {
//                BlockVector2 dimensions = BlockVector2.at(IOUtil.readVarInt(fis), IOUtil.readVarInt(fis));
//                DIMENSION_CACHE.put(file, MathMan.pair((short) dimensions.getBlockX(), (short) dimensions.getBlockZ()));
//                BlockVector2 offset = registerAndGetChunkOffset(dimensions, cached);
//            }
//        } else {
//            try {
//                player.print("Converting: " + file);
//                cached.createNewFile();
//                try (FileInputStream in = new FileInputStream(file)) {
//                    ClipboardFormat format = ClipboardFormats.findByFile(file);
//                    if (format != null) {
//                        ClipboardReader reader = format.getReader(in);
//                        Clipboard clipboard = reader.read();
//                        clipboard.setOrigin(clipboard.getMinimumPoint());
//                        try {
//                            MCAQueue queue = new MCAQueue(null, null, false);
//                            BlockVector2 dimensions = clipboard.getDimensions().toBlockVector2();
//                            BlockVector2 offset = registerAndGetChunkOffset(dimensions, cached);
//                            new Schematic(clipboard).paste(queue, BlockVector3.ZERO, true);
//                            try (FileOutputStream fos = new FileOutputStream(cached)) {
//                                IOUtil.writeVarInt(fos, dimensions.getBlockX());
//                                IOUtil.writeVarInt(fos, dimensions.getBlockZ());
//
//                                try (FaweOutputStream cos = MainUtil.getCompressedOS(fos, 2)) {
//                                    NBTOutputStream nos = new NBTOutputStream((DataOutput) cos);
//                                    Collection<FaweChunk> writeChunks = queue.getFaweChunks();
//                                    cos.writeInt(writeChunks.size());
//
//                                    boolean selected = isSelected(file);
//
//                                    for (FaweChunk chunk : writeChunks) {
//                                        MCAChunk mcaChunk = ((MCAChunk) chunk);
//                                        mcaChunk.write(nos);
//                                        mcaChunk.setLoc(this, mcaChunk.getX() + offset.getBlockX(), mcaChunk.getZ() + offset.getBlockZ());
//                                        if (Math.abs(mcaChunk.getX()) <= 15 && Math.abs(mcaChunk.getZ()) <= 15) {
//                                            cacheChunk(mcaChunk, selected);
//                                        }
//                                    }
//                                }
//                            }
//                            if (System.getProperty("os.name").contains("Windows")) {
//                                Path path = cached.toPath();
//                                Object hidden = java.nio.file.Files.getAttribute(path, "dos:hidden", LinkOption.NOFOLLOW_LINKS);
//                                if (hidden != null) {
//                                    //link file to DosFileAttributes
//                                    java.nio.file.Files.setAttribute(path, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
//                                }
//                            }
//
//                            DIMENSION_CACHE.put(file, MathMan.pair((short) dimensions.getBlockX(), (short) dimensions.getBlockZ()));
//                        } finally {
//                            if (clipboard instanceof Closeable) {
//                                ((Closeable) clipboard).close();
//                            }
//                        }
//                    }
//                }
//            } catch (Throwable e) {
//                e.printStackTrace();
//                cached.delete();
//            }
//        }
//    }
//
//    private synchronized MCAChunk getCachedChunk(int chunkX, int chunkZ) {
//        return chunks.get(MathMan.pairInt(chunkX, chunkZ));
//    }
//
//    private MCAChunk getChunk(int chunkX, int chunkZ) {
//        long pair = MathMan.pairInt(chunkX, chunkZ);
//        // Check cached
//        MCAChunk chunk = getCachedChunk(chunkX, chunkZ);
//        if (chunk != null) return chunk;
//
//        // We need to cache it
//        Map.Entry<File, Long> entry = files.get(pair);
//        if (entry != null) {
//            File cached = entry.getKey();
//
//            // Guard caching by other threads
//            synchronized (cached) {
//                chunk = getCachedChunk(chunkX, chunkZ);
//
//                // Read chunks from disk
//                if (chunk == null) {
//                    clean();
//                    String filename = cached.getName();
//                    File file = new File(cached.getParentFile(), filename.substring(1, filename.length() - 7));
//                    boolean selected = isSelected(file);
//
//                    long origin = entry.getValue();
//                    int OCX = MathMan.unpairIntX(origin);
//                    int OCZ = MathMan.unpairIntY(origin);
//                    try (FileInputStream fis = new FileInputStream(cached); FaweInputStream in = MainUtil.getCompressedIS(fis)) {
//                        NonCloseableInputStream nonCloseable = new NonCloseableInputStream(in);
//                        try (NBTInputStream nis = new NBTInputStream(nonCloseable)) {
//                            int numChunks = in.readInt();
//                            for (int i = 0; i < numChunks; i++) {
//                                MCAChunk mcaChunk = new MCAChunk(nis, null, 0, 0, true);
//                                mcaChunk.setLoc(this, mcaChunk.getX() + OCX, mcaChunk.getZ() + OCZ);
//                                cacheChunk(mcaChunk, selected);
//                            }
//                        }
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    // Return the cached chunk, or an empty one
//                    chunk = getCachedChunk(chunkX, chunkZ);
//                    if (chunk == null) {
//                        // TODO use shared chunk
//                        // TODO synchronize on sending chunk packet
//                        cacheChunk(chunk = new MCAChunk(this, chunkX, chunkZ), selected);
//                    }
//                }
//            }
//        } else {
//            chunk = new MCAChunk(this, chunkX, chunkZ);
//        }
//        return chunk;
//    }
//
//    /**
//     * Return a lazily evaluated chunk
//     * @param chunkX
//     * @param chunkZ
//     * @return lazy chunk
//     */
//    @Override
//    public FaweChunk getSnapshot(int chunkX, int chunkZ) {
//        return new LazyFaweChunk<MCAChunk>(this, chunkX, chunkZ) {
//            @Override
//            public MCAChunk getChunk() {
//                MCAChunk tmp = SchemVis.this.getChunk(chunkX, chunkZ);
//                tmp.setLoc(SchemVis.this, getX(), getZ());
//                return tmp;
//            }
//
//            @Override
//            public void addToQueue() {
//                MCAChunk cached = getCachedChunk();
//                if (cached != null) setChunk(cached);
//            }
//        };
//    }
//
//    @Override
//    public FawePlayer getActor() {
//        return player;
//    }
//
//    public void bind() {
//        player.setVirtualWorld(this);
//    }
//
//    /**
//     * Send all chunks to the player
//     */
//    @Override
//    public void update() {
//        IQueueExtent packetQueue = SetQueue.IMP.getNewQueue(player.getWorld(), true, false);
//
//        if (!packetQueue.supports(Capability.CHUNK_PACKETS)) {
//            return;
//        }
//
//        int OX = chunkOffset.getBlockX();
//        int OZ = chunkOffset.getBlockZ();
//
//        Location position = player.toWorldEditPlayer().getLocation();
//        int pcx = (position.getBlockX() >> 4) - OX;
//        int pcz = (position.getBlockZ() >> 4) - OZ;
//
//        int scx = pcx - 15;
//        int scz = pcz - 15;
//        int ecx = pcx + 15;
//        int ecz = pcz + 15;
//
//        for (int cz = scz; cz <= ecz; cz++) {
//            for (int cx = scx; cx <= ecx; cx++) {
//                send(packetQueue, cx, cz);
//            }
//        }
//    }
//
//    @Override
//    public void sendChunk(FaweChunk chunk) { /* do nothing - never used  */ }
//
//    @Override
//    public void sendChunk(int x, int z, int bitMask) { /* do nothing - never used*/ }
//
//    @Override
//    public BiomeType getBiomeType(int x, int y, int z) throws FaweCache.CHUNK {
//        // TODO later (currently not used)
//        return BiomeTypes.FOREST;
//    }
//
//    @Override
//    public int getCombinedId4Data(int x, int y, int z) throws FaweCache.CHUNK {
//        MCAChunk chunk = getChunk(x >> 4, z >> 4);
//        if (y < 0 || y > 255) return 0;
//        return chunk.getBlockCombinedId(x & 15, y, z & 15);
//    }
//
//    /**
//     * Closes this virtual world and sends the normal world chunks to the player
//     * @throws IOException
//     */
//    @Override
//    public synchronized void close(boolean update) throws IOException {
//        clear();
//        chunks.clear();
//        files.clear();
//        player.getActor().setPosition(origin, origin.getPitch(), origin.getYaw());
//        if (update) {
//            IQueueExtent packetQueue = SetQueue.IMP.getNewQueue(player.getWorld(), true, false);
//
//            int OX = chunkOffset.getBlockX();
//            int OZ = chunkOffset.getBlockZ();
//
//            Location position = player.toWorldEditPlayer().getLocation();
//            int pcx = (position.getBlockX() >> 4) - OX;
//            int pcz = (position.getBlockZ() >> 4) - OZ;
//
//            int scx = pcx - 15;
//            int scz = pcz - 15;
//            int ecx = pcx + 15;
//            int ecz = pcz + 15;
//
//            for (int cz = scz; cz <= ecz; cz++) {
//                for (int cx = scx; cx <= ecx; cx++) {
//                    packetQueue.sendChunk(cx + OX, cz + OZ, 0);
//                }
//            }
//        }
//    }
//}
