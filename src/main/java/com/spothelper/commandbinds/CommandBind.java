package com.spothelper.commandbinds;

/**
 * Один бинд: команда + клавиша + режим циклического выполнения.
 */
public class CommandBind {

    /** Команда без слэша (например, "spawn"). */
    public String command;

    /** Код клавиши GLFW (например, GLFW_KEY_K = 75). 0 = не назначена. */
    public int keyCode;

    /** true — циклический бинд (работает как переключатель); false — однократный. */
    public boolean cyclic;

    /** Интервал в секундах для циклического режима. */
    public int intervalSeconds;

    /** Рантайм: активно ли циклическое выполнение (переключатель). */
    public transient boolean active;

    /** Рантайм: тиков до следующего запуска. */
    public transient int ticksUntilNext;

    /** Рантайм: была ли клавиша зажата в прошлом тике. */
    public transient boolean wasPressed;

    public CommandBind() {
        this.command = "";
        this.keyCode = 0;
        this.cyclic = false;
        this.intervalSeconds = 5;
        this.active = false;
        this.ticksUntilNext = 0;
        this.wasPressed = false;
    }

    public CommandBind(String command, int keyCode, boolean cyclic, int intervalSeconds) {
        this.command = command;
        this.keyCode = keyCode;
        this.cyclic = cyclic;
        this.intervalSeconds = intervalSeconds;
        this.active = false;
        this.ticksUntilNext = 0;
        this.wasPressed = false;
    }
}