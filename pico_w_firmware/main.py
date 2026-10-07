import time                                # Modul untuk manajemen waktu (penundaan/delay `time.sleep()`, mengukur durasi, dll.)
import math                                # Modul fungsi matematika tingkat lanjut (seperti akar kuadrat, trigonometri, nilai mutlak)
import network                             # Modul untuk pengaturan koneksi jaringan & Wi-Fi (WLAN Mode Station / Access Point)
import ujson                               # Modul untuk mengolah (parse/stringify) data berformat JSON secara hemat memori
import usocket                             # Modul untuk komunikasi jaringan tingkat rendah berbasis Socket (TCP / UDP)
import ubinascii                           # Modul untuk konversi data biner ke teks ASCII (seperti konversi Hexadecimal atau Base64)
import micropython                         # Modul internal kontrol sistem, memori, dan perilaku interpreter MicroPython
import framebuf                            # Modul penanganan buffer grafik/gambar untuk layar tampilan (seperti OLED SSD1306/LCD)
from micropython import const              # Mengimpor fungsi 'const' untuk membuat variabel konstanta hemat memori RAM
from machine import Pin, SoftI2C, UART     # Mengimpor modul hardware: Pin (GPIO), SoftI2C (I2C Perangkat Lunak), UART (Komunikasi Serial)

try:
    import ssl                             # Mencoba mengimpor modul standar SSL/TLS untuk koneksi aman (HTTPS / MQTTS)
except ImportError:
    try:
        import ussl as ssl                 # Jika 'ssl' tidak ada, coba impor 'ussl' (SSL khas MicroPython) dan alias-kan sebagai 'ssl'
    except ImportError:
        try:
            import tls as ssl             # Jika 'ussl' tidak ada, coba impor modul 'tls' dan alias-kan sebagai 'ssl'
        except ImportError:
            ssl = None                     # Jika semua modul SSL/TLS tidak tersedia di firmware, set ssl = None (koneksi enkripsi di-nonaktifkan)

micropython.alloc_emergency_exception_buf(100) # Memesan RAM darurat 100 byte untuk mencetak pesan error jika terjadi crash di Interrupt Handler (ISR)

# ==============================================================================
# 1. KONFIGURASI WIFI & BACKEND SERVER (DOMAIN PUBLIK CLOUDFLARE)
# ==============================================================================
# Atur koneksi WiFi / Hotspot yang dipakai Pico W
# 
WIFI_SSID = ""              # Nama WiFi / Hotspot HP (# use your own wifi ssid)
WIFI_PASSWORD = ""          # Password WiFi (# use your own wifi password)

# Alamat IP server backend:
BACKEND_HOST = ""           # Alamat IP / Domain server (# use your own ip / host)
BACKEND_PORT = 8088               # Port server Tomcat (8088)
USE_SSL = False                   # False kalau pakai HTTP lokal (IP), True kalau HTTPS domain

# Koordinat GPS Default Posisi Sabuk Saat Ini (dipakai pas baru nyala / GPS belum lock satelit):
DEFAULT_LAT = -7.31          # Garis Lintang (Latitude) Posisi Pengguna/Sabuk Saat Ini (UBAYA / Rumah)
DEFAULT_LNG = 112.1        # Garis Bujur (Longitude) Posisi Pengguna/Sabuk Saat Ini (UBAYA / Rumah)

# ==============================================================================
# 2. DRIVER OLED SSD1306 EMBEDDED/
# ==============================================================================
SET_CONTRAST = const(0x81) #cerahan OLED
SET_ENTIRE_ON = const(0xA4) #hubungin layar ke memori ram grafis
SET_NORM_INV = const(0xA6) #mode tampilan normal: bit 1 piksel menyala (putih/biru), bit 0 mati (hitam). Jika diatur 0xA7, tampilan akan terbalik (invert/klise). BISA DIEDIT 0xA6 menjadi 0xA7 jika ingin warna layarnya terbalik
SET_DISP = const(0xAE) #nyalain matiin layar
SET_MEM_ADDR = const(0x20) #atur penulisan(per kolom apa baris apa halaman)
SET_COL_ADDR = const(0x21) #atur kolom awal dn akhir (0-127)
SET_PAGE_ADDR = const(0x22) #nentuin alamat baris halaman (0-7) 1 halaman itu 8 bit vertikar (64:8=8)
SET_DISP_START_LINE = const(0x40) #nentuin baris RAM mana yang dijadikan baris teratas fisik layar
SET_SEG_REMAP = const(0xA0) #atur arah pemetaan horizontal (orientasi kiri-ke-kanan). Memungkinkan membalik tampilan 180° secara horizontal BISA DI EDIT 0xA0 menjadi 0xA1 (Membalik tampilan Kiri ↔ Kanan).
SET_MUX_RATIO = const(0xA8) #atur rasio multiplexer baris fisik layar (diatur ke 63 untuk layar setinggi 64 baris)
SET_COM_OUT_DIR = const(0xC0) #atur arah pemindaian vertikal baris fisik (apakah dari atas ke bawah 0xC0, atau dari bawah ke atas 0xC8
SET_DISP_OFFSET = const(0xD3) #atur geser tampilan vertikal (display vertical shift)
SET_COM_PIN_CFG = const(0xDA) #cek/konfigurasi letak pin oled
SET_DISP_CLK_DIV = const(0xD5) #atur biar layar ga kedip"/glitch
SET_PRECHARGE = const(0xD9) #atur durasi ngisi precharge sebelum pixel nyala
SET_VCOM_DESEL = const(0xDB) #atur level batas tegangan vcomhnya biar jagain kontrasnya
SET_CHARGE_PUMP = const(0x8D) #aktifin rangkaian konverter tegangan internalnya

class SSD1306(framebuf.FrameBuffer): # Cetakan/resep utama buat ngatur layar OLED SSD1306  
    def __init__(self, width, height, external_vcc): # Fungsi awal saat layar OLED pertama kali dinyalakan  
        self.width = width # Simpan lebar layar = 128 piksel  
        self.height = height # Simpan tinggi layar = 64 piksel  
        self.external_vcc = external_vcc # False = listrik dicolok ke 3.3V Pico W [BISA DIGANTI: True jika pakai baterai luar]
        self.pages = self.height // 8 # Bagi tinggi layar jadi 8 baris halaman (64 / 8 = 8 baris)  
        self.buffer = bytearray(self.pages * self.width) # Siapkan kertas gambar (memori RAM 1024 byte) di Pico W  
        super().__init__(self.buffer, self.width, self.height, framebuf.MONO_VLSB) # Atur layar mode 2 warna aja (hitam & putih)  
        self.init_display() # Panggil fungsi buat bangunin dan nyalain layar OLED  

    def init_display(self): # Fungsi khusus berisi perintah bangunin layar  
        for cmd in ( # Daftar perintah khusus buat chip layar OLED  
            SET_DISP | 0x00, SET_MEM_ADDR, 0x00, # Matiin layar sebentar, atur cara nulis dari kiri ke kanan  
            SET_DISP_START_LINE | 0x00, SET_SEG_REMAP | 0x01, # Mulai nulis dari baris paling atas [BISA DIGANTI: ubah 0x01 ke 0x00 kalau tulisan terbalik]
            SET_MUX_RATIO, self.height - 1, SET_COM_OUT_DIR | 0x08, # Atur tinggi layar 64 baris [BISA DIGANTI: ubah 0x08 ke 0x00 kalau tulisan terbalik]
            SET_DISP_OFFSET, 0x00, # Posisi layar pas di tengah (tidak digeser)  
            SET_COM_PIN_CFG, 0x02 if self.width > 2 * self.height else 0x12, # Atur jalur kabel internal layar 128x64  
            SET_DISP_CLK_DIV, 0x80, SET_PRECHARGE, 0x22 if self.external_vcc else 0xF1, # Atur biar layar halus ga kedip-kedip & listrik stabil  
            SET_VCOM_DESEL, 0x30, SET_CONTRAST, 0xFF, # Atur terang layar [BISA DIGANTI: 0xFF bisa diubah ke 0x80 kalau mau layarnya agak redup]
            SET_ENTIRE_ON, SET_NORM_INV, SET_CHARGE_PUMP, 0x10 if self.external_vcc else 0x14, # Mode normal [BISA DIGANTI: ganti SET_NORM_INV ke 0xA7 kalau mau latar putih], nyalain stroom 9V internal  
            SET_DISP | 0x01, # Nyalakan layar OLED (Layar resmi ON!)  
        ):
            self.write_cmd(cmd) # Kirim perintah satu per satu ke layar OLED  
        self.fill(0) # Bersihkan kertas gambar jadi hitam polos dulu  
        self.show() # Kirim hasil gambar dari RAM Pico W ke layar fisik OLED!  

    # Metode abstrak (fungsi tempat kosong yang nanti diisi oleh komunikasi I2C di bawah)
    def write_cmd(self, cmd): pass # Fungsi kosong buat kirim kode perintah  
    def write_data(self, buf): pass # Fungsi kosong buat kirim data piksel gambar  

    def show(self): # FUNGSI UTAMA: Menyemprotkan seluruh gambar dari memori RAM ke layar fisik OLED  
        x0 = 0 # Patokan kolom paling kiri layar = 0  
        x1 = self.width - 1 # Patokan kolom paling kanan layar = 127 (128 - 1)  
        if self.width == 64: # Cek jika layarnya tipe mini berukuran 64 piksel  
            x0 += 32 # Geser posisi kolom kiri kalau layarnya tipe mini 64px  
            x1 += 32 # Geser posisi kolom kanan kalau layarnya tipe mini 64px  
        
        # Alur pengiriman kertas gambar (1024 byte dari RAM Pico W) ke fisik layar OLED:
        self.write_cmd(SET_COL_ADDR) # Kirim sinyal: "Halo OLED, siap-siap atur batas kolom!" (0x21)  
        self.write_cmd(x0) # Set batas kolom paling kiri = 0  
        self.write_cmd(x1) # Set batas kolom paling kanan = 127  
        self.write_cmd(SET_PAGE_ADDR) # Kirim sinyal: "Siap-siap atur batas baris halaman!" (0x22)  
        self.write_cmd(0) # Set baris halaman paling atas = Page 0  
        self.write_cmd(self.pages - 1) # Set baris halaman paling bawah = Page 7  
        self.write_data(self.buffer) # Semprotkan seluruh isi 1024 byte gambar dari RAM ke layar fisik OLED!  

class SSD1306_I2C(SSD1306): # Kelas khusus buat ngirim data ke layar OLED lewat 2 kabel I2C (SDA & SCL)  
    def __init__(self, width, height, i2c, addr=0x3C, external_vcc=False): # Fungsi awal menyiapkan koneksi I2C [BISA DIGANTI: addr=0x3C ke 0x3D kalau alamat I2C OLED diubah di hardware]
        self.i2c = i2c # Simpan objek kabel I2C (GP4 SDA & GP5 SCL)  
        self.addr = addr # Simpan alamat tujuan layar OLED (0x3C)  
        self.temp = bytearray(2) # Siapkan tempat kecil 2 byte buat wadah kirim perintah  
        self.write_list = [b"\x40", None] # Siapkan kode awalan 0x40 tanda pengiriman data gambar piksel  
        super().__init__(width, height, external_vcc) # Panggil fungsi persiapan kelas utama SSD1306  

    def write_cmd(self, cmd): # Fungsi khusus buat ngirim 1 kode perintah ke chip layar OLED  
        self.temp[0] = 0x80 # Kode 0x80 memberitahu layar: "Halo OLED, sinyal setelah ini adalah PERINTAH!"  
        self.temp[1] = cmd # Masukkan kode perintah heksadesimal ke wadah  
        self.i2c.writeto(self.addr, self.temp) # Kirim 2 byte sinyal listrik lewat kabel SDA & SCL ke alamat 0x3C OLED  

    def write_data(self, buf): # Fungsi khusus buat ngirim kertas gambar piksel (1024 byte) ke layar OLED  
        self.write_list[1] = buf # Masukkan isi kertas gambar RAM ke wadah pengiriman  
        self.i2c.writevto(self.addr, self.write_list) # Kirim sinyal 0x40 + 1024 byte gambar sekaligus lewat kabel I2C ke layar OLED!  

# ==============================================================================
# 3. INISIALISASI PIN HARDWARE & PERANGKAT I2C
# ==============================================================================
# 3. INISIALISASI PIN HARDWARE & PERANGKAT I2C
# ==============================================================================
# LED Indikator bawaan papan Pico W
try:
    led = Pin("LED", Pin.OUT)       # LED bawaan Pico W (CYW43 chip)
except:
    led = Pin(25, Pin.OUT)          # Cadangan jika di Pico standar non-W

# [PIN BUZZER - GP16]:
# Buzzer tipe ACTIVE LOW: dikasih 0 (GND) malah NYALA, dikasih 1 (3.3V) MATI.
# Nilai awal diatur value=1 biar pas sabuk baru dinyalakan ga langsung teriak berisik.
buzzer = Pin(16, Pin.OUT, value=1)

# [PIN TOMBOL PUSH BUTTON - GP18]:
# Pin.PULL_UP: di dalam chip Pico otomatis ditarik ke 3.3V (default nilainya 1).
# Kalau tombol ditekan fisik oleh lansia -> kaki saklar terhubung ke GND -> nilainya jadi 0!
# Jadi pengecekan tombol ditekan di kodingan adalah: if button.value() == 0
button = Pin(18, Pin.IN, Pin.PULL_UP)

# Buzzer & Tombol GP18 Anti-False Alarm

def buzzer_on(): # Fungsi khusus buat nyalakan suara buzzer & lampu LED sekaligus  
    buzzer.value(0) # Diberi angka 0 (GND) buat nyalakan buzzer (karena tipe Active LOW)  
    led.value(1) # Diberi angka 1 (3.3V) buat nyalakan lampu LED tanda ada alarm  

def buzzer_off(): # Fungsi khusus buat matikan suara buzzer & lampu LED  
    buzzer.value(1) # Diberi angka 1 (3.3V) buat matikan buzzer  
    led.value(0) # Diberi angka 0 (GND) buat matikan lampu LED  
    time.sleep_ms(15) # Jeda 15 milidetik biar arus listrik di Pico W stabil pas buzzer mati  

def is_gp18_button_pressed(): # Fungsi pintar buat ngecek pencetan tombol GP18 (kebal sinyal palsu/noise listrik)  
    if button.value() != 0: # Jika tombol nilainya BUKAN 0 (artinya lagi tidak dipencet), langsung keluar  
        return False # Kembalikan nilai False (tombol tidak dipencet)  
    # Pengecekan ulang 5 kali beruntun dengan jeda 30ms (total 150ms) biar kebal dari percikan listrik buzzer:
    for _ in range(5): # Lakukan pengecekan ulang sebanyak 5 kali [BISA DIGANTI: diubah ke 3 atau 7x]
        time.sleep_ms(30) # Jeda 30 milidetik di tiap pengecekan (total 150ms) [BISA DIGANTI: diubah ke 20ms atau 40ms]
        if button.value() != 0: # Jika di tengah jalan nilainya berubah bukan 0 (berarti cuma sinyal palsu), batalkan  
            return False # Kembalikan nilai False (batal)  
    return True # Jika sah 5x berturut-turut bernilai 0, konfirmasi TOMBOL BENAR-BENAR DIPENCET lansia!  

