package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.Origin
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.PropertyDescription
import com.strumenta.starlasu.model.asContainment
import com.strumenta.starlasu.model.children
import com.strumenta.starlasu.model.processProperties
import com.strumenta.starlasu.model.withOrigin
import com.strumenta.starlasu.validation.Issue
import com.strumenta.starlasu.validation.IssueSeverity
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.superclasses

/**
 * Factory that, given a tree node, will instantiate the corresponding transformed node.
 */
class Transform<Source, Output : ASTNode>(
    val constructor: (Source, TransformationContext, ASTTransformer, Transform<Source, Output>) -> List<Output>,
    var children: MutableMap<String, ChildTransform<Source, *, *>?> = mutableMapOf(),
    var finalizer: (Output, TransformationContext) -> Unit = { _, _ -> },
    var skipChildren: Boolean = false,
    var childrenSetAtConstruction: Boolean = false,
) {
    companion object {
        fun <Source, Output : ASTNode> single(
            singleConstructor: (Source, TransformationContext, ASTTransformer, Transform<Source, Output>) -> Output?,
            children: MutableMap<String, ChildTransform<Source, *, *>?> = mutableMapOf(),
            finalizer: (Output, TransformationContext) -> Unit = { _, _ -> },
            skipChildren: Boolean = false,
            childrenSetAtConstruction: Boolean = false,
        ): Transform<Source, Output> =
            Transform({ source, ctx, at, nf ->
                val result = singleConstructor(source, ctx, at, nf)
                if (result == null) emptyList() else listOf(result)
            }, children, finalizer, skipChildren, childrenSetAtConstruction)
    }

    /**
     * Specify how to convert a child. The value obtained from the conversion could either be used
     * as a constructor parameter when instantiating the parent, or be used to set the value after
     * the parent has been instantiated.
     *
     * Example using the scopedToType parameter:
     * ```
     *     on.registerTransform(SASParser.DatasetOptionContext::class) { ctx ->
     *         when {
     *             ...
     *         }
     *     }
     *         .withChild(SASParser.DatasetOptionContext::macroStatementStrict, ComputedDatasetOption::computedWith, ComputedDatasetOption::class)
     *         .withChild(SASParser.DatasetOptionContext::variableList, DropDatasetOption::variables, DropDatasetOption::class)
     *         .withChild(SASParser.DatasetOptionContext::variableList, KeepDatasetOption::variables, KeepDatasetOption::class)
     *         .withChild(SASParser.DatasetOptionContext::variableList, InDatasetOption::variables, InDatasetOption::class)
     *         .withChild("indexDatasetOption.variables", IndexDatasetOption::variables, IndexDatasetOption::class)
     *  ```
     *
     *  Please note that we cannot merge this method with the variant without the type (making the type optional),
     *  as it would not permit to specify the lambda outside the list of method parameters.
     */
    fun withChild(
        targetProperty: KMutableProperty1<*, *>,
        sourceAccessor: Source.() -> Any?,
        scopedToType: KClass<*>,
    ): Transform<Source, Output> =
        withChild(
            get = { source -> source.sourceAccessor() },
            set = (targetProperty as KMutableProperty1<Any, Any?>)::set,
            targetProperty.name,
            scopedToType,
            getPropertyType(targetProperty),
        )

    private fun getPropertyType(targetProperty: KProperty1<out Any, *>): KClass<out ASTNode> {
        val returnType = targetProperty.asContainment().type
        return if (returnType.isSubclassOf(ASTNode::class)) {
            returnType as KClass<out ASTNode>
        } else {
            ASTNode::class
        }
    }

    /**
     * Specify how to convert a child. The value obtained from the conversion could either be used
     * as a constructor parameter when instantiating the parent, or be used to set the value after
     * the parent has been instantiated.
     */
    fun withChild(
        targetProperty: KMutableProperty1<out Any, *>,
        sourceAccessor: Source.() -> Any?,
    ): Transform<Source, Output> =
        withChild(
            get = { source -> source.sourceAccessor() },
            set = (targetProperty as KMutableProperty1<Any, Any?>)::set,
            targetProperty.name,
            null,
            getPropertyType(targetProperty),
        )

    /**
     * Specify how to convert a child. The value obtained from the conversion can only be used
     * as a constructor parameter when instantiating the parent. It cannot be used to set the value after
     * the parent has been instantiated, because the property is not mutable.
     */
    fun withChild(
        targetProperty: KProperty1<out Any, *>,
        sourceAccessor: Source.() -> Any?,
    ): Transform<Source, Output> =
        withChild<Output, Any>(
            get = { source -> source.sourceAccessor() },
            null,
            targetProperty.name,
            null,
            getPropertyType(targetProperty),
        )

    /**
     * Specify how to convert a child. The value obtained from the conversion can only be used
     * as a constructor parameter when instantiating the parent. It cannot be used to set the value after
     * the parent has been instantiated, because the property is not mutable.
     *
     * Please note that we cannot merge this method with the variant without the type (making the type optional),
     * as it would not permit to specify the lambda outside the list of method parameters.
     */
    fun withChild(
        targetProperty: KProperty1<out Any, *>,
        sourceAccessor: Source.() -> Any?,
        scopedToType: KClass<*>,
    ): Transform<Source, Output> =
        withChild<Output, ASTNode>(
            get = { source -> source.sourceAccessor() },
            null,
            targetProperty.name,
            scopedToType,
            getPropertyType(targetProperty),
        )

    /**
     * Specify how to convert a child. The value obtained from the conversion could either be used
     * as a constructor parameter when instantiating the parent, or be used to set the value after
     * the parent has been instantiated.
     */
    @JvmOverloads
    fun <Target : Output, Child : Any> withChild(
        get: (Source) -> Any?,
        set: ((Target, Child?) -> Unit)?,
        name: String,
        scopedToType: KClass<*>? = null,
        childType: KClass<out ASTNode> = ASTNode::class,
    ): Transform<Source, Output> {
        val prefix = if (scopedToType != null) scopedToType.qualifiedName + "#" else ""
        if (set == null) {
            // given we have no setter we MUST set the children at construction
            childrenSetAtConstruction = true
        }

        children[prefix + name] = ChildTransform(prefix + name, get, set, childType)
        return this
    }

    fun withFinalizer(finalizer: (Output, TransformationContext) -> Unit): Transform<Source, Output> {
        this.finalizer = finalizer
        return this
    }

    fun withFinalizer(finalizer: (Output) -> Unit): Transform<Source, Output> {
        this.finalizer = { n, _ -> finalizer(n) }
        return this
    }

    /**
     * Tells the transformer whether this factory already takes care of the node's children and no further computation
     * is desired on that subtree. E.g., when we're mapping an ANTLR parse tree, and we have a context that is only a
     * wrapper over several alternatives, and for some reason those are not labeled alternatives in ANTLR (subclasses),
     * we may configure the transformer as follows:
     *
     * ```kotlin
     * transformer.registerTransform(XYZContext::class) { ctx -> transformer.transform(ctx.children[0]) }
     * ```
     *
     * However, if the result of `transformer.transform(ctx.children[0])` is an instance of a ASTNode with a child
     * for which `withChild` was configured, the transformer will think that it has to populate that child,
     * according to the configuration determined by reflection. When it tries to do so, the "source" of the node will
     * be an instance of `XYZContext` that may not have a child with a corresponding name, and the transformation will
     * fail – or worse, it will map an unrelated node.
     */
    fun skipChildren(skip: Boolean = true): Transform<Source, Output> {
        this.skipChildren = skip
        return this
    }

    fun getter(path: String) =
        { src: Source ->
            var sub: Any? = src
            for (elem in path.split('.')) {
                if (sub == null) {
                    break
                }
                sub = getSubExpression(sub, elem)
            }
            sub
        }

    private fun getSubExpression(
        src: Any,
        elem: String,
    ): Any? =
        if (src is Collection<*>) {
            src.map { getSubExpression(it!!, elem) }
        } else {
            val sourceProp = src::class.memberProperties.find { it.name == elem }
            if (sourceProp == null) {
                val sourceMethod =
                    src::class.memberFunctions.find { it.name == elem && it.parameters.size == 1 }
                        ?: throw Error("$elem not found in $src (${src::class})")
                sourceMethod.call(src)
            } else {
                (sourceProp as KProperty1<Any, Any>).get(src)
            }
        }
}

