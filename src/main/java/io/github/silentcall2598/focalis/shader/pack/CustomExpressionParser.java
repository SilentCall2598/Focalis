// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiles one custom uniform expression into typed {@link CustomExpression} nodes. Operators follow C precedence.
 * Names are resolved through the caller while parsing, so the result never looks anything up again. Only used while
 * a pack is configured.
 */
final class CustomExpressionParser {

    /** Turns a name into its node, or explains why it can't be used. */
    interface Names {

        CustomExpression resolve(String name) throws ParseException;
    }

    static final class ParseException extends Exception {

        private static final long serialVersionUID = 1L;

        ParseException(String message) {
            super(message);
        }
    }

    // Documented functions Focalis doesn't evaluate yet. Some have unclear documented semantics, like trig in
    // degrees or log in base 10, so they wait until a pack needs them.
    private static final Set<String> DEFERRED_FUNCTIONS = new HashSet<>(Arrays.asList("sin", "cos", "asin", "acos",
            "tan", "atan", "atan2", "torad", "todeg", "exp", "frac", "log", "pow", "random", "round", "signum", "fmod",
            "lerp", "print", "equals", "vec2", "vec3", "vec4"));

    private final List<String> tokens;
    private final Names names;
    private final int[] nextSite;
    private int position;

    private CustomExpressionParser(List<String> tokens, Names names, int[] nextSite) {
        this.tokens = tokens;
        this.names = names;
        this.nextSite = nextSite;
    }

    /**
     * @param nextSite the next free smooth() call site, advanced for every one this expression has
     */
    static CustomExpression parse(String text, Names names, int[] nextSite) throws ParseException {
        CustomExpressionParser parser = new CustomExpressionParser(tokenize(text), names, nextSite);
        if (parser.tokens.isEmpty()) {
            throw new ParseException("the expression is empty");
        }
        CustomExpression expression = parser.or();
        if (parser.position < parser.tokens.size()) {
            throw new ParseException("unexpected '" + parser.tokens.get(parser.position) + "'");
        }
        return expression;
    }

    private CustomExpression or() throws ParseException {
        CustomExpression left = and();
        while (accept("||")) {
            left = new CustomExpression.Logic(false, bool(left, "||"), bool(and(), "||"));
        }
        return left;
    }

    private CustomExpression and() throws ParseException {
        CustomExpression left = equality();
        while (accept("&&")) {
            left = new CustomExpression.Logic(true, bool(left, "&&"), bool(equality(), "&&"));
        }
        return left;
    }

    private CustomExpression equality() throws ParseException {
        CustomExpression left = relation();
        while (true) {
            String operator = accept("==") ? "==" : accept("!=") ? "!=" : null;
            if (operator == null) {
                return left;
            }
            CustomExpression right = relation();
            if (left.type != right.type) {
                throw new ParseException("'" + operator + "' compares a " + name(left.type) + " with a "
                        + name(right.type));
            }
            left = new CustomExpression.Compare(operator, left, right);
        }
    }

    private CustomExpression relation() throws ParseException {
        CustomExpression left = sum();
        while (true) {
            String operator = accept("<=") ? "<=" : accept(">=") ? ">=" : accept("<") ? "<" : accept(">") ? ">"
                    : null;
            if (operator == null) {
                return left;
            }
            left = new CustomExpression.Compare(operator, number(left, operator), number(sum(), operator));
        }
    }

    private CustomExpression sum() throws ParseException {
        CustomExpression left = product();
        while (true) {
            char operator = accept("+") ? '+' : accept("-") ? '-' : 0;
            if (operator == 0) {
                return left;
            }
            left = new CustomExpression.Arithmetic(operator, number(left, String.valueOf(operator)),
                    number(product(), String.valueOf(operator)));
        }
    }

    private CustomExpression product() throws ParseException {
        CustomExpression left = unary();
        while (true) {
            char operator = accept("*") ? '*' : accept("/") ? '/' : accept("%") ? '%' : 0;
            if (operator == 0) {
                return left;
            }
            left = new CustomExpression.Arithmetic(operator, number(left, String.valueOf(operator)),
                    number(unary(), String.valueOf(operator)));
        }
    }

    private CustomExpression unary() throws ParseException {
        if (accept("-")) {
            return new CustomExpression.Negate(number(unary(), "-"));
        }
        if (accept("+")) {
            return number(unary(), "+");
        }
        if (accept("!")) {
            return new CustomExpression.Not(bool(unary(), "!"));
        }
        return primary();
    }

    private CustomExpression primary() throws ParseException {
        if (position >= tokens.size()) {
            throw new ParseException("the expression ends too early");
        }
        String token = tokens.get(position++);
        if (token.equals("(")) {
            CustomExpression inner = or();
            expect(")");
            return inner;
        }
        if (Character.isDigit(token.charAt(0)) || token.charAt(0) == '.') {
            return new CustomExpression.Number(number(token));
        }
        if (isNameStart(token.charAt(0))) {
            if (accept("(")) {
                return function(token);
            }
            return names.resolve(token);
        }
        throw new ParseException("unexpected '" + token + "'");
    }

