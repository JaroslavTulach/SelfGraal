package org.apidesign.language.self;

import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.apidesign.language.self.PELexer.LexerList;
import static org.apidesign.language.self.PEParser.*;
import org.apidesign.language.self.SelfLexer.BasicNode;
import static org.apidesign.language.self.SelfLexer.concat;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenId;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.spi.lexer.LanguageHierarchy;
import org.netbeans.spi.lexer.Lexer;
import org.netbeans.spi.lexer.LexerInput;
import org.netbeans.spi.lexer.LexerRestartInfo;
import org.netbeans.spi.lexer.TokenFactory;

final class SelfParser {
    private static final PEParser PARSER;
    static {
        PARSER = new PEParser();
        // create the rules
        Rule<SelfLexer.BasicNode> program = PARSER.rule("program");
        Rule<SelfLexer.BasicNode> statement = PARSER.rule("statement");
        Rule<SelfLexer.BasicNode[]> exprlist = PARSER.rule("exprlist");
        Rule<SelfLexer.BasicNode[]> varlist = PARSER.rule("varlist");
        Rule<SelfLexer.BasicNode> expression = PARSER.rule("expression");
        Rule<SelfLexer.BasicNode> term = PARSER.rule("term");
        Rule<SelfLexer.BasicNode> factor = PARSER.rule("factor");
        Rule<SelfLexer.BasicNode> vara = PARSER.rule("vara");
        Rule<SelfLexer.BasicNode> string = PARSER.rule("string");
        Rule<SelfLexer.RelOp> relop = PARSER.rule("relop");

        // define the rules
        // program: line {line}
        program.define(seq(statement, rep(statement),
                (l, r) -> new SelfLexer.BasicNode("program", concat(l, r))));

        Element<SelfLexer.BasicNode> objectStatement = seq(
                ref(SelfTokenId.LPAREN), ref(SelfTokenId.RPAREN),
                (TokenId t, SelfTokenId u) -> {
                    return new SelfLexer.BasicNode("()") {
                        @Override
                        void print(Consumer<Object> registrar) {
                            registrar.accept(new Object());
                        }
                    };
                }
        );

        /*
        line.define(seq(opt(ref(NUMBER)), statement, ref(CR),
                        (n, s, c) -> s));

        Element<BasicNode> printStatement = seq(ref(PRINT), exprlist,
                        (p, e) -> new BasicNode("print", e));
        Element<BasicNode> ifCondition = seq(expression, relop, expression,
                        (a, r, b) -> new BasicNode(r.toString(), a, b));
        Element<BasicNode> ifStatement = seq(ref(IF), ifCondition, opt(ref(THEN)), statement,
                        (i, cond, t, s) -> new BasicNode("if", cond, s));
        Element<BasicNode> gotoStatement = seq(ref(GOTO), ref(NUMBER),
                        (g, n) -> new BasicNode("goto"));
        Element<BasicNode> inputStatement = seq(ref(INPUT), varlist,
                        (i, v) -> new BasicNode("input", v));
        Element<BasicNode> assignStatement = seq(opt(ref(LET)), vara, ref(EQUALS), expression,
                        (l, v, s, e) -> new BasicNode(l.isPresent() ? "let" : "assing", v, e));
        Element<BasicNode> gosubStatement = seq(ref(GOSUB), expression,
                        (g, e) -> new BasicNode("gosub", e));
        Element<BasicNode> returnStatement = ref(RETURN,
                        t -> new BasicNode("return"));
        Element<BasicNode> clearStatement = ref(CLEAR,
                        t -> new BasicNode("clear"));
        Element<BasicNode> listStatement = ref(LIST,
                        t -> new BasicNode("list"));
        Element<BasicNode> runStatement = ref(RUN,
                        t -> new BasicNode("run"));
        Element<BasicNode> endStatement = ref(END,
                        t -> new BasicNode("end"));
        statement.define(alt(printStatement, ifStatement, gotoStatement, inputStatement, assignStatement, gosubStatement, returnStatement, clearStatement, listStatement, runStatement, endStatement));
         */
        statement.define(alt(objectStatement));

        /*
        Element<LexerList<BasicNode>> exprlistRep = rep(seq(ref(COMMA), alt(string, expression), PEParser::selectSecond));
        exprlist.define(seq(alt(string, expression), exprlistRep, PEParser::concat));

        Element<LexerList<BasicNode>> varlistRep = rep(seq(ref(COMMA), vara, PEParser::selectSecond));
        varlist.define(seq(vara, varlistRep, PEParser::concat));

        Element<LexerList<PEParser.TermFactor>> expressionRep = rep(seq(alt(ref(PLUS, t -> "plus"), ref(MINUS, t -> "minus")), term, PEParser.TermFactor::new));
        Element<Optional<Boolean>> plusOrMinus = opt(alt(ref(PLUS, t -> false), ref(MINUS, t -> true)));
        expression.define(seq(plusOrMinus, term, expressionRep,
                        (pm, first, additionalTerms) -> {
                            BasicNode result = first;
                            if (pm.orElse(false)) {
                                result = new BasicNode("unaryMinus", result);
                            }
                            for (PEParser.TermFactor tf : additionalTerms) {
                                result = new BasicNode(tf.op, result, tf.operand);
                            }
                            return result;
                        }));

        term.define(seq(factor, rep(seq(alt(ref(MUL, t -> "mul"), ref(DIV, t -> "div")), factor, PEParser.TermFactor::new)),
                        (first, additionalFactors) -> {
                            BasicNode result = first;
                            for (PEParser.TermFactor tf : additionalFactors) {
                                result = new BasicNode(tf.op, result, tf.operand);
                            }
                            return result;
                        }));
        factor.define(alt(vara, ref(NUMBER, t -> new BasicNode("number"))));
        vara.define(alt(ref(NAME, t -> new BasicNode("name")), string));
        string.define(ref(STRING, t -> new BasicNode("string")));
        relop.define(alt(
                        seq(ref(LESS_THAN, t -> RelOp.LessThan), opt(alt(ref(LARGER_THAN, t -> RelOp.NotEquals), ref(EQUALS, t -> RelOp.LessThanEquals))), RelOp::choose),
                        seq(ref(LARGER_THAN, t -> RelOp.LargerThan), opt(alt(ref(LESS_THAN, t -> RelOp.NotEquals), ref(EQUALS, t -> RelOp.LargerThanEquals))), RelOp::choose),
                        ref(EQUALS, t -> RelOp.Equals),
                        ref(PLUS, t -> RelOp.Plus),
                        ref(MINUS, t -> RelOp.Minus)));
         */
        PARSER.initialize(program);

    }

