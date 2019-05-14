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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.function.BiFunction;

@GenerateWrapper
abstract class SelfCode extends Node implements InstrumentableNode {
    abstract SelfObject executeMessage(VirtualFrame frame, SelfObject self, Object... args);
    abstract int offset();
    abstract int length();

    @Override
    public boolean isInstrumentable() {
        return true;
    }

    @Override
    public SourceSection getSourceSection() {
        final RootNode rn = getRootNode();
        Source src;
        if (rn instanceof SelfSource) {
            src = ((SelfSource)rn).source;
        } else if (rn instanceof SelfCode.Root) {
            src = ((SelfCode.Root)rn).source;
        } else {
            src = null;
        }
        return src == null ? null : src.createSection(offset(), length());
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new SelfCodeWrapper(this, probe);
    }
    
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode constant(int offset, int length, SelfObject obj) {
        return new Constant(offset, length, obj);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode convertArgument(SelfPrimitives primitives, int arg) {
        return new Convert(primitives, arg);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode self() {
        return new Self();
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode block(SelfCode... children) {
        return new Block(children);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode unaryMessage(int offset, int length, SelfCode receiver, SelfSelector message) {
        return new Message(offset, length, receiver, message);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode binaryMessage(int offset, int length, SelfCode receiver, SelfSelector message, SelfCode arg) {
        return new Message(offset, length, receiver, message, arg);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode keywordMessage(int offset, int length, SelfCode receiver, SelfSelector selector, SelfCode... args) {
        return new Message(offset, length, receiver, selector, args);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode compute(BiFunction<SelfObject, Object[], SelfObject> fn) {
        return new Compute(fn);
    }

    private static class Constant extends SelfCode {
        private final SelfObject obj;
        private final int offset;
        private final int length;

        Constant(int offset, int length, SelfObject obj) {
            this.offset = offset;
            this.length = length;
            this.obj = obj;
        }

        @Override
        SelfObject executeMessage(VirtualFrame frame, SelfObject self, Object... args) {
            return obj.evalSelf(self, args);
        }

        @Override
        public String toString() {
            return "[Constant=" + obj + "]";
        }

        @Override
        int offset() {
            return offset;
        }

        @Override
        int length() {
            return length;
        }
    }

    private static class Self extends SelfCode {
        @Override
        SelfObject executeMessage(VirtualFrame frame, SelfObject self, Object... args) {
            return self;
        }

        @Override
        int offset() {
            return 0;
        }

        @Override
        int length() {
            return 0;
        }
    }

    private static class Message extends SelfCode {
        @Child
        private SelfCode receiver;
        @Children
        private SelfCode[] args;
        private final SelfSelector message;
        private final int length;
        private final int offset;

        Message(int offset, int length, SelfCode receiver, SelfSelector message, SelfCode... args) {
            this.offset = offset;
            this.length = length;
            this.receiver = receiver;
            this.message = message;
            this.args = args;
        }

        @ExplodeLoop
        @Override
        SelfObject executeMessage(VirtualFrame frame, SelfObject self, Object... myArgs) {
            SelfObject obj = receiver.executeMessage(frame, self);
            SelfObject[] values = new SelfObject[args.length];
            for (int i = 0; i < args.length; i++) {
                values[i] = args[i].executeMessage(frame, self, myArgs);
            }
            final SelfObject msg = (SelfObject) obj.get(message.toString());
            if (msg == null) {
                throw UnknownIdentifierException.raise(message.toString());
            }
            return msg.evalSelf(obj, values);
        }

        @Override
        int offset() {
            return offset;
        }

        @Override
        int length() {
            return length;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == StandardTags.StatementTag.class;
        }
    }

    private static class Block extends SelfCode {
        @Children
        private SelfCode[] children;

        Block(SelfCode[] children) {
            this.children = children;
        }
        
        

        @ExplodeLoop
        @Override
        SelfObject executeMessage(VirtualFrame frame, SelfObject self, Object... args) {
            SelfObject res = self;
            for (int i = 0; i < children.length; i++) {
                res = children[i].executeMessage(frame, self);
            }
            return res;
        }

        @Override
        int offset() {
            return 0;
        }

        @Override
        int length() {
            return 0;
        }
    }

    private static class Compute extends SelfCode {
        private final BiFunction<SelfObject, Object[], SelfObject> fn;

        Compute(BiFunction<SelfObject, Object[], SelfObject> fn) {
            this.fn = fn;
        }

        @Override
        SelfObject executeMessage(VirtualFrame frame, SelfObject self, Object... args) {
            return fn.apply(self, null);
        }

        @Override
        int offset() {
            return 0;
        }

        @Override
        int length() {
            return 0;
        }
    }

    private static class Convert extends SelfCode {
        private final int index;
        private final SelfPrimitives primitives;

        Convert(SelfPrimitives primitives, int index) {
            this.index = index;
            this.primitives = primitives;
        }

        @Override
        SelfObject executeMessage(VirtualFrame frame, SelfObject self, Object... args) {
            Object value = args[index];
            if (value instanceof Number) {
                return primitives.valueOf(((Number) value).intValue());
            } else if (value instanceof Boolean) {
                return primitives.valueOf((Boolean) value);
            } else if (value instanceof String) {
                return primitives.valueOf((String) value);
            }
            return (SelfObject) value;
        }

        @Override
        int offset() {
            return 0;
        }

        @Override
        int length() {
            return 0;
        }
    }

    static CallTarget toCallTarget(final SelfLanguage l, Source src, SelfCode code) {
        RootNode root = new SelfCode.Root(l, src, code);
        return Truffle.getRuntime().createCallTarget(root);
    }


    static final class Root extends RootNode implements InstrumentableNode {
        @Child
        private SelfCode code;
        private final Source source;

        private Root(SelfLanguage language, Source src, SelfCode code) {
            super(language);
            this.source = src;
            this.code = code;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            SelfObject thiz = (SelfObject) frame.getArguments()[0];
            SelfObject self = (SelfObject) frame.getArguments()[1];
            Object[] values = (Object[]) frame.getArguments()[2];
            SelfObject methodActivation = thiz.cloneWithArgs(self, values);
            if (methodActivation.blockCode() != null) {
                return methodActivation;
            }
            SelfObject result = code.executeMessage(frame, methodActivation, values);
            return result;
        }

        @Override
        public boolean isInstrumentable() {
            return code.isInstrumentable();
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return null;
        }

    }
}
