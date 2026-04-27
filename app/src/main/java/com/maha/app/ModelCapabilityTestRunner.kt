// ModelCapabilityTestRunner.kt

package com.maha.app

import android.content.Context
import org.json.JSONObject

object ModelCapabilityTestRunner {

    suspend fun runTest(
        context: Context,
        providerName: String,
        modelName: String,
        testType: String
    ): ModelCapabilityTestRecord {
        val safeProviderName = providerName.trim().ifBlank { ModelProviderType.DUMMY }
        val safeModelName = modelName.trim().removePrefix("models/")
        val safeTestType = testType.trim()

        if (safeProviderName == ModelProviderType.DUMMY) {
            val record = buildRecord(
                providerName = safeProviderName,
                modelName = safeModelName,
                testType = safeTestType,
                status = ModelCapabilityTestStatus.UNSUPPORTED,
                latencyMs = 0L,
                message = "Dummy Provider는 기능 테스트 대상이 아닙니다.",
                sampleOutput = ""
            )

            ModelCapabilityTestManager.saveRecord(context, record)
            return record
        }

        val prompt = buildPrompt(safeTestType)
        val start = System.currentTimeMillis()

        val response = runCatching {
            when (safeProviderName) {
                ModelProviderType.GOOGLE -> {
                    GoogleModelProvider.generate(
                        ModelRequest(
                            agentId = "capability_test_agent",
                            agentName = "Capability Test",
                            providerName = ModelProviderType.GOOGLE,
                            modelName = safeModelName,
                            inputText = prompt,
                            stepNumber = 1,
                            runType = "CAPABILITY_TEST"
                        )
                    )
                }

                ModelProviderType.NVIDIA -> {
                    NvidiaModelProvider.generate(
                        ModelRequest(
                            agentId = "capability_test_agent",
                            agentName = "Capability Test",
                            providerName = ModelProviderType.NVIDIA,
                            modelName = safeModelName,
                            inputText = prompt,
                            stepNumber = 1,
                            runType = "CAPABILITY_TEST"
                        )
                    )
                }

                else -> {
                    ModelResponse(
                        outputText = "UNSUPPORTED_PROVIDER",
                        status = "FAILED"
                    )
                }
            }
        }.getOrElse { exception ->
            ModelResponse(
                outputText = exception.message ?: "CAPABILITY_TEST_EXCEPTION",
                status = "FAILED"
            )
        }

        val latencyMs = System.currentTimeMillis() - start
        val output = response.outputText.trim()

        val status = classifyStatus(
            responseStatus = response.status,
            outputText = output,
            testType = safeTestType
        )

        val record = buildRecord(
            providerName = safeProviderName,
            modelName = safeModelName,
            testType = safeTestType,
            status = status,
            latencyMs = latencyMs,
            message = buildMessage(status, output),
            sampleOutput = output.take(1200)
        )

        ModelCapabilityTestManager.saveRecord(context, record)

        return record
    }

    private fun buildPrompt(testType: String): String {
        return when (testType) {
            ModelCapabilityTestType.TEXT_TEST -> {
                "짧은 한국어 문장 하나로 멀티 에이전트 앱이 무엇인지 설명해줘."
            }

            ModelCapabilityTestType.SUMMARY_TEST -> {
                """
                아래 문단을 한국어 한 문장으로 요약해줘.

                멀티 에이전트 하네스 앱은 여러 Worker를 순서대로 실행하고, 이전 Worker의 결과를 다음 Worker에게 전달하며, 실행 결과와 로그를 저장하는 앱이다.
                """.trimIndent()
            }

            ModelCapabilityTestType.CODING_TEST -> {
                """
                Kotlin 함수 하나만 작성해줘.
                함수 이름은 sumNumbers.
                입력은 List<Int>.
                반환값은 모든 숫자의 합계 Int.
                코드만 출력해줘.
                """.trimIndent()
            }

            ModelCapabilityTestType.REASONING_TEST -> {
                """
                작업자가 3개의 작업을 완료했고, 1개 작업은 실패했고, 이후 2개의 작업을 추가로 완료했다.
                완료한 작업은 총 몇 개인가?
                짧게 이유도 설명해줘.
                """.trimIndent()
            }

            ModelCapabilityTestType.JSON_TEST -> {
                """
                아래 형식의 JSON만 출력해줘. 설명 문장이나 markdown은 쓰지 마.
                {
                  "status": "ok",
                  "count": 3
                }
                """.trimIndent()
            }

            else -> {
                "간단한 한국어 문장 하나를 출력해줘."
            }
        }
    }

