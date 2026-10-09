package tictac7x.motherlode;

import java.util.HashMap;
import java.util.Map;
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
import net.runelite.client.plugins.loottracker.PluginLootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OreLootTrackerTest {
    private Client client;
    private EventBus eventBus;
    private TicTac7xMotherlodePlugin source;
    private OreLootTracker tracker;
    private boolean enabled;

    @Before
    public void setup() {
        client = mock(Client.class);
        eventBus = mock(EventBus.class);
        source = new TicTac7xMotherlodePlugin();
        enabled = true;
        TicTac7xMotherlodeConfig config = new TicTac7xMotherlodeConfig() {
            @Override
            public boolean trackOresFound() { return enabled; }
        };
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getMapRegions()).thenReturn(new int[] {14679, 14935});
        when(client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT)).thenReturn(80);
        tracker = new OreLootTracker(client, config, eventBus, source);
        tracker.initialize();
        beforeInventory();
    }

    @Test
    public void recordsOnlyNewRewardsUnderBuiltInEventName() {
        beforeInventory(item(ItemID.COAL, 1), item(ItemID.MOTHERLODE_NUGGET, 4), item(ItemID.RUNITE_ORE, 1));
        sack(60);
        inventory(item(ItemID.COAL, 1), item(ItemID.COAL, 1), item(ItemID.COAL, 1),
            item(ItemID.MOTHERLODE_NUGGET, 8), item(ItemID.RUNITE_ORE, 1),
            item(ItemID.RUNITE_ORE, 1), item(ItemID.ADAMANTITE_ORE, 1),
            item(ItemID.MITHRIL_ORE, 1), item(ItemID.GOLD_ORE, 1),
            item(ItemID.UNCUT_DIAMOND, 1), item(-1, 0));

        ArgumentCaptor<PluginLootReceived> captor = ArgumentCaptor.forClass(PluginLootReceived.class);
        verify(eventBus).post(captor.capture());
        PluginLootReceived event = captor.getValue();
        assertSame(source, event.getSource());
        assertEquals("Motherlode Mine", event.getName());
        assertEquals(LootRecordType.EVENT, event.getType());
        Map<Integer, Integer> actual = new HashMap<>();
        for (ItemStack reward : event.getItems()) actual.put(reward.getId(), reward.getQuantity());
        Map<Integer, Integer> expected = new HashMap<>();
        expected.put(ItemID.COAL, 2);
        expected.put(ItemID.MOTHERLODE_NUGGET, 4);
        expected.put(ItemID.RUNITE_ORE, 1);
        expected.put(ItemID.ADAMANTITE_ORE, 1);
        expected.put(ItemID.MITHRIL_ORE, 1);
        expected.put(ItemID.GOLD_ORE, 1);
        assertEquals(expected, actual);
    }

    @Test
    public void ignoresBankWithdrawalsAndSackDeposits() {
        inventory(item(ItemID.COAL, 10));
        sack(90);
        inventory(item(ItemID.COAL, 20));
        verifyNoInteractions(eventBus);
    }

    @Test
    public void repeatedInventoryAndVarbitNotificationsDoNotDuplicateLoot() {
        sack(60);
        sack(60);
        inventory(item(ItemID.COAL, 20));
        inventory(item(ItemID.COAL, 20));
        verify(eventBus, times(1)).post(any(PluginLootReceived.class));
    }

    @Test
    public void recordsSuccessiveCollectionsSeparately() {
        sack(60);
        inventory(item(ItemID.COAL, 20));
        tracker.onGameTick();
        beforeInventory();
        sack(40);
        inventory(item(ItemID.GOLD_ORE, 20));
        verify(eventBus, times(2)).post(any(PluginLootReceived.class));
    }

    @Test
    public void unrelatedContainerDoesNotConsumePendingWithdrawal() {
        sack(60);
        tracker.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, container(item(ItemID.COAL, 99))));
        inventory(item(ItemID.COAL, 20));
        verify(eventBus).post(any(PluginLootReceived.class));
    }

    @Test
    public void disabledSettingDoesNotPublishOrLeavePendingLoot() {
        enabled = false;
        sack(60);
        enabled = true;
        inventory(item(ItemID.COAL, 20));
        sack(40);
        enabled = false;
        inventory(item(ItemID.COAL, 20));
        verifyNoInteractions(eventBus);
    }

    @Test
    public void missingInventoryDoesNotCreateLoot() {
        when(client.getItemContainer(InventoryID.INV)).thenReturn(null);
        sack(60);
        inventory(item(ItemID.COAL, 20));
        verifyNoInteractions(eventBus);
    }

    @Test
    public void emptyOrNegativeDifferencesDoNotPublish() {
        beforeInventory(item(ItemID.COAL, 20));
        sack(60);
        inventory(item(ItemID.COAL, 10), item(ItemID.UNCUT_DIAMOND, 1));
        verifyNoInteractions(eventBus);
    }

    @Test
    public void pendingWithdrawalExpiresBeforeLaterBankWithdrawal() {
        sack(60);
        tracker.onGameTick();
        inventory(item(ItemID.COAL, 20));
        verifyNoInteractions(eventBus);
    }

    @Test
    public void resetDiscardsPendingWithdrawalAndDoesNotTreatLoginAsLoot() {
        sack(60);
        tracker.reset();
        sack(20);
        inventory(item(ItemID.COAL, 20));
        tracker.initialize();
        inventory(item(ItemID.COAL, 40));
        verifyNoInteractions(eventBus);
    }

    @Test
    public void ignoresRewardsOutsideMineOrWhileLoggedOut() {
        when(client.getMapRegions()).thenReturn(new int[] {12850});
        sack(60);
        inventory(item(ItemID.COAL, 20));
        when(client.getMapRegions()).thenReturn(new int[] {14679});
        tracker.initialize();
        sack(40);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        inventory(item(ItemID.COAL, 20));
        verifyNoInteractions(eventBus);
    }

    private void sack(int size) {
        when(client.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT)).thenReturn(size);
        VarbitChanged event = new VarbitChanged();
        event.setVarbitId(VarbitID.MOTHERLODE_SACK_TRANSMIT);
        tracker.onVarbitChanged(event);
    }

    private void beforeInventory(Item... items) {
        ItemContainer before = container(items);
        when(client.getItemContainer(InventoryID.INV)).thenReturn(before);
    }

    private void inventory(Item... items) {
        tracker.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, container(items)));
    }

    private static Item item(int id, int quantity) { return new Item(id, quantity); }

    private static ItemContainer container(Item... items) {
        ItemContainer container = mock(ItemContainer.class);
        when(container.getItems()).thenReturn(items);
        return container;
    }
}
