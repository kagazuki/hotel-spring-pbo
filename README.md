# 🏨 Grand Palace Hotel - Sistem Manajemen & Reservasi Hotel
> **Tugas Praktikum Pemrograman Berorientasi Objek (PBO)**  
> **Arsitektur:** Backend Java (Spring Boot Framework) + Spring Data JPA + MySQL + Frontend Bootstrap 5

---

## 📌 1. Gambaran Umum Project
Aplikasi ini merupakan sistem informasi manajemen dan reservasi hotel berbasis web. Dibangun menggunakan arsitektur **RESTful API** dengan framework **Spring Boot (Java 17)** dan database relasional **MySQL**. Sistem ini mendukung peran ganda (**Admin** dan **Customer**) dengan proteksi sesi (*Session-based Authentication*), validasi pencegahan overbooking cerdas, manajemen stok kamar masal, dan alur persetujuan (*Approval Flow*) transaksi oleh Admin.

---

## 🏛️ 2. Kerangka Arsitektur Framework Java (Spring Boot)
Aplikasi ini menerapkan pola arsitektur **Layered Architecture (MVC - Model View Controller)** yang merupakan standar industri enterprise Java:

```
hotel-spring/
├── pom.xml                                   # Manajemen Dependensi Maven
└── src/main/
    ├── resources/
    │   ├── application.properties            # Konfigurasi Database & Server
    │   └── static/
    │       └── index.html                    # Frontend Web UI (Bootstrap 5)
    └── java/com/hotel/
        ├── HotelApplication.java            # Main Entry Point Aplikasi (@SpringBootApplication)
        │
        ├── config/                           # Lapisan Konfigurasi
        │   └── DatabaseConfig.java
        │
        ├── entity/                           # Lapisan Model / Entity (JPA & Hibernate)
        │   ├── User.java
        │   ├── RoomTier.java
        │   ├── Room.java
        │   ├── Reservation.java
        │   └── ReservationItem.java
        │
        ├── repository/                       # Lapisan Data Access Object (DAO / Spring Data JPA)
        │   ├── UserRepository.java
        │   ├── RoomTierRepository.java
        │   ├── RoomRepository.java
        │   ├── ReservationRepository.java
        │   └── ReservationItemRepository.java
        │
        ├── dto/                              # Lapisan Data Transfer Object (DTO)
        │   └── ReservationRequest.java
        │
        └── controller/                       # Lapisan REST Controller / API Endpoint
            ├── UserController.java
            ├── RoomTierController.java
            ├── RoomController.java
            └── ReservationController.java
```

---

## ⚙️ 3. Penjelasan Layer & Fungsi Masing-Masing File

### A. Main Entry Point
* **`HotelApplication.java`**:
  - **Anotasi:** `@SpringBootApplication`
  - **Fungsi:** Titik awal (*entry point*) jalannya seluruh sistem. Mengaktifkan fitur *Auto-Configuration*, *Component Scanning*, dan menjalankan embedded server Tomcat pada port yang ditentukan.

---

### B. Layer Entity (`com.hotel.entity`)
Layer ini bertindak sebagai **Object-Relational Mapping (ORM)** menggunakan **Jakarta Persistence (JPA)** dan **Hibernate**. Class Java di sini memetakan tabel database secara langsung ke dalam bentuk object berorientasi objek (PBO):
1. **`User.java`**: Representasi tabel `users` (id, username, email, password, full_name, role [ADMIN/CUSTOMER]).
2. **`RoomTier.java`**: Representasi tabel `room_tiers` (kategori kelas kamar, kapasitas, deskripsi, tarif dasar).
3. **`Room.java`**: Representasi tabel unit fisik `rooms` (nomor kamar, status [AVAILABLE/OCCUPIED/MAINTENANCE], relasi ManyToOne ke `RoomTier`).
4. **`Reservation.java`**: Representasi tabel transaksi `reservations` (kode booking unik, check-in, check-out, total bayar, status [PENDING/CONFIRMED/CANCELLED/COMPLETED]).
5. **`ReservationItem.java`**: Representasi tabel `reservation_items` (kamar-kamar yang dipesan dalam satu transaksi booking).

---

### C. Layer Repository (`com.hotel.repository`)
Layer ini mengimplementasikan **Spring Data JPA** (`JpaRepository`). Berfungsi sebagai jembatan manipulasi data (CRUD & Query) ke database MySQL tanpa perlu menulis SQL manual mentah:
1. **`UserRepository.java`**: Query pengecekan kredensial login (`findByUsername`), verifikasi email, dan registrasi.
2. **`RoomTierRepository.java`**: Pengambilan dan manipulasi data kategori kamar.
3. **`RoomRepository.java`**:
   - Memiliki method `@Query` tingkat lanjut: **`findAvailableRoomsForDates`** yang melakukan pengecekan ketersediaan kamar riil pada rentang tanggal booking untuk mencegah *overbooking*.
