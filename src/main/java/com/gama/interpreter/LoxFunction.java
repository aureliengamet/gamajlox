package com.gama.interpreter;

import java.util.List;

public class LoxFunction implements LoxCallable {
    private final String name;
    private final List<Token> params;
    private final List<Stmt> body;
    private final Environment closure;
    private final boolean isInitializer;

    public LoxFunction(Stmt.Function declaration, boolean isInitializer, Environment closure) {
        this(declaration.name.lexeme, declaration.params, declaration.body, isInitializer, closure);
    }

    public LoxFunction(Expr.AnonFunction declaration, Environment closure) {
        this("anonymous function", declaration.params, declaration.body, false, closure);
    }

    private LoxFunction(String name, List<Token> params, List<Stmt> body, boolean isInitializer, Environment closure) {
        this.name = name;
        this.params = params;
        this.body = body;
        this.isInitializer = isInitializer;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return params.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment executionEnvironment = new Environment(this.closure);
        for (int i = 0; i < arguments.size(); ++i) {
            executionEnvironment.define(params.get(i).lexeme, arguments.get(i));
        }
        try {
            interpreter.executeBlock(body, executionEnvironment);
        } catch (Return returnException) {
            if (isInitializer) {
                return closure.getAt(0, "this");
            }
            return returnException.returnValue;
        }
        if (isInitializer) {
            return closure.getAt(0, "this");
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + name + ">";
    }

    public LoxFunction bind(LoxInstance loxInstance) {
        Environment environment = new Environment(closure);
        environment.define("this", loxInstance);
        return new LoxFunction(name, params, body, isInitializer, environment);
    }
}
