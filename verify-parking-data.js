// Verify parking data in database
print("=== PARKING DATABASE VERIFICATION ===\n");

print("📊 Collections:");
print("  Parking Lots: " + db.parking_lots.countDocuments());
print("  Parking Spots: " + db.parking_spots.countDocuments());
print("");

const lots = db.parking_lots.find({}).toArray();

function normalizeId(value) {
    if (value === null || value === undefined) return "";
    if (typeof value === "string") return value.trim();
    try {
        return String(value.valueOf());
    } catch (e) {
        return String(value);
    }
}

function collectLotIdCandidates(lot) {
    const candidates = [];
    if (lot._id !== undefined && lot._id !== null) {
        candidates.push(lot._id);
        const normalized = normalizeId(lot._id);
        if (normalized) candidates.push(normalized);
    }
    if (lot.id !== undefined && lot.id !== null) {
        candidates.push(lot.id);
        const normalized = normalizeId(lot.id);
        if (normalized) candidates.push(normalized);
    }
    return candidates.filter(Boolean);
}

print("🅿️  PARKING LOTS:");
print("─".repeat(60));
lots.forEach(lot => {
    print(`  ${lot.name}`);
    print(`    📍 ${lot.address}`);
    print(`    🚗 ${lot.availableSpots}/${lot.totalSpots} spots available`);
    print(`    💰 ₹${lot.pricePerHour}/hour`);
    print(`    ⏰ ${lot.operatingHours}`);
    print("");
});

print("DATA INTEGRITY:");
print("─".repeat(60));
const lotIdSet = new Set();
const lotIdQueryValues = [];
lots.forEach(lot => {
    const candidates = collectLotIdCandidates(lot);
    candidates.forEach(value => {
        lotIdQueryValues.push(value);
        const normalized = normalizeId(value);
        if (normalized) lotIdSet.add(normalized);
    });
});

const missingLotIdQuery = {
    $or: [
        { lotId: { $exists: false } },
        { lotId: null },
        { lotId: "" }
    ]
};
const missingLotIdCount = db.parking_spots.countDocuments(missingLotIdQuery);
const invalidLotIdQuery = lotIdQueryValues.length
    ? { lotId: { $exists: true, $nin: lotIdQueryValues.concat([null, ""]) } }
    : { _id: null };
const invalidLotIdCount = lotIdQueryValues.length
    ? db.parking_spots.countDocuments(invalidLotIdQuery)
    : 0;

print(`  Spots missing lotId: ${missingLotIdCount}`);
print(`  Spots with lotId not in parking_lots: ${invalidLotIdCount}`);

if (missingLotIdCount > 0) {
    print("  Sample missing lotId:");
    db.parking_spots.find(missingLotIdQuery).limit(5).forEach(spot => {
        print(`    - _id=${spot._id}, lotName=${spot.lotName}`);
    });
}

if (invalidLotIdCount > 0) {
    print("  Sample invalid lotId:");
    db.parking_spots.find(invalidLotIdQuery).limit(5).forEach(spot => {
        print(`    - _id=${spot._id}, lotId=${spot.lotId}, lotName=${spot.lotName}`);
    });
}
print("");

print("🚗 PARKING SPOTS BY LOT:");
print("─".repeat(60));
lots.forEach(lot => {
    const lotIdCandidates = collectLotIdCandidates(lot);
    if (lotIdCandidates.length === 0) {
        print(`  ${lot.name} (missing id)`);
        print("");
        return;
    }

    const total = db.parking_spots.countDocuments({ lotId: { $in: lotIdCandidates } });
    const available = db.parking_spots.countDocuments({ lotId: { $in: lotIdCandidates }, available: true });
    const occupied = total - available;
    print(`  ${lot.name} (${normalizeId(lot._id) || lot.id}):`);
    print(`    Total: ${total} spots`);
    print(`    Available: ${available} spots`);
    print(`    Occupied: ${occupied} spots`);

    // Show sample spots
    const samples = db.parking_spots.find({ lotId: { $in: lotIdCandidates } }).limit(5).toArray();
    print(`    Sample spots: ${samples.map(s => s.spotId).join(', ')}...`);
    print("");
});

print("✅ Database verification complete!");
print("\n💡 Tip: Use Google Sign-In in the app to view these parking lots.");
