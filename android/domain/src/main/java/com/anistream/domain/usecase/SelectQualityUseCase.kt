package com.anistream.domain.usecase

import com.anistream.domain.model.StreamSource

class SelectQualityUseCase {
    operator fun invoke(
        variants: List<StreamSource>,
        preferred: String
    ): StreamSource {
        return when (preferred.lowercase()) {
            "best" -> variants.maxByOrNull { it.height } ?: variants.first()
            "worst" -> variants.minByOrNull { it.height } ?: variants.last()
            else -> {
                val numeric = preferred.replace("p", "").toIntOrNull()
                if (numeric != null) {
                    variants.find { it.height == numeric }
                        ?: variants.minByOrNull { kotlin.math.abs(it.height - numeric) }
                        ?: variants.first()
                } else {
                    variants.first()
                }
            }
        }
    }
}