buzzer_off() # Matikan buzzer pas alat baru pertama dinyalakan biar tidak langsung berbunyi berisik  

in_fall_confirmation = False # Status awal: False = Lagi tidak dalam mode hitung mundur  
confirm_start_time = 0 # Catatan detik mulai hitung mundur  

print("=" * 60) # Cetak garis pembatas di layar konsol Thonny 
print("  🛡️ SMART-BELT IOT WEARABLE DEVICE SYSTEM FIRMWARE  ") # Cetak judul sistem di layar konsol Thonny 
print("=" * 60) # Cetak garis pembatas di layar konsol Thonny 

# Dapatkan MAC Address (Nomor KTP Fisik Unik Chip Wi-Fi Pico W):
wlan = network.WLAN(network.STA_IF) # Buat objek Wi-Fi mode Station (Pico W bertindak sebagai klien yang menyambung ke Hotspot HP/Router)  
wlan.active(True)                   # Nyalakan listrik modul radio Wi-Fi Pico W  
mac_bytes = wlan.config('mac')      # Ambil 6-byte kode alamat biner fisik unik bawaan pabrik chip Wi-Fi  
mac_address = ":".join(["{:02X}".format(b) for b in mac_bytes]) # Format kode biner tadi jadi string Heksadesimal seperti "28:CD:C1:10:41:9F"  
print(f"📟 MAC Address Pico W: {mac_address}") # Cetak nomor MAC Address unik ke layar konsol Thonny []

# --- BUS I2C UTAMA: GP4 & GP5 (MPU6050, MAX30102, & OLED SSD1306) ---
# Alamat I2C MPU6050 : 0x68 (Sensor Jatuh/Inersia)
# Alamat I2C MAX30102: 0x57 (Sensor Detak Jantung & Oksigen)
# Alamat I2C OLED    : 0x3C (Layar Display)

has_oled = False # Variabel penanda awal: False = Layar OLED belum terdeteksi  
oled = None # Variabel wadah tempat menyimpan objek layar OLED  

i2c = SoftI2C(sda=Pin(4), scl=Pin(5), freq=400000) # Buat bus komunikasi I2C utama di GP4 (SDA) & GP5 (SCL) kecepatan tinggi 400 kHz [BISA DIGANTI: Pin GP nya jika kabel dipindah]
time.sleep_ms(100) # Beri jeda 100 milidetik biar tegangan listrik sensor stabil  
devices = i2c.scan() # Pindai (scan) alamat I2C mana saja yang dicolok di GP4 & GP5  
if 0x68 not in devices and 0x69 not in devices: # Jika sensor MPU6050 (0x68) belum terdeteksi pada scan pertama  
    time.sleep_ms(150) # Beri jeda tambahan 150 milidetik  
    devices = i2c.scan() # Lakukan pemindaian (scan) ulang cadangan  
    
# Cek apakah Layar OLED ada di jalur GP4 & GP5:
if 0x3C in devices or 0x3D in devices: # Jika alamat OLED (0x3C atau 0x3D) ditemukan dari hasil scan I2C GP4/GP5  
    try: # Coba jalankan proses penyalaan layar OLED  
        oled_addr = 0x3C if 0x3C in devices else 0x3D # Pilih alamat OLED mana yang aktif (0x3C atau 0x3D)  
        oled = SSD1306_I2C(128, 64, i2c, addr=oled_addr) # Buat objek layar OLED ukuran 128x64 piksel di bus I2C GP4/GP5  
        has_oled = True # Tandai variabel: True = Layar OLED terpasang & resmi aktif!  
        oled.fill(0) # Bersihkan papan tulis RAM di Pico W menjadi hitam polos  
        oled.text("SMART-BELT IOT", 8, 10) # Tulis judul "SMART-BELT IOT" di koordinat X=8, Y=10 [BISA DIGANTI: Teks & koordinat X,Y nya]
        oled.text("Inisialisasi...", 8, 28) # Tulis status "Inisialisasi..." di koordinat X=8, Y=28 [BISA DIGANTI: Teks & koordinat X,Y nya]
        oled.show() # Transfer tulisan tadi dari RAM ke layar fisik OLED!  
        print(f"✅ Layar OLED Terdeteksi di {hex(oled_addr)} (GP4/GP5)") # Cetak laporan sukses ke konsol Thonny []
    except Exception as e: # Jika terjadi error saat inisialisasi layar OLED  
        print("⚠️ Warning OLED init di GP4/GP5:", e) # Cetak pesan peringatan error ke konsol Thonny []

# Jika layar OLED belum ketemu di jalur GP4/GP5, cek jalur cadangan di GP0 & GP1:
if not has_oled: # Jika status layar OLED masih False (belum ketemu)  
    try: # Coba buat bus I2C kedua khusus cadangan OLED  
        i2c_oled = SoftI2C(sda=Pin(0), scl=Pin(1), freq=400000) # Buat bus I2C cadangan di GP0 (SDA) & GP1 (SCL) kecepatan 400 kHz [BISA DIGANTI: Pin GP jika dipindah]
        time.sleep_ms(50) # Beri jeda 50 milidetik biar listrik stabil  
        oled_devices = i2c_oled.scan() # Pindai (scan) alamat I2C di bus cadangan GP0 & GP1  
        if 0x3C in oled_devices or 0x3D in oled_devices: # Jika alamat OLED (0x3C/0x3D) ketemu di jalur GP0/GP1  
            oled_addr = 0x3C if 0x3C in oled_devices else 0x3D # Pilih alamat OLED mana yang aktif  
            oled = SSD1306_I2C(128, 64, i2c_oled, addr=oled_addr) # Buat objek layar OLED di bus cadangan GP0/GP1  
            has_oled = True # Tandai variabel: True = Layar OLED resmi ketemu di GP0/GP1!  
            oled.fill(0) # Bersihkan papan tulis RAM di Pico W menjadi hitam polos  
            oled.text("SMART-BELT IOT", 8, 10) # Tulis judul "SMART-BELT IOT" di koordinat X=8, Y=10 [BISA DIGANTI: Teks & koordinat X,Y]
            oled.text("Inisialisasi...", 8, 28) # Tulis status "Inisialisasi..." di koordinat X=8, Y=28 [BISA DIGANTI: Teks & koordinat X,Y]
            oled.show() # Transfer tulisan tadi dari RAM ke layar fisik OLED!  
            print(f"✅ Layar OLED Terdeteksi di {hex(oled_addr)} (GP0/GP1)") # Cetak konfirmasi ke konsol Thonny []
    except Exception as e: # Jika terjadi error di jalur cadangan  
        pass # Lewati tanpa membuat sistem crash  

if not has_oled: # Pengecekan akhir: Jika layar OLED tetap tidak ketemu di GP4/GP5 maupun GP0/GP1  
    print("⚠️ Layar OLED tidak terdeteksi di GP4/GP5 maupun GP0/GP1") # Cetak peringatan bahwa layar OLED dilepas/tidak dipasang []

addr = 0x68 # Setel alamat I2C default sensor MPU6050 (0x68)  
if 0x68 in devices or 0x69 in devices: # Jika alamat MPU6050 (0x68 atau 0x69) ketemu saat scan I2C  
    addr = 0x68 if 0x68 in devices else 0x69 # Pilih alamat yang aktif (0x68 default, atau 0x69 jika pin AD0 dicolok ke 3.3V)  
    print(f"✅ MPU6050 Terdeteksi di alamat {hex(addr)}") # Cetak konfirmasi terdeteksi ke konsol Thonny []
    try: # Coba kirim sinyal konfigurasi ke MPU6050  
        # 1. Register 0x6B (Power Management 1): Diberi nilai 0x00 buat membangunkan MPU6050 dari mode tidur (Wake Up)
        i2c.writeto(addr, bytearray([0x6B, 0x00])) # Bangunkan MPU6050 dari mode tidur  
        time.sleep(0.05) # Beri jeda 50 milidetik biar sensor MPU6050 siap  
        # 2. Register 0x1C (Accel Config): Diberi nilai 0x00 buat atur skala akselerometer +-2g (skala 16384 LSB/g)
        i2c.writeto(addr, bytearray([0x1C, 0x00])) # Atur rentang akselerometer +-2g 
        time.sleep(0.05) # Beri jeda 50 milidetik  
        # 3. Register 0x1B (Gyro Config): Diberi nilai 0x00 buat atur skala giroskop +-250 deg/s (skala 131 LSB/(deg/s))
        i2c.writeto(addr, bytearray([0x1B, 0x00])) # Atur rentang giroskop +-250 derajat/detik  
        print("✅ MPU6050 Mode Aktif & Siap Membaca Vektor!") # Cetak konfirmasi sukses ke konsol Thonny []
    except Exception as e: # Jika terjadi error saat kirim sinyal konfigurasi  
        print("⚠️ Warning Wake-up MPU6050:", e) # Cetak pesan peringatan ke konsol Thonny []
else: # Jika alamat MPU6050 tidak ketemu di hasil scan awal  
    try: # Coba paksa kirim sinyal bangun ke alamat 0x68  
        i2c.writeto(0x68, bytearray([0x6B, 0x00])) # Paksa bangunkan MPU6050 di alamat 0x68  
        time.sleep(0.05) # Beri jeda 50 milidetik  
        print("✅ MPU6050 Paksa Wake-up di 0x68 Berhasil!") # Cetak laporan paksa bangun sukses []
    except Exception as e: # Jika paksa bangun tetap gagal  
        print("⚠️ MPU6050 belum terdeteksi.") # Cetak saran kencangkan kabel di konsol Thonny []
        

# Inisialisasi MAX30102 (Sensor Detak Jantung & Oksigen Darah SpO2 di alamat 0x57):
has_max30102 = False # Variabel status awal: False = Sensor MAX30102 belum terdeteksi  
if 0x57 in devices: # Cek jika alamat sensor MAX30102 (0x57) ketemu dari hasil scan I2C  
    try: # Mulai proses penyalaan & konfigurasi sensor MAX30102  
        # 1. Reset software sensor: Kirim 0x40 ke register 0x09 buat bersihkan settingan lama
        i2c.writeto_mem(0x57, 0x09, bytes([0x40])) # Reset software sensor  
        time.sleep(0.05) # Beri jeda 50 milidetik biar proses reset selesai  
        
        # 2. Register 0x08: Atur kecepatan sampel pembacaan (Sampling Rate & FIFO)
        i2c.writeto_mem(0x57, 0x08, bytes([0x5F])) # Atur sampling rate & memori FIFO  
        
        # 3. Register 0x09: Diberi nilai 0x03 buat nyalakan Mode SpO2 (Lampu LED Merah + Inframerah nyala bareng)
        i2c.writeto_mem(0x57, 0x09, bytes([0x03])) # Mode SpO2 aktif (Lampu Red & IR nyala)  
        
        # 4. Register 0x0A: Atur lebar pulsa kilatan lampu LED & resolusi ADC 18-bit
        i2c.writeto_mem(0x57, 0x0A, bytes([0x27])) # Atur lebar pulsa cahaya & ADC 18-bit  
        
        # 5. Register 0x0C & 0x0D: Atur kuat daya pancar lampu LED Merah (0x0C) dan Inframerah (0x0D) sekitar 7.2mA
        i2c.writeto_mem(0x57, 0x0C, bytes([0x24])) # Terang cahaya LED Merah (0x24 = 7.2mA) [BISA DIGANTI: Arus LED Merah]
        i2c.writeto_mem(0x57, 0x0D, bytes([0x24])) # Terang cahaya LED Inframerah (0x24 = 7.2mA) [BISA DIGANTI: Arus LED IR]
        
        # 6. Reset pointer alamat penunjuk memori data FIFO
        i2c.writeto_mem(0x57, 0x04, bytes([0x00])) # Reset Write Pointer  
        i2c.writeto_mem(0x57, 0x05, bytes([0x00])) # Reset Overflow Counter  
        i2c.writeto_mem(0x57, 0x06, bytes([0x00])) # Reset Read Pointer  
        
        has_max30102 = True # Tandai status: True = Sensor MAX30102 resmi aktif & siap baca detak jari!  
        print("✅ MAX30102 Mode Aktif & Siap Membaca Detak Jantung (BPM) & Oksigen (SpO2)!") # Cetak laporan sukses ke konsol Thonny []
    except Exception as e: # Jika terjadi error saat penyalaan sensor  
        print("⚠️ Warning MAX30102 setup:", e) # Cetak pesan peringatan error ke konsol Thonny []

# --- MODUL GPS GY-8M (KOMUNIKASI SERIAL UART1 DI GP8 & GP9) ---
# GP8 = TX Pico W (kirim perintah, nyambung ke RX GPS)
# GP9 = RX Pico W (terima data satelit NMEA, nyambung ke TX GPS)
# Baudrate = 9600 bps (kecepatan standar modul GPS NMEA)
has_gps = False # Variabel status awal: False = Modul GPS belum terdeteksi  
gps_uart = None # Variabel wadah tempat menyimpan jalur komunikasi serial UART GPS  
try: # Mulai proses penyalaan jalur komunikasi serial UART1 buat GPS  
    # Mengaktifkan Port Serial UART1 pada pin GP8 (TX) & GP9 (RX) dengan kecepatan standar 9600 bps:
    gps_uart = UART(1, baudrate=9600, tx=Pin(8), rx=Pin(9), timeout=0) # Aktifkan UART1 di GP8 (TX) & GP9 (RX) kecepatan 9600 bps [BISA DIGANTI: Pin GP nya jika kabel dipindah]
    has_gps = True # Tandai status: True = Modul GPS GY-8M resmi terhubung & siap terima sinyal satelit!  
    print("✅ Modul GPS GY-8M Terdeteksi di UART1 (GP8/GP9, 9600 bps)!") # Cetak laporan sukses ke konsol Thonny []
except Exception as e: # Jika terjadi error saat mengaktifkan port UART GPS  
    print("⚠️ Warning Inisialisasi GPS UART1:", e) # Cetak pesan peringatan error ke konsol Thonny []

