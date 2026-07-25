package com.aechak.api.user.address.request

object DeliveryAddressPatterns {
    // 연락처는 숫자만 저장함.
    // 010 시작 시 뒤 8자리 고정, 011, 016~019는 7~8자리라 갈라서 씀
    // 표시용 하이픈 조립은 클라이언트에서...
    const val CONTACT_NUMBER = "^(010\\d{8}|01[16789]\\d{7,8})$"
    const val ZIP_CODE = "^\\d{5}$"

    /**
     * 구분자(공백·하이픈·점·괄호) 걷어냄
     */
    fun normalizeContactNumber(raw: String): String = raw.replace(SEPARATORS, "")

    private val SEPARATORS = Regex("[\\s().-]")
}
