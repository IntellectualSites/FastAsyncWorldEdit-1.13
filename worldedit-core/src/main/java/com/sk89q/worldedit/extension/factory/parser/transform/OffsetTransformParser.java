package com.sk89q.worldedit.extension.factory.parser.transform;

import com.boydti.fawe.object.extent.OffsetExtent;
import com.boydti.fawe.object.extent.ResettableExtent;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.SuggestionHelper;
import com.sk89q.worldedit.extension.factory.parser.RichParser;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class OffsetTransformParser extends RichParser<ResettableExtent> {

    /**
     * Create a new rich parser with a defined prefix for the result, e.g. {@code #simplex}.
     *
     * @param worldEdit the worldedit instance.
     */
    public OffsetTransformParser(WorldEdit worldEdit) {
        super(worldEdit, "#offset");
    }

    @Override
    protected Stream<String> getSuggestions(String argumentInput, int index) {
        if (index < 3) {
            return SuggestionHelper.suggestPositiveIntegers(argumentInput);
        }
        return Stream.empty();
    }

    @Override
    protected ResettableExtent parseFromInput(@NotNull String[] arguments, ParserContext context) throws InputParseException {
        if (arguments.length != 3) {
            throw new InputParseException(TranslatableComponent.of("fawe.error.command.syntax",
                    TextComponent.of("#offset[x][y][z]")));
        }
        int xOffset = Integer.parseInt(arguments[0]);
        int yOffset = Integer.parseInt(arguments[1]);
        int zOffset = Integer.parseInt(arguments[2]);
        return new OffsetExtent(context.requireExtent(), xOffset, yOffset, zOffset);
    }
}
