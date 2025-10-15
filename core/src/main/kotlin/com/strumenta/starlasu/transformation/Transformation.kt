package com.strumenta.starlasu.transformation

import com.strumenta.starlasu.model.ASTNode
import com.strumenta.starlasu.model.GenericErrorNode
import com.strumenta.starlasu.model.Origin
import com.strumenta.starlasu.model.Position
import com.strumenta.starlasu.model.PropertyDescription
import com.strumenta.starlasu.model.asContainment
import com.strumenta.starlasu.model.children
import com.strumenta.starlasu.model.processProperties
import com.strumenta.starlasu.model.withOrigin
import com.strumenta.starlasu.transformation.dummyInstance
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
class NodeFactory<Source, Output : ASTNode>(
    val constructor: (Source, ASTTransformer, NodeFactory<Source, Output>) -> List<Output>,
    var children: MutableMap<String, ChildNodeFactory<Source, *, *>?> = mutableMapOf(),
    var finalizer: (Output) -> Unit = {},
    var skipChildren: Boolean = false,
    var childrenSetAtConstruction: Boolean = false,
) {
    companion object {
        fun <Source, Output : ASTNode> single(
            singleConstructor: (Source, ASTTransformer, NodeFactory<Source, Output>) -> Output?,
            children: MutableMap<String, ChildNodeFactory<Source, *, *>?> = mutableMapOf(),
            finalizer: (Output) -> Unit = {},
            skipChildren: Boolean = false,
            childrenSetAtConstruction: Boolean = false,
        ): NodeFactory<Source, Output> =
            NodeFactory({ source, at, nf ->
                val result = singleConstructor(source, at, nf)
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
     *     on.registerNodeFactory(SASParser.DatasetOptionContext::class) { ctx ->
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
    ): NodeFactory<Source, Output> =
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
    ): NodeFactory<Source, Output> =
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
    ): NodeFactory<Source, Output> =
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
    ): NodeFactory<Source, Output> =
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
    ): NodeFactory<Source, Output> {
        val prefix = if (scopedToType != null) scopedToType.qualifiedName + "#" else ""
        if (set == null) {
            // given we have no setter we MUST set the children at construction
            childrenSetAtConstruction = true
        }

        children[prefix + name] = ChildNodeFactory(prefix + name, get, set, childType)
        return this
    }

    fun withFinalizer(finalizer: (Output) -> Unit): NodeFactory<Source, Output> {
        this.finalizer = finalizer
        return this
    }

    /**
     * Tells the transformer whether this factory already takes care of the node's children and no further computation
     * is desired on that subtree. E.g., when we're mapping an ANTLR parse tree, and we have a context that is only a
     * wrapper over several alternatives, and for some reason those are not labeled alternatives in ANTLR (subclasses),
     * we may configure the transformer as follows:
     *
     * ```kotlin
     * transformer.registerNodeFactory(XYZContext::class) { ctx -> transformer.transform(ctx.children[0]) }
     * ```
     *
     * However, if the result of `transformer.transform(ctx.children[0])` is an instance of a ASTNode with a child
     * for which `withChild` was configured, the transformer will think that it has to populate that child,
     * according to the configuration determined by reflection. When it tries to do so, the "source" of the node will
     * be an instance of `XYZContext` that may not have a child with a corresponding name, and the transformation will
     * fail – or worse, it will map an unrelated node.
     */
    fun skipChildren(skip: Boolean = true): NodeFactory<Source, Output> {
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
data class ChildNodeFactory<Source, Target, Child : Any>(
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
private val NO_CHILD_NODE = ChildNodeFactory<Any, Any, Any>("", { x -> x }, { _, _ -> }, ASTNode::class)

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
        /**
         * Additional issues found during the transformation process.
         */
        val issues: MutableList<Issue> = mutableListOf(),
        @Deprecated("To be removed in Kolasu 1.6")
        val allowGenericNode: Boolean = true,
        val throwOnUnmappedNode: Boolean = false,
        /**
         * When the fault tollerant flag is set, in case a transformation fails we will add a node
         * with the origin FailingASTTransformation. If the flag is not set, then the transformation will just
         * fail.
         */
        val faultTollerant: Boolean = !throwOnUnmappedNode,
        val defaultTransformation: (
            (
                source: Any?,
                parent: ASTNode?,
                expectedType: KClass<out ASTNode>,
                astTransformer: ASTTransformer,
            ) -> List<ASTNode>
        )? = null,
    ) {
        /**
         * Factories that map from source tree node to target tree node.
         */
        val factories = mutableMapOf<KClass<*>, NodeFactory<*, *>>()

        private val _knownClasses = mutableMapOf<String, MutableSet<KClass<*>>>()
        val knownClasses: Map<String, Set<KClass<*>>> = _knownClasses

        /**
         * This ensures that the generated value is a single ASTNode or null.
         */
        @JvmOverloads
        fun transform(
            source: Any?,
            parent: ASTNode? = null,
            expectedType: KClass<out ASTNode> = ASTNode::class,
        ): ASTNode? {
            val result = transformIntoNodes(source, parent, expectedType)
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
            parent: ASTNode? = null,
            expectedType: KClass<out ASTNode> = ASTNode::class,
        ): List<ASTNode> {
            if (source == null) {
                return emptyList()
            }
            if (source is Collection<*>) {
                throw Error("Mapping error: received collection when value was expected")
            }
            val factory = getNodeFactory<Any, ASTNode>(source::class as KClass<Any>)
            val nodes: List<ASTNode>
            if (factory != null) {
                nodes = makeNodes(factory, source, allowGenericNode = allowGenericNode)
                if (!factory.skipChildren && !factory.childrenSetAtConstruction) {
                    nodes.forEach { node -> setChildren(factory, source, node) }
                }
                nodes.forEach { node ->
                    factory.finalizer(node)
                    node.parent = parent
                }
            } else {
                if (defaultTransformation != null) {
                    nodes = defaultTransformation.invoke(source, parent, expectedType, this)
                } else if (allowGenericNode) {
                    val origin = asOrigin(source)
                    nodes =
                        listOf(
                            GenericNode(
                                parent,
                            ).withOrigin(origin),
                        )
                    issues.add(
                        Issue.semantic(
                            "Source node not mapped: ${source::class.qualifiedName}",
                            IssueSeverity.WARNING,
                            origin?.position,
                        ),
                    )
                } else if (expectedType.isDirectlyOrIndirectlyInstantiable() && !throwOnUnmappedNode) {
                    try {
                        val node = expectedType.dummyInstance()
                        node.origin = MissingASTTransformation(asOrigin(source), source, expectedType)
                        nodes = listOf(node)
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
            }
            return nodes
        }

        protected open fun setChildren(
            factory: NodeFactory<Any, ASTNode>,
            source: Any,
            node: ASTNode,
        ) {
            node.processProperties { pd ->
                val childNodeFactory = factory.getChildNodeFactory<Any, ASTNode, Any>(node, pd.name)
                if (childNodeFactory != null) {
                    if (childNodeFactory != NO_CHILD_NODE) {
                        setChild(childNodeFactory, source, node, pd)
                    }
                } else {
                    factory.children[getChildKey(node.nodeType, pd.name)] = NO_CHILD_NODE
                }
            }
        }

        open fun asOrigin(source: Any): Origin? = if (source is Origin) source else null

        protected open fun setChild(
            childNodeFactory: ChildNodeFactory<*, *, *>,
            source: Any,
            node: ASTNode,
            pd: PropertyDescription,
        ) {
            val childFactory = childNodeFactory as ChildNodeFactory<Any, Any, Any>
            val childrenSource = childFactory.get(getSource(node, source))
            val child: Any? =
                if (pd.multiple) {
                    (childrenSource as List<*>?)
                        ?.map {
                            transformIntoNodes(it, node, childFactory.type)
                        }?.flatten() ?: listOf<ASTNode>()
                } else {
                    transform(childrenSource, node)
                }
            try {
                childNodeFactory.set(node, child)
            } catch (e: IllegalArgumentException) {
                throw Error("Could not set child $childNodeFactory", e)
            }
        }

        protected open fun getSource(
            node: ASTNode,
            source: Any,
        ): Any = source

        protected open fun <S : Any, T : ASTNode> makeNodes(
            factory: NodeFactory<S, T>,
            source: S,
            allowGenericNode: Boolean = true,
        ): List<ASTNode> {
            val nodes =
                try {
                    factory.constructor(source, this, factory)
                } catch (e: Exception) {
                    if (allowGenericNode) {
                        listOf(
                            GenericErrorNode(
                                e,
                            ),
                        )
                    } else {
                        throw e
                    }
                }
            nodes.forEach { node ->
                if (node.origin == null) {
                    node.withOrigin(asOrigin(source))
                }
            }
            return nodes
        }

        protected open fun <S : Any, T : ASTNode> getNodeFactory(kClass: KClass<S>): NodeFactory<S, T>? {
            val factory = factories[kClass]
            if (factory != null) {
                return factory as NodeFactory<S, T>
            } else {
                if (kClass == Any::class) {
                    return null
                }
                for (superclass in kClass.superclasses) {
                    val nodeFactory = getNodeFactory<S, T>(superclass as KClass<S>)
                    if (nodeFactory != null) {
                        return nodeFactory
                    }
                }
            }
            return null
        }

        fun <S : Any, T : ASTNode> registerNodeFactory(
            kclass: KClass<S>,
            factory: (S, ASTTransformer, NodeFactory<S, T>) -> T?,
        ): NodeFactory<S, T> {
            val nodeFactory = NodeFactory.single(factory)
            factories[kclass] = nodeFactory
            return nodeFactory
        }

        fun <S : Any, T : ASTNode> registerMultipleNodeFactory(
            kclass: KClass<S>,
            factory: (S, ASTTransformer, NodeFactory<S, T>) -> List<T>,
        ): NodeFactory<S, T> {
            val nodeFactory = NodeFactory(factory)
            factories[kclass] = nodeFactory
            return nodeFactory
        }

        fun <S : Any, T : ASTNode> registerNodeFactory(
            kclass: KClass<S>,
            factory: (S, ASTTransformer) -> T?,
        ): NodeFactory<S, T> = registerNodeFactory(kclass) { source, transformer, _ -> factory(source, transformer) }

        inline fun <reified S : Any, T : ASTNode> registerNodeFactory(
            crossinline factory: S.(ASTTransformer) -> T?,
        ): NodeFactory<S, T> = registerNodeFactory(S::class) { source, transformer, _ -> source.factory(transformer) }

        /**
         * We need T to be reified because we may need to install dummy classes of T.
         */
        inline fun <S : Any, reified T : ASTNode> registerNodeFactory(
            kclass: KClass<S>,
            crossinline factory: (S) -> T?,
        ): NodeFactory<S, T> =
            registerNodeFactory(kclass) { input, _, _ ->
                try {
                    factory(input)
                } catch (t: NotImplementedError) {
                    if (faultTollerant) {
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
                    if (faultTollerant) {
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

        fun <S : Any, T : ASTNode> registerMultipleNodeFactory(
            kclass: KClass<S>,
            factory: (S) -> List<T>,
        ): NodeFactory<S, T> = registerMultipleNodeFactory(kclass) { input, _, _ -> factory(input) }

        inline fun <reified S : Any, reified T : ASTNode> registerNodeFactory(): NodeFactory<S, T> =
            registerNodeFactory(S::class, T::class)

        inline fun <reified S : Any> notTranslateDirectly(): NodeFactory<S, ASTNode> =
            registerNodeFactory<S, ASTNode> {
                throw java.lang.IllegalStateException(
                    "A ASTNode of this type (${this.javaClass.canonicalName}) should never be translated directly. " +
                        "It is expected that the container will not delegate the translation of this node but it " +
                        "will handle it directly",
                )
            }

        private fun <S : Any, T : ASTNode> parameterValue(
            kParameter: KParameter,
            source: S,
            childNodeFactory: ChildNodeFactory<Any, T, Any>,
        ): ParameterValue =
            when (val childSource = childNodeFactory.get.invoke(source)) {
                null -> {
                    AbsentParameterValue
                }

                is List<*> -> {
                    PresentParameterValue(
                        childSource
                            .map { transformIntoNodes(it) }
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
                        PresentParameterValue(transformIntoNodes(childSource))
                    } else {
                        PresentParameterValue(transform(childSource))
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
        fun <S : Any, T : ASTNode> registerNodeFactory(
            source: KClass<S>,
            target: KClass<T>,
            nodeType: String = target.qualifiedName!!,
        ): NodeFactory<S, T> {
            registerKnownClass(target)
            // We are looking for any constructor with does not take parameters or have default
            // values for all its parameters
            val emptyLikeConstructor = target.constructors.find { it.parameters.all { param -> param.isOptional } }
            val nodeFactory =
                NodeFactory.single(
                    { source: S, _, thisFactory ->
                        if (target.isSealed) {
                            throw IllegalStateException("Unable to instantiate sealed class $target")
                        }

                        fun getConstructorParameterValue(kParameter: KParameter): ParameterValue {
                            try {
                                val childNodeFactory =
                                    thisFactory.getChildNodeFactory<Any, T, Any>(
                                        nodeType,
                                        kParameter.name!!,
                                    )
                                if (childNodeFactory == null) {
                                    if (kParameter.isOptional) {
                                        return AbsentParameterValue
                                    }
                                    throw java.lang.IllegalStateException(
                                        "We do not know how to produce parameter ${kParameter.name!!} for $target",
                                    )
                                } else {
                                    return parameterValue(kParameter, source, childNodeFactory)
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
                            if (thisFactory.childrenSetAtConstruction) {
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
            factories[source] = nodeFactory
            return nodeFactory
        }

        /**
         * Here the method needs to be inlined and the type parameter reified as in the invoked
         * registerNodeFactory we need to access the nodeClass
         */
        inline fun <reified T : ASTNode> registerIdentityTransformation(nodeClass: KClass<T>) =
            registerNodeFactory(nodeClass) { node -> node }.skipChildren()

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

private fun <Source : Any, Target : ASTNode, Child : Any> NodeFactory<*, *>.getChildNodeFactory(
    node: Target,
    parameterName: String,
): ChildNodeFactory<Source, Target, Child>? = getChildNodeFactory(node.nodeType, parameterName)

private fun <Source : Any, Target : ASTNode, Child : Any> NodeFactory<*, *>.getChildNodeFactory(
    nodeType: String,
    parameterName: String,
): ChildNodeFactory<Source, Target, Child>? {
    val childKey = getChildKey(nodeType, parameterName)
    var childNodeFactory = this.children[childKey]
    if (childNodeFactory == null) {
        childNodeFactory = this.children[parameterName]
    }
    return childNodeFactory as ChildNodeFactory<Source, Target, Child>?
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
