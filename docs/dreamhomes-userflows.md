# DreamHomes — User Flow Documentation 🏠

> Making dreams come true, one home at a time. This document captures the full user flows for every actor on the DreamHomes platform — researched from first principles, digitalized from how real estate has worked for centuries.

---

## Platform Philosophy

DreamHomes is a property management platform connecting owners, agents, and applicants. The core niche is **Moniepoint-powered home financing** — making house financing accessible and seamless. The platform is built on transparency, trust, and verified interactions. Every actor has a clear role, clear permissions, and clear visibility into what's happening with their property or their search.

**Monetization vectors:**
- Moniepoint home financing integration (primary)
- Ads — featured listings, featured agent profiles
- Commission tracking on closed deals

---

## Actors

- **Owner** — lists properties for rent or sale, self-manages or assigns an agent
- **Agent** — manages listings on behalf of owners, handles inspections, qualifies leads, closes deals
- **Applicant** — browses, saves, requests inspections, submits expressions of interest and offers
- **Admin** — root access, verification authority, platform configuration, analytics

---

## 1. Owner Flow

### Registration & Identity
- Register with name, email, phone, password
- Account is created immediately but carries an **unverified badge** by default
- Owner can list properties right away — verification is non-blocking
- Submit identity verification (government ID, NIN/BVN reference) — goes to Admin queue
- Admin reviews and grants **Owner Verified badge** — strict, high bar, premium status
- Verified badge is a trust signal like a blue tick — 95% trustworthy signal to the market

### Listing a Property
- Add property details — address, type (apartment, house, land, commercial), bedrooms, bathrooms, size, description
- Set terms — rent or sale flag, asking price, negotiable or fixed, caution fee, service charge, agency fee (if applicable)
- Upload photos metadata, virtual tour link
- Set availability — available now, available from a future date, unavailable
- Submit property ownership documents (Certificate of Occupancy, deed of assignment — metadata only) for **Property Verification**
- Property goes live with an **unverified property badge** until Admin approves documents
- Verified property badge is separate from owner badge — both can exist independently

### Agent Assignment (Optional)
- Owner can choose to self-manage their listing entirely
- Or search and select a verified agent to manage the listing on their behalf
- Agent receives a listing management request and must accept
- If agent is assigned — agent takes operational control but owner retains final authority
- Owner is notified of every significant action the agent takes

### Self-Managing Owner — Full Action Set
- Edit listing details, pricing, terms at any time
- Pause or unpause listing (e.g. travelling, not ready)
- Set available inspection time slots
- View all inspection requests — approve or decline
- View and vet applicant profiles before approving inspections
- See leads dashboard — who saved, who requested inspection, who submitted interest
- Track lead temperature — hot (offer submitted) vs warm (inspection done) vs cold (just saved)
- Reveal applicant contact details for serious leads
- Receive offers — review, counter-offer, accept or reject
- Manage comments on listing — reply to questions, delete inappropriate comments
- See likes and saves count on listing
- Message applicants directly within platform
- Close listing when deal is done — mark as rented or sold

### Monitoring
- Dashboard showing all active listings and their status
- Inspection activity per listing
- Offer history per listing
- Agent activity log (if agent assigned)
- Responsiveness metrics — platform tracks and displays response rate and average response time

---

## 2. Agent Flow

### Registration & Verification
- Register as an agent — name, email, phone, password
- Submit professional credentials — real estate license, means of ID, CAC registration (if running an agency)
- Admin reviews and approves before agent can operate — **stricter than owner verification**
- Agent profile setup — bio, specializations (residential, commercial, luxury), years of experience, locations covered, languages spoken
- **Verified Agent badge** displayed prominently on profile

### Getting Clients (Owners)
- Agent profile is publicly discoverable — owners can search and filter by location, specialization, ratings, deals closed
- Owner sends agent a listing management request
- Agent accepts or declines
- Agent can also proactively reach out to owners on the platform
- Agent can manage multiple owners and multiple listings simultaneously

