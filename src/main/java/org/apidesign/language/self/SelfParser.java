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

import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import static org.apidesign.language.self.PEParser.*;
import org.apidesign.language.self.SelfLexer.BasicNode;
import org.apidesign.language.self.SelfLexer.ListItem;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

final class SelfParser {
    private static final PEParser PARSER;
    static {
        PARSER = new PEParser();
        // create the rules
        Rule<SelfObject> statement = PARSER.rule("statement");
        Rule<SelfObject> objectLiteral = PARSER.rule("object");
        Rule<SelfCode> exprlist = PARSER.rule("exprlist");
        Rule<SelfObject> constant = PARSER.rule("constant");
        Rule<SelfCode> unaryLevel = PARSER.rule("unaryLevel");
        Rule<SelfCode> binaryLevel = PARSER.rule("binaryLevel");
        Rule<SelfCode> keywordLevel = PARSER.rule("keywordLevel");
        Rule<SelfCode> expression = PARSER.rule("expression");

        Element<Token<SelfTokenId>> slotId = alt(
                ref(SelfTokenId.IDENTIFIER),
                seq(ref(SelfTokenId.KEYWORD_LOWERCASE), alt(
                        seq(
                            ref(SelfTokenId.IDENTIFIER), rep(
                                seq(ref(SelfTokenId.KEYWORD), ref(SelfTokenId.IDENTIFIER), (key, id) -> {
                                    return key;
                                }),
                                ListItem::empty, ListItem::new, ListItem::self
                            ), (id, rest) -> {
                            return id;
                        })
//                        rep(ref(SelfTokenId.KEYWORD))
                ), (key, alt) -> {
                    return key;
                }),
                seq(ref(SelfTokenId.OPERATOR), opt(ref(SelfTokenId.IDENTIFIER)), (op, id) -> op)
        );

        Element<SlotInfo> slot = alt(
                seq(
                    slotId, alt(ref(SelfTokenId.EQUAL), ref(SelfTokenId.ARROW)), alt(constant, ref(SelfTokenId.IDENTIFIER), statement),
                    (a, b, c) -> {
                        boolean mutable = b.id() != SelfTokenId.EQUAL;
                        return new SlotInfo(a.text(), mutable, false, c);
                    }
                ),
                ref(SelfTokenId.ARGUMENT, (t) -> SlotInfo.argument(t.text()))
        );

        final Element<SlotInfo> dotAndSlot = seq(ref(SelfTokenId.DOT), slot, ListItem::second);
        Element<ListItem<SlotInfo>> extraSlots = rep(dotAndSlot, ListItem::<SlotInfo>empty, ListItem::new, ListItem::self);

        Element<ListItem<SlotInfo>> slotsDef = alt(
            ref(SelfTokenId.BAR, ListItem::<SlotInfo>empty),
            seq(slot, extraSlots, ref(SelfTokenId.BAR), (t, m, u) -> {
                return new ListItem<>(m, t);
            })
        );

        Element<SelfObject> objectStatement = seq(
                ref(SelfTokenId.LPAREN), alt(
                    seq(ref(SelfTokenId.BAR), slotsDef, opt(exprlist), ref(SelfTokenId.RPAREN), (bar, slts, expr, rparen) -> {
                        SelfObject.Builder builder = SelfObject.newBuilder();
                        while (slts != null) {
                            if (slts.item.argument) {
                                builder.argument(slts.item.id.toString());
                            } else {
                                builder.slot(slts.item.id.toString(), slts.item.value);
                            }
                            slts = slts.prev;
                        }
                        if (expr.isPresent()) {
                            builder.code(expr.get());
                        }
                        return builder;
                    }),
                    seq(exprlist, ref(SelfTokenId.RPAREN), (expr, rparen) -> {
                        return SelfObject.newBuilder().code(expr);
                    }),
                    ref(SelfTokenId.RPAREN, (rparen) -> SelfObject.newBuilder())
                ),
                (t, u) -> {
                    return u.build();
                }
        );
        objectLiteral.define(objectStatement);



        statement.define(alt(constant));

        final Element<SelfObject> constantDef = alt(
            ref(SelfTokenId.BOOLEAN, (t) -> {
                return SelfObject.valueOf(Boolean.valueOf(t.text().toString()));
            }),
            ref(SelfTokenId.STRING, (t) -> {
                return SelfObject.valueOf(t.text().toString());
            }),
            ref(SelfTokenId.NUMBER, (t) -> {
                return SelfObject.valueOf(Integer.valueOf(t.text().toString()));
            }),
            objectLiteral
        );
        constant.define(constantDef);

        Element<Object> unaryExprHead = alt(constant, ref(SelfTokenId.IDENTIFIER));
        Element<ListItem<Token<SelfTokenId>>> unaryExprTail = rep(
            ref(SelfTokenId.IDENTIFIER),
            ListItem::<Token<SelfTokenId>>empty, ListItem::new, ListItem::self
        );
        unaryLevel.define(seq(unaryExprHead, unaryExprTail, (t, u) -> {
            SelfCode[] receiver = { null };
            if (t instanceof SelfObject) {
                // constant
                receiver[0] = SelfCode.constant((SelfObject) t);
            } else {
                // identifier - default receiver is self
                receiver[0] = SelfCode.self();
            }
            ListItem.firstToLast(u, (item) -> {
                final SelfSelector msg = SelfSelector.keyword(u.item.text().toString());
                receiver[0] = SelfCode.unaryMessage(receiver[0], msg);
            });
            return receiver[0];
        }));

        Element<SelfCode> binaryExpr = alt(
            seq(ref(SelfTokenId.OPERATOR), unaryLevel, (t, u) -> {
                return null;
            }),
            seq(unaryLevel, opt(
                seq(ref(SelfTokenId.OPERATOR), unaryLevel, (operator, argument) -> {
                    return new Object[] { operator, argument };
                })
            ), (unary, operatorAndArgument) -> {
                if (operatorAndArgument.isEmpty()) {
                    return unary;
                } else {
                    Token<?> token = (Token<?>) operatorAndArgument.get()[0];
                    SelfCode arg = (SelfCode) operatorAndArgument.get()[1];
                    final SelfSelector msg = SelfSelector.keyword(token.text().toString());
                    return SelfCode.binaryMessage(unary, msg, arg);
                }
            })
        );
        binaryLevel.define(binaryExpr);

        Element<ListItem<SelectorArg>> keywordSeq = seq(ref(SelfTokenId.KEYWORD_LOWERCASE), binaryLevel, rep(
            seq(ref(SelfTokenId.KEYWORD), keywordLevel, (selectorPart, arg) -> {
                return new SelectorArg(selectorPart.text().toString(), arg);
            }),
            ListItem::<SelectorArg>empty, ListItem::new, ListItem::self
        ), (selectorPart, arg, subsequent) -> {
            return new ListItem<SelectorArg>(subsequent, new SelectorArg(selectorPart.text().toString(), arg));
        });
        keywordLevel.define(seq(keywordSeq, (selectorAndArgList) -> {
            return SelectorArg.createKeywordInvocation(selectorAndArgList, SelfCode.self());
        }));
        expression.define(alt(keywordLevel, seq(
            binaryLevel, opt(keywordSeq), (t, u) -> {
                if (u.isPresent()) {
                    return SelectorArg.createKeywordInvocation(u.get(), t);
                } else {
                    return t;
                }
            }
        )));
        exprlist.define(seq(expression, rep(seq(ref(SelfTokenId.DOT), expression, ListItem::second),
            ListItem::<SelfCode>empty, ListItem::new, ListItem::self), (head, tail) -> {
            ListItem<SelfCode> whole = new ListItem<>(tail, head);
            SelfCode[] arr = ListItem.toArray(whole, SelfCode[]::new);
            return SelfCode.block(arr);
        }));
        PARSER.initialize(exprlist);
    }

