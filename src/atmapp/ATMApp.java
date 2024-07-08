package atmapp;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

class Card {
    private String cardNumber;
    private String pinCode;
    private double balance;
    private boolean blocked;
    private int pinAttempts;
    private LocalDateTime lastAttemptTime;
    private LocalDateTime blockTime;

    public Card(String cardNumber, String pinCode, double balance, boolean blocked, LocalDateTime blockTime) {
        this.cardNumber = cardNumber;
        this.pinCode = pinCode;
        this.balance = balance;
        this.blocked = blocked;
        this.pinAttempts = 0;
        this.lastAttemptTime = LocalDateTime.now();
        this.blockTime = blockTime;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getPinCode() {
        return pinCode;
    }

    public double getBalance() {
        return balance;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
        if (blocked) {
            this.blockTime = LocalDateTime.now();
        } else {
            this.blockTime = null;
        }
    }

    public LocalDateTime getBlockTime() {
        return blockTime;
    }

    public void incrementPinAttempts() {
        pinAttempts++;
        if (pinAttempts >= 3) {
            blocked = true;
            this.blockTime = LocalDateTime.now();
            System.out.println("Card " + cardNumber + " has been blocked due to 3 incorrect PIN attempts.");
        }
    }

    public void resetPinAttempts() {
        pinAttempts = 0;
        blocked = false;
        lastAttemptTime = LocalDateTime.now();
        this.blockTime = null;
    }

    public boolean checkPinCode(String enteredPinCode) {
        if (blocked) {
            System.out.println("Card " + cardNumber + " is blocked. Contact customer support.");
            return false;
        }

        boolean correct = enteredPinCode.equals(pinCode);
        if (!correct) {
            incrementPinAttempts();
        } else {
            resetPinAttempts();
        }
        return correct;
    }

    public boolean isUnlockable() {
        if (blocked && blockTime != null) {
            LocalDateTime now = LocalDateTime.now();
            long hoursSinceBlock = ChronoUnit.HOURS.between(blockTime, now);
            return hoursSinceBlock >= 24;
        }
        return false;
    }

    public boolean withdraw(double amount) {
        System.out.println("Your balance: " + balance);
        if (amount <= 0) {
            System.out.println("Invalid amount. Please enter a positive number.");
            return false;
        }

        if (blocked) {
            System.out.println("Card " + cardNumber + " is blocked. Contact customer support.");
            return false;
        }

        if (amount > balance) {
            System.out.println("Insufficient funds.");
            return false;
        }

        balance -= amount;
        System.out.println("Withdrawal successful. New balance: " + balance);
        return true;
    }

    public boolean deposit(double amount) {
        System.out.println("Your balance: " + balance);
        if (amount <= 0 || amount > 1_000_000) {
            System.out.println("Invalid amount. Please enter a positive number up to 1,000,000.");
            return false;
        }

        if (blocked) {
            System.out.println("Card " + cardNumber + " is blocked. Contact customer support.");
            return false;
        }

        balance += amount;
        System.out.println("Deposit successful. New balance: " + balance);
        return true;
    }

    @Override
    public String toString() {
        String blockInfo = blocked ? "Blocked since " + blockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "Not blocked";
        return "Card: " + cardNumber + ", PIN Code: " + pinCode + ", Balance: " + balance + ", " + blockInfo;
    }

    public String toFileString() {
        String blockInfo = blocked ? "true " + blockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "false null";
        return cardNumber + " " + pinCode + " " + balance + " " + blockInfo;
    }
}

class CardManager {
    private static final String FILE_NAME = "cards.txt";
    private List<Card> cards;
    private Scanner scanner;
    private int pinAttempts;

    public CardManager() {
        this.cards = new ArrayList<>();
        this.scanner = new Scanner(System.in);
        loadCardsFromFile();
        pinAttempts = 3;
    }

