public class Demo {

    void processOrder(int amount, boolean express, boolean giftWrap) {
        int unusedDiscount = 10;

        if (amount > 0) {
            if (express) {
                if (giftWrap) {
                    if (amount > 100) {
                        System.out.println("Express order with gift wrap over 100");
                    }
                }
            }
        }
    }

    void generateReport(int items) {
        System.out.println("Starting report");
        System.out.println("Header");
        System.out.println("----------------------------------------");
        int total = 0;
        for (int i = 0; i < items; i++) {
            total += i;
            System.out.println("Item " + i);
            System.out.println("Running total: " + total);
        }
        System.out.println("----------------------------------------");
        System.out.println("Subtotal: " + total);
        double tax = total * 0.19;
        System.out.println("Tax: " + tax);
        double shipping = total > 100 ? 0 : 5;
        System.out.println("Shipping: " + shipping);
        double grandTotal = total + tax + shipping;
        System.out.println("Grand total: " + grandTotal);
        System.out.println("Thank you for your order");
        System.out.println("----------------------------------------");
        System.out.println("Footer line 1");
        System.out.println("Footer line 2");
        System.out.println("Footer line 3");
        System.out.println("Footer line 4");
        System.out.println("Footer line 5");
        System.out.println("Footer line 6");
        System.out.println("Footer line 7");
        System.out.println("Footer line 8");
        System.out.println("Footer line 9");
        System.out.println("Footer line 10");
        System.out.println("Footer line 11");
        System.out.println("Footer line 12");
        System.out.println("Footer line 13");
        System.out.println("Footer line 14");
        System.out.println("Footer line 15");
        System.out.println("Footer line 16");
        System.out.println("Footer line 17");
        System.out.println("Footer line 18");
        System.out.println("Footer line 19");
        System.out.println("Footer line 20");
        System.out.println("Report complete");
    }
}