    private static class SelectorArg {
        final String selector;
        final SelfCode arg;

        SelectorArg(String selector, SelfCode arg) {
            this.selector = selector;
            this.arg = arg;
        }

        static SelfCode createKeywordInvocation(ListItem<SelectorArg> selectorAndArgList, final SelfCode self) {
            int size = ListItem.size(selectorAndArgList);
            String[] selectorParts = new String[size];
            SelfCode[] args = new SelfCode[size];
            ListItem<SelectorArg> head = selectorAndArgList;
            for (int i = size - 1; i >= 0; i--) {
                selectorParts[i] = head.item.selector;
                args[i] = head.item.arg;
            }
            SelfSelector selector = SelfSelector.keyword(selectorParts);
            return SelfCode.keywordMessage(self, selector, args);
        }
    }


    public static SelfCode parse(Source s) {
        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(s.getCharacters(), SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        class SeqLexer implements PELexer {
            private final Object[] self = new Object[] { this };
            private boolean eof;
            {
                nextTokenMove(null);
            }

            @Override
            public Object[] asArgumentsArray() {
                return self;
            }

            @Override
            public Token<SelfTokenId> peek(ConditionProfile seenEof) {
                if (eof) {
                    return null;
                }
                return seq.token();
            }

            @Override
            public String position() {
                return "at: " + seq.offset() + ": " + seq.subSequence(seq.offset()).toString();
            }

            @Override
            public void resetStackPointer(int pointer) {
                seq.move(pointer);
                seq.moveNext();
            }

            @Override
            public Token<SelfTokenId> nextToken(ConditionProfile seenEof) {
                Token<SelfTokenId> token = peek(seenEof);
                nextTokenMove(seenEof);
                return token;
            }

            private void nextTokenMove(ConditionProfile seenEof) {
                while (seq.moveNext()) {
                    final Token<SelfTokenId> lookahead = seq.token();
                    if (lookahead.id() != SelfTokenId.WHITESPACE) {
                        return;
                    }
                }
                eof = true;
                if (seenEof != null) {
                    seenEof.profile(true);
                }
            }

            @Override
            public int getStackPointer() {
                return seq.offset();
            }

            @Override
            public String tokenNames(TokenId id) {
                return "token " + id;
            }

            @Override
            public String toString() {
                return position();
            }
        }
        final PELexer lexer = new SeqLexer();
        SelfCode code = (SelfCode) PARSER.parse(lexer);
        assert lexer.peek(null) == null : "Fully parsed: " + seq;
        return code;
    }

    private static final class SlotInfo {
        private static SlotInfo argument(CharSequence text) {
            return new SlotInfo(text, false, true, null);
        }

        private final CharSequence id;
        private final boolean mutable;
        private final Object value;
        private final boolean argument;

        public SlotInfo(CharSequence id, boolean mutable, boolean argument, Object value) {
            this.id = id;
            this.mutable = mutable;
            this.argument = argument;
            this.value = value;
        }

        private Object valueToString() {
            if (value instanceof Token) {
                return ((Token)value).text().toString();
            }
            return value.toString();
        }
    }
}

enum SelfTokenId implements TokenId {

