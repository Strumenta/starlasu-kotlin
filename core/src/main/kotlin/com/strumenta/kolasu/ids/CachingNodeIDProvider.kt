package com.strumenta.kolasu.ids

import com.strumenta.kolasu.model.ASTNode
import java.util.IdentityHashMap

/**
 * The Node ID of most nodes is a Positional Node ID. Positional Node IDs are calculated by considering the Node ID of
 * the parent,so that the Node IDs of all ancestors are calculated again and again.
 * By using a cache we can avoid that.
 */
class CachingNodeIDProvider(
    val wrapped: NodeIdProvider,
) : BaseNodeIdProvider() {
    private val cache = IdentityHashMap<ASTNode, String>()

    init {
        wrapped.topLevelProvider.parentProvider = this
    }

    override fun id(kNode: ASTNode): String {
        if (cache.containsKey(kNode)) {
            return cache[kNode]!!
        } else {
            val value = wrapped.id(kNode)
            cache[kNode] = value
            return value
        }
    }

    fun clear() {
        cache.clear()
    }

    override fun toString(): String = "CachingNodeIDProvider with wrapped=$wrapped"
}

fun NodeIdProvider.caching(): CachingNodeIDProvider = CachingNodeIDProvider(this)
