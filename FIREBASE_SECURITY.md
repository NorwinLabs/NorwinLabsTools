# Firebase security

## The problem

The app reached the Realtime Database with **no identity at all** - there was no `FirebaseAuth`
anywhere in the codebase. Both callers just did `FirebaseDatabase.getInstance().reference`:

- `CircleShareFragment` - writes live GPS coordinates to `/circles/{id}/members/{userId}`
- `VoipCallManager` - writes call signalling to `/voipCalls` and `/voipInbox`

Rules cannot restrict an unauthenticated client to anything meaningful, so for the app to work
at all the rules must currently allow unauthenticated access. Combined with `google-services.json`
being committed to a public repository, that means anyone who reads the repo can very likely
read **live location coordinates of every Circle Share user**, and read or tamper with call
signalling.

Please confirm in the console (Realtime Database → Rules). If it says `".read": true` and
`".write": true`, this is live, not theoretical.

## What the app now does

`FirebaseAuthGate` signs in anonymously at app start (`NorwinLabsApp.onCreate`), so every install
has a stable `auth.uid`. Sign-in is cached by the SDK, so it only actually runs on first launch.
`CircleShareFragment` also awaits it before touching the database, which only matters on a
genuine first run.

Anonymous auth is not a login. It does not identify a person, and it does not stop someone from
creating an anonymous account of their own. What it buys is that rules can require `auth != null`
and can eventually attribute writes to a uid.

## Rollout order - this part matters

`database.rules.json` requires `auth != null`. **Deploying it will break every currently
installed copy of the app**, because v1.0.104 and earlier never authenticate. Their Circle Share
and VoIP calling will start failing with permission-denied.

So the order is:

1. Ship a release built from this branch, so installs start authenticating.
2. Give users time to update. The in-app updater helps here.
3. Then deploy the rules:
   ```bash
   firebase deploy --only database
   ```

If you would rather close the hole immediately and accept that old installs break, deploy the
rules first. Given the data involved is live location, that is a defensible call - but make it
deliberately rather than by accident.

## What this does not fix yet

These rules require a signed-in client, and bound the type and size of everything written. They
do **not** yet prove that a writer owns what it writes: any signed-in client can still write to
another user's member entry or ring any inbox. Closing that needs uid ownership in the data
model - each member node and inbox claiming `auth.uid` on creation, with rules checking it:

```
"$memberId": {
  ".write": "auth != null && (!data.exists() || data.child('uid').val() === auth.uid)",
  "uid": { ".validate": "newData.val() === auth.uid" }
}
```

That is a data-model change: `userId` is currently a random 6-character string generated on the
device and shared between users to place calls, so it cannot simply become the uid without
breaking the shareable ID. The workable shape is to keep the short handle for display and sharing
while binding ownership to uid underneath.

## Testing rules before deploying

Never deploy rules straight to production - a mistake locks out every user at once:

```bash
firebase emulators:start --only database
```
