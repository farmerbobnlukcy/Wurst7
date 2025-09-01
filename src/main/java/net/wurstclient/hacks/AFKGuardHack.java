package net.wurstclient.hacks;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.wurstclient.Category;
import net.wurstclient.WurstClient;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.events.TitleScreenListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

public final class AFKGuardHack extends Hack
        implements TitleScreenListener, ChatInputListener, UpdateListener, LeftClickListener, RightClickListener {
    
    // Settings (wire to your Setting classes if you like)
    private boolean listenTitles  = true;
    private boolean listenChat    = true;
    private boolean restoreOnExit = true;
    private boolean releaseAttack = true;
    
    // Phrase lists
    private static final String[] ENTER_AFK = new String[] {
            "you are now afk", "now afk", "[afk]", "afk mode"
    };
    private static final String[] EXIT_AFK = new String[] {
            "you are no longer afk", "no longer afk", "you are no longer idle", "no longer idle"
    };
    
    private final Set<Hack> disabledCombatHacks = new HashSet<Hack>();
    private boolean currentlyAfk = false;
    
    public AFKGuardHack() {
        super("AFKGuard");
        setCategory(Category.COMBAT); // pick whatever category you prefer
    }
    
    @Override
    public void onEnable() {
        EVENTS.add(UpdateListener.class, this);
        EVENTS.add(ChatInputListener.class, this);
        EVENTS.add(TitleScreenListener.class, this); // remove if you don't use titles
        EVENTS.add(LeftClickListener.class, this);
        EVENTS.add(RightClickListener.class, this);
    }
    
    @Override
    public void onDisable() {
        EVENTS.remove(UpdateListener.class, this);
        EVENTS.remove(ChatInputListener.class, this);
        EVENTS.remove(TitleScreenListener.class, this);
        EVENTS.remove(LeftClickListener.class, this);
        EVENTS.remove(RightClickListener.class, this);
        
        if (currentlyAfk && restoreOnExit)
            restorePreviouslyDisabled();
        currentlyAfk = false;
        disabledCombatHacks.clear();
    }
    
    /* ===== Title detection ===== */
    @Override
    public void onTitle(Text text, boolean isSubtitle) {
        if (!listenTitles || text == null) return;
        handleMessage(text.getString());
    }
    
    @Override
    public void onTitleClear() {
        if (currentlyAfk) leaveAfk();
    }
    
    /* ===== Chat detection ===== */
    @Override
    public void onReceivedMessage(ChatInputEvent event) {
        String chatlines = event.getChatLines() != null ? event.getChatLines().toString() : null;
        if (!listenChat || chatlines == null) return;
        handleMessage(chatlines);
    }
    
    /* ===== Input suppression while AFK ===== */
    @Override
    public void onLeftClick(LeftClickEvent event) {
        if (currentlyAfk) event.cancel();
    }
    
    @Override
    public void onRightClick(RightClickEvent event) {
        if (currentlyAfk) event.cancel();
    }
    
    /* ===== Tick safety (optional) ===== */
    @Override
    public void onUpdate() {
        if (!currentlyAfk || !releaseAttack) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }
    }
    
    /* ===== Core logic ===== */
    private void handleMessage(String raw) {
        if (raw == null) return;
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim();
        
        if (!currentlyAfk && containsAny(s, ENTER_AFK)) {
            enterAfk();
            return;
        }
        if (currentlyAfk && containsAny(s, EXIT_AFK)) {
            leaveAfk();
        }
    }
    
    private static boolean containsAny(String s, String[] needles) {
        for (int i = 0; i < needles.length; i++) {
            String n = needles[i];
            if (n != null && n.length() > 0 && s.contains(n))
                return true;
        }
        return false;
    }
    
    private void enterAfk() {
        currentlyAfk = true;
        disableAllCombatHacks();
    }
    
    private void leaveAfk() {
        currentlyAfk = false;
        if (restoreOnExit) restorePreviouslyDisabled();
    }
    
    /* ===== Disable / Restore ===== */
    private void disableAllCombatHacks() {
        for (Hack h : WurstClient.INSTANCE.getHax().getAllHax()) {
            if (h == this) continue;
            if (h.getCategory() != Category.COMBAT) continue;
            if (!h.isEnabled()) continue;
            
            disabledCombatHacks.add(h);
            tryDisable(h);
        }
    }
    
    private void restorePreviouslyDisabled() {
        for (Hack h : disabledCombatHacks) {
            tryEnable(h);
        }
        disabledCombatHacks.clear();
    }
    
    private static void tryDisable(Hack h) {
        try {
            h.setEnabled(false);
        } catch (Throwable t1) {
            try { System.out.println("Error disabling hack: " + h.getName()); } catch (Throwable t2) { /* last resort */ }
        }
    }
    
    private static void tryEnable(Hack h) {
        try {
            h.setEnabled(true);
        } catch (Throwable t1) {
            try { System.out.println("Error enabling hack: " + h.getName()); } catch (Throwable t2) { /* last resort */ }
        }
    }
}
