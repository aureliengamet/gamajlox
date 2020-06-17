package com.gama.interpreter;

import java.util.Map;

public class LoxMetaClass implements LoxClass {
    private final String name;
    private final Map<String, LoxFunction> methods;

    public LoxMetaClass(String name, Map<String, LoxFunction> methods) {
        this.name = name;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return "<metaclass " + name + ">";
    }

    @Override
    public LoxFunction findMethod(String name) {
        return methods.get(name);
    }

    @Override
    public LoxFunction findGetter(String name) {
        return null;
    }
}
