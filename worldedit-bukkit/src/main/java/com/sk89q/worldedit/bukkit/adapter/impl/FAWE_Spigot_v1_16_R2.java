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

package com.sk89q.worldedit.bukkit.adapter.impl;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.FaweCache;
import com.boydti.fawe.beta.IChunkGet;
import com.boydti.fawe.beta.IQueueChunk;
import com.boydti.fawe.beta.IQueueExtent;
import com.boydti.fawe.beta.implementation.packet.ChunkPacket;
import com.boydti.fawe.beta.implementation.queue.SingleThreadQueueExtent;
import com.boydti.fawe.bukkit.adapter.mc1_16_2.*;
import com.boydti.fawe.bukkit.adapter.mc1_16_2.nbt.LazyCompoundTag_1_16_2;
import com.boydti.fawe.util.MathMan;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.blocks.TileEntityBlock;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplAdapter;
import com.sk89q.worldedit.bukkit.adapter.CachedBukkitAdapter;
import com.sk89q.worldedit.bukkit.adapter.IDelegateBukkitImplAdapter;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.LazyBaseEntity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.io.file.SafeFiles;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.*;
import com.sk89q.worldedit.world.entity.EntityType;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.File;
import net.minecraft.server.v1_16_R2.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_16_R2.CraftChunk;
import org.bukkit.craftbukkit.v1_16_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R2.CraftServer;
import org.bukkit.craftbukkit.v1_16_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_16_R2.block.data.CraftBlockData;
import org.bukkit.craftbukkit.v1_16_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_16_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R2.generator.CustomChunkGenerator;
import org.bukkit.craftbukkit.v1_16_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.bukkit.generator.BlockPopulator;


public final class FAWE_Spigot_v1_16_R2 extends CachedBukkitAdapter implements IDelegateBukkitImplAdapter<NBTBase> {
    private final Spigot_v1_16_R2 parent;
    private char[] ibdToStateOrdinal;
    
    private final Field serverWorldsField;
    private final Method getChunkFutureMethod;
    private final Field chunkProviderExecutorField;
    private final Field worldPaperConfigField;

    // ------------------------------------------------------------------------
    // Code that may break between versions of Minecraft
    // ------------------------------------------------------------------------

    public FAWE_Spigot_v1_16_R2() throws NoSuchFieldException, NoSuchMethodException {
        this.parent = new Spigot_v1_16_R2();

        serverWorldsField = CraftServer.class.getDeclaredField("worlds");
        serverWorldsField.setAccessible(true);

        getChunkFutureMethod = ChunkProviderServer.class.getDeclaredMethod("getChunkFutureMainThread", int.class, int.class, ChunkStatus.class, boolean.class);
        getChunkFutureMethod.setAccessible(true);

        chunkProviderExecutorField = ChunkProviderServer.class.getDeclaredField("serverThreadQueue");
        chunkProviderExecutorField.setAccessible(true);
        
        Field tmpPaperConfigField = null;
        try { //only present on paper
            tmpPaperConfigField = World.class.getDeclaredField("paperConfig");
            tmpPaperConfigField.setAccessible(true);
        } catch (Exception e) {
        }
        worldPaperConfigField = tmpPaperConfigField;
    }

    @Override
    public BukkitImplAdapter<NBTBase> getParent() {
        return parent;
    }

    private synchronized boolean init() {
        if (ibdToStateOrdinal != null && ibdToStateOrdinal[1] != 0) return false;
        ibdToStateOrdinal = new char[Block.REGISTRY_ID.a()]; // size
        for (int i = 0; i < ibdToStateOrdinal.length; i++) {
            BlockState state = BlockTypesCache.states[i];
            BlockMaterial_1_16_2 material = (BlockMaterial_1_16_2) state.getMaterial();
            int id = Block.REGISTRY_ID.getId(material.getState());
            ibdToStateOrdinal[id] = state.getOrdinalChar();
        }
        return true;
    }

    @Override
    public BlockMaterial getMaterial(BlockType blockType) {
        Block block = getBlock(blockType);
        return new BlockMaterial_1_16_2(block);
    }

