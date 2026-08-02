package dev.reboot.rule;

/**
 * 报警规则比较运算符。
 *
 * @author hula0710
 * @since 2026-08-02
 */
public enum Operator {
    GT,
    LT,
    GTE,
    LTE,
    EQ,
    NEQ;

    public boolean evaluate(int cmp) {
        return switch (this) {
            case GT  -> cmp > 0;
            case LT  -> cmp < 0;
            case GTE -> cmp >= 0;
            case LTE -> cmp <= 0;
            case EQ  -> cmp == 0;
            case NEQ -> cmp != 0;
        };
    }
}
