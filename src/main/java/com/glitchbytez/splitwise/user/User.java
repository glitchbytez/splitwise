package com.glitchbytez.splitwise.user;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User {

    static final String DELETED_DISPLAY_NAME = "Deleted user";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
        // for JPA
    }

    private User(String email, String displayName, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    /**
     * @param email        already normalised (see {@link EmailAddresses#normalize(String)})
     * @param passwordHash a BCrypt hash, never the raw password
     */
    public static User register(String email, String displayName, String passwordHash) {
        return new User(email, displayName, passwordHash);
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Removes personal data but keeps the row, so expenses and balances that reference this user stay correct.
     * The placeholder email is unique per user and uses the reserved {@code .invalid} TLD.
     */
    public void anonymise(Instant now) {
        this.email = "deleted-" + id + "@deleted.invalid";
        this.displayName = DELETED_DISPLAY_NAME;
        this.passwordHash = null;
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof User other && id != null && id.equals(other.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        // Deliberately excludes email and password hash so entities can't leak personal data into logs.
        return "User[id=" + id + "]";
    }
}