4. **`ReservationRepository.java`**: Query filter data transaksi berdasarkan `user_id` (isolasi riwayat customer) atau seluruh transaksi untuk Admin.
5. **`ReservationItemRepository.java`**: Pengambilan rincian kamar pada tiap invoice reservasi.

---

### D. Layer Controller (`com.hotel.controller`)
Layer ini bertindak sebagai **REST Controller** yang menerima HTTP Request dari klien (Web Browser / Postman), menjalankan validasi bisnis, dan mengembalikan respon berformat JSON:
1. **`UserController.java`**:
   - Endpoint autentikasi: `POST /api/users/login`, `GET /api/users/me`, `POST /api/users/logout`.
   - Registrasi customer publik: `POST /api/users/register`.
   - Pembuatan akun staff/admin: `POST /api/users/create-by-admin` (Admin Only).
   - Edit profil user: `PUT /api/users/profile`.
2. **`RoomTierController.java`**:
   - `GET /api/room-tiers`: Mengambil daftar tipe kamar.
   - `GET /api/room-tiers/stock-summary`: Menghitung dan menyajikan data stok unit kamar (total, available, occupied, maintenance) per tipe kamar secara real-time.
   - `POST`, `PUT`, `DELETE`: Manajemen tipe kamar oleh Admin.
3. **`RoomController.java`**:
   - `GET /api/rooms`: Mengambil data kamar dengan opsi filter status.
   - `GET /api/rooms/available-dates`: Mengembalikan kamar yang siap dibooking pada tanggal tertentu.
   - `POST /api/rooms`: Tambah kamar satuan.
   - `POST /api/rooms/add-stock`: **Fitur Tambah Stok Masal** (menambah 4 atau 5 kamar sekaligus ke tipe kamar tertentu).
   - `PUT /api/rooms/{id}` & `PUT /api/rooms/{id}/status`: Edit nomor kamar & ubah status kamar (Admin Only).
   - `DELETE /api/rooms/{id}`: Hapus unit kamar.
4. **`ReservationController.java`**:
   - `POST /api/reservations`: Pengajuan reservasi oleh user dengan kalkulasi harga multi-malam (Status awal: `PENDING`).
   - `GET /api/reservations`: Pengambilan riwayat transaksi (Customer hanya melihat miliknya, Admin melihat semua).
   - `PUT /api/reservations/{id}/status`: **Fitur Persetujuan Admin (Accept / Reject)** dan pembatalan pesanan.
   - `GET /api/reservations/stats`: Menyajikan ringkasan eksekutif (Total Pendapatan, Booking Pending, Okupansi Hotel).

---

### E. Layer Data Transfer Object (`com.hotel.dto`)
* **`ReservationRequest.java`**: Objek penampung request payload dari form pemesanan frontend (berisi `userId`, `checkInDate`, `checkOutDate`, dan array `roomIds`).

---

## 🌟 4. Fitur-Fitur Unggulan Sistem
| Fitur | Deskripsi |
| :--- | :--- |
| **Session Authentication** | Pengamanan sesi login menggunakan HTTP Session bawaan Java Servlet. |
| **Role-Based Access Control** | Pembatasan hak akses ketat antara `CUSTOMER` dan `ADMIN`. Menu pengguna dan aksi stok tersembunyi total dari customer. |
| **Pencegahan Overbooking** | Logika query tanggal riil memastikan unit kamar yang sudah berstatus CONFIRMED tidak bisa dipesan lagi pada rentang tanggal yang sama. |
| **Approval Flow (Accept / Reject)** | Setiap pengajuan booking masuk ke status `PENDING`. Admin berhak menyetujui (*Accept*) atau menolak (*Reject*). |
| **Manajemen Stok Kamar** | Menampilkan jumlah unit kamar tiap tipe, serta fitur penambahan stok masal (misal: tambah 4 atau 5 kamar sekaligus). |
| **Invoice Digital Siap Cetak** | Bukti pemesanan kamar lengkap dengan rincian tamu, kamar, dan tarif yang siap dicetak ke printer atau disimpan sebagai PDF. |
| **Dashboard Eksekutif** | Pemantauan total omset pendapatan, okupansi kamar, dan jumlah pemesanan pending secara langsung. |

---

## 🚀 5. Cara Menjalankan Aplikasi

### Prasyarat:
1. **Java JDK 17** terpasang.
2. **XAMPP (MySQL)** aktif di port default `3306`.
3. Database `hotel_db` sudah dibuat dan di-import dari file `hotel_db.sql`.

### Langkah Eksekusi:
1. Buka terminal pada direktori `hotel-spring`:
   ```bash
   cd hotel-spring
   ```
2. Jalankan aplikasi menggunakan Maven Wrapper:
   ```bash
   .\mvnw.cmd spring-boot:run
   ```
3. Buka browser dan akses:
   👉 **`http://localhost:8081`**

### Kredensial Akun Default:
* **Admin:** `username: admin` | `password: password123`
* **Customer:** `username: john_doe` | `password: password123` *(atau daftar akun baru via tombol Daftar)*

