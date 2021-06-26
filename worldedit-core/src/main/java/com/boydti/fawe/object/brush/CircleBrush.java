package com.boydti.fawe.object.brush;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;

public class CircleBrush implements Brush {

    private final boolean filled;

    public CircleBrush(boolean filled) {

        this.filled = filled;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) throws MaxChangedBlocksException {
        Actor actor = editSession.getActor();
        if (actor == null) {
            return;
        }
        if (actor instanceof Player) {
            Player player = (Player) actor;
            Vector3 normal = position.toVector3().subtract(player.getLocation());
            editSession.makeCircle(position, pattern, size, size, size, filled, normal);
        }
    }

    private Vector3 any90Rotate(Vector3 normal) {
        normal = normal.normalize();
        if (normal.getX() == 1 || normal.getY() == 1 || normal.getZ() == 1) {
            return Vector3.at(normal.getZ(), normal.getX(), normal.getY());
        }
        AffineTransform affine = new AffineTransform();
        affine = affine.rotateX(90);
        affine = affine.rotateY(90);
        affine = affine.rotateZ(90);
        Vector3 random = affine.apply(normal);
        return random.cross(normal).normalize();
    }
}
