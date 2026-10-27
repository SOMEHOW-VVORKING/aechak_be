package com.aechak.admin.support;

import com.aechak.application.pii.support.PiiStringCodec;
import com.aechak.domain.order.claim.Claim;
import com.aechak.domain.order.claim.ClaimItem;
import com.aechak.domain.order.claim.enums.ClaimReasonCode;
import com.aechak.domain.order.claim.enums.ClaimType;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;
import com.aechak.domain.order.group.DeliveryAddressSnapshot;
import com.aechak.domain.order.group.OrderGroup;
import com.aechak.domain.order.order.Order;
import com.aechak.domain.order.order.OrderItem;
import com.aechak.domain.order.order.enums.OrderStatus;
import com.aechak.domain.product.category.Category;
import com.aechak.domain.product.option.OptionCombination;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.product.product.enums.SaleStatus;
import com.aechak.domain.product.version.ProductVersion;
import com.aechak.domain.product.version.enums.VersionChangedBy;
import com.aechak.domain.user.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 어드민 클레임 통합 테스트 공용 베이스 — MockMvc(시큐리티 체인 포함)와 클레임 그래프 시딩을 제공한다.
 *
 * <p>구매자측·셀러측 API는 다른 실행 모듈 소유라 주문·클레임은 도메인과 EntityManager로 직접 만든다.
 * 셀러 처리(승인·거부·환불 집행)도 같은 이유로 애그리거트를 직접 태운다 — 그 전이 규칙은 이 테스트의 대상이 아니다.
 */
public abstract class AdminClaimIntegrationTestBase extends IntegrationTestBase {

    protected static final String BASE = "/api/v1/admin/claims";
    protected static final String PRODUCT_NAME = "멍냥 사료";
    protected static final String OPTION_NAME = "기본 / 1개";

    private static final AtomicInteger SIG = new AtomicInteger();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    protected PiiStringCodec piiStringCodec;

    protected MockMvc mockMvc;
    protected String adminToken;

    /** 시딩한 클레임의 식별자 묶음. */
    public record SeededClaim(String claimPublicId, long claimId, long orderId, String orderPublicId) {}