### Transparency — Fees & Rankings
- Agent fee structure is publicly visible on their profile — no hidden charges
- Ratings from past owners and applicants are visible
- Rankings based on — deals closed, response rate, average time to close, active listings
- Platform enforces full fee transparency — agents cannot charge undisclosed fees

### Managing Listings
- Create and publish listings on owner's behalf
- Owner approves before listing goes live
- Edit listing details, update pricing — major changes require owner approval
- Boost listings via ads — pay to feature a listing for more visibility
- Manage multiple listings from a single dashboard

### Inspections — Core Operations
- See all incoming inspection requests per listing
- Cross-check against owner's available slots
- Confirm, reschedule or decline inspection requests
- Track who showed up and who didn't — no-show history per applicant
- Add post-inspection notes — applicant interest level, feedback, observations
- Update owner after every inspection

### Lead Qualification
- View full applicant profiles for each interested party
- See applicant's platform history — past inspections, offers made, no-show record
- Shortlist strong candidates and present to owner with recommendation
- Flag suspicious or unserious applicants

### Offers & Negotiation
- Receive offers from applicants through platform
- Review offer details and present to owner with professional recommendation
- Negotiate on owner's behalf — counter offers back and forth through platform
- Owner makes final call on acceptance
- Once accepted — flag deal as closed

### After The Deal
- Mark listing as rented or sold
- Request reviews from owner and applicant
- Commission is recorded on platform — deal history builds agent credibility
- Closed deals count displayed on agent profile

### Running Ads
- Promote own agent profile — Featured Agent placement
- Promote specific listings for higher visibility
- Ad spend tracked and managed through platform

### Owner ↔ Agent Interaction
- Owner sends listing management request → agent accepts (handshake)
- Agent creates/edits listing → owner gets notified, approves major changes
- Agent sends post-inspection updates to owner after every viewing
- Agent presents shortlisted applicants to owner for input
- Agent forwards offers with recommendation → owner decides
- Direct in-app messaging between owner and agent — private, documented, stays on platform

### Agent ↔ Applicant Interaction
- Applicant finds listing → sees agent profile attached
- Applicant submits inspection request → agent confirms or reschedules
- Agent tracks inspection completion and follows up on interest level
- Applicant submits offer through platform → agent receives and presents to owner
- Negotiation happens through platform messaging — everything documented
- Deal closes → agent marks listing, both parties leave reviews

**Rule:** Everything stays on platform. No off-platform communication encouraged. This protects all parties and enables dispute resolution.

---

## 3. Applicant Flow

### Public Access (No Account Required)
- Browse all listings freely
- Search and filter properties by location, type, price, availability, bedrooms, etc.
- View full listing details — photos, description, terms, fees, comments
- View agent profiles and their ratings, fees, rankings
- Interact with **Dream AI Agent** for natural language discovery and guidance
- See pricing, market context, availability

### Registered Applicant (Account Required for Actions)
- Save and favourite listings
- Request property inspections
- Submit expressions of interest — rent or buy
- Make offers on listings
- Counter-offer in negotiation
- Message agents or owners directly within platform
- Comment on listings — ask questions publicly
- Track inspection history and offer status in personal dashboard
- Leave reviews on agents and owners after a deal closes

### Verification (Optional but Rewarded)
- Submit ID for applicant verification
- Verified applicant badge — signals seriousness to agents and owners
- Verified applicants get priority consideration from agents and owners
- Not blocking — fully functional platform without verification
- Encouraged but never enforced

### Key Insight
Most users (est. 70%+) are browsers. The platform must be excellent in discovery mode. Dream AI Agent is the conversion bridge — a browser chats with it, gets value, and naturally creates an account to save preferences and book an inspection.

---

## 4. Dream AI Agent Flow

### What It Is
A conversational discovery and guidance layer. Not an agent replacement — a knowledgeable friend who knows the market deeply.

