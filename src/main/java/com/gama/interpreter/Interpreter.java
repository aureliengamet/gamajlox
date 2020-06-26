package com.gama.interpreter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private static class BreakInterrupt extends RuntimeException {
    }

    public final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr, Integer> locals = new HashMap<>();

    public Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                evaluate(statement);
            }
        } catch (RuntimeError e) {
            Gamajlox.runtimeError(e);
        } catch (Return returnException) {
        }
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        if (stmt.initializer == null) {
            environment.define(stmt.name.lexeme);
        } else {
            environment.define(stmt.name.lexeme, evaluate(stmt.initializer));
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                evaluate(stmt.body);
            }
        } catch (BreakInterrupt e) {
        }
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakInterrupt();
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }

        environment.define(stmt.name.lexeme);

        if (stmt.superclass != null) {
            environment = new Environment(environment);
            environment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, "init".equals(method.name.lexeme), environment);
            methods.put(method.name.lexeme, function);
        }
        Map<String, LoxFunction> getters = new HashMap<>();
        for (Stmt.Function getter : stmt.getters) {
            LoxFunction function = new LoxFunction(getter, false, environment);
            getters.put(getter.name.lexeme, function);
        }
        Map<String, LoxFunction> staticMethods = new HashMap<>();
        for (Stmt.Function staticMethod : stmt.staticMethods) {
            LoxFunction function = new LoxFunction(staticMethod, false, environment);
            staticMethods.put(staticMethod.name.lexeme, function);
        }

        LoxRegularClass loxRegularClass = new LoxRegularClass(stmt.name.lexeme, (LoxClass) superclass, methods, getters, staticMethods);

        if (superclass != null) {
            environment = environment.enclosing;
        }

        environment.assign(stmt.name, loxRegularClass);
        return null;
    }

    public Void executeBlock(List<Stmt> statements, Environment newEnvironment) {
        Environment enclosingEnvironment = environment;
        try {
            environment = newEnvironment;
            for (Stmt statement : statements) {
                evaluate(statement);
            }
            return null;
        } finally {
            environment = enclosingEnvironment;
        }
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt, false, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            evaluate(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            evaluate(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object expressionInterpreted = evaluate(stmt.expression);
        System.out.println(stringify(expressionInterpreted));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object returnValue = stmt.value != null ? evaluate(stmt.value) : null;
        throw new Return(returnValue);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            globals.assign(expr.name, value);
        }
        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if ((double) right == 0) {
                    throw new RuntimeError(expr.operator, "Division by zero is not allowed");
                }
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case EQUAL_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return isEqual(left, right);
            case BANG_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return !isEqual(left, right);
            default:
                throw new RuntimeError(expr.operator, "Unknown operator");
        }
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);
        List<Object> arguments = expr.arguments.stream()
                .map(this::evaluate)
                .collect(Collectors.toList());

        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable) callee;
        if (function.arity() != arguments.size()) {
            throw new RuntimeError(expr.paren, "Expected " + arguments.size() + " arguments, got " + function.arity() + " instead.");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object obj = evaluate(expr.object);
        if (obj instanceof LoxInstance) {
            return ((LoxInstance) obj).get(expr.name, this);
        }
        throw new RuntimeError(expr.name, "Tried to access a property of something other than an instance.");
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        switch (expr.operator.type) {
            case OR:
                if (isTruthy(left)) {
                    return left;
                }
                break;
            case AND:
                if (!isTruthy(left)) {
                    return left;
                }
                break;
            default:
                throw new RuntimeError(expr.operator, "Unknown logical operator.");
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object obj = evaluate(expr.object);

        if (!(obj instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Can't set a field inside an object that is not an instance.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance) obj).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass) environment.getAt(distance, "super");
        LoxInstance instance = (LoxInstance) environment.getAt(distance - 1, "this");
        LoxFunction method = superclass.findMethod(expr.method.lexeme);
        if (method == null) {
            method = superclass.findGetter(expr.method.lexeme);
        }
        if (method == null) {
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(instance);
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
            case BANG:
                return !isTruthy(right);
            default:
                throw new RuntimeError(expr.operator, "Unknown operator");
        }
    }

    @Override
    public Object visitTernaryExpr(Expr.Ternary expr) {
        Object condition = evaluate(expr.condition);
        return isTruthy(condition) ? evaluate(expr.left) : evaluate(expr.right);
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            return globals.get(name);
        }
    }

    @Override
    public Object visitAnonFunctionExpr(Expr.AnonFunction expr) {
        return new LoxFunction(expr, environment);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void evaluate(Stmt stmt) {
        stmt.accept(this);
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) {
            return;
        }
        throw new RuntimeError(operator, "Operand must be a number");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return;
        }

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean) {
            return (boolean) obj;
        }
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        return Objects.equals(a, b);
    }

    private String stringify(Object obj) {
        if (obj == null) {
            return "nil";
        }

        if (obj instanceof Double) {
            String text = obj.toString();
            if (text.endsWith(".0")) {
                return text.substring(0, text.length() - 2);
            }
            return text;
        }

        return obj.toString();
    }
}
