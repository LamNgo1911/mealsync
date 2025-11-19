package com.lamngo.mealsync.application.service.AI;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParallelGenerationLogicTest {

    @Test
    public void testParallelLogicAndDeduplication() throws ExecutionException, InterruptedException {
        // Simulate 3 futures returning lists of recipes
        CompletableFuture<List<String>> future1 = CompletableFuture.completedFuture(List.of("Recipe A"));
        CompletableFuture<List<String>> future2 = CompletableFuture.completedFuture(List.of("Recipe B"));
        CompletableFuture<List<String>> future3 = CompletableFuture.completedFuture(List.of("Recipe A")); // Duplicate

        CompletableFuture<List<String>> resultFuture = CompletableFuture.allOf(future1, future2, future3)
                .thenApply(v -> {
                    List<String> allRecipes = new ArrayList<>();
                    try {
                        List<String> r1 = future1.join();
                        if (r1 != null)
                            allRecipes.addAll(r1);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        List<String> r2 = future2.join();
                        if (r2 != null)
                            allRecipes.addAll(r2);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        List<String> r3 = future3.join();
                        if (r3 != null)
                            allRecipes.addAll(r3);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Deduplicate
                    Map<String, String> uniqueRecipes = new HashMap<>();
                    for (String r : allRecipes) {
                        uniqueRecipes.putIfAbsent(r, r);
                    }

                    return new ArrayList<>(uniqueRecipes.values());
                });

        List<String> result = resultFuture.get();

        // Should have 2 recipes: A and B
        assertEquals(2, result.size());
        System.out.println("Result: " + result);
    }

    @Test
    public void testParallelLogicWithFailure_Fixed() throws ExecutionException, InterruptedException {
        // Simulate 1 failure
        CompletableFuture<List<String>> future1 = CompletableFuture.completedFuture(List.of("Recipe A"));
        CompletableFuture<List<String>> future2 = CompletableFuture
                .<List<String>>failedFuture(new RuntimeException("Failed"))
                .exceptionally(ex -> {
                    System.out.println("Handled exception: " + ex.getMessage());
                    return null;
                });
        CompletableFuture<List<String>> future3 = CompletableFuture.completedFuture(List.of("Recipe C"));

        CompletableFuture<List<String>> resultFuture = CompletableFuture.allOf(future1, future2, future3)
                .thenApply(v -> {
                    List<String> allRecipes = new ArrayList<>();
                    try {
                        List<String> r1 = future1.join();
                        if (r1 != null)
                            allRecipes.addAll(r1);
                    } catch (Exception e) {
                        System.out.println("Future 1 failed: " + e.getMessage());
                    }

                    try {
                        List<String> r2 = future2.join();
                        if (r2 != null)
                            allRecipes.addAll(r2);
                    } catch (Exception e) {
                        // This shouldn't happen if handled above, but join() might still throw if not
                        // handled correctly
                        System.out.println("Future 2 failed: " + e.getMessage());
                    }

                    try {
                        List<String> r3 = future3.join();
                        if (r3 != null)
                            allRecipes.addAll(r3);
                    } catch (Exception e) {
                        System.out.println("Future 3 failed: " + e.getMessage());
                    }

                    // Deduplicate
                    Map<String, String> uniqueRecipes = new HashMap<>();
                    for (String r : allRecipes) {
                        uniqueRecipes.putIfAbsent(r, r);
                    }

                    return new ArrayList<>(uniqueRecipes.values());
                });

        List<String> result = resultFuture.get();

        // Should have 2 recipes: A and C
        assertEquals(2, result.size());
        System.out.println("Result with failure fixed: " + result);
    }
}
