package com.sk89q.worldedit.extension.factory.parser;

import com.google.common.base.Preconditions;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.internal.registry.InputParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A rich parser allows parsing of patterns and masks with extra arguments,
 * e.g. #simplex[scale][pattern].
 *
 * @param <E> the parse result.
 */
public abstract class RichParser<E> extends InputParser<E> {
    private final String[] prefixes;

    /**
     * Create a new rich parser with a defined prefix for the result, e.g. {@code #simplex}.
     *
     * @param worldEdit the worldedit instance.
     * @param aliases    the prefix of this parser result.
     */
    protected RichParser(WorldEdit worldEdit, String... aliases) {
        super(worldEdit);
        Preconditions.checkArgument(aliases.length >= 1, "Aliases may not be empty");
        this.prefixes = aliases;
    }

    @NotNull
    private static Predicate<String> validPrefix(String other) {
        return prefix -> {
            if (prefix.length() > other.length()) {
                return prefix.startsWith(other);
            }
            return other.startsWith(prefix);
        };
    }

    @NotNull
    private Function<String, Stream<? extends String>> extractArguments(String input) {
        return prefix -> {
            if (input.length() > prefix.length()) {
                // input already contains argument(s) -> extract them
                String[] strings = extractArguments(input.substring(prefix.length()), false);
                // rebuild the argument string without the last argument
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < strings.length - 1; i++) {
                    builder.append('[').append(strings[i]).append(']');
                }
                String previous = prefix + builder;
                // read the suggestions for the last argument
                return getSuggestions(strings[strings.length - 1], strings.length - 1)
                        .map(suggestion -> previous + "[" + suggestion);
            } else {
                return Stream.of(prefix);
            }
        };
    }

    public String getPrefix() {
        return this.prefixes[0];
    }

    @Override
    public Stream<String> getSuggestions(String input) {
        return Arrays.stream(this.prefixes)
                .filter(validPrefix(input))
                .flatMap(extractArguments(input));
    }

    @Override
    public E parseFromInput(String input, ParserContext context) throws InputParseException {
        for (String prefix : this.prefixes) {
            if (!input.startsWith(prefix)) {
                continue;
            }
            if (input.length() < prefix.length()) {
                continue;
            }
            String[] arguments = extractArguments(input.substring(prefix.length()), true);
            return parseFromInput(arguments, context);
        }
        return null;
    }

    /**
     * Returns a stream of suggestions for the argument at the given index.
     *
     * @param argumentInput the already provided input for the argument at the given index.
     * @param index         the index of the argument to get suggestions for.
     * @return a stream of suggestions matching the given input for the argument at the given index.
     */
    protected abstract Stream<String> getSuggestions(String argumentInput, int index);

    /**
     * Parses the already split arguments.
     *
     * @param arguments the array of arguments that were split (can be empty).
     * @param context   the context of this parsing process.
     * @return the resulting parsed type.
     * @throws InputParseException if the input couldn't be parsed correctly.
     */
    protected abstract E parseFromInput(@NotNull String[] arguments, ParserContext context) throws InputParseException;

    /**
     * Extracts arguments enclosed by {@code []} into an array.
     * Example: {@code [Hello][World]} results in a list containing {@code Hello} and {@code World}.
     *
     * @param input          the input to extract arguments from.
     * @param requireClosing whether or not the extraction requires valid bracketing.
     * @return an array of extracted arguments.
     * @throws InputParseException if {@code requireClosing == true} and the count of [ != the count of ]
     */
    protected String[] extractArguments(String input, boolean requireClosing) throws InputParseException {
        int open = 0; // the "level"
        int openIndex = 0;
        int i = 0;
        List<String> arguments = new ArrayList<>();
        for (; i < input.length(); i++) {
            if (input.charAt(i) == '[') {
                if (open++ == 0) {
                    openIndex = i;
                }
            }
            if (input.charAt(i) == ']') {
                if (--open == 0) {
                    arguments.add(input.substring(openIndex + 1, i));
                }
            }
        }
        if (!requireClosing)
            if (open > 0) {
                arguments.add(input.substring(openIndex + 1));
            } else {
                int last = input.lastIndexOf(']');
                if (last != -1) {
                    arguments.add(input.substring(last));
                }
            }
        if (requireClosing && open != 0) {
            throw new InputParseException("Invalid bracketing, are you missing a '[' or ']'?");
        }
        return arguments.toArray(new String[0]);
    }
}