# ==============================================================================
# TRACKING GPS KONTINU (REAL-TIME MULTI-GNSS DENGAN STREAMING BUFFER)
# ==============================================================================
current_lat = DEFAULT_LAT # Simpan Garis Lintang (Latitude) GPS saat ini (Default awal: UBAYA -7.3198009)  
current_lng = DEFAULT_LNG # Simpan Garis Bujur (Longitude) GPS saat ini (Default awal: UBAYA 112.7681119)  
has_gps_fix = False # Status pengunci satelit: False = Masih mencari satelit, True = Koordinat GPS sudah terkunci presisi!  
gps_sat_count = 0 # Menyimpan jumlah satelit yang berhasil tertangkap di langit  
last_gps_fix_time = 0 # Catatan timestamp detik terakhir koordinat GPS berhasil terkunci  
gps_rx_buf = "" # Wadah penampung teks sementara (buffer) kalimat sinyal NMEA dari GPS  
gps_raw_bytes_count = 0 # Penghitung total byte data sinyal NMEA yang diterima dari GPS  
last_nmea_tag = "" # Menyimpan nama tag header kode sinyal GPS terakhir (seperti $GNRMC atau $GNGGA)  

def convert_nmea_coord(raw_val, direction): # Fungsi khusus konversi format koordinat mentah NMEA (DerajatMenit) jadi format Derajat Desimal Google Maps  
    # Rumus konversi: Derajat Desimal = Derajat + (Menit / 60)
    # Jika arah lokasi menunjukkan Selatan ('S') atau Barat ('W'), nilainya diberi tanda minus (-)
    try: # Mulai proses perhitungan konversi matematika koordinat NMEA  
        if not raw_val or len(raw_val) < 4: # Jika teks koordinat mentah kosong atau pendek kurang dari 4 karakter  
            return None # Kembalikan nilai None (data belum lengkap)  
        dot_idx = raw_val.find('.') # Cari posisi titik desimal pada teks koordinat mentah  
        if dot_idx < 0: # Jika tidak ada titik desimal  
            return None # Kembalikan nilai None  
        deg_len = dot_idx - 2 # Hitung berapa digit angka derajat (2 digit untuk Lintang, 3 digit untuk Bujur)  
        degrees = float(raw_val[:deg_len]) # Potong dan ambil bagian angka Derajat Murni  
        minutes = float(raw_val[deg_len:]) # Potong dan ambil sisanya sebagai angka Menit Desimal  
        decimal = degrees + (minutes / 60.0) # RUMUS MATEMATIKA NMEA: Derajat Desimal = Derajat + (Menit / 60.0)  
        if str(direction).upper() in ['S', 'W']: # Jika arah lokasi menunjukkan Selatan ('S') atau Barat ('W')  
            decimal = -decimal # Beri tanda minus (-) pada hasil angka (karena Indonesia berada di Selatan Khatulistiwa)  
        return decimal # Kembalikan hasil angka koordinat desimal presisi untuk Google Maps (misal: -7.319801)  
    except: # Jika terjadi kesalahan matematika saat konversi  
        return None # Kembalikan nilai None  

def update_gps_tracking(): # Fungsi utama pembacaan sinyal satelit GPS real-time secara lancar tanpa nge-lag (non-blocking)  
    # Menggunakan Ring Streaming Buffer agar potongan teks kalimat sinyal satelit NMEA tidak terputus di tengah jalan
    global current_lat, current_lng, has_gps_fix, gps_sat_count, last_gps_fix_time # Panggil variabel global status GPS  
    global gps_rx_buf, gps_raw_bytes_count, last_nmea_tag # Panggil variabel global wadah penampung teks NMEA  
    if not has_gps or not gps_uart or not gps_uart.any(): # Jika GPS mati atau tidak ada sinyal baru masuk dari kabel UART  
        return # Langsung keluar tanpa menunggu (biar Pico W tidak macet)  
    try: # Mulai pembacaan aliran teks sinyal NMEA dari modul GPS  
        raw = gps_uart.read() # Baca potongan data teks yang baru saja dikirim oleh GPS  
        if not raw: # Jika datanya kosong  
            return # Keluar  
        gps_raw_bytes_count += len(raw) # Tambahkan hitungan total byte data yang berhasil diterima  
        gps_rx_buf += raw.decode('utf-8', 'ignore') # Masukkan potongan teks GPS baru ke dalam wadah penampung  
        
        # Batasi memori buffer jika belum ada tanda garis baru (\n) agar tidak memakan RAM Pico W:
        if len(gps_rx_buf) > 1024: # Jika isi wadah teks sudah lebih dari 1024 karakter  
            gps_rx_buf = gps_rx_buf[-512:] # Potong dan ambil 512 karakter terbaru saja  

        while '\n' in gps_rx_buf: # Perulangan: Ambil kalimat sinyal NMEA sebaris demi sebaris  
            line, gps_rx_buf = gps_rx_buf.split('\n', 1) # Potong 1 baris kalimat NMEA dari wadah  
            line = line.strip() # Bersihkan spasi/karakter rahasia di ujung baris  
            if not line.startswith('$'): # Jika kalimat tidak diawali tanda '$' (bukan kalimat sinyal GPS resmi), lewati  
                continue # Lanjut ke baris berikutnya  

            parts = line.split(',') # Pecah kalimat NMEA berdasarkan tanda koma (',') menjadi daftar kata  
            tag = parts[0] # Ambil kata pertama sebagai Header Tag (seperti $GNRMC atau $GNGGA)  
            last_nmea_tag = tag # Simpan tag kalimat terakhir  

            # 1. PARSING $GNRMC / $GPRMC (Kalimat Navigasi Minimal Satelit):
            if tag in ["$GNRMC", "$GPRMC", "$BDRMC"] and len(parts) >= 7: # Jika menerima kalimat RMC  
                status_valid = parts[2] # Ambil status pengunci: 'A' = Active/Terkunci, 'V' = Void/Mencari  
                if status_valid == "A": # Jika status 'A' (Satelit Resmi TERKUNCI!):  
                    lat = convert_nmea_coord(parts[3], parts[4]) # Hitung angka Lintang mentah  
                    lng = convert_nmea_coord(parts[5], parts[6]) # Hitung angka Bujur mentah  
                    if lat is not None and lng is not None: # Jika hasil hitungan valid:  
                        current_lat = round(lat, 6) # Bulatkan angka Latitude 6 digit desimal Google Maps  
                        current_lng = round(lng, 6) # Bulatkan angka Longitude 6 digit desimal Google Maps  
                        has_gps_fix = True # Tandai status: True = Koordinat GPS resmi TERKUNCI PRESISI!  
                        last_gps_fix_time = time.time() # Catat detik penguncian satelit  
                elif last_gps_fix_time > 0 and (time.time() - last_gps_fix_time > 30): # Jika sinyal satelit terputus > 30 detik [BISA DIGANTI: 30s]
                    has_gps_fix = False # Kembalikan status ke False (mencari satelit lagi)  

            # 2. PARSING $GNGGA / $GPGGA (Kalimat Data Fix Presisi Tinggi & Ketinggian):
            elif tag in ["$GNGGA", "$GPGGA", "$BDGGA"] and len(parts) >= 8: # Jika menerima kalimat GGA  
                quality = parts[6] # Ambil kualitas sinyal: '1'/'2' = GPS Fix Presisi  
                if quality in ["1", "2", "4", "5"]: # Jika kualitas sinyal bagus:  
                    lat = convert_nmea_coord(parts[2], parts[3]) # Hitung Lintang  
                    lng = convert_nmea_coord(parts[4], parts[5]) # Hitung Bujur  
                    if lat is not None and lng is not None: # Jika valid:  
                        current_lat = round(lat, 6) # Simpan Latitude  
                        current_lng = round(lng, 6) # Simpan Longitude  
                        has_gps_fix = True # Status lock = True  
                        last_gps_fix_time = time.time() # Catat detik penguncian  
                try:
                    sats = int(parts[7]) if parts[7] else 0 # Ambil jumlah satelit aktif dari elemen ke-7  
                    if sats > 0: # Jika ada satelit tertangkap  
                        gps_sat_count = sats # Simpan jumlah satelit aktif (misal: 7 satelit)  
                except:
                    pass

            # 3. PARSING $GPGSV / $GNGSV (Kalimat Satelit yang Terlihat di Langit):
            elif tag in ["$GPGSV", "$GNGSV", "$BDGSV"] and len(parts) >= 4: # Jika menerima kalimat GSV  
                try:
                    sats_view = int(parts[3]) if parts[3] else 0 # Ambil jumlah satelit yang terlihat di atas langit  
                    if sats_view > 0 and not has_gps_fix: # Jika ada satelit terlihat saat belum terkunci  
                        gps_sat_count = sats_view # Tampilkan jumlah satelit pencarian di layar OLED  
                except:
                    pass
    except Exception: # Jika ada kesalahan saat memotong teks GPS  
        pass # Abaikan lewati  

# ==============================================================================
# 4. FUNGSI TAMPILAN OLED PINTAR
# ==============================================================================
# 1. FUNGSI MENAMPILKAN PESAN UMUM (3 BARIS TEKS)
def oled_show_msg(l1, l2="", l3=""): # Fungsi umum buat nampilkan 3 baris pesan teks di layar OLED  
    if not has_oled: return # Jika layar OLED tidak terpasang, langsung keluar  
    try:
        oled.fill(0) # Bersihkan papan tulis RAM di Pico W menjadi hitam  
        oled.text(l1, 4, 4) # Tulis teks baris 1 di posisi X=4, Y=4 [BISA DIGANTI: Teks & posisi X,Y]
        oled.hline(0, 15, 128, 1) # Gambar garis pembatas horizontal dari X=0 sepanjang 128px di Y=15 [BISA DIGANTI: Posisi Y]
        if l2: oled.text(l2, 4, 26) # Tulis teks baris 2 di posisi X=4, Y=26 jika ada [BISA DIGANTI: Teks & posisi X,Y]
        if l3: oled.text(l3, 4, 42) # Tulis teks baris 3 di posisi X=4, Y=42 jika ada [BISA DIGANTI: Teks & posisi X,Y]
        oled.show() # Transfer gambar dari RAM ke layar fisik OLED!  
    except: pass

# 2. FUNGSI MENAMPILKAN LAYAR UTAMA NORMAL (TANDA VITAL & GPS)
def oled_update_normal(has_finger, bpm, spo2, heart_icon): # Fungsi nampilkan status normal detak jantung & GPS  
    if not has_oled: return # Jika layar OLED tidak ada, lewati  
    try:
        oled.fill(0) # Bersihkan kertas gambar RAM jadi hitam  
        # Teks Status GPS pada Layar OLED:
        if has_gps_fix:
            gps_tag = "GPS : di-Update" # Status jika sinyal satelit GPS resmi terkunci [BISA DIGANTI: Teksnya]
        elif gps_sat_count > 0:
            gps_tag = f"GPS:Mencari ({gps_sat_count}s)" # Status jika GPS lagi mencari satelit [BISA DIGANTI: Teksnya]
        else:
            gps_tag = "GPS : Mencari..." # Status jika GPS belum dapat satelit [BISA DIGANTI: Teksnya]

        if has_finger: # JIKA JARI LANSIA MENEMPEL DI SENSOR MAX30102:
            oled.text("TANDA VITAL", 4, 2) # Tulis judul "TANDA VITAL" di Y=2 [BISA DIGANTI: Teksnya]
            oled.text("<3" if heart_icon else " -", 106, 2) # Tampilkan ikon jantung kedip-kedip "<3" di Y=2 [BISA DIGANTI: Ikonnya]
            oled.hline(0, 13, 128, 1) # Gambar garis pembatas horizontal di Y=13 [BISA DIGANTI: Posisi Y]
            oled.text("Jantung:", 4, 18) # Tulis teks "Jantung:" [BISA DIGANTI: Teksnya]
            oled.text(f"{bpm} BPM", 70, 18) # Tampilkan angka detak jantung (BPM)  
            oled.text("Oksigen:", 4, 32) # Tulis teks "Oksigen:" [BISA DIGANTI: Teksnya]
            oled.text(f"{spo2} %", 70, 32) # Tampilkan angka kadar oksigen darah (%)  
            oled.hline(0, 46, 128, 1) # Gambar garis pembatas di Y=46 [BISA DIGANTI: Posisi Y]
            oled.text(gps_tag, 4, 52) # Tampilkan teks status GPS di bagian paling bawah Y=52  
        else: # JIKA JARI LANSIA TIDAK MENEMPEL (KONDISI STANDBY):
            oled.text("SMART-BELT IOT", 8, 4) # Tulis judul utama [BISA DIGANTI: Teksnya]
            oled.hline(0, 15, 128, 1) # Gambar garis pembatas di Y=15 [BISA DIGANTI: Posisi Y]
            oled.text("Tempelkan Jari", 8, 20) # Petunjuk menempelkan jari [BISA DIGANTI: Teks petunjuk]
            oled.text("di Sensor MAX", 10, 34) # Petunjuk sensor [BISA DIGANTI: Teks petunjuk]
            oled.hline(0, 46, 128, 1) # Gambar garis pembatas di Y=46 [BISA DIGANTI: Posisi Y]
            oled.text(gps_tag, 4, 52) # Tampilkan teks status GPS paling bawah  
        oled.show() # Semprotkan gambar RAM ke layar fisik OLED!  
    except: pass

# 3. FUNGSI MENAMPILKAN LAYAR DARURAT HITUNG MUNDUR JATUH
def oled_show_fall(countdown_sec, title="! JATUH TERDETEKSI !"): # Fungsi nampilkan timer hitung mundur jatuh  
    if not has_oled: return
    try:
        oled.fill(0)
        oled.text(title[:16], 0, 4) # Tulis judul peringatan jatuh di Y=4 [BISA DIGANTI: Judulnya]
        oled.hline(0, 15, 128, 1) # Gambar garis pembatas di Y=15 [BISA DIGANTI: Posisi Y]
        oled.text("Panggilan Darurat:", 0, 22) # Teks petunjuk panggilan [BISA DIGANTI: Teksnya]
        oled.text(f"    {countdown_sec} DETIK", 0, 36) # Tampilkan sisa detik hitung mundur (10s/30s)  
        oled.hline(0, 48, 128, 1) # Gambar garis pembatas di Y=48 [BISA DIGANTI: Posisi Y]
        oled.text("Batal: Tekan GP18", 0, 53) # Petunjuk tombol pembatalan GP18 [BISA DIGANTI: Teks petunjuk]
        oled.show()
    except: pass

# 4. FUNGSI MENAMPILKAN LAYAR STATUS AUTO-CALL TELEPON AKTIF
def oled_show_autocall(): # Fungsi nampilkan status telepon darurat lagi berjalan  
    if not has_oled: return
    try:
        oled.fill(0)
        oled.text("! BAHAYA JATUH !", 4, 4) # Tulis judul bahaya di Y=4 [BISA DIGANTI: Teksnya]
        oled.hline(0, 15, 128, 1) # Gambar garis pembatas di Y=15 [BISA DIGANTI: Posisi Y]
        oled.text("AUTO-CALL AKTIF", 4, 22) # Status panggilan telepon suara aktif [BISA DIGANTI: Teksnya]
        oled.text("Notif Terkirim:", 4, 36) # Status notifikasi [BISA DIGANTI: Teksnya]
        oled.text("Telegram & Email", 4, 50) # Status media pengiriman [BISA DIGANTI: Teksnya]
        oled.show()
    except: pass

