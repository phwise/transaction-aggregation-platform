package com.capitec.domain;


public enum TransactionCategory {
    GROCERIES("Groceries"),
    TRANSPORT("Transport"),
    SHOPPING("Shopping"),
    RESTAURANTS("Restaurants and takeaways"),
    BILLS_UTILITIES("Bills and utilities"),
    ENTERTAINMENT("Entertainment"),
    HEALTHCARE("Healthcare"),
    INCOME("Income"),
    TRANSFERS("Transfers"),
    FEES("Fees and charges"),
    OTHER("Other");

    private final String displayName;

    TransactionCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
