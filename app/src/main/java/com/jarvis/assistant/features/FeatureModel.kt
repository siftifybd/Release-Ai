package com.jarvis.assistant.features

enum class FeatureRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class JarvisFeatureItem(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val requiredPermission: String,
    val riskLevel: FeatureRiskLevel = FeatureRiskLevel.LOW,
    val routeKey: String = ""
)

data class FeatureCategoryItem(
    val id: String,
    val title: String,
    val iconName: String,
    val description: String,
    val featureCount: Int = 20
)
