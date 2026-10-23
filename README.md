# GateBuddy — GATE CSE Preparation Command Center

GateBuddy is a mission-critical web companion and study platform engineered for GATE CSE aspirants, featuring real-time bidirectional synchronization with the Android application, strict recovery and backlog scheduling rules, and an authoritative PostgreSQL/Supabase schema.

![GateBuddy](public/cropped_circle_image.png)

---

## 🚀 Key Features

- **Authoritative Syllabus Engine**: 690 canonical curriculum tasks spanning Oct 19, 2026 to Sep 28, 2027.
- **Subject Prior Completion Offsets**: Canonical offsets for 22 subjects (DL: 41 prior, CN: 18 prior, DBMS: 27 prior).
- **Rule 1 & Rule 2 Enforcement**: Maximum 2 lectures per day and no duplicate subjects on the same date.
- **Real-Time Bidirectional Sync**: Instant state reflection across Android and Web without manual page refreshes.
- **Master Security Authorization**: Account modifications protected by the Master Security Code.
- **Comprehensive Assessment Tracking**: Mock test logging with 4-category mistake analysis.

---

## 🔐 Credentials & Master Security

- **Username**: `katurtusridhar`
- **Password**: `Sridhar@758`
- **Master Security Code**: `oA-THL,UXyh6` (Required to authorize username or password modifications)

---

## 🛠 Local Development

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

## 🧪 Testing

```bash
npm test
npm run lint
npm run build
```

---

## 🌐 Deploying to Vercel

1. Push this repository to GitHub under the name `GateBuddy`.
2. In Vercel, click **Add New Project** and select `GateBuddy`.
3. Framework Preset: **Next.js**.
4. Click **Deploy**.
