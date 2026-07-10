package com.aechak.domain.seller.application

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "application_documents")
class ApplicationDocument protected constructor(
    documentType: String,
    storageKey: String,
    contentType: String,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, length = 40)
    val documentType: String = documentType

    @Column(nullable = false, length = 512)
    val storageKey: String = storageKey

    @Column(nullable = false, length = 100)
    val contentType: String = contentType

    @Column(nullable = false, updatable = false)
    val uploadedAt: LocalDateTime = LocalDateTime.now()

    companion object {
        fun of(documentType: String, storageKey: String, contentType: String): ApplicationDocument =
            ApplicationDocument(documentType, storageKey, contentType)
    }
}
