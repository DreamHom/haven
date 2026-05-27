# DreamHomes — Slides Content (ready to paste into Google Slides)

Extracted from `/Users/lukasio/Downloads/dreamhomes-final-script.md`. For each slide:
- **On the slide** = the visual text + visual description (paste this into the slide itself)
- **Speaker notes** = the narration (paste into the speaker-notes pane below each slide)

Total 8 slides, 15 min presentation + 5 min Q&A.

> **Source of truth:** if you change a slide here, also update the underlying script so they stay aligned. The script in `~/Downloads/dreamhomes-final-script.md` is the canonical version.

---

## Slide 1 — Title

### On the slide

```
DreamHomes
A property platform built on trust.

Wisdom (Frontend + Product)
Silas (Backend)

Tuesday, 26 May 2026
github.com/DreamHom/haven
```

### Visual

- DreamHomes logo (large, centred)
- Tagline directly below in lighter weight
- Team names and roles in two-column layout under the tagline
- Date + repo link bottom-right in small text

### Speaker notes

> "Good afternoon. We're Team DreamHomes."
>
> "I'm Wisdom — I led the frontend and product. This is Silas — he led the backend."
>
> "Over the next 15 minutes, we'll show you something we built that we genuinely believe could change how Nigerians find a home."
>
> "But before we show you what it is — we want you to meet someone."

*Pause two seconds before speaking. Click to slide 2 on the last line.*

---

## Slide 2 — The Reality

### On the slide

```
The Reality
```

That's it. Single title, quiet illustration in the background, dark frame. Let the words land.

### Visual

- Dark background
- Small illustration (e.g. silhouettes of a mother + daughter, or a closed front door)
- Single brand-colour accent line
- Minimal text — story lives in the narration

### Speaker notes (narrative — 5 sections, ~3 min total)

**Opening**

> "Meet Ngozi."
>
> "She's been raising her daughter Diane alone since Diane was nine. For two years, they slept on couches in friends' houses. Ngozi always thanking them. Always pretending it wasn't as heavy as it was. When Diane turned eleven, they finally got a one-bedroom flat of their own."

**The job and the dream**

> "For five years, that one-bedroom was home. A friend let her have it below rent. Diane grew up in that room. She did her homework on the floor. She learned to braid her own hair sitting on her mother's bed."
>
> "Ngozi got a real job. Catering. Weddings, birthdays, trays of small chops. The kind of money that lets a mother start thinking properly."
>
> "Next year, Diane turns seventeen. Ngozi wants her to have her own room before that birthday."

**The search and the find**

> "Ngozi started looking. A two-bedroom. Closer to work. She'd been saving for months."
>
> "The journey was rough. Tens of hours on the internet. Strangers on WhatsApp. Property after property. Inspections that led nowhere. Listings she had to avoid because they were obviously scams."
>
> "Then she found it. A beautiful two-bedroom in Surulere. Ten minutes from her shop. An amazing neighbourhood. Diane loved it. So did Ngozi."
>
> "It was way beyond her budget. But as a mother, she did her best. She paid."

**Moving day**

> "They packed everything. Moving day was a Saturday. They got there bright and early. A small bus. Suitcases. A mattress. A kettle. Diane was holding her birthday present in a Shoprite bag."
>
> *[Pause.]*
>
> "There was another family at the gate."
>
> "Also with suitcases. Also with a key."
>
> *[Two seconds of silence.]*
>
> "They had been scammed. Ngozi called the agent. The number was switched off. She called every day for two weeks. He never picked up."

**The pivot to data**

> "This isn't a made-up story. In November 2024, four families showed up at three flats in Port Harcourt. A man calling himself a caretaker had taken one point two million naira from each of them. He was gone before they arrived."
>
> "One in five property transactions in Lagos involves fraud. The Lagos government recovered four hundred and seventy-eight million naira from property scams in 2024 and 2025. That's just what they recovered."
>
> "Six thousand families this year will live this exact moment."
>
> "That's the problem we set out to solve."

---

## Slide 3 — The Solution in One Screen

### On the slide

```
Every owner. Every agent.
Verified — or visibly not.
```

### Visual

- Two listing-card mockups side by side
- Left card: normal listing with a green "Verified" badge
- Right card: same listing layout but with a clear "⚠️ Possible Scam — owner identity not verified" label across it
- Brand-colour accent line at the top of the slide
- Single sentence (above) as the headline

### Speaker notes

