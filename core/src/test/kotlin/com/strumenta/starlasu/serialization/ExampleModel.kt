package com.strumenta.starlasu.serialization

import com.strumenta.starlasu.model.Node

data class MyRoot(
    val mainSection: Section,
    val otherSections: List<Section>,
) : Node()

data class Section(
    val name: String,
    val contents: List<AbstractContent>,
) : Node()

sealed class AbstractContent : Node()

data class Content(
    val _id: Int,
    val annidatedContent: Content?,
) : AbstractContent()

data class OtherContent(
    val values: List<Int>,
) : AbstractContent()
