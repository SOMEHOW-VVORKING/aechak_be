package com.aechak.infra.client.sms

import com.aechak.application.user.verification.port.SmsSender
import com.solapi.sdk.message.model.Message
import com.solapi.sdk.message.service.DefaultMessageService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CoolSMS 실발송 어댑터. 발송 실패는 SDK 예외를 그대로 던진다 — 정책 번역(에러코드·보상)은 application 담당.
 * SDK가 HTTP 타임아웃을 50초로 고정해 두어, 전용 executor에 발송을 맡기고 sendTimeout까지만 기다린다(동기 유지).
 * 타임아웃은 대기 포기일 뿐 발송 취소가 아니다 — SDK가 취소 수단을 노출하지 않아, 대기 상한(sendTimeout)을
 * 넘겨 완료된 발송은 보상(코드 소각·일 상한 롤백)이 끝난 뒤 실제로 나갈 수 있다. 이 늦은 완료는 warn 로그로만 관측된다.
 */
class CoolSmsSender(
    private val messageService: DefaultMessageService,
    private val from: String,
    private val executor: ExecutorService,
    private val sendTimeout: Duration,
) : SmsSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        phoneNumber: String,
        message: String,
    ) {
        val abandoned = AtomicBoolean(false)
        val future =
            try {
                executor.submit {
                    messageService.send(Message(from = from, to = phoneNumber, text = message))
                    if (abandoned.get()) {
                        log.warn("타임아웃으로 보상 처리된 발송이 뒤늦게 완료됨 — 소각된 코드가 실발송되고 일 상한에 미집계")
                    }
                }
            } catch (e: RejectedExecutionException) {
                log.warn("SMS 발송 스레드 풀이 가득 차 발송이 즉시 거절됨 — CoolSMS 장애가 아니라 동시 발송 요청이 풀 크기를 초과함")
                throw e
            }
        try {
            future.get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            abandoned.set(true)
            future.cancel(true)
            throw e
        } catch (e: InterruptedException) {
            future.cancel(true)
            // 인터럽트 플래그를 복원하지 않는다 — 세운 채 던지면 보상 경로의 Redis 명령이 즉시 인터럽트 실패한다
            throw e
        } catch (e: ExecutionException) {
            // Error는 언랩하지 않는다 — 포장(Exception)째 던져야 호출부 catch(Exception)의 보상 경로를 탄다
            throw e.cause as? Exception ?: e
        }
    }
}
