package com.aechak.domain.user.pet

import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.pet.enums.Species
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "breeds")
class Breed protected constructor(
    species: Species,
    label: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val species: Species = species

    @Column(length = 100, nullable = false)
    val label: String = label

    companion object {
        fun of(
            species: Species,
            label: String,
        ): Breed = Breed(species, label)
    }
}
