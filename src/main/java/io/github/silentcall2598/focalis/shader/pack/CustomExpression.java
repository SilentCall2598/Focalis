// SPDX-FileCopyrightText: 2026 Focalis contributors
// SPDX-License-Identifier: LGPL-3.0-only
package io.github.silentcall2598.focalis.shader.pack;

/**
 * One compiled custom uniform expression node. Every node knows its type when it's built, so evaluation never checks
 * types or looks up names, and nothing in here allocates. Values of other declarations are read from the array the
 * evaluation fills in dependency order. A bool declaration is stored there as 1 or 0.
 */
abstract class CustomExpression {

    enum Type {
        FLOAT,
        BOOL
    }

    final Type type;

    CustomExpression(Type type) {
        this.type = type;
    }

    float number(CustomContext context, float[] values) {
        throw new IllegalStateException("Not a float expression");
    }

    boolean test(CustomContext context, float[] values) {
        throw new IllegalStateException("Not a bool expression");
    }

    static final class Number extends CustomExpression {

        private final float value;

        Number(float value) {
            super(Type.FLOAT);
            this.value = value;
        }

        @Override
        float number(CustomContext context, float[] values) {
            return value;
        }
    }

    static final class Truth extends CustomExpression {

        private final boolean value;

        Truth(boolean value) {
            super(Type.BOOL);
            this.value = value;
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            return value;
        }
    }

    static final class Input extends CustomExpression {

        private final int input;

        Input(CustomInput input) {
            super(Type.FLOAT);
            this.input = input.ordinal();
        }

        @Override
        float number(CustomContext context, float[] values) {
            return context.input(input);
        }
    }

    static final class Reference extends CustomExpression {

        private final int declaration;

        Reference(int declaration, Type type) {
            super(type);
            this.declaration = declaration;
        }

        @Override
        float number(CustomContext context, float[] values) {
            return values[declaration];
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            return values[declaration] != 0;
        }
    }

    static final class Negate extends CustomExpression {

        private final CustomExpression operand;

        Negate(CustomExpression operand) {
            super(Type.FLOAT);
            this.operand = operand;
        }

        @Override
        float number(CustomContext context, float[] values) {
            return -operand.number(context, values);
        }
    }

    static final class Not extends CustomExpression {

        private final CustomExpression operand;

        Not(CustomExpression operand) {
            super(Type.BOOL);
            this.operand = operand;
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            return !operand.test(context, values);
        }
    }

    static final class Arithmetic extends CustomExpression {

        private final char operator;
        private final CustomExpression left;
        private final CustomExpression right;

        Arithmetic(char operator, CustomExpression left, CustomExpression right) {
            super(Type.FLOAT);
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        float number(CustomContext context, float[] values) {
            float a = left.number(context, values);
            float b = right.number(context, values);
            switch (operator) {
                case '+':
                    return a + b;
                case '-':
                    return a - b;
                case '*':
                    return a * b;
                case '/':
                    return a / b;
                default:
                    return a % b;
            }
        }
    }

    static final class Compare extends CustomExpression {

        private final String operator;
        private final CustomExpression left;
        private final CustomExpression right;

        Compare(String operator, CustomExpression left, CustomExpression right) {
            super(Type.BOOL);
            this.operator = operator;
            this.left = left;
            this.right = right;
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            if (left.type == Type.BOOL) {
                boolean same = left.test(context, values) == right.test(context, values);
                return operator.equals("==") == same;
            }
            float a = left.number(context, values);
            float b = right.number(context, values);
            switch (operator) {
                case "<":
                    return a < b;
                case "<=":
                    return a <= b;
                case ">":
                    return a > b;
                case ">=":
                    return a >= b;
                case "==":
                    return a == b;
                default:
                    return a != b;
            }
        }
    }

    static final class Logic extends CustomExpression {

        private final boolean and;
        private final CustomExpression left;
        private final CustomExpression right;

        Logic(boolean and, CustomExpression left, CustomExpression right) {
            super(Type.BOOL);
            this.and = and;
            this.left = left;
            this.right = right;
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            return and ? left.test(context, values) && right.test(context, values)
                    : left.test(context, values) || right.test(context, values);
        }
    }

