package com.strumenta.kolasu.emf

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.strumenta.kolasu.model.Destination
import com.strumenta.kolasu.model.Node
import com.strumenta.kolasu.model.Origin
import com.strumenta.kolasu.model.Point
import com.strumenta.kolasu.model.Position
import com.strumenta.kolasu.model.ReferenceByName
import com.strumenta.kolasu.model.SimpleOrigin
import com.strumenta.kolasu.model.TextFileDestination
import com.strumenta.kolasu.model.processProperties
import com.strumenta.kolasu.parsing.ParseTreeOrigin
import com.strumenta.kolasu.validation.Issue
import com.strumenta.kolasu.validation.Result
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EDataType
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emfcloud.jackson.resource.JsonResourceFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.IdentityHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

fun EPackage.getEClass(javaClass: Class<*>): EClass {
    return this.getEClass(javaClass.eClassifierName)
}

fun EPackage.getEClass(klass: KClass<*>): EClass {
    return this.getEClass(klass.eClassifierName)
}

fun EPackage.getEClass(name: String): EClass {
    return (this.eClassifiers.find { it.name == name } ?: throw IllegalArgumentException("EClass not found: $name"))
        as EClass
}

fun EPackage.getEDataType(name: String): EDataType {
    return (this.eClassifiers.find { it.name == name } ?: throw IllegalArgumentException("EDataType not found: $name"))
        as EDataType
}

fun EPackage.getEEnum(javaClass: Class<*>): EEnum {
    return (
        this.eClassifiers.find { it.name == javaClass.eClassifierName } ?: throw IllegalArgumentException(
            "Class not found: $javaClass"
        )
        ) as EEnum
}

fun Any.dataToEObject(ePackage: EPackage): EObject {
    val ec = ePackage.getEClass(this::class)
    val eo = ePackage.eFactoryInstance.create(ec)
    ec.eAllAttributes.forEach { attr ->
        val prop = this::class.memberProperties.find { it.name == attr.name } as KProperty1<Any, *>?
        if (prop != null) {
            val value = prop.getValue(this, prop)
            eo.eSet(attr, value)
        } else {
            throw RuntimeException("Unable to set attribute $attr in $this")
        }
    }
    return eo
}

fun Point.toEObject(): EObject {
    val ec = STARLASU_METAMODEL.getEClass("Point")
    val eo = STARLASU_METAMODEL.eFactoryInstance.create(ec)
    eo.eSet(ec.getEStructuralFeature("line"), this.line)
    eo.eSet(ec.getEStructuralFeature("column"), this.column)
    return eo
}

fun Position.toEObject(): EObject {
    val ec = STARLASU_METAMODEL.getEClass("Position")
    val eo = STARLASU_METAMODEL.eFactoryInstance.create(ec)
    eo.eSet(ec.getEStructuralFeature("start"), this.start.toEObject())
    eo.eSet(ec.getEStructuralFeature("end"), this.end.toEObject())
    return eo
}

fun <T : Node> Result<T>.toEObject(astPackage: EPackage): EObject {
    val resultEO = makeResultEObject(this)
    val rootSF = resultEO.eClass().eAllStructuralFeatures.find { it.name == "root" }!!
    if (root != null) {
        resultEO.eSet(rootSF, root!!.getOrCreateEObject(astPackage))
    }
    return resultEO
}

private fun makeResultEObject(result: Result<*>): EObject {
    val resultEC = STARLASU_METAMODEL.getEClass(Result::class.java)
    val resultEO = STARLASU_METAMODEL.eFactoryInstance.create(resultEC)
    val issuesSF = resultEC.eAllStructuralFeatures.find { it.name == "issues" }!!
    val issues = resultEO.eGet(issuesSF) as MutableList<EObject>
    result.issues.forEach {
        issues.add(it.toEObject())
    }
    return resultEO
}

