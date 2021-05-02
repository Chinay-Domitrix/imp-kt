package org.imp.jvm.expression;

import org.imp.jvm.domain.scope.Scope;
import org.imp.jvm.domain.types.Type;
import org.imp.jvm.statement.Statement;
import org.objectweb.asm.MethodVisitor;

public abstract class Expression extends Statement {
    public Type type;

    public abstract void generate(MethodVisitor mv, Scope scope);

}

