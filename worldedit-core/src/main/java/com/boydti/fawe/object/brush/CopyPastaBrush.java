package com.boydti.fawe.object.brush;

import com.boydti.fawe.config.Caption;
import com.boydti.fawe.object.clipboard.ResizableClipboardBuilder;
import com.boydti.fawe.object.function.NullRegionFunction;
import com.boydti.fawe.object.function.mask.AbstractDelegateMask;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.function.visitor.RecursiveVisitor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.concurrent.ThreadLocalRandom;

public class CopyPastaBrush implements Brush, ResettableTool {

    private final LocalSession session;
    private final Player player;
    public boolean autoRotate;
    public boolean randomRotate;

    public CopyPastaBrush(Player player, LocalSession session, boolean randomRotate, boolean autoRotate) {
        session.setClipboard(null);
        this.player = player;
        this.session = session;
        this.randomRotate = randomRotate;
        this.autoRotate = autoRotate;
    }

    @Override
    public boolean reset() {
        session.setClipboard(null);
        player.print(Caption.of("fawe.worldedit.brush.brush.reset"));
        return true;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) throws MaxChangedBlocksException {
        Actor actor = editSession.getActor();
        if (actor == null) {
            return;
        }
        if (!(actor instanceof Player)) {
            return; //todo throw error
        }
        Player player = (Player) actor;
        ClipboardHolder clipboard = session.getExistingClipboard();
        if (clipboard == null) {
            Mask mask = editSession.getMask();
            if (mask == null) {
                mask = Masks.alwaysTrue();
            }
            final ResizableClipboardBuilder builder = new ResizableClipboardBuilder(editSession.getWorld());
            final int minY = position.getBlockY();
            mask = new AbstractDelegateMask(mask) {
                @Override
                public boolean test(BlockVector3 vector) {
                    if (super.test(vector) && vector.getBlockY() >= minY) {
                        BaseBlock block = editSession.getFullBlock(vector);
                        if (!block.getBlockType().getMaterial().isAir()) {
                            builder.add(vector, BlockTypes.AIR.getDefaultState().toBaseBlock(), block);
                            return true;
                        }
                    }
                    return false;
                }
            };
            // Add origin
            mask.test(position);
            RecursiveVisitor visitor = new RecursiveVisitor(mask, new NullRegionFunction(), (int) size);
            visitor.visit(position);
            Operations.completeBlindly(visitor);
            // Build the clipboard
            Clipboard newClipboard = builder.build();
            newClipboard.setOrigin(position);
            ClipboardHolder holder = new ClipboardHolder(newClipboard);
            session.setClipboard(holder);
            int blocks = builder.size();
            player.print(Caption.of("fawe.worldedit.copy.command.copy", blocks));
        } else {
            AffineTransform transform = null;
            if (randomRotate) {
                transform = new AffineTransform();
                int rotate = 90 * ThreadLocalRandom.current().nextInt(4);
                transform = transform.rotateY(rotate);
            }
            if (autoRotate) {
                if (transform == null) {
                    transform = new AffineTransform();
                }
                Location loc = player.getLocation();
                float yaw = loc.getYaw();
                float pitch = loc.getPitch();
                transform = transform.rotateY(-yaw % 360);
                transform = transform.rotateX(pitch - 90);
            }
            if (transform != null && !transform.isIdentity()) {
                clipboard.setTransform(transform);
            }

            Operation operation = clipboard
                    .createPaste(editSession)
                    .to(position.add(0, 1, 0))
                    .ignoreAirBlocks(true)
                    .build();
            Operations.completeLegacy(operation);
            editSession.flushQueue();
        }
    }
}
