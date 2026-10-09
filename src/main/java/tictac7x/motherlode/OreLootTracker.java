/*
 * Copyright (c) 2018, Seth <Sethtroll3@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Lars <lars.oernlo@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tictac7x.motherlode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.loottracker.PluginLootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/** Publishes sack rewards using RuneLite's built-in Motherlode loot event format. */
final class OreLootTracker {
    private static final Set<Integer> REGIONS = new HashSet<>(Arrays.asList(
        14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192, 15193));
    private static final Set<Integer> REWARDS = new HashSet<>(Arrays.asList(
        ItemID.COAL, ItemID.GOLD_ORE, ItemID.MITHRIL_ORE,
        ItemID.ADAMANTITE_ORE, ItemID.RUNITE_ORE, ItemID.MOTHERLODE_NUGGET));

    private final Client client;
    private final TicTac7xMotherlodeConfig config;
    private final EventBus eventBus;
    private final Plugin source;
    private int previousSackSize = -1;
    private Map<Integer, Integer> inventoryBefore;

    OreLootTracker(Client client, TicTac7xMotherlodeConfig config, EventBus eventBus, Plugin source) {
        this.client = client;
        this.config = config;
        this.eventBus = eventBus;
        this.source = source;
    }

    void initialize() {
        reset();
        if (isInMine()) {
            previousSackSize = client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
        }
    }

    void reset() {
        previousSackSize = -1;
        inventoryBefore = null;
    }

    void onVarbitChanged(VarbitChanged event) {
        if (event.getVarbitId() != VarbitID.MOTHERLODE_SACK_TRANSMIT) return;
        if (!isInMine()) {
            reset();
            return;
        }

        int sackSize = client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
        if (previousSackSize >= 0 && sackSize < previousSackSize && config.trackOresFound()) {
            // The sack varbit changes before the withdrawn rewards reach the inventory.
            ItemContainer inventory = client.getItemContainer(InventoryID.INV);
            inventoryBefore = inventory == null ? null : countRewards(inventory);
        }
        previousSackSize = sackSize;
    }

    void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INV || inventoryBefore == null) return;

        Map<Integer, Integer> before = inventoryBefore;
        inventoryBefore = null;
        if (!isInMine() || !config.trackOresFound()) return;

        List<ItemStack> rewards = new ArrayList<>();
        countRewards(event.getItemContainer()).forEach((id, quantity) -> {
            int gained = quantity - before.getOrDefault(id, 0);
            if (gained > 0) rewards.add(new ItemStack(id, gained));
        });
        if (!rewards.isEmpty()) {
            eventBus.post(PluginLootReceived.builder()
                .source(source)
                .name("Motherlode Mine")
                .type(LootRecordType.EVENT)
                .items(rewards)
                .build());
        }
    }

    void onGameTick() {
        // Sack and inventory updates arrive before GameTick. Do not let a missing
        // inventory update turn a later bank withdrawal into a sack reward.
        inventoryBefore = null;
    }

    private boolean isInMine() {
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        int[] regions = client.getMapRegions();
        if (regions == null || regions.length == 0) return false;
        for (int region : regions) {
            if (!REGIONS.contains(region)) return false;
        }
        return true;
    }

    private static Map<Integer, Integer> countRewards(ItemContainer container) {
        Map<Integer, Integer> rewards = new HashMap<>();
        for (Item item : container.getItems()) {
            if (REWARDS.contains(item.getId())) {
                rewards.merge(item.getId(), item.getQuantity(), Integer::sum);
            }
        }
        return rewards;
    }
}
