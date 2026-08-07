package com.aechak.domain.seller.application

import com.aechak.domain.seller.application.enums.DocumentType
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "application_documents",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_application_documents_application_id_document_type",
            columnNames = ["application_id", "document_type"],
        ),
    ],
)
class ApplicationDocument protected constructor(
    documentType: DocumentType,
    storageKey: String,
    contentType: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val documentType: DocumentType = documentType

    @Column(nullable = false, length = 512)
    val storageKey: String = storageKey

    @Column(nullable = false, length = 100)
    val contentType: String = contentType

    companion object {
        fun of(
            documentType: DocumentType,
            storageKey: String,
            contentType: String,
        ): ApplicationDocument = ApplicationDocument(documentType, storageKey, contentType)
    }
}
