package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.ASTNode;
import com.strumenta.starlasu.transformation.Transform;
import com.strumenta.starlasu.transformation.TransformationContext;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.functions.Function4;
import kotlin.reflect.KClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

import static kotlin.jvm.JvmClassMappingKt.getKotlinClass;

public class ASTTransformer extends com.strumenta.starlasu.transformation.ASTTransformer {
    public ASTTransformer() {
        super();
    }

    public ASTTransformer(boolean throwOnUnmappedNode) {
        super(throwOnUnmappedNode);
    }

    public ASTTransformer(boolean throwOnUnmappedNode, boolean faultTolerant) {
        super(throwOnUnmappedNode, faultTolerant);
    }

    public ASTTransformer(boolean throwOnUnmappedNode, boolean faultTolerant, @Nullable Function4<Object, TransformationContext, ? super KClass<? extends ASTNode>, ? super com.strumenta.starlasu.transformation.ASTTransformer, ? extends List<? extends ASTNode>> defaultTransformation) {
        super(throwOnUnmappedNode, faultTolerant, defaultTransformation);
    }

    protected <S, T extends ASTNode> @NotNull Transform<S, T> registerNodeFactory(Class<S> source, Class<T> target) {
        return registerNodeFactory(source, target, target.getName());
    }

    protected <S, T extends ASTNode> @NotNull Transform<S, T> registerNodeFactory(
            Class<S> source, Class<T> target, String nodeType
    ) {
        return registerTransform(getKotlinClass(source), getKotlinClass(target), nodeType);
    }

    protected <S, T extends ASTNode> Transform<S, T> registerNodeFactory(Class<S> source, Function1<S, T> function) {
        return registerTransform(getKotlinClass(source), (s, t) -> function.invoke(s));
    }

    protected <S, T extends ASTNode> Transform<S, T> registerNodeFactory(
            Class<S> source, 
            Function3<S, TransformationContext, ? super com.strumenta.starlasu.transformation.ASTTransformer, T> function
    ) {
        return registerTransform(getKotlinClass(source), function);
    }

    /**
     * Makes it less verbose to call withChild methods, allowing to use method references instead of closures.
     * Example:
     * <code>.withChild(Constant_bodyContext::base_type, setter(ModelSpecificConstant::setBaseDatatype), "base type");</code>
     * @param consumer the wrapped consumer, typically a setter method reference
     * @return the corresponding Kotlin function of two parameters and Unit return type
     * @param <P> class of the parent
     * @param <C> class of the child
     */
    public static @NotNull <P, C> Function2<P, C, Unit> setter(BiConsumer<P, C> consumer) {
        return (parent, child) -> {
            consumer.accept(parent, child);
            return Unit.INSTANCE;
        };
    }

}
