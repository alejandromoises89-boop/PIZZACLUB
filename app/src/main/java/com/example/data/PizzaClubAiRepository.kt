package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout

class PizzaClubAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.7f
            },
            systemInstruction = content {
                text(
                    "Sos el Maestro Pizzero virtual de Pizza Club en Ciudad del Este, Paraguay. " +
                        "Respondé siempre en español rioplatense/paraguayo natural, cálido y breve (máximo 4 líneas). " +
                        "Nuestro menú artesanal cuesta Gs. 50.000 por pizza (Pepperoni, Mozzarella, Cuatro quesos, Pollo Catupiri), " +
                        "disponible 'Lista para comer' o 'Lista para hornear en casa' (8 minutos en horno)."
                )
            }
        )
    }

    fun streamFlavorRecommendation(
        cravingPrompt: String,
        availableFlavorsSummary: String
    ): Flow<String> {
        val fullPrompt = buildString {
            appendLine("Sabores disponibles hoy en stock:")
            appendLine(availableFlavorsSummary)
            appendLine()
            appendLine("Pedido o antojo del cliente: $cravingPrompt")
            append("Recomendá 1 o 2 sabores del menú que combinen mejor y sugerí si conviene 'Lista para comer' u 'Hornear en casa'.")
        }
        return model.generateContentStream(fullPrompt).mapNotNull { chunk -> chunk.text }
    }

    suspend fun summarizeAdminOrdersInsight(ordersSummary: String): String {
        return withTimeout(60_000L) {
            val prompt = buildString {
                appendLine("Analizá brevemente estos pedidos reales de Pizza Club (en guaraníes PYG) y sugerí 2 acciones prácticas de stock o promoción:")
                append(ordersSummary)
            }
            model.generateContent(prompt).text
                ?: throw IllegalStateException("El modelo no devolvió texto.")
        }
    }
}
