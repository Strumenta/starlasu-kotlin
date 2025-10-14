package com.strumenta.kolasu.ids

import com.strumenta.kolasu.model.ASTNode
import com.strumenta.kolasu.model.ASTRoot
import com.strumenta.kolasu.model.Source
import com.strumenta.kolasu.model.containingProperty
import com.strumenta.kolasu.model.indexInContainingProperty

class ConstantSourceIdProvider(
    var value: String,
) : SourceIdProvider {
    override fun sourceId(source: Source?) = value
}

open class StructuralNodeIdProvider(
    var sourceIdProvider: SourceIdProvider = SimpleSourceIdProvider(),
) : BaseNodeIdProvider() {
    constructor(customSourceId: String) : this(
        ConstantSourceIdProvider(customSourceId),
    )

    override fun id(kNode: ASTNode): String {
        val canBeRoot = kNode::class.annotations.any { it is ASTRoot }
        val mustBeRoot = kNode::class.annotations.any { it is ASTRoot && !it.canBeNotRoot }
        val coordinates: Coordinates =
            if (kNode.parent == null) {
                RootCoordinates
            } else {
                InternalCoordinates(
                    topLevelProvider.id(kNode.parent!!),
                    kNode.containingProperty()!!.name,
                    kNode.indexInContainingProperty()!!,
                )
            }
        return when (coordinates) {
            is RootCoordinates -> {
                if (!canBeRoot) {
                    throw NodeShouldNotBeRootException("Node $kNode should not be root")
                }
                val sourceId =
                    try {
                        sourceIdProvider.sourceId(kNode.source)
                    } catch (e: SourceShouldBeSetException) {
                        throw SourceShouldBeSetException(
                            "Source should be set for node $kNode, as it is a root node " +
                                "looking for a Positional ID",
                            e,
                        )
                    } catch (e: IDGenerationException) {
                        throw IDGenerationException("Cannot get source id for node $kNode", e)
                    }
                "$sourceId"
            }

            is InternalCoordinates -> {
                if (mustBeRoot) {
                    throw NodeShouldBeRootException("Node $kNode should be root")
                }
                val index = coordinates.indexInContainment
                val postfix = if (index == 0) coordinates.containmentName else "${coordinates.containmentName}_$index"
                "${coordinates.containerID!!}_$postfix"
            }
        }
    }
}
