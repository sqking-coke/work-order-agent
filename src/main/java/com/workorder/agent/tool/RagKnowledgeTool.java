package com.workorder.agent.tool;

import cn.hutool.core.collection.*;
import cn.hutool.core.util.*;
import com.workorder.agent.entity.*;
import com.workorder.agent.mapper.*;
import jakarta.annotation.*;
import lombok.extern.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.util.*;
import java.util.stream.*;

/**
 * 轻量化 RAG 知识库工具
 * 基于 TF-IDF + 余弦相似度，无需外部向量数据库
 */
@Slf4j
@Component
public class RagKnowledgeTool {

    @Autowired
    private WorkKnowledgeMapper knowledgeMapper;

    @Autowired
    private LLMClient llmClient;

    @Value("${agent.rag.top-k:5}")
    private int topK;

    @Value("${agent.rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    /** 全量知识库缓存 */
    private List<KnowledgeDoc> docs = new ArrayList<>();

    /** 全局 IDF */
    private Map<String, Double> idf = new HashMap<>();

    @PostConstruct
    public void init() {
        refreshIndex();
    }

    /**
     * 刷新知识库索引
     */
    public synchronized void refreshIndex() {
        List<WorkKnowledge> list = knowledgeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkKnowledge>()
                        .eq(WorkKnowledge::getStatus, 1)
        );
        docs = list.stream().map(k -> {
            KnowledgeDoc doc = new KnowledgeDoc();
            doc.setId(k.getId());
            doc.setTitle(k.getTitle());
            doc.setContent(k.getContent());
            doc.setModule(k.getModule());
            String fullText = k.getTitle() + " " + k.getContent();
            if (StrUtil.isNotBlank(k.getKeywords())) {
                fullText += " " + k.getKeywords();
            }
            doc.setTokens(tokenize(fullText));
            doc.setTf(computeTF(doc.getTokens()));
            return doc;
        }).collect(Collectors.toList());

        // 计算全局 IDF
        idf = computeIDF(docs);
        log.info("RAG 知识库索引刷新完成，共 {} 篇文档", docs.size());
    }

    /**
     * 根据问题检索最匹配的知识点
     */
    public List<KnowledgeDoc> search(String query) {
        if (CollUtil.isEmpty(docs)) {
            return Collections.emptyList();
        }

        List<String> queryTokens = tokenize(query);
        Map<String, Double> queryTF = computeTF(queryTokens);
        double[] queryVec = buildVector(queryTF, idf);

        // 计算余弦相似度
        List<DocSimilarity> similarities = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            double[] docVec = buildVector(doc.getTf(), idf);
            double sim = cosineSimilarity(queryVec, docVec);
            if (sim >= similarityThreshold) {
                DocSimilarity ds = new DocSimilarity();
                ds.setDoc(doc);
                ds.setSimilarity(sim);
                similarities.add(ds);
            }
        }

        // 按相似度降序
        similarities.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        return similarities.stream()
                .limit(topK)
                .map(DocSimilarity::getDoc)
                .collect(Collectors.toList());
    }

    /**
     * RAG 智能答疑：检索 + LLM 生成答复
     */
    public String answer(String question) {
        List<KnowledgeDoc> matched = search(question);

        if (CollUtil.isEmpty(matched)) {
            log.info("RAG 未匹配到相关知识点，问题: {}", question);
            return null; // 兜底，走人工
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < matched.size(); i++) {
            KnowledgeDoc doc = matched.get(i);
            context.append("【知识点").append(i + 1).append("】").append(doc.getTitle()).append("\n");
            context.append(doc.getContent()).append("\n\n");
        }

        String systemPrompt = """
                你是一个专业的企业客服与运维助手。请根据以下知识库内容，准确、简洁地回答用户问题。
                要求：
                1. 严格基于提供的知识库内容回答，不编造信息
                2. 回答清晰有条理，可分点列出操作步骤
                3. 如果知识库无法完全覆盖问题，请如实说明并给出进一步建议
                4. 回答结尾加上「以上答复由AI自动生成，仅供参考」
                """;

        String userPrompt = "知识库内容：\n" + context + "\n用户问题：" + question;
        return llmClient.chat(systemPrompt, userPrompt);
    }

    // ==================== 文本处理 ====================

    private List<String> tokenize(String text) {
        if (StrUtil.isBlank(text)) {
            return Collections.emptyList();
        }
        // 中文按字符 unigram + bigram，英文按空格分词
        List<String> tokens = new ArrayList<>();
        text = text.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", " ");

        // 英文分词
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.length() >= 2) {
                tokens.add(word);
            }
        }

        // 中文 bigram 分词
        StringBuilder chineseChars = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseChars.append(c);
            }
        }
        String cn = chineseChars.toString();
        for (int i = 0; i < cn.length(); i++) {
            tokens.add(String.valueOf(cn.charAt(i))); // unigram
            if (i < cn.length() - 1) {
                tokens.add(cn.substring(i, i + 2)); // bigram
            }
        }

        return tokens;
    }

    private Map<String, Double> computeTF(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (CollUtil.isEmpty(tokens)) return tf;
        for (String t : tokens) {
            tf.merge(t, 1.0, Double::sum);
        }
        // 归一化
        double total = tokens.size();
        tf.replaceAll((k, v) -> v / total);
        return tf;
    }

    private Map<String, Double> computeIDF(List<KnowledgeDoc> docs) {
        Map<String, Double> idf = new HashMap<>();
        int n = docs.size();
        if (n == 0) return idf;

        Map<String, Integer> df = new HashMap<>();
        for (KnowledgeDoc doc : docs) {
            Set<String> uniqueTokens = doc.getTokens().stream().collect(Collectors.toSet());
            for (String t : uniqueTokens) {
                df.merge(t, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : df.entrySet()) {
            idf.put(e.getKey(), Math.log(1.0 + n / (1.0 + e.getValue())));
        }
        return idf;
    }

    private double[] buildVector(Map<String, Double> tf, Map<String, Double> idf) {
        // 使用所有出现过的 term 构建向量
        Set<String> allTerms = new HashSet<>();
        allTerms.addAll(tf.keySet());
        allTerms.addAll(idf.keySet());

        List<String> termList = new ArrayList<>(allTerms);
        double[] vec = new double[termList.size()];
        for (int i = 0; i < termList.size(); i++) {
            String term = termList.get(i);
            double tfVal = tf.getOrDefault(term, 0.0);
            double idfVal = idf.getOrDefault(term, 0.0);
            vec[i] = tfVal * idfVal;
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

    // ==================== 内部类 ====================

    @lombok.Data
    public static class KnowledgeDoc {
        private Long id;
        private String title;
        private String content;
        private String module;
        private List<String> tokens;
        private Map<String, Double> tf;
    }

    @lombok.Data
    private static class DocSimilarity {
        private KnowledgeDoc doc;
        private double similarity;
    }
}