    /** abs, sqrt, floor and ceil. */
    static final class Unary extends CustomExpression {

        private final String function;
        private final CustomExpression argument;

        Unary(String function, CustomExpression argument) {
            super(Type.FLOAT);
            this.function = function;
            this.argument = argument;
        }

        @Override
        float number(CustomContext context, float[] values) {
            float x = argument.number(context, values);
            switch (function) {
                case "abs":
                    return Math.abs(x);
                case "sqrt":
                    return (float) Math.sqrt(x);
                case "floor":
                    return (float) Math.floor(x);
                default:
                    return (float) Math.ceil(x);
            }
        }
    }

    /** min and max over two or more values. */
    static final class Extreme extends CustomExpression {

        private final boolean max;
        private final CustomExpression[] arguments;

        Extreme(boolean max, CustomExpression[] arguments) {
            super(Type.FLOAT);
            this.max = max;
            this.arguments = arguments;
        }

        @Override
        float number(CustomContext context, float[] values) {
            float result = arguments[0].number(context, values);
            for (int i = 1; i < arguments.length; i++) {
                float x = arguments[i].number(context, values);
                result = max ? Math.max(result, x) : Math.min(result, x);
            }
            return result;
        }
    }

    static final class Clamp extends CustomExpression {

        private final CustomExpression value;
        private final CustomExpression min;
        private final CustomExpression max;

        Clamp(CustomExpression value, CustomExpression min, CustomExpression max) {
            super(Type.FLOAT);
            this.value = value;
            this.min = min;
            this.max = max;
        }

        @Override
        float number(CustomContext context, float[] values) {
            return Math.max(min.number(context, values), Math.min(max.number(context, values),
                    value.number(context, values)));
        }
    }

    /** if and ifb. The first condition that holds picks its value, otherwise the last argument is the result. */
    static final class Choose extends CustomExpression {

        private final CustomExpression[] arguments;

        Choose(Type type, CustomExpression[] arguments) {
            super(type);
            this.arguments = arguments;
        }

        private CustomExpression pick(CustomContext context, float[] values) {
            for (int i = 0; i + 1 < arguments.length; i += 2) {
                if (arguments[i].test(context, values)) {
                    return arguments[i + 1];
                }
            }
            return arguments[arguments.length - 1];
        }

        @Override
        float number(CustomContext context, float[] values) {
            return pick(context, values).number(context, values);
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            return pick(context, values).test(context, values);
        }
    }

    /** in(x, a, b, ...) is true when x equals one of the others. */
    static final class OneOf extends CustomExpression {

        private final CustomExpression[] arguments;

        OneOf(CustomExpression[] arguments) {
            super(Type.BOOL);
            this.arguments = arguments;
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            float x = arguments[0].number(context, values);
            for (int i = 1; i < arguments.length; i++) {
                if (arguments[i].number(context, values) == x) {
                    return true;
                }
            }
            return false;
        }
    }

    /** between(x, min, max) includes both ends. */
    static final class Between extends CustomExpression {

        private final CustomExpression value;
        private final CustomExpression min;
        private final CustomExpression max;

        Between(CustomExpression value, CustomExpression min, CustomExpression max) {
            super(Type.BOOL);
            this.value = value;
            this.min = min;
            this.max = max;
        }

        @Override
        boolean test(CustomContext context, float[] values) {
            float x = value.number(context, values);
            return x >= min.number(context, values) && x <= max.number(context, values);
        }
    }

    static final class Smooth extends CustomExpression {

        private final int site;
        private final CustomExpression target;
        private final CustomExpression fadeIn;
        private final CustomExpression fadeOut;

        Smooth(int site, CustomExpression target, CustomExpression fadeIn, CustomExpression fadeOut) {
            super(Type.FLOAT);
            this.site = site;
            this.target = target;
            this.fadeIn = fadeIn;
            this.fadeOut = fadeOut;
        }

        @Override
        float number(CustomContext context, float[] values) {
            return context.smooth(site, target.number(context, values), fadeIn.number(context, values),
                    fadeOut.number(context, values));
        }
    }
}
