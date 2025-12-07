package com.example.localexpense.util

import java.util.regex.Pattern

/**
 * 输入验证工具类
 *
 * 功能：
 * 1. 金额格式验证
 * 2. 商户名称验证
 * 3. 备注内容验证
 * 4. 分类名称验证
 * 5. 危险字符过滤
 */
object InputValidator {

    private const val TAG = "InputValidator"

    // ========== 验证结果 ==========

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    // ========== 金额验证 ==========

    /**
     * 验证金额输入
     * @param input 用户输入的金额字符串
     * @return 验证结果
     */
    fun validateAmount(input: String?): ValidationResult {
        if (input.isNullOrBlank()) {
            return ValidationResult.Invalid("金额不能为空")
        }

        val trimmed = input.trim()

        // 移除货币符号后验证
        val cleaned = trimmed
            .replace("¥", "")
            .replace("￥", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()

        if (cleaned.isEmpty()) {
            return ValidationResult.Invalid("请输入有效金额")
        }

        // 检查是否为有效数字
        val amount = cleaned.toDoubleOrNull()
        if (amount == null) {
            return ValidationResult.Invalid("金额格式不正确")
        }

        // 检查范围
        if (amount <= 0) {
            return ValidationResult.Invalid("金额必须大于0")
        }

        if (amount > Constants.MAX_AMOUNT) {
            return ValidationResult.Invalid("金额超出最大限制")
        }

        // 检查小数位数（最多2位）
        if (cleaned.contains(".")) {
            val decimalPart = cleaned.substringAfter(".")
            if (decimalPart.length > 2) {
                return ValidationResult.Invalid("金额最多保留2位小数")
            }
        }

        return ValidationResult.Valid
    }

    /**
     * 解析并验证金额，返回有效金额或 null
     */
    fun parseAndValidateAmount(input: String?): Double? {
        if (validateAmount(input) != ValidationResult.Valid) {
            return null
        }
        return AmountUtils.parseAmount(input)
    }

    // ========== 商户名称验证 ==========

    /**
     * 验证商户名称
     */
    fun validateMerchant(input: String?): ValidationResult {
        if (input.isNullOrBlank()) {
            return ValidationResult.Invalid("商户名称不能为空")
        }

        val trimmed = input.trim()

        if (trimmed.length > Constants.MAX_MERCHANT_NAME_LENGTH) {
            return ValidationResult.Invalid("商户名称不能超过${Constants.MAX_MERCHANT_NAME_LENGTH}个字符")
        }

        // 检查危险字符
        if (containsDangerousChars(trimmed)) {
            return ValidationResult.Invalid("商户名称包含非法字符")
        }

        return ValidationResult.Valid
    }

    /**
     * 清理并验证商户名称
     */
    fun sanitizeMerchant(input: String?): String {
        if (input.isNullOrBlank()) return "未知商户"

        return input.trim()
            .take(Constants.MAX_MERCHANT_NAME_LENGTH)
            .let { removeDangerousChars(it) }
            .ifBlank { "未知商户" }
    }

    // ========== 备注验证 ==========

    /**
     * 验证备注内容
     */
    fun validateNote(input: String?): ValidationResult {
        if (input == null) {
            return ValidationResult.Valid // 备注可以为空
        }

        val trimmed = input.trim()

        if (trimmed.length > Constants.MAX_NOTE_LENGTH) {
            return ValidationResult.Invalid("备注不能超过${Constants.MAX_NOTE_LENGTH}个字符")
        }

        // 检查危险字符
        if (containsDangerousChars(trimmed)) {
            return ValidationResult.Invalid("备注包含非法字符")
        }

        return ValidationResult.Valid
    }

    /**
     * 清理备注内容
     */
    fun sanitizeNote(input: String?): String {
        if (input.isNullOrBlank()) return ""

        return input.trim()
            .take(Constants.MAX_NOTE_LENGTH)
            .let { removeDangerousChars(it) }
    }

    // ========== 分类名称验证 ==========

    /**
     * 验证分类名称
     */
    fun validateCategory(input: String?): ValidationResult {
        if (input.isNullOrBlank()) {
            return ValidationResult.Invalid("分类名称不能为空")
        }

        val trimmed = input.trim()

        if (trimmed.length > Constants.MAX_CATEGORY_NAME_LENGTH) {
            return ValidationResult.Invalid("分类名称不能超过${Constants.MAX_CATEGORY_NAME_LENGTH}个字符")
        }

        if (trimmed.length < 1) {
            return ValidationResult.Invalid("分类名称至少1个字符")
        }

        // 检查危险字符
        if (containsDangerousChars(trimmed)) {
            return ValidationResult.Invalid("分类名称包含非法字符")
        }

        return ValidationResult.Valid
    }

    /**
     * 清理分类名称
     */
    fun sanitizeCategory(input: String?): String {
        if (input.isNullOrBlank()) return "其他"

        return input.trim()
            .take(Constants.MAX_CATEGORY_NAME_LENGTH)
            .let { removeDangerousChars(it) }
            .ifBlank { "其他" }
    }

    // ========== 搜索关键词验证 ==========

    /**
     * 验证搜索关键词
     */
    fun validateSearchQuery(input: String?): ValidationResult {
        if (input.isNullOrBlank()) {
            return ValidationResult.Valid // 空搜索返回所有结果
        }

        val trimmed = input.trim()

        if (trimmed.length > Constants.MAX_SEARCH_QUERY_LENGTH) {
            return ValidationResult.Invalid("搜索关键词不能超过${Constants.MAX_SEARCH_QUERY_LENGTH}个字符")
        }

        return ValidationResult.Valid
    }

    /**
     * 清理搜索关键词（用于 SQL LIKE 查询）
     */
    fun sanitizeSearchQuery(input: String?): String {
        if (input.isNullOrBlank()) return ""

        return input.trim()
            .take(Constants.MAX_SEARCH_QUERY_LENGTH)
            .let { removeDangerousChars(it) }
    }

    // ========== 危险字符处理 ==========

    // 危险字符模式：SQL 注入、XSS 相关
    private val dangerousPattern = Pattern.compile("[<>\"'`;\\\\\\x00-\\x1f]")

    // 控制字符模式
    private val controlCharPattern = Pattern.compile("[\\x00-\\x1f\\x7f]")

    /**
     * 检查是否包含危险字符
     */
    fun containsDangerousChars(input: String): Boolean {
        return dangerousPattern.matcher(input).find()
    }

    /**
     * 移除危险字符
     */
    fun removeDangerousChars(input: String): String {
        return dangerousPattern.matcher(input).replaceAll("")
            .let { controlCharPattern.matcher(it).replaceAll("") }
            .trim()
    }

    /**
     * 转义 HTML 特殊字符（防止 XSS）
     */
    fun escapeHtml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    // ========== 通用验证 ==========

    /**
     * 验证字符串长度
     */
    fun validateLength(input: String?, maxLength: Int, fieldName: String): ValidationResult {
        if (input == null) return ValidationResult.Valid

        if (input.length > maxLength) {
            return ValidationResult.Invalid("${fieldName}不能超过${maxLength}个字符")
        }

        return ValidationResult.Valid
    }

    /**
     * 批量验证
     * @return 第一个失败的验证结果，或 Valid
     */
    fun validateAll(vararg validations: () -> ValidationResult): ValidationResult {
        for (validation in validations) {
            val result = validation()
            if (result is ValidationResult.Invalid) {
                return result
            }
        }
        return ValidationResult.Valid
    }
}