fun <T : Node> Result<T>.toEObject(
    resource: Resource,
    kolasuToEMFMapping: KolasuToEMFMapping = KolasuToEMFMapping()
): EObject {
    val resultEO = makeResultEObject(this)
    val rootSF = resultEO.eClass().eAllStructuralFeatures.find { it.name == "root" }!!
    if (root != null) {
        resultEO.eSet(rootSF, kolasuToEMFMapping.getOrCreate(root!!, resource))
    }
    return resultEO
}

fun Issue.toEObject(): EObject {
    val ec = STARLASU_METAMODEL.getEClass(Issue::class.java)
    val eo = STARLASU_METAMODEL.eFactoryInstance.create(ec)
    val et = STARLASU_METAMODEL.getEClassifier("IssueType")
    val es = STARLASU_METAMODEL.getEClassifier("IssueSeverity")
    val typeSF = ec.eAllStructuralFeatures.find { it.name == "type" }!!
    eo.eSet(typeSF, (et as EEnum).getEEnumLiteral(type.ordinal))
    val severitySF = ec.eAllStructuralFeatures.find { it.name == "severity" }!!
    eo.eSet(severitySF, (es as EEnum).getEEnumLiteral(severity.ordinal))
    val messageSF = ec.eAllStructuralFeatures.find { it.name == "message" }!!
    eo.eSet(messageSF, message)
    val positionSF = ec.eAllStructuralFeatures.find { it.name == "position" }!!
    if (position != null) {
        eo.eSet(positionSF, position!!.toEObject())
    }
    return eo
}

private fun toValue(
    ePackage: EPackage,
    value: Any?,
    kolasuToEMFMapping: KolasuToEMFMapping = KolasuToEMFMapping()
): Any? {
    val pdValue: Any? = value
    when (pdValue) {
        is Enum<*> -> {
            val ee = ePackage.getEEnum(pdValue.javaClass)
            return ee.getEEnumLiteral(pdValue.name)
        }

        is LocalDate -> {
            return toLocalDateObject(pdValue)
        }

        is LocalTime -> {
            return toLocalTimeObject(pdValue)
        }

        is LocalDateTime -> {
            val eClass = STARLASU_METAMODEL.getEClass("LocalDateTime")
            val eObject = STARLASU_METAMODEL.eFactoryInstance.create(eClass)
            val dateComponent = toLocalDateObject(pdValue.toLocalDate())
            val timeComponent = toLocalTimeObject(pdValue.toLocalTime())
            eObject.eSet(eClass.getEStructuralFeature("date"), dateComponent)
            eObject.eSet(eClass.getEStructuralFeature("time"), timeComponent)
            return eObject
        }

        else -> {
            // this could be not a primitive value but a value that we mapped to an EClass
            val eClass = if (pdValue != null) {
                ePackage.eClassifiers.filterIsInstance<EClass>().find {
                    it.name == pdValue.javaClass.simpleName
                }
            } else {
                null
            }
            return when {
                eClass != null -> {
                    pdValue!!.dataToEObject(ePackage)
                }

                pdValue is ReferenceByName<*> -> {
                    val refEC = STARLASU_METAMODEL.getEClass("ReferenceByName")
                    val refEO = STARLASU_METAMODEL.eFactoryInstance.create(refEC)
                    refEO.eSet(refEC.getEStructuralFeature("name")!!, pdValue.name)
                    // Note that we could either find references to nodes we have already encountered and to nodes
                    // that we have not yet encountered
                    // In one case, the EObject for the referenced Node already exist in the kolasuToEMFMapping, so
                    // we just retrieve it.
                    // In the other case, we create the EObject right now and add it to the mapping, so that later the
                    // same EObject can be inserted in the containment relation where it belongs.
                    refEO.eSet(
                        refEC.getEStructuralFeature("referenced")!!,
                        (pdValue.referred as? Node)?.getOrCreateEObject(ePackage, kolasuToEMFMapping)
                    )
                    refEO
                }

                pdValue is Result<*> -> {
                    val resEC = STARLASU_METAMODEL.getEClass("Result")
                    val resEO = STARLASU_METAMODEL.eFactoryInstance.create(resEC)
                    if (pdValue.root is Node) {
                        resEO.eSet(
                            resEC.getEStructuralFeature("root"),
                            (pdValue.root as Node)
                                .getOrCreateEObject(ePackage, kolasuToEMFMapping)
                        )
                    } else {
                        resEO.eSet(resEC.getEStructuralFeature("root"), toValue(ePackage, pdValue.root))
                    }
                    val issues = resEO.eGet(resEC.getEStructuralFeature("issues")) as EList<EObject>
                    issues.addAll(pdValue.issues.map { it.toEObject() })
                    resEO
                }

                else -> pdValue
            }
        }
    }
}

