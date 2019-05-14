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
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.function.BiFunction;

abstract class SelfCode extends Node {

    abstract SelfObject executeMessage(SelfObject self, Object... args);

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode constant(SelfObject obj) {
        return new Constant(obj);
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
    static SelfCode unaryMessage(SelfCode receiver, SelfSelector message) {
        return new Message(receiver, message);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode binaryMessage(SelfCode receiver, SelfSelector message, SelfCode arg) {
        return new Message(receiver, message, arg);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode keywordMessage(SelfCode receiver, SelfSelector selector, SelfCode... args) {
        return new Message(receiver, selector, args);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    static SelfCode compute(BiFunction<SelfObject, Object[], SelfObject> fn) {
        return new Compute(fn);
    }

    private static class Constant extends SelfCode {
        private final SelfObject obj;

        Constant(SelfObject obj) {
            this.obj = obj;
        }

        @Override
        SelfObject executeMessage(SelfObject self, Object... args) {
            return obj.evalSelf(self, args);
        }

        @Override
        public String toString() {
            return "[Constant=" + obj + "]";
        }
    }

    private static class Self extends SelfCode {
        @Override
        SelfObject executeMessage(SelfObject self, Object... args) {
            return self;
        }
    }

    private static class Message extends SelfCode {
        @Child
        private SelfCode receiver;
        @Children
        private SelfCode[] args;
        private final SelfSelector message;

        Message(SelfCode receiver, SelfSelector message, SelfCode... args) {
            this.receiver = receiver;
            this.message = message;
            this.args = args;
        }

        @ExplodeLoop
        @Override
        SelfObject executeMessage(SelfObject self, Object... myArgs) {
            SelfObject obj = receiver.executeMessage(self);
            SelfObject[] values = new SelfObject[args.length];
            for (int i = 0; i < args.length; i++) {
                values[i] = args[i].executeMessage(self, myArgs);
            }
            final SelfObject msg = (SelfObject) obj.get(message.toString());
            if (msg == null) {
                throw UnknownIdentifierException.raise(message.toString());
            }
            return msg.evalSelf(obj, values);
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
        SelfObject executeMessage(SelfObject self, Object... args) {
            SelfObject res = self;
            for (int i = 0; i < children.length; i++) {
                res = children[i].executeMessage(self);
            }
            return res;
        }
    }

    private static class Compute extends SelfCode {
        private final BiFunction<SelfObject, Object[], SelfObject> fn;

        Compute(BiFunction<SelfObject, Object[], SelfObject> fn) {
            this.fn = fn;
        }

        @Override
        SelfObject executeMessage(SelfObject self, Object... args) {
            return fn.apply(self, null);
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
        SelfObject executeMessage(SelfObject self, Object... args) {
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
    }

    static CallTarget toCallTarget(final SelfLanguage l, SelfCode code) {
        RootNode root = new SelfCode.Root(l, code);
        return Truffle.getRuntime().createCallTarget(root);
    }


    static final class Root extends RootNode {
        private final SelfCode code;

        private Root(SelfLanguage language, SelfCode code) {
            super(language);
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
            SelfObject result = code.executeMessage(methodActivation, values);
            return result;
        }

    }
}
