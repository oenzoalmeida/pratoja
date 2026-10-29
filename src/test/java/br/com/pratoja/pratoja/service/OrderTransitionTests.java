package br.com.pratoja.pratoja.service;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static br.com.pratoja.pratoja.domain.DomainTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderTransitionTests {
    @Test
    void validatesEveryPairForDeliveryAndPickupThroughTheAdminEntryPoint() {
        for (FulfillmentType type : FulfillmentType.values()) {
            List<OrderStatus> flow = type == FulfillmentType.DELIVERY
                    ? List.of(OrderStatus.RECEIVED, OrderStatus.CONFIRMED, OrderStatus.PREPARING,
                    OrderStatus.READY, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED)
                    : List.of(OrderStatus.RECEIVED, OrderStatus.CONFIRMED, OrderStatus.PREPARING,
                    OrderStatus.READY, OrderStatus.DELIVERED);
            for (OrderStatus from : OrderStatus.values()) {
                for (OrderStatus to : OrderStatus.values()) {
                    OrderRepository orders = mock(OrderRepository.class);
                    RealtimeHub hub = mock(RealtimeHub.class);
                    OrderService service = new OrderService(orders, null, null, null, null, null, null, null, hub);
                    Store store = new Store(); store.setId(1L);
                    br.com.pratoja.pratoja.domain.Order order = new br.com.pratoja.pratoja.domain.Order();
                    order.setStatus(from); order.setFulfillmentType(type);
                    when(orders.findByIdAndStore_Id(5L, 1L)).thenReturn(Optional.of(order));
                    boolean terminal = from == OrderStatus.CANCELLED || from == OrderStatus.DELIVERED;
                    boolean successor = flow.contains(from) && flow.indexOf(to) == flow.indexOf(from) + 1;
                    boolean expected = !terminal && (to == OrderStatus.CANCELLED || successor);
                    String context = type + ": " + from + " -> " + to;
                    if (expected) {
                        assertDoesNotThrow(() -> service.transitionInStore(5L, store, to, "admin"), context);
                        assertEquals(to, order.getStatus(), context);
                        assertEquals(1, order.getStatusHistory().size(), context);
                        verify(orders).save(order);
                        verify(hub).publish(5L, 1L, to.name());
                    } else {
                        assertThrows(IllegalArgumentException.class,
                                () -> service.transitionInStore(5L, store, to, "admin"), context);
                        assertEquals(from, order.getStatus(), context);
                        assertTrue(order.getStatusHistory().isEmpty(), context);
                        verify(orders, never()).save(any());
                        verifyNoInteractions(hub);
                    }
                }
            }
        }
    }
}