private fun toLocalTimeObject(value: LocalTime): EObject {
    val eClass = STARLASU_METAMODEL.getEClass("LocalTime")
    val eObject = STARLASU_METAMODEL.eFactoryInstance.create(eClass)
    eObject.eSet(eClass.getEStructuralFeature("hour"), value.hour)
    eObject.eSet(eClass.getEStructuralFeature("minute"), value.minute)
    eObject.eSet(eClass.getEStructuralFeature("second"), value.second)
    eObject.eSet(eClass.getEStructuralFeature("nanosecond"), value.nano)
    return eObject
}

private fun toLocalDateObject(value: LocalDate): EObject {
    val eClass = STARLASU_METAMODEL.getEClass("LocalDate")
    val eObject = STARLASU_METAMODEL.eFactoryInstance.create(eClass)
    eObject.eSet(eClass.getEStructuralFeature("year"), value.year)
    eObject.eSet(eClass.getEStructuralFeature("month"), value.monthValue)
    eObject.eSet(eClass.getEStructuralFeature("dayOfMonth"), value.dayOfMonth)
    return eObject
}

fun packageName(klass: KClass<*>): String = klass.qualifiedName!!.substring(0, klass.qualifiedName!!.lastIndexOf("."))

fun EPackage.findEClass(klass: KClass<*>): EClass? {
    return this.findEClass(klass.eClassifierName)
}

fun EPackage.findEClass(name: String): EClass? {
    return this.eClassifiers.find { it is EClass && it.name == name } as EClass?
}

fun Resource.findEClass(klass: KClass<*>): EClass? {
    val eClass = findEClassJustInThisResource(klass)
    if (eClass == null) {
        val otherResources = this.resourceSet?.resources?.filter { it != this } ?: emptyList()
        for (r in otherResources) {
            val c = r.findEClassJustInThisResource(klass)
            if (c != null) {
                return c
            }
        }
        return STARLASU_METAMODEL.findEClass(klass)
    } else {
        return eClass
    }
}

fun Resource.findEClassJustInThisResource(klass: KClass<*>): EClass? {
    val ePackage = this.contents.find { it is EPackage && it.name == packageName(klass) } as EPackage?
    return ePackage?.findEClass(klass)
}

fun Resource.getEClass(klass: KClass<*>): EClass = this.findEClass(klass)
    ?: throw ClassNotFoundException(klass.qualifiedName)

fun Node.toEObject(ePackage: EPackage, mapping: KolasuToEMFMapping = KolasuToEMFMapping()): EObject =
    toEObject(ePackage.eResource(), mapping)

/**
 * This method retrieves the EObject already built for this Node or create it if it does not exist.
 */
fun Node.getOrCreateEObject(ePackage: EPackage, mapping: KolasuToEMFMapping = KolasuToEMFMapping()): EObject =
    getOrCreateEObject(ePackage.eResource(), mapping)

fun EClass.instantiate(): EObject {
    return this.ePackage.eFactoryInstance.create(this)
}

class KolasuToEMFMapping {
    private val nodeToEObjects = IdentityHashMap<Node, EObject>()

    @Synchronized
    fun associate(node: Node, eo: EObject) {
        nodeToEObjects[node] = eo
    }

    @Synchronized
    fun getAssociatedEObject(node: Node): EObject? {
        return nodeToEObjects[node]
    }