    WHITESPACE(null, "whitespace"),
    IDENTIFIER(null, "identifier"),
    RESEND(null, "identifier"),
    KEYWORD_LOWERCASE(null, "identifier"),
    KEYWORD(null, "identifier"),
    ARGUMENT(null, "identifier"),
    OPERATOR(null, null),
    BOOLEAN(null, "number"),
    NUMBER(null, "number"),
    STRING(null, "string"),

    COMMENT(null, "comment"),
    LPAREN("(", "separator"),
    RPAREN(")", "separator"),
    BAR("|", "separator"),
    DOT(".", "separator"),
    EQUAL("=", "separator"),
    ARROW("<-", "separator"),
    ERROR(null, "error");

    public static final String MIMETYPE = "text/x-self";

    private final String fixedText;

    private final String primaryCategory;

    private SelfTokenId(String fixedText, String primaryCategory) {
        this.fixedText = fixedText;
        this.primaryCategory = primaryCategory;
    }

    public String fixedText() {
        return fixedText;
    }

    @Override
    public String primaryCategory() {
        return primaryCategory;
    }

    private static final Language<SelfTokenId> language = new LanguageHierarchy<SelfTokenId>() {
        @Override
        protected Collection<SelfTokenId> createTokenIds() {
            return EnumSet.allOf(SelfTokenId.class);
        }

        @Override
        protected Map<String,Collection<SelfTokenId>> createTokenCategories() {
            Map<String,Collection<SelfTokenId>> cats = new HashMap<>();
            return cats;
        }

        @Override
        protected Lexer<SelfTokenId> createLexer(LexerRestartInfo<SelfTokenId> info) {
            return new SelfLexer(info);
        }

        @Override
        protected String mimeType() {
            return MIMETYPE;
        }

    }.language();

    public static final Language<SelfTokenId> language() {
        return language;
    }

}

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

                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    return finishIntOrFloatLiteral(ch);

                case '!': case '@': case '#': case '$': case '%':
                case '^': case '&': case '*': case '-': case '+':
                case '=': case '~': case '/': case '?': case '<':
                case '>': case ',': case ';': case '|': case '‘':
                case '\\':
                    boolean justOne = true;
                    for (;;) {
                        switch (input.read()) {
                            case '!': case '@': case '#': case '$': case '%':
                            case '^': case '&': case '*': case '-': case '+':
                            case '=': case '~': case '/': case '?': case '<':
                            case '>': case ',': case ';': case '|': case '‘':
                            case '\\':
                                justOne = false;
                                continue;
                            case '0': case '1': case '2': case '3': case '4':
                            case '5': case '6': case '7': case '8': case '9':
                                if (ch == '-') {
                                    input.backup(1);
                                    return finishIntOrFloatLiteral(ch);
                                }
                        }
                        input.backup(1);
                        if (justOne) {
                            switch (ch) {
                                case '|': return token(SelfTokenId.BAR);
                                case '=': return token(SelfTokenId.EQUAL);
                                case '.': return token(SelfTokenId.DOT);
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
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    break;
                case 'r': case 'R': // base separator
                    if (baseRead) {
                        return token(SelfTokenId.NUMBER);
                    }
                    baseRead = true;
                    break;
                case 'e': case 'E': // exponent part
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
                    case "true": id = SelfTokenId.BOOLEAN; break;
                    case "false": id = SelfTokenId.BOOLEAN; break;
                    case "resend": id = SelfTokenId.RESEND; break;
                    default: id = SelfTokenId.IDENTIFIER;
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
            if (last != null) {
                firstToLast(last.prev, call);
                call.accept(last.item);
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
