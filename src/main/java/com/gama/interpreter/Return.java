package com.gama.interpreter;

public class Return extends RuntimeException {
    public final Object returnValue;

    public Return(Object returnValue) {
        super(null, null, false, false);
        this.returnValue = returnValue;
    }
}
