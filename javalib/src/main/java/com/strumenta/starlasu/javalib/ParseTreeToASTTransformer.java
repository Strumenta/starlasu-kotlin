package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.ASTNode;
import com.strumenta.starlasu.transformation.ASTTransformer;
import com.strumenta.starlasu.transformation.FaultTolerance;
import com.strumenta.starlasu.transformation.TransformationRule;
import com.strumenta.starlasu.transformation.TransformationContext;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function3;
import org.jetbrains.annotations.NotNull;

import static kotlin.jvm.JvmClassMappingKt.getKotlinClass;

public class ParseTreeToASTTransformer extends com.strumenta.starlasu.mapping.ParseTreeToASTTransformer {
    public ParseTreeToASTTransformer() {
        super();
    }

    public ParseTreeToASTTransformer(FaultTolerance faultTolerance) {
        super(faultTolerance);
    }
    
    protected <S, T extends ASTNode> @NotNull TransformationRule<S, T> registerNodeFactory(Class<S> source, Class<T> target) {
        return registerNodeFactory(source, target, target.getName());
    }

    protected <S, T extends ASTNode> @NotNull TransformationRule<S, T> registerNodeFactory(
            Class<S> source, Class<T> target, String nodeType
    ) {
        return registerRule(getKotlinClass(source), getKotlinClass(target), nodeType);
    }

    protected <S, T extends ASTNode> TransformationRule<S, T> registerNodeFactory(Class<S> source, Function1<S, T> function) {
        return registerRule(getKotlinClass(source), (s, t) -> function.invoke(s));
    }

    protected <S, T extends ASTNode> TransformationRule<S, T> registerNodeFactory(
            Class<S> source,
            Function3<S, TransformationContext, ? super ASTTransformer, T> function
    ) {
        return registerRule(getKotlinClass(source), function);
    }

}
