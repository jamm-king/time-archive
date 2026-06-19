# MVP Scope

## Product Goal

Build a simple, reliable, and memorable first version of Time Archive. The main experience is a fullscreen media timeline where visitors can watch owned and unowned seconds. Users can buy available seconds, upload media, and have that media displayed only after admin approval.

## MVP 1

MVP 1 should focus on primary sale and moderated display.

### Included

- One canonical 24-hour archive timeline with exactly 86,400 seconds
- Fullscreen timeline player
- Default placeholder for unowned seconds
- Approved media display for owned seconds
- Current playback timestamp
- Session authentication with local email/password accounts
- Current-user owned range listing
- Availability query for a selected time range
- Primary purchase flow for unowned seconds
- Payment checkout creation
- Payment webhook handling
- Ownership record creation after confirmed payment
- Media upload for owned time ranges
- Admin media approval and rejection
- Admin media hiding
- Admin original-media preview through short-lived presigned URLs
- Audit logs for payment completion and ownership creation

### Excluded

- Resale marketplace
- Offer negotiation
- Counter-offers
- Seller payout automation
- Likes, comments, follows, profiles, or feeds
- Analytics dashboards
- Multiple timelines, seasons, or editions
- Complex media editing
- Public user pages
- Complete admin moderation audit logging

## MVP 2

MVP 2 can add resale and ownership transfer.

### Included

- Offer submission for already-owned time ranges
- Owner accept or reject flow
- Buyer payment for accepted offers
- Platform fee calculation
- Ownership transfer after confirmed payment
- Transaction history
- Payout record creation

### Excluded Until Later

- Counter-offers
- Auctions
- Bidding wars
- Automated dispute resolution
- Complex seller payout scheduling
- Multi-currency settlement

## Why Resale Should Be Delayed

Primary sale and resale have different complexity profiles. Primary sale creates ownership from the platform to a buyer. Resale transfers ownership between users and introduces seller proceeds, platform fees, accepted offers, payment timing, dispute handling, and payout records.

For a high-quality first release, ownership integrity and payment idempotency should be proven in primary sale before resale is added.

## Manual Verification Goals

MVP 1 should be manually verifiable through the following flows:

1. Open the homepage and watch the fullscreen player.
2. Sign up or sign in through a server-side session.
3. Select an available range from the current unowned second.
4. Complete a test checkout.
5. Confirm that ownership is created only after a verified payment webhook.
6. Upload media for the owned range.
7. Confirm the media is not public before approval.
8. Preview the private original media as an admin.
9. Approve the media as an admin.
10. Confirm the approved media appears on the timeline.
11. Hide approved media as an admin.
12. Confirm the hidden media no longer appears publicly.

## Build and Test Expectations

Implementation work should define concrete commands when the application exists. Expected quality gates:

- Compile backend
- Run unit tests
- Run integration tests for database transaction behavior
- Run frontend build
- Run lint and formatting checks
- Run basic manual verification against a local Docker environment
