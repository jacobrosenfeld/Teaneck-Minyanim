# Selichos Timing Support

Issue: #262

## Public behavior

The public schedule distinguishes three Selichos cases:

- `Night Selichos` for late-night standalone Selichos.
- `Selichos & Shacharis` for morning Selichos that are effectively attached to Shacharis.
- Plain `Selichos` only when an event remains standalone and does not classify into the night or combined morning cases.

On shul pages, `Selichos & Shacharis` rows render in the `Shacharis` section rather than in a separate column. The duplicate Shacharis row is hidden when it is used as the linked target.

When a morning Selichos event is followed by a nearby Shacharis event from the same shul and date, the public row keeps the Selichos start time and shows an approximate linked Shacharis time, for example:

```text
Selichos & Shacharis 8:40 AM
Shacharis at approx. 8:45 AM
```

## Import behavior

Calendar import parsing classifies combined titles such as `Selichos/Shacharis`, `Selichot/Shacharit`, `Slichos & Shachris`, and `Selichos followed by Shacharis` as `SELICHOS_SHACHARIS`.

Morning standalone Selichos can also be enriched into the combined public display when the date/time is not a night Selichos case and a nearby Shacharis event is present. The linker prefers the same location when available, then falls back to the nearest same-shul Shacharis within 45 minutes.

## API/mobile contract

Clients should use the schedule display metadata instead of deriving Selichos behavior locally:

- `displayMinyanType` and `displayMinyanTypeDisplay` for the row label.
- `groupMinyanType` and `groupMinyanTypeDisplay` for filters and sections.
- `linkedMinyanType`, `linkedMinyanTypeDisplay`, and `linkedStartTime` for the approximate Shacharis follow-up time.
- `linkedTarget` to hide a source row that has been folded into a combined public event.

Current mobile clients can tolerate the added fields, but the updated app is required for the exact `Shacharis at approx.` wording and shul-detail ordering that treats `Selichos & Shacharis` as part of Shacharis.

## Suggested popup copy

Title: `New: Selichos Times Are Here`

Message: `We are rolling out improved Selichos support ahead of the upcoming Selichos season. The site now does its best to detect Selichos, Selichos & Shacharis, and Night Selichos times automatically. Please help us fine-tune it: if you notice a missing, duplicated, or incorrect time, use the report button in the bottom-right corner of the site.`