    private fun classifyStatus(
        responseStatus: String,
        outputText: String,
        testType: String
    ): String {
        val upper = "$responseStatus $outputText".uppercase()

        if (upper.contains("TIMEOUT")) return ModelCapabilityTestStatus.TIMEOUT
        if (upper.contains("RATE_LIMIT") || upper.contains("429")) return ModelCapabilityTestStatus.RATE_LIMITED
        if (upper.contains("UNSUPPORTED")) return ModelCapabilityTestStatus.UNSUPPORTED
        if (responseStatus != "SUCCESS") return ModelCapabilityTestStatus.FAILED
        if (outputText.isBlank()) return ModelCapabilityTestStatus.FAILED

        return when (testType) {
            ModelCapabilityTestType.TEXT_TEST -> {
                if (outputText.length >= 8) ModelCapabilityTestStatus.PASSED else ModelCapabilityTestStatus.FAILED
            }

            ModelCapabilityTestType.SUMMARY_TEST -> {
                if (outputText.length in 8..400) ModelCapabilityTestStatus.PASSED else ModelCapabilityTestStatus.FAILED
            }

            ModelCapabilityTestType.CODING_TEST -> {
                val lower = outputText.lowercase()
                if (
                    lower.contains("fun") &&
                    lower.contains("sumnumbers") &&
                    lower.contains("list")
                ) {
                    ModelCapabilityTestStatus.PASSED
                } else {
                    ModelCapabilityTestStatus.FAILED
                }
            }

            ModelCapabilityTestType.REASONING_TEST -> {
                if (outputText.contains("5")) {
                    ModelCapabilityTestStatus.PASSED
                } else {
                    ModelCapabilityTestStatus.FAILED
                }
            }

            ModelCapabilityTestType.JSON_TEST -> {
                if (isValidJsonOutput(outputText)) {
                    ModelCapabilityTestStatus.PASSED
                } else {
                    ModelCapabilityTestStatus.FAILED
                }
            }

            else -> ModelCapabilityTestStatus.FAILED
        }
    }

    private fun isValidJsonOutput(outputText: String): Boolean {
        val cleaned = outputText
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return runCatching {
            val json = JSONObject(cleaned)
            json.has("status") && json.has("count")
        }.getOrDefault(false)
    }

    private fun buildMessage(
        status: String,
        outputText: String
    ): String {
        return when (status) {
            ModelCapabilityTestStatus.PASSED -> "기능 테스트 통과"
            ModelCapabilityTestStatus.TIMEOUT -> "기능 테스트 timeout"
            ModelCapabilityTestStatus.RATE_LIMITED -> "기능 테스트 rate limit"
            ModelCapabilityTestStatus.UNSUPPORTED -> "지원되지 않는 테스트 또는 Provider"
            else -> outputText.take(180).ifBlank { "기능 테스트 실패" }
        }
    }

    private fun buildRecord(
        providerName: String,
        modelName: String,
        testType: String,
        status: String,
        latencyMs: Long,
        message: String,
        sampleOutput: String
    ): ModelCapabilityTestRecord {
        return ModelCapabilityTestRecord(
            providerName = providerName,
            modelName = modelName,
            testType = testType,
            status = status,
            testedAt = getCurrentTimeText(),
            latencyMs = latencyMs,
            message = message,
            sampleOutput = sampleOutput
        )
    }
}