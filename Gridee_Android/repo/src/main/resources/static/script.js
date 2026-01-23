
    const baseUrl = window.location.origin + "/api";

    function pad(n) { return n < 10 ? "0" + n : n; }
    function toBackendIsoString(dtStr) {
        if (!dtStr) return "";
        const d = new Date(dtStr);
        if (isNaN(d.getTime())) return "";
        const off = -d.getTimezoneOffset();
        const sign = off >= 0 ? "+" : "-";
        const hr = pad(Math.abs(Math.trunc(off / 60)));
        const min = pad(Math.abs(off % 60));
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00${sign}${hr}:${min}`;
    }
    function showSection(name) {
        document.querySelectorAll(".section").forEach((s) => s.classList.add("hidden"));
        document.getElementById(name).classList.remove("hidden");
        clearResponseAndTable(name);
        if (name === "bookings") {
            fetchUserVehicles();
        }
    }
    function clearResponseAndTable(name) {
        if (name === "wallet") {
            document.getElementById("walletResponse").textContent = "";
            document.querySelector("#walletTable tbody").innerHTML = "";
            document.querySelector("#walletTxTable tbody").innerHTML = "";
        }
        const responseElem = document.getElementById(name + "Response");
        if (responseElem) responseElem.textContent = "";
        const tbody = document.querySelector(`#${name}Table tbody`);
        if (tbody) tbody.innerHTML = "";
        if (name === "bookings") {
            document.getElementById("bookingAmountSection").style.display = "none";
            document.getElementById("bookingAmount").textContent = "0.00";
            document.getElementById("bookingVehicleNumber").innerHTML = "<option value=''>-- Select Vehicle --</option>";
            document.getElementById("bookingUserId").value = "";
        }
        if (name === "spots") {
            document.getElementById('spotLotId').value = "";
            document.getElementById('spotZoneName').value = "";
            document.getElementById('spotCapacity').value = "";
            document.getElementById('spotAvailable').value = "";
        }
        if (name === "lots") {
            document.getElementById('lotName').value = "";
            document.getElementById('lotLocation').value = "";
            document.getElementById('lotDescription').value = "";
        }
        if (name === "wallet") {
            document.getElementById('walletUserId').value = "";
            document.getElementById('topUpUserId').value = "";
            document.getElementById('topUpAmount').value = "";
        }
    }

    // USERS
    async function fetchUsers() {
        const res = await fetch(`${baseUrl}/users`);
        const data = await res.json();
        displayUsers(data);
        document.getElementById("usersResponse").textContent = "Fetched users successfully";
    }
    function displayUsers(users) {
        const tbody = document.querySelector("#usersTable tbody");
        tbody.innerHTML = "";
        users.forEach((user) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `<td>${user.id || ""}</td><td>${user.name || ""}</td><td>${user.email || ""}</td><td>${user.phone || ""}</td><td>${Array.isArray(user.vehicleNumbers) ? user.vehicleNumbers.join(", ") : user.vehicleNumber || ""}</td><td>${user.walletCoins || 0}</td>`;
            tbody.appendChild(tr);
        });
    }
    async function createUser() {
        const name = document.getElementById("userName").value.trim();
        const email = document.getElementById("userEmail").value.trim();
        const phone = document.getElementById("userPhone").value.trim();
        const vehicleNumbers = document.getElementById("userVehicleNumbers").value.split(",").map((v) => v.trim()).filter(Boolean);
        const password = document.getElementById("userPassword").value;
        if (!name || !email || !phone || !password || vehicleNumbers.length === 0) {
            alert("Fill all required fields (name, email, phone, password, at least one vehicle number).");
            return;
        }
        try {
            const res = await fetch(`${baseUrl}/users/register`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ name, email, phone, vehicleNumbers, passwordHash: password }),
            });
            if (res.ok) {
                const user = await res.json();
                document.getElementById("usersResponse").textContent = `User registered: ${JSON.stringify(user)}`;
                fetchUsers();
            } else {
                const err = await res.text();
                alert("Failed to register user: " + err);
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function updateUser() {
        const id = document.getElementById("updateUserId").value.trim();
        if (!id) { alert("Please enter user ID"); return; }
        const name = document.getElementById("updateUserName").value.trim();
        const email = document.getElementById("updateUserEmail").value.trim();
        const phone = document.getElementById("updateUserPhone").value.trim();
        const vehicleNumbers = document.getElementById("updateUserVehicleNumbers").value.split(",").map((v) => v.trim()).filter(Boolean);
        const password = document.getElementById("updateUserPassword").value;
        const payload = {};
        if (name) payload.name = name;
        if (email) payload.email = email;
        if (phone) payload.phone = phone;
        if (vehicleNumbers.length > 0) payload.vehicleNumbers = vehicleNumbers;
        if (password) payload.passwordHash = password;
        try {
            const res = await fetch(`${baseUrl}/users/${encodeURIComponent(id)}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            if (res.ok) {
                const updatedUser = await res.json();
                document.getElementById("usersResponse").textContent = `User updated: ${JSON.stringify(updatedUser)}`;
                fetchUsers();
            } else {
                const err = await res.text();
                alert("Failed to update user: " + err);
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function deleteUser() {
        const id = document.getElementById("deleteUserId").value.trim();
        if (!id) { alert("Please enter user ID"); return; }
        try {
            const res = await fetch(`${baseUrl}/users/${encodeURIComponent(id)}`, { method: "DELETE" });
            if (res.ok) {
                document.getElementById("usersResponse").textContent = "User deleted successfully.";
                fetchUsers();
            } else {
                const err = await res.text();
                alert("Failed to delete user: " + err);
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }

    // BOOKINGS
    function bookingsUserIdInput() { return document.getElementById("bookingUserId").value.trim(); }
    // Fetch bookings for a user
    async function fetchBookings() {
        const userId = bookingsUserIdInput();
        if (!userId) { alert("Enter User ID to fetch bookings"); return; }
        try {
            const res = await fetch(`${baseUrl}/users/${encodeURIComponent(userId)}/bookings`);
            if (!res.ok) {
                const errorText = await res.text();
                alert("Failed to fetch bookings for user: " + errorText);
                return;
            }
            const data = await res.json();
            displayBookings(data);
            document.getElementById("bookingsResponse").textContent = "Fetched bookings successfully";
        } catch (e) {
            alert("Error fetching bookings: " + e.message);
        }
    }
    function displayBookings(bookings) {
        const tbody = document.querySelector("#bookingsTable tbody");
        tbody.innerHTML = "";
        bookings.forEach((b) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
            <td>${b.id || ""}</td>
            <td>${b.userId || ""}</td>
            <td>${b.spotId || ""}</td>
            <td>${b.lotId || ""}</td>
            <td>${b.checkInTime || ""}</td>
            <td>${b.checkOutTime || ""}</td>
            <td>${b.status || ""}</td>
            <td>${b.amount?.toFixed(2) || "0.00"}</td>
            <td>${b.vehicleNumber || ""}</td>
            <td>${b.qrCode ? `<img src="${getQrCodeImageUrl(b.qrCode)}" class="qrcode"/>` : "N/A"}</td>
        `;
            tbody.appendChild(tr);
        });
    }
    async function fetchUserVehicles() {
        const userId = bookingsUserIdInput();
        const select = document.getElementById("bookingVehicleNumber");
        select.innerHTML = "<option value=''>-- Select Vehicle --</option>";
        if (!userId) { return; }
        try {
            const res = await fetch(`${baseUrl}/users/${encodeURIComponent(userId)}`);
            if (!res.ok) { return; }
            const user = await res.json();
            const vehicles = Array.isArray(user.vehicleNumbers) ? user.vehicleNumbers : [];
            vehicles.forEach((v) => {
                const opt = document.createElement("option");
                opt.value = v;
                opt.textContent = v;
                select.appendChild(opt);
            });
        } catch (e) {
            // Silent fail
        }
    }
    function calculateBookingAmount() {
        const checkInTimeStr = document.getElementById("bookingCheckIn").value;
        const checkOutTimeStr = document.getElementById("bookingCheckOut").value;
        const amountSpan = document.getElementById("bookingAmount");
        const amountSection = document.getElementById("bookingAmountSection");
        if (!checkInTimeStr || !checkOutTimeStr) {
            amountSection.style.display = "none";
            return;
        }
        const checkInTime = new Date(checkInTimeStr);
        const checkOutTime = new Date(checkOutTimeStr);
        if (checkOutTime <= checkInTime) {
            amountSpan.textContent = "Invalid times";
            amountSection.style.display = "block";
            return;
        }
        const durationMs = checkOutTime - checkInTime;
        const hours = Math.ceil(durationMs / (1000 * 60 * 60));
        const amount = (hours * 2.5).toFixed(2);
        amountSpan.textContent = amount;
        amountSection.style.display = "block";
    }
    document.getElementById("bookingCheckIn").addEventListener("change", calculateBookingAmount);
    document.getElementById("bookingCheckOut").addEventListener("change", calculateBookingAmount);

    async function createBooking() {
        const userId = bookingsUserIdInput();
        const spotId = document.getElementById("bookingSpotId").value.trim();
        const lotId = document.getElementById("bookingLotId").value.trim();
        const checkInRaw = document.getElementById("bookingCheckIn").value;
        const checkOutRaw = document.getElementById("bookingCheckOut").value;
        const vehicleNumber = document.getElementById("bookingVehicleNumber").value.trim();
        if (!userId || !spotId || !lotId || !checkInRaw || !checkOutRaw || !vehicleNumber) {
            alert("Fill all booking fields, including user ID, check-in/out, and vehicle number.");
            return;
        }
        const checkInTime = toBackendIsoString(checkInRaw);
        const checkOutTime = toBackendIsoString(checkOutRaw);
        if (!checkInTime || !checkOutTime) {
            alert("Invalid date-time format");
            return;
        }
        try {
            const url = `${baseUrl}/users/${encodeURIComponent(userId)}/bookings/start?spotId=${encodeURIComponent(spotId)}&lotId=${encodeURIComponent(lotId)}&checkInTime=${encodeURIComponent(checkInTime)}&checkOutTime=${encodeURIComponent(checkOutTime)}&vehicleNumber=${encodeURIComponent(vehicleNumber)}`;
            const res = await fetch(url, { method: "POST" });
            if (res.ok) {
                const booking = await res.json();
                document.getElementById("bookingsResponse").textContent = "Booking created: " + JSON.stringify(booking);
                fetchBookings();
                document.getElementById("bookingAmountSection").style.display = "none";
                document.getElementById("bookingAmount").textContent = "0.00";
            } else if (res.status === 409) {
                alert("No available spots for this spot");
            } else if (res.status === 400) {
                const err = await res.text();
                alert("Booking failed: " + err);
            } else {
                alert("Failed to create booking");
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function updateBooking() {
        const userId = bookingsUserIdInput();
        const id = document.getElementById("updateBookingId").value.trim();
        if (!userId || !id) { alert("Enter User ID and Booking ID"); return; }
        const status = document.getElementById("updateBookingStatus").value;
        try {
            const url = `${baseUrl}/users/${encodeURIComponent(userId)}/bookings/${encodeURIComponent(id)}`;
            const res = await fetch(url, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ status }),
            });
            if (res.ok) {
                const updated = await res.json();
                document.getElementById("bookingsResponse").textContent = "Updated booking: " + JSON.stringify(updated);
                fetchBookings();
            } else {
                alert("Failed to update booking");
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function deleteBooking() {
        const userId = bookingsUserIdInput();
        const id = document.getElementById("deleteBookingId").value.trim();
        if (!userId || !id) { alert("Enter User ID and Booking ID"); return; }
        try {
            const url = `${baseUrl}/users/${encodeURIComponent(userId)}/bookings/${encodeURIComponent(id)}`;
            const res = await fetch(url, { method: "DELETE" });
            if (res.ok) {
                document.getElementById("bookingsResponse").textContent = "Booking deleted";
                fetchBookings();
            } else {
                alert("Failed to delete booking");
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    function getQrCodeImageUrl(qrCode) {
        const qr = encodeURIComponent(qrCode);
        return `https://chart.googleapis.com/chart?cht=qr&chs=150x150&chl=${qr}`;
    }

    // SPOTS
    async function fetchSpots() {
        const res = await fetch(`${baseUrl}/parking-spots`);
        if (!res.ok) { alert("Failed to fetch parking spots"); return; }
        const spots = await res.json();
        displaySpots(spots);
    }
    function displaySpots(spots) {
        const tbody = document.querySelector("#spotsTable tbody");
        tbody.innerHTML = "";
        spots.forEach((s) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
            <td>${s.id || ""}</td>
            <td>${s.lotId || ""}</td>
            <td>${s.zoneName || ""}</td>
            <td>${s.capacity || ""}</td>
            <td>${s.available || ""}</td>
            <td>
                <button onclick="holdSpot('${s.id}')">Hold Spot</button>
                <button onclick="releaseSpot('${s.id}')">Release Spot</button>
                <button onclick="deleteSpot('${s.id}')">Delete</button>
            </td>
        `;
            tbody.appendChild(tr);
        });
    }
    async function createSpot() {
        const lotId = document.getElementById("spotLotId").value.trim();
        const zoneName = document.getElementById("spotZoneName").value.trim();
        const capacity = parseInt(document.getElementById("spotCapacity").value);
        const available = parseInt(document.getElementById("spotAvailable").value);
        if (!lotId || !zoneName || isNaN(capacity) || isNaN(available)) {
            alert("Fill all required spot fields correctly.");
            return;
        }
        const spot = { lotId, zoneName, capacity, available };
        try {
            const res = await fetch(`${baseUrl}/parking-spots`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(spot),
            });
            if (res.ok) {
                alert("Parking spot created");
                fetchSpots();
            } else {
                const errText = await res.text();
                alert("Failed to create spot: " + errText);
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function holdSpot(id) {
        const userId = prompt("Enter User ID to hold this spot:");
        if (!userId) { alert("User ID required"); return; }
        try {
            const res = await fetch(`${baseUrl}/parking-spots/${encodeURIComponent(id)}/hold?userId=${encodeURIComponent(userId)}`, { method: "POST" });
            if (res.ok) {
                alert("Spot held successfully");
                fetchSpots();
            } else if (res.status === 409) {
                alert("No availability for this spot");
            } else {
                alert("Failed to hold spot");
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function releaseSpot(id) {
        try {
            const res = await fetch(`${baseUrl}/parking-spots/${encodeURIComponent(id)}/release`, { method: "POST" });
            if (res.ok) {
                alert("Spot released successfully");
                fetchSpots();
            } else {
                alert("Failed to release spot");
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function deleteSpot(id) {
        if (!confirm("Are you sure you want to delete this spot?")) return;
        try {
            const res = await fetch(`${baseUrl}/parking-spots/${encodeURIComponent(id)}`, { method: "DELETE" });
            if (res.ok) {
                alert("Spot deleted");
                fetchSpots();
            } else {
                alert("Failed to delete spot");
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }

    // LOTS
    async function fetchLots() {
        const res = await fetch(`${baseUrl}/parking-lots`);
        if (!res.ok) { alert("Failed to fetch parking lots"); return; }
        const lots = await res.json();
        displayLots(lots);
    }
    function displayLots(lots) {
        const tbody = document.querySelector("#lotsTable tbody");
        tbody.innerHTML = "";
        lots.forEach((lot) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
            <td>${lot.id || ""}</td>
            <td>${lot.name || ""}</td>
            <td>${lot.location || ""}</td>
            <td>${lot.description || ""}</td>
            <td>
                <button onclick="deleteLot('${lot.id}')">Delete</button>
            </td>
        `;
            tbody.appendChild(tr);
        });
    }
    async function createLot() {
        const name = document.getElementById("lotName").value.trim();
        const location = document.getElementById("lotLocation").value.trim();
        const description = document.getElementById("lotDescription").value.trim();
        if (!name || !location) {
            alert("Fill at least name and location for the lot.");
            return;
        }
        const lot = { name, location, description };
        try {
            const res = await fetch(`${baseUrl}/parking-lots`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(lot),
            });
            if (res.ok) {
                alert("Parking lot created");
                fetchLots();
            } else {
                const errText = await res.text();
                alert("Failed to create lot: " + errText);
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    async function deleteLot(id) {
        if (!confirm("Are you sure you want to delete this lot?")) return;
        try {
            const res = await fetch(`${baseUrl}/parking-lots/${encodeURIComponent(id)}`, { method: "DELETE" });
            if (res.ok) {
                alert("Lot deleted");
                fetchLots();
            } else {
                alert("Failed to delete lot");
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }

    // WALLET
    async function fetchWallet() {
        const userId = document.getElementById("walletUserId").value.trim();
        if (!userId) { alert("Enter User ID"); return; }
        try {
            const res = await fetch(`${baseUrl}/users/${encodeURIComponent(userId)}/wallet`);
            if (!res.ok) { alert("Wallet not found"); return; }
            const wallet = await res.json();
            displayWallet(wallet);
            document.getElementById("walletResponse").textContent = "Wallet fetched successfully";
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    function displayWallet(wallet) {
        const tbody = document.querySelector("#walletTable tbody");
        tbody.innerHTML = "";
        const tr = document.createElement("tr");
        tr.innerHTML = `
        <td>${wallet.id || ""}</td>
        <td>${wallet.userId || ""}</td>
        <td>${wallet.balance?.toFixed(2) || "0.00"}</td>
        <td>${new Date(wallet.lastUpdated).toLocaleString() || ""}</td>
    `;
        tbody.appendChild(tr);
    }
    async function fetchWalletTransactions() {
        const userId = document.getElementById("walletUserId").value.trim();
        if (!userId) { alert("Enter User ID"); return; }
        try {
            const res = await fetch(`${baseUrl}/users/${encodeURIComponent(userId)}/wallet/transactions`);
            if (!res.ok) { alert("Failed to fetch wallet transactions"); return; }
            const txs = await res.json();
            displayWalletTransactions(txs);
        } catch (e) {
            alert("Error: " + e.message);
        }
    }
    function displayWalletTransactions(txs) {
        const tbody = document.querySelector("#walletTxTable tbody");
        tbody.innerHTML = "";
        txs.forEach((tx) => {
            const tr = document.createElement("tr");
            tr.innerHTML = `
            <td>${tx.referenceId || ""}</td>
            <td>${tx.type || ""}</td>
            <td>${tx.amount?.toFixed(2) || "0.00"}</td>
            <td>${tx.status || ""}</td>
        `;
            tbody.appendChild(tr);
        });
    }
    async function topUpWallet() {
        const userId = document.getElementById("topUpUserId").value.trim();
        const amount = parseFloat(document.getElementById("topUpAmount").value);
        if (!userId || isNaN(amount) || amount <= 0) {
            alert("Enter valid User ID and amount (positive number)");
            return;
        }
        try {
            const res = await fetch(`${baseUrl}/users/${encodeURIComponent(userId)}/wallet/topup`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ amount }),
            });
            if (res.ok) {
                const wallet = await res.json();
                alert("Wallet topped up successfully");
                displayWallet(wallet);
            } else {
                const errorText = await res.text();
                alert("Failed to top-up wallet: " + errorText);
            }
        } catch (e) {
            alert("Error: " + e.message);
        }
    }

    // Initialize on page load
    showSection("users");

