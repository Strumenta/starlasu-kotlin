package com.strumenta.kolasu.model

interface ASTNode {

    @Internal
    var id: String?

    /**
     * The parent node, if any.
     */
    @property:Internal
    var parent: ASTNode?

    @property:Internal
    var source: Source?

    @property:Internal
    var position: Position?

    @property:Internal
    val properties: List<PropertyDescription>

    @property:Internal
    val originalProperties: List<PropertyDescription>

    @property:Internal
    var origin: Origin?

    @Internal
    val nodeType: String

    /**
     * Tests whether the given position is contained in the interval represented by this object.
     * @param position the position
     */
    fun contains(position: Position?): Boolean = this.position?.contains(position) ?: false

    /**
     * Tests whether the given position overlaps the interval represented by this object.
     * @param position the position
     */
    fun overlaps(position: Position?): Boolean = this.position?.overlaps(position) ?: false
}
