package com.strumenta.starlasu.validation

import com.strumenta.starlasu.base.v2.ASTLanguage
import com.strumenta.starlasu.model.Position
import io.lionweb.language.Concept
import io.lionweb.language.Containment
import io.lionweb.language.Property
import io.lionweb.language.Reference
import io.lionweb.model.ClassifierInstance
import io.lionweb.model.HasSettableParent
import io.lionweb.model.Node
import io.lionweb.model.ReferenceValue
import io.lionweb.model.impl.AbstractNode
import java.util.Objects

class Issue(id: String) : AbstractNode(), HasSettableParent {
    private val id: String

    private var parent: ClassifierInstance<*>? = null

    private var type: IssueType? = null

    private var message: String? = null

    private var severity: IssueSeverity? = null

    private var position: Position? = null

    init {
        Objects.requireNonNull<String?>(id, "id must not be null")
        this.id = id
    }

    override fun getID(): String {
        return this.id
    }

    override fun getParent(): ClassifierInstance<*>? {
        return this.parent
    }

    override fun setParent(parent: ClassifierInstance<*>?): ClassifierInstance<*> {
        this.parent = parent
        return this
    }

    override fun getClassifier(): Concept {
        return ASTLanguage.getInstance().getIssue()
    }

    fun getType(): IssueType? {
        return type
    }

    fun setType(value: IssueType?) {
        if (partitionObserverCache != null) {
            partitionObserverCache!!.propertyChanged(
                this, this.getClassifier().requirePropertyByName("type"), getType(), value
            )
        }
        this.type = value
    }

    fun getMessage(): String? {
        return message
    }

    fun setMessage(value: String?) {
        if (partitionObserverCache != null) {
            partitionObserverCache!!.propertyChanged(
                this, this.getClassifier().requirePropertyByName("message"), getMessage(), value
            )
        }
        this.message = value
    }

    fun getSeverity(): IssueSeverity? {
        return severity
    }

    fun setSeverity(value: IssueSeverity?) {
        if (partitionObserverCache != null) {
            partitionObserverCache!!.propertyChanged(
                this, this.getClassifier().requirePropertyByName("severity"), getSeverity(), value
            )
        }
        this.severity = value
    }

    fun getPosition(): Position? {
        return position
    }

    fun setPosition(value: Position?) {
        if (partitionObserverCache != null) {
            partitionObserverCache!!.propertyChanged(
                this, this.getClassifier().requirePropertyByName("position"), getPosition(), value
            )
        }
        this.position = value
    }

    override fun getPropertyValue(property: Property): Any? {
        if (property.getKey() == "com_strumenta_starlasu-Issue-type-key") {
            return type
        }
        if (property.getKey() == "com_strumenta_starlasu-Issue-message-key") {
            return message
        }
        if (property.getKey() == "com_strumenta_starlasu-Issue-severity-key") {
            return severity
        }
        if (property.getKey() == "com_strumenta_starlasu-Issue-position-key") {
            return position
        }
        throw IllegalStateException("Property " + property + " not found.")
    }

    override fun setPropertyValue(property: Property, value: Any?) {
        Objects.requireNonNull<Property?>(property, "Property should not be null")

        Objects.requireNonNull<String?>(property!!.getKey(), "Cannot assign a property with no Key specified")

        if (property.getKey() == "com_strumenta_starlasu-Issue-type-key") {
            setType(value as IssueType?)
            return
        }
        if (property.getKey() == "com_strumenta_starlasu-Issue-message-key") {
            setMessage(value as String?)
            return
        }
        if (property.getKey() == "com_strumenta_starlasu-Issue-severity-key") {
            setSeverity(value as IssueSeverity?)
            return
        }
        if (property.getKey() == "com_strumenta_starlasu-Issue-position-key") {
            setPosition(value as Position?)
            return
        }
        throw IllegalStateException("Property " + property + " not found.")
    }

    override fun getChildren(containment: Containment): MutableList<out Node?> {
        throw IllegalStateException("Containment " + containment + " not found.")
    }

    override fun addChild(containment: Containment, child: Node) {
        Objects.requireNonNull<Containment?>(containment, "Containment should not be null")
        Objects.requireNonNull<Node?>(child, "Child should not be null")
        throw IllegalStateException("Containment " + containment + " not found.")
    }

    override fun addChild(containment: Containment, child: Node, index: Int) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun getReferenceValues(reference: Reference): MutableList<ReferenceValue?> {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun addReferenceValue(reference: Reference, referredNode: ReferenceValue?): Int {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun addReferenceValue(reference: Reference, index: Int, referredNode: ReferenceValue?): Int {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun setReferenceValues(reference: Reference, values: MutableList<out ReferenceValue?>) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun setReferred(reference: Reference, index: Int, referredNode: Node?) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun setResolveInfo(reference: Reference, index: Int, resolveInfo: String?) {
        throw UnsupportedOperationException("Not supported yet.")
    }
}
