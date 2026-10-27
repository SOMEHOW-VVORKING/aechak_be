package com.aechak.admin.order;

import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.domain.order.group.DeliveryAddressSnapshot;
import com.aechak.domain.order.group.OrderGroup;
import com.aechak.domain.order.order.Order;
import com.aechak.domain.order.order.OrderItem;
import com.aechak.domain.order.order.OrderStatusHistory;
import com.aechak.domain.order.order.enums.ConfirmType;
import com.aechak.domain.order.order.enums.OrderStatus;
import com.aechak.domain.order.order.enums.TriggeredBy;
import com.aechak.domain.order.shipment.CourierMaster;
import com.aechak.domain.order.shipment.Shipment;
import com.aechak.domain.order.shipment.enums.ShipmentType;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.product.version.ProductVersion;
import com.aechak.domain.seller.seller.Seller;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 어드민 주문 모니터링 통합 테스트 공용 베이스 — 주문·품목·배송을 실제 전이로 심는다.
 *
 * <p>상태는 도메인 전이 메서드로만 만든다. 상태 컬럼을 직접 쓰면 금지 조합(결제대기인데 송장 있음)이
 * 테스트에만 존재해 고정하는 대상이 실제 데이터와 달라진다.
 *
 * <p>주문 시각은 persist 후 네이티브 UPDATE로 뒤로 민다 — created_at은 updatable=false이자
 * 엔티티의 protected set이고, 지연 판정이 그 값으로 떨어지므로 과거 주문을 심을 다른 길이 없다.
 */
abstract class OrderMonitoringTestBase extends IntegrationTestBase {

    protected static final String BASE = "/api/v1/admin/orders";
    protected static final String COURIER_CODE = "CJ";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    protected MockMvc mockMvc;
    protected String adminToken;
    protected long adminUserId;
    protected long buyerId;
    protected long sellerUserId;
    protected long otherSellerUserId;
    private long productVersionId;
    private long productId;
    private long otherProductVersionId;
    private long otherProductId;

    @BeforeEach
    void setUpMonitoringFixture() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminUserId = createUser();
        adminToken = mintAccessToken(adminUserId);
        buyerId = createUser();
        sellerUserId = createUser();
        otherSellerUserId = createUser();