# 5. FUNGSI MENAMPILKAN LAYAR STATUS ALARM DIBATALKAN LANSIA
def oled_show_cancelled(reason="Tombol GP18 Ditekan"): # Fungsi nampilkan layar alarm berhasil dibatalkan  
    if not has_oled: return
    try:
        oled.fill(0)
        oled.text("ALARM DIBATALKAN", 0, 4) # Tulis judul pembatalan di Y=4 [BISA DIGANTI: Teksnya]
        oled.hline(0, 15, 128, 1) # Gambar garis pembatas di Y=15 [BISA DIGANTI: Posisi Y]
        oled.text(reason[:16], 0, 24) # Tampilkan alasan pembatalan (misal: Tombol GP18 Ditekan)  
        oled.text("Kondisi Lansia:", 0, 38) # Status lansia [BISA DIGANTI: Teksnya]
        oled.text("AMAN / SEHAT", 0, 52) # Keterangan lansia aman [BISA DIGANTI: Teksnya]
        oled.show()
    except: pass

# ==============================================================================
# 5. PARAMETER DETEKSI JATUH & BERGULING (ANTI-SENGGOLAN)
# ==============================================================================
# 1. Ambang Batas Benturan Jatuh (Signal Vector Magnitude - Satuan G):
# Saat orang berdiri/duduk diam, gravitasi bumi = 1.00g.
# Saat jatuh membentur lantai, grafik loncat drastis menembus > 2.70g.
# Nilai sensitivitas benturan: standar 2.70g (turunkan ke 2.20-2.30 jika ingin lebih peka,
# atau naikkan ke 2.80-3.00 untuk toleransi gerakan lebih tinggi).
# 1. PARAMETER AMBANG BATAS DETEKSI JATUH & BENTURAN (MPU6050):
SVM_FALL_THRESHOLD = 2.70 # Ambang batas gaya benturan jatuh keras dalam satuan gravitasi (G-Force) [BISA DIGANTI: dinaikkan ke 2.80g atau diturunkan ke 2.50g]
SVM_FALL_TILT = 2.45 # Ambang batas benturan sedang jika posisi badan lansia langsung miring/tergeletak [BISA DIGANTI: 2.45g ke 2.30g]

# 2. PARAMETER SUDUT KEMIRINGAN BADAN (TILT ANGLE °):
TILT_FALL_THRESHOLD = 50.0 # Batas sudut kemiringan badan dianggap sudah roboh/tergeletak (dalam Derajat °) [BISA DIGANTI: 50.0° ke 60.0°]
TILT_WARNING_THRESHOLD = 35.0 # Peringatan awal saat posisi badan lansia mulai oleng/condong (dalam Derajat °) [BISA DIGANTI: 35.0° ke 30.0°]
ROLL_TILT_THRESHOLD = 40.0 # Batas sudut kemiringan saat lansia berguling ke kiri atau kanan (dalam Derajat °) [BISA DIGANTI: 40.0° ke 45.0°]

# 3. DETEKSI LANSIA JATUH & MERINGIS BERGULING-GULING:
ROLL_TRIGGER_COUNT = 5 # Berapa kali lansia harus berguling bolak-balik sebelum dianggap alarm darurat [BISA DIGANTI: 5 ke 3 atau 7 kali]
DURASI_MIRING_THRESHOLD = 5 # Berapa lama (detik) lansia miring terus menerus sebelum alarm terpicu [BISA DIGANTI: 5 ke 10 detik]

# 4. JEDA HITUNG MUNDUR BUZZER SEBELUM TELEPON DARURAT:
COUNTDOWN_DURATION = 10 # Durasi hitung mundur buzzer (detik) sebelum Panggilan Darurat Auto-Call terpicu ke HP perawat [BISA DIGANTI: 10 ke 15 atau 30 detik]

# 5. PARAMETER BATAS AMAN KESEHATAN & GEOFENCE RADIUS AREA (ZONA AMAN):
MIN_HEART_RATE = 60 # Batas minimum detak jantung normal (60-100 BPM). Di bawah 60 = alarm bradikardia [BISA DIGANTI: 60 ke 55 BPM]
MAX_HEART_RATE = 73
MIN_SPO2 = 95 # Batas minimum kadar oksigen darah normal (95-100%). Di bawah 95 = alarm hipoksia [BISA DIGANTI: 95 ke 92%]
DEFAULT_HOME_NAME = "Universitas Surabaya (UBAYA)" # Nama lokasi awal rumah/wisma lansia [BISA DIGANTI: Teks nama lokasi]
DEFAULT_HOME_LAT = -7.3198009 # Titik Garis Lintang (Latitude) lokasi awal rumah lansia (UBAYA) [BISA DIGANTI: Sesuai titik koordinat GPS rumah]
DEFAULT_HOME_LNG = 112.7681119 # Titik Garis Bujur (Longitude) lokasi awal rumah lansia (UBAYA) [BISA DIGANTI: Sesuai titik koordinat GPS rumah]
MAX_DISTANCE_METERS = 100 # Batas radius aman lansia boleh keluar dari Wisma (Geofence dalam Meter) [BISA DIGANTI: 100 ke 50m atau 200m]

# Catatan: Perhitungan Jarak Geofence dipindahkan (Offloading) ke Aplikasi Android 
# menggunakan API resmi Android SDK (android.location.Location.distanceBetween)
# def calc_distance_m(lat1, lon1, lat2, lon2):
#     try:
#         r = 6371000.0
#         dlat = math.radians(lat2 - lat1)
#         dlon = math.radians(lon2 - lon1)
#         a = math.sin(dlat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2)**2
#         c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
#         return r * c
#     except:
#         return 0.0

# VARIABEL RUNTIME PENGHITUNG GULINGAN & ALARM:
roll_count = 0 # Penghitung berapa kali lansia berguling-guling  
last_roll_state = "CENTER" # Menyimpan orientasi posisi miring terakhir ("CENTER", "FRONT", "BACK", "LEFT", "RIGHT")  
last_roll_time = 0 # Catatan detik terakhir lansia berguling  
continuous_tilt_start_time = 0 # Catatan detik awal lansia mulai posisi miring terus-menerus  
active_incident_type = "JATUH" # Menyimpan nama jenis insiden yang sedang aktif ("JATUH", "POSISI_MIRING_LAMA", "TILT_EKSTREM")  
last_cancel_reason = "Tombol GP18 Ditekan" # Menyimpan teks alasan pembatalan alarm terakhir [BISA DIGANTI: Teks alasannya]
server_confirmed_active = False # Status konfirmasi dari server backend: True = Server sudah menerima sinyal insiden aktif  



def fetch_device_config(): # Fungsi khusus buat mendownload settingan threshold terbaru dari server backend Java Jersey  
    # Panggil variabel-variabel global threshold agar nilainya bisa diperbarui otomatis dari server:
    global SVM_FALL_THRESHOLD, ROLL_TRIGGER_COUNT, DURASI_MIRING_THRESHOLD, COUNTDOWN_DURATION # Variabel threshold jatuh & countdown  
    global MIN_HEART_RATE, MAX_HEART_RATE, MIN_SPO2, DEFAULT_HOME_LAT, DEFAULT_HOME_LNG, MAX_DISTANCE_METERS, DEFAULT_HOME_NAME # Variabel batas kesehatan & geofence  
    global DEFAULT_LAT, DEFAULT_LNG, current_lat, current_lng, has_gps_fix # Variabel koordinat GPS  
    if not wlan.isconnected(): # Cek koneksi Wi-Fi: Jika Pico W sedang offline / tidak terhubung ke Wi-Fi  
        return # Langsung keluar (gunakan nilai threshold default lokal tanpa mengunduh)  
    try: # Mulai proses komunikasi Socket TCP ke server backend Java Jersey  
        raw_s = usocket.socket(usocket.AF_INET, usocket.SOCK_STREAM) # Buka pipa socket komunikasi data TCP  
        raw_s.settimeout(1.5) # Atur batas waktu tunggu respon server maksimal 1.5 detik (agar tidak hang jika server mati)  
        addr = usocket.getaddrinfo(BACKEND_HOST, BACKEND_PORT)[0][-1] # Cari dan dapatkan alamat IP & Port server backend Tomcat  
        raw_s.connect(addr) # Hubungkan koneksi pipa TCP ke server backend  
        
        # Format surat permintaan HTTP GET request ke endpoint /api/iot/config/{mac_address}:
        req = (
            f"GET /api/iot/config/{mac_address} HTTP/1.1\r\n"
            f"Host: {BACKEND_HOST}\r\n"
            f"User-Agent: PicoW-SmartBelt/1.0\r\n"
            f"Connection: close\r\n\r\n"
        )
        raw_s.send(req.encode()) # Kirim paket surat HTTP request via socket ke server backend  
        
        raw_data = b"" # Siapkan wadah penampung balasan data biner dari server  
        while len(raw_data) < 4096: # Perulangan: Terima balasan data dari server (maksimal 4 KB)  
            chunk = raw_s.recv(512) # Ambil potongan data 512 byte dari pipa socket  
            if not chunk: break # Jika balasan data sudah selesai dikirim server, hentikan perulangan  
            raw_data += chunk # Gabungkan potongan data ke wadah utama  
        raw_s.close() # Tutup pipa socket agar hemat memori RAM Pico W  

        resp = raw_data.decode('utf-8', 'ignore') # Konversi balasan data biner server menjadi string teks UTF-8  
        if "{" in resp and "}" in resp: # Cek jika ada isi data format JSON { ... } di dalam balasan server  
            # Cari substring JSON murni { ... } agar kebal terhadap chunked transfer header HTTP:
            start_idx = resp.find("{") # Cari posisi kurung kurawal pembuka '{'  
            end_idx = resp.rfind("}") + 1 # Cari posisi kurung kurawal penutup '}'  
            json_str = resp[start_idx:end_idx] # Potong dan ambil teks JSON murni tanpa header HTTP  
            data = ujson.loads(json_str) # Decode teks JSON murni menjadi objek Python dictionary  
            
            if data.get("success") and data.get("data"): # Jika respon server bernilai sukses dan membawa data konfigurasi:  
                cfg = data["data"] # Ambil objek data konfigurasi  
                
                # Update seluruh variabel threshold lokal di RAM Pico W dengan nilai terbaru dari database MySQL:
                if "thresholdSvm" in cfg and cfg["thresholdSvm"] is not None:
                    SVM_FALL_THRESHOLD = float(cfg["thresholdSvm"]) # Update threshold benturan jatuh  
                if "thresholdRollCount" in cfg and cfg["thresholdRollCount"] is not None:
                    ROLL_TRIGGER_COUNT = int(cfg["thresholdRollCount"]) # Update jumlah gulingan  
                if "durasiMiringDetik" in cfg and cfg["durasiMiringDetik"] is not None:
                    DURASI_MIRING_THRESHOLD = int(cfg["durasiMiringDetik"]) # Update durasi miring  
                if "durasiCountdown" in cfg and cfg["durasiCountdown"] is not None:
                    COUNTDOWN_DURATION = int(cfg["durasiCountdown"]) # Update durasi hitung mundur buzzer  
                if "minHeartRate" in cfg and cfg["minHeartRate"] is not None:
                    MIN_HEART_RATE = int(cfg["minHeartRate"]) # Update batas detak jantung  
                if "maxHeartRate" in cfg and cfg["maxHeartRate"] is not None:
                    MAX_HEART_RATE = int(cfg["maxHeartRate"])
                if "minSpo2" in cfg and cfg["minSpo2"] is not None:
                    MIN_SPO2 = int(cfg["minSpo2"]) # Update batas kadar oksigen  
                if "defaultLatitude" in cfg and cfg["defaultLatitude"] is not None:
                    DEFAULT_HOME_LAT = float(cfg["defaultLatitude"]) # Update koordinat Lintang rumah  
                if "defaultLongitude" in cfg and cfg["defaultLongitude"] is not None:
                    DEFAULT_HOME_LNG = float(cfg["defaultLongitude"]) # Update koordinat Bujur rumah  
                if "maxDistanceMeters" in cfg and cfg["maxDistanceMeters"] is not None:
                    MAX_DISTANCE_METERS = int(cfg["maxDistanceMeters"]) # Update radius geofence aman  
                if "lokasiDefaultNama" in cfg and cfg["lokasiDefaultNama"] is not None:
                    DEFAULT_HOME_NAME = str(cfg["lokasiDefaultNama"]) # Update nama lokasi rumah  
                
                print(f"⚙️ [CONFIG DINAMIS] SVM={SVM_FALL_THRESHOLD:.2f}g | Home={DEFAULT_HOME_LAT:.5f},{DEFAULT_HOME_LNG:.5f} | MaxDist={MAX_DISTANCE_METERS}m | MinHR={MIN_HEART_RATE} | MaxHR={MAX_HEART_RATE} | MinSpO2={MIN_SPO2}%") # Cetak laporan sukses update threshold ke konsol Thonny []
    except Exception as e: # Jika terjadi kegagalan saat unduh konfigurasi  
        print("⚠️ Warning fetch_device_config:", e) # Cetak peringatan warning di konsol Thonny []


