package br.com.pratoja.pratoja.service;

import br.com.pratoja.pratoja.domain.DomainTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record OrderView(
        Long id,
        DomainTypes.OrderStatus status,
        DomainTypes.FulfillmentType fulfillmentType,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal total,
        String notes,
        LocalDateTime createdAt,
        String address,
        DomainTypes.PaymentMethod paymentMethod,
        BigDecimal changeFor,
        List<Item> items,
        List<Step> history,
        boolean canReview,
        boolean reviewed
) {
    public record Item(String name, int quantity, BigDecimal unitPrice, BigDecimal total, String notes, List<String> options) {}
    public record Step(DomainTypes.OrderStatus status, LocalDateTime at) {}
    public record TimelineStep(DomainTypes.OrderStatus status, String label, LocalDateTime at, String state, String icon) {}

    public String statusLabel() {
        return labelFor(status);
    }

    public String number() {
        return String.format("#%04d", id);
    }

    public List<TimelineStep> timeline() {
        List<DomainTypes.OrderStatus> flow = fulfillmentType == DomainTypes.FulfillmentType.DELIVERY
                ? List.of(DomainTypes.OrderStatus.RECEIVED, DomainTypes.OrderStatus.CONFIRMED,
                DomainTypes.OrderStatus.PREPARING, DomainTypes.OrderStatus.READY,
                DomainTypes.OrderStatus.OUT_FOR_DELIVERY, DomainTypes.OrderStatus.DELIVERED)
                : List.of(DomainTypes.OrderStatus.RECEIVED, DomainTypes.OrderStatus.CONFIRMED,
                DomainTypes.OrderStatus.PREPARING, DomainTypes.OrderStatus.READY,
                DomainTypes.OrderStatus.DELIVERED);

        Map<DomainTypes.OrderStatus, LocalDateTime> times = new EnumMap<>(DomainTypes.OrderStatus.class);
        history.forEach(step -> times.put(step.status(), step.at()));
        List<TimelineStep> result = new ArrayList<>();

        if (status == DomainTypes.OrderStatus.CANCELLED) {
            for (DomainTypes.OrderStatus step : flow) {
                if (!times.containsKey(step)) break;
                result.add(new TimelineStep(step, labelFor(step), times.get(step), "done", iconFor(step)));
            }
            result.add(new TimelineStep(status, labelFor(status), times.get(status), "cancelled", iconFor(status)));
            return result;
        }

        int current = flow.indexOf(status);
        for (int index = 0; index < flow.size(); index++) {
            DomainTypes.OrderStatus step = flow.get(index);
            String state = index < current ? "done" : index == current ? "current" : "upcoming";
            result.add(new TimelineStep(step, labelFor(step), times.get(step), state, iconFor(step)));
        }
        return result;
    }

    private String labelFor(DomainTypes.OrderStatus value) {
        return switch (value) {
            case RECEIVED -> "Pedido recebido";
            case CONFIRMED -> "Confirmado";
            case PREPARING -> "Em preparo";
            case READY -> "Pronto";
            case OUT_FOR_DELIVERY -> "Saiu para entrega";
            case DELIVERED -> fulfillmentType == DomainTypes.FulfillmentType.PICKUP ? "Retirado" : "Entregue";
            case CANCELLED -> "Cancelado";
        };
    }

    private String iconFor(DomainTypes.OrderStatus value) {
        return switch (value) {
            case RECEIVED -> "bell";
            case CONFIRMED -> "circle-check";
            case PREPARING -> "chef-hat";
            case READY -> "package";
            case OUT_FOR_DELIVERY -> "truck";
            case DELIVERED -> fulfillmentType == DomainTypes.FulfillmentType.PICKUP ? "store" : "house";
            case CANCELLED -> "circle-x";
        };
    }
}
