package com.sk89q.worldedit.extension.factory.parser.transform;

import com.boydti.fawe.object.extent.PatternTransform;
import com.boydti.fawe.object.extent.ResettableExtent;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.factory.parser.RichParser;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.pattern.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class PatternTransformParser extends RichParser<ResettableExtent> {

    /**
     * Create a new rich parser with a defined prefix for the result, e.g. {@code #simplex}.
     *
     * @param worldEdit the worldedit instance.
     */
    public PatternTransformParser(WorldEdit worldEdit) {
        super(worldEdit, "#pattern");
    }

    @Override
    protected Stream<String> getSuggestions(String argumentInput, int index) {
        if (index == 0) {
            return worldEdit.getPatternFactory().getSuggestions(argumentInput).stream();
        }
        return Stream.empty();
    }

    @Override
    protected ResettableExtent parseFromInput(@NotNull String[] arguments, ParserContext context) throws InputParseException {
        if (arguments.length != 1) {
            return null;
        }
        Pattern pattern = worldEdit.getPatternFactory().parseFromInput(arguments[0], context);
        return new PatternTransform(context.requireExtent(), pattern);
    }
}
