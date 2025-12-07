package com.example.localexpense.parser

import com.example.localexpense.util.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 规则文件校验器
 *
 * 功能：
 * 1. 验证 JSON 格式正确性
 * 2. 验证必需字段存在
 * 3. 验证正则表达式语法
 * 4. 验证规则完整性
 * 5. 生成校验报告
 */
object RuleValidator {

    private const val TAG = "RuleValidator"

    /**
     * 校验结果
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val stats: Stats
    ) {
        data class Stats(
            val totalApps: Int,
            val totalRules: Int,
            val validPatterns: Int,
            val invalidPatterns: Int
        )

        override fun toString(): String {
            return buildString {
                appendLine("===== 规则校验报告 =====")
                appendLine("状态: ${if (isValid) "✓ 通过" else "✗ 失败"}")
                appendLine()
                appendLine("统计:")
                appendLine("  - 应用数: ${stats.totalApps}")
                appendLine("  - 规则数: ${stats.totalRules}")
                appendLine("  - 有效正则: ${stats.validPatterns}")
                appendLine("  - 无效正则: ${stats.invalidPatterns}")

                if (errors.isNotEmpty()) {
                    appendLine()
                    appendLine("错误 (${errors.size}):")
                    errors.forEach { appendLine("  ✗ $it") }
                }

                if (warnings.isNotEmpty()) {
                    appendLine()
                    appendLine("警告 (${warnings.size}):")
                    warnings.forEach { appendLine("  ⚠ $it") }
                }

                appendLine("========================")
            }
        }
    }

    /**
     * 验证规则 JSON 字符串
     */
    fun validate(json: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var totalApps = 0
        var totalRules = 0
        var validPatterns = 0
        var invalidPatterns = 0

        try {
            // 1. 解析 JSON
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                errors.add("JSON 格式错误: ${e.message}")
                return ValidationResult(
                    isValid = false,
                    errors = errors,
                    warnings = warnings,
                    stats = ValidationResult.Stats(0, 0, 0, 0)
                )
            }

            // 2. 验证版本号
            val version = root.optString("version", "")
            if (version.isEmpty()) {
                warnings.add("缺少 version 字段")
            } else if (!isValidVersionFormat(version)) {
                warnings.add("版本号格式不规范: $version (建议使用 x.y.z 格式)")
            }

            // 3. 验证 apps 数组
            val appsArray = root.optJSONArray("apps")
            if (appsArray == null) {
                errors.add("缺少 apps 字段")
                return ValidationResult(
                    isValid = false,
                    errors = errors,
                    warnings = warnings,
                    stats = ValidationResult.Stats(0, 0, 0, 0)
                )
            }

            // 4. 遍历每个 app
            for (i in 0 until appsArray.length()) {
                val app = appsArray.optJSONObject(i)
                if (app == null) {
                    errors.add("apps[$i] 不是有效的 JSON 对象")
                    continue
                }

                totalApps++

                // 验证 packageName
                val packageName = app.optString("packageName", "")
                if (packageName.isEmpty()) {
                    errors.add("apps[$i]: 缺少 packageName")
                    continue
                }

                if (!isValidPackageName(packageName)) {
                    warnings.add("apps[$i]: packageName 格式可能不正确: $packageName")
                }

                // 验证 rules 数组
                val rulesArray = app.optJSONArray("rules")
                if (rulesArray == null) {
                    warnings.add("apps[$i] ($packageName): 缺少 rules 或 rules 为空")
                    continue
                }

                // 5. 验证每条规则
                for (j in 0 until rulesArray.length()) {
                    val rule = rulesArray.optJSONObject(j)
                    if (rule == null) {
                        errors.add("$packageName 规则[$j]: 不是有效的 JSON 对象")
                        continue
                    }

                    totalRules++
                    val ruleResult = validateRule(rule, packageName, j)

                    errors.addAll(ruleResult.errors)
                    warnings.addAll(ruleResult.warnings)
                    validPatterns += ruleResult.validPatterns
                    invalidPatterns += ruleResult.invalidPatterns
                }
            }

        } catch (e: Exception) {
            errors.add("校验过程异常: ${e.message}")
        }

        val isValid = errors.isEmpty()

        val result = ValidationResult(
            isValid = isValid,
            errors = errors,
            warnings = warnings,
            stats = ValidationResult.Stats(
                totalApps = totalApps,
                totalRules = totalRules,
                validPatterns = validPatterns,
                invalidPatterns = invalidPatterns
            )
        )

