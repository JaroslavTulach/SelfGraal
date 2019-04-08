/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.apidesign.language.self;

import com.oracle.truffle.api.CompilerDirectives;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.netbeans.api.lexer.Token;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

final class SelfLexer implements Lexer<SelfTokenId> {

    private static final int EOF = LexerInput.EOF;

    private final LexerInput input;
    private final TokenFactory<SelfTokenId> tokenFactory;

    SelfLexer(LexerRestartInfo<SelfTokenId> info) {
        this.input = info.input();
        this.tokenFactory = info.tokenFactory();
        assert (info.state() == null); // passed argument always null
    }

    @Override
    public Token<SelfTokenId> nextToken() {
        while (true) {
            int ch = input.read();
            switch (ch) {
                case '(':
                    return token(SelfTokenId.LPAREN);

                case ')':
                    return token(SelfTokenId.RPAREN);

                case '.':
                    return token(SelfTokenId.DOT);

                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    return finishIntOrFloatLiteral(ch);

                case '!':
                case '@':
                case '#':
                case '$':
                case '%':
                case '^':
                case '&':
                case '*':
                case '-':
                case '+':
                case '=':
                case '~':
                case '/':
                case '?':
                case '<':
                case '>':
                case ',':
                case ';':
                case '|':
                case '‘':
                case '\\':
                    boolean justOne = true;
                    for (;;) {
                        switch (input.read()) {
                            case '!':
                            case '@':
                            case '#':
                            case '$':
                            case '%':
                            case '^':
                            case '&':
                            case '*':
                            case '-':
                            case '+':
                            case '=':
                            case '~':
                            case '/':
                            case '?':
                            case '<':
                            case '>':
                            case ',':
                            case ';':
                            case '|':
                            case '‘':
                            case '\\':
                                justOne = false;
                                continue;
                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                                if (ch == '-') {
                                    input.backup(1);
                                    return finishIntOrFloatLiteral(ch);
                                }
                        }
                        input.backup(1);
                        if (justOne) {
                            switch (ch) {
                                case '|':
                                    return token(SelfTokenId.BAR);
                                case '=':
                                    return token(SelfTokenId.EQUAL);
                                case '.':
                                    return token(SelfTokenId.DOT);
                            }
                        }
                        return token(SelfTokenId.OPERATOR);
                    }
                case '_':
                    return consumeIdentifier(ch);
                case '\'':
                    return consumeString(ch);
                case '"':
                    return consumeComment(ch);
                case ':':
                    ch = input.read();
                    if (Character.isLowerCase(ch)) {
                        Token<SelfTokenId> ident = consumeIdentifier(ch);
                        if (ident.id() == SelfTokenId.IDENTIFIER) {
                            return token(SelfTokenId.ARGUMENT);
                        }
                    }
                    return consumeUptoWhitespace(SelfTokenId.ERROR);
                case EOF:
                    return null;
                default:
                    if (Character.isWhitespace(ch)) {
                        ch = input.read();
                        while (ch != EOF && Character.isWhitespace(ch)) {
                            ch = input.read();
                        }
                        input.backup(1);
                        return token(SelfTokenId.WHITESPACE);
                    }

                    if (Character.isLowerCase(ch)) {
                        return consumeIdentifier(ch);
                    }

                    if (Character.isAlphabetic(ch)) {
                        Token<SelfTokenId> token = consumeIdentifier(ch);
                        if (token.id() == SelfTokenId.KEYWORD) {
                            return token;
                        }
                    }
                    return consumeUptoWhitespace(SelfTokenId.ERROR);
            }
        }
    }

    private Token<SelfTokenId> consumeUptoWhitespace(SelfTokenId id) {
        for (;;) {
            int ch = input.read();
            if (ch == EOF) {
                break;
            }
            if (Character.isWhitespace(ch)) {
                break;
            }
        }
        input.backup(1);
        return token(id);
    }

    private Token<SelfTokenId> consumeString(int ch) {
        boolean backslash = false;
        for (;;) {
            ch = input.read();
            switch (ch) {
                case '\\':
                    backslash = !backslash;
                    break;
                case '\'':
                    if (!backslash) {
                        return token(SelfTokenId.STRING);
                    }
                    break;
                case '\n':
                case EOF:
                    return token(SelfTokenId.ERROR);
            }
        }
    }

    private Token<SelfTokenId> consumeComment(int ch) {
        for (;;) {
            ch = input.read();
            switch (ch) {
                case '"':
                    return token(SelfTokenId.COMMENT);
                case EOF:
                    return token(SelfTokenId.ERROR);
            }
        }
    }

    @Override
    public Object state() {
        return null;
    }

    @Override
    public void release() {
    }

