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

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@MessageResolution(receiverType = SelfObject.class)
class SelfObject implements Cloneable, TruffleObject {
    private final Map<String, Object> slots;
    private final SelfCode code;

    private SelfObject(Map<String, Object> slots, SelfCode code) {
        this.slots = slots;
        this.code = code;
    }

    final Object get(String name) {
        return slots == null ? null : slots.get(name);
    }

    private static final SelfObject TRUE = SelfObject.newBuilder().
        wrapper(Boolean.TRUE).
        slot("not", SelfObject.newBuilder().code((self) -> valueOf(false)).build()).
        build();

    private static final SelfObject FALSE = SelfObject.newBuilder().
        wrapper(Boolean.FALSE).
        slot("not", SelfObject.newBuilder().code((self) -> valueOf(true)).build()).
        build();

    static SelfObject valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    private static final SelfObject NUMBERS = SelfObject.newBuilder().build();
    static SelfObject valueOf(int number) {
        return new Wrapper<>(NUMBERS, null, number);
    }

    private static final SelfObject TEXTS = SelfObject.newBuilder().build();
    static SelfObject valueOf(String text) {
        return new Wrapper<>(TEXTS, null, text);
    }

    static Builder newBuilder() {
        return new Builder();
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof SelfObject;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return SelfObjectForeign.ACCESS;
    }

    SelfObject evalSelf(SelfObject self) {
        if (code == null) {
            return this;
        } else {
            return code.sendMessage(self);
        }
    }

    @Resolve(message = "UNBOX")
    static abstract class Unbox extends Node {
        Object access(SelfObject.Wrapper<?> obj) {
            return obj.value;
        }
    }

    static final class Builder {
        private Map<String, Object> slots;
        private SelfCode code;
        private Object wrapper;

        Builder code(SelfCode expr) {
            code = expr;
            return this;
        }

        Builder code(Function<SelfObject, SelfObject> fn) {
            code = SelfCode.compute(fn);
            return this;
        }

        Builder argument(String name) {
            slots().put(name, "");
            return this;
        }

        Builder slot(String name, Object value) {
            slots().put(name, value);
            return this;
        }

        SelfObject build() {
            if (wrapper != null) {
                return new Wrapper(null, slots, wrapper);
            }
            return new SelfObject(slots, code);
        }

        private Map<String,Object> slots() {
            if (slots == null) {
                slots = new HashMap<>();
            }
            return slots;
        }

        private Builder wrapper(Object obj) {
            this.wrapper = obj;
            return this;
        }
    }

    static final class Wrapper<T> extends SelfObject {
        private final SelfObject parent;
        private final T value;

        public Wrapper(SelfObject parent, Map<String, Object> slots, T value) {
            super(slots, null);
            this.parent = parent;
            this.value = value;
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
