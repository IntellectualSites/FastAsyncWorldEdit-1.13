package com.boydti.fawe.object.extent;

import com.sk89q.worldedit.WorldEditException;

import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.MutableBlockVector2;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;

import java.util.SplittableRandom;

public class RandomOffsetTransform extends OffsetExtent {
    private transient SplittableRandom random;
    private transient MutableBlockVector2 mutable = new MutableBlockVector2();

    public RandomOffsetTransform(Extent parent, int dx, int dy, int dz) {
        super(parent, dx + 1, dy + 1, dz + 1);
        this.random = new SplittableRandom();
    }

    @Override
    public boolean setBiome(BlockVector2 pos, BiomeType biome) {
        int x = pos.getBlockX() + random.nextInt(1 + (dx << 1)) - dx;
        int z = pos.getBlockZ() + random.nextInt(1 + (dz << 1)) - dz;
        return getExtent().setBiome(mutable.setComponents(x, z), biome);
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 pos, T block) throws WorldEditException {
        int x = pos.getBlockX() + random.nextInt(1 + (dx << 1)) - dx;
        int y = pos.getBlockY() + random.nextInt(1 + (dy << 1)) - dy;
        int z = pos.getBlockZ() + random.nextInt(1 + (dz << 1)) - dz;
        return getExtent().setBlock(x, y, z, block);
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(int x, int y, int z, T block) throws WorldEditException {
        x = x + random.nextInt(1 + (dx << 1)) - dx;
        y = y + random.nextInt(1 + (dy << 1)) - dy;
        z = z + random.nextInt(1 + (dz << 1)) - dz;
        return getExtent().setBlock(x, y, z, block);
    }

    @Override
    public ResettableExtent setExtent(Extent extent) {
        random = new SplittableRandom();
        mutable = new MutableBlockVector2();
        return super.setExtent(extent);
    }
}
