package com.koxudaxi.pydantic

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.completion.getTypeEvalContext
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.TypeEvalContext

class PydanticCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC,
                psiElement()
                        .withLanguage(PythonLanguage.getInstance())
                        .and(psiElement().withParents(
                                PyReferenceExpression::class.java,
                                PyArgumentList::class.java,
                                PyCallExpression::class.java)
                        ),
                KeywordArgumentCompletionProvider)
    }

    private object KeywordArgumentCompletionProvider : CompletionProvider<CompletionParameters>() {

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val pyArgumentList = parameters.position.parent!!.parent !!as PyArgumentList
            val pyCallExpression = pyArgumentList.parent !!as PyCallExpression
            val pyClass = getPyClassByPyCallExpression(pyCallExpression) ?: return
            val typeEvalContext = parameters.getTypeEvalContext()

            if (!isPydanticModel(pyClass, typeEvalContext)) return

            val definedSet = pyArgumentList.children
                    .mapNotNull { (it as? PyKeywordArgument)?.name }
                    .map { "${it}=" }
                    .toHashSet()

            val newElements: LinkedHashMap<String, LookupElement> = LinkedHashMap()

            pyClass.getAncestorClasses(typeEvalContext)
                    .filter { isPydanticModel(it) && !isPydanticBaseModel(it) }
                    .forEach { addFieldElement(it, definedSet, newElements, typeEvalContext) }

            addFieldElement(pyClass, definedSet, newElements, typeEvalContext)

            result.runRemainingContributors(parameters)
            { completionResult ->
                completionResult.lookupElement.lookupString
                        .takeIf { name -> !newElements.containsKey(name) && !definedSet.contains(name) }
                        ?.let { result.passResult(completionResult) }
            }
            result.addAllElements(newElements.values)
        }

        private fun addFieldElement(pyClass: PyClass, excludes: HashSet<String>, results: LinkedHashMap<String, LookupElement>, typeEvalContext: TypeEvalContext) {
            pyClass.classAttributes
                    .asReversed()
                    .asSequence()
                    .filterNot { PyTypingTypeProvider.isClassVar(it, typeEvalContext) }
                    .filter { it.name != null }
                    .forEach {
                        val elementName = "${it.name!!}="
                        if (!excludes.contains(elementName)) {
                            val className = pyClass.qualifiedName ?: pyClass.name
                            val element = PrioritizedLookupElement.withGrouping(
                                    LookupElementBuilder
                                            .createWithSmartPointer(elementName, it)
                                            .withTypeText(className).withIcon(AllIcons.Nodes.Parameter), 1)
                            results[elementName] = PrioritizedLookupElement.withPriority(element, 100.0)
                        }
                    }
        }
    }

}
