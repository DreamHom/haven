# DreamHomes — Product Requirements Document

**Version:** 1.0  
**Date:** May 2026  
**Program:** Moniepoint DreamDev Bootcamp Capstone 2026  
**Team:** DreamHomes  
**Repos:** `haven` (backend) · `vista` (frontend)

---

## 1. Product Vision

> *"Making dreams come true, one home at a time."*

Housing is one of the most fundamental human needs — and one of the most broken experiences in modern markets. DreamHomes is a property management platform that digitizes the centuries-old process of finding, listing, and transacting on properties. It connects **owners**, **agents**, and **applicants** in a transparent, trust-first environment.

The core niche is **Moniepoint-powered home financing** — making property acquisition accessible to people who have the dream but not the immediate capital. DreamHomes is not just a listing platform. It is a dream delivery machine.

---

## 2. Problem Statement

Real estate transactions — especially in markets like Nigeria — are plagued by:

- **Fraud** — fake listings, fake agents, unverified ownership documents
- **Opacity** — hidden agent fees, undisclosed charges, no visibility into deal progress
- **Friction** — everything happens off-platform via WhatsApp and phone calls, leaving no paper trail
- **Inaccessibility** — financing options are disconnected from the discovery process
- **Poor discovery** — search is rigid, filter-based, and doesn't understand what people actually want

DreamHomes solves all five.

---

## 3. Target Users

| Actor | Who They Are |
|---|---|
| **Owner** | Property owners looking to rent out or sell their properties |
| **Agent** | Licensed real estate professionals managing listings on behalf of owners |
| **Applicant** | Individuals looking to rent or purchase a property |
| **Admin** | Platform operators with root-level access to configure, verify, and moderate |

---

## 4. Core Features

### 4.1 Property Listings
- Owners create listings with full property details, pricing, terms, and availability
- Listings are live immediately with an **unverified badge** — verification is non-blocking
- Two separate verification tracks: **owner identity** and **property documents**
- Verified badges are strict, high-bar, premium trust signals — like a blue tick
- Listings support photos metadata, virtual tour links, and detailed fee breakdowns (caution, service charge, agency fee)

### 4.2 Agent Management
- Agents register with professional credentials — reviewed and approved by Admin before they can operate
- Agent profiles are fully transparent — fees, ratings, deals closed, specializations, locations covered, response rate
- Owners can search, filter, and assign agents to manage their listings
- Agent assignment is optional — owners can fully self-manage
- Agents can manage multiple listings across multiple owners simultaneously

### 4.3 Inspection System
- Applicants request inspections on listings they are interested in
- Owners (or assigned agents) manage available inspection slots
- Conflict prevention — no two inspections can be scheduled for the same property at the same time
- Post-inspection notes logged by agent or owner
- No-show tracking per applicant
- Every inspection request triggers an async notification to the assigned agent or self-managing owner via Kafka

### 4.4 Expressions of Interest & Offers
- Applicants submit expressions of interest — rent or buy — on listings
- Full offer submission with amount and terms
- Counter-offer capability — negotiation happens within platform
- Owner has final say on all offers
- If agent is assigned — agent presents offers with recommendation, owner decides

### 4.5 Leads & Pipeline
- Owners and agents see a full leads dashboard per listing
- Lead temperature tracking — saved (cold), inspection requested (warm), offer submitted (hot)
- Applicant profiles visible to owners and agents before approving inspections
- Contact reveal for serious leads

### 4.6 Comments & Community
- Public comments section on every listing
- Applicants ask questions publicly — reduces repetitive inquiries
- Owners and agents can reply and delete comments on their listings
- Likes and saves visible on listings

### 4.7 Dream AI Agent
- Conversational discovery layer — natural language property search
- "Find me a 3 bedroom in Lekki under 2 million, quiet street, near a school"
- Smart recommendations based on preferences and interaction history
- Market insights — is this listing above or below area average?
- Educates first-time renters and buyers on the process, fees, and what to look out for
- Answers listing-specific questions by pulling from listing data and comments
- Acts as the conversion bridge — turns public browsers into registered users
- **Capstone scope:** Discovery and guidance only

### 4.8 Verification System
- **Owner Identity Verification** — government ID, NIN/BVN reference
- **Property Document Verification** — Certificate of Occupancy, deed of assignment
- **Agent Credential Verification** — real estate license, CAC registration
- **Applicant Verification** — optional ID submission, rewarded with a trust badge
- All verification queues managed by Admin
- Full audit trail of every verification decision

### 4.9 Messaging & Communication
- In-app messaging between owners and agents
- In-app messaging between agents/owners and applicants
- Everything stays on platform — no off-platform communication encouraged
- Full message history available for dispute resolution

### 4.10 Admin & Platform Operations
- Seeded admin account — no self-registration
- Tiered admin permissions
- Full user management — activate, deactivate, suspend, override
- Verification queue management across all three tracks
- Listing approval, takedown, and override
- Ads management — approve, price, and track featured listings and featured agent placements
- Platform-wide analytics dashboard
- Content moderation — comments, listing content, reported users
- Full audit trail of all admin actions

