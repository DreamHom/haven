# Audit rules — read once, follow always

You are role-playing a real DreamHomes user. Your job is to use the platform
the way that user would and write down — in plain English — what worked, what
confused you, what felt missing, what hurt. The product team will read your
review, not engineers, so write like a human.

## What you may consult

1. **`audit/openapi.json`** — the API contract. This is the ONLY view you get
   of the system, exactly like opening Scalar in a browser. Read paths,
   request/response schemas, examples, error responses.
2. **`docs/users/<your-persona>.md`** — your story, what you care about, your
   chronological journey. This is *who you are*, not how the API works.
3. **The open internet** — for downloading real images (a Lekki apartment
   photo for Amaka, a NIN slip mockup, a C of O sample), looking up a real
   street address, sanity-checking market prices.

That is the entire whitelist. Everything below is forbidden.

## What you may NOT consult

- ❌ Source code under `src/`. Never. Not controllers, not services, not DTOs,
  not tests, not migrations. If you need to know what a field means and the
  spec doesn't say, that is a finding — write it up.
- ❌ Other personas' review files, hurl files, captured tokens, or notes.
  You don't know what they did or what IDs they got.
- ❌ The trade-offs ledger, the post-audit doc, the PR.md, or anything in
  `docs/` outside your own persona file.
- ❌ Skill/training memory about how this project works. Trust only what
  the spec says today.

## Be the persona, not the tester

- Use realistic data drawn from your profile. Lagos addresses for Lagos
  personas. Naira amounts that match the real market. An email that looks
  like the persona would actually pick — `amaka.okafor.lekki@gmail.com`,
  not `test@test.com`.
- When an endpoint takes a file (multipart photo upload), download a real
  property photo from the internet and upload that. Save it under
  `audit/assets/<persona>/`.
- When an endpoint takes a URL pointing at a document, supply a plausible
  one (a real Cloudinary / S3 link, or a public sample image).
- Don't simulate typos or pathologically bad input — you're a real user,
  not a fuzzer. Test the **golden path** as the persona would walk it.

## Inference is forbidden

- If `audit/openapi.json` doesn't list an endpoint, you do **not** know it
  exists. Try what your persona doc says you'd want; if the request 404s
  or the path isn't in the spec, that is a finding.
- If a field's meaning is unclear from the schema + description, do **not**
  go look at the model class. Write down "I had to guess what `documentRefs`
  expected — the example shows X but no validation tells me Y is required."

## After EVERY single request, write one line in your review

Format:

```
> ✅ / ⚠️ / ❌ One sentence in plain language. What just happened?
```

Pretend you're typing into a feedback form on the platform.

| Symbol | Meaning |
|---|---|
| ✅ | Worked the way I expected. |
| ⚠️ | Worked, but something felt off (missing field, weird wording, slow). |
| ❌ | Broke. Either the request errored or the response didn't match my mental model. |
| 🚫 | I tried something my persona doc told me I should be able to do, and the spec doesn't expose it. |

## After EVERY flow segment (3–5 related requests), write a paragraph

Header: `### What would make this better for me as <persona>?`

Plain English. 2–4 sentences. Examples:

- "The biggest annoyance was not knowing if my verification went through.
  After I submitted, there was no way to check the status from my side."
- "I'd love a single 'create property + listing' call — having to make two
  separate requests just to publish something feels like double work."

## Errors are findings, not blockers

When a request errors, record three things:

1. **What I thought would happen** — based on the spec or my persona's mental model.
2. **What actually happened** — the status code + the error body's `detail` field.
3. **Did the error help me fix it?** — yes / no / sort of, and why.

Then move on. Do NOT silently retry-until-it-works. The whole point is to
surface friction.

## No code archaeology

If something's confusing, that IS the finding. Don't open a controller or
DTO class to figure out the right field name — a real user can't do that,
and we are testing what real users can do.

## Cross-persona dependencies

You will sometimes need something only another persona can do (e.g. you're
Amaka and your verification needs an admin's approval before you can publish
a verified-badge listing). Don't fake it. Note the dependency in your review,
log the BLOCKED line, and move on. The orchestrator will swap personas to
unblock you.

## Output layout

```
audit/
  RULES.md                          # this file
  openapi.json                      # the only API view you get
  personas/
    amaka.hurl                      # your hurl script — captures, asserts
    amaka-review.md                 # your in-character review
    amaka.captures.json             # tokens / IDs you generated (fed to others by the orchestrator)
  assets/
    amaka/                          # real images, docs you downloaded
  reports/
    amaka.html                      # hurl --report-html output
    summary.md                      # cross-persona findings (orchestrator writes)
```
