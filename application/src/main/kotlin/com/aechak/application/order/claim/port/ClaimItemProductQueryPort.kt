package com.aechak.application.order.claim.port

import com.aechak.application.order.claim.port.view.ClaimItemProductView

interface ClaimItemProductQueryPort {
    fun findProductNames(orderItemIds: Collection<Long>): List<ClaimItemProductView>
}