    public static void parse(Source s, Consumer<Object> registrar) {
        TokenSequence<SelfTokenId> seq = TokenHierarchy.create(s.getCharacters(), SelfTokenId.language()).tokenSequence(SelfTokenId.language());
        class SeqLexer implements PELexer {
            private final Object[] self = new Object[] { this };
            {
                boolean b = seq.moveNext();
                assert b : "Moved to start";
            }

            @Override
            public Object[] asArgumentsArray() {
                return self;
            }

            @Override
            public byte peek(ConditionProfile seenEof) {
                return (byte) seq.token().id().ordinal();
            }

            @Override
            public void push(Object t) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public String position() {
                return "at: " + seq.offset();
            }

            @Override
            public void resetStackPointer(int pointer) {
                seq.move(pointer);
            }

            @Override
            public byte nextToken(ConditionProfile seenEof) {
                byte token = peek(seenEof);
                seq.moveNext();
                return token;
            }

            @Override
            public int currentTokenId() {
                return seq.token().id().ordinal();
            }

            @Override
            public int getStackPointer() {
                return seq.offset();
            }

            @Override
            public LexerList getStackList(int pointer) {
                return new LexerList() {
                    @Override
                    public int size() {
                        return 0;
                    }

                    @Override
                    public SelfLexer.BasicNode get(int i) {
                        return null;
                    }
                };
            }

            @Override
            public String tokenNames(int token) {
                return "tokenNames " + SelfTokenId.values()[token];
            }
        }
        BasicNode bn = (BasicNode) PARSER.parse(new SeqLexer());
        bn.print(registrar);
    }
}

enum SelfTokenId implements TokenId {

    WHITESPACE(null, "whitespace"),
    IDENTIFIER(null, null),
    KEYWORD(null, null),
    ARGUMENT(null, null),
    OPERATOR(null, null),
    NUMBER(null, "number"),
    STRING(null, "string"),

    COMMENT(null, "comment"),
    LPAREN("(", "separator"),
    RPAREN(")", "separator"),
    BAR("|", "separator"),
    ERROR(null, "error");


    private final String fixedText;

    private final String primaryCategory;

