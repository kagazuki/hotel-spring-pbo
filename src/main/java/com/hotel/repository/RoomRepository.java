package com.hotel.repository;

import com.hotel.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {
    List<Room> findByStatus(Room.RoomStatus status);
    List<Room> findByTierId(Integer tierId);
    boolean existsByRoomNumber(String roomNumber);

    // Cari kamar yang benar-benar AVAILABLE pada rentang tanggal check-in & check-out tertentu
    @Query("SELECT r FROM Room r WHERE r.status != 'MAINTENANCE' AND r.id NOT IN (" +
           "  SELECT item.room.id FROM ReservationItem item " +
           "  JOIN item.reservation res " +
           "  WHERE res.status IN ('PENDING', 'CONFIRMED') " +
           "  AND NOT (res.checkOutDate <= :checkIn OR res.checkInDate >= :checkOut)" +
           ")")
    List<Room> findAvailableRoomsForDates(@Param("checkIn") LocalDate checkIn, @Param("checkOut") LocalDate checkOut);
}
