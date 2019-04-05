/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import static org.apidesign.language.self.Alternative.error;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenId;

abstract class Element<T> extends Node {

    @CompilationFinal protected long firstA;
    @CompilationFinal protected long firstB;
    @CompilationFinal protected int singleToken = -1;

    protected abstract void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded);

    public abstract void initialize();

    public abstract T consume(PELexer lexer);

    public final boolean canStartWith(Token<? extends TokenId> token) {
        if (token == null) {
            // eof
            return false;
        }
        int id = token.id().ordinal();
        if (singleToken == -1L) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (Long.bitCount(firstA) + Long.bitCount(firstB) == 1) {
                // if the "first" set consists of a single token, it can be checked more efficiently
                if (firstA == 0) {
                    singleToken = Long.numberOfTrailingZeros(firstB) + 64;
                } else {
                    singleToken = Long.numberOfTrailingZeros(firstA);
                }
            } else {
                singleToken = 0;
            }
        }
        if (singleToken != 0) {
            return id == singleToken;
        }

        if (id < 64) {
            assert id > 0;
            return (firstA & (1L << id)) != 0;
        } else {
            assert id < 128;
            return (firstB & (1L << (id - 64))) != 0;
        }
    }
}

class RuleRootNode extends RootNode {

    @Child private Rule<?> rule;

    RuleRootNode(Rule<?> rule) {
        super(null);
        this.rule = rule;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        PELexer lexer = (PELexer) frame.getArguments()[0];
        return rule.element.consume(lexer);
    }

    @Override
    public String getName() {
        return "parser rule " + rule.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}

final class CallRule<T> extends Element<T> {

    private final Rule<T> rule;
    @Child DirectCallNode call;

    CallRule(Rule<T> rule) {
        this.rule = rule;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        rule.element.createFirstSet(setHolder, rulesAdded);
    }

    @Override
    public void initialize() {
        rule.initialize();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T consume(PELexer lexer) {
        if (call == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            call = insert(Truffle.getRuntime().createDirectCallNode(rule.getCallTarget()));
        }
        if (PEParser.PEPARSER_DIRECT_CALL) {
            return rule.element.consume(lexer);
        } else {
            return (T) call.call(lexer.asArgumentsArray()); // do not create a new array every time
        }
    }

    @Override
    public String toString() {
        return "CallRule[" + rule.getName() + "]";
    }
}

final class Rule<T> extends Element<T> {

    private final String name;
    @Child Element<? extends T> element;
    CallTarget target;

    Rule(String name) {
        this.name = name;
    }

    public CallTarget getCallTarget() {
        if (target == null) {
            target = Truffle.getRuntime().createCallTarget(new RuleRootNode(this));
        }
        return target;
    }

    public void define(Element<? extends T> newElement) {
        this.element = newElement;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        if (!rulesAdded.contains(this)) {
            rulesAdded.add(this);
            if (firstA != 0 || firstB != 0) {
                setHolder.firstA |= firstA;
                setHolder.firstB |= firstB;
            } else {
                if (element != null) {
                    element.createFirstSet(setHolder, rulesAdded);
                }
            }
        }
    }

    void initializeRule() {
        CompilerAsserts.neverPartOfCompilation();
        createFirstSet(this, new HashSet<>());
    }

    @Override
    public void initialize() {
        // do nothing - already initialized
    }

    public String getName() {
        return name;
    }

    static int level = 0;

    @Override
    public T consume(PELexer lexer) {
        throw new IllegalStateException(getRootNode().getName());
    }
}

abstract class SequenceBase<T> extends Element<T> {

    protected abstract Element<?>[] elements();

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        int i = 0;
        Element<?>[] elements = elements();
        while (i < elements.length && elements[i] instanceof OptionalElement<?, ?>) {
            // add all optional prefixes
            ((OptionalElement<?, ?>) elements[i]).element.createFirstSet(setHolder, rulesAdded);
            i++;
        }
        assert i < elements.length : "non-optional element needed in sequence";
        // add the first non-optional element
        elements[i].createFirstSet(setHolder, rulesAdded);
    }

    @Override
    public void initialize() {
        for (Element<?> element : elements()) {
            element.initialize();
        }
    }
}

final class Sequence1<T, A> extends SequenceBase<T> {
    @Child private Element<A> a;
    private final Function<? super A, T> action;

    Sequence1(Function<? super A, T> action, Element<A> a) {
        this.action = action;
        this.a = a;
    }

    @Override
    protected Element<?>[] elements() {
        return new Element<?>[]{a};
    }

    @Override
    public T consume(PELexer lexer) {
        final A valueA = a.consume(lexer);
        return action.apply(valueA);
    }
}

final class Sequence2<T, A, B> extends SequenceBase<T> {
    @Child private Element<A> a;
    @Child private Element<B> b;
    private final BiFunction<? super A, ? super B, T> action;

