package com.hotel.repository;

import com.hotel.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {
    Optional<Reservation> findByBookingCode(String bookingCode);
    List<Reservation> findByUserIdOrderByCreatedAtDesc(Integer userId);
    List<Reservation> findAllByOrderByCreatedAtDesc();
}

