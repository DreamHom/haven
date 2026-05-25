# Session 6 — Agent Assignments

## What an agent assignment is

A row in `agent_listings` linking an owner's listing to an agent. The owner is delegating "you can act on this listing on my behalf" to the agent.

Each row has one of 4 statuses:

- **REQUESTED** — owner invited the agent; awaiting response
- **ACCEPTED** — active; agent is now managing this listing
- **DECLINED** — terminal; agent passed
- **REVOKED** — terminal; either party ended an active assignment

## The state machine

From REQUESTED you can go to ACCEPTED, DECLINED, or REVOKED. From ACCEPTED you can only go to REVOKED. DECLINED and REVOKED are terminal — no path forward, the row is dead.

If the owner wants to re-invite the same agent (or a different one) later, they create a new assignment row.

Decline requires a reason. Revoke requires a reason. Accept needs no reason.

## One active agent per listing — enforced twice

A listing can have at most one REQUESTED invite AND at most one ACCEPTED agent at any moment. Enforced at two layers:

- **Service-level** check in `AgentListingService.request()` — friendly error before hitting the DB
- **DB-level partial unique indexes** (from V13) as the race safety net:

```sql
CREATE UNIQUE INDEX agent_listings_one_pending_per_listing
    ON agent_listings (listing_id) WHERE status = 'REQUESTED';

CREATE UNIQUE INDEX agent_listings_one_active_per_listing
    ON agent_listings (listing_id) WHERE status = 'ACCEPTED';
```

DECLINED and REVOKED rows are terminal and drop out of both indexes — so the same listing can cycle through invites over time, but at any single moment, at most one pending + at most one active.

Same design pattern as inspection requests: app-level check for nice errors + partial unique index for race-free correctness.

## To switch agents

You can't just invite a new agent over an existing one. The owner has to:

1. Revoke the current REQUESTED or ACCEPTED row (reason required)
2. THEN request the new agent

Deliberate — forces conscious ending of the current relationship before starting a new one. No accidental dual-assignments.

## What an ACCEPTED agent can do

Once the assignment flips to ACCEPTED, every backend check that asks "is this user an active agent on this listing?" starts saying yes. That unlocks:

- Edit the listing — title, price, description, photos (alongside the owner)
- Publish inspection slots
- Approve / decline / reschedule incoming inspection requests
- Mark inspections as completed or no-show
- Set private "agent extras" notes on inspections
- Accept, decline, or counter offers
- See all offers on the listing (otherwise hidden to non-participants)

Essentially: full operational control of one listing. Same powers as the owner for everything that's been delegated.

## What an ACCEPTED agent cannot do

- Create new listings on the property (owner-only)
- Delete the listing (owner-only)
- Invite or revoke other agents (owner-only)
- Edit the underlying Property (address, bedrooms — owner-only)

Scope is *one specific listing*, not the property or the owner's wider account.

## Revocation is instant

Every authorization check looks at `status = ACCEPTED` at query time. The moment the assignment is revoked:

- Emeka's next attempt to act on the listing → 403
- No grandfathered access, no cleanup job, no "agent's tokens are still valid for 5 minutes"

Side-effect of doing the check on every action instead of caching "is this an agent" on the JWT. Slight extra DB read per request; gives us instant revocation in return.
