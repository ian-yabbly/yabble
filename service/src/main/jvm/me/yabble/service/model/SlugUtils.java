package me.yabble.service.model;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Set;

public class SlugUtils {
    private static final Set<String> ENGLISH_STOP_WORDS = Sets.newHashSet("a", "able", "about", "across", "after", "all", "almost", "also", "am", "among", "an", "and", "any", "are", "as", "at", "be", "because", "been", "but", "by", "can", "cannot", "could", "dear", "did", "do", "does", "either", "else", "ever", "every", "for", "from", "get", "got", "had", "has", "have", "he", "her", "hers", "him", "his", "how", "however", "i", "if", "in", "into", "is", "it", "its", "just", "least", "let", "like", "likely", "may", "me", "might", "most", "must", "my", "neither", "no", "nor", "not", "of", "off", "often", "on", "only", "or", "other", "our", "own", "rather", "said", "say", "says", "she", "should", "since", "so", "some", "than", "that", "the", "their", "them", "then", "there", "these", "they", "this", "tis", "to", "too", "twas", "us", "wants", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why", "will", "with", "would", "yet", "you", "your");

    public static String gen(String text) {
        String t = text
                .replaceAll("\\s+", "-")
                .replaceAll("[^\\p{Alnum}-\\./]+", "")
                .replaceAll("[/\\.]", "-")
                .replaceAll("^-|-$", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("-+$", "")
                .toLowerCase();

        if (t.length() < 140) {
            if (t.length() > 0) {
                return t;
            } else {
                return "yabbly";
            }
        } else {
            List<String> ts = Lists.newArrayList();
            for (String v : Splitter.on('-').split(t)) {
                if (!ENGLISH_STOP_WORDS.contains(v)) {
                    ts.add(v);
                }
                if (ts.size() >= 16) { break; }
            }
            String v = Joiner.on('-').join(ts);
            if (v.length() > 0) {
                return v;
            } else {
                return "yabbly";
            }
        }
    }
}
