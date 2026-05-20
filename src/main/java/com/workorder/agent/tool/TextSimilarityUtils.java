package com.workorder.agent.tool;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文本相似度计算工具，基于 TF-IDF + 余弦相似度，供重复检测和 RAG 检索共用。
 */
public final class TextSimilarityUtils {

    private TextSimilarityUtils() {
    }

    /**
     * 中文 unigram + bigram 分词，英文按空格分词。
     */
    public static List<String> tokenize(String text) {
        if (StrUtil.isBlank(text)) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        String cleaned = text.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", " ");

        // 英文分词
        for (String word : cleaned.split("\\s+")) {
            if (word.length() >= 2) {
                tokens.add(word);
            }
        }

        // 中文分词：unigram + bigram
        StringBuilder chineseChars = new StringBuilder();
        for (char c : cleaned.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fa5) {
                chineseChars.append(c);
            }
        }
        String cn = chineseChars.toString();
        for (int i = 0; i < cn.length(); i++) {
            tokens.add(String.valueOf(cn.charAt(i)));
            if (i < cn.length() - 1) {
                tokens.add(cn.substring(i, i + 2));
            }
        }

        return tokens;
    }

    /**
     * 计算词频（归一化）。
     */
    public static Map<String, Double> computeTF(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        if (CollUtil.isEmpty(tokens)) {
            return tf;
        }
        for (String t : tokens) {
            tf.merge(t, 1.0, Double::sum);
        }
        double total = tokens.size();
        tf.replaceAll((k, v) -> v / total);
        return tf;
    }

    /**
     * 基于文档词条列表集合计算全局 IDF。
     */
    public static Map<String, Double> computeIDF(List<List<String>> allTokenLists) {
        Map<String, Double> idf = new HashMap<>();
        int n = allTokenLists.size();
        if (n == 0) {
            return idf;
        }
        Map<String, Integer> df = new HashMap<>();
        for (List<String> tokens : allTokenLists) {
            Set<String> unique = new HashSet<>(tokens);
            for (String t : unique) {
                df.merge(t, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : df.entrySet()) {
            idf.put(e.getKey(), Math.log(1.0 + n / (1.0 + e.getValue())));
        }
        return idf;
    }

    /**
     * 基于 TF 和 IDF 构建向量。
     */
    public static double[] buildVector(Map<String, Double> tf, Map<String, Double> idf) {
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

    /**
     * 余弦相似度。
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
