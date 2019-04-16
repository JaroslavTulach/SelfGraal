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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

@MessageResolution(receiverType = SelfObject.class)
class SelfObject implements Cloneable, TruffleObject {
    private final boolean block;
    private final Map<String, Object> slots;
    private final SelfCode code;
    private final SelfObject parent;

    private SelfObject(Map<String, Object> slots, SelfCode code, SelfObject parent, boolean block) {
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

    private static final SelfObject TRUE = SelfObject.newBuilder().
        wrapper(Boolean.TRUE).
        slot("not", SelfObject.newBuilder().code((self, __) -> valueOf(false)).build()).
        slot("ifTrue:False:", SelfObject.newBuilder().code((self, args) -> {
            return evalBlock(self, (SelfObject) args[0]);
        }).build()).
        build();

    private static final SelfObject FALSE = SelfObject.newBuilder().
        wrapper(Boolean.FALSE).
        slot("not", SelfObject.newBuilder().code((self, __) -> valueOf(true)).build()).
        slot("ifTrue:False:", SelfObject.newBuilder().code((self, args) -> {
            return evalBlock(self, (SelfObject) args[1]);
        }).build()).
        build();

    static SelfObject valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    private static SelfObject evalBlock(SelfObject self, SelfObject block, Object... args) {
        SelfObject res;
        if (block.block && block.code != null) {
            res = block.code.sendMessage(block, args);
        } else {
            res = block;
        }
        return res;
    }

    private static final SelfObject NUMBERS = SelfObject.newBuilder().
        slot("+", SelfObject.newBuilder().code((self, arg) -> {
            Optional<Object> valueArg = findWrappedValue(arg[0]);
            Optional<Object> valueNum = findWrappedValue(self);
            if (valueArg.isPresent() && valueNum.isPresent()) {
                if (valueArg.get() instanceof Number && valueNum.get() instanceof Number) {
                    int res = ((Number)valueNum.get()).intValue() + ((Number)valueArg.get()).intValue();
                    return SelfObject.valueOf(res);
                }
            }
            return SelfObject.valueOf(self.toString() + Objects.toString(arg[0]));
        }).build()).
        slot("-", SelfObject.newBuilder().code((self, arg) -> {
            Optional<Object> valueArg = findWrappedValue(arg[0]);
            Optional<Object> valueNum = findWrappedValue(self);
            if (valueArg.isPresent() && valueNum.isPresent()) {
                if (valueArg.get() instanceof Number && valueNum.get() instanceof Number) {
                    int res = ((Number)valueNum.get()).intValue() - ((Number)valueArg.get()).intValue();
                    return SelfObject.valueOf(res);
                }
            }
            throw new IllegalStateException(valueArg + " " + valueNum);
        }).build()).
        slot("<", SelfObject.newBuilder().code((self, arg) -> {
            Optional<Object> valueArg = findWrappedValue(arg[0]);
            Optional<Object> valueNum = findWrappedValue(self);
            if (valueArg.isPresent() && valueNum.isPresent()) {
                if (valueArg.get() instanceof Number && valueNum.get() instanceof Number) {
                    boolean res = ((Number)valueNum.get()).intValue() < ((Number)valueArg.get()).intValue();
                    return SelfObject.valueOf(res);
                }
            }
            return SelfObject.valueOf(false);
        }).build()).
        build();
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
        return SelfObjectForeign.ACCESS;
    }

    SelfObject evalSelf(SelfObject self, Object[] values) {
        if (code == null) {
            return this;
        } else {
            SelfObject methodActivation = cloneWithArgs(self, values);
            if (methodActivation.block) {
                return methodActivation;
            }
            return code.sendMessage(methodActivation, values);
        }
    }

    private SelfObject cloneWithArgs(SelfObject parent, Object[] args) {
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

    @Resolve(message = "UNBOX")
    static abstract class Unbox extends Node {
        Object access(SelfObject obj) {
            return findWrappedValue(obj).get();
        }
    }

    @Resolve(message = "INVOKE")
    static abstract class Invoke extends Node {
        SelfCode message;

        Object access(VirtualFrame frame, SelfObject obj, String member, Object... args) {
            if (message == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                SelfSelector selector = SelfSelector.keyword(member);
                SelfCode receiver = SelfCode.constant(obj);
                SelfCode[] values = new SelfCode[args.length];
                for (int i = 0; i < args.length; i++) {
                    values[i] = SelfCode.convertArgument(i);
                }
                final SelfCode msg = SelfCode.keywordMessage(receiver, selector, values);
                message = insert(msg);
            }
            return message.sendMessage(obj, args);
        }
    }

    static final class Builder {
        private Map<String, Object> slots;
        private SelfCode code;
        private boolean block;
        private Object wrapper;

        Builder code(SelfCode expr) {
            code = expr;
            return this;
        }

        Builder code(BiFunction<SelfObject, Object[], SelfObject> fn) {
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
            return new SelfObject(slots, code, null, block);
        }

        private Map<String,Object> slots() {
            if (slots == null) {
                slots = new LinkedHashMap<>();
            }
            return slots;
        }

        private Builder wrapper(Object obj) {
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

    private static Optional<Object> findWrappedValue(Object obj) {
        while (obj instanceof SelfObject) {
            if (obj instanceof Wrapper) {
                return Optional.of(((Wrapper) obj).value);
            }
            obj = ((SelfObject) obj).parent;
        }
        return Optional.empty();
    }
}
