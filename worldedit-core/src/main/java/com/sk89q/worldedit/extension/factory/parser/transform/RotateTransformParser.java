package com.sk89q.worldedit.extension.factory.parser.transform;

import com.boydti.fawe.object.extent.ResettableExtent;
import com.boydti.fawe.object.extent.TransformExtent;
import com.boydti.fawe.util.ExtentTraverser;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.SuggestionHelper;
import com.sk89q.worldedit.extension.factory.parser.RichParser;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.transform.AffineTransform;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class RotateTransformParser extends RichParser<ResettableExtent> {

    /**
     * Create a new rich parser with a defined prefix for the result, e.g. {@code #simplex}.
     *
     * @param worldEdit the worldedit instance.
     */
    public RotateTransformParser(WorldEdit worldEdit) {
        super(worldEdit, "#rotate");
    }

    @Override
    protected Stream<String> getSuggestions(String argumentInput, int index) {
        if (index < 3) {
            return SuggestionHelper.suggestPositiveDoubles(argumentInput);
        }
        if (index == 3) {
            return worldEdit.getTransformFactory().getSuggestions(argumentInput).stream();
        }
        return Stream.empty();
    }

    @Override
    protected ResettableExtent parseFromInput(@NotNull String[] arguments, ParserContext context) throws InputParseException {
        if (arguments.length > 4) {
            return null;
        }
        AffineTransform transform = new AffineTransform();
        if (arguments.length == 4) {
            ResettableExtent extent = worldEdit.getTransformFactory().parseFromInput(arguments[3], context);
            // search if there's already a transformation
            ExtentTraverser<TransformExtent> traverser = new ExtentTraverser<>(extent).find(TransformExtent.class);
            BlockTransformExtent affine = traverser != null ? traverser.get() : null;
            if (affine != null) {
                // found one, so we want to combine that with the new one later
                transform = (AffineTransform) affine.getTransform();
            }
        }
        if (arguments.length == 1) {
            transform = transform.rotateY(Double.parseDouble(arguments[0]));
        } else if (arguments.length >= 3) {
            transform = transform.rotateX(Double.parseDouble(arguments[0]));
            transform = transform.rotateY(Double.parseDouble(arguments[1]));
            transform = transform.rotateZ(Double.parseDouble(arguments[2]));
        }
        return new BlockTransformExtent(context.requireExtent(), transform);
    }
}
