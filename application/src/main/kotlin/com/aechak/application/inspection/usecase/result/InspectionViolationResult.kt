package com.aechak.application.inspection.usecase.result

import com.aechak.application.inspection.port.view.ViolationView

data class InspectionViolationResult(
    val category: String,
    val quote: String?,
    val suggestion: String?,
) {
    companion object {
        fun from(view: ViolationView): InspectionViolationResult = InspectionViolationResult(view.category, view.quote, view.suggestion)
    }
}
