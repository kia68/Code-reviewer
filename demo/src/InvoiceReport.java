import java.util.List;

public class InvoiceReport {

    // Überlange Methode: löst LONG_METHOD aus (Grenzwert 40 Zeilen).
    void printInvoice(List<String> items, double subtotal, String customerName, String address) {
        System.out.println("========================================");
        System.out.println("                RECHNUNG                ");
        System.out.println("========================================");
        System.out.println("Kunde:   " + customerName);
        System.out.println("Adresse: " + address);
        System.out.println("----------------------------------------");
        System.out.println("Positionen:");

        int position = 1;
        for (String item : items) {
            System.out.println("  " + position + ". " + item);
            position++;
        }

        System.out.println("----------------------------------------");
        System.out.println("Zwischensumme: " + subtotal + " EUR");

        double tax = subtotal * 0.19;
        System.out.println("MwSt (19%):    " + tax + " EUR");

        double shipping = subtotal >= 50 ? 0.0 : 4.99;
        System.out.println("Versand:       " + shipping + " EUR");

        double total = subtotal + tax + shipping;
        System.out.println("----------------------------------------");
        System.out.println("Gesamtbetrag:  " + total + " EUR");
        System.out.println("========================================");
        System.out.println("Zahlbar innerhalb von 14 Tagen.");
        System.out.println("Bankverbindung: DE00 0000 0000 0000 0000 00");
        System.out.println("Verwendungszweck: " + customerName);
        System.out.println("========================================");
        System.out.println("Vielen Dank für Ihren Einkauf!");
        System.out.println("Bei Fragen: support@example.com");
        System.out.println("Rücksendungen innerhalb von 30 Tagen.");
        System.out.println("Widerrufsbelehrung siehe Anlage.");
        System.out.println("========================================");
        System.out.println("Seite 1 von 1");
        System.out.println("Erstellt automatisch, ohne Unterschrift gültig.");
        System.out.println("Steuernummer: 000/000/00000");
        System.out.println("USt-IdNr.: DE000000000");
        System.out.println("Handelsregister: HRB 00000");
        System.out.println("Geschäftsführung: Max Mustermann");
        System.out.println("Sitz der Gesellschaft: Musterstadt");
        System.out.println("========================================");
    }
}
