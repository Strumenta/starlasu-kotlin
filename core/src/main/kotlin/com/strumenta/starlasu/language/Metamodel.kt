package com.strumenta.starlasu.language

import com.strumenta.starlasu.model.Multiplicity
import com.strumenta.starlasu.model.checkFeatureName
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Deprecated("Use LionWeb's Language")
sealed class Feature {
    abstract val name: String

    /**
     * Only Containment can have Multiplicity Many.
     */
    abstract val multiplicity: Multiplicity
}

@Deprecated("Use LionWeb's Language")
data class Attribute(
    override val name: String,
    val optional: Boolean,
    val type: KType,
) : Feature() {
    init {
        require(!type.isMarkedNullable) {
            "The type should be specified as not nullable. " +
                "The optional flag should be used to represent nullability"
        }
        checkFeatureName(name)
    }

    override val multiplicity: Multiplicity
        get() = if (optional) Multiplicity.OPTIONAL else Multiplicity.SINGULAR
}

@Deprecated("Use LionWeb's Language")
sealed class Link : Feature() {
    abstract val type: KClass<*>
}

@Deprecated("Use LionWeb's Language")
data class Reference(
    override val name: String,
    val optional: Boolean,
    override val type: KClass<*>,
) : Link() {
    override val multiplicity: Multiplicity
        get() = if (optional) Multiplicity.OPTIONAL else Multiplicity.SINGULAR

    init {
        checkFeatureName(name)
    }
}

@Deprecated("Use LionWeb's Language")
data class Containment(
    override val name: String,
    override val multiplicity: Multiplicity,
    override val type: KClass<*>,
) : Link() {
    init {
        checkFeatureName(name)
    }
}
