package com.sk89q.worldedit.extension.factory.parser.transform;

import com.boydti.fawe.object.extent.Linear3DTransform;
import com.boydti.fawe.object.extent.LinearTransform;
import com.boydti.fawe.object.extent.RandomTransform;
import com.boydti.fawe.object.extent.ResettableExtent;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.parser.RichParser;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class Linear3DTransformParser extends RichParser<ResettableExtent> {

    /**
     * Create a new rich parser with a defined prefix for the result, e.g. {@code #simplex}.
     *
     * @param worldEdit the worldedit instance.
     */
    public Linear3DTransformParser(WorldEdit worldEdit) {
        super(worldEdit, "#linear3d", "#l3d");
    }

    @Override
    protected Stream<String> getSuggestions(String argumentInput, int index) {
        if (index == 0) {
            return worldEdit.getTransformFactory().getSuggestions(argumentInput).stream();
        }
        return Stream.empty();
    }

    @Override
    protected ResettableExtent parseFromInput(@NotNull String[] arguments, ParserContext context) throws InputParseException {
        if (arguments.length != 1) {
            return null;
        }
        ResettableExtent inner = worldEdit.getTransformFactory().parseFromInput(arguments[0], context);
        if (inner instanceof RandomTransform) {
            return new Linear3DTransform(((RandomTransform) inner).getExtents().toArray(new ResettableExtent[0]));
        }
        return inner; // TODO what about non-random transforms?
    }
}