    Sequence2(BiFunction<? super A, ? super B, T> action, Element<A> a, Element<B> b) {
        this.action = action;
        this.a = a;
        this.b = b;
    }

    @Override
    protected Element<?>[] elements() {
        return new Element<?>[]{a, b};
    }

    @Override
    public T consume(PELexer lexer) {
        final A valueA = a.consume(lexer);
        final B valueB = b.consume(lexer);
        return action.apply(valueA, valueB);
    }
}

final class Sequence3<T, A, B, C> extends SequenceBase<T> {
    @Child private Element<A> a;
    @Child private Element<B> b;
    @Child private Element<C> c;
    private final PEParser.Function3<? super A, ? super B, ? super C, T> action;

    Sequence3(PEParser.Function3<? super A, ? super B, ? super C, T> action, Element<A> a, Element<B> b, Element<C> c) {
        this.action = action;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    protected Element<?>[] elements() {
        return new Element<?>[]{a, b, c};
    }

    @Override
    public T consume(PELexer lexer) {
        final A valueA = a.consume(lexer);
        final B valueB = b.consume(lexer);
        final C valueC = c.consume(lexer);
        return action.apply(valueA, valueB, valueC);
    }
}

final class Sequence4<T, A, B, C, D> extends SequenceBase<T> {
    @Child private Element<A> a;
    @Child private Element<B> b;
    @Child private Element<C> c;
    @Child private Element<D> d;
    private final PEParser.Function4<? super A, ? super B, ? super C, ? super D, T> action;

    Sequence4(PEParser.Function4<? super A, ? super B, ? super C, ? super D, T> action, Element<A> a, Element<B> b, Element<C> c, Element<D> d) {
        this.action = action;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    @Override
    protected Element<?>[] elements() {
        return new Element<?>[]{a, b, c};
    }

    @Override
    public T consume(PELexer lexer) {
        final A valueA = a.consume(lexer);
        final B valueB = b.consume(lexer);
        final C valueC = c.consume(lexer);
        final D valueD = d.consume(lexer);
        return action.apply(valueA, valueB, valueC, valueD);
    }
}

final class Alternative<T> extends Element<T> {
    @Children private final Element<? extends T>[] options;
    private final ConditionProfile seenEof = ConditionProfile.createBinaryProfile();

    Alternative(Element<? extends T>[] options) {
        this.options = options;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        assert options.length > 0;

        for (Element<?> option : options) {
            option.createFirstSet(setHolder, rulesAdded);
        }
    }

    @Override
    public void initialize() {
        for (Element<?> element : options) {
            element.createFirstSet(element, new HashSet<>());
            element.initialize();
        }
    }

    @Override
    @ExplodeLoop(kind = LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN)
    public T consume(PELexer lexer) {
        Token<? extends TokenId> lookahead = lexer.peek(seenEof);
        for (Element<? extends T> element : options) {
            if (element.canStartWith(lookahead)) {
                // matched
                return element.consume(lexer);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw error("no alternative found at " + lexer.position() + " in " + getRootNode().getName());
    }
    
    static RuntimeException error(String message) {
        CompilerAsserts.neverPartOfCompilation();
        throw new RuntimeException(message);
    }
}

final class Repetition<T, ListT, R> extends Element<R> {
    @Child private Element<T> element;
    private final Supplier<ListT> createList;
    private final BiFunction<ListT, T, ListT> addToList;
    private final Function<ListT, R> createResult;
    private final ConditionProfile seenEof = ConditionProfile.createBinaryProfile();

    Repetition(Element<T> element, Supplier<ListT> createList, BiFunction<ListT, T, ListT> addToList, Function<ListT, R> createResult) {
        this.element = element;
        this.createList = createList;
        this.addToList = addToList;
        this.createResult = createResult;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        throw new IllegalStateException("should not reach here");
    }

    @Override
    public void initialize() {
        element.createFirstSet(element, new HashSet<>());
        element.initialize();
    }

    @Override
    public R consume(PELexer lexer) {
        ListT list = createList.get();
        while (true) {
            Token<? extends TokenId> lookahead = lexer.peek(seenEof);
            if (!element.canStartWith(lookahead)) {
                return createResult.apply(list);
            }
            list = addToList.apply(list, element.consume(lexer));
        }
    }
}

final class OptionalElement<T, R> extends Element<R> {
    @Child Element<T> element;
    private final Function<T, R> hasValueAction;
    private final Supplier<R> hasNoValueAction;
    private final ConditionProfile seenEof = ConditionProfile.createBinaryProfile();

    OptionalElement(Element<T> element, Function<T, R> hasValueAction, Supplier<R> hasNoValueAction) {
        this.element = element;
        this.hasValueAction = hasValueAction;
        this.hasNoValueAction = hasNoValueAction;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        throw new IllegalStateException("should not reach here");
    }

    @Override
    public void initialize() {
        element.createFirstSet(element, new HashSet<>());
        element.initialize();
    }

    @Override
    public R consume(PELexer lexer) {
        Token<? extends TokenId> lookahead = lexer.peek(seenEof);
        if (element.canStartWith(lookahead)) {
            return hasValueAction.apply(element.consume(lexer));
        }
        return hasNoValueAction.get();
    }
}

final class TokenReference<TID extends TokenId, T> extends Element<T> {
    private final TID token;
    private final PEParser.TokenFunction<TID, T> action;
    private final ConditionProfile seenEof = ConditionProfile.createBinaryProfile();

    TokenReference(TID token, PEParser.TokenFunction<TID, T> action) {
        this.token = token;
        this.action = action;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        int id = token.ordinal();
        if (id < 64) {
            assert id > 0;
            setHolder.firstA |= 1L << id;
        } else {
            assert id < 128;
            setHolder.firstB |= 1L << (id - 64);
        }
    }

    @Override
    public void initialize() {
        // nothing to do
    }

    @Override
    public T consume(PELexer lexer) {
        Token<? extends TokenId> tokenId = lexer.peek(seenEof);
        Token<? extends TokenId> actualToken = lexer.nextToken(seenEof);
        if (actualToken == null || actualToken.id() != token) {
            CompilerDirectives.transferToInterpreter();
            error("expecting " + lexer.tokenNames(token) + ", got " + lexer.tokenNames(actualToken) + " at " + lexer.position());
        }
        return action.apply((Token<TID>) tokenId);
    }
}

@SuppressWarnings("unchecked")
public final class PEParser {

    static final boolean PEPARSER_DIRECT_CALL = Boolean.getBoolean("PEParser.directcall");

    private final ArrayList<Rule<?>> rules = new ArrayList<>();
    @CompilationFinal private Rule<?> root;

    private static <T> void replaceRules(Element<? extends T>[] elements) {
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof Rule) {
                elements[i] = new CallRule<>((Rule<T>) elements[i]);
            }
        }
    }

