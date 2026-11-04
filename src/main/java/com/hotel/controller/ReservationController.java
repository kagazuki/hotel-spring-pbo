package com.hotel.controller;

import com.hotel.dto.ReservationRequest;
import com.hotel.entity.Reservation;
import com.hotel.entity.ReservationItem;
import com.hotel.entity.Room;
import com.hotel.entity.User;
import com.hotel.repository.ReservationItemRepository;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/reservations")
@CrossOrigin(origins = "*")
public class ReservationController {

    @Autowired
    private ReservationRepository reservationRepo;

    @Autowired
    private ReservationItemRepository itemRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private RoomRepository roomRepo;

    // Ambil daftar reservasi (Admin: semua, Customer: miliknya sendiri)
    @GetMapping
    public ResponseEntity<List<Reservation>> getAll(HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        if (currentUser.getRole() == User.Role.ADMIN) {
            return ResponseEntity.ok(reservationRepo.findAllByOrderByCreatedAtDesc());
        } else {
            return ResponseEntity.ok(reservationRepo.findByUserIdOrderByCreatedAtDesc(currentUser.getId()));
        }
    }

    // Statistik Ringkasan Hotel untuk Dashboard Admin
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin."));
        }

        List<Reservation> allReservations = reservationRepo.findAll();
        List<Room> allRooms = roomRepo.findAll();
        long totalUsers = userRepo.count();

        long totalBookings = allReservations.size();
        long pendingBookings = allReservations.stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.PENDING).count();
        long activeBookings = allReservations.stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.CONFIRMED).count();

        BigDecimal totalRevenue = allReservations.stream()
                .filter(r -> r.getStatus() == Reservation.ReservationStatus.CONFIRMED || r.getStatus() == Reservation.ReservationStatus.COMPLETED)
                .map(Reservation::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long availableRooms = allRooms.stream().filter(r -> r.getStatus() == Room.RoomStatus.AVAILABLE).count();
        long occupiedRooms = allRooms.stream().filter(r -> r.getStatus() == Room.RoomStatus.OCCUPIED).count();
        long maintenanceRooms = allRooms.stream().filter(r -> r.getStatus() == Room.RoomStatus.MAINTENANCE).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBookings", totalBookings);
        stats.put("pendingBookings", pendingBookings);
        stats.put("activeBookings", activeBookings);
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalUsers", totalUsers);
        stats.put("totalRooms", allRooms.size());
        stats.put("availableRooms", availableRooms);
        stats.put("occupiedRooms", occupiedRooms);
        stats.put("maintenanceRooms", maintenanceRooms);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Integer id, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Silakan login terlebih dahulu"));
        }

        return reservationRepo.findById(id).map(res -> {
            if (currentUser.getRole() != User.Role.ADMIN && !res.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body((Object) Map.of("message", "Akses ditolak!"));
            }
            return ResponseEntity.ok((Object) res);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/items")
    public ResponseEntity<List<ReservationItem>> getItems(@PathVariable Integer id) {
        return ResponseEntity.ok(itemRepo.findByReservationId(id));
    }

    // Pembuatan Booking: Masuk status 'PENDING' terlebih dahulu menunggu review/approval Admin
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody ReservationRequest req, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Silakan login terlebih dahulu!"));
        }

        Integer targetUserId = (currentUser.getRole() == User.Role.ADMIN && req.getUserId() != null)
                ? req.getUserId()
                : currentUser.getId();

        User user = userRepo.findById(targetUserId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "User tidak ditemukan!"));
        }

        if (req.getRoomIds() == null || req.getRoomIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Pilih minimal satu kamar!"));
        }

        if (req.getCheckInDate() == null || req.getCheckOutDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tanggal check-in dan check-out harus ditentukan!"));
        }

        LocalDate today = LocalDate.now();
        if (req.getCheckInDate().isBefore(today)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tanggal check-in tidak boleh di masa lampau!"));
        }

        long nights = ChronoUnit.DAYS.between(req.getCheckInDate(), req.getCheckOutDate());
        if (nights <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tanggal check-out harus minimal 1 hari setelah check-in!"));
        }

        // Cek kamar yang benar-benar available pada rentang tanggal tersebut
        List<Room> availableForDates = roomRepo.findAvailableRoomsForDates(req.getCheckInDate(), req.getCheckOutDate());
        Set<Integer> availableRoomIds = new HashSet<>();
        for (Room r : availableForDates) {
            availableRoomIds.add(r.getId());
        }

        List<Room> chosenRooms = roomRepo.findAllById(req.getRoomIds());
        for (Room r : chosenRooms) {
            if (r.getStatus() == Room.RoomStatus.MAINTENANCE) {
                return ResponseEntity.badRequest().body(Map.of("message", "Kamar " + r.getRoomNumber() + " sedang dalam masa perbaikan (Maintenance)!"));
            }
            if (!availableRoomIds.contains(r.getId())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Kamar " + r.getRoomNumber() + " sudah dipesan orang lain pada rentang tanggal tersebut!"));
            }
        }

        // Hitung total biaya
        BigDecimal totalPerNight = BigDecimal.ZERO;
        for (Room r : chosenRooms) {
            totalPerNight = totalPerNight.add(r.getTier().getBasePrice());
        }
        BigDecimal totalAmount = totalPerNight.multiply(BigDecimal.valueOf(nights));

        String datePrefix = req.getCheckInDate().toString().replace("-", "");
        String randomSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String bookingCode = "BK-" + datePrefix + "-" + randomSuffix;

        Reservation res = new Reservation();
        res.setBookingCode(bookingCode);
        res.setUser(user);
        res.setCheckInDate(req.getCheckInDate());
        res.setCheckOutDate(req.getCheckOutDate());
        res.setTotalAmount(totalAmount);
        
        // Status awal: PENDING (Menunggu ditinjau dan di-ACC/Reject oleh Admin)
        res.setStatus(Reservation.ReservationStatus.PENDING);

        Reservation savedReservation = reservationRepo.save(res);

        for (Room r : chosenRooms) {
            ReservationItem item = new ReservationItem(savedReservation, r, r.getTier().getBasePrice());
            itemRepo.save(item);
        }

        return ResponseEntity.ok(savedReservation);
    }

    // Konfirmasi Pesanan oleh Admin (Accept / Reject) & Pembatalan oleh User
    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@PathVariable Integer id, @RequestParam Reservation.ReservationStatus status, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Silakan login terlebih dahulu!"));
        }

        return reservationRepo.findById(id).map(res -> {
            boolean isAdmin = currentUser.getRole() == User.Role.ADMIN;

            // Jika bukan Admin, user hanya boleh membatalkan pesanannya sendiri saat masih PENDING atau CONFIRMED
            if (!isAdmin) {
                if (!res.getUser().getId().equals(currentUser.getId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak!"));
                }
                if (status != Reservation.ReservationStatus.CANCELLED) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Customer hanya dapat mengajukan pembatalan (CANCELLED)."));
                }
            }

            res.setStatus(status);

            // Logika Status Kamar:
            // 1. Jika di-ACCEPT (CONFIRMED) oleh Admin dan check-in hari ini -> Ubah status kamar jadi OCCUPIED
            LocalDate today = LocalDate.now();
            List<ReservationItem> items = itemRepo.findByReservationId(res.getId());

            if (status == Reservation.ReservationStatus.CONFIRMED) {
                if (!res.getCheckInDate().isAfter(today) && res.getCheckOutDate().isAfter(today)) {
                    for (ReservationItem item : items) {
                        Room r = item.getRoom();
                        r.setStatus(Room.RoomStatus.OCCUPIED);
                        roomRepo.save(r);
                    }
                }
            } else if (status == Reservation.ReservationStatus.CANCELLED || status == Reservation.ReservationStatus.COMPLETED) {
                // 2. Jika di-REJECT / CANCELLED atau COMPLETED -> Kembalikan kamar ke AVAILABLE jika sebelumnya OCCUPIED
                for (ReservationItem item : items) {
                    Room r = item.getRoom();
                    if (r.getStatus() == Room.RoomStatus.OCCUPIED) {
                        r.setStatus(Room.RoomStatus.AVAILABLE);
                        roomRepo.save(r);
                    }
                }
            }

            return ResponseEntity.ok(reservationRepo.save(res));
        }).orElse(ResponseEntity.notFound().build());
    }
}