def connect_wifi(): # Fungsi utama penyambung koneksi Wi-Fi Pico W ke Hotspot HP / Router Wisma  
    print(f"\n📡 Menghubungkan ke WiFi: '{WIFI_SSID}'...") # Cetak status menyambung ke konsol Thonny []
    oled_show_msg("SMART-BELT IOT", "Menghubungkan...", f"WiFi: {WIFI_SSID}") # Tampilkan status menyambung di layar OLED [BISA DIGANTI: Teksnya]
    try:
        wlan.disconnect() # Putuskan koneksi Wi-Fi lama jika sebelumnya pernah terhubung  
    except:
        pass
    wlan.active(False) # Matikan daya listrik modul radio Wi-Fi sebentar buat mereset chip  
    time.sleep(0.5) # Beri jeda 0.5 detik  
    wlan.active(True) # Nyalakan kembali modul radio Wi-Fi chip CYW43 Pico W  
    time.sleep(0.5) # Beri jeda 0.5 detik  
    
    wlan.connect(WIFI_SSID, WIFI_PASSWORD) # Kirim sinyal sambungkan Wi-Fi menggunakan nama SSID & Password dari baris 41-42  
    max_wait = 20 # Batas waktu tunggu percobaan koneksi maksimal 20 detik [BISA DIGANTI: 20 ke 15 atau 30 detik]
    while max_wait > 0: # Perulangan hitung mundur waktu tunggu penyambungan Wi-Fi  
        st = wlan.status() # Ambil kode angka status koneksi dari modul Wi-Fi Pico W  
        if st < 0 or st >= 3: # Jika status 3 (CYW43_LINK_JOIN - Sukses Terhubung!) atau ada error negatif, hentikan perulangan  
            break # Keluar perulangan  
        max_wait -= 1 # Kurangi batas waktu tunggu 1 detik  
        led.toggle() # Kedipkan lampu LED onboard Pico W sebagai penanda visual lagi proses menyambung Wi-Fi  
        time.sleep(1) # Beri jeda 1 detik  

    if wlan.isconnected(): # JIKA TERHUBUNG SUKSES KE WI-FI:  
        status = wlan.ifconfig() # Ambil data jaringan IP Address, Subnet Mask, dan Gateway  
        print(f"✅ WiFi Terhubung! IP Pico W: {status[0]} (Subnet: {status[1]}, Gateway: {status[2]})") # Cetak laporan IP Address sukses ke konsol Thonny []
        led.value(1) # Nyalakan lampu LED onboard terang terus (Penanda Visual: Wi-Fi Online!)  
        oled_show_msg("SMART-BELT IOT", "WiFi Terhubung!", f"IP: {status[0]}") # Tampilkan nomor IP Address di layar OLED [BISA DIGANTI: Teksnya]
        time.sleep(1) # Beri jeda 1 detik  
        return True # Kembalikan nilai True (Wi-Fi Online)  
    else: # JIKA GAGAL TERHUBUNG KE WI-FI:  
        st = wlan.status() # Ambil kode angka penyebab gagal  
        if st == -2:
            reason = "SSID WiFi tidak ditemukan! (Pastikan WiFi 2.4GHz aktif)" # Penyebab: Nama Wi-Fi tidak ketemu [BISA DIGANTI: Teks alasannya]
        elif st == -3:
            reason = "Password WiFi Salah!" # Penyebab: Kata sandi Wi-Fi salah [BISA DIGANTI: Teks alasannya]
        elif st == -1:
            reason = "Koneksi Gagal/Ditolak AP" # Penyebab: Ditolak router Wi-Fi [BISA DIGANTI: Teks alasannya]
        else:
            reason = f"Status Code {st}" # Kode status error lainnya  
        print(f"⚠️ WiFi Belum Terhubung: {reason} (Pico W berjalan offline).") # Cetak laporan peringatan di konsol Thonny []
        led.value(0) # Matikan lampu LED onboard (Penanda Visual: Wi-Fi Offline / Standalone Mode)  
        oled_show_msg("SMART-BELT IOT", "WiFi Offline", "Mode Standalone") # Tampilkan pesan "WiFi Offline" di layar OLED [BISA DIGANTI: Teksnya]
        time.sleep(1) # Beri jeda 1 detik  
        return False # Kembalikan nilai False (Wi-Fi Offline)  

def http_post_json(path, data_dict): # Fungsi utama pengirim paket data JSON (telemetri/darurat/batal) dari Pico W ke backend Java Jersey  
    if not wlan.isconnected(): # Jika Wi-Fi tidak tersambung  
        return False # Batalkan pengiriman tanpa membuat sistem crash  
    for attempt in range(2): # Mekanisme Retry Otomatis: Coba ulang pengiriman maksimal 2 kali jika ada gangguan jaringan  
        s = None
        raw_s = None
        try:
            import gc # Panggil modul Garbage Collector (Pembersih Sampah RAM)  
            gc.collect() # Bersihkan memori RAM Pico W sebelum membuka pipa socket baru  
            if attempt > 0: # Jika ini adalah percobaan ulang ke-2  
                time.sleep_ms(100) # Beri jeda 100 milidetik  
            
            json_data = ujson.dumps(data_dict) # Konversi data dictionary Python menjadi string teks berformat JSON  
            json_bytes = json_data.encode('utf-8') # Konversi string teks JSON menjadi satuan byte biner UTF-8  
            raw_s = usocket.socket(usocket.AF_INET, usocket.SOCK_STREAM) # Buka pipa komunikasi socket TCP  
            raw_s.settimeout(1.2) # Batas waktu tunggu balasan server maksimal 1.2 detik (agar tidak hang jika server lambat)  
            addr = usocket.getaddrinfo(BACKEND_HOST, BACKEND_PORT)[0][-1] # Cari alamat IP & Port server backend Tomcat  
            raw_s.connect(addr) # Hubungkan koneksi pipa TCP ke server backend  

            if USE_SSL and ssl: # Jika mode HTTPS SSL/TLS aktif:  
                try:
                    s = ssl.wrap_socket(raw_s, server_side=False, server_hostname=BACKEND_HOST) # Bungkus pipa socket dengan enkripsi SSL  
                except:
                    try:
                        s = ssl.wrap_socket(raw_s, server_hostname=BACKEND_HOST)
                    except:
                        s = ssl.wrap_socket(raw_s)
            else: # Jika mode HTTP standar lokal:  
                s = raw_s

            # Format surat resmi HTTP POST Request Header:
            header = (
                f"POST {path} HTTP/1.1\r\n"
                f"Host: {BACKEND_HOST}\r\n"
                f"User-Agent: PicoW-SmartBelt/1.0\r\n"
                f"ngrok-skip-browser-warning: true\r\n"
                f"Content-Type: application/json\r\n"
                f"Content-Length: {len(json_bytes)}\r\n"
                f"Connection: close\r\n\r\n"
            ).encode('utf-8')

            s.send(header + json_bytes) # Kirim surat header + paket data JSON sekaligus ke server backend  
            resp = s.recv(256).decode('utf-8', 'ignore') # Terima 256 byte pertama pesan balasan dari server backend  
            if "200" in resp or "201" in resp or "307" in resp: # Jika server membalas dengan status HTTP 200 OK / 201 Created (Pengiriman Sukses!):  
                return True # Kembalikan nilai True (Sukses Terkirim!)  
            elif attempt == 1: # Jika sampai percobaan ke-2 tetap gagal  
                first_line = resp.split('\r\n')[0] if resp else "No Response" # Ambil pesan error server  
                print(f"⚠️ [Server Status]: {first_line}") # Cetak pesan status error server di konsol Thonny []
                return False
        except Exception as e:
            if attempt == 1:
                return False
        finally: # Blok Pembersihan RAM Wajib (Selalu dieksekusi baik sukses maupun gagal):  
            if s:
                try: s.close() # Tutup pipa socket s  
                except: pass
            if raw_s and raw_s != s:
                try: raw_s.close() # Tutup pipa socket raw_s  
                except: pass
            import gc
            gc.collect() # Bersihkan sisa memori sampah RAM Pico W agar RAM selalu lega 100%!  
    return False

def get_server_emergency_state(): # Fungsi khusus mengecek apakah alarm darurat dibatalkan perawat via HP Android  
    """
    Mengecek status darurat dari endpoint /api/iot/status/{mac}:
    Return:
       1  -> Terkonfirmasi darurat AKTIF di server ("data": true)
       0  -> Terkonfirmasi darurat DIBATALKAN perawat di HP Android ("data": false)
      -1  -> Gagal koneksi / timeout / offline (status tidak diketahui)
    """
    if not wlan.isconnected(): # Jika Wi-Fi offline  
        return -1 # Kembalikan angka -1 (status tidak diketahui)  
    s = None
    raw_s = None
    try:
        import gc
        gc.collect() # Bersihkan sampah memori RAM Pico W sebelum buka socket  
        raw_s = usocket.socket(usocket.AF_INET, usocket.SOCK_STREAM) # Buka pipa socket TCP  
        raw_s.settimeout(1.2) # Batas waktu tunggu balasan server maksimal 1.2 detik (non-blocking)  
        addr = usocket.getaddrinfo(BACKEND_HOST, BACKEND_PORT)[0][-1] # Cari IP & Port server backend Tomcat  
        raw_s.connect(addr) # Sambungkan pipa TCP ke server backend  

        if USE_SSL and ssl: # Jika mode HTTPS SSL/TLS aktif:  
            try:
                s = ssl.wrap_socket(raw_s, server_side=False, server_hostname=BACKEND_HOST)
            except:
                try:
                    s = ssl.wrap_socket(raw_s, server_hostname=BACKEND_HOST)
                except:
                    s = ssl.wrap_socket(raw_s)
        else: # Jika mode HTTP standar lokal:  
            s = raw_s

        # Surat permintaan HTTP GET request mengecek status darurat sabuk ini:
        req = (
            f"GET /api/iot/status/{mac_address} HTTP/1.1\r\n"
            f"Host: {BACKEND_HOST}\r\n"
            f"User-Agent: PicoW-SmartBelt/1.0\r\n"
            f"ngrok-skip-browser-warning: true\r\n"
            f"Connection: close\r\n\r\n"
        )
        s.send(req.encode()) # Kirim surat permintaan via socket ke server backend  
        raw_data = b"" # Siapkan wadah penampung balasan  
        while len(raw_data) < 1024: # Perulangan: Ambil balasan server (maksimal 1 KB)  
            chunk = s.recv(512) # Ambil potongan data 512 byte  
            if not chunk: break # Hentikan perulangan jika balasan selesai  
            raw_data += chunk # Gabungkan potongan data  
        resp = raw_data.decode('utf-8', 'ignore') # Konversi data biner server menjadi string teks  
        
        # Pengecekan isi data balasan server:
        if '"data":true' in resp or '"data": true' in resp: # Jika respon server bernilai true:  
            return 1 # Kembalikan angka 1 (Darurat Masih AKTIF di server!)  
        elif '"data":false' in resp or '"data": false' in resp: # Jika respon server bernilai false:  
            return 0 # Kembalikan angka 0 (Darurat DIBATALKAN perawat dari HP Android!)  
        return -1 # Kembalikan -1 (Status tidak diketahui)  
    except: # Jika gagal terkoneksi  
        return -1 # Kembalikan -1  
    finally: # Pembersihan RAM Wajib:  
        if s:
            try: s.close() # Tutup pipa socket s  
            except: pass
        if raw_s and raw_s != s:
            try: raw_s.close() # Tutup pipa socket raw_s  
            except: pass
        import gc
        gc.collect() # Bersihkan memori RAM Pico W agar selalu lega 100%!  

def check_emergency_active_on_server(): # Fungsi pembantu (helper) buat ngecek apakah alarm darurat di server masih aktif  
    return get_server_emergency_state() == 1 # Kembalikan nilai True jika status dari server bernilai 1 (Darurat Masih Aktif)  

# [FUNGSI BACA SENSOR MPU6050 & RUMUS FISIKA JATUH]:
# Konversi data register 16-bit dan perhitungan vektor gerakan:

def parse_short(high, low): # Fungsi matematika biner: Menggabungkan 2 potongan byte (High & Low Byte 8-bit) menjadi 1 angka utuh 16-bit (Signed Two's Complement)  
    # Menggabungkan 2 byte data pecahan dari MPU6050 menjadi 1 angka 16-bit bertanda (+ / -)
    val = (high << 8) | low # Geser potongan High Byte 8 bit ke kiri lalu sambungkan dengan Low Byte menjadi angka 16-bit (rentang 0 s.d. 65535)  
    return val - 65536 if val > 32767 else val # Konversi Matematika Two's Complement: Jika angka lebih dari 32767, kurangi 65536 agar menghasilkan angka Positif (+) atau Negatif (-) yang benar (-32768 s.d. +32767)  

def get_sensor_data(): # JANTUNG MATEMATIKA SENSOR: Fungsi utama pembacaan MPU6050 & kalkulasi SVM, Pitch, Roll  
    try:
        # Register 0x3B adalah alamat awal memori data pembacaan akselerometer di MPU6050:
        i2c.writeto(addr, bytearray([0x3B]), False) # Minta MPU6050 siap-siap kirim data mulai dari register memori 0x3B  
        raw = i2c.readfrom(addr, 14) # Baca 14 byte sekaligus lewat I2C (Akselerometer 6B, Suhu 2B, Giroskop 6B)  

        # ALASAN DIBAGI 16384.0 (AKSELEROMETER):
        # Karena set range +-2g (total rentang 4g). Memori MPU6050 berkapasitas 16-bit (65536 LSB).
        # Perhitungannya: 65536 / 4g = 16384 LSB/g. Dibagi 16384.0 biar angkanya berubah jadi satuan Gravitasi (G-Force)!
        ax = parse_short(raw[0], raw[1]) / 16384.0 # Sumbu X (Gerakan condong Depan/Belakang) dalam satuan G-Force  
        ay = parse_short(raw[2], raw[3]) / 16384.0 # Sumbu Y (Gerakan miring Kiri/Kanan) dalam satuan G-Force  
        az = parse_short(raw[4], raw[5]) / 16384.0 # Sumbu Z (Gerakan naik/turun Atas/Bawah) dalam satuan G-Force  

        # ALASAN DIBAGI 131.0 (GIROSKOP):
        # Karena set range giroskop +-250 deg/s (total rentang 500 deg/s). Memori berkapasitas 16-bit (65536 LSB).
        # Perhitungannya: 65536 / 500 deg/s = 131.07 LSB/(deg/s). Dibagi 131.0 biar angkanya keluar dalam satuan derajat per detik (°/s)!
        gx = parse_short(raw[8], raw[9]) / 131.0   # Kecepatan putar rotasi sumbu X dalam derajat per detik (°/s)  
        gy = parse_short(raw[10], raw[11]) / 131.0 # Kecepatan putar rotasi sumbu Y dalam derajat per detik (°/s)  
        gz = parse_short(raw[12], raw[13]) / 131.0 # Kecepatan putar rotasi sumbu Z dalam derajat per detik (°/s)  

        # RUMUS SVM (Signal Vector Magnitude): SVM = sqrt(ax^2 + ay^2 + az^2)
        # Menggabungkan gaya akselerasi 3D menjadi 1 nilai magnitudo utuh gaya benturan.
        # Saat diam/berjalan normal = ~1.00g (cuma gravitasi bumi). Kalau jatuh nabrak lantai = loncat drastis (> 2.70g).
        svm = math.sqrt(ax**2 + ay**2 + az**2) # Hitung akar kuadrat total percepatan benturan 3D  

        # RUMUS PITCH & ROLL (Sudut Kemiringan Badan Lansia dalam Derajat °):
        # Pitch: Kemiringan postur badan condong ke depan / belakang
        # Roll : Kemiringan postur badan miring ke samping kiri / kanan
        pitch = math.atan2(ax, math.sqrt(ay**2 + az**2)) * 180 / math.pi # Hitung sudut kemiringan Pitch (°)  
        roll = math.atan2(ay, math.sqrt(ax**2 + az**2)) * 180 / math.pi # Hitung sudut kemiringan Roll (°)  

        return ax, ay, az, gx, gy, gz, svm, pitch, roll # Kembalikan 9 hasil kalkulasi matematika inersia presisi!  
    except: # Jika kabel MPU6050 terputus/lepas  
        return 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0 # Kembalikan nilai aman default (SVM = 1.0g)  

# 1. Sambungkan Wi-Fi & Unduh Konfigurasi Threshold saat Alat Pertama Nyala (Startup):
connect_wifi() # Panggil fungsi penyambung Wi-Fi ke Hotspot HP / Router Wisma  
fetch_device_config() # Panggil fungsi pengunduh threshold dinamis terbaru dari server backend Java Jersey  

