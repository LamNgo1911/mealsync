package com.lamngo.mealsync.infrastructure.repository.mealPlan;

import com.lamngo.mealsync.domain.model.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MealPlanJpaRepo extends JpaRepository<MealPlan, UUID> {
    MealPlan save(MealPlan mealPlan);

    void deleteById(UUID id);

    Optional<MealPlan> findById(UUID id);

    List<MealPlan> findAll();
}
