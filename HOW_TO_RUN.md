# 🚀 How to Run Calendly-Lite — Complete Step-by-Step Guide

> **Time to run:** ~2 minutes  
> **Skill level:** Beginner friendly  
> **OS:** Windows

---

## What You Need (Prerequisites)

Before you start, make sure these are installed on your computer:

| Requirement | Version on this PC | Check it |
|-------------|-------------------|----------|
| Java JDK | 17 (installed at `C:\Program Files\Java\jdk-17`) | Open CMD → `java -version` |
| PostgreSQL | 17 (Windows Service, auto-starts) | Runs automatically |
| Redis | Latest (Windows Service, auto-starts) | Runs automatically |
| Maven | 3.9.6 (downloaded to Temp folder) | Use the run script below |

---

## Option A — One-Click Launch (Recommended) ⚡

We have a script that does everything automatically.

### Step 1 — Open PowerShell as Administrator

Right-click the **Start button** → click **"Windows PowerShell"** (or "Terminal")

### Step 2 — Navigate to the project folder

```powershell
cd "C:\Users\vicky\Desktop\Hackathon\calendly-lite"
```

### Step 3 — Run the launch script

```powershell
.\run.ps1
```

That's it! The script will:
1. ✅ Check PostgreSQL is running
2. ✅ Check Redis is running  
3. ✅ Set Java 17 as the active JDK
4. ✅ Start the Spring Boot application
5. ✅ Open the Swagger UI in your browser automatically

---

## Option B — Manual Step-by-Step

If you prefer to run each step yourself:

---

### Step 1 — Check PostgreSQL is Running

PostgreSQL is set to **auto-start on Windows boot**. To verify it's running:

**Option A:** Press `Win + R` → type `services.msc` → press Enter  
→ Find `postgresql-x64-17` in the list → Status should say **"Running"**

**Option B:** Run this in PowerShell:
```powershell
Get-Service postgresql-x64-17
```
Expected output: `Status = Running`

If it's **stopped**, start it:
```powershell
Start-Service postgresql-x64-17
```

---

### Step 2 — Check Redis is Running

Redis is also set to **auto-start on boot**. Verify in PowerShell:

```powershell
Get-Service Redis
```
Expected output: `Status = Running`

If it's **stopped**, start it:
```powershell
Start-Service Redis
```

---

### Step 3 — Open a Terminal in the Project Folder

1. Open **File Explorer**
2. Navigate to: `C:\Users\vicky\Desktop\Hackathon\calendly-lite`
3. Click the address bar at the top, type `powershell`, press **Enter**

A PowerShell window opens already inside the project folder. ✅

---

### Step 4 — Start the Application

Copy and paste this entire block into PowerShell and press Enter:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& "C:\Users\vicky\AppData\Local\Temp\maven\apache-maven-3.9.6\bin\mvn.cmd" spring-boot:run
```

**What you'll see:**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
...
Started CalendlyLiteApplication in 14.8 seconds
```

When you see `Started CalendlyLiteApplication` — the app is ready! ✅

> ⏱️ First startup takes ~15 seconds. Subsequent starts are faster.

---

### Step 5 — Open the App in Your Browser

Open any web browser and go to:

```
http://localhost:8080/swagger-ui.html
```

You'll see the **Calendly-Lite API Dashboard** with all endpoints ready to test. 🎉

---

## Testing the App (Quick Demo)

Once the Swagger UI is open, follow these steps to see all 7 patterns working:

### 🔵 1. List Demo Hosts
- Click **`GET /api/hosts`** → **Try it out** → **Execute**
- You'll see: **Dr. Priya Sharma** and **Alex Mehta** (pre-loaded on startup)

### 🟢 2. See Available Slots (Redis Cached)
- Click **`GET /api/hosts/{id}/slots`** → **Try it out** → set `id = 1` → **Execute**
- Shows Dr. Priya's open time slots. *(Cached in Redis for 5 minutes)*

### 🟡 3. Book a Slot
- Click **`POST /api/slots/{id}/book`** → **Try it out**
- Set `id = 1`
- Set `Idempotency-Key = my-first-booking`
- Set Request Body:
  ```json
  {
    "guestName": "Your Name",
    "guestEmail": "you@email.com"
  }
  ```
- Click **Execute** → You get **201 Created** ✅

### 🔄 4. Test Idempotency (No Duplicate)
- Click **Execute** again *without changing anything*
- → Returns the **same booking** — not a new one! *(Idempotency working)*

### ❌ 5. Test Double-Booking Prevention
- Change `Idempotency-Key` to `different-key-999`
- Click **Execute**
- → **409 Conflict**: Slot is already booked *(Distributed Lock working)*

### 📬 6. View Notification Events (Outbox Pattern)
- Click **`GET /api/admin/outbox-events`** → **Try it out** → **Execute**
- See the `BOOKING_CONFIRMED` event with `status: SENT` *(Outbox + Retry working)*

### 💀 7. View Dead Letter Queue
- Click **`GET /api/admin/dead-letter-events`** → **Try it out** → **Execute**
- Shows any notifications that failed all 3 retry attempts

### 🗑️ 8. Cancel a Booking
- Click **`DELETE /api/bookings/{id}`** → **Try it out** → set `id = 1` → **Execute**
- → Booking cancelled, slot freed back to **OPEN**, Redis cache evicted

### 🚫 9. Test Rate Limiting
- Book the same endpoint 6 times rapidly with different Idempotency-Keys
- → On the 6th attempt: **429 Too Many Requests** *(Rate Limit: 5 per minute)*

---

## How to Stop the App

Go back to the PowerShell window and press:

```
Ctrl + C
```

The server shuts down safely.

---

## Troubleshooting

### ❓ "Port 8080 already in use"

The app is already running. Either:
- Go to the existing terminal and press `Ctrl + C` to stop it first, OR
- Just open `http://localhost:8080/swagger-ui.html` — it's already working!

```powershell
# Find what's using port 8080
netstat -ano | findstr :8080

# Kill it (replace 12345 with the PID from above)
Stop-Process -Id 12345 -Force
```

### ❓ "Cannot connect to database"

PostgreSQL might have stopped. Restart it:
```powershell
Start-Service postgresql-x64-17
```

### ❓ "Redis connection refused"

Redis might have stopped. Restart it:
```powershell
Start-Service Redis
```

### ❓ "java is not recognized"

The Java path isn't set. Make sure you include the JAVA_HOME lines from Step 4.

### ❓ App starts but Swagger shows blank page

Wait 5 more seconds and refresh. The Swagger UI loads after the app fully initializes.

---

## What Happens on First Start

When you run the app for the very first time, it automatically:

1. **Runs 5 database migrations** (Flyway creates all 5 tables)
2. **Seeds demo data**: 2 hosts + 5 time slots
3. **Starts the outbox worker** (checks for pending notifications every 5 seconds)

On every subsequent start, it skips the seeding step (data already exists).

---

## Quick Reference

| URL | What it opens |
|-----|--------------|
| `http://localhost:8080/swagger-ui.html` | Interactive API explorer |
| `http://localhost:8080/api-docs` | Raw JSON API spec |
| `http://localhost:8080/api/hosts` | List all hosts (JSON) |
| `http://localhost:8080/api/admin/outbox-events` | View notification events |
| `http://localhost:8080/api/admin/dead-letter-events` | View failed notifications |

---

## Project File Reference

```
calendly-lite/
├── run.ps1                  ← One-click launch script (Windows)
├── HOW_TO_RUN.md            ← This file
├── README.md                ← Full architecture & API docs
├── docker-compose.yml       ← Alternative: start with Docker
└── src/                     ← All Java source code
```
