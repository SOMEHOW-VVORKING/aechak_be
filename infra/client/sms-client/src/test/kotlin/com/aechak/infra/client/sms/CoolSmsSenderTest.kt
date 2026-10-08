package com.aechak.infra.client.sms

import com.solapi.sdk.message.service.DefaultMessageService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.io.IOException
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * 계약 — 발송 대기는 sendTimeout까지, 동시 발송은 풀 크기까지. 초과는 예외로 즉시 드러나
 * application의 보상(코드 소각·상한 롤백) 경로를 탄다. 깨지면 벤더 장애가 요청 스레드를 50초씩 잠근다.
 * SDK 클래스가 final이라 목 대신 실제 로컬 소켓으로 지연·실패를 재현한다.
 */
class CoolSmsSenderTest {
    private val executors = mutableListOf<ExecutorService>()

    @AfterTest
    fun tearDown() {
        executors.forEach { it.shutdownNow() }
    }

    private fun newExecutor(threads: Int): ExecutorService =
        ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS, SynchronousQueue()) { task ->
            Thread(task).apply { isDaemon = true }
        }.also { executors += it }

    private fun sender(
        domain: String,
        executor: ExecutorService,
        timeout: Duration,
    ): CoolSmsSender = CoolSmsSender(DefaultMessageService("test-key", "test-secret", domain), "0212345678", executor, timeout)

    @Test
    fun `발송이 대기 상한을 넘기면 TimeoutException을 던진다`() {
        // 연결만 받고(백로그) 응답하지 않는 서버 — SDK의 50초 타임아웃보다 어댑터 상한이 먼저 끊어야 한다
        ServerSocket(0).use { server ->
            val sender = sender("http://127.0.0.1:${server.localPort}", newExecutor(1), Duration.ofMillis(200))
            val startedAt = System.nanoTime()
            assertThatThrownBy { sender.send("01012345678", "인증번호") }
                .isInstanceOf(TimeoutException::class.java)
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5))
        }
    }

    @Test
    fun `발송 실패는 SDK 원인 예외를 그대로 던진다`() {
        // 포트 1: 특권 포트라 리스너가 있을 수 없어 즉시 거부 — ephemeral 포트 재사용 레이스가 없다
        val sender = sender("http://127.0.0.1:1", newExecutor(1), Duration.ofSeconds(10))
        // ExecutionException 포장 없이 원인(IOException)이 그대로 — 발송 실패 번역 시 cause로 보존된다
        assertThatThrownBy { sender.send("01012345678", "인증번호") }
            .isInstanceOf(IOException::class.java)
    }

    @Test
    fun `대기 중 인터럽트되면 InterruptedException을 던진다`() {
        ServerSocket(0).use { server ->
            val sender = sender("http://127.0.0.1:${server.localPort}", newExecutor(1), Duration.ofSeconds(10))
            val thrown = AtomicReference<Throwable>()
            val caller =
                Thread {
                    try {
                        sender.send("01012345678", "인증번호")
                    } catch (e: Throwable) {
                        thrown.set(e)
                    }
                }
            caller.start()
            Thread.sleep(300) // future.get 대기에 들어갈 시간
            caller.interrupt()
            caller.join(3000)
            assertThat(thrown.get()).isInstanceOf(InterruptedException::class.java)
        }
    }

    @Test
    fun `풀이 포화되면 대기 없이 즉시 거절한다`() {
        val executor = newExecutor(1)
        val occupied = CountDownLatch(1)
        executor.submit { occupied.await() }
        try {
            val sender = sender("http://127.0.0.1:1", executor, Duration.ofSeconds(10))
            assertThatThrownBy { sender.send("01012345678", "인증번호") }
                .isInstanceOf(RejectedExecutionException::class.java)
        } finally {
            occupied.countDown()
        }
    }
}
