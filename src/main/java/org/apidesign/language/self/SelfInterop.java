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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = SelfObject.class)
final class SelfInterop {
    @Resolve(message = "UNBOX")
    static abstract class Unbox extends Node {

        Object access(SelfObject obj) {
            return SelfObject.findWrappedValue(obj).get();
        }
    }

    @Resolve(message = "INVOKE")
    static abstract class Invoke extends Node {
        private static final int SIZE = 5;
        @CompilerDirectives.CompilationFinal(dimensions = 1)
        private final String[] selectors = new String[SIZE];
        @Children
        private final SelfCode[] messages = new SelfCode[SIZE];

        @ExplodeLoop
        Object access(VirtualFrame frame, SelfObject obj, String member, Object... args) {
            for (int i = 0; i < selectors.length; i++) {
                if (selectors[i] == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    messages[i] = insert(newMessageHandler(member, args.length));
                    selectors[i] = member;
                }
                if (selectors[i].equals(member)) {
                    return messages[i].sendMessage(obj, args);
                }
            }
            return handleTooManyMessages(obj, member, args);
        }

        @CompilerDirectives.TruffleBoundary
        private SelfObject handleTooManyMessages(SelfObject obj, String member, Object[] args) {
            return newMessageHandler(member, args.length).sendMessage(obj, args);
        }

        private static SelfCode newMessageHandler(String message, int arity) {
            CompilerAsserts.neverPartOfCompilation();
            SelfSelector selector = SelfSelector.keyword(message);
            SelfCode receiver = SelfCode.self();
            SelfCode[] values = new SelfCode[arity];
            for (int i = 0; i < arity; i++) {
                values[i] = SelfCode.convertArgument(i);
            }
            final SelfCode msg = SelfCode.keywordMessage(receiver, selector, values);
            return msg;
        }
    }
}