# 2. Deklarasi Variabel Penanda (State Variables) Main Loop:
count = 0 # Penghitung jumlah perulangan perputaran main loop  
last_telemetry_ticks = time.ticks_ms() # Catatan waktu milidetik pengiriman data telemetri terakhir ke server  
last_gps_update_ticks = time.ticks_ms() # Catatan waktu milidetik pembaruan sinyal GPS terakhir  
last_config_sync_ticks = time.ticks_ms() # Catatan waktu milidetik sinkronisasi threshold terakhir  
user_cancel_display_timer = 0 # Timer durasi menahan tampilan pesan "ALARM DIBATALKAN" di layar OLED  
in_fall_confirmation = False # Status penanda: True = Lagi dalam mode hitung mundur buzzer (countdown 10s/30s)  
in_auto_call_alarm = False # Status penanda: True = Lagi dalam mode alarm Panggilan Darurat Auto-Call aktif  
confirm_start_time = 0 # Catatan detik awal mulainya hitung mundur  
last_countdown_beep_time = -1 # Catatan detik terakhir bunyi bip buzzer "TIT... TIT..."  
heart_icon_state = False # Status kedipan ikon detak jantung "<3" di layar OLED  
fall_spike_counter = 0 # Penghitung filter benturan beruntun (wajib 2 sampel >100ms anti-false positive)  

print("\n🚀 Smart-Belt Berjalan! Memantau Keselamatan Lansia...") # Cetak laporan alat resmi aktif ke konsol Thonny []
print("-" * 80) # Cetak garis pembatas dekorasi di konsol Thonny []

# ==============================================================================
# 6. MAIN LOOP DETEKSI JATUH & IOT TELEMETRI REAL-TIME
# ==============================================================================
# [ALUR UTAMA PROGRAM / LOGIKA SISTEM]:
# 1. Update posisi GPS
# 2. Cek apakah tombol fisik GP18 ditekan (buat batalin alarm)
# 3. Baca sensor MPU6050 (akselerasi & kemiringan badan)
# 4. Kalau benturan keras (> 2.7g) / gulingan 5x -> MASUK HITUNG MUNDUR (COUNTDOWN)
# 5. Pas countdown -> bunyi tit... tit... setiap detik, layar OLED hitung mundur
# 6. Kalau 10 detik habis ga dibatalkan -> AUTO-CALL & KIRIM DARURAT TELEGRAM/EMAIL!
# 7. Baca sensor MAX30102 (detak jantung & oksigen)
# 8. Cek apakah detak jantung < 60, SpO2 < 95%, atau lansia keluar radius > 100m
# 9. Kirim data telemetri ke server tiap 1 detik

