package com.hotel.repository;

import com.hotel.entity.ReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservationItemRepository extends JpaRepository<ReservationItem, Integer> {
    List<ReservationItem> findByReservationId(Integer reservationId);
}

