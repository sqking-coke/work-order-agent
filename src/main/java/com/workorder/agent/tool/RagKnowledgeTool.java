package com.workorder.agent.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workorder.agent.entity.WorkKnowledge;
import com.workorder.agent.mapper.WorkKnowledgeMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 轻量化 RAG 知识库工具，基于 TF-IDF + 余弦相似度实现语义检索，无需外部向量数据库。
 */
@Slf4j
@Component
public class RagKnowledgeTool {

    private final WorkKnowledgeMapper knowledgeMapper;
    private final LLMClient llmClient;
    private final int topK;
    private final double similarityThreshold;

    /** 全量知识库缓存 */
    private List<KnowledgeDoc> docs = new ArrayList<>();

    /** 全局 IDF */
    private Map<String, Double> idf = new HashMap<>();

    public RagKnowledgeTool(WorkKnowledgeMapper knowledgeMapper,
                            LLMClient llmClient,
                            @Value("${agent.rag.top-k:5}") int topK,
                            @Value("${agent.rag.similarity-threshold:0.3}") double similarityThreshold) {
        this.knowledgeMapper = knowledgeMapper;
        this.llmClient = llmClient;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    @PostConstruct
    public void init() {
        refreshIndex();
    }

    /**
     * 刷新知识库索引。
     */
    public synchronized void refreshIndex() {
        List<WorkKnowledge> list = knowledgeMapper.selectList(
                new LambdaQueryWrapper<WorkKnowledge>()
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
            doc.setTokens(TextSimilarityUtils.tokenize(fullText));
            doc.setTf(TextSimilarityUtils.computeTF(doc.getTokens()));
            return doc;
        }).collect(Collectors.toList());

        // 计算全局 IDF
        List<List<String>> allTokenLists = docs.stream()
                .map(KnowledgeDoc::getTokens)
                .collect(Collectors.toList());
        idf = TextSimilarityUtils.computeIDF(allTokenLists);
        log.info("RAG 知识库索引刷新完成，共 {} 篇文档", docs.size());
    }

    /**
     * 根据问题检索最匹配的知识点。
     */
    public List<KnowledgeDoc> search(String query) {
        if (CollUtil.isEmpty(docs)) {
            return Collections.emptyList();
        }

        List<String> queryTokens = TextSimilarityUtils.tokenize(query);
        Map<String, Double> queryTF = TextSimilarityUtils.computeTF(queryTokens);
        double[] queryVec = TextSimilarityUtils.buildVector(queryTF, idf);

        List<DocSimilarity> similarities = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            double[] docVec = TextSimilarityUtils.buildVector(doc.getTf(), idf);
            double sim = TextSimilarityUtils.cosineSimilarity(queryVec, docVec);
            if (sim >= similarityThreshold) {
                DocSimilarity ds = new DocSimilarity();
                ds.setDoc(doc);
                ds.setSimilarity(sim);
                similarities.add(ds);
            }
        }

        similarities.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));

        return similarities.stream()
                .limit(topK)
                .map(DocSimilarity::getDoc)
                .collect(Collectors.toList());
    }

    /**
     * RAG 智能答疑：检索 + LLM 生成答复。
     */
    public String answer(String question) {
        List<KnowledgeDoc> matched = search(question);

        if (CollUtil.isEmpty(matched)) {
            log.info("RAG 未匹配到相关知识点，问题: {}", question);
            return null;
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

    // ==================== 内部类 ====================

    @Data
    public static class KnowledgeDoc {
        private Long id;
        private String title;
        private String content;
        private String module;
        private List<String> tokens;
        private Map<String, Double> tf;
    }

    @Data
    private static class DocSimilarity {
        private KnowledgeDoc doc;
        private double similarity;
    }
}
