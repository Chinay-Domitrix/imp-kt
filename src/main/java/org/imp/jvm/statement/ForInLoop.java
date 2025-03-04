package org.imp.jvm.statement;

import org.imp.jvm.compiler.Logger;
import org.imp.jvm.domain.scope.LocalVariable;
import org.imp.jvm.domain.scope.Scope;
import org.imp.jvm.exception.Errors;
import org.imp.jvm.expression.Expression;
import org.imp.jvm.types.BuiltInType;
import org.imp.jvm.types.ListType;
import org.imp.jvm.types.Type;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ForInLoop extends Loop {
    public final String iteratorName;
    public final Expression expression;
    public final Block block;

    public ForInLoop(String iteratorName, Expression expression, Block block) {
        super(block);
        this.iteratorName = iteratorName;
        this.expression = expression;
        this.block = block;
    }

    @Override
    public void validate(Scope scope) {
        expression.validate(scope);


        // Check that the expression can be iterated upon
        // Until the Imp type hierarchy develops, we will
        // allow for each on types that implement the Java
        // Iterable interface, and on strings.
        Class<?> expressionClass = expression.type.getTypeClass();
        if (Iterable.class.isAssignableFrom(expressionClass)) {
            // Ee know the expression is a List or some other
            // type implementing Iterable.

        } else if (expression.type == BuiltInType.STRING) {
            System.err.println("String for-in loops Todo.");
            // Todo
        } else {
            Logger.syntaxError(Errors.CannotIterate, this, this.expression.getCtx().getText());
            return;
        }

        block.scope = new Scope(scope);

        // Todo: unique names so we can have multiple for-in loops in a scope.
        block.scope.addLocalVariable(new LocalVariable("iterator", BuiltInType.OBJECT));
        // Set the type of the iterator variable
        Type iteratorType = BuiltInType.OBJECT;
        if (expression.type instanceof ListType listType) {
            iteratorType = listType.contentType;
        }

        block.scope.addLocalVariable(new LocalVariable(iteratorName, iteratorType));

        block.validate(block.scope);


    }

    @Override
    public void generate(MethodVisitor mv, Scope scope) {
        Label end = new Label();
        Label loop = new Label();

        expression.generate(mv, scope);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
        int iteratorIndex = block.scope.getLocalVariableIndex("iterator");
        mv.visitVarInsn(Opcodes.ASTORE, iteratorIndex);

        mv.visitLabel(loop);

        mv.visitVarInsn(Opcodes.ALOAD, iteratorIndex);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        mv.visitJumpInsn(Opcodes.IFEQ, end);
        mv.visitVarInsn(Opcodes.ALOAD, iteratorIndex);

        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
        int nextIndex = block.scope.getLocalVariableIndex(iteratorName);
        LocalVariable i = block.scope.getLocalVariable(iteratorName);
        if (i.type instanceof BuiltInType bt) {
            bt.doUnboxing(mv);
        } else {
//            mv.visitTypeInsn(Opcodes.CHECKCAST, i.type.getInternalName());
        }

        mv.visitVarInsn(i.type.getStoreVariableOpcode(), nextIndex);

        block.generate(mv, block.scope);

        mv.visitJumpInsn(Opcodes.GOTO, loop);
        mv.visitLabel(end);
    }
}