### Discovery Capabilities
- Natural language search — "3 bedroom in Lekki under 2 million, quiet street, close to a school"
- Preference learning — understands what you actually want vs what you say you want over time
- Smart recommendations based on search history, saved listings, inspection patterns
- Market insights — is this listing above or below area average?
- Availability matching — finds properties ready for your move-in timeline
- Side by side comparison — here are 3 matching properties, here's how they differ

### Guidance Capabilities
- Answers questions about a listing pulling from description, comments, and platform data
- Educates first time renters and buyers — what is caution fee, what to look out for at inspection, what documents to request
- Flags if a deal looks suspicious or overpriced based on market data
- Guides applicant through the full process step by step

### What It Cannot Do
- Replace human negotiation
- Verify physical property condition
- Handle legal documentation
- Make any transactional decision

### Capstone Scope
Discovery and guidance only. Advanced personalization and market intelligence noted as future vision.

---

## 5. Admin Flow

### Access & Setup
- First admin is seeded — no self-registration for admins ever
- Can create and manage other admin accounts with tiered permission levels
- Full audit trail of every admin action on the platform

### Platform Configuration
- Verification requirements and badge criteria
- Commission rate settings
- Inspection conflict rules
- Ad pricing and duration settings
- Location and property category management
- SLA and response time benchmarks

### User Management
- View all users across every role
- Activate, deactivate, suspend any account
- Override and undo any action on the platform
- View full activity history of any user
- Resolve disputes between any parties

### Verification Queue
- Owner identity verification queue — approve, reject, request more info
- Property document verification queue — approve, reject, request more info
- Agent credential verification queue — approve, reject, request more info
- Full audit trail of every verification decision made

### Listing Management
- View all listings — live, pending, paused, closed
- Approve or reject new listings before they go live
- Take down any listing at any time
- Override listing status
- Manage featured/promoted listings

### Inspection Oversight
- Full visibility into all inspection requests platform-wide
- Intervene in scheduling conflicts
- View no-show history per applicant or agent

### Offers & Deals
- Full visibility into all offers and deal statuses
- Flag suspicious offer patterns
- Officially mark deals as closed on the platform

### Dream AI Agent Management
- Configure and update AI agent behavior
- View conversation logs for quality and safety assurance
- Flag and review problematic interactions

### Analytics & Reporting
- Platform-wide dashboard — active listings, total users per role, inspection volume, deal close rate
- Agent performance rankings — deals closed, response rate, average time to close, ratings
- Property analytics — most viewed, most saved, highest offer activity
- Revenue tracking — ads, commissions, featured placements
- Location-based insights — hottest areas, price trends

### Content Moderation
- Review and remove flagged comments
- Moderate listing content — photos metadata, descriptions
- Handle reported users and listings

### Ads Management
- Approve or reject ad requests from agents and owners
- Set and update ad pricing and duration
- View ad performance metrics per listing or agent

---

## Notes & Future Vision

### Capstone Scope (What We're Building Now)
- Full Owner, Agent, Applicant, Admin flows as documented
- Dream AI Agent — discovery and guidance only
- Moniepoint financing — noted as core niche, integration scoped for future
- Ads system — basic featured listing/agent, full ad management future
- Messaging — in-app contact reveal minimum, full chat system future

### Future Vision
- Moniepoint financing integration — applicant applies for home financing directly through platform
- Full in-app real-time messaging
- Advanced Dream AI Agent — preference learning, market intelligence, deal fairness scoring
- Virtual tour integration
- Rental payment tracking
- Legal document templates and e-signing
- Mobile apps (iOS + Android)
- Collaborative editing and multi-agent team accounts
- Full ad network with performance analytics

### Key Design Principles
- **Transparency first** — agent fees, verification status, ratings all public
- **Trust through verification** — non-blocking but highly rewarded
- **Everything on platform** — no off-platform communication encouraged
- **Discovery is free** — public browsing with no friction
- **Dream AI Agent as conversion bridge** — turns browsers into registered users

---

*DreamHomes — Moniepoint DreamDev Bootcamp Capstone 2025*
*Built from first principles. Digitalized from centuries of real estate practice.*
