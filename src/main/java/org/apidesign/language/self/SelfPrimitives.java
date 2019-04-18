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
import java.util.Objects;
import java.util.Optional;
import static org.apidesign.language.self.SelfObject.findWrappedValue;

final class SelfPrimitives {
    private final SelfLanguage lang;
    private final SelfObject TRUE;
    private final SelfObject FALSE;
    private final SelfObject NUMBERS;
    private final SelfObject TEXTS;

    SelfPrimitives(SelfLanguage lang) {
        this.lang = lang;
        this.TRUE = SelfObject.newBuilder().
            wrapper(Boolean.TRUE).
            slot("not", SelfObject.newBuilder().code(lang, (self, __) -> valueOf(false)).build()).
            slot("ifTrue:False:", SelfObject.newBuilder().argument(":t").argument(":f").code(lang, (self, args) -> {
                return evalBlock(self, (SelfObject) self.get("t"));
            }).build()).
            build();

        this.FALSE = SelfObject.newBuilder().
            wrapper(Boolean.FALSE).
            slot("not", SelfObject.newBuilder().code(lang, (self, __) -> valueOf(true)).build()).
            slot("ifTrue:False:", SelfObject.newBuilder().argument(":t").argument(":f").code(lang, (self, args) -> {
                return evalBlock(self, (SelfObject) self.get("f"));
            }).build()).
            build();

        this.NUMBERS = SelfObject.newBuilder().
            slot("+", SelfObject.newBuilder().argument(":b").code(lang, (self, arg) -> {
                Optional<Object> valueArg = findWrappedValue(self.get("b"));
                Optional<Object> valueNum = findWrappedValue(self);
                if (valueArg.isPresent() && valueNum.isPresent()) {
                    if (valueArg.get() instanceof Number && valueNum.get() instanceof Number) {
                        int res = ((Number) valueNum.get()).intValue() + ((Number) valueArg.get()).intValue();
                        return valueOf(res);
                    }
                }
                return valueOf(self.toString() + Objects.toString(arg[0]));
            }).build()).
            slot("-", SelfObject.newBuilder().argument(":b").code(lang, (self, arg) -> {
                Optional<Object> valueArg = findWrappedValue(self.get("b"));
                Optional<Object> valueNum = findWrappedValue(self);
                if (valueArg.isPresent() && valueNum.isPresent()) {
                    if (valueArg.get() instanceof Number && valueNum.get() instanceof Number) {
                        int res = ((Number) valueNum.get()).intValue() - ((Number) valueArg.get()).intValue();
                        return valueOf(res);
                    }
                }
                throw new IllegalStateException(valueArg + " " + valueNum);
            }).build()).
            slot("<", SelfObject.newBuilder().argument(":b").code(lang, (self, arg) -> {
                Optional<Object> valueArg = findWrappedValue(self.get("b"));
                Optional<Object> valueNum = findWrappedValue(self);
                if (valueArg.isPresent() && valueNum.isPresent()) {
                    if (valueArg.get() instanceof Number && valueNum.get() instanceof Number) {
                        boolean res = ((Number) valueNum.get()).intValue() < ((Number) valueArg.get()).intValue();
                        return valueOf(res);
                    }
                }
                return valueOf(false);
            }).build()).
            build();

        this.TEXTS = SelfObject.newBuilder().build();
    }

    SelfObject valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    private SelfObject evalBlock(SelfObject self, SelfObject block, Object... args) {
        CallTarget code = block.blockCode();
        SelfObject res;
        if (code != null) {
            res = (SelfObject) code.call(block, args);
        } else {
            res = block;
        }
        return res;
    }

    SelfObject valueOf(int number) {
        return SelfObject.newBuilder().wrapper(number).parent(NUMBERS).build();
    }

    SelfObject valueOf(String text) {
        return SelfObject.newBuilder().parent(TEXTS).wrapper(text).build();
    }

}
