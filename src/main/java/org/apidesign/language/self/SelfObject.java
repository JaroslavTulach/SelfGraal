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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

class SelfObject implements Cloneable, TruffleObject {
    private final boolean block;
    private final Map<String, Object> slots;
    private final CallTarget code;
    private final SelfObject parent;

    private SelfObject(Map<String, Object> slots, CallTarget code, SelfObject parent, boolean block) {
        this.slots = slots;
        this.code = code;
        this.parent = parent;
        this.block = block;
    }

    Object get(String name) {
        Object v = slots == null ? null : slots.get(name);
        if (v == null && parent != null) {
            v = parent.get(name);
        }
        return v;
    }

    static Builder newBuilder() {
        return new Builder();
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static Builder newBuilder(SelfObject toCopy) {
        Builder b = new Builder();
        b.code(toCopy.code);
        if (toCopy.slots != null) {
            b.slots = new LinkedHashMap<>(toCopy.slots);
        }
        if (toCopy instanceof Wrapper) {
            b.wrapper(((Wrapper) toCopy).value);
        }
        return b;
    }

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof SelfObject;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return SelfInteropForeign.ACCESS;
    }

    CallTarget blockCode() {
        return block ? code : null;
    }

    SelfObject evalSelf(SelfObject self, Object[] values) {
        if (code == null) {
            return this;
        } else {
            return (SelfObject) code.call(this, self, values);
        }
    }

    @CompilerDirectives.TruffleBoundary
    final SelfObject cloneWithArgs(SelfObject parent, Object[] args) {
        assert code != null;
        Map<String, Object> slotsClone;
        if (slots == null) {
            slotsClone = null;
        } else {
            slotsClone = new LinkedHashMap<>();
            int index = 0;
            for (Map.Entry<String, Object> e : slots.entrySet()) {
                if (e.getKey().startsWith(":")) {
                    slotsClone.put(e.getKey().substring(1), args[index++]);
                } else {
                    slotsClone.put(e.getKey(), e.getValue());
                }
            }
            assert index == args.length : "Slots " + slotsClone + " args: " + Arrays.toString(args);
        }
        return new SelfObject(slotsClone, code, parent, block);
    }

    static final class Builder {
        private Map<String, Object> slots;
        private CallTarget code;
        private boolean block;
        private Object wrapper;
        private SelfObject parent;

        Builder code(CallTarget expr) {
            code = expr;
            return this;
        }

        Builder code(SelfLanguage lang, BiFunction<SelfObject, Object[], SelfObject> fn) {
            code = SelfCode.toCallTarget(lang, SelfCode.compute(fn));
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

        Builder parent(SelfObject parent) {
            this.parent = parent;
            return this;
        }

        SelfObject build() {
            if (wrapper != null) {
                return new Wrapper(parent, slots, wrapper);
            }
            return new SelfObject(slots, code, parent, block);
        }

        private Map<String,Object> slots() {
            if (slots == null) {
                slots = new LinkedHashMap<>();
            }
            return slots;
        }

        Builder wrapper(Object obj) {
            this.wrapper = obj;
            return this;
        }

        Builder block(boolean b) {
            this.block = b;
            return this;
        }
    }

    private static final class Wrapper<T> extends SelfObject {
        private final T value;

        Wrapper(SelfObject parent, Map<String, Object> slots, T value) {
            super(slots, null, parent, false);
            this.value = value;
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    static Optional<Object> findWrappedValue(Object obj) {
        while (obj instanceof SelfObject) {
            if (obj instanceof Wrapper) {
                return Optional.of(((Wrapper) obj).value);
            }
            obj = ((SelfObject) obj).parent;
        }
        return Optional.empty();
    }
}
