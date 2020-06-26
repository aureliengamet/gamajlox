package com.gama.interpreter;

import java.util.List;
import java.util.Map;

public class LoxRegularClass extends LoxInstance implements LoxCallable, LoxClass {
    public final String name;
    private final LoxClass superclass;
    private final Map<String, LoxFunction> methods;
    private final Map<String, LoxFunction> getters;

    public LoxRegularClass(String name,
                           LoxClass superclass,
                           Map<String, LoxFunction> methods,
                           Map<String, LoxFunction> getters,
                           Map<String, LoxFunction> staticMethods) {
        super(new LoxMetaClass(name, staticMethods));
        this.name = name;
        this.superclass = superclass;
        this.getters = getters;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return "<class " + name + ">";
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
        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        if (superclass != null) {
            return superclass.findMethod(name);
        }
        return null;
    }

    @Override
    public LoxFunction findGetter(String name) {
        if (getters.containsKey(name)) {
            return getters.get(name);
        }
        if (superclass != null) {
            return superclass.findGetter(name);
        }
        return null;
    }
}
