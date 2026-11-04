package com.hotel.repository;

import com.hotel.entity.RoomTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTierRepository extends JpaRepository<RoomTier, Integer> {
}

