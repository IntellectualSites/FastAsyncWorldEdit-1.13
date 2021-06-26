package com.boydti.fawe.wrappers;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.util.EditSessionBuilder;
import com.boydti.fawe.util.TaskManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.PlayerProxy;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.util.TargetBlock;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;

public class AsyncPlayer extends PlayerProxy {

    public AsyncPlayer(Player parent) {
        super(parent);
    }

    public static AsyncPlayer wrap(Player parent) {
        if (parent instanceof AsyncPlayer) {
            return (AsyncPlayer) parent;
        }
        return new AsyncPlayer(parent);
    }

    @Override
    public World getWorld() {
        return WorldWrapper.wrap(super.getWorld());
    }

    @Override
    public void findFreePosition(Location searchPos) {
        TaskManager.IMP.sync(new RunnableVal<Boolean>() {
            @Override
            public void run(Boolean value) {
                getBasePlayer().findFreePosition(searchPos);
            }
        });
    }

    @Override
    public void setOnGround(Location searchPos) {
        TaskManager.IMP.sync(new RunnableVal<Boolean>() {
            @Override
            public void run(Boolean value) {
                getBasePlayer().setOnGround(searchPos);
            }
        });
    }

    @Override
    public void findFreePosition() {
        TaskManager.IMP.sync(new RunnableVal<Boolean>() {
            @Override
            public void run(Boolean value) {
                getBasePlayer().findFreePosition();
            }
        });
    }

    @Override
    public boolean ascendLevel() {
        return TaskManager.IMP.sync(() -> getBasePlayer().ascendLevel());
    }

    @Override
    public boolean descendLevel() {
        return TaskManager.IMP.sync(() -> getBasePlayer().descendLevel());
    }

    @Override
    public boolean ascendToCeiling(int clearance) {
        return ascendToCeiling(clearance, true);
    }

    @Override
    public boolean ascendToCeiling(int clearance, boolean alwaysGlass) {
        Location pos = getBlockLocation();
        int x = pos.getBlockX();
        int initialY = Math.max(0, pos.getBlockY());
        int y = Math.max(0, pos.getBlockY() + 2);
        int z = pos.getBlockZ();
        Extent world = getLocation().getExtent();

        // No free space above
        if (!world.getBlock(BlockVector3.at(x, y, z)).getBlockType().getMaterial().isAir()) {
            return false;
        }

        while (y <= world.getMaximumPoint().getY()) {
            // Found a ceiling!
            if (world.getBlock(BlockVector3.at(x, y, z)).getBlockType().getMaterial()
                .isMovementBlocker()) {
                int platformY = Math.max(initialY, y - 3 - clearance);
                floatAt(x, platformY + 1, z, alwaysGlass);
                return true;
            }

            ++y;
        }

        return false;
    }

    @Override
    public boolean ascendUpwards(int distance) {
        return ascendUpwards(distance, true);
    }

    @Override
    public boolean ascendUpwards(int distance, boolean alwaysGlass) {
        final Location pos = getBlockLocation();
        final int x = pos.getBlockX();
        final int initialY = Math.max(0, pos.getBlockY());
        int y = Math.max(0, pos.getBlockY() + 1);
        final int z = pos.getBlockZ();
        final int maxY = Math.min(getWorld().getMaxY() + 1, initialY + distance);
        final Extent world = getLocation().getExtent();

        while (y <= world.getMaximumPoint().getY() + 2) {
            if (world.getBlock(BlockVector3.at(x, y, z)).getBlockType().getMaterial()
                .isMovementBlocker()) {
                break; // Hit something
            } else if (y > maxY + 1) {
                break;
            } else if (y == maxY + 1) {
                floatAt(x, y - 1, z, alwaysGlass);
                return true;
            }

            ++y;
        }

        return false;
    }

    @Override
    public void floatAt(int x, int y, int z, boolean alwaysGlass) {
        if (alwaysGlass || !isAllowedToFly()) {
            RuntimeException caught = null;
            try {
                EditSession edit =
                    new EditSessionBuilder(WorldWrapper.unwrap(getWorld())).actor(unwrap(getBasePlayer())).build();
                edit.setBlock(BlockVector3.at(x, y - 1, z), BlockTypes.GLASS);
                edit.flushQueue();
                LocalSession session = Fawe.get().getWorldEdit().getSessionManager().get(this);
                if (session != null) {
                    session.remember(edit, true, getBasePlayer().getLimit().MAX_HISTORY);
                }
            } catch (RuntimeException e) {
                caught = e;
            }
            if (caught != null) {
                throw caught;
            }
        } else {
            setFlying(true);
        }
        trySetPosition(Vector3.at(x + 0.5, y, z + 0.5));
    }

    @Override
    public void setPosition(Vector3 pos, float pitch, float yaw) {
        Fawe.get().getQueueHandler().sync(() -> super.setPosition(pos, pitch, yaw));
    }

    @Override
    public Location getBlockTrace(int range, boolean useLastBlock) {
        return TaskManager.IMP.sync(() -> {
            TargetBlock tb = new TargetBlock(AsyncPlayer.this, range, 0.2D);
            return useLastBlock ? tb.getAnyTargetBlock() : tb.getTargetBlock();
        });
    }

    @Override
    public Location getBlockTraceFace(int range, boolean useLastBlock) {
        return TaskManager.IMP.sync(() -> {
            TargetBlock tb = new TargetBlock(AsyncPlayer.this, range, 0.2D);
            return useLastBlock ? tb.getAnyTargetBlockFace() : tb.getTargetBlockFace();
        });
    }

    @Override
    public Location getSolidBlockTrace(int range) {
        return TaskManager.IMP.sync(() -> {
            TargetBlock tb = new TargetBlock(AsyncPlayer.this, range, 0.2D);
            return tb.getSolidTargetBlock();
        });
    }

    @Override
    public Direction getCardinalDirection() {
        return getBasePlayer().getCardinalDirection();
    }

    @Override
    public boolean passThroughForwardWall(int range) {
        return TaskManager.IMP.sync(() -> {
            int searchDist = 0;
            TargetBlock hitBlox = new TargetBlock(AsyncPlayer.this, range, 0.2);
            Extent world = getLocation().getExtent();
            Location block;
            boolean firstBlock = true;
            int freeToFind = 2;
            boolean inFree = false;

            while ((block = hitBlox.getNextBlock()) != null) {
                boolean free = !world.getBlock(
                    BlockVector3.at(block.getBlockX(), block.getBlockY(), block.getBlockZ()))
                    .getBlockType().getMaterial().isMovementBlocker();

                if (firstBlock) {
                    firstBlock = false;

                    if (!free) {
                        --freeToFind;
                        continue;
                    }
                }

                ++searchDist;
                if (searchDist > 20) {
                    return false;
                }

                if (inFree != free) {
                    if (free) {
                        --freeToFind;
                    }
                }

                if (freeToFind == 0) {
                    setOnGround(block);
                    return true;
                }

                inFree = free;
            }

            return false;
        });
    }
}
