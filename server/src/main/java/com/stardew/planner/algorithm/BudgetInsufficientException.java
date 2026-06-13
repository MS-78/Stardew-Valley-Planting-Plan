package com.stardew.planner.algorithm;

/**
 * 预算不足异常
 * 当选中的作物最小成本超过预算时抛出
 */
public class BudgetInsufficientException extends RuntimeException {

    public BudgetInsufficientException(String message) {
        super(message);
    }

    public BudgetInsufficientException(String message, Throwable cause) {
        super(message, cause);
    }
}
