package org.imp.jvm.domain.scope;

import org.imp.jvm.types.Mutability;
import org.imp.jvm.types.Type;

public class LocalVariable {
    public final String name;
    public Type type;
    public boolean closure = false;
    public final Mutability mutability;

    public LocalVariable(String name, Type type) {
        this.type = type;
        this.name = name;
        this.mutability = Mutability.Val;
    }

    public LocalVariable(String name, Type type, Mutability mutability) {
        this.type = type;
        this.name = name;
        this.mutability = mutability;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return getName() + " " + getType();
    }
}