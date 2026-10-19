package com.spothelper.waypoints;

public class Waypoint {
    public String name = "";
    public int x, y, z;
    public int color = 0xFF5555;      // RGB
    public String server = "";        // ключ сервера (адрес / singleplayer)
    public String dim = "";           // minecraft:overworld, minecraft:the_nether ...
    public boolean visible = true;

    public boolean event = false;     // метка создана кликом по ивенту
    public int anarchy = 0;           // номер анархии (0 — не привязана)
    public long expiresAt = 0L;       // мс с эпохи; 0 — не удалять автоматически

    public Waypoint() {}

    public Waypoint(String name, int x, int y, int z, int color, String server, String dim) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
        this.server = server;
        this.dim = dim;
    }
}
