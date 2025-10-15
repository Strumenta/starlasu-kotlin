package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.BaseASTNode;
import com.strumenta.starlasu.transformation.NodeFactory;
import com.strumenta.starlasu.validation.Issue;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function4;
import kotlin.reflect.KClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

import static kotlin.jvm.JvmClassMappingKt.getKotlinClass;

public class ASTTransformer extends com.strumenta.starlasu.transformation.ASTTransformer {
    public ASTTransformer(@NotNull List<Issue> issues) {
        super(issues);
    }

    public ASTTransformer(@NotNull List<Issue> issues, boolean allowGenericNode) {
        super(issues, allowGenericNode);
    }

    public ASTTransformer(@NotNull List<Issue> issues, boolean allowGenericNode, boolean throwOnUnmappedNode) {
        super(issues, allowGenericNode, throwOnUnmappedNode);
    }

    public ASTTransformer(@NotNull List<Issue> issues, boolean allowGenericNode, boolean throwOnUnmappedNode, boolean faultTollerant) {
        super(issues, allowGenericNode, throwOnUnmappedNode, faultTollerant);
    }

    public ASTTransformer(@NotNull List<Issue> issues, boolean allowGenericNode, boolean throwOnUnmappedNode, boolean faultTollerant, @Nullable Function4<Object, ? super BaseASTNode, ? super KClass<? extends BaseASTNode>, ? super com.strumenta.starlasu.transformation.ASTTransformer, ? extends List<? extends BaseASTNode>> defaultTransformation) {
        super(issues, allowGenericNode, throwOnUnmappedNode, faultTollerant, defaultTransformation);
    }

    protected <S, T extends BaseASTNode> @NotNull NodeFactory<S, T> registerNodeFactory(Class<S> source, Class<T> target) {
        return registerNodeFactory(source, target, target.getName());
    }

    protected <S, T extends BaseASTNode> @NotNull NodeFactory<S, T> registerNodeFactory(
            Class<S> source, Class<T> target, String nodeType
    ) {
        return registerNodeFactory(getKotlinClass(source), getKotlinClass(target), nodeType);
    }

    protected <S, T extends BaseASTNode> NodeFactory<S, T> registerNodeFactory(Class<S> source, Function1<S, T> function) {
        return registerNodeFactory(getKotlinClass(source), (s, t) -> function.invoke(s));
    }

    protected <S, T extends BaseASTNode> NodeFactory<S, T> registerNodeFactory(Class<S> source, Function2<S, ? super com.strumenta.starlasu.transformation.ASTTransformer, T> function) {
        return registerNodeFactory(getKotlinClass(source), function);
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
