// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

import java.util.ArrayList;
import java.util.List;

/**
 * The integer expressions of {@code #if} lines in {@code shaders.properties} and the boolean conditions of
 * {@code program.<name>.enabled}. Numbers, names, {@code defined NAME}, parentheses, {@code ! -}, {@code * / %},
 * {@code + -}, comparisons, {@code && ||}, with the usual C precedence. Only used while a pack is configured, never
 * while drawing.
 */
final class PackExpression {

    /** What a name means. Throws for a name the expression may not use. */
    interface Names {

        double value(String name) throws ExpressionException;

        boolean defined(String name) throws ExpressionException;
    }

    static final class ExpressionException extends Exception {

        private static final long serialVersionUID = 1L;

        ExpressionException(String message) {
            super(message);
        }
    }

    private final List<String> tokens;
    private final Names names;
    private int position;

    private PackExpression(List<String> tokens, Names names) {
        this.tokens = tokens;
        this.names = names;
    }

    static double evaluate(String text, Names names) throws ExpressionException {
        PackExpression expression = new PackExpression(tokenize(text), names);
        if (expression.tokens.isEmpty()) {
            throw new ExpressionException("empty expression");
        }
        double value = expression.or();
        if (expression.position < expression.tokens.size()) {
            throw new ExpressionException("unexpected '" + expression.tokens.get(expression.position) + "'");
        }
        return value;
    }

    static boolean isTrue(double value) {
        return value != 0;
    }

    private double or() throws ExpressionException {
        double value = and();
        while (accept("||")) {
            double right = and();
            value = isTrue(value) || isTrue(right) ? 1 : 0;
        }
        return value;
    }

    private double and() throws ExpressionException {
        double value = equality();
        while (accept("&&")) {
            double right = equality();
            value = isTrue(value) && isTrue(right) ? 1 : 0;
        }
        return value;
    }

    private double equality() throws ExpressionException {
        double value = relation();
        while (true) {
            if (accept("==")) {
                value = value == relation() ? 1 : 0;
            } else if (accept("!=")) {
                value = value != relation() ? 1 : 0;
            } else {
                return value;
            }
        }
    }

    private double relation() throws ExpressionException {
        double value = sum();
        while (true) {
            if (accept("<=")) {
                value = value <= sum() ? 1 : 0;
            } else if (accept(">=")) {
                value = value >= sum() ? 1 : 0;
            } else if (accept("<")) {
                value = value < sum() ? 1 : 0;
            } else if (accept(">")) {
                value = value > sum() ? 1 : 0;
            } else {
                return value;
            }
        }
    }

    private double sum() throws ExpressionException {
        double value = product();
        while (true) {
            if (accept("+")) {
                value += product();
            } else if (accept("-")) {
                value -= product();
            } else {
                return value;
            }
        }
    }

    private double product() throws ExpressionException {
        double value = unary();
        while (true) {
            if (accept("*")) {
                value *= unary();
            } else if (accept("/")) {
                double divisor = unary();
                if (divisor == 0) {
                    throw new ExpressionException("division by zero");
                }
                value /= divisor;
            } else if (accept("%")) {
                double divisor = unary();
                if (divisor == 0) {
                    throw new ExpressionException("division by zero");
                }
                value %= divisor;
            } else {
                return value;
            }
        }
    }

    private double unary() throws ExpressionException {
        if (accept("!")) {
            return isTrue(unary()) ? 0 : 1;
        }
        if (accept("-")) {
            return -unary();
        }
        if (accept("+")) {
            return unary();
        }
        return primary();
    }

    private double primary() throws ExpressionException {
        if (position >= tokens.size()) {
            throw new ExpressionException("expression ends too early");
        }
        String token = tokens.get(position++);
        if (token.equals("(")) {
            double value = or();
            expect(")");
            return value;
        }
        if (token.equals("defined")) {
            boolean parenthesized = accept("(");
            String name = name();
            if (parenthesized) {
                expect(")");
            }
            return names.defined(name) ? 1 : 0;
        }
        if (isNameStart(token.charAt(0))) {
            return names.value(token);
        }
        if (Character.isDigit(token.charAt(0)) || token.charAt(0) == '.') {
            return number(token);
        }
        throw new ExpressionException("unexpected '" + token + "'");
    }

    private String name() throws ExpressionException {
        if (position >= tokens.size() || !isNameStart(tokens.get(position).charAt(0))) {
            throw new ExpressionException("defined needs a name");
        }
        return tokens.get(position++);
    }

    private boolean accept(String token) {
        if (position < tokens.size() && tokens.get(position).equals(token)) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(String token) throws ExpressionException {
        if (!accept(token)) {
            throw new ExpressionException("missing '" + token + "'");
        }
    }

    /** Parses a number the way packs write them, with an optional {@code f} after a float. */
    static double number(String token) throws ExpressionException {
        String digits = token.endsWith("f") || token.endsWith("F") ? token.substring(0, token.length() - 1) : token;
        try {
            return Double.parseDouble(digits);
        } catch (NumberFormatException e) {
            throw new ExpressionException("'" + token + "' isn't a number");
        }
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static List<String> tokenize(String text) throws ExpressionException {
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
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
            } else if (Character.isDigit(c) || c == '.') {
                while (i < text.length() && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
            } else if (text.startsWith("||", i) || text.startsWith("&&", i) || text.startsWith("==", i)
                    || text.startsWith("!=", i) || text.startsWith("<=", i) || text.startsWith(">=", i)) {
                i += 2;
            } else if ("()!<>+-*/%".indexOf(c) >= 0) {
                i++;
            } else {
                throw new ExpressionException("unexpected character '" + c + "'");
            }
            tokens.add(text.substring(start, i));
        }
        return tokens;
    }
}
