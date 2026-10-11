package com.subhrodip.squarewise.notifications.email.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Repository for durable notification email delivery state. */
interface NotificationEmailDeliveryRepository : JpaRepository<NotificationEmailDeliveryEntity, UUID>
