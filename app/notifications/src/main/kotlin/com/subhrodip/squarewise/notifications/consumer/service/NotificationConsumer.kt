package com.subhrodip.squarewise.notifications.consumer.service

import com.subhrodip.squarewise.notifications.consumer.model.NotificationConsumptionOutcome
import com.subhrodip.squarewise.notifications.consumer.model.NotificationEvent

/** Port for applying broker notification events. */
fun interface NotificationConsumer { fun consume(event: NotificationEvent): NotificationConsumptionOutcome }
