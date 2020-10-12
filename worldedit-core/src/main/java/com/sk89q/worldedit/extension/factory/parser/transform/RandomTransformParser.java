package com.sk89q.worldedit.extension.factory.parser.transform;

import com.boydti.fawe.object.extent.RandomTransform;
import com.boydti.fawe.object.extent.ResettableExtent;
import com.sk89q.util.StringUtil;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.internal.registry.InputParser;

import java.util.List;
import java.util.stream.Stream;

public class RandomTransformParser extends InputParser<ResettableExtent> {

    public RandomTransformParser(WorldEdit worldEdit) {
        super(worldEdit);
    }

    @Override
    public Stream<String> getSuggestions(String input) {
        if (input.isEmpty()) {
            return Stream.empty();
        }
        List<String> split = StringUtil.split(input, ',', '[', ']');
        if (split.size() == 1) {
            return Stream.empty();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < split.size() - 1; i++) {
            builder.append(split.get(i)).append(',');
        }
        String previous = builder.toString();
        return worldEdit.getTransformFactory().getSuggestions(split.get(split.size() - 1)).stream()
                .map(s -> previous + s);
    }

    @Override
    public ResettableExtent parseFromInput(String input, ParserContext context) throws InputParseException {
        if (input.isEmpty()) {
            return null;
        }
        List<String> split = StringUtil.split(input, ',', '[', ']');
        if (split.size() == 1) {
            return null;
        }
        ResettableExtent[] transforms = new ResettableExtent[split.size()];
        for (int i = 0; i < split.size(); i++) {
            transforms[i] = worldEdit.getTransformFactory().parseFromInput(split.get(i), context);
        }
        RandomTransform randomTransform = new RandomTransform();
        for (ResettableExtent transform : transforms) {
            randomTransform.add(transform, 1d);
        }
        return randomTransform;
    }
}
