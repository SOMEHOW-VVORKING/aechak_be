package com.aechak.api.sns

import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 통합 — 댓글 목록이 최상위 댓글 단위로 페이지를 나누고 대댓글을 부모 아래 붙이며 지운 댓글을 안내 문구 규칙대로 보여주는지.
 */
class CommentListIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `최상위 댓글은 오래된순이고 대댓글은 부모 바로 아래 작성 순서로 붙는다`() {
        val postId = createdPublicId(onboardedUser())
        val root1 = seedComment(onboardedUser(), postId)
        val root2 = seedComment(onboardedUser(), postId)
        val reply1 = seedComment(onboardedUser(), postId, parentPublicId = root1)
        val reply2 = seedComment(onboardedUser(), postId, parentPublicId = root1)

        val items = commentPage(postId).feedItems()

        assertEquals(listOf(root1, reply1, reply2, root2), items.map { it["publicId"] })
        assertEquals(listOf(null, root1, root1, null), items.map { it["parentPublicId"] })
    }

    @Test
    fun `size는 최상위 댓글 수이고 대댓글은 부모와 같은 페이지에 오며 커서로 끝까지 이어 부른다`() {
        val postId = createdPublicId(onboardedUser())
        val root1 = seedComment(onboardedUser(), postId)
        val root2 = seedComment(onboardedUser(), postId)
        val root3 = seedComment(onboardedUser(), postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root2)

        val first = commentPage(postId, size = 2)
        val second = commentPage(postId, cursor = first["nextCursor"] as String, size = 2)

        assertEquals(listOf(root1, root2, reply), first.feedItems().map { it["publicId"] })
        assertEquals(true, first["hasNext"])
        assertEquals(listOf(root3), second.feedItems().map { it["publicId"] })
        assertEquals(false, second["hasNext"])
        assertNull(second["nextCursor"])
    }

    @Test
    fun `다른 게시물의 커서는 400 90002다`() {
        val postId = createdPublicId(onboardedUser())
        repeat(2) { seedComment(onboardedUser(), postId) }
        val cursor = commentPage(postId, size = 1)["nextCursor"] as String

        getComments(createdPublicId(onboardedUser()), cursor = cursor)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `size가 0이거나 101이면 400 90001이다`() {
        val postId = createdPublicId(onboardedUser())

        listOf(0, 101).forEach { size ->
            getComments(postId, size = size)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(90001))
        }
    }

    @Test
    fun `댓글이 없으면 빈 목록이고 다음 페이지가 없다`() {
        val page = commentPage(createdPublicId(onboardedUser()))

        assertEquals(emptyList<Any>(), page.feedItems())
        assertEquals(false, page["hasNext"])
        assertNull(page["nextCursor"])
    }

    @Test
    fun `보이는 댓글은 작성자와 내용을 싣고 고친 댓글은 edited가 true다`() {
        val authorId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val plain = seedComment(authorId, postId, content = "산책 코스 어디예요?")
        val edited = seedComment(authorId, postId, content = "처음 쓴 글", edited = true)

        val items = commentPage(postId, authorId).feedItems().associateBy { it["publicId"] }

        val item = items.getValue(plain)
        assertEquals("ACTIVE", item["status"])
        assertEquals("산책 코스 어디예요?", item["content"])
        assertEquals(publicIdOf(authorId), (item["author"] as Map<*, *>)["publicId"])
        assertEquals(false, item["edited"])
        assertEquals(true, item["replyable"])
        assertEquals(true, item["isMine"])
        assertEquals(0, item["likeCount"])
        assertEquals(false, item["liked"])
        assertEquals("처음 쓴 글 (고침)", items.getValue(edited)["content"])
        assertEquals(true, items.getValue(edited)["edited"])
        assertEquals(false, commentPage(postId, onboardedUser()).feedItems().first()["isMine"])
    }

    @Test
    fun `대댓글이 있는 지운 최상위 댓글은 안내 문구로 남고 대댓글은 그대로이며 답글을 달 수 없다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root)
        deleteCommentRow(root)

        val items = commentPage(postId).feedItems()

        assertEquals(listOf(root, reply), items.map { it["publicId"] })
        assertPlaceholder(items[0], "DELETED")
        assertEquals("ACTIVE", items[1]["status"])
        assertEquals(false, items[1]["replyable"])
    }

    @Test
    fun `대댓글이 없는 지운 댓글과 지운 대댓글은 빠진다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId, deletedBy = CommentDeletionReason.AUTHOR)
        val kept = seedComment(onboardedUser(), postId)
        seedComment(onboardedUser(), postId, parentPublicId = kept, deletedBy = CommentDeletionReason.AUTHOR)

        assertEquals(listOf(kept), visibleCommentIds(postId))
        assertFalse(root in visibleCommentIds(postId))
    }

    @Test
    fun `안내 문구 아래 마지막 대댓글을 지우면 안내 문구도 빠진다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root)
        deleteCommentRow(root)
        deleteCommentRow(reply)

        assertEquals(emptyList(), visibleCommentIds(postId))
    }

    @Test
    fun `운영자가 지운 댓글은 작성자에게도 DELETED이고 탈퇴로 지운 댓글은 WITHDRAWN이다`() {
        val authorId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val byOperator = seedComment(authorId, postId)
        seedComment(onboardedUser(), postId, parentPublicId = byOperator)
        val byWithdrawal = seedComment(onboardedUser(), postId)
        seedComment(onboardedUser(), postId, parentPublicId = byWithdrawal)
        deleteCommentRow(byOperator, CommentDeletionReason.OPERATOR)
        deleteCommentRow(byWithdrawal, CommentDeletionReason.WITHDRAWAL)

        val items = commentPage(postId, authorId).feedItems().associateBy { it["publicId"] }

        assertPlaceholder(items.getValue(byOperator), "DELETED")
        assertPlaceholder(items.getValue(byWithdrawal), "WITHDRAWN")
    }

    @Test
    fun `프로필 행이 없는 작성자의 댓글과 대댓글은 WITHDRAWN이고 그 아래 대댓글도 답글을 달 수 없다`() {
        val withdrawnId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(withdrawnId, postId)
        val reply = seedComment(onboardedUser(), postId, parentPublicId = root)
        val other = seedComment(onboardedUser(), postId)
        val withdrawnReply = seedComment(withdrawnId, postId, parentPublicId = other)
        tx.execute { em.find(User::class.java, withdrawnId).withdraw() }

        val items = commentPage(postId).feedItems().associateBy { it["publicId"] }

        assertPlaceholder(items.getValue(root), "WITHDRAWN")
        assertEquals("ACTIVE", items.getValue(reply)["status"])
        assertEquals(false, items.getValue(reply)["replyable"])
        assertEquals(true, items.getValue(other)["replyable"])
        assertPlaceholder(items.getValue(withdrawnReply), "WITHDRAWN")
    }

    @Test
    fun `이용 제한 사용자와 만료 전 임시 정지 사용자의 댓글도 보인다`() {
        val restrictedId = onboardedUser()
        val suspendedId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val restricted = seedComment(restrictedId, postId)
        val suspended = seedComment(suspendedId, postId)
        tx.execute {
            em.find(User::class.java, restrictedId).restrict()
            em.find(User::class.java, suspendedId).suspendUntil(LocalDateTime.now().plusDays(7))
        }

        assertEquals(listOf(restricted, suspended), visibleCommentIds(postId, onboardedUser()))
    }

    @Test
    fun `지운 게시물, 탈퇴한 작성자의 게시물, 나를 차단한 작성자의 게시물은 404 130400이다`() {
        val viewerId = onboardedUser()
        val deleted = createdPublicId(onboardedUser()).also { postId -> deletePost(postId, authorOf(postId)) }
        val withdrawnAuthor = onboardedUser()
        val withdrawn = createdPublicId(withdrawnAuthor)
        tx.execute { em.find(User::class.java, withdrawnAuthor).withdraw() }
        val blockingAuthor = onboardedUser()
        val blocking = createdPublicId(blockingAuthor)
        relate(blockingAuthor, viewerId, "block")

        listOf(deleted, withdrawn, blocking).forEach { postId ->
            expectStatus(getComments(postId, viewerId), 404, 130400)
        }
    }

    @Test
    fun `내가 차단한 작성자의 게시물은 403 130001이다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val postId = createdPublicId(authorId)
        relate(viewerId, authorId, "block")

        expectStatus(getComments(postId, viewerId), 403, 130001)
    }

    @Test
    fun `페이지와 항목의 필드가 계약과 같고 내부 id와 totalCount가 없다`() {
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        seedComment(onboardedUser(), postId, parentPublicId = root)
        seedComment(onboardedUser(), postId)

        val page = commentPage(postId, size = 1)

        assertEquals(setOf("items", "nextCursor", "hasNext"), page.keys)
        page.feedItems().forEach { item ->
            assertEquals(
                setOf(
                    "publicId",
                    "parentPublicId",
                    "status",
                    "author",
                    "content",
                    "likeCount",
                    "liked",
                    "edited",
                    "replyable",
                    "isMine",
                    "createdAt",
                ),
                item.keys,
            )
            assertEquals(setOf("publicId", "nickname", "profileImageUrl"), (item["author"] as Map<*, *>).keys)
        }
        assertNotNull(page["nextCursor"])
    }

    @Test
    fun `어느 쪽으로든 차단 관계인 사용자의 댓글과 그 아래 다른 사람의 대댓글이 빠진다`() {
        val viewerId = onboardedUser()
        val blockedId = onboardedUser()
        val blockerId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val byBlocked = seedComment(blockedId, postId)
        seedComment(onboardedUser(), postId, parentPublicId = byBlocked)
        val byBlocker = seedComment(blockerId, postId)
        seedComment(onboardedUser(), postId, parentPublicId = byBlocker)
        val kept = seedComment(onboardedUser(), postId)
        seedComment(blockedId, postId, parentPublicId = kept)
        relate(viewerId, blockedId, "block")
        relate(blockerId, viewerId, "block")

        assertEquals(listOf(kept), visibleCommentIds(postId, viewerId))
        assertEquals(6, visibleCommentIds(postId, onboardedUser()).size)
    }

    @Test
    fun `내가 뮤트한 사용자의 댓글과 그 아래 대댓글, 그 사용자의 대댓글이 빠진다`() {
        val viewerId = onboardedUser()
        val mutedId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val byMuted = seedComment(mutedId, postId)
        seedComment(onboardedUser(), postId, parentPublicId = byMuted)
        val kept = seedComment(onboardedUser(), postId)
        seedComment(mutedId, postId, parentPublicId = kept)
        relate(viewerId, mutedId, "mute")

        assertEquals(listOf(kept), visibleCommentIds(postId, viewerId))
    }

    @Test
    fun `뮤트한 작성자의 게시물을 직접 열면 그 작성자의 댓글만 빠진다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val postId = createdPublicId(authorId)
        seedComment(authorId, postId)
        val other = seedComment(onboardedUser(), postId)
        relate(viewerId, authorId, "mute")

        assertEquals(listOf(other), visibleCommentIds(postId, viewerId))
    }

    @Test
    fun `지운 최상위 댓글 아래 대댓글이 모두 나와 차단 관계인 사용자의 것이면 안내 문구도 없다`() {
        val viewerId = onboardedUser()
        val blockedId = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val root = seedComment(onboardedUser(), postId)
        seedComment(blockedId, postId, parentPublicId = root)
        deleteCommentRow(root)
        relate(viewerId, blockedId, "block")

        assertEquals(emptyList(), visibleCommentIds(postId, viewerId))
        assertEquals(2, visibleCommentIds(postId, onboardedUser()).size)
    }

    /** A는 게시물 작성자, B는 A가 뮤트한(섀도우 처리된) 사용자, C와 D는 다른 사용자다 */
    @Nested
    inner class ShadowMute {
        private var a = 0L
        private var b = 0L
        private var c = 0L
        private var d = 0L
        private lateinit var postId: String

        @BeforeEach
        fun setUp() {
            a = onboardedUser()
            b = onboardedUser()
            c = onboardedUser()
            d = onboardedUser()
            postId = createdPublicId(a)
        }

        @Test
        fun `뮤트당한 사용자의 댓글은 그 사용자에게만 보인다`() {
            relate(a, b, "mute")
            val byB = seedComment(b, postId)

            assertEquals(listOf(byB), visibleCommentIds(postId, b))
            assertEquals(emptyList(), visibleCommentIds(postId, a))
            assertEquals(emptyList(), visibleCommentIds(postId, c))
            assertCounts(b to 1, a to 0, c to 0)
        }

        @Test
        fun `B에게 보이는 댓글 수는 공개 수에 B의 숨긴 댓글을 더한 값이다`() {
            repeat(10) { seedComment(onboardedUser(), postId) }
            relate(a, b, "mute")
            seedComment(b, postId)

            assertCounts(b to 11, c to 10, null to 10)
        }

        @Test
        fun `B의 숨긴 댓글 아래 다른 사람의 대댓글은 B에게만 보인다`() {
            relate(a, b, "mute")
            val byB = seedComment(b, postId)
            val byC = seedComment(c, postId, parentPublicId = byB)

            assertEquals(listOf(byB, byC), visibleCommentIds(postId, b))
            assertEquals(emptyList(), visibleCommentIds(postId, c))
            assertCounts(b to 2, c to 0)
        }

        @Test
        fun `B의 숨긴 댓글 아래 B와 차단 관계인 사람의 대댓글은 B에게도 보이지 않는다`() {
            relate(a, b, "mute")
            val byB = seedComment(b, postId)
            seedComment(c, postId, parentPublicId = byB)
            relate(c, b, "block")

            assertEquals(listOf(byB), visibleCommentIds(postId, b))
            assertCounts(b to 2)
        }

        @Test
        fun `부모 댓글 작성자와 대댓글 작성자를 모두 뮤트하면 대댓글은 누구에게도 보이지 않는다`() {
            val byB = seedComment(b, postId)
            seedComment(c, postId, parentPublicId = byB)
            relate(a, b, "mute")
            relate(a, c, "mute")

            assertEquals(listOf(byB), visibleCommentIds(postId, b))
            assertEquals(emptyList(), visibleCommentIds(postId, c))
            assertEquals(emptyList(), visibleCommentIds(postId, a))
            assertCounts(b to 1, c to 0, a to 0)
        }

        @Test
        fun `숨긴 댓글 아래 B의 대댓글은 다른 사람에게 둘 다 보이지 않는다`() {
            relate(a, b, "mute")
            val byB = seedComment(b, postId)
            seedComment(b, postId, parentPublicId = byB)

            assertEquals(emptyList(), visibleCommentIds(postId, c))
            assertEquals(2, visibleCommentIds(postId, b).size)
            assertCounts(c to 0, b to 2)
        }

        @Test
        fun `B가 남의 댓글에 단 대댓글은 B에게만 보인다`() {
            relate(a, b, "mute")
            val byC = seedComment(c, postId)
            val byB = seedComment(b, postId, parentPublicId = byC)

            assertEquals(listOf(byC, byB), visibleCommentIds(postId, b))
            assertEquals(listOf(byC), visibleCommentIds(postId, c))
            assertEquals(listOf(byC), visibleCommentIds(postId, a))
            assertCounts(b to 2, c to 1, a to 1)
        }

        @Test
        fun `뮤트 전에 쓴 댓글과 그 아래 대댓글도 B를 뺀 모든 사람에게 숨긴다`() {
            val byB = seedComment(b, postId)
            val byC = seedComment(c, postId, parentPublicId = byB)
            relate(a, b, "mute")

            assertEquals(emptyList(), visibleCommentIds(postId, d))
            assertEquals(listOf(byB, byC), visibleCommentIds(postId, b))
            assertCounts(d to 0, b to 2)
        }

        @Test
        fun `뮤트 전에 대댓글을 쓴 사람에게도 B의 댓글과 자기 대댓글이 보이지 않는다`() {
            val byB = seedComment(b, postId)
            seedComment(c, postId, parentPublicId = byB)
            relate(a, b, "mute")

            assertEquals(emptyList(), visibleCommentIds(postId, c))
            assertCounts(c to 0)
        }

        @Test
        fun `뮤트를 풀면 숨긴 댓글이 다시 보인다`() {
            relate(a, b, "mute")
            val byB = seedComment(b, postId)
            unrelate(a, b, "mute")

            assertEquals(listOf(byB), visibleCommentIds(postId, c))
            assertCounts(c to 1, a to 1)
        }

        @Test
        fun `다른 사람 게시물에 쓴 B의 댓글은 뮤트한 A에게만 보이지 않는다`() {
            relate(a, b, "mute")
            val otherPost = createdPublicId(d)
            val byB = seedComment(b, otherPost)

            assertEquals(listOf(byB), visibleCommentIds(otherPost, d))
            assertEquals(listOf(byB), visibleCommentIds(otherPost, c))
            assertEquals(emptyList(), visibleCommentIds(otherPost, a))
            listOf(d, c, a).forEach { assertEquals(1, postDetail(otherPost, it)["commentCount"]) }
        }

        @Test
        fun `섀도우 처리된 작성자가 탈퇴해도 그 댓글과 아래 대댓글은 계속 숨긴다`() {
            relate(a, b, "mute")
            val byB = seedComment(b, postId)
            seedComment(c, postId, parentPublicId = byB)
            tx.execute { em.find(User::class.java, b).withdraw() }
            deleteCommentRow(byB, CommentDeletionReason.WITHDRAWAL)

            assertEquals(emptyList(), visibleCommentIds(postId, c))
            assertEquals(emptyList(), visibleCommentIds(postId, a))
            assertCounts(c to 0, a to 0)
        }

        @Test
        fun `뮤트당한 사용자가 로그아웃하고 보면 자기 댓글이 보이지 않는다`() {
            relate(a, b, "mute")
            seedComment(b, postId)
            val byC = seedComment(c, postId)

            assertEquals(listOf(byC), visibleCommentIds(postId))
            assertCounts(null to 1, b to 2)
        }

        /** 보는 사람(null은 비로그인)마다 상세 commentCount */
        private fun assertCounts(vararg expected: Pair<Long?, Int>) {
            expected.forEach { (viewer, count) -> assertEquals(count, postDetail(postId, viewer)["commentCount"], "viewer=$viewer") }
        }
    }

    private fun assertPlaceholder(
        item: Map<String, Any?>,
        status: String,
    ) {
        assertEquals(status, item["status"])
        assertNull(item["author"])
        assertNull(item["content"])
        assertEquals(0, item["likeCount"])
        listOf("liked", "edited", "replyable", "isMine").forEach { assertEquals(false, item[it], it) }
    }

    private fun authorOf(postPublicId: String): Long = postRow(postPublicId)["author_id"] as Long

    private fun expectStatus(
        result: ResultActions,
        httpStatus: Int,
        errorCode: Int,
    ) {
        result.andExpect(status().`is`(httpStatus)).andExpect(jsonPath("$.errorCode").value(errorCode))
    }
}
