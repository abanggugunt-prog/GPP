# HITUT AI — Aplikasi AI Pribadi Multifungsi & Gratis (Rp0 Prioritas)

HITUT AI adalah aplikasi Android berkinerja tinggi yang menyediakan asisten AI cerdas (Chat, Generator Gambar, dan Generator Video) dengan sistem **Backend Cadangan Otomatis (Failover)** sehingga aplikasi tetap aktif meskipun server utama terputus atau batas kuota tercapai.

---

## 🌟 Fitur Utama

1. **AI Chat (Pengalaman Mirip ChatGPT)**:
   - Terintegrasi dengan Google AI Studio (Gemini 3.5 Flash & Gemini 3.1 Pro).
   - Memahami konteks percakapan multi-turn.
   - Fitur salin jawaban, ulangi (regenerate), dan riwayat percakapan lokal di HP.
   - Deteksi batas kuota (HTTP 429) dengan panduan beralih ke backend cadangan.

2. **AI Generator Gambar**:
   - Menghasilkan gambar asli dari deskripsi teks (Text-to-Image) dan referensi gambar (Image-to-Image).
   - Pilihan rasio aspek (1:1, 16:9, 9:16, 4:3).
   - Mesin default: **Pollinations AI Flux (100% Bebas Biaya Rp0, tanpa API key)**, serta opsi Gemini Image atau Colab SD pribadi.
   - Tombol unduh hasil gambar langsung ke Galeri HP (folder `Pictures/HITUT_AI`).

3. **AI Generator Video**:
   - **Mode Text-to-Video**: Mengubah prompt teks menjadi video klip MP4.
   - **Mode Image-to-Video**: Menganimasikan foto dengan prompt gerakan kamera.
   - Antrean proses & indikator persentase render GPU langsung dari backend.
   - Pemutar video terintegrasi dan tombol unduh ke penyimpanan internal (`Movies/HITUT_AI`).

4. **Sistem Backend Cadangan Otomatis (Failover)**:
   - Pemisahan adapter untuk Chat, Gambar, dan Video (kerusakan di satu modul tidak mempengaruhi modul lain).
   - Backend Video Utama: Google Colab (GPU T4 gratis).
   - Backend Video Cadangan: Kaggle Notebooks.
   - Pemantauan status koneksi langsung (Online, Offline, Latensi ms).
   - Failover otomatis: jika Colab terputus, pekerjaan dialihkan ke server cadangan yang aktif.
   - Pembaruan URL dinamis tanpa perlu membangun ulang APK.

---

## 🚀 Panduan Penggunaan & Pengaturan

### 1. Menjalankan Aplikasi
Aplikasi dapat dijalankan langsung di emulator pratinjau browser AI Studio atau dibangun menjadi APK untuk dipasang pada ponsel Android fisik.

### 2. Mengatur Gemini dengan Kuota Gratis
1. Dapatkan API Key gratis di [Google AI Studio](https://aistudio.google.com/).
2. Masukkan API key pada panel **Secrets** di AI Studio (`GEMINI_API_KEY`).
3. Anda juga dapat memasukkan API Key cadangan langsung dari aplikasi melalui menu **Pengaturan Backend** -> **API Key Kustom**.

### 3. Menjalankan Backend Video di Google Colab
1. Buka [Google Colab](https://colab.research.google.com/).
2. Unggah file notebook `colab/hitut_video_backend.ipynb` yang disertakan dalam proyek ini.
3. Masuk ke menu **Runtime** > **Change runtime type**, lalu pilih **T4 GPU** (Tersedia gratis).
4. Klik **Runtime** > **Run all** (Jalankan semua sel).
5. Pada sel terakhir, salin URL publik yang dicetak oleh ngrok/localtunnel (contoh: `https://abcd-1234.ngrok-free.app`).

### 4. Menghubungkan Backend Video ke Aplikasi
1. Buka aplikasi **HITUT AI** di HP Anda.
2. Buka tab **Backend** (atau ikon Menu > **Pengaturan Backend**).
3. Tempelkan URL publik dari Google Colab ke kolom **Backend Video Utama: Google Colab**.
4. Tekan tombol **Simpan** lalu **Tes**. Indikator akan berubah menjadi hijau (Online) dengan informasi GPU.

### 5. Mengganti URL Backend Dinamis
Sesi gratis Google Colab umumnya memiliki batas waktu beberapa jam. Ketika sesi Colab berakhir dan Anda menjalankannya kembali:
- Cukup salin URL baru dari Colab.
- Buka **Pengaturan Backend** di HITUT AI dan perbarui URL.
- Tidak perlu menginstal ulang aplikasi!

### 6. Mengonfigurasi Backend Cadangan (Kaggle)
1. Buat notebook di [Kaggle](https://www.kaggle.com/code).
2. Aktifkan **GPU T4 x2** (Kaggle memberikan kuota 30 jam/minggu gratis).
3. Masukkan kode dari notebook `hitut_video_backend.ipynb`.
4. Salin URL publik ke kolom **Backend Video Cadangan: Kaggle Notebook** di aplikasi.
5. Aktifkan sakelar **Failover Otomatis**. Jika Colab kehabisan kuota, aplikasi akan otomatis memakai Kaggle.

### 7. Mengatasi Error Umum
- **Error HTTP 429 pada Chat**: Kuota gratis per menit/hari Google AI Studio telah tercapai. Tunggu 1 menit atau masukkan API Key sekunder di Pengaturan Backend.
- **Backend Video Offline**: Pastikan sel terakhir di notebook Google Colab masih berjalan dan URL ngrok belum kedaluwarsa.
- **CUDA Out of Memory di Backend**: Notebook telah dikonfigurasi dengan *attention slicing* dan *model cpu offload* untuk mengoptimalkan VRAM 15GB T4. Jika terjadi OOM, kurangi resolusi ke rasio 16:9 atau 4 detik.

---

## 📦 Cara Membangun APK Android

Aplikasi ini adalah native Android application berbasis Kotlin dan Jetpack Compose.

Untuk mengemas menjadi APK siap pasang:
1. Melalui AI Studio: Buka menu pengaturan proyek > **Export** / **Build APK**.
2. Melalui terminal lokal:
   ```bash
   # Masuk ke direktori proyek
   cd /workspace
   
   # Rakit APK rilis/debug
   gradle assembleDebug
   ```
3. File APK yang dihasilkan berada di:
   `app/build/outputs/apk/debug/app-debug.apk`
4. Transfer file `.apk` ke ponsel Android Anda dan pasang secara langsung.
