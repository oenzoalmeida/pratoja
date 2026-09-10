package br.com.pratoja.pratoja.domain;

public final class DomainTypes {
    private DomainTypes() {}

    public enum Role { CUSTOMER, ADMIN }
    public enum OptionType { BASE, PROTEIN, SIDE, EXTRA, BEVERAGE }
    public enum FulfillmentType { DELIVERY, PICKUP }
    public enum PaymentMethod { PIX, CARD, CASH }
    public enum PaymentStatus { PENDING, CONFIRMED, CASH_ON_DELIVERY, CANCELLED }
    public enum OrderStatus { RECEIVED, CONFIRMED, PREPARING, READY, OUT_FOR_DELIVERY, DELIVERED, CANCELLED }
}
