package me.matsubara.realisticvillagers.data;

public enum InteractType {
    GUI,
    FOLLOW_ME,
    STAY_HERE,
    TALKING;

    public boolean isGUI() {
        return this == GUI;
    }

    public boolean isFollowMe() {
        return this == FOLLOW_ME;
    }

    public boolean isStayHere() {
        return this == STAY_HERE;
    }
    
    public boolean isTalking() {
        return this == TALKING;
    }
}