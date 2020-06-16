package com.gama.interpreter;

import java.util.List;
import java.util.Map;

public class LoxRegularClass extends LoxInstance implements LoxCallable, LoxClass {
    public final String name;
    private final Map<String, LoxFunction> methods;

    public LoxRegularClass(String name, Map<String, LoxFunction> methods, Map<String, LoxFunction> staticMethods) {
        super(new LoxMetaClass(name, staticMethods));
        this.name = name;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        return initializer == null ? 0 : initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);

        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    @Override
    public LoxFunction findMethod(String name) {
        return methods.get(name);
    }
}
