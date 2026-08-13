package xyz.ppmblszdp.ai.safeguard;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

/**
 * 默认敏感词匹配器实现。
 * 针对 < 1000 条词库采用预编译模式高效替换；若未来词库扩展至上万条，可替换为 Aho-Corasick (AC自动机) 实现类。
 */
public class DefaultSensitiveWordMatcher implements SensitiveWordMatcher {

    private final Set<String> words = new CopyOnWriteArraySet<>();
    private volatile int maxWordLength = 0;
    private volatile Pattern cachedPattern = null;

    public DefaultSensitiveWordMatcher(Collection<String> initialWords) {
        reload(initialWords);
    }

    @Override
    public boolean containsAny(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return false;
        }
        Pattern p = this.cachedPattern;
        if (p != null) {
            return p.matcher(text).find();
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String mask(String text, String replacement) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return text;
        }
        String safeReplacement = java.util.regex.Matcher.quoteReplacement(replacement != null ? replacement : "***");
        Pattern p = this.cachedPattern;
        if (p != null) {
            return p.matcher(text).replaceAll(safeReplacement);
        }
        String result = text;
        for (String word : words) {
            if (result.contains(word)) {
                result = result.replace(word, replacement);
            }
        }
        return result;
    }

    @Override
    public synchronized void reload(Collection<String> newWords) {
        this.words.clear();
        int maxLen = 0;
        if (newWords != null) {
            for (String w : newWords) {
                if (w != null && !w.trim().isEmpty()) {
                    String trimmed = w.trim();
                    this.words.add(trimmed);
                    if (trimmed.length() > maxLen) {
                        maxLen = trimmed.length();
                    }
                }
            }
        }
        this.maxWordLength = maxLen;

        if (!this.words.isEmpty()) {
            // 将词库构建为单一预编译正则表达式 (word1|word2|...)
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (String word : this.words) {
                if (i > 0) sb.append("|");
                sb.append(Pattern.quote(word));
                i++;
            }
            this.cachedPattern = Pattern.compile(sb.toString());
        } else {
            this.cachedPattern = null;
        }
    }

    @Override
    public int getMaxWordLength() {
        return maxWordLength;
    }

    public Set<String> getWords() {
        return Collections.unmodifiableSet(words);
    }
}