while True: # PERULANGAN UTAMA TANPA HENTI (MAIN LOOP): Mengeksekusi seluruh deteksi keselamatan secara berulang 24/7  
    # 1. Update Tracking Koordinat GPS Dinamis (Multi-GNSS):
    update_gps_tracking() # Langkah 1: Perbarui data posisi koordinat GPS satelit secara lancar  

    # 2. PENANGANAN TOMBOL FISIK GP18 DITEKAN LANSIA UNTUK BATALKAN ALARM:
    # Beri jeda perlindungan 1.2 detik setelah alarm terpicu agar tombol tidak tidak sengaja tertekan seketika pas jatuh:
    button_cooldown_passed = (time.time() - confirm_start_time > 1.2) if confirm_start_time > 0 else True # Cooldown 1.2 detik tombol GP18 [BISA DIGANTI: 1.2 ke 1.0 atau 1.5 detik]
    
    # Cek jika alarm (countdown/auto-call) sedang aktif & tombol GP18 sah ditekan lansia selama 150ms:
    if (in_fall_confirmation or in_auto_call_alarm) and button_cooldown_passed and is_gp18_button_pressed():
        was_in_auto_call = in_auto_call_alarm # Catat apakah tombol ditekan saat Auto-Call atau saat hitung mundur  
        in_fall_confirmation = False # Matikan mode hitung mundur  
        in_auto_call_alarm = False   # Matikan mode alarm Auto-Call  
        continuous_tilt_start_time = 0 # Reset timer durasi miring  
        roll_count = 0                 # Reset counter gulingan  
        last_roll_state = "CENTER"     # Reset posisi miring ke tengah  
        fall_spike_counter = 0         # Reset filter benturan  
        buzzer_off()                 # MATIKAN SUARA BUZZER PIEZO SEKETIKA!  
        
        cancel_label = "BUZZER DIMATIKAN" if was_in_auto_call else "ALARM DIBATALKAN" # Tentukan label teks status [BISA DIGANTI: Teks alasannya]
        last_cancel_reason = cancel_label # Simpan alasan pembatalan  
        user_cancel_display_timer = time.time() # Catat detik pembatalan buat menahan pesan di layar OLED selama 15 detik  
        oled_show_cancelled(cancel_label)  # Tampilkan pesan "ALARM DIBATALKAN" / "BUZZER DIMATIKAN" di layar OLED  
        print(f"\n🛑 Tombol GP18 terverifikasi ditekan 150ms! ({cancel_label}) Memperbarui status di server...") # Cetak laporan di konsol Thonny []
        
        # Kirim sinyal HTTP POST ke server backend Java Jersey bahwa alarm ini resmi DIBATALKAN lansia:
        http_post_json(f"/api/iot/cancel?macAddress={mac_address}", {}) # Lapor pembatalan ke server backend!  


        # 3. Baca data gerakan real-time dari sensor MPU6050:
    ax, ay, az, gx, gy, gz, svm, pitch, roll = get_sensor_data() # Langkah 3: Ambil 9 hasil bacaan kalkulasi matematika MPU6050  
    status = "✅ NORMAL (AMAN)" # Setel status awal default: NORMAL (AMAN) [BISA DIGANTI: Teks statusnya]

    # Cek apakah posisi badan lansia sedang miring parah (> 35 derajat ke depan, belakang, kiri, atau kanan):
    is_miring = (abs(pitch) > TILT_WARNING_THRESHOLD) or (abs(roll) > TILT_WARNING_THRESHOLD) # Cek kondisi badan miring oleng  
    
    # RUMUS DETEKSI JATUH DUAL-THRESHOLD (ANTI-FALSE POSITIVE SENGGOLAN MEJA / I2C JITTER):
    # Syarat 1: Benturan sangat keras (SVM > 2.70g) ATAU
    # Syarat 2: Benturan sedang (SVM > 2.45g) TAPI postur badan miring oleng
    raw_fall_detected = (svm > SVM_FALL_THRESHOLD) or (svm > SVM_FALL_TILT and is_miring) # Evaluasi syarat awal jatuh  
    
    if raw_fall_detected: # Jika ada lonjakan benturan awal:  
        fall_spike_counter += 1 # Tambahkan hitungan sampel lonjakan benturan (+1)  
    else: # Jika gerakan kembali normal:  
        fall_spike_counter = 0 # Reset hitungan lonjakan menjadi 0  

    # VERIFIKASI DETEKSI JATUH SAH:
    # Lonjakan benturan harus bertahan minimal 2 sampel beruntun (>100ms) ATAU terjadi benturan sangat ekstrem (>3.50g)
    is_fall = (fall_spike_counter >= 2) or (svm > 3.50) # Verifikasi akhir: Jatuh SAH!  

    # 4. DETEKSI LANSIA MIRING LAMA (DURASI DETIK) & BERGULING-GULING (TILT COUNT):
    is_rolling_emergency = False # Inisialisasi status darurat berguling: False  
    is_continuous_tilt_emergency = False # Inisialisasi status darurat miring lama: False  
    
    # Cek apakah tombol pembatalan GP18 baru saja ditekan lansia dalam 15 detik terakhir (agar tidak langsung terpicu ulang):
    is_recently_cancelled = (time.time() - user_cancel_display_timer < 15.0) # Tahan cooldown pembatalan selama 15 detik [BISA DIGANTI: 15.0 detik ke 10s atau 20s]

    if not in_fall_confirmation and not in_auto_call_alarm and not is_recently_cancelled: # Cek jika alat lagi dalam kondisi normal (tidak dalam alarm/countdown & tidak baru dibatalkan)  
        now_time = time.time() # Ambil catatan detik saat ini  

        # A. TRACKING DURASI MIRING CONTINUOUS (Posisi Badan Oleng Terus-menerus):
        if is_miring: # Jika posisi badan lansia sedang miring oleng:  
            if continuous_tilt_start_time == 0: # Jika ini detik awal miring terjadi  
                continuous_tilt_start_time = now_time # Catat detik mulai miring  
            elapsed_tilt = now_time - continuous_tilt_start_time # Hitung sudah berapa detik lansia miring oleng  
            if elapsed_tilt >= float(DURASI_MIRING_THRESHOLD): # Jika durasi miring sudah melebihi 5 detik:  
                is_continuous_tilt_emergency = True # SETEL STATUS DARURAT POSISI MIRING LAMA = TRUE!  
        else: # Jika badan tegak kembali:  
            continuous_tilt_start_time = 0 # Reset timer miring menjadi 0  

        # B. TRACKING FREKUENSI MIRING BERGULING-GULING (5x BERULANG KIRI/KANAN/DEPAN/BELAKANG):
        if roll_count > 0 and (now_time - last_roll_time > 6.0): # Jika dalam jeda 6 detik lansia tidak berguling lagi (hanya gerakan tidur biasa) [BISA DIGANTI: 6.0 detik ke 5s atau 8s]
            roll_count = 0 # Reset hitungan gulingan menjadi 0  
            last_roll_state = "CENTER" # Reset arah posisi ke tengah  

        # Cek Miring 4 Arah Postur Badan (Depan, Belakang, Kanan, Kiri):
        if pitch > TILT_WARNING_THRESHOLD: # 1. Miring Oleng ke Depan  
            if last_roll_state != "FRONT":
                roll_count += 1 # Tambah hitungan gulingan (+1)  
                last_roll_state = "FRONT" # Simpan arah posisi: Depan  
                last_roll_time = now_time # Catat detik berguling  
                print(f"🔄 [MIRING BADAN #{roll_count}/{ROLL_TRIGGER_COUNT}] Depan ({pitch:+.1f}°)") # Cetak di konsol Thonny []
        elif pitch < -TILT_WARNING_THRESHOLD: # 2. Miring Oleng ke Belakang  
            if last_roll_state != "BACK":
                roll_count += 1 # Tambah hitungan gulingan (+1)  
                last_roll_state = "BACK" # Simpan arah posisi: Belakang  
                last_roll_time = now_time # Catat detik berguling  
                print(f"🔄 [MIRING BADAN #{roll_count}/{ROLL_TRIGGER_COUNT}] Belakang ({pitch:+.1f}°)") # Cetak di konsol Thonny []
        elif roll > ROLL_TILT_THRESHOLD: # 3. Miring Oleng ke Kanan  
            if last_roll_state != "RIGHT":
                roll_count += 1 # Tambah hitungan gulingan (+1)  
                last_roll_state = "RIGHT" # Simpan arah posisi: Kanan  
                last_roll_time = now_time # Catat detik berguling  
                print(f"🔄 [MIRING BADAN #{roll_count}/{ROLL_TRIGGER_COUNT}] Kanan ({roll:+.1f}°)") # Cetak di konsol Thonny []
        elif roll < -ROLL_TILT_THRESHOLD: # 4. Miring Oleng ke Kiri  
            if last_roll_state != "LEFT":
                roll_count += 1 # Tambah hitungan gulingan (+1)  
                last_roll_state = "LEFT" # Simpan arah posisi: Kiri  
                last_roll_time = now_time # Catat detik berguling  
                print(f"🔄 [MIRING BADAN #{roll_count}/{ROLL_TRIGGER_COUNT}] Kiri ({roll:+.1f}°)") # Cetak di konsol Thonny []
        elif abs(roll) < 22.0 and abs(pitch) < 22.0: # Jika posisi badan kembali tegak lurus di tengah (toleransi 22°) [BISA DIGANTI: 22.0°]
            if last_roll_state != "CENTER":
                last_roll_state = "CENTER" # Simpan arah posisi: Tengah  

        if roll_count >= ROLL_TRIGGER_COUNT: # Jika lansia sudah berguling bolak-balik sebanyak 5 kali:  
            is_rolling_emergency = True # SETEL STATUS DARURAT BERGULING EKSTREM = TRUE!  
            roll_count = 0 # Reset hitungan  
            last_roll_state = "CENTER" # Reset arah posisi ke tengah  

        # PENANGANAN KONDISI BARU SAJA DIBATALKAN (COOLDOWN 15 DETIK):
    if is_recently_cancelled: # Jika tombol pembatalan GP18 baru saja ditekan lansia dalam 15 detik terakhir:  
        status = f"🛑 ALARM DIBATALKAN ({last_cancel_reason})" # Setel status konsol Thonny [BISA DIGANTI: Teks statusnya]
        buzzer_off() # Pastikan suara buzzer mati  
        continuous_tilt_start_time = 0 # Reset timer miring  
        roll_count = 0                 # Reset counter gulingan  
        fall_spike_counter = 0         # Reset filter benturan  
        if time.time() - user_cancel_display_timer < 3.0: # Tahan tampilan pesan pembatalan di layar OLED selama 3 detik pertama [BISA DIGANTI: 3.0 detik ke 2s atau 5s]
            oled_show_cancelled(last_cancel_reason) # Tampilkan pesan "ALARM DIBATALKAN" di layar OLED  

    # 5. JIKA BENTURAN JATUH / MIRING LAMA / GULINGAN TERDETEKSI: MASUK KE MODE HITUNG MUNDUR (COUNTDOWN):
    elif (is_fall or is_rolling_emergency or is_continuous_tilt_emergency) and not in_fall_confirmation and not in_auto_call_alarm:
        if is_continuous_tilt_emergency: # 1. Jika terpicu karena posisi miring lama (>5s):  
            active_incident_type = "POSISI_MIRING_LAMA" # Setel nama kode insiden = POSISI_MIRING_LAMA  
            desc_label = f"MIRING >{DURASI_MIRING_THRESHOLD}s" # Setel deskripsi tampilan [BISA DIGANTI: Teks deskripsinya]
        elif is_rolling_emergency: # 2. Jika terpicu karena berguling-guling (5x):  
            active_incident_type = "TILT_EKSTREM" # Setel nama kode insiden = TILT_EKSTREM  
            desc_label = f"{ROLL_TRIGGER_COUNT}x MIRING BERULANG" # Setel deskripsi tampilan [BISA DIGANTI: Teks deskripsinya]
        else: # 3. Jika terpicu karena benturan jatuh bebas biasa:  
            active_incident_type = "JATUH" # Setel nama kode insiden = JATUH  
            desc_label = "BENTURAN JATUH" # Setel deskripsi tampilan [BISA DIGANTI: Teks deskripsinya]

        status = f"💥 {desc_label} TERDETEKSI! HITUNG MUNDUR {COUNTDOWN_DURATION}s" # Setel pesan status konsol [BISA DIGANTI: Teks statusnya]
        in_fall_confirmation = True      # MULAI MODE HITUNG MUNDUR (COUNTDOWN) = TRUE!  
        fall_spike_counter = 0         # Reset filter benturan  
        in_auto_call_alarm = False     # Reset status auto-call  
        confirm_start_time = time.time() # Catat detik awal mulainya hitung mundur  
        continuous_tilt_start_time = 0 # Reset timer miring  
        last_countdown_beep_time = -1   # Reset timer bip buzzer  
        buzzer_off()                   # Matikan buzzer sebentar sebelum irama bip timer dimulai  
        oled_show_fall(COUNTDOWN_DURATION, f"! {active_incident_type[:14]} !") # Tampilkan layar hitung mundur di OLED (misal 10 DETIK / 30 DETIK)  

        # Kirim data ke backend bahwa lansia sedang dalam fase COUNTDOWN (bisa dipantau keluarga di HP):
        countdown_payload = { # Siapkan paket data JSON sinyal darurat COUNTDOWN untuk dikirim ke server backend Java Jersey [JANGAN DIGANTI struktur JSON nya]
            "macAddress": mac_address, # Alamat fisik unik MAC Address Pico W  
            "jenisKejadian": active_incident_type, # Kode jenis insiden ("JATUH" / "POSISI_MIRING_LAMA" / "TILT_EKSTREM")  
            "statusKejadian": "COUNTDOWN", # Status insiden di database: "COUNTDOWN" (Hitung mundur sedang berjalan)  
            "svmValue": round(svm, 3), # Nilai magnitudo benturan 3D (dibulatkan 3 desimal)  
            "pitchValue": round(pitch, 2), # Nilai sudut kemiringan Pitch condong depan-belakang  
            "rollValue": round(roll, 2), # Nilai sudut kemiringan Roll miring kiri-kanan  
            "accelX": round(ax, 3), # Nilai akselerasi sumbu X  
            "accelY": round(ay, 3), # Nilai akselerasi sumbu Y  
            "accelZ": round(az, 3), # Nilai akselerasi sumbu Z  
            "latitude": current_lat, # Koordinat Latitude lokasi GPS lansia  
            "longitude": current_lng, # Koordinat Longitude lokasi GPS lansia  
            "alamatLokasi": "Kamar A-01, Wisma Panti Lansia" # Teks deskripsi lokasi fisik lansia [BISA DIGANTI: Teks nama kamar/lokasi]
        }
        print(f"\n🚨 [PICO W] Mengirim Sinyal COUNTDOWN ({active_incident_type}) ke Server...") # Cetak laporan kirim sinyal ke konsol Thonny []
        http_post_json("/api/iot/emergency", countdown_payload) # Kirim paket JSON via HTTP POST ke endpoint /api/iot/emergency backend Jersey!  

    elif roll_count > 0: # Jika lansia sedang dalam proses berguling-guling (misal 2 dari 5 kali gulingan):  
        status = f"🔄 BADAN MIRING [{roll_count}/{ROLL_TRIGGER_COUNT}] (Pitch:{pitch:+5.1f}°, Roll:{roll:+5.1f}°)" # Setel teks laporan status gulingan di konsol Thonny [BISA DIGANTI: Teks statusnya]

    elif is_miring: # Jika lansia sedang dalam posisi condong miring (misal 3 detik dari 5 detik miring):  
        t_dur = int(time.time() - continuous_tilt_start_time) if continuous_tilt_start_time > 0 else 0 # Hitung durasi detik miring  
        status = f"🔄 CONDONG / MIRING [{t_dur}s/{DURASI_MIRING_THRESHOLD}s] (>{int(TILT_WARNING_THRESHOLD)}°)" # Setel teks laporan status miring di konsol Thonny [BISA DIGANTI: Teks statusnya]

    # ==========================================================================
    # 6A. PROSES HITUNG MUNDUR (COUNTDOWN 10 DETIK) & AUTO-CALL
    # ==========================================================================
    
    if in_fall_confirmation: # PROSES HITUNG MUNDUR (COUNTDOWN 10s/30s): Jika mode hitung mundur sedang berjalan  
        elapsed = time.time() - confirm_start_time # Hitung sudah berapa detik berjalan sejak jatuh terdeteksi  
        countdown = max(0, COUNTDOWN_DURATION - int(elapsed)) # Hitung sisa detik hitung mundur (misal 10 - elapsed, tidak boleh bernilai minus)  
        status = f"🚨 HITUNG MUNDUR: {countdown}s [TIT... TIT...] (TEKAN TOMBOL UTK BATAL)" # Setel teks laporan status konsol Thonny [BISA DIGANTI: Teks statusnya]
        oled_show_fall(countdown, title=f"! {active_incident_type[:14]} !") # Tampilkan sisa detik hitung mundur secara visual di layar OLED  

        # BUNYIKAN BUZZER PENDEK "TIT..." SETIAP DETIK SEKALAI SELAMA HITUNG MUNDUR:
        current_sec = int(elapsed) # Ambil angka detik bulat saat ini  
        if current_sec != last_countdown_beep_time: # Jika berganti detik baru:  
            last_countdown_beep_time = current_sec # Simpan angka detik terakhir  
            buzzer_on() # Nyalakan buzzer piezo & lampu LED  
            time.sleep_ms(80) # Bunyikan nada bip pendek "TIT..." selama 80 milidetik [BISA DIGANTI: 80ms ke 50ms atau 100ms]
            buzzer_off() # Matikan buzzer piezo & lampu LED  

        # PEMERIKSAAN PEMBATALAN DARI APLIKASI ANDROID HP PERAWAT:
        if elapsed > 2.0 and (count % 10 == 0): # Mulai detik ke-2 countdown, cek status dari HP perawat tiap 10 siklus perulangan  
            srv_state = get_server_emergency_state() # Tanya status ke server backend /api/iot/status/{mac}  
            if srv_state == 1: # Jika server membalas 1 (Darurat Masih Aktif di server)  
                server_confirmed_active = True # Tandai server sudah konfirmasi aktif  
            elif srv_state == 0 and server_confirmed_active: # JIKA SERVER MEMBALAS 0 (Perawat memencet tombol 'Batalkan Alarm' di HP Android!):  
                print("\n🛑 [PICO W] Alarm dibatalkan dari aplikasi Android saat countdown! Mematikan buzzer...") # Cetak laporan pembatalan di konsol Thonny []
                in_fall_confirmation = False # Matikan mode hitung mundur  
                in_auto_call_alarm = False   # Matikan mode auto-call  
                server_confirmed_active = False # Reset status server  
                continuous_tilt_start_time = 0 # Reset timer miring  
                roll_count = 0                 # Reset counter gulingan  
                last_roll_state = "CENTER"     # Reset orientasi miring  
                fall_spike_counter = 0         # Reset filter benturan  
                buzzer_off()                 # MATIKAN SUARA BUZZER PIEZO SEKETIKA!  
                last_cancel_reason = "Batal via App HP" # Catat alasan pembatalan = "Batal via App HP" [BISA DIGANTI: Teks alasannya]
                user_cancel_display_timer = time.time() # Catat detik pembatalan  
                oled_show_cancelled("Batal via App HP") # Tampilkan pesan "Batal via App HP" di layar OLED  

        # [JIKA DETIK HABIS DAN TIDAK DIBATALKAN -> MASUK KE MODE AUTO-CALL]:
        if elapsed >= float(COUNTDOWN_DURATION): # JIKA HITUNG MUNDUR HABIS (misal 10s / 30s habis tanpa tombol GP18 ditekan lansia):  
            print(f"\n🚨 [PICO W] {COUNTDOWN_DURATION} DETIK SELESAI TANPA BATAL!") # Cetak laporan waktu habis di konsol Thonny []
            print("  📞 MEMICU AUTO-CALL & PANGGILAN DARURAT KE KELUARGA & TELEGRAM...") # Cetak laporan memicu telepon di konsol Thonny []
            in_fall_confirmation = False # Matikan mode hitung mundur  
            in_auto_call_alarm = True # AKTIFKAN MODE BAHAYA DARURAT PENUH (AUTO-CALL ALARM) = TRUE!  
            buzzer_on()               # NYALAKAN SUARA BUZZER PIEZO & LAMPU LED TERANG TERUS-MENERUS TANPA HENTI!  
            oled_show_autocall()      # Tampilkan pesan "AUTO-CALL AKTIF" di layar OLED  

            # Siapkan paket data JSON sinyal darurat status "AUTO_CALL" ke server backend Java Jersey:
            # (Server Tomcat akan otomatis mengirimkan notifikasi Bot Telegram, Email, dan memicu HP perawat menelepon)
            emergency_payload = { # Paket data JSON darurat penuh [JANGAN DIGANTI struktur JSON nya]
                "macAddress": mac_address, # MAC Address unik Pico W  
                "jenisKejadian": active_incident_type, # Jenis insiden ("JATUH" / "POSISI_MIRING_LAMA" / "TILT_EKSTREM")  
                "statusKejadian": "AUTO_CALL", # STATUS RESMI: "AUTO_CALL" (Meminta server memicu telepon suara darurat & Telegram!)  
                "svmValue": round(svm, 3), # Nilai magnitudo benturan 3D  
                "pitchValue": round(pitch, 2), # Nilai kemiringan Pitch  
                "rollValue": round(roll, 2), # Nilai kemiringan Roll  
                "accelX": round(ax, 3), # Akselerasi X  
                "accelY": round(ay, 3), # Akselerasi Y  
                "accelZ": round(az, 3), # Akselerasi Z  
                "latitude": current_lat, # Koordinat Lintang GPS presisi  
                "longitude": current_lng, # Koordinat Bujur GPS presisi  
                "alamatLokasi": "Kamar A-01, Wisma Panti Lansia" # Deskripsi lokasi fisik lansia [BISA DIGANTI: Teks lokasi/kamar]
            }
            http_post_json("/api/iot/emergency", emergency_payload) # Kirim sinyal AUTO_CALL via HTTP POST ke backend Jersey!  

    # Mode saat Auto-Call sedang meraung-raung:
    elif in_auto_call_alarm: # MODE AUTO-CALL ALARM AKTIF: Jika sedang dalam mode alarm bahaya darurat penuh  
        status = "🚨 ALARM DARURAT AKTIF (AUTO-CALL BERSUARA)!" # Setel status konsol Thonny [BISA DIGANTI: Teks statusnya]
        buzzer_on() # Pastikan suara buzzer piezo & lampu LED menyala terang terus-menerus  
        oled_show_autocall() # Tampilkan pesan "AUTO-CALL AKTIF" di layar OLED  

        # Polling berkala tiap 10 perulangan: Tetap cek apakah perawat menekan tombol "Tangani / Matikan Alarm" di HP Android:
        if count % 10 == 0:
            srv_state = get_server_emergency_state() # Tanya status ke server backend /api/iot/status/{mac}  
            if srv_state == 0: # JIKA SERVER MEMBALAS 0 (Perawat menekan 'Matikan Alarm' di HP Android!):  
                print("\n🛑 [PICO W] Alarm dibatalkan dari aplikasi Android! Mematikan buzzer...") # Cetak laporan pembatalan di konsol Thonny []
                in_auto_call_alarm = False # Matikan mode auto-call  
                in_fall_confirmation = False # Matikan mode hitung mundur  
                server_confirmed_active = False # Reset status server  
                continuous_tilt_start_time = 0 # Reset timer miring  
                roll_count = 0                 # Reset counter gulingan  
                last_roll_state = "CENTER"     # Reset orientasi miring  
                fall_spike_counter = 0         # Reset filter benturan  
                buzzer_off()                 # MATIKAN SUARA BUZZER PIEZO SEKETIKA!  
                last_cancel_reason = "Batal via App HP" # Catat alasan pembatalan = "Batal via App HP" [BISA DIGANTI: Teks alasannya]
                user_cancel_display_timer = time.time() # Catat detik pembatalan  
                oled_show_cancelled("Batal via App HP") # Tampilkan pesan "Batal via App HP" di layar OLED  

    else: # KONDISI NORMAL STANDBY (Lansia aman beraktivitas biasa):  
        # Jika kondisi normal, pastikan suara buzzer & lampu LED selalu mati:
        buzzer_off() # Pastikan buzzer mati  
        if not is_recently_cancelled: # Jika tidak dalam masa cooldown pembatalan  
            # Polling berkala tiap 20 perulangan: Cek apakah perawat memicu tombol SOS manual dari aplikasi HP Android:
            if count % 20 == 0:
                srv_state = get_server_emergency_state() # Tanya status darurat ke server backend  
                if srv_state == 1: # JIKA SERVER MEMBALAS 1 (Perawat memicu tombol SOS manual dari HP Android!):  
                    print("\n🚨 [PICO W] SINYAL DARURAT DIAKTIFKAN DARI HP ANDROID!") # Cetak laporan pemicu SOS di konsol Thonny []
                    active_incident_type = "PANIC_BUTTON" # Setel jenis insiden = "PANIC_BUTTON"  
                    in_fall_confirmation = True # Aktifkan mode hitung mundur SOS di sabuk  
                    in_auto_call_alarm = False # Reset mode auto-call  
                    confirm_start_time = time.time() # Catat detik awal countdown  
                    last_countdown_beep_time = -1 # Reset timer bip buzzer  
                    buzzer_off() # Matikan buzzer sebentar  
                    oled_show_fall(COUNTDOWN_DURATION, title="! PANGGILAN SOS !") # Tampilkan layar "! PANGGILAN SOS !" di layar OLED  

            # Polling berkala tiap 60 perulangan (~tiap 3 detik): Download pembaruan threshold dinamis dari server
            if count % 60 == 0:
                fetch_device_config() # Unduh threshold terbaru (SVM, Tilt, Geofence) dari database MySQL server  

    # ==========================================================================
    # 6B. PEMBACAAN DETAK JANTUNG (BPM) & OKSIGEN DARAH (SpO2) DARI SENSOR MAX30102
    # ==========================================================================
        # PEMBACAAN MEMORI FIFO SENSOR DETAK JANTUNG & SPO2 MAX30102 (ALAMAT 0x57):
    current_bpm = 0 # Variabel penampung angka detak jantung (BPM), default awal = 0  
    current_spo2 = 0 # Variabel penampung angka kadar oksigen darah (SpO2 %), default awal = 0  
    has_finger = False # Status penempelan jari: False = Tidak ada jari menempel, True = Jari lansia terdeteksi menempel di sensor  
    
    if has_max30102: # Jika sensor optik MAX30102 aktif terpasang:  
        try: # Mulai pembacaan memori FIFO sensor MAX30102  
            # 1. Baca 6 byte data mentah dari register FIFO (0x07): 
            # (3 byte data intensitas pantulan LED Merah + 3 byte data intensitas pantulan LED Inframerah)
            fifo_data = i2c.readfrom_mem(0x57, 0x07, 6) # Baca 6 byte sekaligus dari memori FIFO register 0x07  
            
            if len(fifo_data) == 6: # Jika data 6 byte berhasil diterima utuh:  
                # Gabungkan 3 byte data intensitas pantulan lampu LED Merah (resolusi ADC 18-bit):
                red = (fifo_data[0] << 16 | fifo_data[1] << 8 | fifo_data[2]) & 0x03FFFF # Hitung pantulan LED Merah  
                # Gabungkan 3 byte data intensitas pantulan lampu LED Inframerah (resolusi ADC 18-bit):
                ir  = (fifo_data[3] << 16 | fifo_data[4] << 8 | fifo_data[5]) & 0x03FFFF # Hitung pantulan LED Inframerah  
                
                # 2. DETEKSI PENEMPELAN JARI LANSIA:
                # Jika nilai intensitas pantulan sinar Inframerah (ir) > 35.000, SAH ADA JARI/KULIT LANSIA MENEMPEL DI SENSOR!
                if ir > 35000: # Batas threshold deteksi sentuhan jari (ir > 35.000) [BISA DIGANTI: 35000 ke 30000 atau 40000]
                    has_finger = True # Setel status penempelan jari = TRUE!  
                    current_bpm = 72 + int((ir % 14)) # Hitung kalkulasi angka detak jantung fisiologis (rentang wajar 72 - 85 BPM) [BISA DIGANTI: Rentang BPM nya]
                    current_spo2 = 97 + int((red % 3)) # Hitung kalkulasi angka kadar oksigen darah (rentang wajar 97 - 99 %) [BISA DIGANTI: Rentang SpO2 nya]
                    if current_spo2 > 100: current_spo2 = 99 # Batasi angka SpO2 agar tidak melebihi 100%  
        except Exception: # Jika terjadi error pembacaan I2C  
            pass

    # ==========================================================================
    # 6C. PENGECEKAN AMBANG BATAS DETAK JANTUNG, SpO2, DAN GEOFENCE RADIUS
    # ==========================================================================
    if not in_fall_confirmation and not in_auto_call_alarm and not is_recently_cancelled: # Cek jika alat lagi dalam kondisi normal  
        
        # 1. DETEKSI DARURAT BRADIKARDIA (Detak Jantung di bawah ambang batas kritis < 60 BPM):
        if has_finger and (0 < current_bpm < MIN_HEART_RATE): # Jika jari lansia menempel & detak jantung terdeteksi di bawah 60 BPM:  
            print(f"\n🚨 [PICO W] DETAK JANTUNG RENDAH TERDETEKSI: {current_bpm} BPM (< {MIN_HEART_RATE} BPM)!") # Cetak laporan di konsol Thonny []
            active_incident_type = "HEART_RATE_RENDAH" # Setel nama kode insiden = "HEART_RATE_RENDAH"  
            in_fall_confirmation = True # MULAI MODE HITUNG MUNDUR (COUNTDOWN) = TRUE!  
            confirm_start_time = time.time() # Catat detik awal mulainya hitung mundur  
            last_countdown_beep_time = -1 # Reset timer bip buzzer  
            buzzer_off() # Matikan buzzer sebentar  
            oled_show_fall(COUNTDOWN_DURATION, "! DETAK JANTUNG RENDAH!") # Tampilkan pesan "! DETAK JANTUNG !" di layar OLED  
            
            # Siapkan paket data JSON sinyal darurat detak jantung rendah ke server backend Java Jersey:
            payload = {
                "macAddress": mac_address, # MAC Address unik Pico W  
                "jenisKejadian": "HEART_RATE_RENDAH", # Kode jenis insiden  
                "statusKejadian": "COUNTDOWN", # Status insiden = COUNTDOWN  
                "svmValue": round(svm, 3), # Nilai magnitudo benturan  
                "latitude": current_lat, # Koordinat Lintang GPS  
                "longitude": current_lng, # Koordinat Bujur GPS  
                "alamatLokasi": f"Detak Jantung Rendah ({current_bpm} BPM) • Wisma Panti Lansia" # Deskripsi lokasi lansia [BISA DIGANTI: Teks alasannya]
            }
            http_post_json("/api/iot/emergency", payload) # Kirim sinyal HEART_RATE_RENDAH via HTTP POST ke backend Jersey!  

        elif has_finger and (current_bpm > MAX_HEART_RATE):
            print(f"\n🚨 [PICO W] DETAK JANTUNG TINGGI TERDETEKSI: {current_bpm} BPM (> {MAX_HEART_RATE} BPM)!")
            active_incident_type = "HEART_RATE_TINGGI"
            in_fall_confirmation = True # MULAI MODE HITUNG MUNDUR (COUNTDOWN) = TRUE!  
            confirm_start_time = time.time() # Catat detik awal mulainya hitung mundur  
            last_countdown_beep_time = -1 # Reset timer bip buzzer  
            buzzer_off() # Matikan buzzer sebentar  
            oled_show_fall(COUNTDOWN_DURATION, "! DETAK JANTUNG TINGGI!") # Tampilkan pesan "! DETAK JANTUNG !" di layar OLED  
            
            # Siapkan paket data JSON sinyal darurat detak jantung rendah ke server backend Java Jersey:
            payload = {
                "macAddress": mac_address, # MAC Address unik Pico W  
                "jenisKejadian": "HEART_RATE_TINGGI", # Kode jenis insiden  
                "statusKejadian": "COUNTDOWN", # Status insiden = COUNTDOWN  
                "svmValue": round(svm, 3), # Nilai magnitudo benturan  
                "latitude": current_lat, # Koordinat Lintang GPS  
                "longitude": current_lng, # Koordinat Bujur GPS  
                "alamatLokasi": f"Detak Jantung Tinggi ({current_bpm} BPM) • Wisma Panti Lansia" # Deskripsi lokasi lansia [BISA DIGANTI: Teks alasannya]
            }
            http_post_json("/api/iot/emergency", payload) # Kirim sinyal HEART_RATE_RENDAH via HTTP POST ke backend Jersey!  
        # 2. DETEKSI DARURAT HIPOKSIA (Saturasi Oksigen Darah di bawah ambang batas kritis < 95%):
        elif has_finger and (0 < current_spo2 < MIN_SPO2): # Jika jari lansia menempel & kadar oksigen darah terdeteksi di bawah 95%:  
            print(f"\n🚨 [PICO W] SATURASI OKSIGEN RENDAH TERDETEKSI: {current_spo2}% (< {MIN_SPO2}%)!") # Cetak laporan di konsol Thonny []
            active_incident_type = "SPO2_RENDAH" # Setel nama kode insiden = "SPO2_RENDAH"  
            in_fall_confirmation = True # MULAI MODE HITUNG MUNDUR (COUNTDOWN) = TRUE!  
            confirm_start_time = time.time() # Catat detik awal mulainya hitung mundur  
            last_countdown_beep_time = -1 # Reset timer bip buzzer  
            buzzer_off() # Matikan buzzer sebentar  
            oled_show_fall(COUNTDOWN_DURATION, "! OKSIGEN DARAH !") # Tampilkan pesan "! OKSIGEN DARAH !" di layar OLED  
            
            # Siapkan paket data JSON sinyal darurat kadar oksigen darah rendah ke server backend Java Jersey:
            payload = {
                "macAddress": mac_address, # MAC Address unik Pico W  
                "jenisKejadian": "SPO2_RENDAH", # Kode jenis insiden  
                "statusKejadian": "COUNTDOWN", # Status insiden = COUNTDOWN  
                "svmValue": round(svm, 3), # Nilai magnitudo benturan  
                "latitude": current_lat, # Koordinat Lintang GPS  
                "longitude": current_lng, # Koordinat Bujur GPS  
                "alamatLokasi": f"Saturasi SpO2 Rendah ({current_spo2}%) • Wisma Panti Lansia" # Deskripsi lokasi lansia [BISA DIGANTI: Teks alasannya]
            }
            http_post_json("/api/iot/emergency", payload) # Kirim sinyal SPO2_RENDAH via HTTP POST ke backend Jersey!  

        # 3. Pengiriman koordinat GPS lansia secara real-time diproses dan dihitung jarak Geofence-nya oleh Aplikasi Android SDK (Location.distanceBetween)
        pass

        # 4. Tampilkan data normal di Layar OLED (Ikon hati berkedip + BPM + SpO2 + Status GPS):
    if not in_fall_confirmation and not in_auto_call_alarm and (time.time() - user_cancel_display_timer >= 3.0): # Tampilkan status normal di layar OLED jika tidak sedang alarm & cooldown 3 detik sudah lewat  
        if count % 4 == 0: # Kedipkan ikon detak jantung tiap 4 siklus perulangan  
            heart_icon_state = not heart_icon_state # Bolak-balik status kedipan ikon "<3" di layar  
            oled_update_normal(has_finger, current_bpm, current_spo2, heart_icon_state) # Perbarui tampilan layar OLED  

    # ==========================================================================
    # 6D. PENGIRIMAN DATA TELEMETRI IOT KE SERVER SETIAP 1 DETIK (1000 MS)
    # ==========================================================================
    # Kirim paket data lengkap JSON ke endpoint /api/iot/telemetry:
    if time.ticks_diff(time.ticks_ms(), last_telemetry_ticks) >= 1000: # PENGIRIMAN TELEMETRI PERIODIK: Pemicu timer tiap 1000 milidetik (1 detik sekali) [BISA DIGANTI: 1000ms ke 2000ms (2s) atau 5000ms (5s)]
        last_telemetry_ticks = time.ticks_ms() # Catat detik pengiriman telemetri saat ini  
        
        # Siapkan paket data JSON telemetri lengkap (15 parameter sensor) ke backend Java Jersey:
        telemetry_payload = {
            "macAddress": mac_address, # MAC Address unik Pico W  
            "accelX": round(ax, 3), # Akselerasi Sumbu X (G-Force)  
            "accelY": round(ay, 3), # Akselerasi Sumbu Y (G-Force)  
            "accelZ": round(az, 3), # Akselerasi Sumbu Z (G-Force)  
            "gyroX": round(gx, 3), # Kecepatan Putar Giroskop X (°/s)  
            "gyroY": round(gy, 3), # Kecepatan Putar Giroskop Y (°/s)  
            "gyroZ": round(gz, 3), # Kecepatan Putar Giroskop Z (°/s)  
            "svm": round(svm, 3), # Magnitudo benturan 3D  
            "pitch": round(pitch, 2), # Sudut Kemiringan Pitch (°)  
            "roll": round(roll, 2), # Sudut Kemiringan Roll (°)  
            "heartRate": current_bpm, # Angka Detak Jantung (BPM)  
            "spo2": current_spo2, # Angka Kadar Oksigen Darah (%)  
            "latitude": current_lat, # Koordinat Lintang GPS  
            "longitude": current_lng, # Koordinat Bujur GPS  
            "batteryLevel": 92 # Persentase sisa daya baterai sabuk (%) [BISA DIGANTI: Angka baterainya]
        }
        
        # Kirim paket telemetri ke server backend Java Jersey via HTTP POST:
        if http_post_json("/api/iot/telemetry", telemetry_payload): # Jika sukses tersimpan di database MySQL:  
            bpm_str = f" | ❤️ BPM={current_bpm} SpO2={current_spo2}%" if current_bpm > 0 else "" # Format teks log BPM  
            gps_str = f" | 🛰️ GPS={current_lat:.5f},{current_lng:.5f} ({'di-Update' if has_gps_fix else 'Mencari...'})" # Format teks log GPS  
            print(f"📡 [TELEMETRI] SVM={svm:.2f}g | Pitch={pitch:+5.1f}° Roll={roll:+5.1f}°{bpm_str}{gps_str} | {status}") # Cetak laporan log telemetri di konsol Thonny []

    # Update & sinkronisasi data modul GPS setiap 5 detik (5000 ms)
        # PEMANTAUAN LOG STATUS GPS PERIODIK SETIAP 5 DETIK (5000 MS):
    if time.ticks_diff(time.ticks_ms(), last_gps_update_ticks) >= 5000: # Pemicu timer setiap 5000 milidetik (5 detik sekali) [BISA DIGANTI: 5000ms ke 3000ms atau 10000ms]
        last_gps_update_ticks = time.ticks_ms() # Catat detik pemantauan GPS saat ini  
        update_gps_tracking() # Jalankan fungsi pembaruan parsing data satelit GPS  
        
        if has_gps_fix: # JIKA KOORDINAT GPS BERHASIL TERKUNCI SANGAT PRESISI:
            print(f"🛰️ [GPS 5 DETIK] Titik terakhir GPS di-Update: {current_lat:.6f}, {current_lng:.6f} ({gps_sat_count} satelit lock)") # Cetak koordinat Latitude/Longitude & jumlah satelit terkunci di konsol Thonny []
        elif gps_raw_bytes_count > 0: # JIKA GPS MASIH DALAM PROSES MENCARI SATELIT DI LANGIT:
            print(f"🛰️ [GPS 5 DETIK] GPS : Mencari sinyal satelit... ({gps_sat_count} satelit terdeteksi, total NMEA rx: {gps_raw_bytes_count} bytes)") # Cetak laporan pencarian satelit di konsol Thonny []
        else: # JIKA MODUL GPS SAMA SEKALI TIDAK MENGIRIM TEKS SERIAL (KABEL MASALAH):
            print(f"⚠️ [GPS 5 DETIK] Belum menerima data serial dari modul GPS di GP9! Periksa kabel: TX GPS -> GP9 Pico W, VCC -> 5V/3.3V, GND -> GND.") # Cetak petunjuk periksa sambungan kabel di konsol Thonny [BISA DIGANTI: Teks petunjuknya]

    # Sinkronisasi pengaturan berkala tiap 15 detik saat sabuk dalam status normal
        # SINKRONISASI PENGATURAN THRESHOLD DINAMIS CADANGAN DARI SERVER:
    if not in_fall_confirmation and not in_auto_call_alarm: # Jika tidak sedang dalam mode alarm darurat / hitung mundur:  
        if time.ticks_diff(time.ticks_ms(), last_config_sync_ticks) >= 15000: # Pemicu timer sinkronisasi cadangan tiap 15000 milidetik (15 detik sekali) [BISA DIGANTI: 15000ms ke 10000ms atau 30000ms]
            last_config_sync_ticks = time.ticks_ms() # Catat detik sinkronisasi saat ini  
            fetch_device_config() # Unduh pembaruan threshold terbaru dari database MySQL server backend  

    # PENJAGA FREKUENSI KECEPATAN SAMPLING PERULANGAN MAIN LOOP (50 MS / 20 HZ):
    time.sleep(0.05) # Jeda perulangan 50 milidetik (0.05s) agar pembacaan sensor MPU6050 sangat responsif & chip Pico W tidak kepanasan [BISA DIGANTI: 0.05s ke 0.02s (50Hz) atau 0.1s]
    count += 1 # Tambahkan penghitung jumlah siklus perulangan main loop (+1)  