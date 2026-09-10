package it.andreademartini.trading.orders;

import java.util.Optional;

public enum TakeProfitLevel {
    TP1(1),
    TP2(2),
    TP3(3);

    private final int number;

    TakeProfitLevel(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    public static Optional<TakeProfitLevel> fromRole(String role) {
        if (role == null) {
            return Optional.empty();
        }
        return switch (role.trim().toUpperCase()) {
            case "TAKE_PROFIT_1", "TP1" -> Optional.of(TP1);
            case "TAKE_PROFIT_2", "TP2" -> Optional.of(TP2);
            case "TAKE_PROFIT_3", "TP3" -> Optional.of(TP3);
            default -> Optional.empty();
        };
    }

    public static TakeProfitLevel fromNumber(int number) {
        return switch (number) {
            case 1 -> TP1;
            case 2 -> TP2;
            case 3 -> TP3;
            default -> throw new IllegalArgumentException(
                    "Take profit level non supportato: " + number);
        };
    }
}
