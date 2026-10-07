# 🛡️ Smart-Belt IoT Wearable & Emergency Monitoring System

![Java](https://img.shields.io/badge/Backend-Java%20Jersey-orange?style=for-the-badge&logo=openjdk)
![Kotlin](https://img.shields.io/badge/Mobile-Android%20Kotlin-brightgreen?style=for-the-badge&logo=android)
![MicroPython](https://img.shields.io/badge/Firmware-MicroPython%20Pico%20W-blue?style=for-the-badge&logo=python)
![MySQL](https://img.shields.io/badge/Database-MySQL-blue?style=for-the-badge&logo=mysql)
![Security](https://img.shields.io/badge/Domain-Network%20%26%20Cyber%20Security-red?style=for-the-badge)

An IoT-based wearable smart belt device and mobile emergency monitoring system designed for early fall detection, real-time vital sign monitoring (*heart rate & SpO2*), GPS location tracking, and automated multi-channel emergency dispatch (*Telegram Bot, Email, Android Popup & Auto-Call*) for caregivers and elderly family members.

> **Undergraduate Thesis Project**  
> **Author:** Chrisyon Putra Sitania (NRP: 160422121)  
> **Major:** Informatics Engineering (Network & Cyber Security)  
> **Institution:** University of Surabaya (UBAYA)

---

## 📋 Table of Contents
- [System Architecture](#-system-architecture)
- [Key Features](#-key-features)
- [Hardware Components](#-hardware-components)
- [Repository Structure](#-repository-structure)
- [Installation & Configuration](#-installation--configuration)
  - [1. MySQL Database](#1-mysql-database)
  - [2. Backend Server (Java Jersey)](#2-backend-server-java-jersey)
  - [3. Wearable Firmware (Raspberry Pi Pico W)](#3-wearable-firmware-raspberry-pi-pico-w)
  - [4. Android Mobile App](#4-android-mobile-app)
- [Fall Detection Workflow](#-fall-detection-workflow)
- [License & Copyright](#-license--copyright)

---

## 🏗️ System Architecture

```mermaid
graph TD
    subgraph Wearable Device
        PicoW[Raspberry Pi Pico W]
        MPU[MPU6050 Inertia Sensor] --> PicoW
        MAX[MAX30102 Heart Rate & SpO2] --> PicoW
        GPS[GY-8M GPS NMEA Module] --> PicoW
        OLED[OLED SSD1306 Display] <-- PicoW
        BTN[Push Button GP18 / SOS] --> PicoW
        BUZ[Active Low Buzzer] <-- PicoW
    end

    subgraph Server & Cloud Services
        PicoW -- HTTP / Socket API --> Backend[Java Jersey REST API]
        Backend <--> DB[(MySQL Database db_smart_belt)]
        Backend -- Bot API --> Tele[Telegram Bot Notification]
        Backend -- SMTP TLS --> Mail[Gmail OTP Notification]
    end

    subgraph Caregiver Mobile App
        App[Android App Kotlin] <--> Backend
        App -- Real-Time Monitoring --> Perawat[Caregiver / Nurse]
        App -- Emergency Auto-Call --> Telepon[Emergency Call / SMS]
    end
```

---

## ✨ Key Features

- 🚨 **Multi-Parameter Fall Detection (*Anti-False Alarm*)**: Advanced collision detection algorithm using Signal Vector Magnitude (*SVM*), body tilt angle (*Pitch/Roll*), and continuous tilt duration tracking.
- ⏱️ **Emergency Buzzer Countdown**: Features a customizable 10–30 second delay countdown allowing users to cancel false alarms via the GP18 button before alert dispatch.
- 💓 **Real-Time Vital Signs Monitoring**: Continuous pulse rate (*BPM*) and blood oxygen saturation (*SpO2*) monitoring via the MAX30102 sensor.
- 📍 **GPS Tracking & Geofencing**: Precise latitude/longitude positioning with automated safe area radius breach detection (*Geofencing*).
- 📱 **Multi-Channel Alert Dispatch**: Instant emergency broadcasts via **Telegram Bot API**, **Gmail SMTP Email**, and **Android Full-Screen Emergency Popup with Auto-Call**.
- 🛡️ **Account Approval Guard**: Enhanced security layer requiring Admin approval for newly registered users before granting system access.

---

## 🔌 Hardware Components

| Component / Sensor | Interface & Pinout | Function |
| :--- | :--- | :--- |
| **Microcontroller** | Raspberry Pi Pico W (Wi-Fi CYW43) | Main processing unit & wireless network gateway |
| **Inertial Sensor** | MPU6050 (SoftI2C GP4 SDA, GP5 SCL) | Measures $g$-force acceleration & body tilt angles |
| **Vital Sensor** | MAX30102 (SoftI2C Address 0x57) | Measures pulse rate (BPM) & oxygen saturation (%) |
| **GPS Module** | GY-8M GPS (UART1 GP8 TX, GP9 RX) | Captures satellite position coordinates (NMEA) |
| **Display Unit** | OLED SSD1306 (SoftI2C 0x3C / 0x3D) | Displays vital status, GPS fix, and countdown timer |
| **Audio Alarm** | Active Low Buzzer (GP16) | Emits loud sound alerts during fall incidents |
| **Cancel Button** | Push Button (GP18 Pull-Up) | Manual alarm cancellation & emergency SOS trigger |

---

## 📁 Repository Structure

```text
Smart-Belt_160422121/
├── database/
│   └── smart_belt_database.sql        # MySQL Database Schema DDL (db_smart_belt)
├── pico_w_firmware/
│   └── main.py                         # MicroPython Firmware for Raspberry Pi Pico W
├── smartbelt-backend-jersey/
│   ├── src/com/smartbelt/
│   │   ├── constant/Constant.java      # Central credential & constant definitions
│   │   ├── db/DbHelper.java            # JDBC Query Executor & Business Logic
│   │   ├── restjersey/                 # RESTful API Endpoints (Auth, IoT, Incidents)
│   │   └── util/                       # Telegram Bot API & Gmail SMTP Helpers
│   ├── WebContent/                     # Deployment web descriptors (web.xml)
│   └── pom.xml                         # Maven dependencies (Jersey, Jackson, MySQL Driver)
├── smartbelt_android/
│   └── app/src/main/java/com/smartbelt/caregiver/
│       ├── data/                       # Retrofit ApiClient & SharedPreferences Session
│       ├── service/                    # Background Emergency Incident Monitoring Service
│       └── ui/                         # Activities, Fragments & ViewModels (MVVM)
└── README.md                           # Project documentation
```

---

## 🛠️ Installation & Configuration

### 1. MySQL Database
1. Open your MySQL client (PHPMyAdmin / DBeaver / MySQL CLI).
2. Create a new database named `db_smart_belt`.
3. Import the SQL schema file located in the `database/` directory:
   ```bash
   mysql -u root -p db_smart_belt < database/smart_belt_database.sql
   ```

### 2. Backend Server (Java Jersey)
1. Open the `smartbelt-backend-jersey` project in your IDE (Eclipse / IntelliJ IDEA).
2. Update the credentials in [`Constant.java`](file:///c:/Users/chris/Downloads/Smart-Belt_160422121%20-%20Copy/smartbelt-backend-jersey/src/com/smartbelt/constant/Constant.java):
   ```java
   User("root"),
   Password("your_mysql_password"),              // // use your own database password
   TelegramBotToken("your_telegram_bot_token"),  // // use your own telegram bot token
   TelegramChatId("your_telegram_chat_id"),      // // use your own telegram chat id
   SmtpUser("your_email@gmail.com"),             // // use your own email
   SmtpPassword("your_smtp_app_password");       // // use your own app password
   ```
3. Build and deploy the server on Apache Tomcat 9/10 (Default Port: `8088`).

### 3. Wearable Firmware (Raspberry Pi Pico W)
1. Connect the Raspberry Pi Pico W to your PC using a Micro-USB cable.
2. Open **Thonny IDE** and set the interpreter to **MicroPython (Raspberry Pi Pico)**.
3. Open [`main.py`](file:///c:/Users/chris/Downloads/Smart-Belt_160422121%20-%20Copy/pico_w_firmware/main.py) and update the Wi-Fi & server host configurations:
   ```python
   WIFI_SSID = "Your_WiFi_SSID"        # (# use your own wifi ssid)
   WIFI_PASSWORD = "Your_WiFi_Password" # (# use your own wifi password)
   BACKEND_HOST = "192.168.x.x"       # (# use your own ip / host)
   BACKEND_PORT = 8088
   ```
4. Save the file as `main.py` directly to the Raspberry Pi Pico W root storage.

### 4. Android Mobile App
1. Open the `smartbelt_android` folder in **Android Studio**.
2. Update the default backend IP in [`SessionManager.kt`](file:///c:/Users/chris/Downloads/Smart-Belt_160422121%20-%20Copy/smartbelt_android/app/src/main/java/com/smartbelt/caregiver/data/pref/SessionManager.kt):
   ```kotlin
   const val DEFAULT_BASE_URL = "http://192.168.x.x:8088/" // use your own ip
   ```
3. Build and run the app on a physical Android device or emulator (Android 8.0 Oreo / API 26+ required).

---

## 🔄 Fall Detection Workflow

```text
[Hard Impact / Body Fall]
           │
           ▼
[MPU6050: SVM > 2.70g & Tilt Angle > 50°]
           │
           ▼
[Pico W: Buzzer On + OLED Countdown Timer (10s)]
           │
 ┌─────────┴─────────┐
 │ (User Presses)    │ (Timer Expires)
 │  GP18 Button      │
 ▼                   ▼
[CANCEL ALARM]   [HTTP POST Request to Backend]
                     │
                     ├──> [Telegram Bot Alert to Caregiver Channel]
                     ├──> [Send Email Notification via SMTP]
                     └──> [Android Popup Alert & Auto-Call Activation]
```

---

## 📜 License & Copyright

This project was developed as a Bachelor of Computer Science Undergraduate Thesis at the **University of Surabaya (UBAYA)**.

Copyright © 2026 **Chrisyon Putra Sitania**. All rights reserved.