    @BeforeEach
    void setUpMockMvcAndAdminToken() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        adminToken = mintAccessToken(createUser());
    }

    /** 닉네임을 가진 활성 구매자. 목록의 구매자 검색이 닉네임을 보므로 프로필까지 만든다. */
    protected long seedBuyer(String nickname) {
        return Objects.requireNonNull(tx.execute(status -> {
            User user = User.Companion.preRegister();
            user.completeOnboarding(nickname);
            em.persist(user);
            em.flush();
            return user.getId();
        }));
    }

    protected SeededClaim seedClaim() {
        return seedClaim(ClaimType.CANCEL, 1_000L, "애착상회", seedBuyer("구매자" + SIG.incrementAndGet()));
    }

    protected SeededClaim seedClaim(ClaimType claimType) {
        return seedClaim(claimType, 1_000L, "애착상회", seedBuyer("구매자" + SIG.incrementAndGet()));
    }

    protected SeededClaim seedClaim(ClaimType claimType, long sellerId, String sellerName, long buyerId) {
        return seedClaim(claimType, sellerId, sellerName, buyerId, 0L);
    }

    /**
     * 클레임 1건과 그에 필요한 주문 그래프를 심는다.
     *
     * <p>주문 상태는 클레임 유형이 받아 주는 상태로 맞춘다 — 취소는 발송 전(PAID), 반품·교환은 배송완료다.
     * 유형을 나중에 UPDATE로 바꾸면 그 규칙을 비켜 간 행이 생겨 상세 응답의 전제가 깨진다.
     *
     * <p>shippingFee를 주면 주문그룹·주문의 배송비가 같은 값으로 심긴다 — 환불 예정액이 배송비 환불까지
     * 계산기를 거치는지 보려면 0이 아닌 배송비가 필요하다.
     */
    protected SeededClaim seedClaim(
            ClaimType claimType, long sellerId, String sellerName, long buyerId, long shippingFee) {
        return Objects.requireNonNull(tx.execute(status -> {
            int sig = SIG.incrementAndGet();
            OrderGroup group = OrderGroup.Companion.create(
                    buyerId,
                    0L,
                    new DeliveryAddressSnapshot(
                            piiStringCodec.encrypt("홍길동"),
                            piiStringCodec.encrypt("01012341234"),
                            "06236",
                            "서울시 애착구 멍냥로 1",
                            "101동 1001호",
                            "문 앞에 놓아주세요"),
                    0L,
                    25_000L,
                    shippingFee,
                    "admin-claim-" + sig + "-" + System.nanoTime(),
                    LocalDateTime.now().plusMinutes(10));
            em.persist(group);
            Order order = Order.Companion.create(
                    group, sellerId, sellerName, 0L, shippingFee, shippingFee, null, List.of(orderItem(sellerId, sig)));
            forceOrderStatus(order, claimType == ClaimType.CANCEL ? OrderStatus.PAID : OrderStatus.DELIVERED);
            em.persist(order);
            em.flush();
            Claim claim = Claim.Companion.request(
                    order,
                    claimType,
                    reasonOf(claimType),
                    "받았는데 포장이 터져 있었어요",
                    order.getItems().stream()
                            .map(item -> ClaimItem.Companion.of(item, item.getQuantity(), 25_000L))
                            .toList(),
                    LocalDateTime.now().plusDays(2));
            em.persist(claim);
            em.flush();
            return new SeededClaim(claim.getPublicId(), claim.getId(), order.getId(), order.getPublicId());
        }));
    }

    /** 유형이 받아 주는 사유를 고른다 — 취소는 파손을 사유로 받지 않는다(발송 전이다). */
    private static ClaimReasonCode reasonOf(ClaimType claimType) {
        return claimType == ClaimType.CANCEL ? ClaimReasonCode.CHANGE_OF_MIND : ClaimReasonCode.DEFECTIVE;
    }

    /** 셀러 거부를 심는다 — 운영자가 뒤집는 대상이다. */
    protected void rejectBySeller(String claimPublicId, String reason) {
        tx.executeWithoutResult(status -> loadClaim(claimPublicId).rejectBySeller(reason));
    }

    protected void approveBySeller(String claimPublicId, ReturnCostBearer bearer) {
        tx.executeWithoutResult(status -> loadClaim(claimPublicId).approveBySeller(bearer));
    }

    protected void withdrawByBuyer(String claimPublicId) {
        tx.executeWithoutResult(status -> loadClaim(claimPublicId).withdrawByBuyer());
    }

    /** 환불 집행을 선점만 해 둔다 — 돈이 움직이기 시작한 건을 만든다. */
    protected void markRefundRequested(String claimPublicId) {
        tx.executeWithoutResult(status -> loadClaim(claimPublicId).markRefundRequested());
    }

    protected void markRefunded(String claimPublicId) {
        tx.executeWithoutResult(status -> {
            Claim claim = loadClaim(claimPublicId);
            claim.markRefundRequested();
            claim.markRefunded();
        });
    }

    protected void markRefundFailed(String claimPublicId) {
        tx.executeWithoutResult(status -> {
            Claim claim = loadClaim(claimPublicId);
            claim.markRefundRequested();
            claim.markRefundFailed();
        });
    }

    protected Claim loadClaim(String claimPublicId) {
        return em.createQuery("select c from Claim c where c.publicId = :publicId", Claim.class)
                .setParameter("publicId", claimPublicId)
                .getSingleResult();
    }

    /** 트랜잭션 밖에서 현재 저장 상태를 읽는다 — 부작용 단언이 응답이 아니라 DB를 보게 한다. */
    protected Claim readClaim(String claimPublicId) {
        return Objects.requireNonNull(tx.execute(status -> {
            Claim claim = loadClaim(claimPublicId);
            em.detach(claim);
            return claim;
        }));
    }

    protected long countInterventions(long claimId) {
        return Objects.requireNonNull(tx.execute(status -> em.createQuery(
                        "select count(i) from ClaimIntervention i where i.claimId = :claimId", Long.class)
                .setParameter("claimId", claimId)
                .getSingleResult()));
    }

    protected MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private OrderItem orderItem(long sellerId, int sig) {
        Category category = Category.Companion.create(null, 1, "카테고리-" + sig, null, 1);
        em.persist(category);
        Product product = Product.Companion.register(
                category, sellerId, PRODUCT_NAME, null, null, 25_000L, null, null, null, List.of(), List.of());
        em.persist(product);
        ProductVersion version = ProductVersion.Companion.snapshot(
                product, 1, PRODUCT_NAME, 25_000L, SaleStatus.ON_SALE, "thumb-" + sig, VersionChangedBy.SELLER);
        em.persist(version);
        OptionCombination combo =
                OptionCombination.Companion.create(product, OPTION_NAME, 0L, 100, "sig-" + sig, List.of());
        em.persist(combo);
        em.flush();
        return OrderItem.Companion.of(product.getId(), combo.getId(), OPTION_NAME, 1, 25_000L, 0L, version.getId());
    }

    /** 전이 API를 거치지 않고 상태를 심는다 — 주문 전이 규칙은 이 테스트의 대상이 아니다. */
    private void forceOrderStatus(Order order, OrderStatus status) {
        try {
            var field = Order.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(order, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("주문 상태 시딩 실패", e);
        }
    }
}
