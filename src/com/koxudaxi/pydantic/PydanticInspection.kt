package com.koxudaxi.pydantic

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyNames
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.inspections.quickfix.RenameParameterQuickFix
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl
import com.jetbrains.python.psi.impl.PyStarArgumentImpl

class PydanticInspection : PyInspection() {

    override fun buildVisitor(holder: ProblemsHolder,
                              isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor = Visitor(holder, session)

    private class Visitor(holder: ProblemsHolder, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

        override fun visitPyFunction(node: PyFunction?) {
            super.visitPyFunction(node)

            val pyClass = node?.parent?.parent as? PyClass ?: return
            if (!isPydanticModel(pyClass, myTypeEvalContext) || !validatorMethod(node)) return
            val paramList = node.parameterList
            val params = paramList.parameters
            val firstParam = params.firstOrNull()
            if (firstParam == null) {
                registerProblem(paramList, PyBundle.message("INSP.must.have.first.parameter", PyNames.CANONICAL_CLS),
                        ProblemHighlightType.GENERIC_ERROR)
            } else if (firstParam.asNamed?.isSelf == true && firstParam.asNamed?.name != PyNames.CANONICAL_CLS) {

                registerProblem(PyUtil.sure(firstParam),
                        PyBundle.message("INSP.usually.named.\$0", PyNames.CANONICAL_CLS),
                        ProblemHighlightType.WEAK_WARNING, null,
                        RenameParameterQuickFix(PyNames.CANONICAL_CLS))
            }

        }

        override fun visitPyCallExpression(node: PyCallExpression?) {
            super.visitPyCallExpression(node)

            if (node != null) { // $COVERAGE-IGNORE$
                val pyClass: PyClass = getPyClassByPyCallExpression(node) ?: return
                if (!isPydanticModel(pyClass, myTypeEvalContext)) return
                if ((node.callee as PyReferenceExpressionImpl).isQualified) return // $COVERAGE-IGNORE$
                for (argument in node.arguments) {
                    if (argument is PyKeywordArgument) {
                        continue
                    }
                    if ((argument as? PyStarArgumentImpl)?.isKeyword == true) {
                        continue
                    }
                    registerProblem(argument,
                            "class '${pyClass.name}' accepts only keyword arguments")
                }

            }
        }
    }
}