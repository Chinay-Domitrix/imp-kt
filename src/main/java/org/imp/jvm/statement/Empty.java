package org.imp.jvm.statement;

import org.antlr.v4.runtime.ParserRuleContext;
import org.imp.jvm.domain.scope.Scope;
import org.objectweb.asm.MethodVisitor;

public class Empty extends Statement {

    public Empty(ParserRuleContext ctx, String name) {
        setCtx(ctx, name);
    }

    @Override
    public void generate(MethodVisitor mv, Scope scope) {

    }

    @Override
    public void validate(Scope scope) {

    }
}
