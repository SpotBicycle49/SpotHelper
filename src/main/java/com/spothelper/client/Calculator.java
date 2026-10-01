package com.spothelper.client;

/** Small arithmetic expression parser used by /calc. */
public final class Calculator {
    private Calculator() {}

    public static double evaluate(String expression) {
        Parser parser = new Parser(expression);
        double result = parser.parseExpression();
        parser.skipSpaces();
        if (!parser.atEnd()) throw new IllegalArgumentException("Лишние символы");
        if (Double.isNaN(result) || Double.isInfinite(result)) throw new IllegalArgumentException("Некорректный результат");
        return result;
    }

    public static String format(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        return Double.toString(value);
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text == null ? "" : text;
        }

        boolean atEnd() { return pos >= text.length(); }
        void skipSpaces() { while (!atEnd() && Character.isWhitespace(text.charAt(pos))) pos++; }

        double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (match('+')) value += parseTerm();
                else if (match('-')) value -= parseTerm();
                else return value;
            }
        }

        double parseTerm() {
            double value = parseUnary();
            while (true) {
                skipSpaces();
                if (match('*')) value *= parseUnary();
                else if (match('/')) {
                    double divisor = parseUnary();
                    if (divisor == 0.0D) throw new IllegalArgumentException("Деление на ноль");
                    value /= divisor;
                } else return value;
            }
        }

        double parseUnary() {
            skipSpaces();
            if (match('+')) return parseUnary();
            if (match('-')) return -parseUnary();
            return parsePrimary();
        }

        double parsePrimary() {
            skipSpaces();
            if (match('(')) {
                double value = parseExpression();
                skipSpaces();
                if (!match(')')) throw new IllegalArgumentException("Нет закрывающей скобки");
                return value;
            }
            int start = pos;
            boolean dot = false;
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (Character.isDigit(c)) pos++;
                else if (c == '.' && !dot) { dot = true; pos++; }
                else break;
            }
            if (start == pos) throw new IllegalArgumentException("Ожидалось число");
            try {
                return Double.parseDouble(text.substring(start, pos));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Некорректное число");
            }
        }

        boolean match(char c) {
            if (!atEnd() && text.charAt(pos) == c) { pos++; return true; }
            return false;
        }
    }
}
