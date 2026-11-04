package com.hotel.controller;

import com.hotel.entity.Room;
import com.hotel.entity.RoomTier;
import com.hotel.entity.User;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTierRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@CrossOrigin(origins = "*")
public class RoomController {

    @Autowired
    private RoomRepository roomRepo;

    @Autowired
    private RoomTierRepository tierRepo;

    // Read: Semua kamar
    @GetMapping
    public ResponseEntity<List<Room>> getAll(@RequestParam(required = false) Room.RoomStatus status) {
        if (status != null) {
            return ResponseEntity.ok(roomRepo.findByStatus(status));
        }
        return ResponseEntity.ok(roomRepo.findAll());
    }

    // Read: Ketersediaan kamar riil sesuai rentang tanggal
    @GetMapping("/available-dates")
    public ResponseEntity<List<Room>> getAvailableForDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(roomRepo.findAvailableRoomsForDates(checkIn, checkOut));
    }

    // Read: Detail kamar by ID
    @GetMapping("/{id}")
    public ResponseEntity<Room> getById(@PathVariable Integer id) {
        return roomRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Create: Tambah Unit Kamar Satuan (Admin)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> req, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin yang boleh menambah kamar."));
        }

        if (req.get("tierId") == null || req.get("roomNumber") == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tipe kamar dan nomor kamar wajib diisi!"));
        }

        Integer tierId = Integer.valueOf(req.get("tierId").toString());
        String roomNumber = req.get("roomNumber").toString().trim();
        String statusStr = (String) req.getOrDefault("status", "AVAILABLE");

        if (roomRepo.existsByRoomNumber(roomNumber)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nomor kamar " + roomNumber + " sudah terdaftar!"));
        }

        RoomTier tier = tierRepo.findById(tierId).orElse(null);
        if (tier == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Room tier tidak ditemukan!"));
        }

        Room room = new Room();
        room.setTier(tier);
        room.setRoomNumber(roomNumber);
        try {
            room.setStatus(Room.RoomStatus.valueOf(statusStr));
        } catch (IllegalArgumentException e) {
            room.setStatus(Room.RoomStatus.AVAILABLE);
        }

        return ResponseEntity.ok(roomRepo.save(room));
    }

    // Create: Tambah Stok Masal / Bulk Rooms ke Tipe Kamar Tertentu (Admin)
    // Contoh: Admin minta tambah 4 atau 5 kamar sekaligus ke tipe kamar X
    @PostMapping("/add-stock")
    @Transactional
    public ResponseEntity<?> addStockBulk(@RequestBody Map<String, Object> req, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin."));
        }

        Integer tierId = Integer.valueOf(req.get("tierId").toString());
        int count = Integer.parseInt(req.get("count").toString());
        String prefix = req.getOrDefault("prefix", "").toString().trim();

        RoomTier tier = tierRepo.findById(tierId).orElse(null);
        if (tier == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tipe kamar tidak ditemukan!"));
        }

        if (count <= 0 || count > 50) {
            return ResponseEntity.badRequest().body(Map.of("message", "Jumlah stok yang ditambahkan minimal 1 dan maksimal 50 kamar!"));
        }

        List<Room> createdRooms = new ArrayList<>();
        int baseSeed = prefix.isEmpty() ? (tier.getId() * 100) : 0;
        int currentNum = 1;

        for (int i = 0; i < count; i++) {
            String roomNum;
            do {
                if (prefix.isEmpty()) {
                    roomNum = String.valueOf(baseSeed + currentNum);
                } else {
                    roomNum = prefix + "-" + (currentNum < 10 ? "0" + currentNum : currentNum);
                }
                currentNum++;
            } while (roomRepo.existsByRoomNumber(roomNum));

            Room r = new Room(tier, roomNum, Room.RoomStatus.AVAILABLE);
            createdRooms.add(roomRepo.save(r));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Berhasil menambahkan " + count + " stok kamar ke " + tier.getTierName() + "!",
                "createdCount", createdRooms.size()
        ));
    }

    // Update: Edit Data Kamar (Nomor Kamar, Tipe Kamar, Status)
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody Map<String, Object> req, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin yang boleh mengubah data kamar."));
        }

        return roomRepo.findById(id).map(room -> {
            if (req.get("roomNumber") != null) {
                String newRoomNum = req.get("roomNumber").toString().trim();
                if (!newRoomNum.equalsIgnoreCase(room.getRoomNumber()) && roomRepo.existsByRoomNumber(newRoomNum)) {
                    return ResponseEntity.badRequest().body((Object) Map.of("message", "Nomor kamar " + newRoomNum + " sudah digunakan!"));
                }
                room.setRoomNumber(newRoomNum);
            }

            if (req.get("tierId") != null) {
                Integer tierId = Integer.valueOf(req.get("tierId").toString());
                RoomTier tier = tierRepo.findById(tierId).orElse(null);
                if (tier != null) {
                    room.setTier(tier);
                }
            }

            if (req.get("status") != null) {
                try {
                    room.setStatus(Room.RoomStatus.valueOf(req.get("status").toString()));
                } catch (IllegalArgumentException ignored) {}
            }

            return ResponseEntity.ok((Object) roomRepo.save(room));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Update: Ubah Status Cepat (AVAILABLE, OCCUPIED, MAINTENANCE)
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Integer id, @RequestParam Room.RoomStatus status, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin yang boleh mengubah status kamar."));
        }

        return roomRepo.findById(id).map(room -> {
            room.setStatus(status);
            return ResponseEntity.ok(roomRepo.save(room));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Delete: Hapus Unit Kamar
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin yang boleh menghapus kamar."));
        }

        if (!roomRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        roomRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Kamar berhasil dihapus"));
    }
}