    /**
     * If a corresponding EObject for the node has been already created, then it is returned.
     * Otherwise the EObject is created and returned. The same EObject is also stored and associated with the Node,
     * so that future calls to this method will return that EObject.
     */
    fun getOrCreate(node: Node, eResource: Resource): EObject {
        val existing = getAssociatedEObject(node)
        return if (existing != null) {
            existing
        } else {
            val eo = node.toEObject(eResource, this)
            associate(node, eo)
            eo
        }
    }

    val size
        get() = nodeToEObjects.size
}

/**
 * This method retrieves the EObject already built for this Node or create it if it does not exist.
 */
fun Node.getOrCreateEObject(eResource: Resource, mapping: KolasuToEMFMapping = KolasuToEMFMapping()): EObject {
    return mapping.getOrCreate(this, eResource)
}

private fun setOrigin(
    eo: EObject,
    origin: Origin?,
    resource: Resource,
    mapping: KolasuToEMFMapping = KolasuToEMFMapping()
) {
    if (origin == null) {
        return
    }
    val astNode = STARLASU_METAMODEL.getEClass("ASTNode")
    val originSF = astNode.getEStructuralFeature("origin")
    when (origin) {
        is Node -> {
            val nodeOriginClass = STARLASU_METAMODEL.getEClass("NodeOrigin")
            val nodeSF = nodeOriginClass.getEStructuralFeature("node")
            val nodeOrigin = nodeOriginClass.instantiate()
            val eoCorrespondingToOrigin = mapping.getAssociatedEObject(origin) ?: throw IllegalStateException(
                "No EObject mapped to origin $origin. " +
                    "Mapping contains ${mapping.size} entries"
            )
            nodeOrigin.eSet(nodeSF, eoCorrespondingToOrigin)
            eo.eSet(originSF, nodeOrigin)
        }

        is ParseTreeOrigin -> {
            // The ParseTreeOrigin is not saved in EMF as we do not want to replicate the whole parse-tree
            eo.eSet(originSF, null)
        }

        is SimpleOrigin -> {
            val simpleOriginClass = STARLASU_METAMODEL.getEClass("SimpleOrigin")
            val simpleOrigin = simpleOriginClass.instantiate()
            simpleOrigin.eSet(simpleOriginClass.getEStructuralFeature("position"), origin.position?.toEObject())
            simpleOrigin.eSet(simpleOriginClass.getEStructuralFeature("sourceText"), origin.sourceText)
            eo.eSet(originSF, simpleOrigin)
        }

        else -> {
            throw IllegalStateException("Only origins representing Nodes or ParseTreeOrigins are currently supported")
        }
    }
}

private fun setDestination(
    eo: EObject,
    destination: Destination?,
    eResource: Resource,
    mapping: KolasuToEMFMapping = KolasuToEMFMapping()
) {
    val astNode = STARLASU_METAMODEL.getEClass("ASTNode")
    when (destination) {
        null -> return
        is Node -> {
            val nodeDestination = STARLASU_METAMODEL.getEClass("NodeDestination")

            val nodeDestinationInstance = nodeDestination.instantiate()
            val nodeSF = nodeDestination.getEStructuralFeature("node")
            val eoCorrespondingToOrigin = destination.getOrCreateEObject(eResource, mapping)
            nodeDestinationInstance.eSet(nodeSF, eoCorrespondingToOrigin)

            val destinationSF = astNode.getEStructuralFeature("destination")
            eo.eSet(destinationSF, nodeDestinationInstance)
        }

        is TextFileDestination -> {
            val textFileInstance = STARLASU_METAMODEL.getEClass("TextFileDestination").instantiate()

            val positionSF = STARLASU_METAMODEL.getEClass("TextFileDestination").getEStructuralFeature("position")
            textFileInstance.eSet(positionSF, destination.position?.toEObject())

            val destinationSF = astNode.getEStructuralFeature("destination")
            eo.eSet(destinationSF, textFileInstance)
        }

        else -> {
            throw IllegalStateException(
                "Only destinations represented Nodes or TextFileDestinations are currently supported"
            )
        }
    }
}

