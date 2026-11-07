package com.aechak.admin.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.admin.support.FakeFileStorage;
import com.aechak.admin.support.IntegrationTestBase;
import com.aechak.application.file.port.FileStorage;
import com.aechak.domain.sns.comment.Comment;
import com.aechak.domain.sns.post.Post;
import com.aechak.domain.support.Ulid;
import com.aechak.domain.user.social.SocialIdentity;
import com.aechak.domain.user.social.enums.SocialProvider;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 운영자 강제 탈퇴도 자기 탈퇴처럼 SNS 데이터, 프로필 이미지, 소셜 계정 이메일을 정리한다. */
class AdminForceWithdrawalIntegrationTest extends IntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy securityFilterChain;

    @Autowired
    private FileStorage fileStorage;

    private MockMvc mockMvc;
    private String actorToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
        ((FakeFileStorage) fileStorage).reset();
        actorToken = mintAccessToken(createMember("운영자", UserRole.ADMIN));
    }

    @Test
    void 강제_탈퇴하면_게시물과_댓글이_사유_WITHDRAWAL로_지워지고_미디어와_프로필_이미지가_커밋_뒤_지워진다() throws Exception {
        long targetId = createMember("문제회원", UserRole.GENERAL);
        String profileImageKey = "users/profile/" + Ulid.INSTANCE.generate() + ".jpg";
        setProfileImage(targetId, profileImageKey);
        String postId = persistSnsPost(targetId);
        String otherPostId = persistSnsPost(createMember("다른회원", UserRole.GENERAL));
        String commentId = persistSnsComment(targetId, otherPostId);

        forceWithdraw(targetId);

        assertThat(statusAndReason("sns_posts", postId)).isEqualTo("DELETED/WITHDRAWAL");
        assertThat(statusAndReason("sns_comments", commentId)).isEqualTo("DELETED/WITHDRAWAL");
        assertThat(statusAndReason("sns_posts", otherPostId)).isEqualTo("PUBLISHED/null");
        assertThat(((FakeFileStorage) fileStorage).deletedKeys())
                .containsExactlyInAnyOrder(mediaKeyOf(postId), profileImageKey);
    }

    @Test
    void 강제_탈퇴하면_소셜_계정의_이메일을_지우고_재가입_확인용_식별자는_남긴다() throws Exception {
        long targetId = createMember("문제회원", UserRole.GENERAL);
        tx.executeWithoutResult(txStatus -> em.persist(SocialIdentity.Companion.link(
                em.find(User.class, targetId), SocialProvider.KAKAO, "kakao-1", "bad@example.com", null)));

        forceWithdraw(targetId);

        Object[] row = (Object[]) tx.execute(
                txStatus -> em.createNativeQuery("select email, provider_id from social_identities where user_id = :id")
                        .setParameter("id", targetId)
                        .getSingleResult());
        assertThat(row[0]).isNull();
        assertThat(row[1]).isEqualTo("kakao-1");
    }

    private void forceWithdraw(long targetId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/members/" + targetId + "/sanctions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"FORCE_WITHDRAW\",\"reasonCode\":\"FALSE_REPORT\"}"))
                .andExpect(status().isNoContent());
    }

    private long createMember(String nickname, UserRole role) {
        long userId = Objects.requireNonNull(tx.execute(txStatus -> {
            User user = User.Companion.preRegister();
            em.persist(user);
            user.completeOnboarding(nickname);
            em.flush();
            return user.getId();
        }));
        tx.executeWithoutResult(txStatus -> em.createQuery("update User u set u.role = :role where u.id = :id")
                .setParameter("role", role)
                .setParameter("id", userId)
                .executeUpdate());
        return userId;
    }

    private void setProfileImage(long userId, String key) {
        tx.executeWithoutResult(
                txStatus -> em.createQuery("update UserProfile p set p.profileImageKey = :key where p.userId = :id")
                        .setParameter("key", key)
                        .setParameter("id", userId)
                        .executeUpdate());
    }

    private String persistSnsPost(long author) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            String idempotencyKey = Ulid.INSTANCE.generate();
            Post post = Post.Companion.create(
                    author,
                    "산책 사진",
                    List.of("sns/posts/" + idempotencyKey + ".jpg"),
                    null,
                    List.of(),
                    null,
                    idempotencyKey);
            em.persist(post);
            em.flush();
            return post.getPublicId();
        }));
    }

    private String persistSnsComment(long author, String postPublicId) {
        return Objects.requireNonNull(tx.execute(txStatus -> {
            Post post = em.createQuery("select p from Post p where p.publicId = :id", Post.class)
                    .setParameter("id", postPublicId)
                    .getSingleResult();
            Comment comment = Comment.Companion.write(post, null, author, "댓글이에요", Ulid.INSTANCE.generate());
            em.persist(comment);
            em.flush();
            return comment.getPublicId();
        }));
    }

    private String mediaKeyOf(String postPublicId) {
        return (String) tx.execute(txStatus -> em.createNativeQuery("select m.storage_key from sns_post_media m "
                        + "join sns_posts p on p.id = m.post_id where p.public_id = :id")
                .setParameter("id", postPublicId)
                .getSingleResult());
    }

    /** "상태/삭제 사유". 지우지 않았으면 사유는 null 이다. */
    private String statusAndReason(String table, String publicId) {
        Object[] row = (Object[]) Objects.requireNonNull(tx.execute(txStatus -> em.createNativeQuery(
                        "select status, deletion_reason from " + table + " where public_id = :id")
                .setParameter("id", publicId)
                .getSingleResult()));
        return row[0] + "/" + row[1];
    }
}
