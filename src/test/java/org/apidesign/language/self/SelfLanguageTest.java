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

import java.io.IOException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SelfLanguageTest {
    @Before
    public void registerInGraalVMLanguagePath() {
        final String path = System.getProperty("java.class.path");
        System.setProperty("truffle.class.path.append", path);
    }

    @Test
    public void evalTrue() {
        Object yes = Context.create().eval("Self", "true").asBoolean();
        Assert.assertEquals(Boolean.TRUE, yes);
    }

    @Test
    public void evalFalse() {
        Object no = Context.create().eval("Self", "false").asBoolean();
        Assert.assertEquals(Boolean.FALSE, no);
    }

    @Test
    public void evalNotTrue() {
        Object no = Context.create().eval("Self", "true not").asBoolean();
        Assert.assertEquals(Boolean.FALSE, no);
    }

    @Test
    public void evalNotNotTrue() {
        Object yes = Context.create().eval("Self", "true not not").asBoolean();
        Assert.assertEquals(Boolean.TRUE, yes);
    }

    @Test
    public void evalPlus() {
        int three = Context.create().eval("Self", "(1 + 2)").asInt();
        Assert.assertEquals(3, three);
    }

    @Test
    public void invokeKeyMessageOnEmptyObject() {
        final Context ctx = Context.create();
        try {
            Value res = ctx.eval("Self", "() plus: 2");
            fail("Unexpected result: " + res);
        } catch (PolyglotException ex) {
            assertNotEquals(ex.getMessage(), -1, ex.getMessage().indexOf("Unknown identifier: plus:"));
        }
    }

    @Test
    public void evalNplusOneArgument() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus: = ( | :n | 1 + n ) | ) plus: 2");
        Assert.assertEquals(3, res.asInt());
    }

    @Test
    public void evalNplusOneDirect() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus: n = ( 1 + n ) | ) plus: 2");
        Assert.assertEquals(3, res.asInt());
    }

    @Test
    public void evalMultiKeywordMessage() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus: n And: m = ( m + n ) | ) plus: 2 And: 3");
        Assert.assertEquals(5, res.asInt());
    }

    @Test
    public void evalMultiKeywordMessageWithInObjectArgs() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus:And:= ( | :n. :m | m + n ) | ) plus: 2 And: 3");
        Assert.assertEquals(5, res.asInt());
    }

    @Test
    public void evalMultiKeywordMessage3() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus: n And: m By: o = ( (m + n) + o ) | ) plus: 2 And: 2 By: 1");
        Assert.assertEquals(5, res.asInt());
    }

    @Test
    public void evalMultiKeywordMessageWithInObjectArgs3() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus:And:By: = ( | :n. :m. :o | (m + n) + o ) | ) plus: 2 And: 1 By: 2");
        Assert.assertEquals(5, res.asInt());
    }

    @Test
    public void evalMultiKeywordMessage4() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus: n And: m By: o Yet: p = ( m + n + o + p ) | ) plus: 1 And: 2 By: 1 Yet: 1");
        Assert.assertEquals(5, res.asInt());
    }

    @Test(expected = PolyglotException.class)
    public void rowOfHeterogenousOperandsIsntAllowed() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus: n And: m By: o = ( m + n * o) | )");
        fail("Parse shouldn't succeed: " + res);
    }

    @Test
    public void evalMultiKeywordMessageWithInObjectArgs4() {
        final Context ctx = Context.create();
        Value res = ctx.eval("Self", "( | plus:And:By:Yet: = ( | :n. :m. :o. :p | (m + n) + (o + p) ) | ) plus: 2 And: 1 By: 1 Yet: 1");
        Assert.assertEquals(5, res.asInt());
    }

    @Test
    public void evalNplusOne() {
        final Context ctx = Context.create();
        Value inc = ctx.eval("Self", "( | plus: n = ( n + 1 ). minus: n = (n - 1) | )");
        Value three = inc.invokeMember("plus:", 2);
        Assert.assertEquals(3, three.asInt());
        Value five = inc.invokeMember("plus:", 4);
        Assert.assertEquals(5, five.asInt());
        Value four = inc.invokeMember("minus:", 5);
        Assert.assertEquals(4, four.asInt());
    }

    @Test
    public void abs() {
        final Context ctx = Context.create();
        Value five = ctx.eval("Self", "( | abs: n = ( n < 0 ifTrue: 0 - n False: n ) | ) abs: -5");
        assertEquals(5, five.asInt());
    }

    @Test
    public void fibonacci() {
        final Context ctx = Context.create();
        Value fibonacci = ctx.eval("Self", "( | fib: n = ( n < 3 ifTrue: 1 False: [ (fib: (n - 1)) + (fib: (n - 2)) ] ) | ) fib: 20");
        assertEquals(6765, fibonacci.asInt());
    }

    @Test
    public void benchmark() throws Exception {
        String benchmarkName = System.getProperty("SelfGraal.Benchmark");
        Assume.assumeNotNull("Not running the benchmark without a name", benchmarkName);
        StringBuilder sb = new StringBuilder();
        sb.append("( |");
        for (int i = 0; i < 100000; i++) {
            sb.append("plus").append(i).append(": n = ( n + 1 ).\n");
        }
        sb.append(" x = 1 |)");
        Context ctx = Context.create();

        System.out.println("Warming up...");
        benchmarkNTimes("WarmUp", 20, sb, ctx, new long[1]);

        System.out.println("Benchmarking...");
        long[] sum = { 0 };
        int count = 10;
        benchmarkNTimes(benchmarkName, count, sb, ctx, sum);
        System.out.println(benchmarkName + " took " + (sum[0] / count) + " ms on average");
    }

    private void benchmarkNTimes(String name, int count, StringBuilder sb, Context ctx, long[] sum) throws IOException {
        for (int i = 1; i <= count; i++) {
            System.gc();
            System.runFinalization();
            System.gc();
            Source src = Source.newBuilder("Self", sb.toString(), "large" + i + ".sf").build();
            long before = System.currentTimeMillis();
            ctx.eval(src);
            long after = System.currentTimeMillis();
            final long took = after - before;
            System.out.println(name + " #" + i + " took " + took + " ms");
            sum[0] += took;
            sb.append("\n");
        }
    }
}
