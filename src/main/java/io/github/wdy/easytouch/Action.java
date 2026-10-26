package io.github.wdy.easytouch;

public enum Action {
    ATTACK, USE;

    public Action opposite() { return this == ATTACK ? USE : ATTACK; }
}
