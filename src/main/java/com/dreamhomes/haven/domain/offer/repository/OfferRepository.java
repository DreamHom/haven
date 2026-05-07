package com.dreamhomes.haven.domain.offer.repository;

import com.dreamhomes.haven.domain.offer.model.Offer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {}