> "This is what Ngozi would have seen on DreamHomes."
>
> "Two listings. Same price. Same neighbourhood."
>
> "One is from someone we know. One is from someone who couldn't be bothered to prove who they are."
>
> "On DreamHomes, every owner and every agent uploads a government ID. They do a liveness check. Until they do — every listing they post carries a warning."
>
> "Ngozi wouldn't have paid that agent. Because she would have known."

---

## Slide 4 — Architecture and Stack

### On the slide

Architecture diagram (clean, readable from the back of a room):

```
   ┌────────────────────────┐
   │  Vista (Next.js, SSR)  │
   │  vista.dreamhomes.today│
   └───────────┬────────────┘
               │ HTTPS · JWT
               ▼
   ┌──────────────────────────────────────┐
   │  Haven (Spring Boot 3, Java 21)      │
   │  haven.dreamhomes.today              │
   └──┬────────────┬────────────┬─────────┘
      ▼            ▼            ▼
   PostgreSQL  Confluent      Cloudflare R2
   +pgvector   Cloud Kafka   (listing photos)
```

### Stack labels (bottom of slide, three columns)

| Frontend | Backend | Infra |
|---|---|---|
| Next.js (App Router) | Spring Boot 3 / Java 21 | PostgreSQL 16 |
| TanStack Query | JJWT (RS256) | Confluent Cloud (Kafka) |
| Tailwind | Flyway + JPA | Cloudflare R2 |
| Vercel | Railway | GitHub Actions CI/CD |

### Speaker notes

> "This is DreamHomes under the hood."
>
> "Two services. One frontend — Vista. One backend — Haven. They talk over HTTPS. Haven owns the database, the auth, and the events."
>
> "Spring Boot 3 for the backend. We picked it because the team had real production experience with it — not because it was popular. Java's type system catches a lot of property and money bugs at compile time, which matters when you're moving rent."
>
> "PostgreSQL for the data. Real estate is relational by nature — owners have properties, properties have listings, listings have inspections. We didn't need a document store. We needed constraints we could trust."
>
> "Confluent Cloud for Kafka. We didn't run our own broker. Confluent's free tier was enough for the events we needed, and not running Kafka ourselves meant we could focus on the events themselves instead of cluster health."
>
> "Next.js with the App Router for the frontend. Server components for the listings feed, client components for the inspection flow. Fast first paint on public pages, full interactivity on booking surfaces."
>
> "Everything is containerised. CI/CD on GitHub Actions. Deployed live — Vista on Vercel, Haven on Railway."
>
> "One thing worth naming — we kept the system small on purpose. Two services, not seven. Because the panel that grades a capstone shouldn't have to debug our microservices on demo day."

---

## Slide 5 — Engineering Decisions and Trade-offs

### On the slide

Two decision cards, side by side:

**Card 1 — Listings go live the moment they're created**
- Picked: instant publication with visible "unverified" warning
- Not picked: admin queue blocking every listing
- Cost: fraudulent listings live briefly before takedown
- Mitigation: community report button + admin moderation queue

**Card 2 — Kafka events keyed by listing ID**
- Picked: per-listing partition (every event for one listing → same partition, in order)
- Not picked: per-event partition (max parallelism)
- Cost: one viral listing could bottleneck its partition
- Mitigation: at our scale, correctness > throughput

### Speaker notes

> "Every engineering decision has a winner in some contexts and a loser in others. We want to walk through two of ours. What we picked. What we didn't pick. And what we paid for the choice."

**Decision 1 — Listings go live the moment they're created**

> "Verification is non-blocking on DreamHomes. That was a deliberate decision."
>
> "The alternative was the safe choice. Every listing waits in an admin queue. No one sees it until a human approves it. That's how property platforms in regulated markets work. It's also how marketplaces that never reach critical mass die."
>
> "We picked the opposite. The moment an owner uploads a listing, it's live. It's public. Anyone can find it. What changes is what the platform says about it."
>
> "If the owner hasn't verified their identity, every listing they post carries a warning. The signal is loud. The decision is the user's."
>
> "The cost: a fraudulent listing can be public for a window before we take it down. We accept that cost. We built two things to shrink the window — a community reporting button on every listing, and an admin moderation queue that surfaces fresh reports as they come in."
>
> "If we had picked the safe choice, DreamHomes wouldn't have a supply problem. It wouldn't have any supply at all."

**Decision 2 — Kafka events keyed by listing ID**