    private static <T> Element<T> replaceRule(Element<T> element) {
        if (element instanceof Rule<?>) {
            return new CallRule<>((Rule<T>) element);
        } else {
            return element;
        }
    }

    public static <T> Element<T> alt(Element<? extends T>... options) {
        replaceRules(options);
        return new Alternative<>(options);
    }

    public static <A, R> Element<R> seq(Element<A> a, Function<? super A, R> action) {
        return new Sequence1<>(action, replaceRule(a));
    }

    public static <A, B, R> Element<R> seq(Element<A> a, Element<B> b, BiFunction<? super A, ? super B, R> action) {
        return new Sequence2<>(action, replaceRule(a), replaceRule(b));
    }

    public static <A, B, C, R> Element<R> seq(Element<A> a, Element<B> b, Element<C> c, Function3<? super A, ? super B, ? super C, R> action) {
        return new Sequence3<>(action, replaceRule(a), replaceRule(b), replaceRule(c));
    }

    public static <A, B, C, D, R> Element<R> seq(Element<A> a, Element<B> b, Element<C> c, Element<D> d, Function4<? super A, ? super B, ? super C, ? super D, R> action) {
        return new Sequence4<>(action, replaceRule(a), replaceRule(b), replaceRule(c), replaceRule(d));
    }

    public static <T, ListT, R> Element<R> rep(Element<T> element, Supplier<ListT> createList, BiFunction<ListT, T, ListT> addToList, Function<ListT, R> createResult) {
        return new Repetition<>(replaceRule(element), createList, addToList, createResult);
    }

    public static <T> Element<Optional<T>> opt(Element<T> element) {
        return new OptionalElement<>(replaceRule(element), v -> Optional.ofNullable(v), () -> Optional.empty());
    }

    public static <T extends TokenId> Element<Token<T>> ref(T id) {
        return ref(id, (t) -> t);
    }

    public static <T extends TokenId, R> Element<R> ref(T id, TokenFunction<T, R> action) {
        return new TokenReference<>(id, action);
    }

    public <T> Rule<T> rule(String name) {
        Rule<T> rule = new Rule<>(name);
        rules.add(rule);
        return rule;
    }

    public interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    public interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    public interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    public interface TokenFunction<T extends TokenId, R> {
        R apply(Token<T> token);
    }

    PEParser() {
        // private constructor
    }

    final void initialize(Rule<?> newRoot) {
        this.root = newRoot;
        for (Rule<?> rule : rules) {
            rule.initializeRule();
        }
        for (Rule<?> rule : rules) {
            if (rule.element != null) {
                rule.element.initialize();
            }
        }

    }

    Object parse(PELexer lexer) {
        return root.getCallTarget().call(lexer.asArgumentsArray());
    }
}
