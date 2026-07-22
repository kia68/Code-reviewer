import java.util.ArrayList;
import java.util.List;

public class CustomerRegistry {

    private final List<String> customers = new ArrayList<>();

    // Ungenutzte lokale Variable: löst UNUSED_VARIABLE aus.
    void register(String name) {
        int maxRetries = 3;
        customers.add(name);
    }

    boolean isRegistered(String name) {
        return customers.contains(name);
    }

    int count() {
        return customers.size();
    }
}
