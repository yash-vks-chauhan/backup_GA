// AD_TOP_UP forensic audit — run against production MongoDB.
//   mongosh "$MONGODB_URI" --file scripts/ad_top_up_audit.js
// Read-only. No writes, no deletes.

const AD = "AD_TOP_UP";

print("\n=== 1. Totals to date ===");
printjson(db.transactions.aggregate([
  { $match: { type: AD } },
  { $group: {
      _id: null,
      credits: { $sum: 1 },
      totalValue: { $sum: "$amount" },
      distinctUsers: { $addToSet: "$userId" },
      first: { $min: "$timestamp" },
      last:  { $max: "$timestamp" }
  }},
  { $project: {
      _id: 0, credits: 1, totalValue: 1,
      users: { $size: "$distinctUsers" }, first: 1, last: 1
  }}
]).toArray());
// Compare totalValue against (rewarded impressions * 10) for the same window.
// Materially higher => credits are being issued without impressions.

print("\n=== 2. Top 25 users by credit count ===");
printjson(db.transactions.aggregate([
  { $match: { type: AD } },
  { $group: { _id: "$userId", credits: { $sum: 1 }, value: { $sum: "$amount" },
              first: { $min: "$timestamp" }, last: { $max: "$timestamp" } }},
  { $sort: { credits: -1 } },
  { $limit: 25 }
]).toArray());
// A legitimate user tops out near one per day. Hundreds or thousands = scripted.

print("\n=== 3. Burst detection: >20 credits in any single hour ===");
printjson(db.transactions.aggregate([
  { $match: { type: AD } },
  { $group: {
      _id: { user: "$userId",
             hour: { $dateToString: { format: "%Y-%m-%d %H", date: "$timestamp" } } },
      credits: { $sum: 1 }, value: { $sum: "$amount" }
  }},
  { $match: { credits: { $gt: 20 } } },
  { $sort: { credits: -1 } },
  { $limit: 50 }
]).toArray());
// No human watches 20 rewarded ads in an hour. Any row here is abuse.

print("\n=== 4. Amounts other than the expected 10.0 ===");
printjson(db.transactions.aggregate([
  { $match: { type: AD } },
  { $group: { _id: "$amount", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
]).toArray());
// Anything above 10.0 predates the MAX_CLIENT_AD_TOP_UP_AMOUNT cap
// or was issued through an admin path.

print("\n=== 5. Daily trend, last 60 days ===");
printjson(db.transactions.aggregate([
  { $match: { type: AD,
              timestamp: { $gte: new Date(Date.now() - 60*24*60*60*1000) } }},
  { $group: {
      _id: { $dateToString: { format: "%Y-%m-%d", date: "$timestamp" } },
      credits: { $sum: 1 }, value: { $sum: "$amount" },
      users: { $addToSet: "$userId" }
  }},
  { $project: { credits: 1, value: 1, users: { $size: "$users" } } },
  { $sort: { _id: 1 } }
]).toArray());
// Credits per user per day trending above ~1 is the leading indicator.

print("\n=== 6. Realized cost: how much AD_TOP_UP balance was actually spent ===");
printjson(db.transactions.aggregate([
  { $match: { type: { $in: [AD, "BOOKING_FEE"] } } },
  { $group: { _id: "$type", count: { $sum: 1 }, value: { $sum: "$amount" } } }
]).toArray());
// Wallet is fungible, so this is an upper bound on breakage, not an exact
// figure. If BOOKING_FEE spend is far below AD_TOP_UP issuance, much of the
// liability is unredeemed and the real loss is smaller than the headline.
