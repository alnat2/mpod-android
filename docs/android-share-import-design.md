# Android Share import design

This design applies to a single `ACTION_SEND` containing either an HTTP(S) URL
or an audio `content://` URI. `ACTION_SEND_MULTIPLE` and `content://` values in
`text/plain` are unsupported and must produce a visible explanation.

## Persistence model

Room owns one row per accepted Share event:

| Column | Purpose |
|---|---|
| `token` | Stable UUID for delivery identity and acknowledgement |
| `payload` | Parsed HTTP(S) URL or original shared URI |
| `title` | Optional shared title |
| `status` | `ENQUEUED`, `COPYING`, `IMPORTING`, `DONE`, or `FAILED` |
| `sourceUri` | Canonical source identity and retry source for local audio |
| `copyPath` | App-private temporary/final path for `content://` payloads |
| `error` | User-displayable failure detail |
| `attempts` | Import/copy retry count |
| `batchId` | Quiet-window result aggregation identity |
| `fingerprint` | Canonical parsed-payload fingerprint for short redelivery suppression |
| `progressBytes`, `totalBytes` | Copy progress for the banner and foreground notification |
| `completionAcknowledged` | Durable acknowledgement of one batch refresh/navigation effect |
| `createdAt`, `updatedAt` | Stable ordering, grace-window and recovery metadata |

The database schema advances with an explicit Room migration. Work is serialized
by one app-scoped unique WorkManager job. Workers query unfinished rows in
creation order and acknowledge each row by transitioning it to `DONE` or
`FAILED`; a crash before acknowledgement leaves a resumable row. The worker is
idempotent at the repository boundary and never treats an in-memory callback as
the source of truth.

## Submission and recovery

`MainActivity` parses the incoming intent once and submits an immutable payload;
the original `Intent` is never persisted or replayed. `onNewIntent` always gets a
new token. On `onCreate`, the payload fingerprint is checked first against the
saved-instance-state fingerprint, then against an unfinished pending row; only
otherwise is a new token created. This suppresses platform redelivery without
collapsing separate intentional Shares outside the 2–5 second grace window.

For a `content://` source, submission persists an `ENQUEUED` row and starts the
copy immediately while the receiving Activity's URI grant is live. Copying uses
an app-private `.part` file and atomic rename. A resumed process cleans an
incomplete temp file and retries from `sourceUri` when its grant remains valid;
once a final copy exists, all later work uses `copyPath` and does not need the
provider grant. Unsupported/revoked provider reads become a `FAILED` event with
a user-visible error. A file is associated with its episode only after a full
copy and successful import.

The copy is keyed by the shared URI identity. Re-import of the same episode
reuses the existing managed copy. The episode records the app-private copy as
its downloaded file, so the existing episode cleanup path removes it when the
episode is deleted or marked as listened. A copy whose import has not reached
`DONE` is retained for retry/recovery.

## States and acknowledgement

```text
HTTP(S): ENQUEUED -> IMPORTING -> DONE | FAILED
content: ENQUEUED -> COPYING -> IMPORTING -> DONE | FAILED
recovery: COPYING + completed final copy -> IMPORTING
recovery: COPYING + partial temp -> delete temp, retry source copy if readable
retry: FAILED -> ENQUEUED (new batchId; same payload/token lineage)
```

Each terminal update and its error/attempt metadata is committed to Room. The UI
derives batch completion from the Room-backed rows. Room is the authority for
cold start, Activity recreation, and process death. WorkManager is the durable
wake-up mechanism; an application-scoped task accelerates immediate foreground
copying while the incoming URI grant is available.

## Batches and UI

An accepted submission synchronously exposes Loading. New events arriving
within a 300–500 ms quiet window share a `batchId`; late arrivals begin another
batch even if an earlier import is still running. The repository/manager emits
one terminal group result (`successes`, `failures`, and `remaining`) per closed
batch. UI derives pending progress and terminal state from Room-backed state.
Mixed/failed results remain visible with retry of failed rows only. Auto-dismiss
is permitted only when every row in the batch succeeds. Library refresh and
navigation are issued once per batch if it has at least one success.

`completionAcknowledged` is set for all rows in a successful batch after its
single refresh/navigation effect, preventing duplicate effects after Activity
recreation. Unacknowledged terminal results remain recoverable after process
death.

`AddPodcastViewModel` owns only RSS/OPML form submission. Audio Share work,
loading, retries, and progress belong to the app-scoped import manager and must
not reset or block the form state.

## Content copy progress and cleanup

Copy progress reports bytes copied and total bytes when the provider supplies a
size. Unknown length remains indeterminate. Content copies expose foreground
notification progress and a Cancel action; cancellation removes the partial file
and records a retryable failure. Provider exceptions are recorded as failures,
not as empty/valid audio. An interrupted `.part` file is overwritten when the
pending row resumes; copy failures and cancellations remove the partial file.
