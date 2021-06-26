package com.boydti.fawe.object.brush.sweep;

import com.boydti.fawe.config.Caption;
import com.boydti.fawe.object.brush.ResettableTool;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.interpolation.Interpolation;
import com.sk89q.worldedit.math.interpolation.KochanekBartelsInterpolation;
import com.sk89q.worldedit.math.interpolation.Node;
import com.sk89q.worldedit.math.interpolation.ReparametrisingInterpolation;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SweepBrush implements Brush, ResettableTool {
    private List<BlockVector3> positions;
    private BlockVector3 position;
    private int copies;

    private static final double tension = 0D;
    private static final double bias = 0D;
    private static final double continuity = 0D;

    public SweepBrush(int copies) {
        this.positions = new ArrayList<>();
        this.copies = copies > 0 ? copies : -1;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) throws MaxChangedBlocksException {

        boolean newPos = !position.equals(this.position);
        this.position = position;
        Actor actor = editSession.getActor();
        if (actor == null) {
            //TODO Insert Error Message here or modify EditSession to not require a player.
            return;
        }
        if (newPos) {
            actor.print(Caption.of("fawe.worldedit.brush.spline.primary.2"));
            positions.add(position);
            return;
        }

        if (positions.size() < 2) {
            actor.print(Caption.of("fawe.worldedit.brush.brush.spline.secondary.error"));
            return;
        }

        Interpolation interpol = new ReparametrisingInterpolation(new KochanekBartelsInterpolation());
        List<Node> nodes = positions.stream().map(v -> {
            Node n = new Node(v.toVector3());
            n.setTension(tension);
            n.setBias(bias);
            n.setContinuity(continuity);
            return n;
        }).collect(Collectors.toList());
        interpol.setNodes(nodes);

        LocalSession session = actor.getSession();
        ClipboardHolder holder = session.getExistingClipboard();
        if (holder == null) {
            throw new RuntimeException(new EmptyClipboardException());
        }
        Clipboard clipboard = holder.getClipboard();

        BlockVector3 dimensions = clipboard.getDimensions();
        double quality = Math.max(dimensions.getBlockX(), dimensions.getBlockZ());

        AffineTransform transform = new AffineTransform();

        ClipboardSpline spline = new ClipboardSpline(editSession, holder, interpol, transform, nodes.size());

        if (dimensions.getBlockX() > dimensions.getBlockZ()) {
            spline.setDirection(Vector2.at(0, 1));
        }

        switch (copies) {
            case 1: {
                spline.pastePosition(0D);
                break;
            }
            case -1: {
                double length = interpol.arcLength(0, 1);
                double step = 1 / (length * quality);
                for (double pos = 0; pos <= 1; pos += step) {
                    spline.pastePosition(pos);
                }
                break;
            }
            default: {
                for (double pos = 0D; pos <= 1D; pos += 1D / (copies -  1)) {
                    spline.pastePosition(pos);
                }
                break;
            }
        }
        actor.print(Caption.of("fawe.worldedit.brush.spline.secondary"));
        reset();
    }

    @Override
    public boolean reset() {
        positions.clear();
        position = null;
        return true;
    }
}
