package com.workorder.agent.tool;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.agent.entity.WorkOrder;
import com.workorder.agent.mapper.WorkOrderMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 重复工单识别工具，基于 TF-IDF + 余弦相似度检测重复或高度相似的工单。
 */
@Slf4j
@Component
public class DuplicateCheckTool {

    private final WorkOrderMapper workOrderMapper;
    private final double duplicateThreshold;

    public DuplicateCheckTool(WorkOrderMapper workOrderMapper,
                              @Value("${agent.duplicate.threshold:0.75}") double duplicateThreshold) {
        this.workOrderMapper = workOrderMapper;
        this.duplicateThreshold = duplicateThreshold;
    }

    /**
     * 检测是否有重复工单。
     *
     * @param title   新工单标题
     * @param content 新工单内容
     * @return 最相似的重复工单（null 表示无重复）
     */
    public DuplicateResult check(String title, String content) {
        List<WorkOrder> recent = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .ne(WorkOrder::getStatus, 2)
                        .ne(WorkOrder::getStatus, 3)
                        .orderByDesc(WorkOrder::getCreateTime)
                        .last("LIMIT 100")
        );

        if (CollUtil.isEmpty(recent)) {
            return new DuplicateResult(false, null, 0);
        }

        String newText = (title + " " + content).toLowerCase();
        List<String> newTokens = TextSimilarityUtils.tokenize(newText);
        Map<String, Double> newTF = TextSimilarityUtils.computeTF(newTokens);

        // 计算所有文档的 IDF
        List<List<String>> allTokenLists = new ArrayList<>();
        allTokenLists.add(newTokens);
        for (WorkOrder wo : recent) {
            String woText = (wo.getTitle() + " " + wo.getContent()).toLowerCase();
            allTokenLists.add(TextSimilarityUtils.tokenize(woText));
        }
        Map<String, Double> idf = TextSimilarityUtils.computeIDF(allTokenLists);

        double[] newVec = TextSimilarityUtils.buildVector(newTF, idf);

        double maxSimilarity = 0;
        WorkOrder mostSimilar = null;

        for (WorkOrder wo : recent) {
            String woText = (wo.getTitle() + " " + wo.getContent()).toLowerCase();
            List<String> woTokens = TextSimilarityUtils.tokenize(woText);
            Map<String, Double> woTF = TextSimilarityUtils.computeTF(woTokens);
            double[] woVec = TextSimilarityUtils.buildVector(woTF, idf);
            double sim = TextSimilarityUtils.cosineSimilarity(newVec, woVec);

            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                mostSimilar = wo;
            }
        }

        boolean isDuplicate = maxSimilarity >= duplicateThreshold;
        if (isDuplicate) {
            log.info("检测到重复工单: orderId={}, similarity={}", mostSimilar.getId(), maxSimilarity);
        }

        return new DuplicateResult(isDuplicate, mostSimilar, maxSimilarity);
    }

    @Data
    @AllArgsConstructor
    public static class DuplicateResult {
        private boolean duplicate;
        private WorkOrder similarOrder;
        private double similarity;
    }
}