/**
 * Information on how to retrieve a child node.
 *
 * The setter could be null, if the property is not mutable. In that case the value
 * must necessarily be passed when constructing the parent.
 *
 * @param type the property type if single, the collection's element type if multiple
 */
data class ChildTransform<Source, Target, Child : Any>(
    val name: String,
    val get: (Source) -> Any?,
    val setter: ((Target, Child?) -> Unit)?,
    val type: KClass<out ASTNode>,
) {
    fun set(
        node: Target,
        child: Child?,
    ) {
        if (setter == null) {
            throw IllegalStateException("Unable to set value $name in $node")
        }
        try {
            setter!!(node, child)
        } catch (e: Exception) {
            throw Exception("$name could not set child $child of $node using $setter", e)
        }
    }
}

/**
 * Sentinel value used to represent the information that a given property is not a child node.
 */
private val NO_CHILD_NODE = ChildTransform<Any, Any, Any>("", { x -> x }, { _, _ -> }, ASTNode::class)

open class TransformationContext
    @JvmOverloads
    constructor(
        /**
         * Additional issues found during the transformation process.
         */
        val issues: MutableList<Issue> = mutableListOf(),
        var parent: ASTNode? = null,
    ) {
        fun addIssue(
            message: String,
            severity: IssueSeverity = IssueSeverity.ERROR,
            position: Position? = null,
        ): Issue {
            val issue = Issue.semantic(message, severity, position)
            issues.add(issue)
            return issue
        }
    }

