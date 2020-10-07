package com.sk89q.worldedit.bukkit.adapter;

import com.boydti.fawe.beta.IChunkCache;
import com.boydti.fawe.beta.IChunkGet;
import com.boydti.fawe.beta.implementation.queue.SingleThreadQueueExtent;
import com.boydti.fawe.util.MathMan;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.RegenOptions;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.bukkit.generator.BlockPopulator;

/**
 * Represents an abstract regeneration handler.
 * @param <IChunkAccess> the type of the {@Code IChunkAccess}
 * @param <ProtoChunk> the type of the {@Code ProtoChunk
 * @param <Chunk> the type of the {@Code Chunk}
 * @param <ChunkStatus> the type of the {@Code ChunkStatusWrapper} wrapping the {@Code ChunkStatus} enum
 */
public abstract class Regenerator<IChunkAccess, ProtoChunk extends IChunkAccess, Chunk extends IChunkAccess, ChunkStatus extends Regenerator.ChunkStatusWrapper<IChunkAccess>> {

    protected final org.bukkit.World originalBukkitWorld;
    protected final Region region;
    protected final Extent target;
    protected final RegenOptions options;

    //runtime
    protected final Map<ChunkStatus, Concurrency> chunkStati = new LinkedHashMap<>();
    protected boolean generateConcurrent = true;
    protected long seed;
    
    private final Long2ObjectLinkedOpenHashMap<ProtoChunk> protoChunks = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
    private ExecutorService executor;
    private SingleThreadQueueExtent source;

    /**
     * Initializes an abstract regeneration handler.
     * @param originalBukkitWorld the Bukkit world containing all the information on how to regenerate the {code Region}
     * @param region the selection to regenerate
     * @param target the target {@code Extent} to paste the regenerated blocks into
     * @param options the options to used while regenerating and pasting into the target {@code Extent}
     */
    public Regenerator(org.bukkit.World originalBukkitWorld, Region region, Extent target, RegenOptions options) {
        this.originalBukkitWorld = originalBukkitWorld;
        this.region = region;
        this.target = target;
        this.options = options;
    }

    /**
     * Regenerates the selected {@code Region}.
     * @return whether or not the regeneration process was successful
     * @throws Exception when something goes terribly wrong
     */
    public boolean regenerate() throws Exception {
        if (!prepare()) {
            return false;
        }

        try {
            if (!initNewWorld()) {
                cleanup0();
                return false;
            }
        } catch (Exception e) {
            cleanup0();
            throw e;
        }

        try {
            if (!generate()) {
                cleanup0();
                return false;
            }
        } catch (Exception e) {
            cleanup0();
            throw e;
        }

        try {
            copyToWorld();
        } catch (Exception e) {
            cleanup0();
            throw e;
        }

        cleanup0();
        return true;
    }

    /**
     * Returns the {@code ProtoChunk} at the given chunk coordinates.
     * @param x the chunk x coordinate
     * @param z the chunk z coordinate
     * @return the {@code ProtoChunk} at the given chunk coordinates or null if it is not part of the regeneration process or has not been initialized yet.
     */
    protected ProtoChunk getProtoChunkAt(int x, int z) {
        return protoChunks.get(MathMan.pairInt(x, z));
    }

    /**
     * Returns the {@code Chunk} at the given chunk coordinates.
     * @param x the chunk x coordinate
     * @param z the chunk z coordinate
     * @return the {@code Chunk} at the given chunk coordinates or null if it is not part of the regeneration process or has not been converted yet.
     */
    protected Chunk getChunkAt(int x, int z) {
        return chunks.get(MathMan.pairInt(x, z));
    }