    private void loadCardsFromFile() {
        String directoryPath = System.getProperty("user.dir");
        File file = new File(directoryPath, FILE_NAME);

        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split(" ");
                if (parts.length >= 5) {
                    String cardNumber = parts[0];
                    String pinCode = parts[1];
                    double balance = Double.parseDouble(parts[2]);
                    boolean blocked = Boolean.parseBoolean(parts[3]);
                    LocalDateTime blockTime = parts[4].equals("null") ? null : LocalDateTime.parse(parts[4] + " " + parts[5], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    Card card = new Card(cardNumber, pinCode, balance, blocked, blockTime);
                    cards.add(card);
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("Cards file not found. Starting with an empty card list.");
        }
    }

    public void addCard(String cardNumber, String pinCode, double balance) {
        if (!isValidCardNumber(cardNumber)) {
            System.out.println("Invalid card number format. It should be in the format XXXX-XXXX-XXXX-XXXX.");
            return;
        }

        if (isCardNumberUnique(cardNumber)) {
            if (pinCode.length() != 4 || !pinCode.matches("\\d{4}")) {
                System.out.println("PIN Code must be exactly 4 digits.");
                return;
            }

            Card newCard = new Card(cardNumber, pinCode, balance, false, null);
            cards.add(newCard);
            saveCardsToFile();
        } else {
            System.out.println("Card with this number already exists.");
        }
    }

    public double getBalance(String cardNumber, String pinCode) {
        Card card = findCard(cardNumber);
        if (card != null) {
            if (card.isBlocked()) {
                System.out.println("Card " + card.getCardNumber() + " is blocked. Contact customer support.");
                return -1;
            }
            while (true) {
                if (card.checkPinCode(pinCode)) {
                    return card.getBalance();
                } else {
                    pinAttempts--;
                    System.out.println("Invalid PIN Code. Attempts left: " + pinAttempts);
                    if (pinAttempts == 0) {
                        card.setBlocked(true);
                        System.out.println("Card " + card.getCardNumber() + " has been blocked due to 3 incorrect PIN attempts.");
                        break;
                    }
                    pinCode = scanner.nextLine();
                }
            }
        } else {
            System.out.println("Card not found.");
        }
        return -1; // Card not found or blocked
    }

    public boolean withdraw(String cardNumber, String pinCode, double amount) {
        Card card = findCard(cardNumber);
        if (card != null) {
            if (card.isBlocked()) {
                System.out.println("Card " + card.getCardNumber() + " is blocked. Contact customer support.");
                return false;
            }
            while (true) {
                if (card.checkPinCode(pinCode)) {
                    return card.withdraw(amount);
                } else {
                    pinAttempts--;
                    System.out.println("Invalid PIN Code. Attempts left: " + pinAttempts);
                    if (pinAttempts == 0) {
                        card.setBlocked(true);
                        System.out.println("Card " + card.getCardNumber() + " has been blocked due to 3 incorrect PIN attempts.");
                        break;
                    }
                    pinCode = scanner.nextLine();
                }
            }
        } else {
            System.out.println("Card not found.");
        }
        return false;
    }

    public boolean deposit(String cardNumber, String pinCode, double amount) {
        Card card = findCard(cardNumber);
        if (card != null) {
            if (card.isBlocked()) {
                System.out.println("Card " + card.getCardNumber() + " is blocked. Contact customer support.");
                return false;
            }
            while (true) {
                if (card.checkPinCode(pinCode)) {
                    return card.deposit(amount);
                } else {
                    pinAttempts--;
                    System.out.println("Invalid PIN Code. Attempts left: " + pinAttempts);
                    if (pinAttempts == 0) {
                        card.setBlocked(true);
                        System.out.println("Card " + card.getCardNumber() + " has been blocked due to 3 incorrect PIN attempts.");
                        break;
                    }
                    pinCode = scanner.nextLine();
                }
            }
        } else {
            System.out.println("Card not found.");
        }
        return false;
    }

    public void readAllCards() {
        for (Card card : cards) {
            System.out.println(card);
        }
    }

    public void saveCardsToFile() {
        String directoryPath = System.getProperty("user.dir");
        File file = new File(directoryPath, FILE_NAME);

        try (FileWriter writer = new FileWriter(file)) {
            for (Card card : cards) {
                writer.write(card.toFileString() + "\n");
            }
        } catch (IOException e) {
            System.out.println("An error occurred while writing to the file.");
            e.printStackTrace();
        }
    }

    public void unlockCardsIfPossible() {
        for (Card card : cards) {
            if (card.isUnlockable()) {
                card.setBlocked(false);
                System.out.println("Card " + card.getCardNumber() + " has been automatically unlocked.");
            }
        }
        saveCardsToFile();
    }

    private boolean isValidCardNumber(String cardNumber) {
        return cardNumber.matches("\\d{4}-\\d{4}-\\d{4}-\\d{4}");
    }

    private boolean isCardNumberUnique(String cardNumber) {
        for (Card card : cards) {
            if (card.getCardNumber().equals(cardNumber)) {
                return false;
            }
        }
        return true;
    }

    private Card findCard(String cardNumber) {
        for (Card card : cards) {
            if (card.getCardNumber().equals(cardNumber)) {
                return card;
            }
        }
        return null;
    }
}

public class ATMApp {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        CardManager cardManager = new CardManager();
        System.out.println("Welcome to the ATM App!");
        boolean running = true;
        while (running) {
            System.out.println("Select an action:");
            System.out.println("1 - Add a card");
            System.out.println("2 - Check balance");
            System.out.println("3 - Withdraw funds");
            System.out.println("4 - Deposit funds");
            System.out.println("5 - Show all cards");
            System.out.println("Type 'exit' to quit");

            String input = scanner.nextLine();

            switch (input) {
                case "exit":
                    running = false;
                    System.out.println("Exiting the program.");
                    break;

                case "1":
                    System.out.println("Enter card number (format XXXX-XXXX-XXXX-XXXX):");
                    String cardNumber = scanner.nextLine();

                    if (!cardNumber.matches("\\d{4}-\\d{4}-\\d{4}-\\d{4}")) {
                        System.out.println("Invalid card number format. It should be in the format XXXX-XXXX-XXXX-XXXX.");
                        break;
                    }

                    System.out.println("Enter PIN Code (4 digits):");
                    String pinCode = scanner.nextLine();

                    double balance;
                    try {
                        System.out.println("Enter balance:");
                        balance = Double.parseDouble(scanner.nextLine());
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input. Please enter a valid number.");
                        break;
                    }

                    cardManager.addCard(cardNumber, pinCode, balance);
                    break;

                case "2":
                    System.out.println("Enter card number:");
                    String checkCardNumber = scanner.nextLine();

                    if (!checkCardNumber.matches("\\d{4}-\\d{4}-\\d{4}-\\d{4}")) {
                        System.out.println("Invalid card number format. It should be in the format XXXX-XXXX-XXXX-XXXX.");
                        break;
                    }

                    System.out.println("Enter PIN Code:");
                    String checkPinCode = scanner.nextLine();

                    double cardBalance = cardManager.getBalance(checkCardNumber, checkPinCode);
                    if (cardBalance != -1) {
                        System.out.println("Card balance: " + cardBalance);
                    } else {
                        System.out.println("Invalid card number or PIN Code.");
                    }
                    break;

                case "3":
                    System.out.println("Enter card number:");
                    String withdrawCardNumber = scanner.nextLine();

                    if (!withdrawCardNumber.matches("\\d{4}-\\d{4}-\\d{4}-\\d{4}")) {
                        System.out.println("Invalid card number format. It should be in the format XXXX-XXXX-XXXX-XXXX.");
                        break;
                    }

                    System.out.println("Enter PIN Code:");
                    String withdrawPinCode = scanner.nextLine();

                    double withdrawAmount;
                    try {
                        System.out.println("Enter amount to withdraw:");
                        withdrawAmount = Double.parseDouble(scanner.nextLine());
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input. Please enter a valid number.");
                        break;
                    }

                    if (cardManager.withdraw(withdrawCardNumber, withdrawPinCode, withdrawAmount)) {
                        cardManager.saveCardsToFile();
                    }
                    break;

                case "4":
                    System.out.println("Enter card number:");
                    String depositCardNumber = scanner.nextLine();

                    if (!depositCardNumber.matches("\\d{4}-\\d{4}-\\d{4}-\\d{4}")) {
                        System.out.println("Invalid card number format. It should be in the format XXXX-XXXX-XXXX-XXXX.");
                        break;
                    }

                    System.out.println("Enter PIN Code:");
                    String depositPinCode = scanner.nextLine();

                    double depositAmount;
                    try {
                        System.out.println("Enter amount to deposit (up to 1,000,000):");
                        depositAmount = Double.parseDouble(scanner.nextLine());
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input. Please enter a valid number.");
                        break;
                    }

                    if (cardManager.deposit(depositCardNumber, depositPinCode, depositAmount)) {
                        cardManager.saveCardsToFile();
                    }
                    break;

                case "5":
                    cardManager.readAllCards();
                    break;

                default:
                    System.out.println("Invalid choice. Please enter a number (1-5) or 'exit' to quit.");
                    break;
            }

            cardManager.unlockCardsIfPossible();
        }

        scanner.close();
    }
}