/**
 * Implementation of a tree-to-tree transformation. For each source node type, we can register a factory that knows how
 * to create a transformed node. Then, this transformer can read metadata in the transformed node to recursively
 * transform and assign children.
 * If no factory is provided for a source node type, a GenericNode is created, and the processing of the subtree stops
 * there.
 */
open class ASTTransformer
    @JvmOverloads
    constructor(
        val throwOnUnmappedNode: Boolean = false,
        /**
         * When the fault-tolerant flag is set, in case a transformation fails we will add a node
         * with the origin FailingASTTransformation. If the flag is not set, then the transformation will just
         * fail.
         */
        val faultTolerant: Boolean = !throwOnUnmappedNode,
        val defaultTransformation: (
            (
                source: Any?,
                context: TransformationContext,
                expectedType: KClass<out ASTNode>,
                astTransformer: ASTTransformer,
            ) -> List<ASTNode>
        )? = null,
    ) {
        /**
         * Factories that map from source tree node to target tree node.
         */
        val transforms = mutableMapOf<KClass<*>, Transform<*, *>>()

        private val _knownClasses = mutableMapOf<String, MutableSet<KClass<*>>>()
        val knownClasses: Map<String, Set<KClass<*>>> = _knownClasses

        /**
         * This ensures that the generated value is a single ASTNode or null.
         */
        @JvmOverloads
        fun transform(
            source: Any?,
            context: TransformationContext = TransformationContext(),
            expectedType: KClass<out ASTNode> = ASTNode::class,
        ): ASTNode? {
            val result = transformIntoNodes(source, context, expectedType)
            return when (result.size) {
                0 -> null
                1 -> {
                    val node = result.first()
                    require(node is ASTNode)
                    node
                }

                else -> throw IllegalStateException(
                    "Cannot transform into a single ASTNode as multiple nodes where produced",
                )
            }
        }

        /**
         * Performs the transformation of a node and, recursively, its descendants.
         */
        @JvmOverloads
        open fun transformIntoNodes(
            source: Any?,
            context: TransformationContext,
            expectedType: KClass<out ASTNode> = ASTNode::class,
        ): List<ASTNode> {
            if (source == null) {
                return emptyList()
            }
            if (source is Collection<*>) {
                throw Error("Mapping error: received collection when value was expected")
            }
            val transform = getTransform<Any, ASTNode>(source::class as KClass<Any>)
            val nodes: List<ASTNode>
            if (transform != null) {
                nodes = makeNodes(transform, source, context)
                val parent = context.parent
                if (!transform.skipChildren && !transform.childrenSetAtConstruction) {
                    nodes.forEach { node ->
                        context.parent = node
                        setChildren(transform, source, context)
                    }
                }
                context.parent = parent
                nodes.forEach { node ->
                    transform.finalizer(node, context)
                    node.parent = parent
                }
            } else {
                if (defaultTransformation == null && throwOnUnmappedNode) {
                    throw IllegalStateException(
                        "Unable to translate node $source (class ${source::class.qualifiedName})",
                    )
                }
                nodes = defaultNodes(source, context, expectedType)
                nodes.filter { it.origin == null }.forEach { node ->
                    node.origin = MissingASTTransformation(asOrigin(source), source, expectedType)
                }
            }
            return nodes
        }

        protected fun defaultNodes(
            source: Any,
            context: TransformationContext,
            expectedType: KClass<out ASTNode>,
            nullable: Boolean = false,
        ): List<ASTNode> =
            defaultTransformation?.invoke(source, context, expectedType, this)
                ?: if (nullable) {
                    listOf()
                } else if (expectedType.isDirectlyOrIndirectlyInstantiable()) {
                    try {
                        val node = expectedType.dummyInstance()
                        listOf(node)
                    } catch (e: Exception) {
                        throw IllegalStateException(
                            "Unable to instantiate desired node type ${expectedType.qualifiedName}",
                            e,
                        )
                    }
                } else {
                    throw IllegalStateException(
                        "Unable to translate node $source (class ${source::class.qualifiedName})",
                    )
                }

        protected open fun setChildren(
            transform: Transform<Any, ASTNode>,
            source: Any,
            context: TransformationContext,
        ) {
            val node = context.parent!!
            node.processProperties { pd ->
                val childTransform = transform.getChildTransform<Any, ASTNode, Any>(node, pd.name)
                if (childTransform != null) {
                    if (childTransform != NO_CHILD_NODE) {
                        setChild(childTransform, source, context, pd)
                    }
                } else {
                    transform.children[getChildKey(node.nodeType, pd.name)] = NO_CHILD_NODE
                }
            }
        }

        open fun asOrigin(source: Any): Origin? = if (source is Origin) source else null

        protected open fun setChild(
            childTransform: ChildTransform<*, *, *>,
            source: Any,
            context: TransformationContext,
            pd: PropertyDescription,
        ) {
            val node = context.parent!!
            val childFactory = childTransform as ChildTransform<Any, Any, Any>
            val childrenSource = childFactory.get(getSource(node, source))
            val child: Any? =
                if (pd.multiple) {
                    (childrenSource as List<*>?)
                        ?.map {
                            transformIntoNodes(it, context, childFactory.type)
                        }?.flatten() ?: listOf<ASTNode>()
                } else {
                    transform(childrenSource, context)
                }
            try {
                childTransform.set(node, child)
            } catch (e: IllegalArgumentException) {
                throw Error("Could not set child $childTransform", e)
            }
        }

        protected open fun getSource(
            node: ASTNode,
            source: Any,
        ): Any = source

        protected open fun <S : Any, T : ASTNode> makeNodes(
            transform: Transform<S, T>,
            source: S,
            context: TransformationContext,
        ): List<ASTNode> {
            val nodes = transform.constructor(source, context, this, transform)
            nodes.forEach { node ->
                if (node.origin == null) {
                    node.withOrigin(asOrigin(source))
                }
            }
            return nodes
        }

        protected open fun <S : Any, T : ASTNode> getTransform(kClass: KClass<S>): Transform<S, T>? {
            val transform = transforms[kClass]
            if (transform != null) {
                return transform as Transform<S, T>
            } else {
                if (kClass == Any::class) {
                    return null
                }
                for (superclass in kClass.superclasses) {
                    val transform = getTransform<S, T>(superclass as KClass<S>)
                    if (transform != null) {
                        return transform
                    }
                }
            }
            return null
        }

        fun <S : Any, T : ASTNode> registerTransform(
            kclass: KClass<S>,
            factory: (S, TransformationContext, ASTTransformer, Transform<S, T>) -> T?,
        ): Transform<S, T> {
            val transform = Transform.single(factory)
            transforms[kclass] = transform
            return transform
        }

        fun <S : Any, T : ASTNode> registerMultipleTransform(
            kclass: KClass<S>,
            factory: (S, TransformationContext, ASTTransformer, Transform<S, T>) -> List<T>,
        ): Transform<S, T> {
            val transform = Transform(factory)
            transforms[kclass] = transform
            return transform
        }

        fun <S : Any, T : ASTNode> registerTransform(
            kclass: KClass<S>,
            factory: (S, TransformationContext, ASTTransformer) -> T?,
        ): Transform<S, T> =
            registerTransform(kclass) { source, context, transformer, _ -> factory(source, context, transformer) }

        fun <S : Any, T : ASTNode> registerTransform(
            kclass: KClass<S>,
            factory: (S, TransformationContext) -> T?,
        ): Transform<S, T> = registerTransform(kclass) { source, context, _, _ -> factory(source, context) }

        inline fun <reified S : Any, T : ASTNode> registerTransform(
            crossinline factory: S.(TransformationContext) -> T?,
        ): Transform<S, T> = registerTransform(S::class) { source, context, _, _ -> source.factory(context) }

        /**
         * We need T to be reified because we may need to install dummy classes of T.
         */
        inline fun <S : Any, reified T : ASTNode> registerTransform(
            kclass: KClass<S>,
            crossinline factory: (S) -> T?,
        ): Transform<S, T> =
            registerTransform(kclass) { input, _, _, _ ->
                try {
                    factory(input)
                } catch (t: NotImplementedError) {
                    if (faultTolerant) {
                        val node = T::class.dummyInstance()
                        node.origin =
                            FailingASTTransformation(
                                asOrigin(input),
                                "Failed to transform $input into $kclass because the implementation is not complete " +
                                    "(${t.message}",
                            )
                        node
                    } else {
                        throw RuntimeException("Failed to transform $input into $kclass", t)
                    }
                } catch (e: Exception) {
                    if (faultTolerant) {
                        val node = T::class.dummyInstance()
                        node.origin =
                            FailingASTTransformation(
                                asOrigin(input),
                                "Failed to transform $input into $kclass because of an error (${e.message})",
                            )
                        node
                    } else {
                        throw RuntimeException("Failed to transform $input into $kclass", e)
                    }
                }
            }

        fun <S : Any, T : ASTNode> registerMultipleTransform(
            kclass: KClass<S>,
            factory: (S) -> List<T>,
        ): Transform<S, T> = registerMultipleTransform(kclass) { input, _, _, _ -> factory(input) }

        inline fun <reified S : Any, reified T : ASTNode> registerTransform(): Transform<S, T> =
            registerTransform(S::class, T::class)

        inline fun <reified S : Any> notTranslateDirectly(): Transform<S, ASTNode> =
            registerTransform<S, ASTNode> {
                throw java.lang.IllegalStateException(
                    "A ASTNode of this type (${this.javaClass.canonicalName}) should never be translated directly. " +
                        "It is expected that the container will not delegate the translation of this node but it " +
                        "will handle it directly",
                )
            }

        private fun <S : Any, T : ASTNode> parameterValue(
            kParameter: KParameter,
            source: S,
            childTransform: ChildTransform<Any, T, Any>,
            context: TransformationContext,
        ): ParameterValue =
            when (val childSource = childTransform.get.invoke(source)) {
                null -> {
                    AbsentParameterValue
                }

                is List<*> -> {
                    PresentParameterValue(
                        childSource
                            .map { transformIntoNodes(it, context) }
                            .flatten()
                            .toMutableList(),
                    )
                }

                is String -> {
                    PresentParameterValue(childSource)
                }

                else -> {
                    if (kParameter.type == String::class.createType()) {
                        val string = asString(childSource)
                        if (string != null) {
                            PresentParameterValue(string)
                        } else {
                            AbsentParameterValue
                        }
                    } else if ((kParameter.type.classifier as? KClass<*>)?.isSubclassOf(Collection::class) == true) {
                        PresentParameterValue(transformIntoNodes(childSource, context))
                    } else {
                        PresentParameterValue(transform(childSource, context))
                    }
                }
            }

        protected open fun asString(source: Any): String? = null

        /**
         * Registers a factory that will be used to translate nodes of type S.
         *
         * @param source the class of the source node
         * @param target the class of the target node
         * @param nodeType the [ASTNode.nodeType] of the target node. Normally, the node type is the same as the class name,
         * however, [ASTNode] subclasses may want to override it, and in that case, the parameter must be provided explicitly.
         */
        fun <S : Any, T : ASTNode> registerTransform(
            source: KClass<S>,
            target: KClass<T>,
            nodeType: String = target.qualifiedName!!,
        ): Transform<S, T> {
            registerKnownClass(target)
            // We are looking for any constructor with does not take parameters or have default
            // values for all its parameters
            val emptyLikeConstructor = target.constructors.find { it.parameters.all { param -> param.isOptional } }
            val transform =
                Transform.single(
                    { source: S, context, _, thisTransform ->
                        if (target.isSealed) {
                            throw IllegalStateException("Unable to instantiate sealed class $target")
                        }

                        fun getConstructorParameterValue(kParameter: KParameter): ParameterValue {
                            try {
                                val childTransform =
                                    thisTransform.getChildTransform<Any, T, Any>(
                                        nodeType,
                                        kParameter.name!!,
                                    )
                                if (childTransform == null) {
                                    if (kParameter.isOptional) {
                                        return AbsentParameterValue
                                    }
                                    throw java.lang.IllegalStateException(
                                        "We do not know how to produce parameter ${kParameter.name!!} for $target",
                                    )
                                } else {
                                    return parameterValue(kParameter, source, childTransform, context)
                                }
                            } catch (t: Throwable) {
                                throw RuntimeException(
                                    "Issue while populating parameter ${kParameter.name} in " +
                                        "constructor ${target.qualifiedName}.${target.preferredConstructor()}",
                                    t,
                                )
                            }
                        }
                        // We check `childrenSetAtConstruction` and not `emptyLikeConstructor` because, while we set this value
                        // initially based on `emptyLikeConstructor` being equal to null, this can be later changed in `withChild`,
                        // so we should really check the value that `childrenSetAtConstruction` time has when we actually invoke
                        // the factory.
                        val instance =
                            if (thisTransform.childrenSetAtConstruction) {
                                val constructor = target.preferredConstructor()
                                val constructorParamValues =
                                    constructor.parameters
                                        .map { it to getConstructorParameterValue(it) }
                                        .filter { it.second is PresentParameterValue }
                                        .associate { it.first to (it.second as PresentParameterValue).value }
                                try {
                                    val instance = constructor.callBy(constructorParamValues)
                                    instance.children.forEach { child -> child.parent = instance }
                                    instance
                                } catch (t: Throwable) {
                                    throw RuntimeException(
                                        "Invocation of constructor $constructor failed. " +
                                            "We passed: ${
                                                constructorParamValues.map { "${it.key.name}=${it.value}" }
                                                    .joinToString(", ")
                                            }",
                                        t,
                                    )
                                }
                            } else {
                                if (emptyLikeConstructor == null) {
                                    throw RuntimeException(
                                        "childrenSetAtConstruction is not set but there is no empty like " +
                                            "constructor for $target",
                                    )
                                }
                                target.createInstance()
                            }
                        if (instance.nodeType != nodeType) {
                            throw RuntimeException(
                                "Configuration exception: nodeType of instance of $target is " +
                                    "${instance.nodeType} instead of $nodeType",
                            )
                        }
                        instance
                    },
                    // If I do not have an emptyLikeConstructor, then I am forced to invoke a constructor with parameters and
                    // therefore setting the children at construction time.
                    // Note that we are assuming that either we set no children at construction time or we set all of them
                    childrenSetAtConstruction = emptyLikeConstructor == null,
                )
            transforms[source] = transform
            return transform
        }

        /**
         * Here the method needs to be inlined and the type parameter reified as in the invoked
         * registerTransform we need to access the nodeClass
         */
        inline fun <reified T : ASTNode> registerIdentityTransformation(nodeClass: KClass<T>) =
            registerTransform(nodeClass) { node -> node }.skipChildren()

        private fun registerKnownClass(target: KClass<*>) {
            val qualifiedName = target.qualifiedName
            val packageName =
                if (qualifiedName != null) {
                    val endIndex = qualifiedName.lastIndexOf('.')
                    if (endIndex >= 0) {
                        qualifiedName.substring(0, endIndex)
                    } else {
                        ""
                    }
                } else {
                    ""
                }
            val set = _knownClasses.computeIfAbsent(packageName) { mutableSetOf() }
            set.add(target)
        }
    }

private fun <Source : Any, Target : ASTNode, Child : Any> Transform<*, *>.getChildTransform(
    node: Target,
    parameterName: String,
): ChildTransform<Source, Target, Child>? = getChildTransform(node.nodeType, parameterName)

private fun <Source : Any, Target : ASTNode, Child : Any> Transform<*, *>.getChildTransform(
    nodeType: String,
    parameterName: String,
): ChildTransform<Source, Target, Child>? {
    val childKey = getChildKey(nodeType, parameterName)
    var childTransform = this.children[childKey]
    if (childTransform == null) {
        childTransform = this.children[parameterName]
    }
    return childTransform as ChildTransform<Source, Target, Child>?
}

private fun <Target : Any> getChildKey(
    nodeClass: KClass<out Target>,
    parameterName: String,
): String = getChildKey(nodeClass.qualifiedName!!, parameterName)

private fun getChildKey(
    nodeType: String,
    parameterName: String,
): String = "$nodeType#$parameterName"

private sealed class ParameterValue

private class PresentParameterValue(
    val value: Any?,
) : ParameterValue()

private object AbsentParameterValue : ParameterValue()
