package org.imp.jvm.parsing.visitor;

import org.imp.jvm.ImpParser;
import org.imp.jvm.ImpParserBaseVisitor;
import org.imp.jvm.domain.ImpFile;
import org.imp.jvm.domain.scope.Identifier;
import org.imp.jvm.domain.scope.Scope;
import org.imp.jvm.types.BuiltInType;
import org.imp.jvm.parsing.visitor.statement.StatementVisitor;
import org.imp.jvm.statement.*;
import org.imp.jvm.types.FunctionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImpFileVisitor extends ImpParserBaseVisitor<ImpFile> {
    private final String filename;


    public ImpFileVisitor(String filename) {
        this.filename = filename;
    }



    @Override
    public ImpFile visitProgram(ImpParser.ProgramContext ctx) {
        // get all top level statements in the file
        List<ImpParser.StatementContext> statementContexts = ctx.statement();

        // Root Scope for Static Unit
        Scope staticScope = new Scope();

        // static unit for all non-class statements in the file
        // Todo: remove StaticUnits

        // create an ImpFile node with appropriate children
        var impFile = new ImpFile(filename);


        var mainFunctionType = new FunctionType("main", impFile);
        var main = new Function(mainFunctionType,
                new ArrayList<>(),
                BuiltInType.VOID, new Block());

        Identifier varArgs = new Identifier();
        varArgs.type = BuiltInType.STRING_ARR;
        varArgs.name = "args";
        main.parameters.add(varArgs);
        main.block.scope = staticScope;

        impFile.functions.add(main);

        // handle each statement appropriately
        StatementVisitor statementVisitor = new StatementVisitor(staticScope, impFile);
        for (var statement : statementContexts) {
            Statement s = statement.accept(statementVisitor);
//            System.out.println(s);


            // Split classes out to their own files
            if (s instanceof Struct struct) {
                impFile.structTypes.add(struct.structType);
            } else {
                // For everything else, add to the static class.
                if (s instanceof Function f) {

                    // add function to static class methods
                    impFile.functions.add(f);
                } else {
                    // All other root level nodes go in the main method
                    main.block.statements.add(s);
                }

            }


        }

        var constructorType = new FunctionType("<init>", impFile);
        impFile.functions.add(new Constructor(null, constructorType, Collections.emptyList(), new Block()));


        return impFile;
    }

}
