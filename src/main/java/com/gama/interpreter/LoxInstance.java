package com.gama.interpreter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LoxInstance {
    public final LoxClass loxClass;
    private final Map<String, Object> fields = new HashMap<>();

    public LoxInstance(LoxClass loxClass) {
        this.loxClass = loxClass;
    }

    @Override
    public String toString() {
        return "<" + loxClass + " instance" + ">";
    }

    public Object get(Token name, Interpreter interpreter) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }
        LoxFunction getter = loxClass.findGetter(name.lexeme);
        if (getter != null) {
            return getter.bind(this).call(interpreter, Collections.emptyList());
        }
        LoxFunction method = loxClass.findMethod(name.lexeme);
        if (method != null) {
            return method.bind(this);
        }
        throw new RuntimeError(name, "Undefined property " + name.lexeme + ".");
    }

    public void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }
}
