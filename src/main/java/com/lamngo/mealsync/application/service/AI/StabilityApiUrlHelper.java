package com.lamngo.mealsync.application.service.AI;

/**
 * Utility class for parsing and normalizing Stability AI API URLs and model names.
 */
class StabilityApiUrlHelper {
    private static final String GENERATE_PATTERN = "/generate/";
    private static final String V2BETA_PATTERN = "/v2beta/";
    private static final String BASE_MODEL_SD3 = "sd3";

    static boolean isV2BetaApi(String url) {
        return url != null && url.contains(V2BETA_PATTERN);
    }

    static String getBaseUrlForV2Beta(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        int generateIndex = url.indexOf("/generate");
        if (generateIndex != -1) {
            return url.substring(0, generateIndex + "/generate".length()) + "/" + BASE_MODEL_SD3;
        }
        return url;
    }

    static String extractModelFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int generateIndex = url.indexOf(GENERATE_PATTERN);
        if (generateIndex == -1) {
            return null;
        }
        int modelStart = generateIndex + GENERATE_PATTERN.length();
        if (modelStart >= url.length()) {
            return null;
        }
        String model = url.substring(modelStart);
        int queryIndex = model.indexOf('?');
        if (queryIndex != -1) {
            model = model.substring(0, queryIndex);
        }
        if (model.endsWith("/")) {
            model = model.substring(0, model.length() - 1);
        }
        return model.isEmpty() ? null : model;
    }

    static String normalizeModelName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return modelName;
        }
        String lowerModel = modelName.toLowerCase();
        if (lowerModel.contains("sd3.5") || lowerModel.contains("sd-3-5") || lowerModel.contains("sd35")) {
            if (lowerModel.contains("flash")) return "sd3.5-flash";
            if (lowerModel.contains("large") && lowerModel.contains("turbo")) return "sd3.5-large-turbo";
            if (lowerModel.contains("large")) return "sd3.5-large";
            if (lowerModel.contains("medium")) return "sd3.5-medium";
        }
        if (lowerModel.equals("sd3") || lowerModel.equals("sd-3")) {
            return "sd3";
        }
        return modelName;
    }
}

