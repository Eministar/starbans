package dev.eministar.starbans.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class InteractiveGui implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> clickActions = new HashMap<>();

    public InteractiveGui(int size, String title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setAction(int slot, Consumer<InventoryClickEvent> action) {
        clickActions.put(slot, action);
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getRawSlot() < 0 || event.getRawSlot() >= inventory.getSize()) {
            return;
        }

        event.setCancelled(true);
        Consumer<InventoryClickEvent> action = clickActions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }
}
