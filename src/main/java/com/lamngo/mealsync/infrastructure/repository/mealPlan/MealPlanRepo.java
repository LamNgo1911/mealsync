package com.lamngo.mealsync.infrastructure.repository.mealPlan;

import com.lamngo.mealsync.domain.model.MealPlan;
import com.lamngo.mealsync.domain.repository.IMealPlanRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MealPlanRepo implements IMealPlanRepo {

    @Autowired
    private MealPlanJpaRepo _mealPlanJpaRepo;

    @Override
    public MealPlan createMealPlan(MealPlan mealPlan) {
        return _mealPlanJpaRepo.save(mealPlan);
    }

    @Override
    public Optional<MealPlan> getMealPlanById(UUID id) {
        return _mealPlanJpaRepo.findById(id);
    }

    @Override
    public List<MealPlan> getAllMealPlans() {
        return _mealPlanJpaRepo.findAll();
    }

    @Override
    public void deleteMealPlan(UUID id) {
        _mealPlanJpaRepo.deleteById(id);

    }
    // This class is a placeholder for the actual implementation of the MealPlan repository.
    // In a real-world scenario, this class would interact with a database to perform CRUD operations
    // on MealPlan entities. For now, it serves as a marker interface for the repository layer.
}
