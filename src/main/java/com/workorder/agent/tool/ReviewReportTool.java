package com.workorder.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 统计复盘工具，对周期内工单数据进行智能分析，生成 Markdown 格式复盘报告。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewReportTool {

    private final WorkOrderMapper workOrderMapper;
    private final LLMClient llmClient;

    private static final String REPORT_SYSTEM_PROMPT = """
            你是一个企业智能工单系统的数据分析师。请根据提供的工单统计数据，生成一份专业的复盘报告（Markdown格式）。

            报告结构要求：
            ## 一、周期工单数据总览
            - 工单总数、办结数、AI自动办结数、超时数、重复数
            - 办结率、AI自动办结率、超时率

            ## 二、工单类型分布
            - 各类型（咨询/故障/申诉/建议/运维报错/功能异常）工单数量及占比
            - 趋势分析

            ## 三、高频问题总结
            - TOP 5 高频问题关键词
            - 高频业务模块

            ## 四、优先级分布
            - 紧急/高/中/低 各级工单数量及处理情况

            ## 五、核心问题与薄弱环节
            - 分析当前最突出的问题
            - 系统薄弱环节

            ## 六、改进建议
            - 针对高频问题的预防措施
            - 流程优化建议
            - 知识库补充建议

            请确保报告专业、有数据支撑、建议可落地。
            """;

    /**
     * 生成周期复盘报告。
     */
    public String generateReport(String reportType, String reportPeriod,
                                 LocalDateTime start, LocalDateTime end) {
        List<WorkOrder> orders = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .ge(WorkOrder::getCreateTime, start)
                        .le(WorkOrder::getCreateTime, end)
        );

        String stats = buildStatistics(orders, reportType, reportPeriod);

        log.info("开始生成 {} 复盘报告，周期: {}", reportType, reportPeriod);
        String report = llmClient.chat(REPORT_SYSTEM_PROMPT, stats);
        if (report == null) {
            log.warn("LLM 不可用，返回统计摘要作为报告");
            return "## " + reportType + "复盘报告（" + reportPeriod + "）\n\n" + stats;
        }
        return report;
    }

    /**
     * 对单个工单进行复盘分析。
     */
    public String reviewSingleOrder(WorkOrder order, String extraPrompt) {
        String systemPrompt = """
                你是一个企业工单处理质量评审专家。请对以下工单的处理过程进行分析评估。

                分析维度：
                1. 工单分类是否准确
                2. 优先级评定是否合理
                3. 处理流程是否规范
                4. 答复质量如何（如有AI答复）
                5. 是否存在优化空间
                6. 改进建议

                输出简洁有条理的分析报告。
                """;

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("工单编号：").append(order.getOrderNo()).append("\n");
        userPrompt.append("标题：").append(order.getTitle()).append("\n");
        userPrompt.append("内容：").append(order.getContent()).append("\n");
        userPrompt.append("AI分类：").append(order.getWorkType()).append("\n");
        userPrompt.append("优先级：").append(order.getPriority()).append("\n");
        userPrompt.append("状态：").append(order.getStatus()).append("\n");
        userPrompt.append("AI答复：").append(order.getAiAnswer() != null ? order.getAiAnswer() : "无").append("\n");
        if (extraPrompt != null) {
            userPrompt.append("额外说明：").append(extraPrompt).append("\n");
        }

        String result = llmClient.chat(systemPrompt, userPrompt.toString());
        if (result == null) {
            return "LLM 不可用，无法生成复盘分析。工单编号：" + order.getOrderNo();
        }
        return result;
    }

    private String buildStatistics(List<WorkOrder> orders, String reportType, String reportPeriod) {
        int total = orders.size();
        int finishCount = (int) orders.stream().filter(o -> o.getStatus() == 2).count();
        int aiAutoCount = (int) orders.stream().filter(o -> o.getIsAutoFinish() == 1).count();
        int duplicateCount = (int) orders.stream().filter(o -> o.getIsDuplicate() == 1).count();

        double finishRate = total > 0 ? (double) finishCount / total * 100 : 0;
        double aiRate = total > 0 ? (double) aiAutoCount / total * 100 : 0;

        Map<String, Long> typeDist = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getWorkType() != null ? o.getWorkType() : "未分类",
                        Collectors.counting()
                ));

        Map<Integer, Long> priorityDist = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getPriority() != null ? o.getPriority() : 3,
                        Collectors.counting()
                ));

        Map<String, Long> moduleDist = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getModule() != null ? o.getModule() : "未分类",
                        Collectors.counting()
                ));

        StringBuilder stats = new StringBuilder();
        stats.append("报告类型：").append(reportType).append("\n");
        stats.append("报告周期：").append(reportPeriod).append("\n");
        stats.append("工单总数：").append(total).append("\n");
        stats.append("办结数：").append(finishCount)
                .append("（办结率 ").append(String.format("%.1f", finishRate)).append("%）\n");
        stats.append("AI自动办结数：").append(aiAutoCount)
                .append("（AI办结率 ").append(String.format("%.1f", aiRate)).append("%）\n");
        stats.append("超时数：0\n");
        stats.append("重复工单数：").append(duplicateCount).append("\n\n");

        stats.append("## 工单类型分布\n");
        typeDist.forEach((k, v) -> stats.append("- ").append(k).append(": ").append(v).append(" 单\n"));

        stats.append("\n## 优先级分布\n");
        priorityDist.forEach((k, v) -> {
            String label = switch (k) {
                case 1 -> "紧急";
                case 2 -> "高";
                case 3 -> "中";
                case 4 -> "低";
                default -> "未知";
            };
            stats.append("- ").append(label).append(": ").append(v).append(" 单\n");
        });

        stats.append("\n## 高频业务模块 TOP 5\n");
        moduleDist.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> stats.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append(" 单\n"));

        return stats.toString();
    }
}
