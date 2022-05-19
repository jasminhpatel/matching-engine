package com.solfini.matchengine;

public class MessageValidator {
    String currentSender = null;

    // Returns true if the message sender and the current sender is same. False
    // otherwise.
    public boolean validate(final String sender) {
        if (currentSender == null) {
            return true;
        }

        if (sender == null) {
            return true;
        }

        return currentSender.equals(sender);
    }

    // Returns the current sender that is set.
    public String getCurrentSender() {
        return currentSender;
    }

    // Sets the current sender.
    public void setCurrentSender(final String compId) {
        currentSender = compId;
    }
}