> "Our system fires two events to Kafka — inspection requested, and offer submitted. Both are critical. Missing one means a missed deal."
>
> "Kafka splits its queue into partitions for parallelism. So the question is — what key decides which partition an event lands in?"
>
> "The standard answer is the most granular ID possible. The inspection ID. The offer ID. Maximum parallelism, every event flows independently."
>
> "We picked the opposite. Every event for a listing — every inspection request, every offer, every counter — goes to the same partition. Same lane. Same order they were produced."
>
> "Property transactions tell a story. Temi requests an inspection. Emeka accepts. Temi submits an offer. Emeka counters. Temi accepts. If those events arrive out of order on the consumer side, the audit log becomes fiction. The notifications make no sense. And when something goes wrong, debugging becomes guesswork."
>
> "The cost is real. If a listing goes viral, its partition becomes a bottleneck while the rest sit idle. We're not Twitter. We're never going to see a listing receive ten thousand offers in an hour. At our scale, correctness costs us nothing. So we bought it."

**Closing**

> "Both decisions have the same shape. We picked the option that loses in the general case. We picked the option that wins for the system we're building. That's the trade-off we made twice."

---

## Slide 6 — What Was Hard

### On the slide

Visual showing the race condition resolved:

```
Adaeze 14:32:08.412  ──┐
                       ├──→ Same slot. Same Saturday.
Babatunde 14:32:08.419 ┘

Before: both succeed (race condition)
After:  Postgres EXCLUDE USING GIST → one passes, one rejected
```

Below the diagram: `"The application no longer asks for permission. It asks for forgiveness — and the database tells it no."`

### Visual

- Top half: two arrows pointing to the same calendar slot, both labelled "Confirmed" (the bug)
- Bottom half: database icon with a constraint symbol, one arrow gets through, one bounces off
- The line at the bottom in italic / quote style

### Speaker notes (the 6-act story — Symptom → First guess → What we found → Root cause → Fix → Verification)

**Symptom**

> "Our test suite has fake users in it. Real names, real behaviors. Ngozi browses anonymously. Adaeze books inspections. Babatunde submits offers. Amaka manages her properties. Each one is a script that runs against the live system."
>
> "One Saturday, the system did something it shouldn't have."
>
> "Adaeze and Babatunde both got confirmation messages for the same inspection slot. Same property. Same agent. Same time. Same Saturday afternoon."
>
> "The slot was supposed to be taken the moment Adaeze claimed it. Babatunde's request should have been rejected. It wasn't."

**First guess**

> "We assumed Adaeze's claim hadn't been saved by the time Babatunde's request arrived. Maybe the save was slow. Maybe the read was stale."
>
> "So we added timestamps. We added logs. We ran the simulation again."

**What we actually found**

> "Both requests went through our application cleanly. Both read the slot. Both saw it was free. Both inserted."
>
> "The logs told the story. Adaeze's request arrived at 14:32:08.412. Babatunde's arrived at 14:32:08.419. Seven milliseconds apart."
>
> "The bug wasn't a missing save. It was a race."

**Root cause**

> "Our check was the shape every backend engineer has written a hundred times. Read the slot. See if it's free. Insert if it is. But between the read and the insert — even seven milliseconds — anything can happen. Babatunde's request walked straight through the door Adaeze hadn't finished closing."
>
> "We were validating in the wrong place. Our application thought it was the gatekeeper. It wasn't."
>
> "In real estate, a double-booked inspection isn't a small bug. It's the exact kind of betrayal the platform exists to prevent."

**The fix**

> "We moved the check into the database itself."
>
> "PostgreSQL has a feature called a GIST exclusion constraint. Think of it as a bouncer that lives inside the database. Every time a row tries to come in, it checks — does this time range overlap with any existing booking on this listing? If yes, the row is rejected before it's ever saved."
>
> "It's atomic. The check and the write happen as one operation. Race conditions stop being a possibility, not because we caught them — because they can't exist."
>
> "The application no longer asks for permission. It asks for forgiveness — and the database tells it no."

**Verification**

> "We re-ran the simulation. Babatunde's request failed with the right error. Adaeze kept her slot."
>
> "Then we stress-tested it. Twenty parallel requests for the same slot, fired in the same millisecond. One succeeded. Nineteen rejected. Every time."
>
> "The lesson we took from that week was this: In a system where trust is the product, the database is the last line of defense. So we made sure it was the strongest one."

---

## Slide 7 — Learnings and What's Next

