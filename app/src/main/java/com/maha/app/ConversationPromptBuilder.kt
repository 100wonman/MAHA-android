package com.maha.app

class ConversationPromptBuilder {
    fun build(request: ConversationRequest): String {
        val sections = mutableListOf<String>()

        val safeSystemInstruction = request.systemInstruction?.trim().orEmpty()
        if (safeSystemInstruction.isNotBlank()) {
            sections.add(
                buildString {
                    appendLine("[SYSTEM]")
                    append(safeSystemInstruction)
                }
            )
        }

        if (request.recentMessages.isNotEmpty()) {
            sections.add(
                buildString {
                    appendLine("[RECENT_MESSAGES]")
                    request.recentMessages.forEach { message ->
                        appendLine("role: ${message.role}")
                        val text = message.blocks.joinToString("\n") { block -> block.content }
                        appendLine(text)
                        appendLine()
                    }
                }.trim()
            )
        }

        val ragContext = request.ragContext
        if (ragContext != null && ragContext.enabled && ragContext.contextText.isNotBlank()) {
            val safeContext = ragContext.contextText.take(ragContext.maxContextChars)
            sections.add(
                buildString {
                    appendLine("[RAG_CONTEXT]")
                    append(safeContext)
                }
            )
        }

        sections.add(
            buildString {
                appendLine("[USER_INPUT]")
                append(request.userInput)
            }
        )

        return sections.joinToString("\n\n")
    }
}
