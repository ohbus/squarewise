package com.subhrodip.squarewise.notifications.email.smtp

import com.subhrodip.squarewise.notifications.email.config.EmailProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import com.subhrodip.squarewise.errors.catalog.NotificationErrors
import com.subhrodip.squarewise.notifications.errors.NotificationDomainException
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import java.nio.charset.StandardCharsets

/**
 * Default SMTP adapter implementing JavaMailSender.
 * Connects directly to SMTP host:port (e.g. local Mailpit on localhost:1025).
 */
@Component
class SmtpJavaMailSender(private val properties: EmailProperties) : JavaMailSender {

    private val log = LoggerFactory.getLogger(SmtpJavaMailSender::class.java)

    override fun send(message: SimpleMailMessage) {
        val recipients = message.to ?: emptyArray()
        if (recipients.isEmpty()) {
            throw NotificationDomainException(NotificationErrors.EMAIL_RECIPIENT_REQUIRED, "At least one recipient must be specified")
        }

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(properties.host, properties.port), 5000)
                socket.soTimeout = 5000

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)

                fun readResponse(expectedPrefix: String) {
                    val line = reader.readLine() ?: throw MailSendException("Premature end of stream from SMTP server")
                    if (!line.startsWith(expectedPrefix)) {
                        throw MailSendException("Unexpected SMTP response: $line (expected prefix $expectedPrefix)")
                    }
                }

                fun sendCommand(cmd: String, expectedPrefix: String) {
                    writer.print(cmd + "\r\n")
                    writer.flush()
                    readResponse(expectedPrefix)
                }

                // Initial 220 banner
                readResponse("220")

                // HELO
                sendCommand("HELO localhost", "250")

                // MAIL FROM
                val from = message.from ?: properties.fromAddress
                sendCommand("MAIL FROM:<$from>", "250")

                // RCPT TO
                for (rcpt in recipients) {
                    sendCommand("RCPT TO:<$rcpt>", "250")
                }

                // DATA
                sendCommand("DATA", "354")

                // Message payload
                writer.print("From: $from\r\n")
                writer.print("To: ${recipients.joinToString(", ")}\r\n")
                writer.print("Subject: ${message.subject ?: ""}\r\n")
                writer.print("${ApiEndpoints.Headers.CONTENT_TYPE}: ${ApiEndpoints.Headers.TEXT_PLAIN_UTF8}\r\n")
                writer.print("\r\n")
                writer.print((message.text ?: "") + "\r\n")
                writer.print(".\r\n")
                writer.flush()
                readResponse("250")

                // QUIT
                sendCommand("QUIT", "221")
            }
        } catch (e: MailException) {
            throw e
        } catch (e: Exception) {
            throw MailSendException("Failed to send email to ${recipients.contentToString()}: ${e.message}", e)
        }
    }
}
