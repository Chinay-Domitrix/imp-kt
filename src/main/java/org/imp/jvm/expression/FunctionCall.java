package org.imp.jvm.expression;

import org.imp.jvm.compiler.DescriptorFactory;
import org.imp.jvm.compiler.Logger;
import org.imp.jvm.domain.ImpFile;
import org.imp.jvm.domain.scope.Identifier;
import org.imp.jvm.domain.scope.LocalVariable;
import org.imp.jvm.domain.scope.Scope;
import org.imp.jvm.exception.Errors;
import org.imp.jvm.expression.reference.ModuleReference;
import org.imp.jvm.expression.reference.VariableReference;
import org.imp.jvm.runtime.Glue;
import org.imp.jvm.types.*;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionCall extends Expression {
    public Function function;
    public List<Expression> arguments;
    public final ImpFile owner;

    public List<Type> argTypes;
    public String name;

    private boolean hasBeenInitialized = false;

    private boolean isModule = false;
    private Class<?> moduleClass = null;


    public FunctionCall(String name, List<Expression> arguments, ImpFile owner) {
        this.name = name;
        this.arguments = arguments;

        this.function = null;
        this.owner = owner;
    }

    public void setOwner(Expression owner) {
//        this.module = owner;
    }

    @Override
    public void validate(Scope scope) {
        // Find the types of each of the arguments
        for (var arg : arguments) {
            arg.validate(scope);
        }
        argTypes = arguments.stream().map(expression -> expression.type).collect(Collectors.toList());


        /*
         * Functions are resolved in a specific order, first by name and then by parameter types.
         * The order of search by name is as follows:
         *
         * 1. Functions in the always-imported "batteries" module
         * 2. Functions defined in the current scope.
         * 3. Functions from any imported standard library modules.
         * 4. Functions from user defined modules.
         *
         * If no match is found by name, search by parameters does not occur.
         *
         * Search by parameters performs an exact match.
         */
        FunctionType functionType = this.searchByName(scope);


        // If not found at all, error
        if (functionType == null) {
            Logger.syntaxError(Errors.FunctionNotFound, owner.name, getCtx(), getCtx().getStart().getText());
            return;
        }

        if (this.arguments.size() > 0 && this.arguments.get(0).type == BuiltInType.MODULE) {
            var variableReference = (VariableReference) this.arguments.get(0);
            var moduleReference = (ModuleReference) variableReference.reference;
            this.argTypes = this.argTypes.subList(1, this.argTypes.size());
            this.arguments = this.arguments.subList(1, this.arguments.size());
            this.isModule = true;
            this.moduleClass = Glue.coreModules.get(moduleReference.name);
        }
        this.function = functionType.getSignatureByTypes(this.argTypes);
        if (this.function == null) {
            Logger.syntaxError(Errors.FunctionSignatureMismatch, owner.name, getCtx(), getCtx().getStart().getText(), getCtx().getText());
            return;
        }
        this.type = function.returnType;


        // Store as new local variable
        scope.addLocalVariable(new LocalVariable(function.functionType.name, function.functionType));
    }

    public void generate(MethodVisitor mv, Scope scope) {
        // generate arguments

        if (function.isStandard) {
            String owner = "org/imp/jvm/runtime/stdlib/Batteries";

            // Todo: clean this up. Having a faster log for single
            //  objects shouldn't require this much stuff.
            if (this.name.equals("log") && arguments.size() > 1) {
                mv.visitLdcInsn(arguments.size());
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

                for (int i = 0; i < arguments.size(); i++) {
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitLdcInsn(i);
                    arguments.get(i).generate(mv, scope);
                    // Todo: cast bad
                    BuiltInType bt = (BuiltInType) arguments.get(i).type;
                    bt.doBoxing(mv);
                    mv.visitInsn(Opcodes.AASTORE);

                }

            } else if (this.name.equals("log") && arguments.size() == 1) {
                arguments.get(0).generate(mv, scope);
                BuiltInType bt = (BuiltInType) arguments.get(0).type;
                bt.doBoxing(mv);

            } else {
                var argList = this.arguments;
                if (this.isModule) {
                    owner = this.moduleClass.getName().replace(".", "/");
                }
                for (var arg : argList) {
                    arg.generate(mv, scope);
                }

            }

//            mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");


            List<Identifier> params = arguments.stream().map(arg -> new Identifier(arg.type.getName(), arg.type)).collect(Collectors.toList());
            Type returnType = this.function.returnType;

            // Todo: method descriptors have to be dynamically generated
            // based on ModuleReferences being the first parameter.
            String methodDescriptor = DescriptorFactory.getMethodDescriptor(params, returnType);
            if (this.name.equals("log")) {
                if (arguments.size() > 1) methodDescriptor = "([Ljava/lang/Object;)V";
                else if (arguments.size() == 1) methodDescriptor = "(Ljava/lang/Object;)V";
            }

            String name = this.function.functionType.name;

            mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, methodDescriptor, false);
        } else {
            // 0. If the First Class Function has not been initialized, do so.
            String localVariableName = this.name;
            if (!hasBeenInitialized) {
                // Initialize the first-class function closure object
                String ownerDescriptor = this.function.functionType.getInternalName();
                mv.visitTypeInsn(Opcodes.NEW, ownerDescriptor);
                mv.visitInsn(Opcodes.DUP);

                // Call constructor on first-class function closure object
                List<Identifier> params = Collections.emptyList();
                String methodDescriptor = DescriptorFactory.getMethodDescriptor(params, BuiltInType.VOID);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ownerDescriptor, "<init>", methodDescriptor, false);
//
//                // Store as new local variable
//                scope.addLocalVariable(new LocalVariable(localVariableName, function.functionType));
                mv.visitVarInsn(Opcodes.ASTORE, scope.getLocalVariableIndex(localVariableName));
                hasBeenInitialized = true;
            }

            // 1. Load the First Class Function object
            String ownerDescriptor = this.function.functionType.getInternalName();
            int index = scope.getLocalVariableIndex(localVariableName);
            mv.visitVarInsn(Opcodes.ALOAD, index);

            // 2. Load the variables that must be passed to the closure
            var s = function.functionType.signatures.getValue(0).block.scope;
            List<Identifier> closureParams = new ArrayList<>();
            for (var p : s.closures.values()) {
                var identifier = new Identifier(p.getName(), BuiltInType.BOX);
                closureParams.add(identifier);
                int i = scope.getLocalVariableIndex(p.getName());
                mv.visitVarInsn(Opcodes.ALOAD, i);
            }

            // 3. Call the closure() method on the First Class Function object
            String methodDescriptor = DescriptorFactory.getMethodDescriptor(closureParams, BuiltInType.VOID);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ownerDescriptor, "closure", methodDescriptor, false);

            // 4. Generate arguments for the function itself
            index = scope.getLocalVariableIndex(localVariableName);
            mv.visitVarInsn(Opcodes.ALOAD, index);
            for (var arg : arguments) {
                arg.generate(mv, scope);
            }

            // 5. Call the appropriate invoke method on the First Class Function object
            List<Identifier> params = arguments.stream().map(arg -> new Identifier(arg.type.getName(), arg.type)).collect(Collectors.toList());

            Type returnType = this.function.returnType;
            methodDescriptor = DescriptorFactory.getMethodDescriptor(params, returnType);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ownerDescriptor, "invoke", methodDescriptor, false);


        }
    }

    /**
     * @param name a
     * @return a
     * @deprecated
     */
    private FunctionType getFunctionType(String name) {
        for (var imported : this.owner.qualifiedImports) {
            for (var func : imported.functions) {
                if (func.functionType.name.equals(name)) {
                    return func.functionType;
                }
            }
        }
        return null;
    }


    private FunctionType searchByName(Scope scope) {
        FunctionType functionType = null;

        // 1. Functions in the always-imported "batteries" module
        functionType = Glue.findBatteriesFunction(this.name, this.owner);
        if (functionType != null) return functionType;

        // 2. Functions defined in the current scope.
        functionType = scope.findFunctionType(this.name);
        if (functionType != null) return functionType;

        // 3. Functions from any imported standard library modules.
        if (arguments.size() > 0) {
            var potentialVariableReference = arguments.get(0);
            if (potentialVariableReference instanceof VariableReference variableReference) {
                if (variableReference.reference instanceof ModuleReference moduleReference) {
                    String moduleName = moduleReference.name;
                    functionType = Glue.findStandardLibraryFunction(moduleName, this.name, this.owner);
                }
                // Todo: potentially error here, don't need to check anything else if name not in module.
            }
        }
        if (functionType != null) return functionType;

        // Todo: 4. Functions from user defined modules.
//        if (this.module != null) {
//            if (this.module instanceof VariableReference variableReference) {
//                String modulePath = variableReference.name;
//                var importedFile = this.owner.qualifiedImports.stream().filter(e -> e.name.contains(modulePath)).findFirst();
//                if (importedFile.isPresent()) {
//                    var i = importedFile.get();
//                    var func = i.functions.stream().filter(e -> e.functionType.name.equals(this.name)).findFirst();
//                    if (func.isPresent()) {
//                        functionType = func.get().functionType;
//                    }
//                } else {
//                    Logger.syntaxError(Errors.ModuleNotImported, owner.name, module.getCtx(), module.getCtx().getText());
//                }
//
//            } else {
//                System.err.println("Bad namespace on:" + module);
//                System.exit(81);
//            }
//        }


        return null;
    }
}
