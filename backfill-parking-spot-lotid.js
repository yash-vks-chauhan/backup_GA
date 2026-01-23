// Backfill lotId in parking_spots from lotName -> parking_lots._id
// Usage:
//   mongosh <connection> backfill-parking-spot-lotid.js
//   mongosh <connection> --eval "var APPLY=true" backfill-parking-spot-lotid.js

const APPLY = (typeof APPLY !== "undefined" && APPLY === true);

function normalizeId(value) {
    if (value === null || value === undefined) return "";
    if (typeof value === "string") return value.trim();
    try {
        return String(value.valueOf());
    } catch (e) {
        return String(value);
    }
}

function normalizeName(value) {
    if (!value) return "";
    return String(value).trim().toLowerCase().replace(/\s+/g, " ");
}

const lots = db.parking_lots.find({}).toArray();
const lotIdSet = new Set();
const lotNameToId = new Map();
const ambiguousNames = new Set();

lots.forEach(lot => {
    const lotId = normalizeId(lot._id || lot.id);
    if (lotId) lotIdSet.add(lotId);

    const nameKey = normalizeName(lot.name);
    if (!nameKey || !lotId) return;

    if (ambiguousNames.has(nameKey)) return;

    if (!lotNameToId.has(nameKey)) {
        lotNameToId.set(nameKey, lotId);
        return;
    }

    if (lotNameToId.get(nameKey) !== lotId) {
        lotNameToId.delete(nameKey);
        ambiguousNames.add(nameKey);
    }
});

let inspected = 0;
let needsFix = 0;
let updated = 0;
let skipped = 0;
let missingName = 0;
let noMatch = 0;
let ambiguous = 0;

const updateSamples = [];
const skipSamples = [];
const MAX_SAMPLES = 5;

db.parking_spots.find({}).forEach(spot => {
    inspected++;

    const currentLotId = normalizeId(spot.lotId);
    const hasValidLotId = currentLotId && lotIdSet.has(currentLotId);

    if (hasValidLotId) return;

    needsFix++;

    const nameKey = normalizeName(spot.lotName);
    if (!nameKey) {
        missingName++;
        skipped++;
        if (skipSamples.length < MAX_SAMPLES) {
            skipSamples.push({ id: spot._id, reason: "missing lotName" });
        }
        return;
    }

    if (ambiguousNames.has(nameKey)) {
        ambiguous++;
        skipped++;
        if (skipSamples.length < MAX_SAMPLES) {
            skipSamples.push({ id: spot._id, reason: "ambiguous lotName" });
        }
        return;
    }

    const resolvedLotId = lotNameToId.get(nameKey);
    if (!resolvedLotId) {
        noMatch++;
        skipped++;
        if (skipSamples.length < MAX_SAMPLES) {
            skipSamples.push({ id: spot._id, reason: "no matching lot" });
        }
        return;
    }

    if (APPLY) {
        db.parking_spots.updateOne(
            { _id: spot._id },
            { $set: { lotId: resolvedLotId } }
        );
    }

    updated++;
    if (updateSamples.length < MAX_SAMPLES) {
        updateSamples.push({ id: spot._id, lotId: resolvedLotId, lotName: spot.lotName });
    }
});

print("=== PARKING SPOT LOTID BACKFILL ===");
print("Apply mode: " + (APPLY ? "true" : "false (dry run)"));
print("Lots: " + lots.length);
print("Spots inspected: " + inspected);
print("Spots needing lotId fix: " + needsFix);
print("Spots updated: " + updated);
print("Spots skipped: " + skipped);
print("  - missing lotName: " + missingName);
print("  - no matching lot: " + noMatch);
print("  - ambiguous lotName: " + ambiguous);

if (updateSamples.length > 0) {
    print("\nSample updates:");
    updateSamples.forEach(sample => {
        print("  - _id=" + sample.id + ", lotId=" + sample.lotId + ", lotName=" + sample.lotName);
    });
}

if (skipSamples.length > 0) {
    print("\nSample skips:");
    skipSamples.forEach(sample => {
        print("  - _id=" + sample.id + ", reason=" + sample.reason);
    });
}

if (!APPLY) {
    print("\nDry run only. Re-run with APPLY=true to persist changes.");
}
