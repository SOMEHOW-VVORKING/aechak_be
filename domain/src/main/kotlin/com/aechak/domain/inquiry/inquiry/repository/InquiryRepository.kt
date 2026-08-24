package com.aechak.domain.inquiry.inquiry.repository

import com.aechak.domain.inquiry.inquiry.Inquiry

interface InquiryRepository {
    fun save(inquiry: Inquiry): Inquiry
}
