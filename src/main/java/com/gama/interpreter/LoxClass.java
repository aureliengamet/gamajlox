package com.gama.interpreter;

public interface LoxClass {
    LoxFunction findMethod(String name);

    LoxFunction findGetter(String name);
}
