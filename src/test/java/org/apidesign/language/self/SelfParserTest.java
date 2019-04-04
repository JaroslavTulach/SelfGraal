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

import com.oracle.truffle.api.source.Source;
import java.util.Map;
import java.util.function.Consumer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;

public class SelfParserTest {

    public SelfParserTest() {
    }

    @Test
    public void testLexingTheInput() {
        TokenSequence<SelfTokenId> seq = TokenHierarchy.create("1 + 2.3", SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNull("before 1st token", seq.token());
        assertNextToken("1", seq);
        assertNextToken(" ", seq);
        assertNextToken("+", seq);
        assertNextToken(" ", seq);
        assertNextToken("2.3", seq);
        assertFalse("At the end of input", seq.moveNext());
    }

    @Test
    public void identifiers() {
        String text = "    i _IntAdd cloud9 resend m a_point \n\t\r NotAnIdent self";

        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(text, SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.IDENTIFIER, seq).text("i");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.IDENTIFIER, seq).text("_IntAdd");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.IDENTIFIER, seq).text("cloud9");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.RESEND, seq);
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.IDENTIFIER, seq).text("m");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.IDENTIFIER, seq).text("a_point");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.ERROR, seq).text("NotAnIdent");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.SELF, seq);
        assertFalse("At the end of input", seq.moveNext());
    }

    @Test
    public void keywords() {
        String text = "\tat: NoKeyword Put:\n_IntAdd:";

        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(text, SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.KEYWORD_LOWERCASE, seq).text("at:");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.ERROR, seq).text("NoKeyword");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.KEYWORD, seq).text("Put:");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.KEYWORD, seq).text("_IntAdd:");
        assertFalse("At the end of input", seq.moveNext());
    }

    @Test
    public void arguments() {
        String text = "\t:arg1 :NoArg :x";

        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(text, SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.ARGUMENT, seq).text(":arg1");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.ERROR, seq).text(":NoArg");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.ARGUMENT, seq).text(":x");
        assertFalse("At the end of input", seq.moveNext());
    }

    @Test
    public void operators() {
        String text = "   && ++ * +=- |";

        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(text, SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.OPERATOR, seq).text("&&");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.OPERATOR, seq).text("++");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.OPERATOR, seq).text("*");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.OPERATOR, seq).text("+=-");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.BAR, seq);
        assertFalse("At the end of input", seq.moveNext());
    }

    @Test
    public void numbers() {
        String text = "\r123 . 3.14 1272.34e+15 1e10 1272.34e-15 16r27fe -5";

        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(text, SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.NUMBER, seq).text("123");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.DOT, seq).text(".");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.NUMBER, seq).text("3.14");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.NUMBER, seq).text("1272.34e+15");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.NUMBER, seq).text("1e10");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.NUMBER, seq).text("1272.34e-15");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.NUMBER, seq).text("16r27fe");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.NUMBER, seq).text("-5");
        assertFalse("At the end of input", seq.moveNext());
    }

    @Test
    public void strings() {
        String text = "   'Hi' '\\t\\f\\'\\x20\\d32\\o40\\\"\\\\ \\n\n 'x'";

        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(text, SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.STRING, seq).text("'Hi'");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.ERROR, seq);
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.STRING, seq).text("'x'");
        assertFalse("At the end of input", seq.moveNext());
    }
    @Test
    public void comments() {
        String text = "   \"comment\"  \n \"multiline\ncomment\" ";

        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(text, SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.COMMENT, seq).text("\"comment\"");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertNextToken(SelfTokenId.COMMENT, seq).text("\"multiline\ncomment\"");
        assertNextToken(SelfTokenId.WHITESPACE, seq);
        assertFalse("At the end of input", seq.moveNext());
    }

    @Test
    public void parseCodeObject() {
        Source s = Source.newBuilder("Self", "( 1 + 2 )", "empty.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
    }
    @Test
    public void parseEmptyObject() {
        Source s = Source.newBuilder("Self", "()", "empty.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
    }

    @Test
    public void parseEmptyObjectWithSlots() {
        Source s = Source.newBuilder("Self", "( | | )", "empty.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
    }

    @Test
    public void parseEmptyObjectWithOneSlot() {
        Source s = Source.newBuilder("Self", "( | x = 's' | )", "empty.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
        assertTrue("Instance of hash map: " + c.obj, c.obj instanceof Map);
        Map<?,?> map = (Map<?,?>) c.obj;
        assertEquals("Value of x is s", "'s'", map.get("x"));
    }

    @Test
    public void parseIdFn() {
        Source s = Source.newBuilder("Self", "( | id: n = ( ^n ) | )", "empty.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
        assertTrue("Instance of hash map: " + c.obj, c.obj instanceof Map);
        Map<?,?> map = (Map<?,?>) c.obj;
        assertNotNull("Value of id is set", map.get("id:"));
    }

    @Test
    public void parsePlusFn() {
        Source s = Source.newBuilder("Self", "( | plus: n = ( n + 1 ) | ) plus: 3", "plus.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
        assertTrue("Instance of hash map: " + c.obj, c.obj instanceof Map);
        Map<?,?> map = (Map<?,?>) c.obj;
        assertNotNull("Value of id is set", map.get("plus:"));
    }

    @Test
    public void parseConstantFn() {
        Source s = Source.newBuilder("Self", "( | id: n = 'e' | )", "empty.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
        assertTrue("Instance of hash map: " + c.obj, c.obj instanceof Map);
        Map<?,?> map = (Map<?,?>) c.obj;
        assertEquals("Value of id is object", "'e'", map.get("id:"));
    }

    @Test
    public void parseEmptyObjectWithTwoSlots() {
        Source s = Source.newBuilder("Self", "( | x = 's' . y = 3 | )", "empty.sf").build();
        class Collect implements Consumer<Object> {
            Object obj;
            @Override
            public void accept(Object arg0) {
                assertNull("No object yet", obj);
                obj = arg0;
            }
        }
        Collect c = new Collect();
        SelfParser.parse(s, c);

        assertNotNull("Object created", c.obj);
        assertTrue("Instance of hash map: " + c.obj, c.obj instanceof Map);
        Map<?,?> map = (Map<?,?>) c.obj;
        assertEquals("Value of x is s", "'s'", map.get("x"));
        assertEquals("Value of y is s", "3", map.get("y"));
    }

    private TokenHandle assertNextToken(String text, TokenSequence<SelfTokenId> seq) {
        assertTrue("There is more tokens", seq.moveNext());
        Token<SelfTokenId> token = seq.token();
        assertEquals(text, token.text());
        return new TokenHandle(token);
    }

    private TokenHandle assertNextToken(SelfTokenId id, TokenSequence<SelfTokenId> seq) {
        assertTrue("There is more tokens", seq.moveNext());
        Token<SelfTokenId> token = seq.token();
        assertEquals(id, token.id());
        return new TokenHandle(token);
    }

    private static final class TokenHandle {
        private final Token<SelfTokenId> token;

        TokenHandle(Token<SelfTokenId> token) {
            this.token = token;
        }

        TokenHandle id(SelfTokenId id) {
            assertEquals(token.id(), id);
            return this;
        }

        TokenHandle text(String text) {
            assertEquals(text, token.text());
            return this;
        }
    }
}