    private CustomExpression function(String function) throws ParseException {
        int idStart = position;
        List<CustomExpression> arguments = new ArrayList<>();
        if (!accept(")")) {
            do {
                arguments.add(or());
            } while (accept(","));
            expect(")");
        }
        CustomExpression[] args = arguments.toArray(new CustomExpression[0]);
        int count = args.length;
        switch (function) {
            case "abs":
            case "sqrt":
            case "floor":
            case "ceil":
                arity(function, count, count == 1, "1 argument");
                return new CustomExpression.Unary(function, number(args[0], function));
            case "min":
            case "max":
                arity(function, count, count >= 2, "2 or more arguments");
                return new CustomExpression.Extreme(function.equals("max"), numbers(args, 0, function));
            case "clamp":
                arity(function, count, count == 3, "3 arguments");
                numbers(args, 0, function);
                return new CustomExpression.Clamp(args[0], args[1], args[2]);
            case "if":
            case "ifb":
                arity(function, count, count >= 3 && count % 2 == 1, "an odd number of arguments, 3 or more");
                return choose(function, args);
            case "in":
                arity(function, count, count >= 2, "2 or more arguments");
                return new CustomExpression.OneOf(numbers(args, 0, function));
            case "between":
                arity(function, count, count == 3, "3 arguments");
                numbers(args, 0, function);
                return new CustomExpression.Between(args[0], args[1], args[2]);
            case "smooth":
                return smooth(args, idStart);
            default:
                if (DEFERRED_FUNCTIONS.contains(function)) {
                    throw new ParseException("the function " + function + " isn't supported yet");
                }
                throw new ParseException("there is no function " + function);
        }
    }

    private CustomExpression choose(String function, CustomExpression[] args) throws ParseException {
        CustomExpression.Type type = function.equals("if") ? CustomExpression.Type.FLOAT : CustomExpression.Type.BOOL;
        for (int i = 0; i < args.length; i++) {
            boolean condition = i + 1 < args.length && i % 2 == 0;
            CustomExpression.Type expected = condition ? CustomExpression.Type.BOOL : type;
            if (args[i].type != expected) {
                throw new ParseException(function + " needs a " + name(expected) + " as argument " + (i + 1));
            }
        }
        return new CustomExpression.Choose(type, args);
    }

    // smooth(value) and smooth(id, value, fadeIn, fadeOut). The two and three argument forms can't be told apart, and
    // the id only has to be a number since every call site keeps its own state.
    private CustomExpression smooth(CustomExpression[] args, int idStart) throws ParseException {
        CustomExpression one = new CustomExpression.Number(1);
        if (args.length == 1) {
            return new CustomExpression.Smooth(nextSite[0]++, number(args[0], "smooth"), one, one);
        }
        if (args.length != 4) {
            throw new ParseException("smooth needs 1 or 4 arguments here, the 2 and 3 argument forms are ambiguous");
        }
        String id = tokens.get(idStart);
        if (!(Character.isDigit(id.charAt(0)) && tokens.get(idStart + 1).equals(","))) {
            throw new ParseException("the id of smooth has to be a number");
        }
        numbers(args, 1, "smooth");
        return new CustomExpression.Smooth(nextSite[0]++, args[1], args[2], args[3]);
    }

    private static void arity(String function, int count, boolean ok, String expected) throws ParseException {
        if (!ok) {
            throw new ParseException(function + " needs " + expected + ", not " + count);
        }
    }

    private static CustomExpression[] numbers(CustomExpression[] args, int from, String function)
            throws ParseException {
        for (int i = from; i < args.length; i++) {
            if (args[i].type != CustomExpression.Type.FLOAT) {
                throw new ParseException(function + " needs a float as argument " + (i + 1));
            }
        }
        return args;
    }

    private static CustomExpression number(CustomExpression expression, String operator) throws ParseException {
        if (expression.type != CustomExpression.Type.FLOAT) {
            throw new ParseException("'" + operator + "' needs a float, not a bool");
        }
        return expression;
    }

    private static CustomExpression bool(CustomExpression expression, String operator) throws ParseException {
        if (expression.type != CustomExpression.Type.BOOL) {
            throw new ParseException("'" + operator + "' needs a bool, not a float");
        }
        return expression;
    }

    static String name(CustomExpression.Type type) {
        return type == CustomExpression.Type.FLOAT ? "float" : "bool";
    }

    private boolean accept(String token) {
        if (position < tokens.size() && tokens.get(position).equals(token)) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(String token) throws ParseException {
        if (!accept(token)) {
            throw new ParseException("missing '" + token + "'");
        }
    }

    private static float number(String token) throws ParseException {
        try {
            return Float.parseFloat(token);
        } catch (NumberFormatException e) {
            throw new ParseException("'" + token + "' isn't a number");
        }
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    // Names keep their dotted components, like cameraPosition.x, so they resolve as one.
    private static List<String> tokenize(String text) throws ParseException {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int start = i;
            if (isNameStart(c)) {
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_'
                        || text.charAt(i) == '.')) {
                    i++;
                }
            } else if (Character.isDigit(c) || c == '.') {
                while (i < text.length() && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
            } else if (text.startsWith("||", i) || text.startsWith("&&", i) || text.startsWith("==", i)
                    || text.startsWith("!=", i) || text.startsWith("<=", i) || text.startsWith(">=", i)) {
                i += 2;
            } else if ("()!<>+-*/%,".indexOf(c) >= 0) {
                i++;
            } else {
                throw new ParseException("unexpected character '" + c + "'");
            }
            tokens.add(text.substring(start, i));
        }
        return tokens;
    }
}
