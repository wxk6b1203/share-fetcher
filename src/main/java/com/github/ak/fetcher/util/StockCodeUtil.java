package com.github.ak.fetcher.util;

/**
 * 股票代码工具类
 */
public class StockCodeUtil {

    /**
     * 从带前缀的代码中提取纯数字代码
     * sh600000 -> 600000, SZ000776 -> 000776, bj920000 -> 920000
     */
    public static String extractPureCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        // 去掉 sh/SZ/bj 等前缀（不区分大小写）
        if (code.length() > 2) {
            String prefix = code.substring(0, 2).toLowerCase();
            if ("sh".equals(prefix) || "sz".equals(prefix) || "bj".equals(prefix)) {
                return code.substring(2);
            }
        }
        return code;
    }

    /**
     * 转换为带市场前缀的代码
     * 600000 -> sh600000, 000776 -> sz000776, 920000 -> bj920000
     */
    public static String toMarketSymbol(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        // 如果已经有前缀，直接返回
        if (code.length() > 2) {
            String prefix = code.substring(0, 2).toLowerCase();
            if ("sh".equals(prefix) || "sz".equals(prefix) || "bj".equals(prefix)) {
                return code;
            }
        }
        // 根据代码开头添加前缀
        if (code.startsWith("6")) {
            return "sh" + code;
        } else if (code.startsWith("0") || code.startsWith("3")) {
            return "sz" + code;
        } else if (code.startsWith("8") || code.startsWith("4") || code.startsWith("9")) {
            return "bj" + code;
        }
        return code;
    }
}
