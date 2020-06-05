package com.gama.interpreter;

public class AstPrinter implements Expr.Visitor<String> {
    public String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) {
            return "nil";
        }
        return expr.value.toString();
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return parenthesize("?", expr.condition, expr.left, expr.right);
    }

    private String parenthesize(String lexeme, Expr... exprs) {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(lexeme);

        for (Expr expr : exprs) {
            sb.append(" ");
            sb.append(expr.accept(this));
        }

        sb.append(")");
        return sb.toString();
    }
}
