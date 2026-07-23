package ru.kiviuly.mg;

import java.sql.SQLException;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.kiviuly.mg.api.MgCore;
import ru.kiviuly.mg.api.game.Minigame;
import ru.kiviuly.mg.api.net.TransportService;
import ru.kiviuly.mg.arena.ArenaManager;
import ru.kiviuly.mg.command.MinigameCommand;
import ru.kiviuly.mg.game.TemplateGame;
import ru.kiviuly.mg.net.LocalTransport;
import ru.kiviuly.mg.listener.ChatListener;
import ru.kiviuly.mg.listener.GameListener;
import ru.kiviuly.mg.listener.ProtectionListener;
import ru.kiviuly.mg.listener.SetupListener;
import ru.kiviuly.mg.menu.MenuListener;
import ru.kiviuly.mg.stats.StatsRepository;
import ru.kiviuly.mg.api.util.DebugLog;
import ru.kiviuly.mg.api.util.DebugLog.Cat;
import ru.kiviuly.mg.api.util.Keys;
import ru.kiviuly.mg.api.util.Msg;

/**
 * MgCore — платформа мини-игр Kiviuly. Ядро игро-независимо: конкретная игра
 * подключается через {@link Minigame}. В этой (переходной) версии игра всё ещё
 * регистрируется прямо здесь через {@code new TemplateGame(this)}; на следующем
 * шаге ядро начнёт публиковать фасад {@code MgCore} в {@code ServicesManager}, и
 * игры станут отдельными плагинами, регистрирующими свой {@link Minigame} снаружи.
 * См. docs/06-roadmap.md (Фаза 0 → Фаза 1).
 */
public final class MgCorePlugin extends JavaPlugin implements MgCore
{
    private ArenaManager arenaManager;
    private StatsRepository statsRepository;
    private final TransportService transport = new LocalTransport();
    private Minigame game;

    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        Keys.init(this);
        Msg.init(this);
        DebugLog.init(this);

        arenaManager = new ArenaManager(this);
        statsRepository = new StatsRepository(this);
        try {statsRepository.open();}
        catch (SQLException e) {getLogger().severe("Failed to open stats.db: " + e.getMessage());}

        // Публикуем фасад платформы: игровые плагины берут его через
        // getServicesManager().load(MgCore.class) и регистрируют свою игру (Фаза 1).
        getServer().getServicesManager().register(MgCore.class, this, this, ServicePriority.Normal);

        // >>> ТОЧКА РАСШИРЕНИЯ: пока заглушка TemplateGame (регистрируется через тот же
        //     register(), что вызовут внешние игры). На Фазе 1 её заменит внешний плагин. <<<
        register(new TemplateGame(this));

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new GameListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new SetupListener(this), this);

        MinigameCommand command = new MinigameCommand(this);
        var mg = getCommand("mg");
        mg.setExecutor(command);
        mg.setTabCompleter(command);

        // Загрузку арен откладываем на первый тик: миры арен (в т.ч. загружаемые ДРУГИМИ
        // плагинами — Multiverse и т.п.) к этому моменту уже подняты. Иначе Location с
        // ещё-не-загруженным миром роняет парсинг YAML и весь плагин при onEnable.
        getServer().getScheduler().runTask(this, () ->
        {
            arenaManager.loadAll();
            getLogger().info("MgCore: arenas loaded: " + arenaManager.all().size());
        });

        getLogger().info("MgCore enabled, game: " + game.id());
        DebugLog.log(Cat.ADMIN, "plugin enable game=%s", game.id());
    }

    @Override
    public void onDisable()
    {
        DebugLog.log(Cat.ADMIN, "plugin disable");
        if (arenaManager != null) {arenaManager.stopAll();}
        saveEverything();
        if (statsRepository != null) {statsRepository.close();}
    }

    public void saveEverything()
    {
        if (arenaManager != null) {arenaManager.saveAll();}
    }

    public void reloadEverything()
    {
        reloadConfig();
        Msg.reload();
        DebugLog.reload();
        arenaManager.loadAll();
        if (game != null) {game.onReload();}
    }

    // ===== MgCore (фасад для игровых плагинов) =====

    @Override
    public void register(Minigame game)
    {
        this.game = game;
        getLogger().info("MgCore: registered game '" + game.id() + "'");
    }

    // Возвращают КОНКРЕТНЫЕ типы (ковариантно переопределяют MgCore.arenas()/stats()):
    // внутренний код ядра пользуется методами сверх контракта (bind/create/…).
    @Override public ArenaManager arenas() {return arenaManager;}
    @Override public StatsRepository stats() {return statsRepository;}
    @Override public TransportService transport() {return transport;}

    /** Зарегистрированная игра (или null). */
    public Minigame game() {return game;}
}