    private boolean generate() throws Exception {
        if (generateConcurrent) {
            //Using concurrent chunk generation
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        } else {
            //using sequential chunk generation, concurrent not supported
        }

        long start = System.currentTimeMillis();

        //TODO: can we get that required radius down without affecting chunk generation (e.g. strucures, features, ...)?
        //TODO: maybe do some chunk stages in parallel, e.g. those not requiring neighbour chunks?
        //      for those ChunkStati that need neighbox chunks a special queue could help (e.g. for FEATURES and NOISE)
        //generate chunk coords lists with a certain radius
        Int2ObjectOpenHashMap<List<Long>> chunkCoordsForRadius = new Int2ObjectOpenHashMap<>();
        time(() -> {
            chunkStati.keySet().stream().map(ChunkStatus::requiredNeigborChunkRadius).distinct().forEach(radius -> {
                if (radius == -1) //ignore ChunkStatus.EMPTY
                    return;
                int border = 16 - radius; //9 = 8 + 1, 8: max border radius used in chunk stages, 1: need 1 extra chunk for chunk features to generate at the border of the region
                chunkCoordsForRadius.put(radius, getChunkCoordsRegen(region, border));
            });
        }, "precomputing chunkCoordsForRadius lists");

        //create chunks
        time(() -> {
            for (Long xz : chunkCoordsForRadius.get(0)) {
                ProtoChunk chunk = createProtoChunk(MathMan.unpairIntX(xz), MathMan.unpairIntY(xz));
                protoChunks.put(xz, chunk);
            }
        }, "ctor");

        //generate lists for RegionLimitedWorldAccess, need to be square with odd length (e.g. 17x17), 17 = 1 middle chunk + 8 border chunks * 2
        Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<List<IChunkAccess>>> worldlimits = new Int2ObjectOpenHashMap<>();
        time(() -> {
            chunkStati.keySet().stream().map(ChunkStatus::requiredNeigborChunkRadius).distinct().forEach(radius -> {
                if (radius == -1) //ignore ChunkStatus.EMPTY
                    return;
                Long2ObjectOpenHashMap<List<IChunkAccess>> map = new Long2ObjectOpenHashMap<>();
                for (Long xz : chunkCoordsForRadius.get(radius)) {
                    int x = MathMan.unpairIntX(xz);
                    int z = MathMan.unpairIntY(xz);
                    List<IChunkAccess> l = new ArrayList<>((radius + 1 + radius) * (radius + 1 + radius));
                    for (int zz = z - radius; zz <= z + radius; zz++) { //order is important, first z then x
                        for (int xx = x - radius; xx <= x + radius; xx++) {
                            l.add(protoChunks.get(MathMan.pairInt(xx, zz)));
                        }
                    }
                    map.put(xz, l);
                }
                worldlimits.put(radius, map);
            });
        }, "precomputing RegionLimitedWorldAccess chunks lists");

        //run generation tasks exluding FULL chunk status
        for (Map.Entry<ChunkStatus, Concurrency> entry : chunkStati.entrySet()) {
            time(() -> {
                ChunkStatus chunkStatus = entry.getKey();
                int radius = Math.max(0, chunkStatus.requiredNeigborChunkRadius()); //EMPTY.requiredNeigborChunkRadius() == -1

                List<Long> coords = chunkCoordsForRadius.get(radius);
                if (this.generateConcurrent && entry.getValue() == Concurrency.RADIUS) {
                    SequentialTasks<ConcurrentTasks<SequentialTasks<Long>>> tasks = getChunkStatusTaskRows(coords, radius);
                    for (ConcurrentTasks<SequentialTasks<Long>> para : tasks) {
                        List scheduled = new ArrayList<>(tasks.size());
                        for (SequentialTasks<Long> row : para) {
                            scheduled.add((Callable) () -> {
                                for (Long xz : row) {
                                    chunkStatus.processChunkSave(xz, worldlimits.get(radius).get(xz));
                                }
                                return null;
                            });
                        }
                        try {
                            List<Future> futures = executor.invokeAll(scheduled);
                            for (Future future : futures) {
                                future.get();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else if (this.generateConcurrent && entry.getValue() == Concurrency.FULL) {
                    // every chunk can be processed individually
                    List scheduled = new ArrayList(coords.size());
                    for (long xz : coords) {
                        scheduled.add((Callable) () -> {
                            chunkStatus.processChunkSave(xz, worldlimits.get(radius).get(xz));
                            return null;
                        });
                    }
                    try {
                        List<Future> futures = executor.invokeAll(scheduled);
                        for (Future future : futures) {
                            future.get();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else { // Concurrency.NONE or generateConcurrent == false
                    // run sequential
                    for (long xz : coords) {
                        chunkStatus.processChunkSave(xz, worldlimits.get(radius).get(xz));
                    }
                }
            }, entry.getKey().name());
        }

        //convert to proper chunks
        time(() -> {
            for (Long xz : chunkCoordsForRadius.get(0)) {
                ProtoChunk proto = protoChunks.get(xz);
                chunks.put(xz, createChunk(proto));
            }
        }, "converting to chunks");

        //final chunkstatus
        time(() -> {
            ChunkStatus chunkStatus = getFullChunkStatus();
            for (Long xz : chunkCoordsForRadius.get(0)) { //FULL.f() == 0!
                Chunk chunk = chunks.get(xz);
                chunkStatus.processChunkSave(xz, Arrays.asList(chunk));
            }
        }, "full");

        //populate
        List<BlockPopulator> populators = getBlockPopulators();
        time(() -> {
            for (Long xz : chunkCoordsForRadius.get(0)) {
                int x = MathMan.unpairIntX(xz);
                int z = MathMan.unpairIntY(xz);

                //prepare chunk seed
                Random random = getChunkRandom(seed, x, z);

                //actually populate
                Chunk c = chunks.get(xz);
                populators.forEach(pop -> {
                    populate(c, random, pop);
                });
            }
        }, "populate with " + populators.size() + " populators");

        source = new SingleThreadQueueExtent();
        source.init(null, initSourceQueueCache(), null);
        return true;
    }

    private void copyToWorld() {
        //Setting Blocks
        long start = System.currentTimeMillis();
        boolean genbiomes = options.shouldRegenBiomes();
        for (BlockVector3 vec : region) {
            target.setBlock(vec, source.getBlock(vec));
            if (genbiomes) {
                target.setBiome(vec, source.getBiome(vec));
            }
//                    realExtent.setSkyLight(vec, extent.getSkyLight(vec));
//                    realExtent.setBlockLight(vec, extent.getBrightness(vec));
        }
    }
    
    private void cleanup0() {
        if (executor != null)
            executor.shutdownNow();
        cleanup();
    }
    
    //functions to be implemented by sub class
    /**
     * Implement the preparation process in here. DO NOT instanciate any variable here that require the cleanup function. This function is for gathering further information before initializing a new
     * world.
     * 
     * Fields required to be initialized: chunkStati, seed
     *
     * @return
     */
    protected abstract boolean prepare();

    /**
     * Implement the creation of the seperate world in here.
     * 
     * Fields required to be initialized: generateConcurrent
     *
     * @return true if everything went fine, otherwise false. When false is returned the Regenerator halts the regeneration process and calls the cleanup function.
     * @throws java.lang.Exception When the implementation of this method throws and exception the Regenerator halts the regeneration process and calls the cleanup function.
     */
    protected abstract boolean initNewWorld() throws Exception;
    
    /**
     * Implement the cleanup of all the mess that is created during the regeneration process (initNewWorld() and generate()).This function must not throw any exceptions.
     */
    protected abstract void cleanup();
    
    //functions to implement by sub class - regenate related
    /**
     * Implement the initialization of a {@code ProtoChunk} here.
     *
     * @param x the x coorinate of the {@code ProtoChunk} to create
     * @param z the z coorinate of the {@code ProtoChunk} to create
     * @return an initialized {@code ProtoChunk}
     */
    protected abstract ProtoChunk createProtoChunk(int x, int z);

    /**
     * Implement the convertion of a {@code ProtoChunk} to a {@code Chunk} here.
     *
     * @param protoChunk the {@code ProtoChunk} to be converted to a {@code Chunk}
     * @return the converted {@code Chunk}
     */
    protected abstract Chunk createChunk(ProtoChunk protoChunk);

    /**
     * Return the {@code ChunkStatus.FULL} here.
     *
     * @return {@code ChunkStatus.FULL}
     */
    protected abstract ChunkStatus getFullChunkStatus();

    /**
     * Return a list of {@code BlockPopulator} used to populate the original world here.
     *
     * @return {@code ChunkStatus.FULL}
     */
    protected abstract List<BlockPopulator> getBlockPopulators();

    /**
     * Implement the population of the {@code Chunk} with the given chunk random and {@code BlockPopulator} here.
     *
     * @param chunk the {@code Chunk} to populate
     * @param random the chunk random to use for population
     * @param pop the {@code BlockPopulator} to use
     */
    protected abstract void populate(Chunk chunk, Random random, BlockPopulator pop);
    
    /**
     * Implement the initialization an {@code IChunkCache<IChunkGet>} here. Use will need the {@code getChunkAt} function
     * @return an initialized {@code IChunkCache<IChunkGet>}
     */
    protected abstract IChunkCache<IChunkGet> initSourceQueueCache();
    
    //algorithms
    private List<Long> getChunkCoordsRegen(Region region, int border) { //needs to be square num of chunks
        BlockVector3 oldMin = region.getMinimumPoint();
        BlockVector3 newMin = BlockVector3.at((oldMin.getX() >> 4 << 4) - border * 16, oldMin.getY(), (oldMin.getZ() >> 4 << 4) - border * 16);
        BlockVector3 oldMax = region.getMaximumPoint();
        BlockVector3 newMax = BlockVector3.at((oldMax.getX() >> 4 << 4) + (border + 1) * 16 - 1, oldMax.getY(), (oldMax.getZ() >> 4 << 4) + (border + 1) * 16 - 1);
        Region adjustedRegion = new CuboidRegion(newMin, newMax);
        return adjustedRegion.getChunks().stream()
                .map(c -> BlockVector2.at(c.getX(), c.getZ()))
                .sorted(Comparator.<BlockVector2>comparingInt(c -> c.getZ()).thenComparingInt(c -> c.getX())) //needed for RegionLimitedWorldAccess
                .map(c -> MathMan.pairInt(c.getX(), c.getZ()))
                .collect(Collectors.toList());
    }

    /**
     * Creates a list of chunkcoord rows that may be executed concurrently
     *
     * @param allcoords the coords that should be sorted into rows, must be sorted by z and x
     * @param requiredNeighborChunkRadius the radius of neighbor chunks that may not be written to conccurently (ChunkStatus.requiredNeighborRadius)
     * @return a list of chunkcoords rows that may be executed concurrently
     */
    private SequentialTasks<ConcurrentTasks<SequentialTasks<Long>>> getChunkStatusTaskRows(List<Long> allcoords, int requiredNeighborChunkRadius) {
        int requiredneighbors = Math.max(0, requiredNeighborChunkRadius);

        int minx = allcoords.isEmpty() ? 0 : MathMan.unpairIntX(allcoords.get(0));
        int maxx = allcoords.isEmpty() ? 0 : MathMan.unpairIntX(allcoords.get(allcoords.size() - 1));
        int minz = allcoords.isEmpty() ? 0 : MathMan.unpairIntY(allcoords.get(0));
        int maxz = allcoords.isEmpty() ? 0 : MathMan.unpairIntY(allcoords.get(allcoords.size() - 1));
        SequentialTasks<ConcurrentTasks<SequentialTasks<Long>>> tasks;
        if (maxz - minz > maxx - minx) {
            int numlists = Math.min(requiredneighbors * 2 + 1, maxx - minx + 1);

            Int2ObjectOpenHashMap<SequentialTasks<Long>> byx = new Int2ObjectOpenHashMap();
            int expectedListLength = (allcoords.size() + 1) / (maxx - minx);

            //init lists
            for (int i = minx; i <= maxx; i++) {
                byx.put(i, new SequentialTasks(expectedListLength));
            }

            //sort into lists by x coord
            for (Long xz : allcoords) {
                byx.get(MathMan.unpairIntX(xz)).add(xz);
            }

            //create parallel tasks
            tasks = new SequentialTasks(numlists);
            for (int offset = 0; offset < numlists; offset++) {
                ConcurrentTasks<SequentialTasks<Long>> para = new ConcurrentTasks((maxz - minz + 1) / numlists + 1);
                for (int i = 0; minx + i * numlists + offset <= maxx; i++)
                    para.add(byx.get(minx + i * numlists + offset));
                tasks.add(para);
            }
        } else {
            int numlists = Math.min(requiredneighbors * 2 + 1, maxz - minz + 1);

            Int2ObjectOpenHashMap<SequentialTasks<Long>> byz = new Int2ObjectOpenHashMap();
            int expectedListLength = (allcoords.size() + 1) / (maxz - minz);

            //init lists
            for (int i = minz; i <= maxz; i++) {
                byz.put(i, new SequentialTasks(expectedListLength));
            }

            //sort into lists by x coord
            for (Long xz : allcoords) {
                byz.get(MathMan.unpairIntY(xz)).add(xz);
            }

            //create parallel tasks
            tasks = new SequentialTasks(numlists);
            for (int offset = 0; offset < numlists; offset++) {
                ConcurrentTasks<SequentialTasks<Long>> para = new ConcurrentTasks((maxx - minx + 1) / numlists + 1);
                for (int i = 0; minz + i * numlists + offset <= maxz; i++)
                    para.add(byz.get(minz + i * numlists + offset));
                tasks.add(para);
            }
        }

        return tasks;
    }

    protected static Random getChunkRandom(long worldseed, int x, int z) {
        Random random = new Random();
        random.setSeed(worldseed);
        long xRand = random.nextLong() / 2L * 2L + 1L;
        long zRand = random.nextLong() / 2L * 2L + 1L;
        random.setSeed((long) x * xRand + (long) z * zRand ^ worldseed);
        return random;
    }

    //util
    public static void time(Runnable r, String text) {
        long starttask = System.currentTimeMillis();
//            System.out.println(text);
        r.run();
        //Debug outputs:
        //System.out.println(text + " took " + (System.currentTimeMillis() - starttask) + "ms");
    }

    //classes
    public static abstract class ChunkStatusWrapper<IChunkAccess> {

        /**
         * Must not return a negative integer
         *
         * @return the radius of required neighbor chunk
         */
        public abstract int requiredNeigborChunkRadius();

        public abstract String name();

        public abstract void processChunk(Long xz, List<IChunkAccess> accessibleChunks);

        void processChunkSave(Long xz, List<IChunkAccess> accessibleChunks) {
            try {
                processChunk(xz, accessibleChunks);
            } catch (Exception e) {
                System.err.println("Error while running " + name() + " on chunk " + MathMan.unpairIntX(xz) + "/" + MathMan.unpairIntY(xz));
                e.printStackTrace();
            }
        }
    }

    public enum Concurrency {
        FULL,
        RADIUS,
        NONE
    }

    public static class SequentialTasks<T> extends Tasks<T> {

        public SequentialTasks(int expectedsize) {
            super(expectedsize);
        }
    }

    public static class ConcurrentTasks<T> extends Tasks<T> {

        public ConcurrentTasks(int expectedsize) {
            super(expectedsize);
        }
    }

    public static class Tasks<T> implements Iterable<T> {

        private final List<T> tasks;

        public Tasks(int expectedsize) {
            tasks = new ArrayList(expectedsize);
        }

        public void add(T task) {
            tasks.add(task);
        }

        public List<T> list() {
            return tasks;
        }

        public int size() {
            return tasks.size();
        }
        
        @Override
        public Iterator<T> iterator() {
            return tasks.iterator();
        }

        @Override
        public String toString() {
            return toString(0);
        }
        
        public String toString(int indent) {
            String ret = new String(new char[indent]).replace('\0', ' ') + getClass().getSimpleName() +" [\n";
            ret += new String(new char[indent]).replace('\0', ' ') + tasks.stream().map(e -> {
                if (e == null)
                    return "null";
                else if (e instanceof Tasks) {
                    return ((Tasks) e).toString(indent + 2);
                } else {
                    return e.toString();
                }
            }).collect(Collectors.joining(", "));
            return ret + new String(new char[indent]).replace('\0', ' ') + "]\n";
        }
    }
}