    private Token<SelfTokenId> finishIntOrFloatLiteral(int ch) {
        boolean firstLetter = true;
        boolean baseRead = false;
        boolean floatLiteral = false;
        boolean inExponent = false;
        while (true) {
            switch (ch) {
                case '.':
                    if (floatLiteral) {
                        return token(SelfTokenId.NUMBER);
                    } else {
                        floatLiteral = true;
                    }
                    break;
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    break;
                case 'r':
                case 'R': // base separator
                    if (baseRead) {
                        return token(SelfTokenId.NUMBER);
                    }
                    baseRead = true;
                    break;
                case 'e':
                case 'E': // exponent part
                    if (baseRead && 'a' <= ch && ch <= 'z') {
                        break;
                    }
                    if (inExponent) {
                        return token(SelfTokenId.NUMBER);
                    } else {
                        floatLiteral = true;
                        inExponent = true;
                    }
                    break;
                case '-':
                    if (firstLetter) {
                        break;
                    }
                case '+':
                    if (inExponent) {
                        break;
                    }
                // fallthrough
                default:
                    if (baseRead && 'a' <= ch && ch <= 'z') {
                        break;
                    }
                    input.backup(1);
                    return token(SelfTokenId.NUMBER);
            }
            firstLetter = false;
            ch = input.read();
        }
    }

    private Token<SelfTokenId> token(SelfTokenId id) {
        return (id.fixedText() != null)
            ? tokenFactory.getFlyweightToken(id, id.fixedText())
            : tokenFactory.createToken(id);
    }

    private Token<SelfTokenId> consumeIdentifier(int first) {
        for (;;) {
            int ch = input.read();
            if (Character.isLetterOrDigit(ch)) {
                continue;
            }
            if ('_' == ch) {
                continue;
            }
            SelfTokenId id;
            if (':' == ch) {
                id = Character.isLowerCase(first) ? SelfTokenId.KEYWORD_LOWERCASE : SelfTokenId.KEYWORD;
            } else {
                input.backup(1); // backup the extra char (or EOF)
                switch (input.readText().toString()) {
                    case "true":
                        id = SelfTokenId.BOOLEAN;
                        break;
                    case "false":
                        id = SelfTokenId.BOOLEAN;
                        break;
                    case "resend":
                        id = SelfTokenId.RESEND;
                        break;
                    default:
                        id = SelfTokenId.IDENTIFIER;
                }
            }
            return token(id);
        }
    }

    static class BasicNode {

        private final String name;
        private final BasicNode[] children;
        private final ListItem<SelfObject> objects;

        BasicNode(String name, BasicNode... children) {
            this.name = name;
            this.children = children;
            this.objects = null;
        }

        BasicNode(String name, List<BasicNode> children) {
            this.name = name;
            this.children = children.toArray(new BasicNode[children.size()]);
            this.objects = null;
        }

        BasicNode(String name, ListItem<SelfObject> createdObjects) {
            this.name = name;
            this.children = new BasicNode[0];
            this.objects = createdObjects;
        }

        public void print(int level) {
            for (int i = 0; i < level; i++) {
                System.out.print("  ");
            }
            System.out.println(name);
            for (BasicNode child : children) {
                child.print(level + 1);
            }
        }

        void print(Consumer<Object> registrar) {
            for (BasicNode child : children) {
                child.print(registrar);
            }
            ListItem<SelfObject> obj = objects;
            while (obj != null) {
                registrar.accept(obj.item);
                obj = obj.prev;
            }
        }
    }

    public static BasicNode[] concat(BasicNode first, ListItem<BasicNode> rest) {
        final int size = ListItem.size(rest);
        BasicNode[] result = new BasicNode[size + 1];
        result[0] = first;
        for (int i = size; i >= 1; i--) {
            result[i + 1] = rest.item;
            rest = rest.prev;
        }
        return result;
    }

    public static final class ListItem<E> {

        final ListItem<E> prev;
        final E item;

        public ListItem(ListItem<E> prev, E item) {
            this.prev = prev;
            this.item = item;
        }

        public static <T> ListItem<T> empty() {
            return null;
        }

        public static <T> ListItem<T> empty(Object ignore) {
            return null;
        }

        public static <T> ListItem<T> self(ListItem<T> self) {
            return self;
        }

        public static <A, B> A first(A a, B b) {
            return a;
        }

        public static <A, B> B second(A a, B b) {
            return b;
        }

        public static int size(ListItem<?> item) {
            int cnt = 0;
            while (item != null) {
                cnt++;
                item = item.prev;
            }
            return cnt;
        }

        public static <T> void firstToLast(ListItem<T> last, Consumer<T> call) {
            int len = size(last);
            Object[] arr = new Object[len];
            for (int i = len; i > 0;) {
                arr[--i] = last.item;
                last = last.prev;
            }
            for (int i = 0; i < len; i++) {
                call.accept((T) arr[i]);
            }
        }

        public static <T> T[] toArray(ListItem<T> node, Function<Integer, T[]> factory) {
            T[] arr = factory.apply(size(node));
            for (int i = arr.length; node != null;) {
                arr[--i] = node.item;
                node = node.prev;
            }
            return arr;
        }
    }
}
