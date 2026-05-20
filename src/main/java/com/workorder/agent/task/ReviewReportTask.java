package com.workorder.agent.task;

import com.workorder.agent.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

/**
 * 周期复盘报告定时任务，每日/每周自动生成 AI 复盘报告。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewReportTask {

    private final ReportService reportService;

    /**
     * 每日复盘：每天 18:00。
     */
    @Scheduled(cron = "${agent.task.daily-report-cron:0 0 18 * * ?}")
    public void generateDailyReport() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        log.info("开始生成每日复盘报告: {}", today);
        try {
            reportService.generateReport("day", today);
            log.info("每日复盘报告生成完成: {}", today);
        } catch (Exception e) {
            log.error("每日复盘报告生成失败", e);
        }
    }

    /**
     * 每周复盘：每周一 09:00。
     */
    @Scheduled(cron = "${agent.task.weekly-report-cron:0 0 9 * * MON}")
    public void generateWeeklyReport() {
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        int weekOfYear = today.get(weekFields.weekOfYear());
        String weekPeriod = today.getYear() + "-W" + String.format("%02d", weekOfYear);

        log.info("开始生成每周复盘报告: {}", weekPeriod);
        try {
            reportService.generateReport("week", weekPeriod);
            log.info("每周复盘报告生成完成: {}", weekPeriod);
        } catch (Exception e) {
            log.error("每周复盘报告生成失败", e);
        }
    }
}
