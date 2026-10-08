package com.subhrodip.squarewise.notifications.email.smtp

import com.subhrodip.squarewise.errors.code.CategoryCode
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter

import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import com.subhrodip.squarewise.notifications.email.config.EmailProperties
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** Verifies the SMTP wire adapter without requiring a live Mailpit container. */
class SmtpJavaMailSenderTest {
    @Test
    fun `sends envelope and message data for all recipients`() {
        val received = runSmtpServer { reader, writer, capture ->
            writer.reply("220 test SMTP")
            capture.command(reader, writer, "250 test")
            capture.command(reader, writer, "250 test")
            capture.command(reader, writer, "250 test")
            capture.command(reader, writer, "250 test")
            capture.command(reader, writer, "354 continue")
            capture.data(reader, writer, "250 queued")
            capture.command(reader, writer, "221 bye")
        }

        assertThat(received).contains(
            "HELO localhost",
            "MAIL FROM:<sender@example.com>",
            "RCPT TO:<alice@example.com>",
            "RCPT TO:<bob@example.com>",
            "Subject: Test subject",
            "Content-Type: text/plain; charset=UTF-8",
            "hello from SMTP"
        )
    }

    @Test
    fun `turns an unexpected SMTP response into MailSendException`() {
        val error = assertThrows(MailSendException::class.java) {
            runSmtpServer { reader, writer, capture ->
                writer.reply("220 test SMTP")
                capture.command(reader, writer, "550 rejected")
            }
        }

        assertThat(error.message).contains("Unexpected SMTP response")
    }

    /** Verifies configured sender and empty optional fields are serialized safely. */
    @Test
    fun `uses configured sender and empty optional fields`() {
        val received = runSmtpServer(
            SimpleMailMessage(to = arrayOf("alice@example.com"))
        ) { reader, writer, capture ->
            writer.reply("220 test SMTP")
            capture.command(reader, writer, "250 test")
            capture.command(reader, writer, "250 test")
            capture.command(reader, writer, "250 test")
            capture.command(reader, writer, "354 continue")
            capture.data(reader, writer, "250 queued")
            capture.command(reader, writer, "221 bye")
        }

        assertThat(received).contains(
            "MAIL FROM:<sender@example.com>",
            "Subject: ",
            "Content-Type: text/plain; charset=UTF-8"
        )
    }

    /** Verifies a closed SMTP response stream is reported as a delivery failure. */
    @Test
    fun `rejects a premature SMTP response stream`() {
        val error = assertThrows(MailSendException::class.java) {
            runSmtpServer { _, writer, _ -> writer.close() }
        }

        assertThat(error.message).contains("Premature end of stream")
    }

    @Test
    fun `rejects messages without recipients before opening a socket`() {
        val properties = EmailProperties("127.0.0.1", 1, "sender@example.com", true, 1, 0)

        val error = assertThrows(SquarewiseException::class.java) {
            SmtpJavaMailSender(properties).send(SimpleMailMessage(subject = "subject"))
        }

        assertEquals(CategoryCode.VALIDATION_ERROR, error.definition.category)
    }

    /** Verifies non-SMTP socket failures are normalized as delivery failures. */
    @Test
    fun `wraps invalid socket configuration as MailSendException`() {
        val error = assertThrows(MailSendException::class.java) {
            SmtpJavaMailSender(
                EmailProperties("127.0.0.1", -1, "sender@example.com", true, 1, 0)
            ).send(SimpleMailMessage(to = arrayOf("alice@example.com")))
        }

        assertThat(error.message).contains("Failed to send email")
    }

    private fun runSmtpServer(
        script: (BufferedReaderWithReply, BufferedWriterWithCapture, Capture) -> Unit
    ): String = runSmtpServer(
        SimpleMailMessage(
            from = "sender@example.com",
            to = arrayOf("alice@example.com", "bob@example.com"),
            subject = "Test subject",
            text = "hello from SMTP"
        ),
        script
    )

    private fun runSmtpServer(
        message: SimpleMailMessage,
        script: (BufferedReaderWithReply, BufferedWriterWithCapture, Capture) -> Unit
    ): String {
        ServerSocket(0).use { server ->
            val received = StringBuilder()
            val failure = AtomicReference<Throwable?>(null)
            val worker = thread(start = true, name = "smtp-test-server") {
                try {
                    server.accept().use { socket ->
                        val reader = BufferedReaderWithReply(socket.getInputStream())
                        val writer = BufferedWriterWithCapture(socket.getOutputStream())
                        script(reader, writer, Capture(received))
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }

            SmtpJavaMailSender(
                EmailProperties("127.0.0.1", server.localPort, "sender@example.com", true, 1, 0)
            ).send(
                message
            )
            worker.join(5000)
            failure.get()?.let { throw it }
            return received.toString()
        }
    }

    private class Capture(private val received: StringBuilder) {
        fun command(reader: BufferedReaderWithReply, writer: BufferedWriterWithCapture, response: String) {
            received.append(reader.readLine()).append('\n')
            writer.reply(response)
        }

        fun data(reader: BufferedReaderWithReply, writer: BufferedWriterWithCapture, response: String) {
            while (true) {
                val line = reader.readLine()
                if (line == "." || line == null) break
                received.append(line).append('\n')
            }
            writer.reply(response)
        }
    }

    private class BufferedReaderWithReply(input: InputStream) : BufferedReader(
        InputStreamReader(input, Charsets.UTF_8)
    )

    private class BufferedWriterWithCapture(output: OutputStream) : PrintWriter(
        OutputStreamWriter(output, Charsets.UTF_8), true
    ) {
        fun reply(value: String) {
            print(value)
            print("\r\n")
            flush()
        }
    }
}
