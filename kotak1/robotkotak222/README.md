# Robot Beroda Grip Kotak (Kelompok 1) - Remote Android (Java)

Objek: Skyblock 20 x 20 cm | Project Internship BANDHAYUDHA 2026

## Alur
    HP (aplikasi) --WiFi (TCP)--> ESP8266/ESP32 --UART--> STM32 USART1 (PA9 TX, PA10 RX) --> driver BTS + motor

- ESP membuat hotspot `RobotKotak` (password `robot1234`), IP `192.168.4.1`, port `8888`.
- Sambungkan HP ke WiFi `RobotKotak`, buka aplikasi, tekan **Hubungkan WiFi**.
- Program ESP: `esp32_esp8266_bridge/esp32_esp8266_bridge.ino` (hanya meneruskan data TCP ke UART).

## Kabel ESP <-> STM32 (3,3 V, GND disambung)
| ESP8266 | ESP32 | STM32 |
|---|---|---|
| TX (GPIO1) | GPIO17 (TX2) | PA10 (USART1 RX) |
| RX (GPIO3) | GPIO16 (RX2) | PA9 (USART1 TX) |
| GND | GND | GND |

## Tampilan (landscape, 3 kolom)
- Kiri (gelap)  : joystick GERAK (X, Y) + tombol bulat J (Jepit) dan B (Buka)
- Tengah (terang): status, IP/port, Hubungkan WiFi, ARAH (X, Y), kotak PERINTAH DIKIRIM, STOP
- Kanan (gelap) : joystick PUTAR + tombol Naik / Turun (LIFT)
- Jepit / Buka / Naik / Turun ditahan = motor jalan, lepas = berhenti.

## Protokol (teks, 20x/detik, UART 115200 8N1)
    X,Y,ARAH,PUTAR,KECEPATAN,GRIP,LIFT\n        contoh: 0,60,FORWARD,NONE,60,NONE,NONE
    X, Y       nilai joystick gerak -100..100 (X kanan +, Y maju +)
    ARAH       STOP, FORWARD, BACKWARD, LEFT, RIGHT,
               FORWARD-LEFT, FORWARD-RIGHT, BACKWARD-LEFT, BACKWARD-RIGHT
    PUTAR      NONE, CLOCKWISE, ANTICLOCKWISE
    KECEPATAN  0..100 = akar(X^2 + Y^2), maks 100
               (bila hanya memutar, KECEPATAN = 100)
    GRIP       NONE, CLOSE (jepit), OPEN (buka)
    LIFT       NONE, UP, DOWN
Tombol STOP / putus koneksi mengirim `0,0,STOP,NONE,0,NONE,NONE`. Firmware menghentikan motor
bila tidak ada baris valid > 300 ms (failsafe).

Di STM32, arah gerak dihitung dari (X, Y) dan besarnya dari KECEPATAN:
`vx = X/akar(X^2+Y^2) * KECEPATAN`, `vy = Y/akar(X^2+Y^2) * KECEPATAN`.

## Pemetaan ke file .ioc
| Fungsi | PROJECT_KOTAK (F405) | PROJECT_KOTAK2 (F401) |
|---|---|---|
| Roda L (kiri-belakang) | TIM2 CH1/CH2 (PA0/PA1) | kiri: TIM3 (PA6/PA7) |
| Roda F (depan, atas) | TIM3 CH1/CH2 (PA6/PA7) | - |
| Roda R (kanan-belakang) | TIM4 CH1/CH2 (PB6/PB7) | kanan: TIM4 (PB6/PB7) |
| Grip (CW/CCW GPIO) | DC1 (PB12/PB13) | DC1 (PB12/PB13) |
| Lift (CW/CCW GPIO) | DC2 (PB14/PB15) | DC2 (PB14/PB15) |
| DC3 (PC6/PC7) | TIDAK dipakai (rules maks 5 motor) | - |
| Komunikasi | USART1 | USART1 |
Total motor: KOTAK = 3 roda + grip + lift = 5 (sesuai batas). KOTAK2 = 2 roda + grip + lift = 4.

## Cek wajib di CubeMX
1. USART1 global interrupt BELUM aktif -> aktifkan (NVIC Settings), kalau tidak data tidak diterima.
2. USART1 Baud Rate = 115200, 8 bit, tanpa parity, 1 stop bit (sama dengan program ESP).
3. TIM3 & TIM4 (KOTAK) dan TIM4 (KOTAK2) belum punya Prescaler/Period -> PWM tidak keluar. Isi seperti TIM2 (Prescaler 36, Period 99 = ~19,5 kHz).
4. BTS7960: pin R_EN & L_EN harus dihubungkan ke 3,3V/5V.
5. Cek Clock Configuration: HSE di .ioc = 25 MHz tetapi PLLM=8 menghasilkan VCO input 2 MHz (hitungan untuk 16 MHz). Pastikan clock 72 MHz benar, karena baud UART & PWM bergantung padanya.

## Tes tanpa robot
Sambungkan HP ke WiFi ESP, lalu di laptop sambungkan USB-TTL ke pin TX ESP, buka serial monitor 115200:
gerakkan joystick, harus muncul baris `x,y,ARAH,PUTAR,KECEPATAN,GRIP,LIFT`.
Kotak "PERINTAH DIKIRIM" di aplikasi juga menampilkan baris yang sama.
