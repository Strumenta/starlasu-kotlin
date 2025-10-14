package com.strumenta.kolasu.serialization

import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.processProperties
import com.strumenta.kolasu.validation.Issue
import com.strumenta.kolasu.validation.Result
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Converts an AST to XML.
 * The XML Generator is not supporting all features of Kolasu, at this time.
 * It may be removed in future version of Kolasu.
 */
class XMLGenerator {
    fun generateXML(root: Node): Document {
        val documentFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentFactory.newDocumentBuilder()
        val document = documentBuilder.newDocument()
        document.appendChild(root.toXML("root", document))
        return document
    }

    fun generateXML(result: Result<out Node>): Document {
        val documentFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentFactory.newDocumentBuilder()
        val document = documentBuilder.newDocument()
        val root = document.createElement("result")
        root.addListOfElements("issues", result.issues.map { it.toXML(document) }, document)
        root.addChildPossiblyEmpty("root", result.root, document)
        document.appendChild(root)
        return document
    }

    fun generateString(root: Node): String {
        val document = generateXML(root)
        return document.toXmlString()
    }

    private fun Document.toXmlString(): String {
        val tf = TransformerFactory.newInstance()
        tf.setAttribute("indent-number", 4)
        val transformer = tf.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")

        val writer = StringWriter()

        transformer.transform(DOMSource(this), StreamResult(writer))

        val xmlString = writer.buffer.toString()
        return xmlString
    }

    fun generateString(result: Result<out Node>): String {
        val document = generateXML(result)
        return document.toXmlString()
    }

    fun generateFile(
        root: Node,
        file: File,
    ) {
        File(file.toURI()).writeText(generateString(root))
    }

    fun generateFile(
        result: Result<out Node>,
        file: File,
    ) {
        File(file.toURI()).writeText(generateString(result))
    }
}

private fun Element.addChildPossiblyEmpty(
    role: String,
    node: Node?,
    document: Document,
) {
    if (node == null) {
        this.addNullChild(role, document)
    } else {
        this.addChild(node.toXML(role, document))
    }
}

private fun Element.addListOfNodes(
    listName: String,
    elements: Iterable<Node>,
    document: Document,
) {
    elements.forEach {
        addChild(it.toXML(listName, document))
    }
}

private fun Element.addListOfElements(
    listName: String,
    elements: Iterable<Element>,
    document: Document,
) {
    val listElement = document.createElement(listName)
    elements.forEach {
        listElement.addChild(it)
    }
    this.appendChild(listElement)
}

private fun Element.addChild(child: Element) {
    this.appendChild(child)
}

private fun Element.addNullChild(
    role: String,
    document: Document,
) {
    val element = document.createElement(role)
    this.appendChild(element)
}

private fun Node.toXML(
    role: String,
    document: Document,
): Element {
    val element = document.createElement(role)
    element.setAttribute("type", this.javaClass.simpleName)
    this.position?.let {
        element.addChild(it.toXML(document = document))
    }
    this.processProperties {
        val value = it.value
        if (value == null) {
            element.addNullChild(it.name, document)
        } else if (it.multiple) {
            if (it.providesNodes) {
                element.addListOfNodes(it.name, (value as Collection<*>).map { it as Node }, document)
            } else {
                element.addAttributesList(it.name, value as Collection<*>, document)
            }
        } else {
            if (it.providesNodes) {
                element.addChild((value as Node).toXML(it.name, document))
            } else {
                element.addAttribute(it.name, value)
            }
        }
    }
    return element
}

private fun Element.addAttribute(
    role: String,
    value: Any,
) {
    this.setAttribute(role, value.toString())
}

private fun Element.addAttributesList(
    listName: String,
    values: Collection<*>,
    document: Document,
) {
    values.forEach {
        val childElement = document.createElement(listName)
        childElement.setAttribute("value", it.toString())
        this.appendChild(childElement)
    }
}

private fun Issue.toXML(document: Document): Element {
    val element = document.createElement("Issue")
    element.setAttribute("type", this.type.name)
    element.setAttribute("message", this.message)
    element.setAttribute("severity", this.severity.name)
    this.position?.let {
        element.addChild(it.toXML(document = document))
    }
    return element
}

private fun Position.toXML(
    role: String = "position",
    document: Document,
): Element {
    val xmlNode = document.createElement(role)
    xmlNode.setAttribute("description", this.toString())
    xmlNode.addChild(this.start.toXML("start", document))
    xmlNode.addChild(this.end.toXML("end", document))
    return xmlNode
}

private fun Point.toXML(
    role: String,
    document: Document,
): Element {
    val xmlNode = document.createElement(role)
    xmlNode.setAttribute("line", this.line.toString())
    xmlNode.setAttribute("column", this.column.toString())
    return xmlNode
}
