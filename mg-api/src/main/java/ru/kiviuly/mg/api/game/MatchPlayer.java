package ru.kiviuly.mg.api.game;

import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * Состояние одного игрока в рамках матча. Живёт только пока идёт сессия.
 * {@code alive} = участвует (не выбыл); выбывший становится спектатором.
 * Игро-специфичные поля храните в {@link Match#data()} под ключом с UUID.
 */
public class MatchPlayer
{
    private final UUID uuid;
    private final String name;
    private boolean alive = true;
    private int kills = 0;

    public MatchPlayer(Player p)
    {
        this.uuid = p.getUniqueId();
        this.name = p.getName();
    }

    public UUID getUuid() {return uuid;}
    public String getName() {return name;}
    public boolean isAlive() {return alive;}
    public void setAlive(boolean alive) {this.alive = alive;}
    public int getKills() {return kills;}
    public void addKill() {this.kills++;}
    public void setKills(int kills) {this.kills = kills;}
}