    private SelfTokenId(String fixedText, String primaryCategory) {
        this.fixedText = fixedText;
        this.primaryCategory = primaryCategory;
    }

    public String fixedText() {
        return fixedText;
    }

    @Override
    public String primaryCategory() {
        return primaryCategory;
    }

    private static final Language<SelfTokenId> language = new LanguageHierarchy<SelfTokenId>() {
        @Override
        protected Collection<SelfTokenId> createTokenIds() {
            return EnumSet.allOf(SelfTokenId.class);
        }

        @Override
        protected Map<String,Collection<SelfTokenId>> createTokenCategories() {
            Map<String,Collection<SelfTokenId>> cats = new HashMap<>();
            return cats;
        }

        @Override
        protected Lexer<SelfTokenId> createLexer(LexerRestartInfo<SelfTokenId> info) {
            return new SelfLexer(info);
        }

        @Override
        protected String mimeType() {
            return "text/x-self";
        }

    }.language();

    public static final Language<SelfTokenId> language() {
        return language;
    }

}

final class SelfLexer implements Lexer<SelfTokenId> {
    private static final int EOF = LexerInput.EOF;

    private final LexerInput input;
    private final TokenFactory<SelfTokenId> tokenFactory;

    SelfLexer(LexerRestartInfo<SelfTokenId> info) {
        this.input = info.input();
        this.tokenFactory = info.tokenFactory();
        assert (info.state() == null); // passed argument always null
    }

    @Override
    public Token<SelfTokenId> nextToken() {
        while (true) {
            int ch = input.read();
            switch (ch) {
                case '(':
                    return token(SelfTokenId.LPAREN);

                case ')':
                    return token(SelfTokenId.RPAREN);

                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                case '.':
                    return finishIntOrFloatLiteral(ch);

                case '!': case '@': case '#': case '$': case '%':
                case '^': case '&': case '*': case '-': case '+':
                case '=': case '~': case '/': case '?': case '<':
                case '>': case ',': case ';': case '|': case '‘':
                case '\\':
                    boolean justOne = true;
                    for (;;) {
                        switch (input.read()) {
                            case '!': case '@': case '#': case '$': case '%':
                            case '^': case '&': case '*': case '-': case '+':
                            case '=': case '~': case '/': case '?': case '<':
                            case '>': case ',': case ';': case '|': case '‘':
                            case '\\':
                                justOne = false;
                                continue;
                            case '0': case '1': case '2': case '3': case '4':
                            case '5': case '6': case '7': case '8': case '9':
                                if (ch == '-') {
                                    input.backup(1);
                                    return finishIntOrFloatLiteral(ch);
                                }
                        }
                        input.backup(1);
                        if (justOne && ch == '|') {
                            return token(SelfTokenId.BAR);
                        }
                        return token(SelfTokenId.OPERATOR);
                    }
                case '_':
                    return consumeIdentifier(ch);
                case '\'':
                    return consumeString(ch);
                case '"':
                    return consumeComment(ch);
                case ':':
                    ch = input.read();
                    if (Character.isLowerCase(ch)) {
                        Token<SelfTokenId> ident = consumeIdentifier(ch);
                        if (ident.id() == SelfTokenId.IDENTIFIER) {
                            return token(SelfTokenId.ARGUMENT);
                        }
                    }
                    return consumeUptoWhitespace(SelfTokenId.ERROR);
                case EOF:
                    return null;
                default:
                    if (Character.isWhitespace(ch)) {
                        ch = input.read();
                        while (ch != EOF && Character.isWhitespace(ch)) {
                            ch = input.read();
                        }
                        input.backup(1);
                        return token(SelfTokenId.WHITESPACE);
                    }

                    if (Character.isLowerCase(ch)) {
                        return consumeIdentifier(ch);
                    }

                    if (Character.isAlphabetic(ch)) {
                        Token<SelfTokenId> token = consumeIdentifier(ch);
                        if (token.id() == SelfTokenId.KEYWORD) {
                            return token;
                        }
                    }
                    return consumeUptoWhitespace(SelfTokenId.ERROR);
            }
        }
    }

    private Token<SelfTokenId> consumeUptoWhitespace(SelfTokenId id) {
        for (;;) {
            int ch = input.read();
            if (ch == EOF) {
                break;
            }
            if (Character.isWhitespace(ch)) {
                break;
            }
        }
        input.backup(1);
        return token(id);
    }