    @Override
    public BlockMaterial getMaterial(BlockState state) {
        IBlockData bs = ((CraftBlockData) Bukkit.createBlockData(state.getAsString())).getState();
        return new BlockMaterial_1_16_2(bs.getBlock(), bs);
    }

    public Block getBlock(BlockType blockType) {
        return IRegistry.BLOCK.get(new MinecraftKey(blockType.getNamespace(), blockType.getResource()));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BaseBlock getBlock(Location location) {
        Preconditions.checkNotNull(location);

        CraftWorld craftWorld = ((CraftWorld) location.getWorld());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        final WorldServer handle = craftWorld.getHandle();
        Chunk chunk = handle.getChunkAt(x >> 4, z >> 4);
        final BlockPosition blockPos = new BlockPosition(x, y, z);
        org.bukkit.block.Block bukkitBlock = location.getBlock();
        BlockState state = BukkitAdapter.adapt(bukkitBlock.getBlockData());
        if (state.getBlockType().getMaterial().hasContainer()) {

        // Read the NBT data
            TileEntity te = chunk.a(blockPos, Chunk.EnumTileEntityState.CHECK);
            if (te != null) {
                NBTTagCompound tag = new NBTTagCompound();
                te.save(tag); // readTileEntityIntoTag - load data
                return state.toBaseBlock((CompoundTag) toNative(tag));
            }
        }

        return state.toBaseBlock();
    }

    @Override
    public Set<SideEffect> getSupportedSideEffects() {
        return SideEffectSet.defaults().getSideEffectsToApply();
    }

    public boolean setBlock(org.bukkit.Chunk chunk, int x, int y, int z, BlockStateHolder state, boolean update) {
        CraftChunk craftChunk = (CraftChunk) chunk;
        Chunk nmsChunk = craftChunk.getHandle();
        World nmsWorld = nmsChunk.getWorld();

        BlockPosition blockPos = new BlockPosition(x, y, z);
        IBlockData blockData = ((BlockMaterial_1_16_2) state.getMaterial()).getState();
        ChunkSection[] sections = nmsChunk.getSections();
        int y4 = y >> 4;
        ChunkSection section = sections[y4];

        IBlockData existing;
        if (section == null) {
            existing = ((BlockMaterial_1_16_2) BlockTypes.AIR.getDefaultState().getMaterial()).getState();
        } else {
            existing = section.getType(x & 15, y & 15, z & 15);
        }


        nmsChunk.removeTileEntity(blockPos); // Force delete the old tile entity

        CompoundTag nativeTag = state instanceof BaseBlock ? ((BaseBlock)state).getNbtData() : null;
        if (nativeTag != null || existing instanceof TileEntityBlock) {
            nmsWorld.setTypeAndData(blockPos, blockData, 0);
            // remove tile
            if (nativeTag != null) {
                // We will assume that the tile entity was created for us,
                // though we do not do this on the Forge version
                TileEntity tileEntity = nmsWorld.getTileEntity(blockPos);
                if (tileEntity != null) {
                    NBTTagCompound tag = (NBTTagCompound) fromNative(nativeTag);
                    tag.set("x", NBTTagInt.a(x));
                    tag.set("y", NBTTagInt.a(y));
                    tag.set("z", NBTTagInt.a(z));
                    tileEntity.load(tileEntity.getBlock(), tag); // readTagIntoTileEntity - load data
                }
            }
        } else {
            if (existing == blockData) return true;
            if (section == null) {
                if (blockData.isAir()) return true;
                sections[y4] = section = new ChunkSection(y4 << 4);
            }
            nmsChunk.setType(blockPos, blockData, false);
        }
        if (update) {
            nmsWorld.getMinecraftWorld().notify(blockPos, existing, blockData, 0);
        }
        return true;
    }

    @Override
    public WorldNativeAccess<?, ?, ?> createWorldNativeAccess(org.bukkit.World world) {
        return new FAWEWorldNativeAccess_1_16(this,
                new WeakReference<>(((CraftWorld)world).getHandle()));
    }

    @Nullable
    private static String getEntityId(Entity entity) {
        MinecraftKey minecraftkey = EntityTypes.getName(entity.getEntityType());
        return minecraftkey == null ? null : minecraftkey.toString();
    }

    private static void readEntityIntoTag(Entity entity, NBTTagCompound tag) {
        entity.save(tag);
    }

    @Override
    public BaseEntity getEntity(org.bukkit.entity.Entity entity) {
        Preconditions.checkNotNull(entity);

        CraftEntity craftEntity = ((CraftEntity) entity);
        Entity mcEntity = craftEntity.getHandle();

        String id = getEntityId(mcEntity);

        if (id != null) {
            EntityType type = com.sk89q.worldedit.world.entity.EntityTypes.get(id);
            Supplier<CompoundTag> saveTag = () -> {
                NBTTagCompound tag = new NBTTagCompound();
                readEntityIntoTag(mcEntity, tag);

                //add Id for AbstractChangeSet to work
                CompoundTag natve = (CompoundTag) toNative(tag);
                natve.getValue().put("Id", new StringTag(id));
                return natve;
            };
            return new LazyBaseEntity(type, saveTag);
        } else {
            return null;
        }
    }

    @Override
    public OptionalInt getInternalBlockStateId(BlockState state) {
        BlockMaterial_1_16_2 material = (BlockMaterial_1_16_2) state.getMaterial();
        IBlockData mcState = material.getCraftBlockData().getState();
        return OptionalInt.of(Block.REGISTRY_ID.getId(mcState));
    }

    @Override
    public BlockState adapt(BlockData blockData) {
        CraftBlockData cbd = ((CraftBlockData) blockData);
        IBlockData ibd = cbd.getState();
        return adapt(ibd);
    }

    public BlockState adapt(IBlockData ibd) {
        return BlockTypesCache.states[adaptToChar(ibd)];
    }

    /**
     * @deprecated
     * Method unused. Use #adaptToChar(IBlockData).
     */
    @Deprecated
    public int adaptToInt(IBlockData ibd) {
        synchronized (this) {
            try {
                int id = Block.REGISTRY_ID.getId(ibd);
                return ibdToStateOrdinal[id];
            } catch (NullPointerException e) {
                init();
                return adaptToInt(ibd);
            }
        }
    }

    public char adaptToChar(IBlockData ibd) {
        synchronized (this) {
            try {
                int id = Block.REGISTRY_ID.getId(ibd);
                return ibdToStateOrdinal[id];
            } catch (NullPointerException e) {
                init();
                return adaptToChar(ibd);
            } catch(ArrayIndexOutOfBoundsException e1){
                Fawe.debug("Attempted to convert " + ibd.getBlock() + " with ID " + Block.REGISTRY_ID.getId(ibd) + " to char. ibdToStateOrdinal length: " + ibdToStateOrdinal.length + ". Defaulting to air!");
                return 0;
            }
        }
    }

    @Override
    public <B extends BlockStateHolder<B>> BlockData adapt(B state) {
        BlockMaterial_1_16_2 material = (BlockMaterial_1_16_2) state.getMaterial();
        return material.getCraftBlockData();
    }

    private MapChunkUtil_1_16_2 mapUtil = new MapChunkUtil_1_16_2();

    @Override
    public void sendFakeChunk(org.bukkit.World world, Player player, ChunkPacket packet) {
        WorldServer nmsWorld = ((CraftWorld) world).getHandle();
        PlayerChunk map = BukkitAdapter_1_16_2.getPlayerChunk(nmsWorld, packet.getChunkX(), packet.getChunkZ());
        if (map != null && map.hasBeenLoaded()) {
            boolean flag = false;
            PlayerChunk.d players = map.players;
            Stream<EntityPlayer> stream = players.a(new ChunkCoordIntPair(packet.getChunkX(), packet.getChunkZ()), flag);

            EntityPlayer checkPlayer = player == null ? null : ((CraftPlayer) player).getHandle();
            stream.filter(entityPlayer -> checkPlayer == null || entityPlayer == checkPlayer)
                    .forEach(entityPlayer -> {
                        synchronized (packet) {
                            PacketPlayOutMapChunk nmsPacket = (PacketPlayOutMapChunk) packet.getNativePacket();
                            if (nmsPacket == null) {
                                nmsPacket = mapUtil.create( this, packet);
                                packet.setNativePacket(nmsPacket);
                            }
                            try {
                                FaweCache.IMP.CHUNK_FLAG.get().set(true);
                                entityPlayer.playerConnection.sendPacket(nmsPacket);
                            } finally {
                                FaweCache.IMP.CHUNK_FLAG.get().set(false);
                            }
                        }
                    });
        }
    }

    @Override
    public Map<String, ? extends Property<?>> getProperties(BlockType blockType) {
        return getParent().getProperties(blockType);
    }

    @Override
    public org.bukkit.inventory.ItemStack adapt(BaseItemStack item) {
        ItemStack stack = new ItemStack(IRegistry.ITEM.get(MinecraftKey.a(item.getType().getId())), item.getAmount());
        stack.setTag(((NBTTagCompound) fromNative(item.getNbtData())));
        return CraftItemStack.asCraftMirror(stack);
    }

    @Override
    public BaseItemStack adapt(org.bukkit.inventory.ItemStack itemStack) {
        final ItemStack nmsStack = CraftItemStack.asNMSCopy(itemStack);
        final BaseItemStack weStack = new BaseItemStack(BukkitAdapter.asItemType(itemStack.getType()), itemStack.getAmount());
        weStack.setNbtData(((CompoundTag) toNative(nmsStack.getTag())));
        return weStack;
    }

    @Override
    public Tag toNative(NBTBase foreign) {
        return parent.toNative(foreign);
    }

    @Override
    public NBTBase fromNative(Tag foreign) {
        if (foreign instanceof LazyCompoundTag_1_16_2) {
            return ((LazyCompoundTag_1_16_2) foreign).get();
        }
        return parent.fromNative(foreign);
    }

    private ResourceKey<WorldDimension> getWorldDimKey(org.bukkit.World.Environment env) {
        switch(env) {
            case NETHER:
                return WorldDimension.THE_NETHER;
            case THE_END:
                return WorldDimension.THE_END;
            case NORMAL:
            default:
                return WorldDimension.OVERWORLD;
        }
    }

    private Dynamic<NBTBase> recursivelySetSeed(Dynamic<NBTBase> dynamic, long seed, Set<Dynamic<NBTBase>> seen) {
        return !seen.add(dynamic) ? dynamic : dynamic.updateMapValues((pair) -> {
            if (((Dynamic)pair.getFirst()).asString("").equals("seed")) {
                return pair.mapSecond((v) -> {
                    return v.createLong(seed);
                });
            } else {
                return ((Dynamic)pair.getSecond()).getValue() instanceof NBTTagCompound ? pair.mapSecond((v) -> {
                    return this.recursivelySetSeed((Dynamic)v, seed, seen);
                }) : pair;
            }
        });
    }

    private static class RegenNoOpWorldLoadListener implements WorldLoadListener {
        private RegenNoOpWorldLoadListener() {
        }

        public void a(ChunkCoordIntPair chunkCoordIntPair) {
        }

        public void a(ChunkCoordIntPair chunkCoordIntPair, @Nullable ChunkStatus chunkStatus) {
        }

        public void b() {
        }
    }
    
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

    @Override
    public boolean regenerate(org.bukkit.World bukkitWorld, Region region, Extent realExtent, RegenOptions options) throws Exception {
        WorldServer originalWorld = ((CraftWorld) bukkitWorld).getHandle();
        ChunkProviderServer provider = originalWorld.getChunkProvider();
        if (!(provider instanceof ChunkProviderServer)) {
            return false;
        }
        
        // TODO make it work for CustomChunkGenerator
        Field generatorSettingBaseSupplierField = provider.getChunkGenerator().getClass().getDeclaredField("h");
        generatorSettingBaseSupplierField.setAccessible(true);

        Field structureManagerField = WorldServer.class.getDeclaredField("structureManager");
        structureManagerField.setAccessible(true);

        Field chunkProviderField = WorldServer.class.getDeclaredField("chunkProvider");
        chunkProviderField.setAccessible(true);
        
        //flat bedrock? (only on paper)
        boolean tmpgenerateFlatBedrock = false;
        try {
            tmpgenerateFlatBedrock = (boolean) worldPaperConfigField.get(originalWorld);
        } catch (Exception ignored) {
        }
        boolean generateFlatBedrock = tmpgenerateFlatBedrock;

        //world folder
        File saveFolder = Files.createTempDir();
        // register this just in case something goes wrong
        // normally it should be deleted at the end of this method
        saveFolder.deleteOnExit();
        
        //prepare for world init (see upstream implementation for reference)
        org.bukkit.World.Environment env = bukkitWorld.getEnvironment();
        org.bukkit.generator.ChunkGenerator gen = bukkitWorld.getGenerator();
        Path tempDir = java.nio.file.Files.createTempDirectory("WorldEditWorldGen");
        Convertable convertable = Convertable.a(tempDir);
        ResourceKey<WorldDimension> worldDimKey = getWorldDimKey(env);
        try (Convertable.ConversionSession session = convertable.c("worldeditregentempworld", worldDimKey)) {
            WorldDataServer originalWorldData = originalWorld.worldDataServer;

            long seed = options.getSeed().orElse(originalWorld.getSeed());

            WorldDataServer levelProperties = (WorldDataServer)originalWorld.getServer().getServer().getSaveData();
            RegistryReadOps<NBTBase> nbtRegOps = RegistryReadOps.a(DynamicOpsNBT.a, originalWorld.getServer().getServer().dataPackResources.h(), IRegistryCustom.b());
            GeneratorSettings newOpts = GeneratorSettings.a.encodeStart(nbtRegOps, levelProperties.getGeneratorSettings()).flatMap(tag -> GeneratorSettings.a.parse(recursivelySetSeed(new Dynamic<>(nbtRegOps, tag), seed, new HashSet<>()))).result().orElseThrow(() -> new IllegalStateException("Unable to map GeneratorOptions"));
            WorldSettings newWorldSettings = new WorldSettings("worldeditregentempworld", originalWorldData.b.getGameType(), originalWorldData.b.hardcore, originalWorldData.b.getDifficulty(), originalWorldData.b.e(), originalWorldData.b.getGameRules(), originalWorldData.b.g());
            WorldDataServer newWorldData = new WorldDataServer(newWorldSettings, newOpts, Lifecycle.stable());
            
            //init world
            Long2ObjectLinkedOpenHashMap<ProtoChunk> protoChunks = new Long2ObjectLinkedOpenHashMap<>(); //need to be an ordered list for RegionLimitedWorldAccess
            WorldServer freshWorld = Fawe.get().getQueueHandler().sync((Supplier<WorldServer>) () -> new WorldServer(originalWorld.getMinecraftServer(), originalWorld.getMinecraftServer().executorService, session, newWorldData, originalWorld.getDimensionKey(), originalWorld.getDimensionManager(), new RegenNoOpWorldLoadListener(), ((WorldDimension)newOpts.d().a(worldDimKey)).c(), originalWorld.isDebugWorld(), seed, ImmutableList.of(), false, env, gen) {
                @Override
                public IChunkAccess getChunkAt(int i, int j, ChunkStatus chunkstatus, boolean flag) {
                    return protoChunks.get(MathMan.pairInt(i, j));
                }
            }).get();

            ChunkProviderServer chunkProvider = new ChunkProviderServer(freshWorld, session, freshWorld.getMinecraftServer().getDataFixer(), freshWorld.getMinecraftServer().getDefinedStructureManager(), freshWorld.getMinecraftServer().executorService, provider.chunkGenerator, freshWorld.spigotConfig.viewDistance, freshWorld.getMinecraftServer().isSyncChunkWrites(), new RegenNoOpWorldLoadListener(), () -> freshWorld.getMinecraftServer().E().getWorldPersistentData()) {
                // needed as it otherwise waits endlessly somehow
                @Override
                public IChunkAccess getChunkAt(int i, int j, ChunkStatus chunkstatus, boolean flag) {
                    return protoChunks.get(MathMan.pairInt(i, j));
                }
            };
            chunkProviderField.set(freshWorld, chunkProvider);

            //generator
            Supplier<GeneratorSettingBase> generatorSettingBaseSupplier = (Supplier<GeneratorSettingBase>) generatorSettingBaseSupplierField.get(freshWorld.getChunkProvider().getChunkGenerator());
            ChunkGenerator tempgenerator = new ChunkGeneratorAbstract(provider.getChunkGenerator().getWorldChunkManager(), seed, generatorSettingBaseSupplier);
            if (originalWorld.generator != null) {
                // wrap custom world generator
                tempgenerator = new CustomChunkGenerator(freshWorld, tempgenerator, originalWorld.generator);
            }

            //lets start then
            DefinedStructureManager structManager = freshWorld.getMinecraftServer().getDefinedStructureManager();
            LightEngineThreaded lightEngine = chunkProvider.getLightEngine();
            ChunkGenerator generator = tempgenerator;
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            
            try {
                long start = System.currentTimeMillis();

                //list of chunk stati in correct order
                List<ChunkStatus> chunkStati = Arrays.asList(
                        ChunkStatus.EMPTY,
                        ChunkStatus.STRUCTURE_STARTS,
                        ChunkStatus.STRUCTURE_REFERENCES,
                        ChunkStatus.BIOMES,
                        ChunkStatus.NOISE,
                        ChunkStatus.SURFACE,
                        ChunkStatus.CARVERS,
                        ChunkStatus.LIQUID_CARVERS,
                        ChunkStatus.FEATURES,
                        ChunkStatus.LIGHT,
                        ChunkStatus.SPAWN,
                        ChunkStatus.HEIGHTMAPS
                );
                
                //TODO: can we get that required radius down without affecting chunk generation (e.g. strucures, features, ...)?
                //TODO: maybe do some chunk stages in parallel, e.g. those not requiring neighbour chunks?
                //      for those ChunkStati that need neighbox chunks a special queue could help (e.g. for FEATURES and NOISE)
                
                //generate chunk coords lists with a certain radius
                Int2ObjectOpenHashMap<List<Long>> chunkCoordsForRadius = new Int2ObjectOpenHashMap();
                System.out.println("precomputing chunkCoordsForRadius lists");
                chunkStati.stream().map(ChunkStatus::f).distinct().forEach(radius -> {
                    if (radius == -1) //ignore ChunkStatus.EMPTY
                        return;
                    int border = 16 - radius; //9 = 8 + 1, 8: max border radius used in chunk stages, 1: need 1 extra chunk for chunk features to generate at the border of the region
                    chunkCoordsForRadius.put(radius, getChunkCoordsRegen(region, border));
                });
                
                //create chunks
                System.out.println("ctor");
                for (Long xz : chunkCoordsForRadius.get(0)) {
                    ProtoChunk chunk = new ProtoChunk(new ChunkCoordIntPair(MathMan.unpairIntX(xz), MathMan.unpairIntY(xz)), ChunkConverter.a) {
                        public boolean generateFlatBedrock() {
                            return generateFlatBedrock;
                        }
                    };
                    protoChunks.put(xz, chunk);
                }
                
                //generate lists for RegionLimitedWorldAccess, need to be square with odd length (e.g. 17x17), 17 = 1 middle chunk + 8 border chunks * 2
                Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<List<IChunkAccess>>> worldlimits = new Int2ObjectOpenHashMap();
                System.out.println("precomputing RegionLimitedWorldAccess chunks lists");
                chunkStati.stream().map(ChunkStatus::f).distinct().forEach(radius -> {
                    if (radius == -1) //ignore ChunkStatus.EMPTY
                        return;
                    Long2ObjectOpenHashMap<List<IChunkAccess>> map = new Long2ObjectOpenHashMap();
                    for (Long xz : chunkCoordsForRadius.get(radius)) {
                        int x = MathMan.unpairIntX(xz);
                        int z = MathMan.unpairIntY(xz);
                        List<IChunkAccess> l = new ArrayList((radius + 1 + radius) * (radius + 1 + radius));
                        for (int zz = z - radius; zz <= z + radius; zz++) { //order is important , first z then x
                            for (int xx = x - radius; xx <= x + radius; xx++) {
                                l.add(protoChunks.get(MathMan.pairInt(xx, zz)));
                            }
                        }
                        map.put(xz, l);
                    }
                    worldlimits.put(radius, map);
                });
                
                //new
                for (ChunkStatus chunkstatus : chunkStati) {
                    System.out.println(chunkstatus.d());
                    int radius = Math.max(0, chunkstatus.f()); //f() = required border chunks, EMPTY.f() == -1
                    
                    List<Long> coords = chunkCoordsForRadius.get(radius);
                    List<List<Long>> rows = getChunkStatusTaskRows(coords, chunkstatus);
                    List tasks = new ArrayList(rows.size());
                    for (List<Long> row : rows) {
                        tasks.add((Callable) () -> {
                            for (Long xz : row) {
                                try {
                                    chunkstatus.a(freshWorld,
                                                  generator,
                                                  structManager,
                                                  lightEngine,
                                                  c -> CompletableFuture.completedFuture(Either.left(c)),
                                                  worldlimits.get(radius).get(xz));
                                } catch (Exception e) {
                                    System.err.println("error while running " + chunkstatus.d() + " on chunk " + MathMan.unpairIntX(xz) + "/" + MathMan.unpairIntY(xz));
                                    e.printStackTrace();
                                }
                            }
                            return null;
                        });
                    }
                    
                    List<Future> fut = executor.invokeAll(tasks);
                    for (Future f : fut) {
                        try {
                            f.get();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                
                //run generation tasks exluding FULL chunk status
//                chunkStati.forEach(chunkstatus -> {
//                    System.out.println(chunkstatus.d());
//                    int radius = Math.max(0, chunkstatus.f()); //f() is required border chunks, EMPTY: f() == -1
//                    List<Future> fut = new LinkedList<>();
//                    for (Long xz : chunkCoordsForRadius.get(radius)) {
////                        ProtoChunk chunk = protoChunks.get(xz);
////                        System.out.println(MathMan.unpairIntX(xz) + "/" + MathMan.unpairIntY(xz) + ": " + chunk.getPos().x + "/" + chunk.getPos().z);
//                        if (chunkstatus.f() == 0) {
//                            Future f = executor.submit(() -> chunkstatus.a(freshWorld,
//                                                                           generator,
//                                                                           structManager,
//                                                                           lightEngine,
//                                                                           c -> CompletableFuture.completedFuture(Either.left(c)),
//                                                                           worldlimits.get(radius).get(xz)));
//                            fut.add(f);
//                        } else {
//                            
//                            chunkstatus.a(freshWorld,
//                                          generator,
//                                          structManager,
//                                          lightEngine,
//                                          c -> CompletableFuture.completedFuture(Either.left(c)),
//                                          worldlimits.get(radius).get(xz));
//                        }
//                        for (Future f : fut) {
//                            try {
//                                f.get();
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    }
//                });

                //convert to proper chunks
                Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap();
                for (Long xz : chunkCoordsForRadius.get(0)) {
                    ProtoChunk chunk = protoChunks.get(xz);
                    chunks.put(xz, new Chunk(freshWorld, chunk));
                }
                
                //final chunkstatus
                System.out.println("full");
                for (Long xz : chunkCoordsForRadius.get(0)) {
                    Chunk chunk = chunks.get(xz);
                    ChunkStatus.FULL.a(freshWorld, generator, structManager, lightEngine,
                                          c -> CompletableFuture.completedFuture(Either.left(c)),
                                          Arrays.asList(chunk)); //chunkstatus.f() == 0!
                }
                
                //populate
                List<BlockPopulator> defaultPopulators = originalWorld.getWorld().getPopulators();
                System.out.println("populate with " + defaultPopulators.size() + " populators");
                for (Long xz : chunkCoordsForRadius.get(0)) {
                    int x = MathMan.unpairIntX(xz);
                    int z = MathMan.unpairIntY(xz);
                    
                    //prepare chunk seed
                    Random random = new Random();
                    random.setSeed(seed);
                    long xRand = random.nextLong() / 2L * 2L + 1L;
                    long zRand = random.nextLong() / 2L * 2L + 1L;
                    random.setSeed((long) x * xRand + (long) z * zRand ^ seed);
                    
                    //actually populate
                    Chunk c = chunks.get(xz);
                    defaultPopulators.forEach(pop -> {
                        pop.populate(freshWorld.getWorld(), random, c.bukkitChunk);
                    });
                }

                System.out.println("Finished chunk generation in " + (System.currentTimeMillis() - start) + " ms");
                IQueueExtent<IQueueChunk> extent = new SingleThreadQueueExtent();
                extent.init(null, (chunkX, chunkZ) -> new BukkitGetBlocks_1_16_2(freshWorld, chunkX, chunkZ) {
                    @Override
                    public Chunk ensureLoaded(World nmsWorld, int X, int Z) {
                        return chunks.get(MathMan.pairInt(X, Z));
                    }
                }, null);
                
                System.out.println("Set blocks");
                boolean genbiomes = options.shouldRegenBiomes();
                for (BlockVector3 vec : region) {
                    realExtent.setBlock(vec, extent.getBlock(vec));
                    if (genbiomes) {
                        realExtent.setBiome(vec, extent.getBiome(vec));
                    }
//                    realExtent.setSkyLight(vec, extent.getSkyLight(vec));
//                    realExtent.setBlockLight(vec, extent.getBrightness(vec));
                }
                System.out.println("Finished setting blocks");
            } finally {
                executor.shutdownNow();
                Fawe.get().getQueueHandler().sync(() -> {
                    try {
                        freshWorld.getChunkProvider().close(false);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        } finally {
            try {
                Fawe.get().getQueueHandler().sync(() -> {
                    try {
                        Map<String, org.bukkit.World> map = (Map<String, org.bukkit.World>) serverWorldsField.get(Bukkit.getServer());
                        map.remove("worldeditregentempworld");
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
            } finally {
                SafeFiles.tryHardToDeleteDir(tempDir);
                saveFolder.delete();
            }
        }
        return true;
    }
    
    /**
     * Creates a list of chunkcoord rows that may be executed concurrently
     * @param allcoords the coords that should be sorted into rows
     * @param chunkstatus the chunkstatus bein executed on the returned rows
     * @return a list of chunkcoords rows that may be executed concurrently
     */
    private List<List<Long>> getChunkStatusTaskRows(List<Long> allcoords, ChunkStatus chunkstatus) {
        int requiredneighbors = Math.max(0, chunkstatus.f());
        
        int minx = allcoords.isEmpty() ? 0 : MathMan.unpairIntX(allcoords.get(0));
        int maxx = allcoords.isEmpty() ? 0 : MathMan.unpairIntX(allcoords.get(allcoords.size() - 1));
        int numlists = Math.min(requiredneighbors * 2 + 1, maxx - minx + 1);
        List<List<Long>> ret = new ArrayList(numlists);
        
        for (int i = 0; i < numlists; i++) {
            List<Long> current = new ArrayList((allcoords.size() + 1) / numlists);
            for (Long xz : allcoords) {
                if ((MathMan.unpairIntX(xz) - minx) % numlists == i)
                    current.add(xz);
            }
            ret.add(current);
        }
        
//        if (ret.stream().mapToInt(e -> e.size()).sum() != allcoords.size())
//            System.out.println("size mismatch: ex=" + allcoords.size() + "; ac: " + ret.stream().mapToInt(e -> e.size()).sum());
//        else if (!ret.stream().flatMap(List::stream).collect(Collectors.toSet()).equals(new HashSet(allcoords))) {
//            System.out.println("cord mismatch:");
//            List<Long> ac = ret.stream().flatMap(List::stream).collect(Collectors.toSet()).stream().sorted().collect(Collectors.toList());
//            System.out.println("ex: " + allcoords);
//            System.out.println("ac: " + ac);
//        } else {
//            List<List<Integer>> xs = ret.stream().map(e -> e.stream().map(x -> MathMan.unpairIntX(x)).distinct().sorted().collect(Collectors.toList())).collect(Collectors.toList());
//            System.out.println("xs: " + xs);
//            List<Integer> oxs = allcoords.stream().map(x -> MathMan.unpairIntX(x)).distinct().sorted().collect(Collectors.toList());
//            System.out.println("oxs: " + oxs);
//        }
        
        return ret;
    }
    
    @Override
    public IChunkGet get(org.bukkit.World world, int chunkX, int chunkZ) {
        return new BukkitGetBlocks_1_16_2(world, chunkX, chunkZ);
    }

    @Override
    public int getInternalBiomeId(BiomeType biome) {
        BiomeBase base = CraftBlock.biomeToBiomeBase(MinecraftServer.getServer().aX().b(IRegistry.ay), BukkitAdapter.adapt(biome));
        return MinecraftServer.getServer().aX().b(IRegistry.ay).a(base);
    }
}
