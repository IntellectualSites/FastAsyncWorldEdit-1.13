package com.boydti.fawe.object.brush;

import com.boydti.fawe.config.Caption;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.command.tool.brush.Brush;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;

public class LineBrush implements Brush, ResettableTool {

    private final boolean shell;
    private final boolean select;
    private final boolean flat;
    private BlockVector3 pos1;

    public LineBrush(boolean shell, boolean select, boolean flat) {
        this.shell = shell;
        this.select = select;
        this.flat = flat;
    }

    @Override
    public void build(EditSession editSession, BlockVector3 position, Pattern pattern, double size) throws MaxChangedBlocksException {
        if (pos1 == null) {
            pos1 = position;
            editSession.getActor().print(Caption.of("fawe.worldedit.brush.brush.line.primary", position));
            return;
        }
        editSession.drawLine(pattern, pos1, position, size, !shell, flat);
        editSession.getActor().print(Caption.of("fawe.worldedit.brush.brush.line.secondary"));
        if (!select) {
            pos1 = null;
        } else {
            pos1 = position;
        }
    }

    @Override
    public boolean reset() {
        pos1 = null;
        return true;
    }
}