    private Token<SelfTokenId> consumeString(int ch) {
        boolean backslash = false;
        for (;;) {
            ch = input.read();
            switch (ch) {
                case '\\':
                    backslash = !backslash;
                    break;
                case '\'':
                    if (!backslash) {
                        return token(SelfTokenId.STRING);
                    }
                    break;
                case '\n':
                case EOF:
                    return token(SelfTokenId.ERROR);
            }
        }
    }

    private Token<SelfTokenId> consumeComment(int ch) {
        for (;;) {
            ch = input.read();
            switch (ch) {
                case '"':
                    return token(SelfTokenId.COMMENT);
                case EOF:
                    return token(SelfTokenId.ERROR);
            }
        }
    }

    @Override
    public Object state() {
        return null;
    }

    @Override
    public void release() {
    }

    private Token<SelfTokenId> finishIntOrFloatLiteral(int ch) {
        boolean firstLetter = true;
        boolean baseRead = false;
        boolean floatLiteral = false;
        boolean inExponent = false;
        while (true) {
            switch (ch) {
                case '.':
                    if (floatLiteral) {
                        return token(SelfTokenId.NUMBER);
                    } else {
                        floatLiteral = true;
                    }
                    break;
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    break;
                case 'r': case 'R': // base separator
                    if (baseRead) {
                        return token(SelfTokenId.NUMBER);
                    }
                    baseRead = true;
                    break;
                case 'e': case 'E': // exponent part
                    if (baseRead && 'a' <= ch && ch <= 'z') {
                        break;
                    }
                    if (inExponent) {
                        return token(SelfTokenId.NUMBER);
                    } else {
                        floatLiteral = true;
                        inExponent = true;
                    }
                    break;
                case '-':
                    if (firstLetter) {
                        break;
                    }
                case '+':
                    if (inExponent) {
                        break;
                    }
                    // fallthrough
                default:
                    if (baseRead && 'a' <= ch && ch <= 'z') {
                        break;
                    }
                    input.backup(1);
                    return token(SelfTokenId.NUMBER);
            }
            firstLetter = false;
            ch = input.read();
        }
    }

    private Token<SelfTokenId> token(SelfTokenId id) {
        return (id.fixedText() != null)
            ? tokenFactory.getFlyweightToken(id, id.fixedText())
            : tokenFactory.createToken(id);
    }

    private Token<SelfTokenId> consumeIdentifier(int ch) {
        for (;;) {
            ch = input.read();
            if (Character.isLetterOrDigit(ch)) {
                continue;
            }
            if ('_' == ch) {
                continue;
            }
            SelfTokenId id;
            if (':' == ch) {
                id = SelfTokenId.KEYWORD;
            } else {
                input.backup(1); // backup the extra char (or EOF)
                id = SelfTokenId.IDENTIFIER;
            }
            return token(id);
        }
    }
    static class BasicNode {

        private final String name;
        private final BasicNode[] children;

        BasicNode(String name, BasicNode... children) {
            this.name = name;
            this.children = children;
        }

        BasicNode(String name, List<BasicNode> children) {
            this.name = name;
            this.children = children.toArray(new BasicNode[children.size()]);
        }

        public void print(int level) {
            for (int i = 0; i < level; i++) {
                System.out.print("  ");
            }
            System.out.println(name);
            for (BasicNode child : children) {
                child.print(level + 1);
            }
        }

        void print(Consumer<Object> registrar) {
            for (BasicNode child : children) {
                child.print(registrar);
            }
        }
    }

    public enum RelOp {
        LessThan,
        LessThanEquals,
        LargerThan,
        LargerThanEquals,
        Equals,
        NotEquals,
        Plus,
        Minus;

        static RelOp choose(RelOp a, Optional<RelOp> b) {
            return b.orElse(a);
        }
    }

    static class TermFactor {

        private final String op;
        private final BasicNode operand;

        TermFactor(String op, BasicNode operand) {
            this.op = op;
            this.operand = operand;
        }
    }

    public static <A, B> A selectFirst(A a, @SuppressWarnings("unused") B b) {
        return a;
    }

    public static <A, B> B selectSecond(@SuppressWarnings("unused") A a, B b) {
        return b;
    }

    public static BasicNode[] concat(BasicNode first, LexerList<BasicNode> rest) {
        BasicNode[] result = new BasicNode[rest.size() + 1];
        result[0] = first;
        for (int i = 0; i < rest.size(); i++) {
            result[i + 1] = rest.get(i);
        }
        return result;
    }
}
