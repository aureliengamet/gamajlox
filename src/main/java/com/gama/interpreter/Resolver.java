package com.gama.interpreter;

import java.util.*;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, VarInfo>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private ClassType currentClass = ClassType.NONE;
    private boolean isInLoop = false;

    private static class VarInfo {
        public final Token token;
        public final boolean initialized;
        public boolean used;

        public VarInfo(Token token, boolean initialized) {
            this(token, initialized, false);
        }

        public VarInfo(Token token, boolean initialized, boolean used) {
            this.token = token;
            this.initialized = initialized;
            this.used = used;
        }

        public void setUsed() {
            this.used = true;
        }
    }

    private enum FunctionType {
        NONE, FUNCTION, INITIALIZER, METHOD, STATIC_METHOD;
    }

    private enum ClassType {
        NONE, CLASS;
    }

    public Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void resolve(List<Stmt> statements) {
        statements.forEach(this::resolve);
    }

    private void resolve(Stmt statement) {
        statement.accept(this);
    }

    private void resolve(Expr expression) {
        expression.accept(this);
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                scopes.get(i).get(name.lexeme).setUsed();
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    private void resolveFunction(List<Token> params, List<Stmt> body, FunctionType functionType) {
        FunctionType enclosing = currentFunction;
        currentFunction = functionType;
        beginScope();
        for (Token param : params) {
            declare(param);
            define(param);
        }
        resolve(body);
        endScope();
        currentFunction = enclosing;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        expr.arguments.forEach(this::resolve);
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.object);
        resolve(expr.value);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr) {
        if (!Arrays.asList(FunctionType.METHOD, FunctionType.INITIALIZER).contains(currentFunction)) {
            Gamajlox.error(expr.keyword, "Can only use 'this' in instance methods.");
            return null;
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitTernaryExpr(Expr.Ternary expr) {
        resolve(expr.condition);
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty()) {
            VarInfo varInfo = scopes.peek().get(expr.name.lexeme);
            if (varInfo != null && !varInfo.initialized) {
                Gamajlox.error(expr.name, "Cannot read local variable in its own initializer.");
            }
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitAnonFunctionExpr(Expr.AnonFunction expr) {
        resolveFunction(expr.params, expr.body, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosing = currentClass;
        currentClass = ClassType.CLASS;
        declare(stmt.name);
        define(stmt.name);
        beginScope();
        scopes.peek().put("this", new VarInfo(stmt.name, true, true));
        for (Stmt.Function method : stmt.methods) {
            FunctionType functionType = "init".equals(method.name.lexeme) ? FunctionType.INITIALIZER : FunctionType.METHOD;
            resolveFunction(method.params, method.body, functionType);
        }
        for (Stmt.Function getter : stmt.getters) {
            resolveFunction(getter.params, getter.body, FunctionType.METHOD);
        }
        for (Stmt.Function staticMethod : stmt.staticMethods) {
            resolveFunction(staticMethod.params, staticMethod.body, FunctionType.STATIC_METHOD);
        }
        endScope();
        currentClass = enclosing;
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        resolveFunction(stmt.params, stmt.body, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Gamajlox.error(stmt.keyword, "Cannot return from top-level code.");
        }
        if (stmt.value != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Gamajlox.error(stmt.keyword, "Cannot return a value from an initializer.");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        boolean enclosing = isInLoop;
        isInLoop = true;
        resolve(stmt.body);
        isInLoop = false;
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        if (!isInLoop) {
            Gamajlox.error(stmt.breakToken, "Cannot break when not in a loop.");
        }
        return null;
    }

    private void beginScope() {
        scopes.push(new HashMap<>());
    }

    private void endScope() {
        Map<String, VarInfo> scope = scopes.pop();
        scope.values().stream()
                .filter(var -> !var.used)
                .forEach(var -> Gamajlox.warning(var.token, "This variable is unused."));
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) {
            return;
        }
        Map<String, VarInfo> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Gamajlox.error(name, "Variable with this name already declared in this scope.");
        }
        scope.put(name.lexeme, new VarInfo(name, false));
    }

    private void define(Token name) {
        if (scopes.isEmpty()) {
            return;
        }
        scopes.peek().put(name.lexeme, new VarInfo(name, true));
    }
}
