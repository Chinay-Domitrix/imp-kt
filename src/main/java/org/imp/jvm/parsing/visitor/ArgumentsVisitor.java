package org.imp.jvm.parsing.visitor;

import org.imp.jvm.ImpParser;
import org.imp.jvm.ImpParserBaseVisitor;
import org.imp.jvm.compiler.Logger;
import org.imp.jvm.domain.ImpFile;
import org.imp.jvm.domain.scope.Identifier;
import org.imp.jvm.domain.scope.Scope;
import org.imp.jvm.exception.Errors;
import org.imp.jvm.statement.Empty;
import org.imp.jvm.types.BuiltInType;
import org.imp.jvm.types.ListType;
import org.imp.jvm.types.Type;
import org.imp.jvm.types.TypeResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArgumentsVisitor extends ImpParserBaseVisitor<List<Identifier>> {

    public final Scope scope;
    private final ImpFile parent;

    public ArgumentsVisitor(Scope scope, ImpFile parent) {
        this.scope = scope;
        this.parent = parent;
    }

    @Override
    public List<Identifier> visitArguments(ImpParser.ArgumentsContext ctx) {
        var argumentsCtx = ctx.argument();
        var arguments = new ArrayList<Identifier>();

        try {
            for (var argCtx : argumentsCtx) {
                var identifier = new Identifier();
                identifier.name = argCtx.identifier().getText();
                // Todo: type resolution should be moved to the validate() step

                if (argCtx.type() instanceof ImpParser.TypeVarargsContext varargsContext) {
                    Type type = TypeResolver.getFromTypeContext(varargsContext.type(), scope);
                    if (type != null) {
                        identifier.type = new ListType(type);
                    }
                } else {
                    identifier.type = TypeResolver.getFromTypeContext(argCtx.type(), scope);
                }

                if (identifier.type == null) {
                    throw new Error("reeses");
                }

                arguments.add(identifier);
            }
        } catch (Error err) {
            arguments = new ArrayList<>();
            Empty empty = new Empty(ctx, parent.name);
            Logger.syntaxError(Errors.TypeNotFound, empty, ctx.getStop().getText());

        }


        return arguments;
    }
}
