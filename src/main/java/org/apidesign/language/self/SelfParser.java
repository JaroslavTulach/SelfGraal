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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import static org.apidesign.language.self.PEParser.*;
import org.apidesign.language.self.SelfLexer.ListItem;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;

final class SelfParser {
    private final PEParser parser;
    private final SelfLanguage lang;
    private final SelfPrimitives primitives;

    SelfParser(SelfLanguage lang, SelfPrimitives primitives) {
        this.lang = lang;
        this.primitives = primitives;
        this.parser = new PEParser();
        // create the rules
        Rule<SelfObject> statement = parser.rule("statement");
        Rule<SelfObject> objectLiteral = parser.rule("object");
        Rule<SelfCode> exprlist = parser.rule("exprlist");
        Rule<SelfObject> constant = parser.rule("constant");
        Rule<SelfCode> unaryLevel = parser.rule("unaryLevel");
        Rule<SelfCode> binaryLevel = parser.rule("binaryLevel");
        Rule<SelfCode> keywordLevel = parser.rule("keywordLevel");
        Rule<SelfCode> expression = parser.rule("expression");

        Element<ListItem<IdArg>> slotId = alt(
                ref(SelfTokenId.IDENTIFIER, (t) -> {
                    return new ListItem<>(null, new IdArg(t, null));
                }),
                seq(ref(SelfTokenId.KEYWORD_LOWERCASE), opt(alt(
                        seq(
                            ref(SelfTokenId.IDENTIFIER), rep(
                                seq(ref(SelfTokenId.KEYWORD), ref(SelfTokenId.IDENTIFIER), (key, arg) -> {
                                    return new IdArg(key, arg);
                                }),
                                ListItem::<IdArg>empty, ListItem::new, ListItem::self
                            ), (id, rest) -> {
                            return new ListItem<>(rest, new IdArg(null, id));
                        }),
                        seq(
                            ref(SelfTokenId.KEYWORD),rep(
                                ref(SelfTokenId.KEYWORD, (key) -> {
                                    return new IdArg(key, null);
                                }),
                                ListItem::<IdArg>empty, ListItem::new, ListItem::self
                            ), (secondKeyword, moreKeywords) -> {
                                return ListItem.firstAndNewer(new IdArg(secondKeyword, null), moreKeywords);
                            }
                        )
                )), (key, alt) -> {
                    if (alt.isPresent()) {
                        ListItem<IdArg> rest = alt.get();
                        if (rest.item.id == null) {
                            return ListItem.firstAndNewer(new IdArg(key, rest.item.arg), rest.prev);
                        } else {
                            return ListItem.firstAndNewer(new IdArg(key, null), rest);
                        }
                    } else {
                        return new ListItem<>(null, new IdArg(key, null));
                    }
                }),
                seq(ref(SelfTokenId.OPERATOR), opt(ref(SelfTokenId.IDENTIFIER)), (op, id) -> {
                    return new ListItem<>(null, new IdArg(op, null));
                })
        );

        Element<SlotInfo> slot = alt(
                seq(
                    slotId, alt(ref(SelfTokenId.EQUAL), ref(SelfTokenId.ARROW)), alt(constant, ref(SelfTokenId.IDENTIFIER), statement),
                    (idsAndArgs, b, c) -> {
                        boolean mutable = b.id() != SelfTokenId.EQUAL;
                        SelfSelector messageSelector = IdArg.toSelector(idsAndArgs);
                        IdArg a = idsAndArgs.item;
                        if (a.arg != null && c instanceof SelfObject) {
                            ListItem<IdArg> at = idsAndArgs;
                            while (at != null) {
                                final String argName = ":" + at.item.arg.text();
                                c = SelfObject.newBuilder((SelfObject) c).argument(argName).build();
                                at = at.prev;
                            }
                        }
                        return new SlotInfo(messageSelector.toString(), mutable, false, c);
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
                            builder.code(toCallTarget(expr.get()));
                        }
                        return builder;
                    }),
                    seq(exprlist, ref(SelfTokenId.RPAREN), (expr, rparen) -> {
                        return SelfObject.newBuilder().code(toCallTarget(expr));
                    }),
                    ref(SelfTokenId.RPAREN, (rparen) -> SelfObject.newBuilder())
                ),
                (t, u) -> {
                    return u.build();
                }
        );

        Element<SelfObject> blockStatement = seq(
                ref(SelfTokenId.LBRACKET), alt(
                    seq(ref(SelfTokenId.BAR), slotsDef, opt(exprlist), ref(SelfTokenId.RBRACKET), (bar, slts, expr, rparen) -> {
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
                            builder.code(toCallTarget(expr.get()));
                        }
                        return builder;
                    }),
                    seq(exprlist, ref(SelfTokenId.RBRACKET), (expr, rparen) -> {
                        return SelfObject.newBuilder().code(toCallTarget(expr));
                    }),
                    ref(SelfTokenId.RBRACKET, (rparen) -> SelfObject.newBuilder())
                ),
                (t, u) -> {
                    return u.block(true).build();
                }
        );
        objectLiteral.define(alt(objectStatement, blockStatement));


        statement.define(alt(constant));

        final Element<SelfObject> constantDef = alt(
            ref(SelfTokenId.BOOLEAN, (t) -> {
                return primitives.valueOf(Boolean.valueOf(t.text().toString()));
            }),
            ref(SelfTokenId.STRING, (t) -> {
                return primitives.valueOf(t.text().toString());
            }),
            ref(SelfTokenId.NUMBER, (t) -> {
                return primitives.valueOf(Integer.valueOf(t.text().toString()));
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
                final SelfSelector selector = SelfSelector.keyword(((Token<?>)t).text().toString());
                // identifier - default receiver is self
                receiver[0] = SelfCode.unaryMessage(SelfCode.self(), selector);
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
            seq(unaryLevel, rep(
                seq(ref(SelfTokenId.OPERATOR), unaryLevel, (operator, argument) -> {
                    return new Object[] { operator, argument };
                }), ListItem::<Object[]>empty, ListItem::new, ListItem::self
            ), (unary, operatorAndArgument) -> {
                if (operatorAndArgument == null) {
                    return unary;
                } else {
                    SelfCode[] tree = { unary };
                    String[] previousText = { null };
                    ListItem.firstToLast(operatorAndArgument, (opArg) -> {
                        String operator = ((Token<?>) opArg[0]).text().toString();
                        if (previousText[0] != null && !previousText[0].equals(operator)) {
                            throw new IllegalStateException("no precedence for binary operator - please use parentheses for " + previousText[0] + " and " + operator);
                        }
                        previousText[0] = operator;
                        SelfCode arg = (SelfCode) opArg[1];
                        final SelfSelector msg = SelfSelector.keyword(operator);
                        tree[0] = SelfCode.binaryMessage(tree[0], msg, arg);
                    });
                    return tree[0];
                }
            })
        );
        binaryLevel.define(binaryExpr);

        Element<ListItem<SelectorArg>> keywordSeq = seq(ref(SelfTokenId.KEYWORD_LOWERCASE), expression, rep(
            seq(ref(SelfTokenId.KEYWORD), expression, (selectorPart, arg) -> {
                return new SelectorArg(selectorPart.text().toString(), arg);
            }),
            ListItem::<SelectorArg>empty, ListItem::new, ListItem::self
        ), (selectorPart, arg, subsequent) -> {
            return ListItem.firstAndNewer(new SelectorArg(selectorPart.text().toString(), arg), subsequent);
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
        parser.initialize(exprlist);
    }

    private CallTarget toCallTarget(SelfCode code) {
        return SelfCode.toCallTarget(lang, code);
    }

    private static class IdArg {
        final Token<SelfTokenId> id;
        final Token<SelfTokenId> arg;

        IdArg(Token<SelfTokenId> id, Token<SelfTokenId> arg) {
            this.id = id;
            this.arg = arg;
        }

        static SelfSelector toSelector(ListItem<IdArg> args) {
            int size = ListItem.size(args);
            String[] keywords = new String[size];
            for (int i = size; i > 0;) {
                keywords[--i] = args.item.id.text().toString();
                args = args.prev;
            }
            return SelfSelector.keyword(keywords);
        }
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
            for (int i = size; i > 0;) {
                --i;
                selectorParts[i] = head.item.selector;
                args[i] = head.item.arg;
                head = head.prev;
            }
            SelfSelector selector = SelfSelector.keyword(selectorParts);
            return SelfCode.keywordMessage(self, selector, args);
        }
    }


    public SelfCode parse(Source s) {
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
        SelfCode code = (SelfCode) parser.parse(lexer);
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
