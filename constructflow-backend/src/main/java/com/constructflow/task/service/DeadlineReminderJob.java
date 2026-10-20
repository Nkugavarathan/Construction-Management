package com.constructflow.task.service;

import com.constructflow.notification.entity.NotificationType;
import com.constructflow.notification.service.NotificationService;
import com.constructflow.task.entity.Task;
import com.constructflow.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Daily reminders for tasks due within two days and overdue tasks. Dedupe keys include the due date,
 * so re-running the job (or a restart) never repeats a reminder, but moving the due date produces a
 * fresh one.
 *
 * <p>Limitations: "today" is computed in UTC for all organizations, and with several backend instances
 * each would run the job (a distributed lock such as ShedLock would be needed).
 */
@Component
public class DeadlineReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderJob.class);

    private final TaskRepository tasks;
    private final NotificationService notifications;
    private final Clock clock;

    public DeadlineReminderJob(TaskRepository tasks, NotificationService notifications, Clock clock) {
        this.tasks = tasks;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.scheduling.reminder-cron:0 0 1 * * *}", zone = "UTC")
    @Transactional
    public void sendReminders() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        int sent = 0;
        for (Task task : tasks.findOpenDueOnOrBefore(today.plusDays(2))) {
            boolean overdue = task.getDueDate().isBefore(today);
            String link = "/projects/%s/tasks?task=%s".formatted(task.getProject().getId(), task.getId());
            notifications.notify(task.getProject().getOrganizationId(), task.getAssignee().getId(),
                    overdue ? NotificationType.TASK_OVERDUE : NotificationType.TASK_DUE_SOON,
                    (overdue ? "Overdue: " : "Due soon: ") + task.getTitle(),
                    "%s · due %s".formatted(task.getProject().getCode(), task.getDueDate()), link,
                    (overdue ? "task-overdue:" : "task-due:") + task.getId() + ":" + task.getDueDate());
            sent++;
        }
        log.info("Deadline reminder job processed {} tasks", sent);
    }
}
