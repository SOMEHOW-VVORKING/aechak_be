package com.aechak.domain.settlement.account

import com.aechak.domain.settlement.account.enums.AccountVerificationStatus
import com.aechak.domain.support.AggregateRoot
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
    name = "settlement_accounts",
    uniqueConstraints = [UniqueConstraint(name = "uk_settlement_accounts_seller_id", columnNames = ["seller_id"])],
)
class SettlementAccount protected constructor(
    sellerId: Long,
    bankCode: String,
    accountNumberEnc: String,
    accountHolderName: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    val sellerId: Long = sellerId

    @Column(length = 20, nullable = false)
    var bankCode: String = bankCode
        protected set

    @Column(length = 255, nullable = false)
    var accountNumberEnc: String = accountNumberEnc
        protected set

    @Column(length = 100, nullable = false)
    var accountHolderName: String = accountHolderName
        protected set

    @Column(length = 100)
    var portonePartnerId: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var verificationStatus: AccountVerificationStatus = AccountVerificationStatus.UNVERIFIED
        protected set

    companion object {
        fun register(
            sellerId: Long,
            bankCode: String,
            accountNumberEnc: String,
            accountHolderName: String,
        ): SettlementAccount =
            SettlementAccount(
                sellerId = sellerId,
                bankCode = bankCode,
                accountNumberEnc = accountNumberEnc,
                accountHolderName = accountHolderName,
            )
    }
}
