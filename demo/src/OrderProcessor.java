public class OrderProcessor {

    // Tief verschachtelte Rabattlogik: löst DEEP_NESTING aus.
    double calculateDiscount(double amount, boolean isMember, boolean hasCoupon, boolean isBlackFriday) {
        double unusedBaseRate = 0.05;

        if (amount > 0) {
            if (isMember) {
                if (hasCoupon) {
                    if (isBlackFriday) {
                        return amount * 0.4;
                    }
                    return amount * 0.25;
                }
            }
        }
        return 0;
    }

    boolean isEligibleForFreeShipping(double amount) {
        return amount >= 50;
    }
}