### On the slide

Three lesson cards side by side, then a single line below:

**Lesson 1 — Trust is structural, not a feature.**
**Lesson 2 — Test the way users behave.**
**Lesson 3 — Saying no was the hardest work.**

`What's next: Moniepoint financing integration · Product personas (security, compliance, legal)`

### Visual

- Three columns of equal width with the lesson cards
- A horizontal divider below
- "What's next" as the section header for the roadmap line
- Brand-colour accents at the top of each card

### Speaker notes

> "Three things we walk away from this build with."
>
> "The first is about trust. We came in thinking it was a feature we'd build — a verified badge, a verification flow. We left understanding it's not a feature at all. It's a property of the whole system. The UI can claim someone is verified. The API can return a badge. But if the database doesn't enforce the rule, none of it matters. Trust either lives in every layer, or it doesn't live anywhere."
>
> "The second is about testing. We've both written tests for years. But this was the first time we wrote tests that behaved like users — not just unit tests that prove a function works. Adaeze and Babatunde aren't just names in our test files. They have full days. They browse, they hesitate, they make offers, they collide with each other. That kind of testing finds bugs that pure code coverage never will. Bruno was the tool that made it possible — and honestly, we only discovered it during this build."
>
> "The third is harder to admit. Saying no was the hardest engineering work we did. Counter-offers with full negotiation chains. Real-time messaging. Tiered admin roles. Every one of those was a feature we wanted. Every one of them would have cost us a week we didn't have. The system we shipped is the one we could stand behind today. The features we kept earned their place."
>
> "As for what's next — if we had four more weeks, we'd integrate Moniepoint financing. Right now, DreamHomes can show Ngozi a home she can trust. With Moniepoint, the platform could help her afford it."
>
> "We'd also deepen our testing. Today our personas are users. Next, we'd add product personas. A security engineer probing for the seams. A compliance specialist checking whether our verification meets regulatory standards. A real estate lawyer reading the agreements."
>
> "Testing isn't done when users are happy. Testing is done when the experts whose job is to find flaws can't find any."

---

## Slide 8 — Live Demo (starter draft — script's pending)

### On the slide

```
Live Demo

Temi requests an inspection.
Emeka receives the Kafka notification in real time.

vista.dreamhomes.today
```

### Visual

- Large "Live Demo" header
- One-line journey description
- Live URL prominently displayed
- Optional: small QR code linking to the live URL so judges can follow along on their phones

### Speaker notes (you'll want to refine this — it's a starter)

> "Now — let me show you the thing itself."
>
> "I'm going to log in as Temi. She's looking for a 2-bedroom in Lekki under 4 million naira. I'll search. The verified-owner listings come up first. The unverified ones carry the warning we showed earlier."
>
> "She picks one. She sees the available inspection slots. She picks Saturday afternoon. She books."
>
> "Now I'm switching to Emeka — the agent on that listing. Within a second, the notification arrives. He sees Temi's name, the property, the slot. He can approve, reschedule, or decline."
>
> "That round trip — booking to notification — is the Kafka pipeline we just talked about, running live in production. Per-listing partition, ordered events, transactional outbox."
>
> "Any questions?"

### Driver / narrator split

Decide before going on stage:
- Who runs the laptop (one driver)
- Who narrates (the other person)
- Don't share the driving — it slows everything down

### Backup video

Record a screen capture of the same journey ahead of time. If the live demo glitches (Vista cold-starts, Kafka latency, Wi-Fi flakes), cut to the video without ceremony.

---

## Q&A Preparation (cross-reference)

For likely judge questions, see [`docs/demo-prep/likely-questions.md`](likely-questions.md):

- The top 7 curveballs are pre-answered (OAuth vs JWT, GIST mechanics, rogue agent, AI doing what, scaling to a million, two-decisions defense, Kafka failure mode)
- Each session has its own Q&A section
- Aim for confident 2-3 sentence answers; offer to deep-dive only if asked

---

## Defensible Moat (one-slide backup, if asked about competition)

| Scam pattern | Our defence |
|---|---|
| Fake agents duplicating listings | Agent credential verification + verified badge |
| Same property "rented" to multiple tenants | Property document verification + audit trail |
| Landlords pretending to be agents | Role separation + fee transparency |
| Inspection fee racket | Platform policy: zero inspection fees |
| Hidden agent fees / two-year fees on one-year tenancy | Fee structure visible on agent profile before contact |