---

## 5. User Flows Summary

> Full user flows documented in `dreamhomes-userflows.md`

| Actor | Key Journey |
|---|---|
| **Owner** | Register → List Property → Verify (optional but rewarded) → Assign Agent or Self-Manage → Handle Inspections → Review Offers → Close Deal |
| **Agent** | Register → Submit Credentials → Get Verified → Build Profile → Accept Listings → Manage Inspections → Close Deals → Build Reputation |
| **Applicant** | Browse Freely → Interact with Dream AI → Register → Save Listings → Request Inspections → Submit Offers → Close Deal |
| **Admin** | Seed Access → Configure Platform → Manage Verification Queues → Approve Listings → Moderate Content → Monitor Analytics |

---

## 6. Non-Functional Requirements

### Security
- JWT-based authentication with role-based access control
- No admin self-registration — seeded only
- All sensitive document references stored as metadata only — no raw file storage in DB
- Full audit trails for admin and verification actions
- On-platform communication to protect all parties

### Performance
- Inspection conflict prevention must be handled at the data layer — no race conditions
- Kafka used deliberately and surgically — only where async decoupling is genuinely justified
- Public listing discovery must be fast — no auth required, highly cacheable

### Trust & Transparency
- Agent fees always publicly visible
- Verification badges always visually distinct and honestly communicated
- Responsiveness metrics (response rate, average response time) displayed on owner and agent profiles
- No hidden charges anywhere on the platform

### Scalability (Future)
- Architecture designed to support Moniepoint financing integration
- Messaging infrastructure ready to evolve into real-time chat
- Dream AI Agent designed to support preference learning and market intelligence layers

---

## 7. Domain Events (Kafka)

### Why Kafka — and Where

Kafka is a distributed event streaming system. At its core it is a reliable, ordered queue that decouples the thing that fires an event from the things that react to it. A service publishes an event, Kafka holds it, and any consumer that cares picks it up independently — without blocking the original action.

At DreamHomes' scale, Kafka is not needed for speed or throughput. We are not processing millions of transactions per second. We use it deliberately in exactly two places where **missing the communication is the most costly failure in the entire system** — a missed inspection or a missed offer means a deal dies. That is the real problem Kafka solves for us.

All other notifications — listing approvals, verification updates — are straightforward request/response flows handled with a simple notification record in the database. No need to over-engineer them.

### The Two Kafka Events

| Event | Trigger | Producer | Consumer | Why Kafka |
|---|---|---|---|---|
| `INSPECTION_REQUESTED` | Applicant submits an inspection request | Inspection Service | Notification Service | Agent or owner must be notified reliably and asynchronously. The applicant's request should not wait for the notification to deliver before getting a response. A missed notification = a missed deal. |
| `OFFER_SUBMITTED` | Applicant submits a formal offer on a listing | Offer Service | Notification Service | Highest stakes event on the platform. Owner or agent must know reliably. Failure here = deal lost, trust destroyed. Async delivery ensures the offer is recorded instantly and notification is guaranteed. |

### Everything Else
All other system notifications are handled via direct database records and synchronous response flows. Simple, honest, appropriate for the scope.

---

## 8. Core Entities

| Entity | Description |
|---|---|
| `User` | Base entity for all actors — role determines permissions |
| `Property` | The physical property with details and ownership |
| `Listing` | The active market listing for a property — rent or sale |
| `InspectionRequest` | An applicant's request to view a property |
| `InspectionSlot` | Available time windows for inspections on a listing |
| `Offer` | A formal offer submitted by an applicant on a listing |
| `Verification` | Document/identity verification submission and status |
| `Comment` | Public question or note on a listing |
| `Notification` | Platform notification triggered by domain events |
| `AgentListing` | Junction — agent assigned to manage a listing |

---

## 9. Out of Scope (Capstone)

These are acknowledged, designed for, but not built in this iteration:

- Moniepoint financing integration
- Real-time messaging (contact reveal minimum for capstone)
- Full ad network with performance analytics
- Virtual tour integration
- Rental payment tracking
- Legal document templates and e-signing
- Mobile applications
- Advanced Dream AI Agent — preference learning, market intelligence

---

## 10. Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.3.x, Java 21 |
| Database | PostgreSQL |
| Messaging | Apache Kafka |
| Auth | JWT + Role-Based Access Control |
| Frontend | Next.js 14, TypeScript, Tailwind CSS |
| Infrastructure | Docker Compose (app + DB + Kafka + Zookeeper) |
| Testing | TDD — tests written before implementation, no exceptions |
| Package Manager (FE) | pnpm |

---

## 11. Development Philosophy

- **TDD throughout** — every service method has a test written before implementation
- **First principles thinking** — every feature traced back to how real estate actually works in the physical world, then digitalized
- **Transparency by default** — if information should be public, it is public
- **Trust is earned, not assumed** — verification is non-blocking but meaningfully rewarded
- **Everything on platform** — no feature should push users off-platform to complete an action

---

*DreamHomes — Moniepoint DreamDev Bootcamp Capstone 2026*  
*Built from first principles. Digitalized from centuries of real estate practice.*
