package com.gama.interpreter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.gama.interpreter.TokenType.*;

public class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;
    private boolean isReplMode;

    public Parser(List<Token> tokens, boolean isReplMode) {
        this.tokens = tokens;
        this.isReplMode = isReplMode;
    }

    public List<Stmt> parse() {
        if (isReplMode) {
            try {
                Expr expression = expression();
                if (isAtEnd()) {
                    return Collections.singletonList(new Stmt.Print(expression));
                }
            } catch (ParseError e) {
            }
            reset();
        }

        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(CLASS)) {
                return classDeclaration();
            }
            if (check(FUN)) {
                return funDeclaration("function");
            }
            if (match(VAR)) {
                return varDeclaration();
            }
            return statement();
        } catch (ParseError e) {
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expected class name.");
        consume(LEFT_BRACE, "Expected '{' before class body.");

        List<Stmt.Function> methods = new ArrayList<>();
        List<Stmt.Function> getters = new ArrayList<>();
        List<Stmt.Function> staticMethods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            if (match(CLASS)) {
                staticMethods.add(function("static method"));
            } else {
                if (peek().type == IDENTIFIER && peekNext().type == LEFT_BRACE) {
                    Token getterName = advance();
                    List<Stmt> getterBody = getFunctionBody("getter");
                    getters.add(new Stmt.Function(getterName, Collections.emptyList(), getterBody));
                } else {
                    methods.add(function("method"));
                }
            }
        }

        consume(RIGHT_BRACE, "Expected '}' after class body.");
        return new Stmt.Class(name, methods, getters, staticMethods);
    }

    private Stmt funDeclaration(String kind) {
        if (peekNext().type == LEFT_PAREN) {
            return expressionStatement();
        }
        consume(FUN, "Expected fun keyword.");
        return function(kind);
    }

    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expected " + kind + " name.");
        List<Token> parameters = getFunctionParameters(kind);
        List<Stmt> body = getFunctionBody(kind);
        return new Stmt.Function(name, parameters, body);
    }

    private List<Token> getFunctionParameters(String kind) {
        consume(LEFT_PAREN, "Expected '(' before " + kind + " parameters.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Cannot have more than 255 parameters.");
                }
                parameters.add(consume(IDENTIFIER, "Expected parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expected ')' after parameters.");
        return parameters;
    }

    private List<Stmt> getFunctionBody(String kind) {
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        return block();
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expected variable name.");
        Expr expression = null;
        if (match(EQUAL)) {
            expression = expression();
        }
        consume(SEMICOLON, "Expected ';' after variable declaration.");
        return new Stmt.Var(name, expression);
    }

    private Stmt statement() {
        if (match(IF)) {
            return ifStatement();
        } else if (match(WHILE)) {
            return whileStatement();
        } else if (match(FOR)) {
            return forStatement();
        } else if (match(PRINT)) {
            return printStatement();
        } else if (match(RETURN)) {
            return returnStatement();
        } else if (match(LEFT_BRACE)) {
            return blockStatement();
        } else if (match(BREAK)) {
            return breakStatement();
        }
        return expressionStatement();
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expected '(' before condition.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expected '(' before condition.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expected ')' after condition.");
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expected '(' after for.");

        Stmt initializer = null;
        if (match(VAR)) {
            initializer = varDeclaration();
        } else if (!match(SEMICOLON)) {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!match(SEMICOLON)) {
            condition = expression();
            consume(SEMICOLON, "Expected ';' after condition.");
        }

        Expr increment = null;
        if (!match(RIGHT_PAREN)) {
            increment = expression();
            consume(RIGHT_PAREN, "Expected ')' at the end of for loop");
        }

        Stmt body = statement();
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        Stmt whileStmt = condition != null ?
                new Stmt.While(condition, body) :
                new Stmt.While(new Expr.Literal(true), body);

        Stmt forStmt = whileStmt;
        if (initializer != null) {
            forStmt = new Stmt.Block(Arrays.asList(initializer, forStmt));
        }

        return forStmt;
    }

    private Stmt printStatement() {
        Expr expression = expression();
        consume(SEMICOLON, "Expected ';' here.");
        return new Stmt.Print(expression);
    }

    private Stmt returnStatement() {
        Token token = previous();
        Expr expression = null;
        if (!check(SEMICOLON)) {
            expression = expression();
        }
        consume(SEMICOLON, "Expected ';' here.");
        return new Stmt.Return(token, expression);
    }

    private Stmt blockStatement() {
        return new Stmt.Block(block());
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Stmt breakStatement() {
        Token breakToken = previous();
        consume(SEMICOLON, "Expected ';' after break.");
        return new Stmt.Break(breakToken);
    }

    private Stmt expressionStatement() {
        Expr expression = expression();
        consume(SEMICOLON, "Expected ';' here.");
        return new Stmt.Expression(expression);
    }

    private Expr expression() {
        return comma();
    }

    private Expr comma() {
        Expr expr = conditional();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = conditional();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr conditional() {
        Expr expr = assignment();

        if (match(QUESTION_MARK)) {
            Expr left = conditional();
            consume(COLON, "Missing a ':' in this conditional expression.");
            Expr right = conditional();
            expr = new Expr.Ternary(expr, left, right);
        }

        return expr;
    }

    private Expr assignment() {
        Expr expr = or();
        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get) expr;
                return new Expr.Set(get.object, get.name, value);
            }
            error(equals, "Before that equal, expected a proper assignment target.");
        }
        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(EQUAL_EQUAL, BANG_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = addition();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr addition() {
        Expr expr = multiplication();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr multiplication() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expected property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "Cannot have more than 255 arguments.");
                }
                arguments.add(conditional());
            } while (match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN, "Expected ')' to close the argument list.");
        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        } else if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        } else if (match(TRUE)) {
            return new Expr.Literal(true);
        } else if (match(FALSE)) {
            return new Expr.Literal(false);
        } else if (match(NIL)) {
            return new Expr.Literal(null);
        } else if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        } else if (match(THIS)) {
            return new Expr.This(previous());
        } else if (match(FUN)) {
            return anonFunction();
        }
        throw error(peek(), "Expected an expression.");
    }

    private Expr anonFunction() {
        String kind = "anonymous function";
        List<Token> parameters = getFunctionParameters(kind);
        List<Stmt> body = getFunctionBody(kind);
        return new Expr.AnonFunction(parameters, body);
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            }
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        }
        return peek().type == type;
    }

    private boolean checkNext(TokenType type) {
        if (isAtEnd()) {
            return false;
        }
        return peekNext().type == type;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekNext() {
        return tokens.get(current + 1);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return tokens.get(current - 1);
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private ParseError error(Token token, String message) {
        if (!isReplMode) {
            Gamajlox.error(token, message);
        }
        return new ParseError();
    }

    private void reset() {
        current = 0;
        isReplMode = false;
    }
}
