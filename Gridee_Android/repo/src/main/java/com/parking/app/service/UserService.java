package com.parking.app.service;
import com.parking.app.model.Users;
import com.parking.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    // Email Regex (simple, you may replace with a more robust pattern)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w-\\.]+@[\\w-\\.]+\\.[a-z]{2,}$", Pattern.CASE_INSENSITIVE);

    // Phone Regex (for example, 10-15 digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,15}$");

    // Create User with strong validation
    public Users createUser(Users user) {
        if (user == null) throw new IllegalArgumentException("User data is required.");

        if (user.getName() == null || user.getName().trim().isEmpty()) throw new IllegalArgumentException("Name is required.");

        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) throw new IllegalArgumentException("Email is required.");
        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) throw new IllegalArgumentException("Invalid email format.");

        if (user.getPhone() == null || user.getPhone().trim().isEmpty()) throw new IllegalArgumentException("Phone is required.");
        if (!PHONE_PATTERN.matcher(user.getPhone()).matches()) throw new IllegalArgumentException("Invalid phone format.");

        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) throw new IllegalArgumentException("Password is required.");

        // Check duplicates
        if (userRepository.findByEmail(user.getEmail()).isPresent()) throw new IllegalArgumentException("Email already registered.");
        if (userRepository.findByPhone(user.getPhone()).isPresent()) throw new IllegalArgumentException("Phone already registered.");

        // Hash password (input is plain password in passwordHash field)
        String hashedPassword = BCrypt.hashpw(user.getPasswordHash(), BCrypt.gensalt());
        user.setPasswordHash(hashedPassword);

        // Initialize fields
        user.setWalletCoins(0);
        user.setFirstUser(true);
        user.setCreatedAt(new java.util.Date());

        return userRepository.save(user);
    }

    // Authenticate user by email or phone and plain password
    public Users authenticate(String emailOrPhone, String plainPassword) {
        if (emailOrPhone == null || emailOrPhone.trim().isEmpty() || plainPassword == null || plainPassword.isEmpty()) {
            return null;
        }
        System.out.println(plainPassword);
        Users user = userRepository.findByEmail(emailOrPhone).orElse(null);
        if (user == null) user = userRepository.findByPhone(emailOrPhone).orElse(null);
        if (user != null && BCrypt.checkpw(plainPassword, user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    public List<Users> getAllUsers() {
        return userRepository.findAll();
    }

    public Users getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    public Users updateUser(String id, Users userDetails) {
        Users existingUser = userRepository.findById(id).orElse(null);
        if (existingUser == null) return null;

        // Update only non-null/non-empty fields
        if (userDetails.getName() != null && !userDetails.getName().trim().isEmpty()) {
            existingUser.setName(userDetails.getName());
        }

        if (userDetails.getEmail() != null && !userDetails.getEmail().trim().isEmpty()) {
            if (!EMAIL_PATTERN.matcher(userDetails.getEmail()).matches()) {
                throw new IllegalArgumentException("Invalid email format.");
            }
            // Check email uniqueness if changed
            if (!userDetails.getEmail().equals(existingUser.getEmail()) &&
                    userRepository.findByEmail(userDetails.getEmail()).isPresent()) {
                throw new IllegalArgumentException("Email already registered.");
            }
            existingUser.setEmail(userDetails.getEmail());
        }

        if (userDetails.getPhone() != null && !userDetails.getPhone().trim().isEmpty()) {
            if (!PHONE_PATTERN.matcher(userDetails.getPhone()).matches()) {
                throw new IllegalArgumentException("Invalid phone format.");
            }
            // Check phone uniqueness if changed
            if (!userDetails.getPhone().equals(existingUser.getPhone()) &&
                    userRepository.findByPhone(userDetails.getPhone()).isPresent()) {
                throw new IllegalArgumentException("Phone already registered.");
            }
            existingUser.setPhone(userDetails.getPhone());
        }

        if (userDetails.getVehicleNumbers() != null && !userDetails.getVehicleNumbers().isEmpty()) {
            existingUser.setVehicleNumbers(userDetails.getVehicleNumbers());
        }


        if (userDetails.getPasswordHash() != null && !userDetails.getPasswordHash().isEmpty()) {
            String hashedPassword = BCrypt.hashpw(userDetails.getPasswordHash(), BCrypt.gensalt());
            existingUser.setPasswordHash(hashedPassword);
        }

        return userRepository.save(existingUser);
    }


    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }
}