        tx.executeWithoutResult(txStatus -> {
            em.persist(Seller.Companion.open(sellerUserId, "멍멍마켓", 3_000L));
            em.persist(Seller.Companion.open(otherSellerUserId, "냥이상점", 3_000L));
            em.persist(CourierMaster.Companion.register(COURIER_CODE, "CJ대한통운", true, null));

            Category root = Category.Companion.create(null, Category.ROOT_DEPTH, "강아지", null, 1);
            em.persist(root);
            Category mid = Category.Companion.create(root, Category.MID_DEPTH, "건강관리", null, 1);
            em.persist(mid);
            Category leaf = Category.Companion.create(mid, Category.LEAF_DEPTH, "영양제", null, 1);
            em.persist(leaf);

            long[] first = persistProduct(leaf, sellerUserId, "프리미엄 관절 영양제 180정");
            productId = first[0];
            productVersionId = first[1];
            long[] second = persistProduct(leaf, otherSellerUserId, "수제 오리목뼈 간식 500g");
            otherProductId = second[0];
            otherProductVersionId = second[1];
        });
    }

    private long[] persistProduct(Category leaf, long sellerId, String name) {
        Product product = Product.Companion.register(
                leaf, sellerId, name, "설명", "products/thumb.png", 39_000L, null, null, null, List.of(), List.of());
        em.persist(product);
        em.flush();
        ProductVersion version = ProductVersion.Companion.create(product, "products/thumb.png");
        em.persist(version);
        em.flush();
        return new long[] {product.getId(), version.getId()};
    }

    /** 요청 주문 상태까지 실제 전이로 밀어 올린 주문 한 건을 심고 공개 식별자를 돌려준다. */
    protected SeededOrder seedOrder(long sellerId, String sellerName, OrderStatus target, LocalDateTime orderedAt) {
        return seedOrder(sellerId, sellerName, target, orderedAt, 2, 39_000L);
    }

    protected SeededOrder seedOrder(
            long sellerId,
            String sellerName,
            OrderStatus target,
            LocalDateTime orderedAt,
            int quantity,
            long unitPrice) {
        return seedOrder(sellerId, sellerName, target, orderedAt, quantity, unitPrice, 1, 0L);
    }

    /**
     * 품목 수와 안분 쿠폰할인까지 지정한다 — 금액 합이 품목 조인으로 복제되는지, 쿠폰할인이 주문당
     * 한 번만 빠지는지는 품목이 둘 이상이고 할인이 0이 아니어야 드러난다.
     */
    protected SeededOrder seedOrder(
            long sellerId,
            String sellerName,
            OrderStatus target,
            LocalDateTime orderedAt,
            int quantity,
            long unitPrice,
            int itemCount,
            long couponDiscount) {
        SeededOrder seeded = tx.execute(txStatus -> {
            long productAmount = unitPrice * quantity * itemCount;
            OrderGroup group = OrderGroup.Companion.create(
                    buyerId,
                    1L,
                    new DeliveryAddressSnapshot("enc-name", "enc-phone", "06236", "서울 강남구", "101호", null),
                    0L,
                    productAmount,
                    3_000L,
                    UUID.randomUUID().toString(),
                    LocalDateTime.now().plusMinutes(10));
            em.persist(group);

            boolean other = sellerId == otherSellerUserId;
            List<OrderItem> items = new ArrayList<>();
            for (int i = 0; i < itemCount; i++) {
                items.add(OrderItem.Companion.of(
                        other ? otherProductId : productId,
                        1L,
                        "기본",
                        quantity,
                        unitPrice,
                        0L,
                        other ? otherProductVersionId : productVersionId));
            }
            Order order =
                    Order.Companion.create(group, sellerId, sellerName, couponDiscount, 3_000L, 3_000L, null, items);
            em.persist(order);
            em.flush();

            advanceTo(order, target);
            em.flush();
            return new SeededOrder(order.getId(), order.getPublicId());
        });
        backdate(seeded.orderId(), orderedAt);
        return seeded;
    }

    /** 전이마다 이력 행을 함께 남긴다 — 상세의 타임라인이 그 행을 읽는다. */
    private void advanceTo(Order order, OrderStatus target) {
        if (target == OrderStatus.PENDING_PAYMENT) {
            return;
        }
        if (target == OrderStatus.CANCELLED) {
            record(order, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM);
            order.cancelUnpaid();
            return;
        }
        record(order, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, TriggeredBy.SYSTEM);
        order.markPaid();
        if (target == OrderStatus.PAID) {
            return;
        }
        record(order, OrderStatus.PAID, OrderStatus.PREPARING, TriggeredBy.SELLER);
        order.accept();
        if (target == OrderStatus.PREPARING) {
            return;
        }
        record(order, OrderStatus.PREPARING, OrderStatus.DISPATCH_PENDING, TriggeredBy.SELLER);
        order.markDispatchPending();
        if (target == OrderStatus.DISPATCH_PENDING) {
            return;
        }
        record(order, OrderStatus.DISPATCH_PENDING, OrderStatus.SHIPPING, TriggeredBy.SELLER);
        order.markShipping();
        if (target == OrderStatus.SHIPPING) {
            return;
        }
        record(order, OrderStatus.SHIPPING, OrderStatus.DELIVERED, TriggeredBy.SELLER);
        order.markDelivered(LocalDateTime.now().plusDays(7));
        if (target == OrderStatus.DELIVERED) {
            return;
        }
        record(order, OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED, TriggeredBy.BUYER);
        order.confirmPurchase(ConfirmType.MANUAL);
    }

    private void record(Order order, OrderStatus from, OrderStatus to, TriggeredBy triggeredBy) {
        em.persist(OrderStatusHistory.Companion.record(order, from, to, triggeredBy));
    }

    protected void seedShipment(long orderId, String trackingNumber) {
        tx.executeWithoutResult(txStatus -> {
            Order order = em.find(Order.class, orderId);
            CourierMaster courier = em.find(CourierMaster.class, COURIER_CODE);
            em.persist(Shipment.Companion.dispatch(
                    courier, order, trackingNumber, ShipmentType.INITIAL, null, LocalDateTime.now()));
        });
    }

    /** created_at은 updatable=false라 JPA로는 못 옮긴다 — 지연 판정이 이 값으로 떨어지므로 직접 쓴다. */
    protected void backdate(long orderId, LocalDateTime orderedAt) {
        tx.executeWithoutResult(txStatus -> em.createNativeQuery("update orders set created_at = :at where id = :id")
                .setParameter("at", orderedAt)
                .setParameter("id", orderId)
                .executeUpdate());
    }

    protected MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    protected record SeededOrder(long orderId, String publicId) {}
}
