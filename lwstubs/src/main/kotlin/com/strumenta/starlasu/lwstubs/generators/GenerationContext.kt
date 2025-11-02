package com.strumenta.starlasu.lwstubs.generators

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.strumenta.starlasu.base.v1.ASTLanguageV1
import com.strumenta.starlasu.lwstubs.model.BehaviorDeclarationLW
import com.strumenta.starlasu.lwstubs.model.DocumentationLW
import com.strumenta.starlasu.lwstubs.model.EntityDeclarationLW
import com.strumenta.starlasu.lwstubs.model.ExpressionLW
import com.strumenta.starlasu.lwstubs.model.NamedLW
import com.strumenta.starlasu.lwstubs.model.ParameterLW
import com.strumenta.starlasu.lwstubs.model.PlaceholderElementLW
import com.strumenta.starlasu.lwstubs.model.StarlasuLWBaseASTNode
import com.strumenta.starlasu.lwstubs.model.StatementLW
import com.strumenta.starlasu.lwstubs.model.TypeAnnotationLW
import io.lionweb.LionWebVersion
import io.lionweb.language.Classifier
import io.lionweb.language.Language
import io.lionweb.language.LionCoreBuiltins

class GenerationContext(
    val packageName: String,
    val language: Language,
    val languageType: ClassName,
) {
    fun classifierToClassName(classifier: Classifier<*>?): ClassName? =
        when {
            classifier == null -> null
            classifier == ASTLanguageV1.getASTNode() -> StarlasuLWBaseASTNode::class.asClassName()
            classifier == ASTLanguageV1.getStatement() -> StatementLW::class.asClassName()
            classifier == ASTLanguageV1.getExpression() -> ExpressionLW::class.asClassName()
            classifier == ASTLanguageV1.getParameter() -> ParameterLW::class.asClassName()
            classifier == ASTLanguageV1.getBehaviorDeclaration() -> BehaviorDeclarationLW::class.asClassName()
            classifier == ASTLanguageV1.getPlaceholderElement() -> PlaceholderElementLW::class.asClassName()
            classifier == ASTLanguageV1.getEntityDeclaration() -> EntityDeclarationLW::class.asClassName()
            classifier == ASTLanguageV1.getDocumentation() -> DocumentationLW::class.asClassName()
            classifier == LionCoreBuiltins.getINamed(LionWebVersion.v2023_1) -> NamedLW::class.asClassName()
            classifier == ASTLanguageV1.getTypeAnnotation() -> TypeAnnotationLW::class.asClassName()
            language == classifier.language -> ClassName(packageName, classifier.name!!)
            else -> TODO()
        }
}
