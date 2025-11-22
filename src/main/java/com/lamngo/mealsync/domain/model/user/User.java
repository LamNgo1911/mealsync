package com.lamngo.mealsync.domain.model.user;

import com.lamngo.mealsync.domain.model.UserRecipe;
import com.lamngo.mealsync.domain.model.mealPlan.MealPlan;
import com.lamngo.mealsync.domain.model.recipe.Recipe;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true)
})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password cannot be blank")
    @Column(nullable = false)
    private String password; // Length constraint might be added depending on hashing

    @NotBlank(message = "Name cannot be blank") // Added validation consistency
    @Column(nullable = false, length = 100) // Added reasonable length
    private String name;

    @NotNull // Ensure role is not null at persistence time
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) // Length based on expected Role names
    private UserRole role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE; // Default status is ACTIVE

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private UserPreference userPreference;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<MealPlan> mealPlans = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserRecipe> userRecipes = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private RefreshToken refreshToken;

    @Column(nullable = false)
    private boolean emailVerified = false;

    // Subscription fields
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.TRIAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    @Column(nullable = false, updatable = false)
    private Instant accountCreatedAt = Instant.now();

    @Column
    private Instant subscriptionStartDate;

    @Column
    private Instant subscriptionEndDate;

    @Column
    private Instant trialEndDate; // accountCreatedAt + 3 days

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentProvider paymentProvider = PaymentProvider.TRIAL;

    @Column(length = 255)
    private String paymentProviderId; // Apple transaction ID or Google purchase token

    @Column(columnDefinition = "TEXT")
    private String receiptData; // Store receipt for re-validation

    @Column
    private Integer scansUsed = 0;

    @Column
    private Integer scansLimit = 999; // Unlimited during trial

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Assuming credentials are always valid
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.BANNED;
    }

    @Override
    public boolean isAccountNonExpired() {
        return status != UserStatus.INACTIVE;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if(role == null) {
            return Collections.emptyList();
        }

        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", name='" + name + '\'' +
                ", role=" + role +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User)) return false;

        User user = (User) o;

        return id != null && Objects.equals(id, user.id);
    }
}