/**
 * Translates this node – and, recursively, its descendants – into an [EObject] (EMF/Ecore representation).
 *
 * The classes of the node are resolved against the provided [Resource]. That is, the resource must contain:
 *  - the [Kolasu metamodel package][STARLASU_METAMODEL]
 *  - every [EPackage] containing the definitions of the node classes in the tree.
 */
fun Node.toEObject(eResource: Resource, mapping: KolasuToEMFMapping = KolasuToEMFMapping()): EObject {
    try {
        val ec = eResource.getEClass(this::class)
        val eo = ec.ePackage.eFactoryInstance.create(ec)
        mapping.associate(this, eo)
        val astNode = STARLASU_METAMODEL.getEClass("ASTNode")

        val position = astNode.getEStructuralFeature("position")
        val positionValue = this.position?.toEObject()
        eo.eSet(position, positionValue)

        setOrigin(eo, this.origin, eResource, mapping)
        setDestination(eo, this.destination, eResource, mapping)

        this.processProperties { pd ->
            val esf = ec.eAllStructuralFeatures.find { it.name == pd.name }!!
            if (pd.provideNodes) {
                if (pd.multiple) {
                    val elist = eo.eGet(esf) as MutableList<EObject?>
                    (pd.value as List<*>?)?.forEach {
                        try {
                            val childEO = (it as Node?)?.getOrCreateEObject(eResource, mapping)
                            elist.add(childEO)
                        } catch (e: Exception) {
                            throw RuntimeException("Unable to map to EObject child $it in property $pd of $this", e)
                        }
                    }
                } else {
                    if (pd.value == null) {
                        eo.eSet(esf, null)
                    } else {
                        eo.eSet(esf, (pd.value as Node).getOrCreateEObject(eResource, mapping))
                    }
                }
            } else {
                if (pd.multiple) {
                    val elist = eo.eGet(esf) as MutableList<Any>
                    (pd.value as List<*>?)?.forEach {
                        try {
                            val childValue = toValue(ec.ePackage, it, mapping)
                            elist.add(childValue!!)
                        } catch (e: Exception) {
                            throw RuntimeException("Unable to map to EObject child $it in property $pd of $this", e)
                        }
                    }
                } else {
                    try {
                        eo.eSet(esf, toValue(ec.ePackage, pd.value, mapping))
                    } catch (e: Exception) {
                        throw RuntimeException("Unable to set property $pd. Structural feature: $esf", e)
                    }
                }
            }
        }
        return eo
    } catch (e: Exception) {
        throw RuntimeException("Unable to map to EObject $this", e)
    }
}

fun EObject.saveXMI(xmiFile: File) {
    val resource: Resource = createResource(URI.createFileURI(xmiFile.absolutePath))!!
    resource.contents.add(this)
    resource.save(null)
}

fun EPackage.saveAsJson(jsonFile: File, restoringURI: Boolean = true) {
    val startURI = this.eResource().uri
    (this as EObject).saveAsJson(jsonFile)
    if (restoringURI) {
        this.setResourceURI(startURI.toString())
    }
}

fun EObject.saveAsJson(jsonFile: File) {
    val resource: Resource = createResource(URI.createFileURI(jsonFile.absolutePath))!!
    resource.contents.add(this)
    resource.save(null)
}

fun EObject.saveAsJson(): String {
    val uri: URI = URI.createURI("dummy-URI")
    val resource: Resource = JsonResourceFactory().createResource(uri)
    resource.contents.add(this)
    val output = ByteArrayOutputStream()
    resource.save(output, null)
    return output.toString(Charsets.UTF_8.name())
}

fun EObject.saveAsJsonObject(): JsonObject {
    return JsonParser.parseString(this.saveAsJson()).asJsonObject
}

fun EObject.eGet(name: String): Any? {
    val sfs = this.eClass().eAllStructuralFeatures.filter { it.name == name }
    when (sfs.size) {
        0 -> throw IllegalArgumentException("No feature $name found")
        1 -> return this.eGet(sfs.first())
        else -> throw IllegalArgumentException("Feature $name is ambiguous")
    }
}
