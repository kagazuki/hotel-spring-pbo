package com.hotel.controller;

import com.hotel.entity.Room;
import com.hotel.entity.RoomTier;
import com.hotel.entity.User;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.RoomTierRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/room-tiers")
@CrossOrigin(origins = "*")
public class RoomTierController {

    @Autowired
    private RoomTierRepository tierRepo;

    @Autowired
    private RoomRepository roomRepo;

    @GetMapping
    public ResponseEntity<List<RoomTier>> getAll() {
        return ResponseEntity.ok(tierRepo.findAll());
    }

    // Endpoint Rangkuman Stok Tiap Tipe Kamar (Total Kamar, Tersedia, Terisi, Maintenance)
    @GetMapping("/stock-summary")
    public ResponseEntity<?> getStockSummary() {
        List<RoomTier> tiers = tierRepo.findAll();
        List<Room> rooms = roomRepo.findAll();

        List<Map<String, Object>> summaryList = new ArrayList<>();

        for (RoomTier tier : tiers) {
            long total = 0;
            long available = 0;
            long occupied = 0;
            long maintenance = 0;
            List<Room> tierRooms = new ArrayList<>();

            for (Room r : rooms) {
                if (r.getTier().getId().equals(tier.getId())) {
                    total++;
                    tierRooms.add(r);
                    if (r.getStatus() == Room.RoomStatus.AVAILABLE) available++;
                    else if (r.getStatus() == Room.RoomStatus.OCCUPIED) occupied++;
                    else if (r.getStatus() == Room.RoomStatus.MAINTENANCE) maintenance++;
                }
            }

            Map<String, Object> map = new HashMap<>();
            map.put("tierId", tier.getId());
            map.put("tierName", tier.getTierName());
            map.put("basePrice", tier.getBasePrice());
            map.put("capacity", tier.getCapacity());
            map.put("description", tier.getDescription());
            map.put("totalStock", total);
            map.put("availableStock", available);
            map.put("occupiedStock", occupied);
            map.put("maintenanceStock", maintenance);
            map.put("rooms", tierRooms);

            summaryList.add(map);
        }

        return ResponseEntity.ok(summaryList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomTier> getById(@PathVariable Integer id) {
        return tierRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Admin: Tambah Tipe Kamar Baru (Opsional langsung tentukan jumlah stok awal)
    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, Object> req, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin yang boleh menambah tipe kamar."));
        }

        String tierName = (String) req.get("tierName");
        Object priceObj = req.get("basePrice");
        Object capObj = req.get("capacity");
        String description = (String) req.get("description");
        Object initialStockObj = req.get("initialStock"); // Misal: buat 4 kamar sekaligus

        if (tierName == null || tierName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nama tipe kamar tidak boleh kosong!"));
        }

        BigDecimal basePrice = new BigDecimal(priceObj.toString());
        Integer capacity = capObj != null ? Integer.valueOf(capObj.toString()) : 2;

        RoomTier tier = new RoomTier(tierName.trim(), basePrice, capacity, description);
        RoomTier savedTier = tierRepo.save(tier);

        // Jika admin menyertakan jumlah stok awal (misal: 4 atau 5 kamar)
        if (initialStockObj != null) {
            int stockCount = Integer.parseInt(initialStockObj.toString());
            if (stockCount > 0) {
                // Generate nomor kamar otomatis berdasarkan tierId (misal: 401, 402, ...)
                int baseNum = savedTier.getId() * 100;
                for (int i = 1; i <= stockCount; i++) {
                    String roomNumber = String.valueOf(baseNum + i);
                    while (roomRepo.existsByRoomNumber(roomNumber)) {
                        baseNum += 10;
                        roomNumber = String.valueOf(baseNum + i);
                    }
                    Room r = new Room(savedTier, roomNumber, Room.RoomStatus.AVAILABLE);
                    roomRepo.save(r);
                }
            }
        }

        return ResponseEntity.ok(savedTier);
    }

    // Admin: Edit Tipe Kamar
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Integer id, @RequestBody RoomTier tier, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin yang boleh mengedit tipe kamar."));
        }

        return tierRepo.findById(id).map(existing -> {
            if (tier.getTierName() != null && !tier.getTierName().trim().isEmpty()) {
                existing.setTierName(tier.getTierName());
            }
            if (tier.getBasePrice() != null && tier.getBasePrice().compareTo(BigDecimal.ZERO) > 0) {
                existing.setBasePrice(tier.getBasePrice());
            }
            if (tier.getCapacity() != null && tier.getCapacity() > 0) {
                existing.setCapacity(tier.getCapacity());
            }
            if (tier.getDescription() != null) {
                existing.setDescription(tier.getDescription());
            }
            return ResponseEntity.ok(tierRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Admin: Hapus Tipe Kamar
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Integer id, HttpSession session) {
        User currentUser = (User) session.getAttribute("currentUser");
        if (currentUser == null || currentUser.getRole() != User.Role.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Akses ditolak! Hanya Admin yang boleh menghapus tipe kamar."));
        }

        if (!tierRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (!roomRepo.findByTierId(id).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tidak bisa menghapus tipe kamar ini karena masih memiliki unit kamar!"));
        }

        tierRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Tipe kamar berhasil dihapus"));
    }
}