        // 输出日志
        if (isValid) {
            Logger.i(TAG, "规则校验通过: ${totalApps}个应用, ${totalRules}条规则")
        } else {
            Logger.e(TAG, "规则校验失败: ${errors.size}个错误")
        }

        return result
    }

    /**
     * 验证单条规则
     */
    private fun validateRule(rule: JSONObject, packageName: String, index: Int): RuleValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var validPatterns = 0
        var invalidPatterns = 0

        val prefix = "$packageName 规则[$index]"

        // 验证 type
        val type = rule.optString("type", "")
        if (type.isEmpty()) {
            errors.add("$prefix: 缺少 type")
        } else if (type !in listOf("expense", "income")) {
            errors.add("$prefix: type 必须是 'expense' 或 'income'，当前值: $type")
        }

        // 验证 category
        val category = rule.optString("category", "")
        if (category.isEmpty()) {
            errors.add("$prefix: 缺少 category")
        } else if (category.length > 20) {
            warnings.add("$prefix: category 过长 (${category.length}字符)")
        }

        // 验证 triggerKeywords
        val triggerKeywords = rule.optJSONArray("triggerKeywords")
        if (triggerKeywords == null || triggerKeywords.length() == 0) {
            errors.add("$prefix: 缺少 triggerKeywords 或为空")
        }

        // 验证 amountRegex
        val amountRegex = rule.optJSONArray("amountRegex")
        if (amountRegex == null || amountRegex.length() == 0) {
            errors.add("$prefix: 缺少 amountRegex 或为空")
        } else {
            // 验证每个正则表达式
            for (k in 0 until amountRegex.length()) {
                val regex = amountRegex.optString(k, "")
                if (regex.isEmpty()) {
                    warnings.add("$prefix amountRegex[$k]: 空正则")
                    continue
                }

                val patternResult = validatePattern(regex)
                if (patternResult.isValid) {
                    validPatterns++
                    // 检查是否有捕获组
                    if (!regex.contains("(") || !regex.contains(")")) {
                        warnings.add("$prefix amountRegex[$k]: 正则缺少捕获组，可能无法提取金额")
                    }
                } else {
                    invalidPatterns++
                    errors.add("$prefix amountRegex[$k]: 无效正则 - ${patternResult.error}")
                }
            }
        }

        // 验证 merchantRegex（可选）
        val merchantRegex = rule.optJSONArray("merchantRegex")
        if (merchantRegex != null) {
            for (k in 0 until merchantRegex.length()) {
                val regex = merchantRegex.optString(k, "")
                if (regex.isEmpty()) continue

                val patternResult = validatePattern(regex)
                if (patternResult.isValid) {
                    validPatterns++
                } else {
                    invalidPatterns++
                    warnings.add("$prefix merchantRegex[$k]: 无效正则 - ${patternResult.error}")
                }
            }
        }

        // 验证 priority（可选）
        val priority = rule.optInt("priority", 0)
        if (priority < 0) {
            warnings.add("$prefix: priority 为负数 ($priority)")
        }

        return RuleValidationResult(errors, warnings, validPatterns, invalidPatterns)
    }

    /**
     * 验证正则表达式
     */
    private fun validatePattern(regex: String): PatternValidationResult {
        return try {
            Pattern.compile(regex)
            PatternValidationResult(true, null)
        } catch (e: PatternSyntaxException) {
            PatternValidationResult(false, e.description)
        }
    }

    /**
     * 验证版本号格式
     */
    private fun isValidVersionFormat(version: String): Boolean {
        return version.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))
    }

    /**
     * 验证包名格式
     */
    private fun isValidPackageName(packageName: String): Boolean {
        return packageName.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))
    }

    /**
     * 规则验证结果
     */
    private data class RuleValidationResult(
        val errors: List<String>,
        val warnings: List<String>,
        val validPatterns: Int,
        val invalidPatterns: Int
    )

    /**
     * 正则验证结果
     */
    private data class PatternValidationResult(
        val isValid: Boolean,
        val error: String?
    )

    /**
     * 快速验证（只检查是否能解析）
     */
    fun quickValidate(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val apps = root.optJSONArray("apps")
            apps != null && apps.length() > 0
        } catch (e: Exception) {
            false
        }
    }
}
