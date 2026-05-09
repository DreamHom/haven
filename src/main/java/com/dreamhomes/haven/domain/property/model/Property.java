package com.dreamhomes.haven.domain.property.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;
    private String addressLine1;
    private String city;

    @Column(name = "state_name", nullable = false)
    private String state;

    @Column(nullable = false)
    private String country;

    @Enumerated(EnumType.STRING)
    private PropertyStatus status = PropertyStatus.ACTIVE;
}

