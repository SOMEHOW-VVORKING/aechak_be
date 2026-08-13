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
    var storageKey: String = storageKey
        protected set

    @Column(nullable = false, length = 100)
    var contentType: String = contentType
        protected set

    /** 동일 종류 재등록 = 파일 교체 — 행을 지웠다 다시 만들지 않고 같은 행을 갱신한다(updated_at이 곧 최신 업로드 시각). */
    fun replaceFile(
        storageKey: String,
        contentType: String,
    ) {
        this.storageKey = storageKey
        this.contentType = contentType
    }

    companion object {
        fun of(
            documentType: DocumentType,
            storageKey: String,
            contentType: String,
        ): ApplicationDocument = ApplicationDocument(documentType, storageKey, contentType)
    }
}
