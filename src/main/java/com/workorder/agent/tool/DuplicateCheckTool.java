package com.workorder.agent.tool;

import cn.hutool.core.collection.*;
import cn.hutool.core.util.*;
import com.baomidou.mybatisplus.core.conditions.query.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.mapper.*;
import lombok.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.util.*;

/**
 * 重复工单识别工具
 * 基于 TF-IDF + 余弦相似度，检测重复或高度相似的工单
 */
@Slf4j
@Component
public class DuplicateCheckTool {

    @Autowired
    private WorkOrderMapper workOrderMapper;

    /** 相似度阈值，超过此值视为重复 */
    private static final double DUPLICATE_THRESHOLD = 0.75;

    /**
     * 检测是否有重复工单
     *
     * @param title   新工单标题
     * @param content 新工单内容
     * @return 最相似的重复工单（null 表示无重复）
     */
    public DuplicateResult check(String title, String content) {
        // 查询近期未办结工单
        List<WorkOrder> recent = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .ne(WorkOrder::getStatus, 2) // 排除已办结
                        .ne(WorkOrder::getStatus, 3) // 排除已关闭
                        .orderByDesc(WorkOrder::getCreateTime)
                        .last("LIMIT 100")
        );

        if (CollUtil.isEmpty(recent)) {
            return new DuplicateResult(false, null, 0);
        }

        String newText = (title + " " + content).toLowerCase();
        List<String> newTokens = tokenize(newText);
        Map<String, Double> newTF = computeTF(newTokens);

        // 计算所有文档的 IDF
        List<List<String>> allTokenLists = new ArrayList<>();
        allTokenLists.add(newTokens);
        for (WorkOrder wo : recent) {
            String woText = (wo.getTitle() + " " + wo.getContent()).toLowerCase();
            allTokenLists.add(tokenize(woText));
        }
        Map<String, Double> idf = computeIDF(allTokenLists);

        double[] newVec = buildVector(newTF, idf);

        double maxSimilarity = 0;
        WorkOrder mostSimilar = null;

        for (WorkOrder wo : recent) {
            String woText = (wo.getTitle() + " " + wo.getContent()).toLowerCase();
            List<String> woTokens = tokenize(woText);
            Map<String, Double> woTF = computeTF(woTokens);
            double[] woVec = buildVector(woTF, idf);
            double sim = cosineSimilarity(newVec, woVec);

            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                mostSimilar = wo;
            }
        }

        boolean isDuplicate = maxSimilarity >= DUPLICATE_THRESHOLD;
        if (isDuplicate) {
            log.info("检测到重复工单: orderId={}, similarity={}", mostSimilar.getId(), maxSimilarity);
        }

        return new DuplicateResult(isDuplicate, mostSimilar, maxSimilarity);
    }

    // ==================== 文本处理（与 RAG 相同逻辑） ====================

    private List<String> tokenize(String text) {
        if (StrUtil.isBlank(text)) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        text = text.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", " ");
        for (String word : text.split("\\s+")) {
            if (word.length() >= 2) tokens.add(word);
        }
        StringBuilder chineseChars = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fa5) chineseChars.append(c);
        }
        String cn = chineseChars.toString();
        for (int i = 0; i < cn.length(); i++) {
            tokens.add(String.valueOf(cn.charAt(i)));
            if (i < cn.length() - 1) tokens.add(cn.substring(i, i + 2));
        }
        return tokens;
    }

    private Map<String, Double> computeTF(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (CollUtil.isEmpty(tokens)) return tf;
        for (String t : tokens) tf.merge(t, 1.0, Double::sum);
        double total = tokens.size();
        tf.replaceAll((k, v) -> v / total);
        return tf;
    }

    private Map<String, Double> computeIDF(List<List<String>> tokenLists) {
        Map<String, Double> idf = new HashMap<>();
        int n = tokenLists.size();
        if (n == 0) return idf;
        Map<String, Integer> df = new HashMap<>();
        for (List<String> tokens : tokenLists) {
            Set<String> unique = new HashSet<>(tokens);
            for (String t : unique) df.merge(t, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : df.entrySet()) {
            idf.put(e.getKey(), Math.log(1.0 + n / (1.0 + e.getValue())));
        }
        return idf;
    }

    private double[] buildVector(Map<String, Double> tf, Map<String, Double> idf) {
        Set<String> allTerms = new HashSet<>();
        allTerms.addAll(tf.keySet());
        allTerms.addAll(idf.keySet());
        List<String> termList = new ArrayList<>(allTerms);
        double[] vec = new double[termList.size()];
        for (int i = 0; i < termList.size(); i++) {
            String term = termList.get(i);
            vec[i] = tf.getOrDefault(term, 0.0) * idf.getOrDefault(term, 0.0);
        }
        return vec;
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Data
    @AllArgsConstructor
    public static class DuplicateResult {
        private boolean duplicate;
        private WorkOrder similarOrder;
        private double similarity;
    }
}
