package com.aechak.application.order.support

object OrderPointKeys {
    const val SOURCE_TYPE_ORDER_GROUP = "ORDER_GROUP"

    fun useKey(orderGroupPublicId: String): String = "USE:ORDER:$orderGroupPublicId"

    fun releaseKey(orderGroupPublicId: String): String = "RELEASE:ORDER:$orderGroupPublicId"
}
